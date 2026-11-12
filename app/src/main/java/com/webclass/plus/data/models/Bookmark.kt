package com.webclass.plus.data.models

data class Bookmark(
    val id: String,
    val title: String,
    val url: String,
    val category: String,
    val courseTitle: String,
    val detailUrl: String? = null,
    val availablePeriod: String? = null,
    val isDisabled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
