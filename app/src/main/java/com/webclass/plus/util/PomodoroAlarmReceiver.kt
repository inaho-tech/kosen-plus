package com.webclass.plus.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val modeStr = intent.getStringExtra("mode") ?: "WORK"
        val isBreakTime = modeStr == "WORK"
        PomodoroNotificationUtils.showCompletionNotification(context, isBreakTime)
    }
}
