package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.webclass.plus.data.models.CourseFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CourseViewModel(application: Application) : AndroidViewModel(application) {
    // Cache map: courseId -> list of folders
    private val courseCache = mutableMapOf<String, List<CourseFolder>>()

    fun getCachedFolders(courseId: String): List<CourseFolder>? {
        return courseCache[courseId]
    }

    fun setCachedFolders(courseId: String, folders: List<CourseFolder>) {
        courseCache[courseId] = folders
    }
    
    fun hasCourse(courseId: String): Boolean {
        return courseCache.containsKey(courseId)
    }

    // Filtering State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _showOnlyValid = MutableStateFlow(false)
    val showOnlyValid: StateFlow<Boolean> = _showOnlyValid.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSelectedCategory(category: String?) { _selectedCategory.value = category }
    fun setShowOnlyValid(show: Boolean) { _showOnlyValid.value = show }
}
