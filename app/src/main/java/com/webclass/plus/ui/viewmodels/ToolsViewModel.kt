package com.webclass.plus.ui.viewmodels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webclass.plus.util.PomodoroAlarmReceiver
import com.webclass.plus.util.PomodoroNotificationUtils
import com.webclass.plus.util.PomodoroPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PomodoroMode { WORK, BREAK }
enum class PomodoroState { IDLE, RUNNING, PAUSED }

class ToolsViewModel : ViewModel() {

    private var sharedPrefs: SharedPreferences? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun registerPrefsListener(context: Context) {
        if (prefsListener != null) return
        val prefs = context.applicationContext.getSharedPreferences("pomodoro_stopwatch_prefs", Context.MODE_PRIVATE)
        sharedPrefs = prefs
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "stopwatch_running" || key == "stopwatch_accumulated_time" || key == "stopwatch_elapsed_time" ||
                key == "pomodoro_state" || key == "pomodoro_time_left" || key == "pomodoro_end_time" || key == "pomodoro_mode") {
                viewModelScope.launch {
                    syncStopwatchState(context.applicationContext, force = true)
                    syncTimerState(context.applicationContext, force = true)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefsListener?.let {
            sharedPrefs?.unregisterOnSharedPreferenceChangeListener(it)
        }
    }
    
    // --- Pomodoro State ---
    var workDurationMinutes by mutableStateOf(25)
    var breakDurationMinutes by mutableStateOf(5)
    var currentMode by mutableStateOf(PomodoroMode.WORK)
    var timerState by mutableStateOf(PomodoroState.IDLE)
    var timeLeftSeconds by mutableStateOf(25 * 60)
    var totalDurationSeconds by mutableStateOf(25 * 60)
    var endTime by mutableStateOf(0L)
    var keepScreenOn by mutableStateOf(false)
    
    private var pomodoroJob: Job? = null

    // --- Stopwatch State ---
    var isStopwatchRunning by mutableStateOf(false)
    var elapsedTime by mutableStateOf(0L)
    var startTime by mutableStateOf(0L)
    var accumulatedTime by mutableStateOf(0L)
    val laps = mutableStateListOf<Long>()
    
    private var stopwatchJob: Job? = null

    // --- Pomodoro Actions ---
    fun updateWorkDuration(minutes: Int) {
        workDurationMinutes = minutes
        if (timerState == PomodoroState.IDLE && currentMode == PomodoroMode.WORK) {
            timeLeftSeconds = minutes * 60
            totalDurationSeconds = timeLeftSeconds
        }
    }

    fun updateBreakDuration(minutes: Int) {
        breakDurationMinutes = minutes
        if (timerState == PomodoroState.IDLE && currentMode == PomodoroMode.BREAK) {
            timeLeftSeconds = minutes * 60
            totalDurationSeconds = timeLeftSeconds
        }
    }

    // --- Pomodoro Actions ---
    private fun savePomodoro(context: Context) {
        PomodoroPrefs.savePomodoroState(
            context,
            timerState.name,
            currentMode.name,
            timeLeftSeconds,
            endTime,
            workDurationMinutes,
            breakDurationMinutes
        )
    }

    private fun saveStopwatch(context: Context) {
        PomodoroPrefs.saveStopwatchState(
            context,
            isStopwatchRunning,
            startTime,
            accumulatedTime,
            elapsedTime,
            laps.toList()
        )
    }

    fun startPomodoro(context: Context) {
        if (timerState == PomodoroState.RUNNING) return
        endTime = System.currentTimeMillis() + timeLeftSeconds * 1000L
        timerState = PomodoroState.RUNNING
        
        scheduleAlarm(context, endTime)
        startPomodoroLoop(context)
        savePomodoro(context)
        PomodoroNotificationUtils.updatePomodoroNotification(context, timerState, currentMode, timeLeftSeconds)
    }

    private fun startPomodoroLoop(context: Context) {
        pomodoroJob?.cancel()
        pomodoroJob = viewModelScope.launch {
            while (timeLeftSeconds > 0) {
                delay(200)
                val now = System.currentTimeMillis()
                val diffSeconds = ((endTime - now) / 1000L).toInt()
                if (diffSeconds <= 0) {
                    timeLeftSeconds = 0
                    break
                } else {
                    timeLeftSeconds = diffSeconds
                }
            }
            onPomodoroFinished(context)
        }
    }

    fun pausePomodoro(context: Context) {
        if (timerState != PomodoroState.RUNNING) return
        pomodoroJob?.cancel()
        pomodoroJob = null
        timerState = PomodoroState.PAUSED
        cancelAlarm(context)
        savePomodoro(context)
        PomodoroNotificationUtils.cancelPomodoroNotification(context)
    }

    fun resetPomodoro(context: Context) {
        pomodoroJob?.cancel()
        pomodoroJob = null
        timerState = PomodoroState.IDLE
        timeLeftSeconds = if (currentMode == PomodoroMode.WORK) {
            workDurationMinutes * 60
        } else {
            breakDurationMinutes * 60
        }
        totalDurationSeconds = timeLeftSeconds
        cancelAlarm(context)
        savePomodoro(context)
        PomodoroNotificationUtils.cancelPomodoroNotification(context)
    }

    fun skipPomodoro(context: Context) {
        pomodoroJob?.cancel()
        pomodoroJob = null
        timerState = PomodoroState.IDLE
        currentMode = if (currentMode == PomodoroMode.WORK) PomodoroMode.BREAK else PomodoroMode.WORK
        timeLeftSeconds = if (currentMode == PomodoroMode.WORK) {
            workDurationMinutes * 60
        } else {
            breakDurationMinutes * 60
        }
        totalDurationSeconds = timeLeftSeconds
        cancelAlarm(context)
        savePomodoro(context)
        PomodoroNotificationUtils.cancelPomodoroNotification(context)
    }

    private fun onPomodoroFinished(context: Context) {
        timerState = PomodoroState.IDLE
        val finishedMode = currentMode
        
        currentMode = if (currentMode == PomodoroMode.WORK) PomodoroMode.BREAK else PomodoroMode.WORK
        timeLeftSeconds = if (currentMode == PomodoroMode.WORK) {
            workDurationMinutes * 60
        } else {
            breakDurationMinutes * 60
        }
        totalDurationSeconds = timeLeftSeconds
        cancelAlarm(context)
        savePomodoro(context)
        
        // Show completion notification (which plays sound/vibration)
        PomodoroNotificationUtils.showCompletionNotification(context, finishedMode == PomodoroMode.WORK)
    }

    fun syncTimerState(context: Context, force: Boolean = false) {
        registerPrefsListener(context)
        val saved = PomodoroPrefs.getPomodoroState(context)
        val savedState = PomodoroState.valueOf(saved.state)
        val savedMode = PomodoroMode.valueOf(saved.mode)

        if (force || timerState != savedState || currentMode != savedMode || timeLeftSeconds != saved.timeLeft || endTime != saved.endTime) {
            workDurationMinutes = saved.workDuration
            breakDurationMinutes = saved.breakDuration
            currentMode = savedMode
            timerState = savedState
            timeLeftSeconds = saved.timeLeft
            endTime = saved.endTime
            totalDurationSeconds = if (currentMode == PomodoroMode.WORK) workDurationMinutes * 60 else breakDurationMinutes * 60

            if (timerState == PomodoroState.RUNNING) {
                val now = System.currentTimeMillis()
                val diffSeconds = ((endTime - now) / 1000L).toInt()
                if (diffSeconds <= 0) {
                    timeLeftSeconds = 0
                    onPomodoroFinished(context)
                } else {
                    timeLeftSeconds = diffSeconds
                    startPomodoroLoop(context)
                    PomodoroNotificationUtils.updatePomodoroNotification(context, timerState, currentMode, timeLeftSeconds)
                }
            } else {
                pomodoroJob?.cancel()
                pomodoroJob = null
                PomodoroNotificationUtils.cancelPomodoroNotification(context)
            }
        } else if (timerState == PomodoroState.RUNNING && pomodoroJob == null) {
            val now = System.currentTimeMillis()
            val diffSeconds = ((endTime - now) / 1000L).toInt()
            if (diffSeconds <= 0) {
                timeLeftSeconds = 0
                onPomodoroFinished(context)
            } else {
                timeLeftSeconds = diffSeconds
                startPomodoroLoop(context)
            }
        }
    }

    // --- Stopwatch Actions ---
    fun startStopwatch(context: Context) {
        if (isStopwatchRunning) return
        startTime = System.currentTimeMillis()
        isStopwatchRunning = true
        startStopwatchLoop()
        saveStopwatch(context)
        PomodoroNotificationUtils.updateStopwatchNotification(context, isStopwatchRunning, elapsedTime)
    }

    private fun startStopwatchLoop() {
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (isStopwatchRunning) {
                elapsedTime = accumulatedTime + (System.currentTimeMillis() - startTime)
                delay(30)
            }
        }
    }

    fun pauseStopwatch(context: Context) {
        if (!isStopwatchRunning) return
        stopwatchJob?.cancel()
        stopwatchJob = null
        isStopwatchRunning = false
        accumulatedTime = elapsedTime
        saveStopwatch(context)
        PomodoroNotificationUtils.cancelStopwatchNotification(context)
    }

    fun resetStopwatch(context: Context) {
        stopwatchJob?.cancel()
        stopwatchJob = null
        isStopwatchRunning = false
        elapsedTime = 0L
        accumulatedTime = 0L
        laps.clear()
        saveStopwatch(context)
        PomodoroNotificationUtils.cancelStopwatchNotification(context)
    }

    fun addLap(context: Context) {
        if (isStopwatchRunning || elapsedTime > 0L) {
            laps.add(0, elapsedTime)
            saveStopwatch(context)
        }
    }

    fun syncStopwatchState(context: Context, force: Boolean = false) {
        registerPrefsListener(context)
        val saved = PomodoroPrefs.getStopwatchState(context)
        
        if (force || isStopwatchRunning != saved.isRunning || accumulatedTime != saved.accumulatedTime || elapsedTime != saved.elapsedTime || laps.size != saved.laps.size) {
            isStopwatchRunning = saved.isRunning
            startTime = saved.startTime
            accumulatedTime = saved.accumulatedTime
            elapsedTime = saved.elapsedTime
            laps.clear()
            laps.addAll(saved.laps)

            if (isStopwatchRunning) {
                startStopwatchLoop()
                PomodoroNotificationUtils.updateStopwatchNotification(context, isStopwatchRunning, elapsedTime)
            } else {
                stopwatchJob?.cancel()
                stopwatchJob = null
                PomodoroNotificationUtils.cancelStopwatchNotification(context)
            }
        } else if (isStopwatchRunning && stopwatchJob == null) {
            startStopwatchLoop()
        }
    }

    // --- Alarm manager helper ---
    private fun scheduleAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
            putExtra("mode", currentMode.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PomodoroAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
