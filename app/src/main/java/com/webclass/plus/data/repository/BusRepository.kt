package com.webclass.plus.data.repository

import com.webclass.plus.data.models.BusDeparture
import com.webclass.plus.data.models.BusStop
import com.webclass.plus.data.models.DayType
import com.webclass.plus.data.models.Direction
import java.util.Calendar

object BusRepository {

    // Helper to parser "HH:MM" strings
    private fun parseTimes(timesCsv: String, busStop: BusStop? = null): List<BusDeparture> {
        return timesCsv.split(",").mapNotNull { 
            val parts = it.trim().split(":")
            if (parts.size == 2) {
                BusDeparture(parts[0].toInt(), parts[1].toInt(), busStop = busStop)
            } else {
                null
            }
        }.sorted()
    }

    // --- Hardcoded Data ---
    
    // Holiday, Kisarazu Station East Exit, DOWN
    private val HOLIDAY_KISARAZU_EAST = parseTimes(
        "06:30,07:10,08:30,08:50,09:30,11:30,12:10,13:50,14:30,15:10,15:50,16:30,17:10,17:50,18:50,19:40,20:30",
        BusStop.KISARAZU_EAST
    )

    // Holiday, Kisarazu Station West Exit, DOWN
    private val HOLIDAY_KISARAZU_WEST = parseTimes(
        "07:50,08:10,09:10,09:50,10:30,11:10,11:50,12:30,13:10,14:10,15:30,16:12,16:50,18:30,19:15,20:15",
        BusStop.KISARAZU_WEST
    )

    // Weekday, Kisarazu Station East Exit, DOWN
    private val WEEKDAY_KISARAZU_EAST = parseTimes(
        "07:15,08:00,09:45,10:25,11:20,11:50,13:00,13:45,14:48,15:35,15:45,16:46,17:35,17:45,18:20,18:52,19:40,20:30,21:50",
        BusStop.KISARAZU_EAST
    )

    // Weekday, Kisarazu Station West Exit, DOWN
    private val WEEKDAY_KISARAZU_WEST = parseTimes(
        "06:25,06:45,07:05,07:30,07:51,08:08,08:20,08:25,08:37,09:05,09:30,10:30,11:10,12:08,12:25,13:05,13:25,14:05,14:25,15:10,16:01,16:25,17:00,17:27,18:00,18:25,19:15,20:15,21:00",
        BusStop.KISARAZU_WEST
    )

    // Holiday, Kosen Back Gate, UP
    private val HOLIDAY_KOSEN_BACK = parseTimes(
        "06:53,08:13,09:13,11:53,13:33,14:53,16:13,17:33,19:13,20:03,20:54",
        BusStop.KOSEN_BACK
    )

    // Holiday, Kosen Front Stop, UP
    private val HOLIDAY_KOSEN_FRONT = parseTimes(
        "07:32,08:32,08:52,09:32,09:52,10:12,10:52,11:32,12:12,12:32,12:52,14:12,14:32,15:32,15:52,16:32,16:52,17:12,18:12,18:52,19:42,20:37",
        BusStop.KOSEN_FRONT
    )

    // Weekday, Kosen Back Gate, UP
    private val WEEKDAY_KOSEN_BACK = parseTimes(
        "06:53,07:39,08:23,10:58,11:41,15:58,18:00,20:01,20:54",
        BusStop.KOSEN_BACK
    )

    // Weekday, Kosen Front Stop, UP
    private val WEEKDAY_KOSEN_FRONT = parseTimes(
        "06:42,07:02,07:25,07:52,08:17,08:30,08:42,08:47,09:09,09:29,09:51,10:07,10:49,11:29,12:07,12:29,12:47,13:29,13:47,14:07,14:27,14:47,15:07,15:29,16:07,16:29,16:47,17:04,17:22,17:49,18:07,18:23,18:47,19:08,19:34,20:37,21:22",
        BusStop.KOSEN_FRONT
    )

    // --- Accessors ---

    fun getTimetable(stop: BusStop, dayType: DayType): List<BusDeparture> {
        return when (stop) {
            BusStop.KISARAZU_EAST -> if (dayType == DayType.WEEKDAY) WEEKDAY_KISARAZU_EAST else HOLIDAY_KISARAZU_EAST
            BusStop.KISARAZU_WEST -> if (dayType == DayType.WEEKDAY) WEEKDAY_KISARAZU_WEST else HOLIDAY_KISARAZU_WEST
            BusStop.KOSEN_BACK -> if (dayType == DayType.WEEKDAY) WEEKDAY_KOSEN_BACK else HOLIDAY_KOSEN_BACK
            BusStop.KOSEN_FRONT -> if (dayType == DayType.WEEKDAY) WEEKDAY_KOSEN_FRONT else HOLIDAY_KOSEN_FRONT
        }
    }

    /**
     * Returns combined timetable for a direction (merging both stops), sorted by time.
     */
    fun getCombinedTimetable(direction: Direction, dayType: DayType): List<BusDeparture> {
        val stops = BusStop.values().filter { it.direction == direction }
        return stops.flatMap { stop -> getTimetable(stop, dayType) }.sorted()
    }

    /**
     * Determines the DayType based on current system time (simple logic).
     * Saturday(7) and Sunday(1) are HOLIDAY.
     * Note: Does not handle public holidays.
     */
    fun getAutoDayType(): DayType {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            DayType.HOLIDAY
        } else {
            DayType.WEEKDAY
        }
    }

    /**
     * Returns the next bus departure relative to the given time (HH, MM).
     * Returns null if no more buses today.
     */
    fun getNextBus(stop: BusStop, dayType: DayType, currentHour: Int, currentMinute: Int): BusDeparture? {
        val timetable = getTimetable(stop, dayType)
        val current = currentHour * 60 + currentMinute
        
        return timetable.firstOrNull { (it.hour * 60 + it.minute) > current }
    }

    /**
     * Returns the next bus departure for a direction (combined stops).
     */
    fun getNextBusForDirection(direction: Direction, dayType: DayType, currentHour: Int, currentMinute: Int): BusDeparture? {
        val timetable = getCombinedTimetable(direction, dayType)
        val current = currentHour * 60 + currentMinute
        return timetable.firstOrNull { (it.hour * 60 + it.minute) > current }
    }
    
    /**
    * Returns next 3 buses
    */
    fun getNextBuses(stop: BusStop, dayType: DayType, currentHour: Int, currentMinute: Int, count: Int = 3): List<BusDeparture> {
        val timetable = getTimetable(stop, dayType)
        val current = currentHour * 60 + currentMinute
        
        return timetable.filter { (it.hour * 60 + it.minute) > current }.take(count)
    }
}
