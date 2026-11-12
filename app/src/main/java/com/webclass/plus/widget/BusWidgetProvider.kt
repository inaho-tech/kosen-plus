package com.webclass.plus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.webclass.plus.MainActivity
import com.webclass.plus.R
import com.webclass.plus.data.models.BusDeparture
import com.webclass.plus.data.models.BusStop
import com.webclass.plus.data.repository.BusRepository
import com.webclass.plus.ui.theme.*
import com.webclass.plus.util.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
        // Force schedule updates to ensure the chain is active
        scheduleUpdates(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelUpdates(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == ACTION_FORCE_UPDATE_THEME) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BusWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        for (appWidgetId in ids) {
                            updateWidget(context, appWidgetManager, appWidgetId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            
            // Re-schedule next exact alarm on refresh trigger to continue the chain
            if (intent.action == ACTION_REFRESH) {
                scheduleUpdates(context)
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.bus_widget_layout)
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val dayType = BusRepository.getAutoDayType()

        // Get next bus for each stop
        val nextKosenFront = BusRepository.getNextBus(BusStop.KOSEN_FRONT, dayType, currentHour, currentMinute)
        val nextKosenBack = BusRepository.getNextBus(BusStop.KOSEN_BACK, dayType, currentHour, currentMinute)
        val nextStationEast = BusRepository.getNextBus(BusStop.KISARAZU_EAST, dayType, currentHour, currentMinute)
        val nextStationWest = BusRepository.getNextBus(BusStop.KISARAZU_WEST, dayType, currentHour, currentMinute)

        // Apply theme colors
        applyThemeColors(context, views)

        // Update times (Remaining time highlighted + Departure time secondary)
        setBusTime(views, R.id.kosen_front_time, R.id.kosen_front_remaining, nextKosenFront, currentHour, currentMinute)
        setBusTime(views, R.id.kosen_back_time, R.id.kosen_back_remaining, nextKosenBack, currentHour, currentMinute)
        setBusTime(views, R.id.station_east_time, R.id.station_east_remaining, nextStationEast, currentHour, currentMinute)
        setBusTime(views, R.id.station_west_time, R.id.station_west_remaining, nextStationWest, currentHour, currentMinute)

        // Day type
        views.setTextViewText(R.id.widget_day_type, dayType.getLabel())

        // Last updated timestamp
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val updateTimeStr = "${timeFormat.format(now.time)} 更新"
        views.setTextViewText(R.id.widget_update_time, updateTimeStr)

        // Tap widget body to open app
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        // Tap refresh button to force update
        val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 1, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun applyThemeColors(context: Context, views: RemoteViews) {
        val prefs = ThemePreferences(context)
        val themeEngine = prefs.themeFlow.first()
        val isDynamic = prefs.dynamicColorFlow.first()
        val customColors = prefs.customColorsFlow.first()
        val isCustomDark = prefs.customDarkModeFlow.first()

        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = uiMode == Configuration.UI_MODE_NIGHT_YES

        val resolvedTheme = if (themeEngine == AppTheme.SYSTEM) {
            if (isSystemDark) AppTheme.DARK else AppTheme.LIGHT
        } else {
            themeEngine
        }

        val colorScheme = when (resolvedTheme) {
            AppTheme.LIGHT -> {
                if (isDynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    androidx.compose.material3.dynamicLightColorScheme(context)
                } else {
                    androidx.compose.material3.lightColorScheme(
                        primary = LightPrimary, onPrimary = LightOnPrimary,
                        primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
                        secondary = LightSecondary, onSecondary = LightOnSecondary,
                        secondaryContainer = LightSecondaryContainer, onSecondaryContainer = LightOnSecondaryContainer,
                        background = LightBackground, onBackground = LightOnBackground,
                        surface = LightSurface, onSurface = LightOnSurface,
                        outline = LightOutline, outlineVariant = LightOutlineVariant
                    )
                }
            }
            AppTheme.DARK -> {
                if (isDynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    androidx.compose.material3.dynamicDarkColorScheme(context)
                } else {
                    androidx.compose.material3.darkColorScheme(
                        primary = DarkPrimary, onPrimary = DarkOnPrimary,
                        primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
                        secondary = DarkSecondary, onSecondary = DarkOnSecondary,
                        secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkOnSecondaryContainer,
                        background = DarkBackground, onBackground = DarkOnBackground,
                        surface = DarkSurface, onSurface = DarkOnSurface,
                        outline = DarkOutline, outlineVariant = DarkOutlineVariant
                    )
                }
            }
            AppTheme.PASTEL -> {
                androidx.compose.material3.lightColorScheme(
                    primary = PastelPrimary, onPrimary = PastelOnPrimary,
                    primaryContainer = PastelPrimaryContainer, onPrimaryContainer = PastelOnPrimaryContainer,
                    secondary = PastelSecondary, onSecondary = PastelOnSecondary,
                    secondaryContainer = PastelSecondaryContainer, onSecondaryContainer = PastelOnSecondaryContainer,
                    background = PastelBackground, onBackground = PastelOnBackground,
                    surface = PastelSurface, onSurface = PastelOnSurface,
                    outline = PastelOutline, outlineVariant = PastelOutlineVariant
                )
            }
            AppTheme.COUNTRY -> {
                androidx.compose.material3.lightColorScheme(
                    primary = CountryPrimary, onPrimary = CountryOnPrimary,
                    primaryContainer = CountryPrimaryContainer, onPrimaryContainer = CountryOnPrimaryContainer,
                    secondary = CountrySecondary, onSecondary = CountryOnSecondary,
                    secondaryContainer = CountrySecondaryContainer, onSecondaryContainer = CountryOnSecondaryContainer,
                    background = CountryBackground, onBackground = CountryOnBackground,
                    surface = CountrySurface, onSurface = CountryOnSurface,
                    outline = CountryOutline, outlineVariant = CountryOutlineVariant
                )
            }
            AppTheme.CUSTOM -> {
                val p = androidx.compose.ui.graphics.Color(customColors["primary"] ?: LightPrimary.toArgb())
                val s = androidx.compose.ui.graphics.Color(customColors["secondary"] ?: LightSecondary.toArgb())
                val b = androidx.compose.ui.graphics.Color(customColors["background"] ?: LightBackground.toArgb())
                val sur = androidx.compose.ui.graphics.Color(customColors["surface"] ?: LightSurface.toArgb())
                
                val onP = if (isCustomDark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                val onB = if (isCustomDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                val onSur = if (isCustomDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                
                if (isCustomDark) {
                    androidx.compose.material3.darkColorScheme(
                        primary = p, onPrimary = onP,
                        primaryContainer = p.copy(alpha = 0.3f), onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
                        secondary = s, onSecondary = onP,
                        secondaryContainer = s.copy(alpha = 0.3f), onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
                        background = b, onBackground = onB,
                        surface = sur, onSurface = onSur,
                    )
                } else {
                    androidx.compose.material3.lightColorScheme(
                        primary = p, onPrimary = onP,
                        primaryContainer = p.copy(alpha = 0.3f), onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
                        secondary = s, onSecondary = onP,
                        secondaryContainer = s.copy(alpha = 0.3f), onSecondaryContainer = androidx.compose.ui.graphics.Color.Black,
                        background = b, onBackground = onB,
                        surface = sur, onSurface = onSur,
                    )
                }
            }
            AppTheme.SYSTEM -> throw IllegalStateException("Should be resolved")
        }

        val surfaceColor = colorScheme.surface.toArgb()
        val surfaceVariantColor = colorScheme.surfaceVariant.toArgb()
        val onSurfaceColor = colorScheme.onSurface.toArgb()
        val onSurfaceVariantColor = colorScheme.onSurfaceVariant.toArgb()
        val primaryColor = colorScheme.primary.toArgb()
        
        val primaryContainerColor = colorScheme.primaryContainer.toArgb()
        val onPrimaryContainerColor = colorScheme.onPrimaryContainer.toArgb()
        
        val secondaryContainerColor = colorScheme.secondaryContainer.toArgb()
        val onSecondaryContainerColor = colorScheme.onSecondaryContainer.toArgb()
        
        val tertiaryContainerColor = colorScheme.tertiaryContainer.toArgb()
        val onTertiaryContainerColor = colorScheme.onTertiaryContainer.toArgb()
        
        val errorContainerColor = colorScheme.errorContainer.toArgb()
        val onErrorContainerColor = colorScheme.onErrorContainer.toArgb()

        // Backgrounds
        views.setInt(R.id.widget_bg_image, "setColorFilter", surfaceColor)
        views.setInt(R.id.kosen_section_bg, "setColorFilter", surfaceVariantColor)
        views.setInt(R.id.station_section_bg, "setColorFilter", surfaceVariantColor)

        // Day Type Badge
        views.setInt(R.id.widget_day_type_bg, "setColorFilter", primaryContainerColor)
        views.setTextColor(R.id.widget_day_type, onPrimaryContainerColor)

        // Material 3 Dynamic Color Badges (Front, Back, East, West)
        // Front (表): Primary
        views.setInt(R.id.kosen_front_badge_bg, "setColorFilter", primaryContainerColor)
        views.setTextColor(R.id.kosen_front_badge_text, onPrimaryContainerColor)

        // Back (裏): Secondary
        views.setInt(R.id.kosen_back_badge_bg, "setColorFilter", secondaryContainerColor)
        views.setTextColor(R.id.kosen_back_badge_text, onSecondaryContainerColor)

        // East (東): Tertiary
        views.setInt(R.id.station_east_badge_bg, "setColorFilter", tertiaryContainerColor)
        views.setTextColor(R.id.station_east_badge_text, onTertiaryContainerColor)

        // West (西): Error/Accent
        views.setInt(R.id.station_west_badge_bg, "setColorFilter", errorContainerColor)
        views.setTextColor(R.id.station_west_badge_text, onErrorContainerColor)

        // Text Colors & Refresh Button Tint
        views.setTextColor(R.id.widget_title, onSurfaceColor)
        views.setTextColor(R.id.widget_update_time, onSurfaceVariantColor)
        views.setInt(R.id.widget_refresh, "setColorFilter", primaryColor)
        
        views.setTextColor(R.id.kosen_section_label, onSurfaceVariantColor)
        views.setTextColor(R.id.station_section_label, onSurfaceVariantColor)

        views.setTextColor(R.id.kosen_front_time, onSurfaceVariantColor)
        views.setTextColor(R.id.kosen_back_time, onSurfaceVariantColor)
        views.setTextColor(R.id.station_east_time, onSurfaceVariantColor)
        views.setTextColor(R.id.station_west_time, onSurfaceVariantColor)

        views.setTextColor(R.id.kosen_front_remaining, primaryColor)
        views.setTextColor(R.id.kosen_back_remaining, primaryColor)
        views.setTextColor(R.id.station_east_remaining, primaryColor)
        views.setTextColor(R.id.station_west_remaining, primaryColor)
    }

    private fun setBusTime(
        views: RemoteViews,
        timeViewId: Int,
        remainingViewId: Int,
        departure: BusDeparture?,
        currentHour: Int,
        currentMinute: Int
    ) {
        if (departure != null) {
            val timeStr = "(%02d:%02d)".format(departure.hour, departure.minute)
            val remaining = (departure.hour * 60 + departure.minute) - (currentHour * 60 + currentMinute)
            views.setTextViewText(timeViewId, timeStr)
            views.setTextViewText(remainingViewId, "あと ${remaining}分")
        } else {
            views.setTextViewText(timeViewId, "--:--")
            views.setTextViewText(remainingViewId, "本日終了")
        }
    }

    private fun scheduleUpdates(context: Context) {
        // Cancel first to avoid duplicate chains
        cancelUpdates(context)
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BusWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Check exact alarm permission on Android 12+
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BusWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_REFRESH = "com.webclass.plus.widget.ACTION_REFRESH_BUS"
        const val ACTION_FORCE_UPDATE_THEME = "com.webclass.plus.widget.ACTION_FORCE_UPDATE_THEME"
        const val UPDATE_INTERVAL_MS = 30_000L // 30 seconds
        const val ALARM_REQUEST_CODE = 1001
    }
}
