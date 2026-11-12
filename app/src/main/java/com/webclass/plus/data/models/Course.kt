package com.webclass.plus.data.models

data class Course(
    val title: String,
    val href: String,
    val code: String,
    val isDueSoon: Boolean = false,
    val unreadCount: Int = 0,
    val messageCount: Int = 0
)

data class TimetableEntry(
    val day: String, // "月", "火", "水", "木", "金", "土", "日"
    val period: Int, // 1, 2, 3, 4, 5, 6
    val courseTitle: String,
    val courseCode: String,
    val url: String
)

data class HomeDataResult(
    val pageType: String? = null,
    val userName: String? = null,
    val courses: List<Course>? = null,
    val scheduleSettings: ScheduleSettings? = null,
    val dashboardInfo: DashboardInfo? = null,
    val timetable: List<TimetableEntry>? = null
)
