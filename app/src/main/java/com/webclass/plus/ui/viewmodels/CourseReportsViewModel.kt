package com.webclass.plus.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.webclass.plus.data.models.ReportItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CourseReportsViewModel : ViewModel() {
    // Cache the reports payload by course ID so we don't reload when switching tabs/screens
    private val _reportsCache = MutableStateFlow<Map<String, List<ReportItem>>>(emptyMap())
    val reportsCache: StateFlow<Map<String, List<ReportItem>>> = _reportsCache.asStateFlow()

    fun getReportsForCourse(courseId: String): List<ReportItem>? {
        return _reportsCache.value[courseId]
    }

    fun updateReports(courseId: String, data: List<ReportItem>) {
        val current = _reportsCache.value.toMutableMap()
        current[courseId] = data
        _reportsCache.value = current
    }
}
