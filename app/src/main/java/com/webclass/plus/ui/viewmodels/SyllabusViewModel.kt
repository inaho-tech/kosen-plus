package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.webclass.plus.data.models.SyllabusDepartment
import com.webclass.plus.data.models.SyllabusDetail
import com.webclass.plus.data.models.SyllabusSubject
import com.webclass.plus.ui.screens.SyllabusPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

class SyllabusViewModel(application: Application) : AndroidViewModel(application) {
    sealed class SyllabusWebViewCommand {
        object Reload : SyllabusWebViewCommand()
        class LoadUrl(val url: String) : SyllabusWebViewCommand()
    }

    private val _webViewCommand = MutableSharedFlow<SyllabusWebViewCommand>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val webViewCommand: SharedFlow<SyllabusWebViewCommand> = _webViewCommand.asSharedFlow()

    fun triggerReload() {
        _webViewCommand.tryEmit(SyllabusWebViewCommand.Reload)
    }

    fun triggerLoadUrl(url: String) {
        _webViewCommand.tryEmit(SyllabusWebViewCommand.LoadUrl(url))
    }
    private val _currentPage = MutableStateFlow(SyllabusPage.DEPARTMENTS)
    val currentPage: StateFlow<SyllabusPage> = _currentPage.asStateFlow()

    private val _departments = MutableStateFlow<List<SyllabusDepartment>>(emptyList())
    val departments: StateFlow<List<SyllabusDepartment>> = _departments.asStateFlow()

    private val _subjects = MutableStateFlow<List<SyllabusSubject>>(emptyList())
    val subjects: StateFlow<List<SyllabusSubject>> = _subjects.asStateFlow()

    private val _syllabusDetail = MutableStateFlow<SyllabusDetail?>(null)
    val syllabusDetail: StateFlow<SyllabusDetail?> = _syllabusDetail.asStateFlow()

    private val _selectedDepartment = MutableStateFlow("")
    val selectedDepartment: StateFlow<String> = _selectedDepartment.asStateFlow()

    private val _selectedSubjectName = MutableStateFlow("")
    val selectedSubjectName: StateFlow<String> = _selectedSubjectName.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    // Whether we have any cached data at all (departments loaded at least once)
    val hasCachedData: Boolean
        get() = _departments.value.isNotEmpty()

    var lastResetTrigger = 0

    fun setCurrentPage(page: SyllabusPage) { _currentPage.value = page }
    fun setDepartments(deps: List<SyllabusDepartment>) { _departments.value = deps }
    fun setSubjects(subs: List<SyllabusSubject>) { _subjects.value = subs }
    fun setSyllabusDetail(detail: SyllabusDetail?) { _syllabusDetail.value = detail }
    fun setSelectedDepartment(name: String) { _selectedDepartment.value = name }
    fun setSelectedSubjectName(name: String) { _selectedSubjectName.value = name }
    fun setLoading(loading: Boolean) { _isLoading.value = loading }
    fun setHasError(error: Boolean) { _hasError.value = error }

    // Subject Filtering State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedRequirement = MutableStateFlow<String?>(null)
    val selectedRequirement: StateFlow<String?> = _selectedRequirement.asStateFlow()

    private val _selectedTargetYear = MutableStateFlow<String?>(null)
    val selectedTargetYear: StateFlow<String?> = _selectedTargetYear.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSelectedCategory(category: String?) { _selectedCategory.value = category }
    fun setSelectedRequirement(requirement: String?) { _selectedRequirement.value = requirement }
    fun setSelectedTargetYear(year: String?) { _selectedTargetYear.value = year }

    fun resetFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = null
        _selectedRequirement.value = null
        _selectedTargetYear.value = null
    }

    fun goBackFromDetail() {
        _currentPage.value = SyllabusPage.SUBJECTS
        _syllabusDetail.value = null
    }

    fun goBackFromSimulator() {
        _currentPage.value = SyllabusPage.DETAIL
    }

    fun goBackFromSubjects() {
        _currentPage.value = SyllabusPage.DEPARTMENTS
        _subjects.value = emptyList()
        resetFilters()
    }

    fun resetToDepartments() {
        _currentPage.value = SyllabusPage.DEPARTMENTS
        _subjects.value = emptyList()
        _syllabusDetail.value = null
        resetFilters()
    }
}
