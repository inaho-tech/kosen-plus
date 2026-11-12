package com.webclass.plus.data.models

data class ScorePageData(
    val page: ScorePageMeta,
    val categories: List<ScoreCategory>
)

data class ScorePageMeta(
    val courseId: String?,
    val summaryTitle: String?,
    val dateRange: DateRange?,
    val showdata: String?
)

data class DateRange(
    val start: String?,
    val end: String?
)

data class ScoreCategory(
    val name: String,
    val items: List<ScoreItem>,
    val total: ScoreTotal?
)

data class ScoreTotal(
    val my: Double?,
    val course: Double?
)

data class ScoreItem(
    val title: String,
    val contentUrl: String?,
    val score: Double?,
    val scoreTextRaw: String,
    val scoreUrl: String?,
    val courseAvg: Double?
) {
    val isAttempted: Boolean
        get() = scoreTextRaw != "未" && scoreTextRaw.isNotEmpty()
}
