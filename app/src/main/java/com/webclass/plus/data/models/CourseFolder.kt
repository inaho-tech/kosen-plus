package com.webclass.plus.data.models

data class CourseFolder(
    val id: String,
    val title: String,
    val items: List<CourseItem>
)

data class CourseItem(
    val id: String,
    val title: String,
    val url: String,
    val category: String,
    val isNew: Boolean = false,
    val isDisabled: Boolean = false,
    val detailUrl: String? = null,
    val historyUrl: String? = null,
    val usageCount: Int? = null,
    val availablePeriod: String? = null,
    val hasSubmitted: Boolean = false,
    val isUnread: Boolean = false
)
