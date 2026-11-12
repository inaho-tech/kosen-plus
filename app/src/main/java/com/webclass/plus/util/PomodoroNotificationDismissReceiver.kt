package com.webclass.plus.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webclass.plus.ui.viewmodels.PomodoroMode
import com.webclass.plus.ui.viewmodels.PomodoroState

class PomodoroNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        
        if (type == "pomodoro") {
            val saved = PomodoroPrefs.getPomodoroState(context)
            val state = try {
                PomodoroState.valueOf(saved.state)
            } catch (e: Exception) {
                PomodoroState.IDLE
            }
            
            if (state == PomodoroState.RUNNING) {
                val mode = try {
                    PomodoroMode.valueOf(saved.mode)
                } catch (e: Exception) {
                    PomodoroMode.WORK
                }
                
                val timeLeft = if (state == PomodoroState.RUNNING) {
                    val remaining = ((saved.endTime - System.currentTimeMillis()) / 1000L).toInt()
                    if (remaining > 0) remaining else 0
                } else {
                    saved.timeLeft
                }
                
                // Re-post notification because it is not allowed to be dismissed while running/paused
                if (state == PomodoroState.RUNNING && timeLeft <= 0) {
                    // Let the alarm or sync handle finished state, don't re-post running if time expired
                } else {
                    PomodoroNotificationUtils.updatePomodoroNotification(context, state, mode, timeLeft)
                }
            }
        } else if (type == "stopwatch") {
            val saved = PomodoroPrefs.getStopwatchState(context)
            if (saved.isRunning) {
                val currentElapsed = if (saved.isRunning) {
                    saved.accumulatedTime + (System.currentTimeMillis() - saved.startTime)
                } else {
                    saved.elapsedTime
                }
                // Re-post notification because it is not allowed to be dismissed while active
                PomodoroNotificationUtils.updateStopwatchNotification(context, saved.isRunning, currentElapsed)
            }
        }
    }
}
