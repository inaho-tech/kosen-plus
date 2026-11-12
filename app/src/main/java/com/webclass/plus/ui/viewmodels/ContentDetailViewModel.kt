package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.webclass.plus.data.models.ContentDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContentDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val _data = MutableStateFlow<ContentDetail?>(null)
    val data: StateFlow<ContentDetail?> = _data.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    /** Tracks which URL's data we have cached */
    private var cachedForUrl: String? = null

    fun hasCachedData(): Boolean = cachedForUrl != null && _data.value != null

    fun initIfNeeded(url: String) {
        if (cachedForUrl != url) {
            cachedForUrl = url
            _data.value = null
            _isLoading.value = true
            _currentUrl.value = url
        }
    }

    fun setData(detail: ContentDetail) {
        _data.value = detail
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    fun clearDataForReload() {
        _data.value = null
        _isLoading.value = true
    }
}
