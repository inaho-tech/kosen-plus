package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webclass.plus.data.models.*
import com.webclass.plus.data.repository.DokoTrainRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DokoTrainUiState(
    val stations: List<DokoTrainStation> = emptyList(),
    val query: String = "",
    val selectedStation: DokoTrainStation? = null,
    val direction: DokoTrainDirection = DokoTrainDirection.UP,
    val upTimetable: DokoTrainTimetable? = null,
    val downTimetable: DokoTrainTimetable? = null,
    val lineStatus: DokoTrainLineStatus? = null,
    val favoriteStationKeys: Set<String> = emptySet(),
    val isLoadingStations: Boolean = true,
    val isLoadingDetails: Boolean = false,
    val error: String? = null
)

class DokoTrainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DokoTrainRepository(application)
    private val favoritePreferences = application.getSharedPreferences("doko_train_preferences", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        DokoTrainUiState(
            favoriteStationKeys = favoritePreferences.getStringSet(FAVORITES_KEY, emptySet())?.toSet().orEmpty()
        )
    )
    private var detailJob: Job? = null
    val uiState: StateFlow<DokoTrainUiState> = _uiState.asStateFlow()

    init { loadStations() }

    fun setQuery(query: String) { _uiState.value = _uiState.value.copy(query = query) }

    fun selectStation(station: DokoTrainStation) {
        _uiState.value = _uiState.value.copy(
            selectedStation = station,
            direction = if (station.upFlag != 0) DokoTrainDirection.UP else DokoTrainDirection.DOWN,
            isLoadingDetails = true,
            error = null,
            upTimetable = null,
            downTimetable = null,
            lineStatus = null
        )
        loadDetails(station)
    }

    fun clearSelection() {
        detailJob?.cancel()
        _uiState.value = _uiState.value.copy(
            selectedStation = null,
            error = null,
            upTimetable = null,
            downTimetable = null,
            lineStatus = null,
            isLoadingDetails = false
        )
    }
    fun setDirection(direction: DokoTrainDirection) { _uiState.value = _uiState.value.copy(direction = direction) }
    fun refresh() { _uiState.value.selectedStation?.let(::loadDetails) ?: loadStations(forceRefresh = true) }

    fun toggleFavorite(station: DokoTrainStation) {
        val updated = _uiState.value.favoriteStationKeys.toMutableSet().apply {
            if (!add(station.favoriteKey)) remove(station.favoriteKey)
        }.toSet()
        favoritePreferences.edit().putStringSet(FAVORITES_KEY, updated).apply()
        _uiState.value = _uiState.value.copy(favoriteStationKeys = updated)
    }

    private fun loadStations(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStations = true, error = null)
            runCatching { repository.loadStations(forceRefresh) }
                .onSuccess { _uiState.value = _uiState.value.copy(stations = it, isLoadingStations = false) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingStations = false, error = "駅情報を取得できませんでした。通信状態を確認してください。")
                }
        }
    }

    private fun loadDetails(station: DokoTrainStation) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDetails = true, error = null, lineStatus = null)
            val up = async { runCatching { repository.loadTimetable(station, DokoTrainDirection.UP) }.getOrNull() }
            val down = async { runCatching { repository.loadTimetable(station, DokoTrainDirection.DOWN) }.getOrNull() }
            val status = async { runCatching { repository.loadLineStatus(station.dokoLineId) }.getOrNull() }
            val upResult = up.await()
            val downResult = down.await()
            val statusResult = status.await()
            if (_uiState.value.selectedStation?.favoriteKey != station.favoriteKey) return@launch
            _uiState.value = _uiState.value.copy(
                upTimetable = upResult,
                downTimetable = downResult,
                lineStatus = statusResult,
                direction = when {
                    _uiState.value.direction == DokoTrainDirection.UP && upResult != null -> DokoTrainDirection.UP
                    downResult != null -> DokoTrainDirection.DOWN
                    else -> DokoTrainDirection.UP
                },
                isLoadingDetails = false,
                error = if (upResult == null && downResult == null) "この駅の時刻表情報を取得できませんでした。" else null
            )
        }
    }

    companion object {
        private const val FAVORITES_KEY = "favorite_station_keys"
    }
}
