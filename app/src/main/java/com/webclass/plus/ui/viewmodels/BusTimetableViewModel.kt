package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webclass.plus.data.models.BusDeparture
import com.webclass.plus.data.models.BusStop
import com.webclass.plus.data.models.DayType
import com.webclass.plus.data.models.Direction
import com.webclass.plus.data.repository.BusRepository
import com.webclass.plus.util.BusPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class BusTimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val busPreferences = BusPreferences(application)

    // UI State
    private val _selectedDirection = MutableStateFlow(Direction.DOWN) // Default: Station -> Kosen
    val selectedDirection: StateFlow<Direction> = _selectedDirection.asStateFlow()

    private val _dayType = MutableStateFlow(DayType.WEEKDAY)
    val dayType: StateFlow<DayType> = _dayType.asStateFlow()

    private val _timetable = MutableStateFlow<List<BusDeparture>>(emptyList())
    val timetable: StateFlow<List<BusDeparture>> = _timetable.asStateFlow()

    private val _nextBus = MutableStateFlow<BusDeparture?>(null)
    val nextBus: StateFlow<BusDeparture?> = _nextBus.asStateFlow()

    private val _currentTime = MutableStateFlow(Calendar.getInstance())
    val currentTime: StateFlow<Calendar> = _currentTime.asStateFlow()
    
    // Preferences State
    val dayTypeMode = busPreferences.dayTypeModeFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BusPreferences.DayTypeMode.AUTO
    )

    private var timerJob: Job? = null

    private val _showOnlyUpcoming = MutableStateFlow(false)
    val showOnlyUpcoming: StateFlow<Boolean> = _showOnlyUpcoming.asStateFlow()

    init {
        // Initialize state based on preferences and current time
        viewModelScope.launch {
            // Load default stop from preferences to determine initial direction
            val defaultStop = busPreferences.defaultBusStopFlow.first()
            _selectedDirection.value = defaultStop.direction
            
            // Start timer
            startTimer()
        }

        // Observe changes to inputs to update timetable
        viewModelScope.launch {
            combine(
                _selectedDirection,
                dayTypeMode,
                _currentTime,
                _showOnlyUpcoming
            ) { direction, mode, time, onlyUpcoming ->
                Quad(direction, mode, time, onlyUpcoming)
            }.collect { (direction, mode, time, onlyUpcoming) ->
                updateDayType(mode)
                updateTimetable(direction, _dayType.value, time, onlyUpcoming)
                updateNextBus(direction, _dayType.value, time)
            }
        }
    }
    
    // Helper data class for combine
    data class Quad<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                _currentTime.value = Calendar.getInstance()
                delay(1000 * 30) // Update every 30 seconds
            }
        }
    }

    private fun updateDayType(mode: BusPreferences.DayTypeMode) {
        _dayType.value = when (mode) {
            BusPreferences.DayTypeMode.AUTO -> BusRepository.getAutoDayType()
            BusPreferences.DayTypeMode.MANUAL_WEEKDAY -> DayType.WEEKDAY
            BusPreferences.DayTypeMode.MANUAL_HOLIDAY -> DayType.HOLIDAY
        }
    }

    private fun updateTimetable(direction: Direction, dayType: DayType, time: Calendar, onlyUpcoming: Boolean) {
        val allBuses = BusRepository.getCombinedTimetable(direction, dayType)
        if (onlyUpcoming) {
            val currentMinutes = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE)
            _timetable.value = allBuses.filter { it.toTimeUserId() >= currentMinutes }
        } else {
            _timetable.value = allBuses
        }
    }

    private fun updateNextBus(direction: Direction, dayType: DayType, time: Calendar) {
        val hour = time.get(Calendar.HOUR_OF_DAY)
        val minute = time.get(Calendar.MINUTE)
        _nextBus.value = BusRepository.getNextBusForDirection(direction, dayType, hour, minute)
    }

    // User Actions
    fun setDirection(direction: Direction) {
        _selectedDirection.value = direction
    }

    fun toggleDayTypeMode() {
        viewModelScope.launch {
            val current = dayTypeMode.value
            val next = when (current) {
                BusPreferences.DayTypeMode.AUTO -> BusPreferences.DayTypeMode.MANUAL_WEEKDAY
                BusPreferences.DayTypeMode.MANUAL_WEEKDAY -> BusPreferences.DayTypeMode.MANUAL_HOLIDAY
                BusPreferences.DayTypeMode.MANUAL_HOLIDAY -> BusPreferences.DayTypeMode.AUTO
            }
            busPreferences.setDayTypeMode(next)
        }
    }

    fun toggleShowOnlyUpcoming() {
        _showOnlyUpcoming.value = !_showOnlyUpcoming.value
    }
}
