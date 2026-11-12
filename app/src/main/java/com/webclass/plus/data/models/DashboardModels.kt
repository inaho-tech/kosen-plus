package com.webclass.plus.data.models

data class AdminAnnouncement(
    val title: String,
    val url: String,
    val date: String,
    val sender: String,
    val messageId: String? = null
)

data class SurveyBlock(
    val title: String,
    val url: String,
    val count: Int
)

data class DashboardInfo(
    val announcements: List<AdminAnnouncement> = emptyList(),
    val surveys: List<SurveyBlock> = emptyList(),
    val unreadMessageCount: Int = 0,
    val messageUrl: String? = null,
    val portfolioUrl: String? = null
)
