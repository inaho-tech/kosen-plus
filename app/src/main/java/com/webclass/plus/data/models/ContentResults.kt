package com.webclass.plus.data.models

data class ContentResults(
    val title: String,
    val type: String,
    val tabs: List<TabInfo> = emptyList(),
    val columns: List<String> = emptyList(),
    val rows: List<List<ResultCell>> = emptyList()
)

data class ResultCell(
    val text: String,
    val href: String? = null
)
