package com.webclass.plus.data.models

data class ReportPageData(
    val courseId: String,
    val reports: List<ReportItem>
)

data class ReportItem(
    val taskName: String,
    val taskUrl: String?,
    val questionNo: String,
    val reportText: String?,
    val reportFileName: String?,
    val reportFileUrl: String?,
    val correctedFileName: String?,
    val correctedFileUrl: String?,
    val comment: String?,
    val submissionDate: String,
    val result: String,
    val scoreText: String,
    val scoreUrl: String?
)
