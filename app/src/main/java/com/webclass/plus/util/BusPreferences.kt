package com.webclass.plus.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.webclass.plus.data.models.BusStop
import com.webclass.plus.data.models.DayType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bus_settings")

class BusPreferences(private val context: Context) {

    companion object {
        val KEY_DAY_TYPE_MODE = stringPreferencesKey("day_type_mode")
        val KEY_DEFAULT_BUS_STOP = stringPreferencesKey("default_bus_stop")
    }

    enum class DayTypeMode {
        AUTO,
        MANUAL_WEEKDAY,
        MANUAL_HOLIDAY
    }

    // Day Type Mode Flow
    val dayTypeModeFlow: Flow<DayTypeMode> = context.dataStore.data.map { preferences ->
        val modeName = preferences[KEY_DAY_TYPE_MODE] ?: DayTypeMode.AUTO.name
        try {
            DayTypeMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            DayTypeMode.AUTO
        }
    }.distinctUntilChanged()

    suspend fun setDayTypeMode(mode: DayTypeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DAY_TYPE_MODE] = mode.name
        }
    }

    // Default Bus Stop Flow
    val defaultBusStopFlow: Flow<BusStop> = context.dataStore.data.map { preferences ->
        val stopName = preferences[KEY_DEFAULT_BUS_STOP] ?: BusStop.KISARAZU_WEST.name
        try {
            BusStop.valueOf(stopName)
        } catch (e: IllegalArgumentException) {
            BusStop.KISARAZU_WEST
        }
    }.distinctUntilChanged()

    suspend fun setDefaultBusStop(stop: BusStop) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_BUS_STOP] = stop.name
        }
    }
}
