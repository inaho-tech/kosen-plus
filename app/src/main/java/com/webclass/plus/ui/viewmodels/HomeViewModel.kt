package com.webclass.plus.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.webclass.plus.data.models.Course
import com.webclass.plus.data.models.DashboardInfo
import com.webclass.plus.data.models.ScheduleSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.webclass.plus.util.HtmlFetcher
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    sealed class WebViewCommand {
        object Reload : WebViewCommand()
        class LoadUrl(val url: String) : WebViewCommand()
    }

    private val _webViewCommand = MutableSharedFlow<WebViewCommand>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val webViewCommand: SharedFlow<WebViewCommand> = _webViewCommand.asSharedFlow()

    fun triggerReload() {
        _webViewCommand.tryEmit(WebViewCommand.Reload)
    }

    fun triggerLoadUrl(url: String) {
        _webViewCommand.tryEmit(WebViewCommand.LoadUrl(url))
    }
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    private val _timetable = MutableStateFlow<List<com.webclass.plus.data.models.TimetableEntry>>(emptyList())
    val timetable: StateFlow<List<com.webclass.plus.data.models.TimetableEntry>> = _timetable.asStateFlow()

    private val _scheduleSettings = MutableStateFlow(ScheduleSettings())
    val scheduleSettings: StateFlow<ScheduleSettings> = _scheduleSettings.asStateFlow()

    private val _dashboardInfo = MutableStateFlow(DashboardInfo())
    val dashboardInfo: StateFlow<DashboardInfo> = _dashboardInfo.asStateFlow()

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: StateFlow<Int> = _unreadMessageCount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView.asStateFlow()

    private val _currentUrl = MutableStateFlow("https://webclass.kosen-k.go.jp/webclass/index.php")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _isOnMicrosoftLogin = MutableStateFlow(false)
    val isOnMicrosoftLogin: StateFlow<Boolean> = _isOnMicrosoftLogin.asStateFlow()

    // Flag to check if we have data cached so we don't need to show the loading screen
    private val _hasCachedData = MutableStateFlow(false)
    val hasCachedData: StateFlow<Boolean> = _hasCachedData.asStateFlow()

    var lastResetTrigger = 0

    fun updateCourses(newCourses: List<Course>) {
        _courses.value = newCourses
        _hasCachedData.value = true
    }

    fun updateTimetable(newTimetable: List<com.webclass.plus.data.models.TimetableEntry>) {
        _timetable.value = newTimetable
    }

    fun updateScheduleSettings(settings: ScheduleSettings) {
        _scheduleSettings.value = settings
        _hasCachedData.value = true
    }

    fun updateDashboardInfo(info: DashboardInfo) {
        _dashboardInfo.value = info
        _unreadMessageCount.value = info.unreadMessageCount.coerceAtLeast(0)
        _hasCachedData.value = true
    }

    fun refreshUnreadMessageCount() {
        viewModelScope.launch {
            val response = HtmlFetcher.fetchHtml(UNREAD_MESSAGE_COUNT_URL) ?: return@launch
            val count = runCatching {
                JsonParser.parseString(response)
                    .asJsonObject
                    .getAsJsonObject("responseData")
                    .get("totalRecords")
                    .asInt
            }.getOrNull() ?: return@launch

            _unreadMessageCount.value = count.coerceAtLeast(0)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setShowWebView(show: Boolean) {
        _showWebView.value = show
    }

    fun setCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    fun setOnMicrosoftLogin(isOnLogin: Boolean) {
        _isOnMicrosoftLogin.value = isOnLogin
    }

    fun resetState() {
        _isLoading.value = true
        _hasCachedData.value = false
        _showWebView.value = false
        _isOnMicrosoftLogin.value = false
        _courses.value = emptyList()
        _timetable.value = emptyList()
        _scheduleSettings.value = ScheduleSettings()
        _dashboardInfo.value = DashboardInfo()
        _unreadMessageCount.value = 0
        _currentUrl.value = "https://webclass.kosen-k.go.jp/webclass/index.php"
    }

    fun setMockData() {
        _isLoading.value = false
        _hasCachedData.value = true
        _showWebView.value = false
        _isOnMicrosoftLogin.value = false
        
        _courses.value = listOf(
            Course("システム設計論 (Mock)", "/webclass/course.php?id=AD101", "AD101", unreadCount = 3),
            Course("ネットワーク工学 (Mock)", "/webclass/course.php?id=AD102", "AD102", unreadCount = 0),
            Course("データベース演習 (Mock)", "/webclass/course.php?id=AD103", "AD103", isDueSoon = true, unreadCount = 2),
            Course("人工知能概論 (Mock)", "/webclass/course.php?id=AD104", "AD104", unreadCount = 0)
        )
        
        _scheduleSettings.value = ScheduleSettings(
            years = listOf(
                com.webclass.plus.data.models.ScheduleOption("2024年度", "2024"),
                com.webclass.plus.data.models.ScheduleOption("2023年度", "2023")
            ),
            semesters = listOf(
                com.webclass.plus.data.models.ScheduleOption("前期", "1"),
                com.webclass.plus.data.models.ScheduleOption("後期", "2")
            ),
            currentYear = "2024",
            currentSemester = "1"
        )
        
        _dashboardInfo.value = DashboardInfo(
            announcements = listOf(
                com.webclass.plus.data.models.AdminAnnouncement("システムメンテナンスのお知らせ (Mock)", "#", "2024/05/01", "管理者"),
                com.webclass.plus.data.models.AdminAnnouncement("授業休講のお知らせ (Mock)", "#", "2024/04/20", "教務係")
            ),
            surveys = listOf(
                com.webclass.plus.data.models.SurveyBlock("授業評価アンケート (Mock)", "#", 1)
            ),
            unreadMessageCount = 2,
            portfolioUrl = "https://webclass.kosen-k.go.jp/webclass/ip_mods.php/plugin/shugaku_carte/carte/mypage?proto_carte_id=C14_Portfolio_D"
        )
        _unreadMessageCount.value = 2

        _timetable.value = listOf(
            com.webclass.plus.data.models.TimetableEntry("月", 1, "システム設計論 (Mock)", "AD101", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD101"),
            com.webclass.plus.data.models.TimetableEntry("月", 2, "ネットワーク工学 (Mock)", "AD102", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD102"),
            com.webclass.plus.data.models.TimetableEntry("火", 3, "データベース演習 (Mock)", "AD103", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD103"),
            com.webclass.plus.data.models.TimetableEntry("水", 1, "人工知能概論 (Mock)", "AD104", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD104"),
            com.webclass.plus.data.models.TimetableEntry("水", 2, "システム設計論 (Mock)", "AD101", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD101"),
            com.webclass.plus.data.models.TimetableEntry("木", 4, "データベース演習 (Mock)", "AD103", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD103"),
            com.webclass.plus.data.models.TimetableEntry("金", 2, "ネットワーク工学 (Mock)", "AD102", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD102"),
            com.webclass.plus.data.models.TimetableEntry("金", 3, "人工知能概論 (Mock)", "AD104", "https://webclass.kosen-k.go.jp/webclass/course.php?id=AD104")
        )
    }

    private companion object {
        const val UNREAD_MESSAGE_COUNT_URL =
            "https://webclass.kosen-k.go.jp/webclass/messages.php/api/count?ft[unread]=1&ft[not_course]=1"
    }
}
