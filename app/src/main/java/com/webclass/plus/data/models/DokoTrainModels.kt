package com.webclass.plus.data.models

import com.google.gson.annotations.SerializedName

data class DokoTrainStation(
    @SerializedName("LINE_ID") val lineId: String = "",
    @SerializedName("LINE_NAME") val lineName: String = "",
    @SerializedName("LINE_dokotrain_ID") val dokoLineId: String = "",
    @SerializedName("LINE_dokotrain_NAME") val dokoLineName: String = "",
    @SerializedName("STATION_ID") val stationId: String = "",
    @SerializedName("STATION_NAME_KANJI") val name: String = "",
    @SerializedName("STATION_NAME_KANA") val kana: String = "",
    @SerializedName("service_flag") val serviceFlag: Int = 0,
    @SerializedName("STAD_FLAG_UP") val upFlag: Int = 0,
    @SerializedName("STAD_FLAG_DOWN") val downFlag: Int = 0
) {
    val favoriteKey: String get() = "$dokoLineId:$stationId"
}

data class DokoTrainDeparture(
    @SerializedName("STD") val departureTime: String = "",
    @SerializedName("STA") val arrivalTime: String = "",
    @SerializedName("STD_TM") val departureEpoch: Long = 0,
    @SerializedName("TRAIN_ID") val trainId: String = "",
    @SerializedName("TRAIN_LCLID") val trainLocalId: String = "",
    @SerializedName("END_STATION") val destination: String = "",
    @SerializedName("TRAIN_NNAME") val trainName: String = "",
    @SerializedName("UNIT_INFO") val cars: String = "",
    @SerializedName("TRAIN_TYPE") val trainType: Int = 0,
    @SerializedName("EXPRESS_CODE") val expressCode: Int = 0,
    @SerializedName("RUN_TYPE") val runType: Int = 0,
    @SerializedName("CANCEL_FLAG") val cancelFlag: String = "",
    @SerializedName("SSPNSN_FLAG") val suspensionFlag: String = "",
    @SerializedName("ad_latency") val liveDelay: String? = null,
    @SerializedName("ad_canceled") val liveCanceled: String? = null
) {
    val isCancelled: Boolean
        get() = cancelFlag == "T" || suspensionFlag == "1" || liveCanceled == "1"

    val typeLabel: String
        get() = when {
            trainType == 3 -> "特急"
            trainType == 2 -> "急行"
            trainType == 1 && expressCode in 2..8 -> "快速"
            trainType == 1 -> "普通"
            else -> "列車"
        }
}

data class DokoTrainTimetable(
    val message: String = "",
    val announcedAt: String = "",
    val departures: List<DokoTrainDeparture> = emptyList()
)

data class DokoTrainLineStatus(
    val maxDelayMinutes: Int = 0,
    val statusCode: Int = 0,
    val announcedAt: String = ""
) {
    val label: String
        get() = when {
            maxDelayMinutes > 0 -> "最大${maxDelayMinutes}分の遅れ"
            statusCode == 0 -> "平常運転"
            else -> "運行情報あり"
        }
}

enum class DokoTrainDirection(val path: String, val label: String) {
    UP("up", "上り"),
    DOWN("down", "下り")
}
