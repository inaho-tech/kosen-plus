package com.webclass.plus.util

import android.content.Context

object PomodoroPrefs {
    private const val PREFS_NAME = "pomodoro_stopwatch_prefs"

    // Pomodoro keys
    private const val KEY_POMODORO_STATE = "pomodoro_state"
    private const val KEY_POMODORO_MODE = "pomodoro_mode"
    private const val KEY_POMODORO_TIME_LEFT = "pomodoro_time_left"
    private const val KEY_POMODORO_END_TIME = "pomodoro_end_time"
    private const val KEY_WORK_DURATION = "pomodoro_work_duration"
    private const val KEY_BREAK_DURATION = "pomodoro_break_duration"

    // Stopwatch keys
    private const val KEY_STOPWATCH_RUNNING = "stopwatch_running"
    private const val KEY_STOPWATCH_START_TIME = "stopwatch_start_time"
    private const val KEY_STOPWATCH_ACCUMULATED_TIME = "stopwatch_accumulated_time"
    private const val KEY_STOPWATCH_ELAPSED_TIME = "stopwatch_elapsed_time"
    private const val KEY_STOPWATCH_LAPS = "stopwatch_laps"

    fun savePomodoroState(
        context: Context,
        state: String,
        mode: String,
        timeLeft: Int,
        endTime: Long,
        workDuration: Int,
        breakDuration: Int
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_POMODORO_STATE, state)
            putString(KEY_POMODORO_MODE, mode)
            putInt(KEY_POMODORO_TIME_LEFT, timeLeft)
            putLong(KEY_POMODORO_END_TIME, endTime)
            putInt(KEY_WORK_DURATION, workDuration)
            putInt(KEY_BREAK_DURATION, breakDuration)
            apply()
        }
    }

    fun getPomodoroState(context: Context): PomodoroSavedState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PomodoroSavedState(
            state = prefs.getString(KEY_POMODORO_STATE, "IDLE") ?: "IDLE",
            mode = prefs.getString(KEY_POMODORO_MODE, "WORK") ?: "WORK",
            timeLeft = prefs.getInt(KEY_POMODORO_TIME_LEFT, 25 * 60),
            endTime = prefs.getLong(KEY_POMODORO_END_TIME, 0L),
            workDuration = prefs.getInt(KEY_WORK_DURATION, 25),
            breakDuration = prefs.getInt(KEY_BREAK_DURATION, 5)
        )
    }

    fun saveStopwatchState(
        context: Context,
        isRunning: Boolean,
        startTime: Long,
        accumulatedTime: Long,
        elapsedTime: Long,
        laps: List<Long>
    ) {
        val lapsString = laps.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_STOPWATCH_RUNNING, isRunning)
            putLong(KEY_STOPWATCH_START_TIME, startTime)
            putLong(KEY_STOPWATCH_ACCUMULATED_TIME, accumulatedTime)
            putLong(KEY_STOPWATCH_ELAPSED_TIME, elapsedTime)
            putString(KEY_STOPWATCH_LAPS, lapsString)
            apply()
        }
    }

    fun getStopwatchState(context: Context): StopwatchSavedState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lapsString = prefs.getString(KEY_STOPWATCH_LAPS, "") ?: ""
        val laps = if (lapsString.isEmpty()) {
            emptyList()
        } else {
            lapsString.split(",").mapNotNull { it.toLongOrNull() }
        }
        return StopwatchSavedState(
            isRunning = prefs.getBoolean(KEY_STOPWATCH_RUNNING, false),
            startTime = prefs.getLong(KEY_STOPWATCH_START_TIME, 0L),
            accumulatedTime = prefs.getLong(KEY_STOPWATCH_ACCUMULATED_TIME, 0L),
            elapsedTime = prefs.getLong(KEY_STOPWATCH_ELAPSED_TIME, 0L),
            laps = laps
        )
    }
}

data class PomodoroSavedState(
    val state: String,
    val mode: String,
    val timeLeft: Int,
    val endTime: Long,
    val workDuration: Int,
    val breakDuration: Int
)

data class StopwatchSavedState(
    val isRunning: Boolean,
    val startTime: Long,
    val accumulatedTime: Long,
    val elapsedTime: Long,
    val laps: List<Long>
)
