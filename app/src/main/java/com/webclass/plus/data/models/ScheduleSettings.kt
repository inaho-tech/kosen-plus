package com.webclass.plus.data.models

data class ScheduleOption(
    val label: String,
    val value: String
)

data class ScheduleSettings(
    val years: List<ScheduleOption> = emptyList(),
    val semesters: List<ScheduleOption> = emptyList(),
    val currentYear: String = "",
    val currentSemester: String = ""
)
