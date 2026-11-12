package com.webclass.plus.data.models

data class AssignmentItem(
    val id: String,
    val title: String,
    val courseId: String,
    val courseTitle: String,
    val url: String,
    val category: String, // e.g., "レポート", "テスト", "アンケート"
    val availablePeriod: String?,
    val isNew: Boolean,
    val hasSubmitted: Boolean = false,
    val isUnread: Boolean = false,
    val deadlineMs: Long? = null
)
