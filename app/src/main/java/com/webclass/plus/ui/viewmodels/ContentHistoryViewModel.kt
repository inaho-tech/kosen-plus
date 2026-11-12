package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.webclass.plus.data.models.ContentHistory
import com.webclass.plus.data.models.ContentResults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContentHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _historyData = MutableStateFlow<ContentHistory?>(null)
    val historyData: StateFlow<ContentHistory?> = _historyData.asStateFlow()

    private val _resultsData = MutableStateFlow<ContentResults?>(null)
    val resultsData: StateFlow<ContentResults?> = _resultsData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageType = MutableStateFlow("UNKNOWN")
    val pageType: StateFlow<String> = _pageType.asStateFlow()

    /** Tracks which URL's data we have cached so we skip reload on re-entry */
    private var cachedForUrl: String? = null

    fun hasCachedDataFor(url: String): Boolean = cachedForUrl == url && (_historyData.value != null || _resultsData.value != null)

    fun initIfNeeded(url: String) {
        if (cachedForUrl != url) {
            // New URL — reset and start fresh
            cachedForUrl = url
            _historyData.value = null
            _resultsData.value = null
            _isLoading.value = true
            _currentUrl.value = url
            _pageType.value = "UNKNOWN"
        }
        // Else: already have data for this URL, keep cached state
    }

    fun setHistoryData(data: ContentHistory) {
        _historyData.value = data
    }

    fun setResultsData(data: ContentResults) {
        _resultsData.value = data
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    fun setPageType(type: String) {
        _pageType.value = type
    }
}
