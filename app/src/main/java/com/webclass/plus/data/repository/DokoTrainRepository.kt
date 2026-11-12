package com.webclass.plus.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.webclass.plus.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DokoTrainRepository(private val context: Context) {
    private val gson = Gson()
    private val stationCache = File(context.filesDir, "doko_train_stations.json")

    suspend fun loadStations(forceRefresh: Boolean = false): List<DokoTrainStation> = withContext(Dispatchers.IO) {
        if (!forceRefresh && stationCache.exists()) {
            runCatching { parseStations(stationCache.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        }
        runCatching {
            val json = get(STATION_MASTER_URL)
            val stations = parseStations(json)
            if (stations.isNotEmpty()) stationCache.writeText(json)
            stations
        }.getOrElse { error ->
            if (stationCache.exists()) parseStations(stationCache.readText()) else throw error
        }
    }

    suspend fun loadTimetable(station: DokoTrainStation, direction: DokoTrainDirection): DokoTrainTimetable = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/diagram/station/${station.dokoLineId}/${station.stationId}_${direction.path}.json"
        val root = gson.fromJson(get(url), JsonObject::class.java)
        val listType = object : TypeToken<List<DokoTrainDeparture>>() {}.type
        val scheduled: List<DokoTrainDeparture> = gson.fromJson(root.getAsJsonArray("ST_DIAGRAM"), listType) ?: emptyList()
        val liveByTrain = runCatching {
            val liveUrl = "$BASE_URL/departure_info/${station.dokoLineId}/${station.stationId}_${direction.path}.json"
            val liveRoot = gson.fromJson(get(liveUrl), JsonObject::class.java)
            val live: List<DokoTrainDeparture> = gson.fromJson(liveRoot.getAsJsonArray("ST_DIAGRAM"), listType) ?: emptyList()
            live.associateBy { it.trainId }
        }.getOrDefault(emptyMap())
        val departures = scheduled.asSequence()
            .filter { it.departureTime.isNotBlank() && it.departureEpoch > 0 && it.runType != 6 && it.cancelFlag != "D" }
            .map { item ->
                liveByTrain[item.trainId]?.let { live -> item.copy(liveDelay = live.liveDelay, liveCanceled = live.liveCanceled) } ?: item
            }
            .sortedBy { it.departureEpoch }
            .toList()
        DokoTrainTimetable(
            message = root.get("MESSAGE")?.asString.orEmpty(),
            announcedAt = root.get("announced_date")?.asString.orEmpty(),
            departures = departures
        )
    }

    suspend fun loadLineStatus(lineId: String): DokoTrainLineStatus = withContext(Dispatchers.IO) {
        val root = gson.fromJson(get("$BASE_URL/trainstatus/lines_status.json"), JsonObject::class.java)
        val status = root.getAsJsonObject("LINE_STATUS")?.getAsJsonObject(lineId)
        DokoTrainLineStatus(
            maxDelayMinutes = status?.get("MAX_DELAY_MINUTES")?.asInt ?: 0,
            statusCode = status?.get("LINE_STAT_TOTAL")?.asInt ?: 0,
            announcedAt = root.get("announced_date")?.asString.orEmpty()
        )
    }

    private fun parseStations(json: String): List<DokoTrainStation> {
        val root = gson.fromJson(json, JsonObject::class.java)
        val type = object : TypeToken<List<DokoTrainStation>>() {}.type
        return (gson.fromJson<List<DokoTrainStation>>(root.getAsJsonArray("data"), type) ?: emptyList())
            .filter { it.name.isNotBlank() && it.stationId.isNotBlank() && it.dokoLineId.isNotBlank() && it.serviceFlag == 1 }
            .distinctBy { "${it.dokoLineId}:${it.stationId}" }
            .sortedWith(compareBy({ it.name }, { it.dokoLineName }))
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "KosenPlus-Android/4.3 DokoTrain")
            connection.useCaches = false
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://doko-train.jp/json"
        private const val STATION_MASTER_URL = "$BASE_URL/station/dokotrain_station_master.json"
    }
}
