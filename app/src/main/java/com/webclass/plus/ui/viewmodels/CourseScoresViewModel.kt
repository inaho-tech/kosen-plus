package com.webclass.plus.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.webclass.plus.data.models.ScorePageData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CourseScoresViewModel : ViewModel() {
    // Cache the scores payload by course ID so we don't reload when switching tabs
    private val _scoresCache = MutableStateFlow<Map<String, ScorePageData>>(emptyMap())
    val scoresCache: StateFlow<Map<String, ScorePageData>> = _scoresCache.asStateFlow()

    fun getScoresForCourse(courseId: String): ScorePageData? {
        return _scoresCache.value[courseId]
    }

    fun updateScores(courseId: String, data: ScorePageData) {
        val current = _scoresCache.value.toMutableMap()
        current[courseId] = data
        _scoresCache.value = current
    }
}
