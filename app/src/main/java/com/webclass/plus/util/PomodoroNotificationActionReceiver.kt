package com.webclass.plus.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webclass.plus.ui.viewmodels.PomodoroMode
import com.webclass.plus.ui.viewmodels.PomodoroState

class PomodoroNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOPWATCH_PAUSE = "com.webclass.plus.ACTION_STOPWATCH_PAUSE"
        const val ACTION_STOPWATCH_RESUME = "com.webclass.plus.ACTION_STOPWATCH_RESUME"
        const val ACTION_STOPWATCH_RESET = "com.webclass.plus.ACTION_STOPWATCH_RESET"

        const val ACTION_POMODORO_PAUSE = "com.webclass.plus.ACTION_POMODORO_PAUSE"
        const val ACTION_POMODORO_RESUME = "com.webclass.plus.ACTION_POMODORO_RESUME"
        const val ACTION_POMODORO_RESET = "com.webclass.plus.ACTION_POMODORO_RESET"
        const val ACTION_POMODORO_SKIP = "com.webclass.plus.ACTION_POMODORO_SKIP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ACTION_STOPWATCH_PAUSE -> {
                val saved = PomodoroPrefs.getStopwatchState(context)
                if (saved.isRunning) {
                    val elapsed = saved.accumulatedTime + (System.currentTimeMillis() - saved.startTime)
                    PomodoroPrefs.saveStopwatchState(
                        context = context,
                        isRunning = false,
                        startTime = 0L,
                        accumulatedTime = elapsed,
                        elapsedTime = elapsed,
                        laps = saved.laps
                    )
                    PomodoroNotificationUtils.updateStopwatchNotification(context, false, elapsed)
                }
            }
            ACTION_STOPWATCH_RESUME -> {
                val saved = PomodoroPrefs.getStopwatchState(context)
                if (!saved.isRunning) {
                    PomodoroPrefs.saveStopwatchState(
                        context = context,
                        isRunning = true,
                        startTime = System.currentTimeMillis(),
                        accumulatedTime = saved.accumulatedTime,
                        elapsedTime = saved.elapsedTime,
                        laps = saved.laps
                    )
                    PomodoroNotificationUtils.updateStopwatchNotification(context, true, saved.elapsedTime)
                }
            }
            ACTION_STOPWATCH_RESET -> {
                PomodoroPrefs.saveStopwatchState(
                    context = context,
                    isRunning = false,
                    startTime = 0L,
                    accumulatedTime = 0L,
                    elapsedTime = 0L,
                    laps = emptyList()
                )
                PomodoroNotificationUtils.cancelStopwatchNotification(context)
            }
            ACTION_POMODORO_PAUSE -> {
                val saved = PomodoroPrefs.getPomodoroState(context)
                if (saved.state == PomodoroState.RUNNING.name) {
                    val remaining = ((saved.endTime - System.currentTimeMillis()) / 1000L).toInt()
                    val timeLeft = if (remaining > 0) remaining else 0

                    PomodoroPrefs.savePomodoroState(
                        context = context,
                        state = PomodoroState.PAUSED.name,
                        mode = saved.mode,
                        timeLeft = timeLeft,
                        endTime = 0L,
                        workDuration = saved.workDuration,
                        breakDuration = saved.breakDuration
                    )
                    cancelAlarm(context)
                    
                    val currentMode = try {
                        PomodoroMode.valueOf(saved.mode)
                    } catch (e: Exception) {
                        PomodoroMode.WORK
                    }
                    PomodoroNotificationUtils.updatePomodoroNotification(context, PomodoroState.PAUSED, currentMode, timeLeft)
                }
            }
            ACTION_POMODORO_RESUME -> {
                val saved = PomodoroPrefs.getPomodoroState(context)
                if (saved.state == PomodoroState.PAUSED.name) {
                    val newEndTime = System.currentTimeMillis() + saved.timeLeft * 1000L

                    PomodoroPrefs.savePomodoroState(
                        context = context,
                        state = PomodoroState.RUNNING.name,
                        mode = saved.mode,
                        timeLeft = saved.timeLeft,
                        endTime = newEndTime,
                        workDuration = saved.workDuration,
                        breakDuration = saved.breakDuration
                    )
                    scheduleAlarm(context, newEndTime, saved.mode)
                    
                    val currentMode = try {
                        PomodoroMode.valueOf(saved.mode)
                    } catch (e: Exception) {
                        PomodoroMode.WORK
                    }
                    PomodoroNotificationUtils.updatePomodoroNotification(context, PomodoroState.RUNNING, currentMode, saved.timeLeft)
                }
            }
            ACTION_POMODORO_RESET -> {
                val saved = PomodoroPrefs.getPomodoroState(context)
                val currentMode = try {
                    PomodoroMode.valueOf(saved.mode)
                } catch (e: Exception) {
                    PomodoroMode.WORK
                }
                val defaultTime = if (currentMode == PomodoroMode.WORK) saved.workDuration * 60 else saved.breakDuration * 60
                
                PomodoroPrefs.savePomodoroState(
                    context = context,
                    state = PomodoroState.IDLE.name,
                    mode = saved.mode,
                    timeLeft = defaultTime,
                    endTime = 0L,
                    workDuration = saved.workDuration,
                    breakDuration = saved.breakDuration
                )
                cancelAlarm(context)
                PomodoroNotificationUtils.cancelPomodoroNotification(context)
            }
            ACTION_POMODORO_SKIP -> {
                val saved = PomodoroPrefs.getPomodoroState(context)
                val currentMode = try {
                    PomodoroMode.valueOf(saved.mode)
                } catch (e: Exception) {
                    PomodoroMode.WORK
                }
                val newMode = if (currentMode == PomodoroMode.WORK) PomodoroMode.BREAK else PomodoroMode.WORK
                val defaultTime = if (newMode == PomodoroMode.WORK) saved.workDuration * 60 else saved.breakDuration * 60
                
                PomodoroPrefs.savePomodoroState(
                    context = context,
                    state = PomodoroState.IDLE.name,
                    mode = newMode.name,
                    timeLeft = defaultTime,
                    endTime = 0L,
                    workDuration = saved.workDuration,
                    breakDuration = saved.breakDuration
                )
                cancelAlarm(context)
                PomodoroNotificationUtils.cancelPomodoroNotification(context)
            }
        }
    }

    private fun scheduleAlarm(context: Context, triggerAtMillis: Long, mode: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PomodoroAlarmReceiver::class.java).apply {
            putExtra("mode", mode)
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
