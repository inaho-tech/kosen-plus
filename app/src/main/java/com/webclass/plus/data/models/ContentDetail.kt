package com.webclass.plus.data.models

data class ContentDetail(
    val title: String,
    val type: String,
    val description: String,
    val execStatusText: String,
    val isCurrentlyAvailable: Boolean = true,
    val infoItems: List<InfoItem> = emptyList(),
    val publicQrUrl: String? = null,
    val execUrl: String? = null,
    val tabs: List<TabInfo> = emptyList()
)

data class InfoItem(
    val label: String,
    val value: String
)

data class TabInfo(
    val label: String,
    val href: String,
    val isActive: Boolean = false
)
