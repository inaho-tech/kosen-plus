package com.webclass.plus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.webclass.plus.MainActivity
import com.webclass.plus.R
import com.webclass.plus.ui.viewmodels.PomodoroMode
import com.webclass.plus.ui.viewmodels.PomodoroState

object PomodoroNotificationUtils {
    private const val CHANNEL_ID = "pomodoro_timer_channel"
    private const val POMODORO_NOTIFICATION_ID = 1002
    private const val STOPWATCH_NOTIFICATION_ID = 1003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ポモドーロタイマー"
            val descriptionText = "ポモドーロタイマーの終了をお知らせします"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCompletionNotification(context: Context, isBreakTime: Boolean) {
        createNotificationChannel(context)

        val title = if (isBreakTime) "集中時間終了！" else "休憩時間終了！"
        val message = if (isBreakTime) "お疲れ様でした。休憩を取りましょう ☕️" else "次の集中時間を始めましょう ✍️"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("shortcut_route", "tools")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(POMODORO_NOTIFICATION_ID, builder.build())
        
        triggerAlert(context)
    }

    // --- Ongoing Pomodoro Timer Notification ---
    fun updatePomodoroNotification(
        context: Context,
        timerState: PomodoroState,
        currentMode: PomodoroMode,
        timeLeftSeconds: Int
    ) {
        createNotificationChannel(context)

        if (timerState == PomodoroState.IDLE) {
            cancelPomodoroNotification(context)
            return
        }

        val modeText = if (currentMode == PomodoroMode.WORK) "集中時間 ✍️" else "休憩時間 ☕️"
        val statusText = when (timerState) {
            PomodoroState.RUNNING -> "計測中"
            PomodoroState.PAUSED -> "一時停止中"
            PomodoroState.IDLE -> ""
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("shortcut_route", "tools")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, PomodoroNotificationDismissReceiver::class.java).apply {
            putExtra("type", "pomodoro")
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            2002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteViews construction
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_pomodoro_collapsed)
        val expandedView = RemoteViews(context.packageName, R.layout.notification_pomodoro_expanded)

        // Set mode description
        collapsedView.setTextViewText(R.id.notification_subtitle, "$modeText ($statusText)")
        expandedView.setTextViewText(R.id.notification_subtitle, "$modeText ($statusText)")

        // Configure time display
        if (timerState == PomodoroState.RUNNING) {
            // Show chronometer
            collapsedView.setViewVisibility(R.id.notification_chronometer, View.VISIBLE)
            expandedView.setViewVisibility(R.id.notification_chronometer, View.VISIBLE)
            collapsedView.setViewVisibility(R.id.notification_time_text, View.GONE)
            expandedView.setViewVisibility(R.id.notification_time_text, View.GONE)

            // Dynamic count-down
            val baseTime = SystemClock.elapsedRealtime() + timeLeftSeconds * 1000L
            collapsedView.setBoolean(R.id.notification_chronometer, "setCountDown", true)
            collapsedView.setChronometer(R.id.notification_chronometer, baseTime, null, true)
            expandedView.setBoolean(R.id.notification_chronometer, "setCountDown", true)
            expandedView.setChronometer(R.id.notification_chronometer, baseTime, null, true)
        } else {
            // Show static time text
            collapsedView.setViewVisibility(R.id.notification_chronometer, View.GONE)
            expandedView.setViewVisibility(R.id.notification_chronometer, View.GONE)
            collapsedView.setViewVisibility(R.id.notification_time_text, View.VISIBLE)
            expandedView.setViewVisibility(R.id.notification_time_text, View.VISIBLE)

            val minutes = timeLeftSeconds / 60
            val seconds = timeLeftSeconds % 60
            val timeString = String.format("%02d:%02d", minutes, seconds)
            collapsedView.setTextViewText(R.id.notification_time_text, timeString)
            expandedView.setTextViewText(R.id.notification_time_text, timeString)
        }

        // Configure actions on expanded view
        val pauseResumeIntent = Intent(context, PomodoroNotificationActionReceiver::class.java).apply {
            action = if (timerState == PomodoroState.RUNNING) {
                PomodoroNotificationActionReceiver.ACTION_POMODORO_PAUSE
            } else {
                PomodoroNotificationActionReceiver.ACTION_POMODORO_RESUME
            }
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        expandedView.setOnClickPendingIntent(R.id.btn_pause_resume, pauseResumePendingIntent)
        
        if (timerState == PomodoroState.RUNNING) {
            expandedView.setTextViewText(R.id.txt_pause_resume, "一時停止")
            expandedView.setImageViewResource(R.id.img_pause_resume, R.drawable.ic_pause)
        } else {
            expandedView.setTextViewText(R.id.txt_pause_resume, "再開")
            expandedView.setImageViewResource(R.id.img_pause_resume, R.drawable.ic_play_arrow)
        }

        val skipIntent = Intent(context, PomodoroNotificationActionReceiver::class.java).apply {
            action = PomodoroNotificationActionReceiver.ACTION_POMODORO_SKIP
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        expandedView.setOnClickPendingIntent(R.id.btn_skip, skipPendingIntent)

        val resetIntent = Intent(context, PomodoroNotificationActionReceiver::class.java).apply {
            action = PomodoroNotificationActionReceiver.ACTION_POMODORO_RESET
        }
        val resetPendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        expandedView.setOnClickPendingIntent(R.id.btn_reset, resetPendingIntent)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Non-dismissible
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColor(context.getColor(R.color.widget_primary))
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(POMODORO_NOTIFICATION_ID, builder.build())
    }

    fun cancelPomodoroNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(POMODORO_NOTIFICATION_ID)
    }

    // --- Ongoing Stopwatch Notification ---
    fun updateStopwatchNotification(
        context: Context,
        isRunning: Boolean,
        elapsedTime: Long
    ) {
        createNotificationChannel(context)

        val statusText = if (isRunning) "計測中" else "一時停止中"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("shortcut_route", "tools")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, PomodoroNotificationDismissReceiver::class.java).apply {
            putExtra("type", "stopwatch")
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            2003,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteViews construction
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_stopwatch_collapsed)
        val expandedView = RemoteViews(context.packageName, R.layout.notification_stopwatch_expanded)

        // Set status text
        collapsedView.setTextViewText(R.id.notification_subtitle, statusText)
        expandedView.setTextViewText(R.id.notification_subtitle, statusText)

        // Configure time display
        if (isRunning) {
            // Show chronometer
            collapsedView.setViewVisibility(R.id.notification_chronometer, View.VISIBLE)
            expandedView.setViewVisibility(R.id.notification_chronometer, View.VISIBLE)
            collapsedView.setViewVisibility(R.id.notification_time_text, View.GONE)
            expandedView.setViewVisibility(R.id.notification_time_text, View.GONE)

            // Dynamic count-up base on system elapsed realtime
            val baseTime = SystemClock.elapsedRealtime() - elapsedTime
            collapsedView.setBoolean(R.id.notification_chronometer, "setCountDown", false)
            collapsedView.setChronometer(R.id.notification_chronometer, baseTime, null, true)
            expandedView.setBoolean(R.id.notification_chronometer, "setCountDown", false)
            expandedView.setChronometer(R.id.notification_chronometer, baseTime, null, true)
        } else {
            // Show static time text (precise to hundredths when paused)
            collapsedView.setViewVisibility(R.id.notification_chronometer, View.GONE)
            expandedView.setViewVisibility(R.id.notification_chronometer, View.GONE)
            collapsedView.setViewVisibility(R.id.notification_time_text, View.VISIBLE)
            expandedView.setViewVisibility(R.id.notification_time_text, View.VISIBLE)

            val min = (elapsedTime / 60000)
            val sec = (elapsedTime % 60000) / 1000
            val hundredths = (elapsedTime % 1000) / 10
            val timeString = String.format("%02d:%02d.%02d", min, sec, hundredths)
            collapsedView.setTextViewText(R.id.notification_time_text, timeString)
            expandedView.setTextViewText(R.id.notification_time_text, timeString)
        }

        // Configure actions on expanded view
        val pauseResumeIntent = Intent(context, PomodoroNotificationActionReceiver::class.java).apply {
            action = if (isRunning) {
                PomodoroNotificationActionReceiver.ACTION_STOPWATCH_PAUSE
            } else {
                PomodoroNotificationActionReceiver.ACTION_STOPWATCH_RESUME
            }
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context,
            301,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        expandedView.setOnClickPendingIntent(R.id.btn_pause_resume, pauseResumePendingIntent)

        if (isRunning) {
            expandedView.setTextViewText(R.id.txt_pause_resume, "一時停止")
            expandedView.setImageViewResource(R.id.img_pause_resume, R.drawable.ic_pause)
        } else {
            expandedView.setTextViewText(R.id.txt_pause_resume, "再開")
            expandedView.setImageViewResource(R.id.img_pause_resume, R.drawable.ic_play_arrow)
        }

        val resetIntent = Intent(context, PomodoroNotificationActionReceiver::class.java).apply {
            action = PomodoroNotificationActionReceiver.ACTION_STOPWATCH_RESET
        }
        val resetPendingIntent = PendingIntent.getBroadcast(
            context,
            302,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        expandedView.setOnClickPendingIntent(R.id.btn_reset, resetPendingIntent)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Non-dismissible
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColor(context.getColor(R.color.widget_primary))
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(STOPWATCH_NOTIFICATION_ID, builder.build())
    }

    fun cancelStopwatchNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(STOPWATCH_NOTIFICATION_ID)
    }

    fun triggerAlert(context: Context) {
        // 1. Play Default Notification Sound
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Vibrate
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 200, 200, 200)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
