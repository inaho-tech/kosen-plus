package com.webclass.plus.data.models

data class ContentHistory(
    val title: String,
    val type: String,
    val tabs: List<TabInfo> = emptyList(),
    val usageSummary: String,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)


