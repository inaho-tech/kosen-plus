package com.webclass.plus.data.models

enum class DayType {
    WEEKDAY,
    HOLIDAY;

    fun getLabel(): String {
        return when(this) {
            WEEKDAY -> "平日"
            HOLIDAY -> "休日"
        }
    }
}

enum class Direction {
    DOWN, // To Kosen (from Station)
    UP;   // To Station (from Kosen)

    fun getLabel(): String {
        return when(this) {
            DOWN -> "木更津駅 発"
            UP -> "高専 発"
        }
    }
    
    fun getDestinationLabel(): String {
        return when(this) {
            DOWN -> "清見台団地 方面"
            UP -> "木更津駅 方面"
        }
    }
}

enum class BusStop(val direction: Direction, val label: String) {
    KISARAZU_EAST(Direction.DOWN, "東口"),
    KISARAZU_WEST(Direction.DOWN, "西口"),
    KOSEN_FRONT(Direction.UP, "正門"),
    KOSEN_BACK(Direction.UP, "裏門");
}

data class BusDeparture(
    val hour: Int,
    val minute: Int,
    val busStop: BusStop? = null,
    val via: String? = null // e.g. "太田" or "高専" if needed, mostly unused for now
) : Comparable<BusDeparture> {
    override fun compareTo(other: BusDeparture): Int {
        if (this.hour != other.hour) {
            return this.hour - other.hour
        }
        if (this.minute != other.minute) {
            return this.minute - other.minute
        }
        // Secondary sort by bus stop name for stable ordering
        return (this.busStop?.name ?: "").compareTo(other.busStop?.name ?: "")
    }
    
    fun toTimeUserId(): Int {
        return hour * 60 + minute
    }

    override fun toString(): String {
        return "%02d:%02d".format(hour, minute)
    }
}
