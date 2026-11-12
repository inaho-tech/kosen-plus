package com.webclass.plus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.webclass.plus.ui.components.*
import com.webclass.plus.ui.screens.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.size
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webclass.plus.data.models.*
import com.webclass.plus.data.repository.UserDataRepository
import com.webclass.plus.util.JavaScriptBridge
import com.webclass.plus.util.HtmlFetcher
import com.webclass.plus.util.WebClassHtmlParser
import com.webclass.plus.ui.screens.SyllabusPage
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow

private const val SYLLABUS_DEPARTMENTS_URL = "https://syllabus.kosen-k.go.jp/Pages/PublicDepartments?school_id=14&lang=ja"

/**
 * Main navigation graph for the app
 */
@Composable
fun NavGraph(startDestination: String = "splash") {
    val navController = rememberNavController()
    var showBottomBar by remember { mutableStateOf(true) }
    var homeResetTrigger by remember { mutableStateOf(0) }
    var syllabusResetTrigger by remember { mutableStateOf(0) }
    var toolsTabClickTrigger by remember { mutableStateOf(0) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    
    // Hoisted states for ToolsScreen
    var isOthelloPlaying by rememberSaveable { mutableStateOf(false) }
    var isGomokuPlaying by rememberSaveable { mutableStateOf(false) }
    var isAirHockeyPlaying by rememberSaveable { mutableStateOf(false) }
    var isMineSweeperPlaying by rememberSaveable { mutableStateOf(false) }
    var isFlappySkyPlaying by rememberSaveable { mutableStateOf(false) }
    var showToolsExitConfirmation by remember { mutableStateOf(false) }
    var toolsAllowsNavigationOverlay by remember { mutableStateOf(true) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val useBrowser by themePreferences.useBrowserFlow.collectAsState(initial = false)
    val showBookmarks by themePreferences.enableBookmarksFlow.collectAsState(initial = false)
    val enableHyperAnimation by themePreferences.enableHyperAnimationFlow.collectAsState(initial = false)
    val enableHyperBootAnimation by themePreferences.enableHyperBootAnimationFlow.collectAsState(initial = false)
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    val hyperAnimationStrength by themePreferences.hyperAnimationStrengthFlow.collectAsState(initial = 1.0f)
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val wallpaperBackgroundAlpha by themePreferences.wallpaperBackgroundAlphaFlow.collectAsState(initial = 0.55f)
    val springDampingEnter = (0.8f - hyperAnimationStrength * 0.4f).coerceIn(0.15f, 0.8f)
    val springStiffnessEnter = (200f / hyperAnimationStrength).coerceIn(50f, 800f)
    val springDampingExit = (0.9f - hyperAnimationStrength * 0.4f).coerceIn(0.2f, 0.9f)
    val springStiffnessExit = (400f / hyperAnimationStrength).coerceIn(100f, 1500f)
    
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    
    val activityContext = context as androidx.activity.ComponentActivity
    val homeViewModel: com.webclass.plus.ui.viewmodels.HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activityContext)
    val syllabusViewModel: com.webclass.plus.ui.viewmodels.SyllabusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activityContext)
    val toolsViewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activityContext)
    
    val showWebView by homeViewModel.showWebView.collectAsState()
    val currentUrl by homeViewModel.currentUrl.collectAsState()
    val isOnMicrosoftLogin by homeViewModel.isOnMicrosoftLogin.collectAsState()
    
    val hasCachedData by homeViewModel.hasCachedData.collectAsState()
    val isMockMode by developerPreferences.mockModeEnabledFlow.collectAsState(initial = false)
    val syllabusDepartments by syllabusViewModel.departments.collectAsState()
    var webClassWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var syllabusWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var shouldRunSyllabusWebView by remember { mutableStateOf(false) }
    var pendingSyllabusUrl by remember { mutableStateOf<String?>(null) }
    var lastSyllabusRequestedUrl by remember { mutableStateOf<String?>(null) }
    var syllabusLoadGeneration by remember { mutableStateOf(0) }
    var hasRequestedSyllabusWarmup by remember { mutableStateOf(false) }
    val hasSeenWelcomeScreen by themePreferences.hasSeenWelcomeScreenFlow.collectAsState(initial = false)
    val alwaysShowWelcomeScreen by developerPreferences.alwaysShowWelcomeScreenFlow.collectAsState(initial = false)
    val isLoading by homeViewModel.isLoading.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val userDataRepository = remember { UserDataRepository(context) }
    var loginDetectedTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    val gson = remember { Gson() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    
    // Determine the selected route for navigation highlighting
    val selectedRoute = when {
        currentRoute.startsWith("course") -> "home"
        currentRoute.startsWith("viewer") -> {
            navBackStackEntry?.arguments?.getString("origin") ?: "home"
        }
        currentRoute.startsWith("detail") -> "home"
        currentRoute.startsWith("history") -> "home"
        currentRoute.startsWith("results") -> "home"
        currentRoute.startsWith("scores") -> "home"
        currentRoute.startsWith("reports") -> "home"
        currentRoute.startsWith("timetable") -> "home"
        currentRoute.startsWith("bus") -> "bus"
        currentRoute.startsWith("tools") -> "tools"
        currentRoute.startsWith("settings") -> "user"
        currentRoute.startsWith("theme_settings") -> "user"
        currentRoute.startsWith("info") -> "user"
        currentRoute.startsWith("developer_options") -> "user"
        currentRoute.startsWith("widget_settings") -> "user"
        currentRoute.startsWith("experimental_features") -> "user"
        currentRoute.startsWith("theme_editor") -> "user"
        currentRoute.startsWith("app_theme_editor") -> "user"
        else -> currentRoute
    }

    val isStudyTabActive = selectedRoute == "home"

    val isLoginVisible = showWebView && isStudyTabActive

    val enableSpeedUp by themePreferences.enableSpeedUpFlow.collectAsState(initial = false)
    val shouldSkipWebClassBgLoad = (!hasSeenWelcomeScreen || alwaysShowWelcomeScreen || enableSpeedUp)
    val webClassInitialUrl = if (shouldSkipWebClassBgLoad) "about:blank" else currentUrl
    val isStudyHomeVisible = currentRoute == "home" && hasCachedData && !isLoading && !showWebView

    // 笏笏 Fetch Helper Functions for Fast Mode 笏笏
    fun performHomeFetch(url: String) {
        scope.launch {
            homeViewModel.setLoading(true)
            // Fetch with a 15-second timeout (same as normal mode)
            val html = withTimeoutOrNull(15_000L) { HtmlFetcher.fetchHtml(url) } ?: run {
                // Timeout: hand off to background WebView - same login flow as normal mode
                android.util.Log.w("WebClass", "Home fetch timed out, handing off to WebView")
                homeViewModel.setLoading(false)
                webClassWebViewRef?.loadUrl(url)
                return@launch
            }
            try {
                val dataResult = WebClassHtmlParser.parseHomeData(html, "https://webclass.kosen-k.go.jp")
                val pageType = dataResult.pageType ?: "UNKNOWN"
                android.util.Log.d("WebClass", "Fetched and parsed pageType: $pageType on $url")
                
                dataResult.courses?.let { extractedCourses ->
                    homeViewModel.updateCourses(extractedCourses.distinctBy { it.code })
                }
                
                dataResult.timetable?.let { extractedTimetable ->
                    homeViewModel.updateTimetable(extractedTimetable)
                }
                
                dataResult.userName?.let { userName ->
                    val cleanUserName = userName.trim()
                    if (cleanUserName.isNotEmpty() && 
                        cleanUserName != "WebClass ユーザー" && 
                        !cleanUserName.contains("言語") && 
                        !cleanUserName.lowercase().contains("language")) {
                        userDataRepository.setUserName(cleanUserName)
                    }
                }
                
                dataResult.scheduleSettings?.let { settings ->
                    homeViewModel.updateScheduleSettings(settings)
                }
                
                dataResult.dashboardInfo?.let { info ->
                    homeViewModel.updateDashboardInfo(info)
                }
                
                homeViewModel.setShowWebView(false)
                homeViewModel.setOnMicrosoftLogin(false)
                homeViewModel.setLoading(false)
                return@launch
            } catch (e: Exception) {
                android.util.Log.e("WebClass", "Error parsing home data via fetch: $e")
            }
            
            // Fallback: hand off to background WebView without showing it
            // Normal login detection (isOnMicrosoftLogin + 5s delay) will show it when ready
            android.util.Log.d("WebClass", "Fetch failed, handing off to background WebView for $url")
            homeViewModel.setLoading(false)
            webClassWebViewRef?.loadUrl(url)
        }
    }

    fun performSyllabusFetch(url: String) {
        scope.launch {
            syllabusViewModel.setLoading(true)
            syllabusViewModel.setHasError(false)
            // Fetch with a 15-second timeout (same as normal mode)
            val html = withTimeoutOrNull(15_000L) { HtmlFetcher.fetchHtml(url) } ?: run {
                // Timeout: hand off to background WebView - same timing as normal mode
                android.util.Log.w("WebClass", "Syllabus fetch timed out, handing off to WebView")
                syllabusViewModel.setLoading(false)
                shouldRunSyllabusWebView = true
                syllabusWebViewRef?.loadUrl(url)
                return@launch
            }
            try {
                val doc = org.jsoup.Jsoup.parse(html, url)
                val pageType = when {
                    doc.selectFirst("#MainContent_SubjectSyllabus_UpdatePanelSyllabus") != null -> "syllabus"
                    doc.select(".subject-item").isNotEmpty() -> "subjects"
                    doc.select("h4.list-group-item-heading").size > 1 -> "departments"
                    else -> "unknown"
                }

                android.util.Log.d("WebClass", "Fetched syllabus pageType: $pageType on $url")
                when (pageType) {
                    "departments" -> {
                        val depts = WebClassHtmlParser.parseSyllabusDepartments(html, url)
                        if (depts.isNotEmpty()) {
                            syllabusViewModel.setDepartments(depts)
                            syllabusViewModel.setCurrentPage(SyllabusPage.DEPARTMENTS)
                            syllabusViewModel.setLoading(false)
                            syllabusViewModel.setHasError(false)
                            return@launch
                        }
                    }
                    "subjects" -> {
                        val subjs = WebClassHtmlParser.parseSyllabusSubjects(html, url)
                        syllabusViewModel.setSubjects(subjs)
                        syllabusViewModel.setCurrentPage(SyllabusPage.SUBJECTS)
                        syllabusViewModel.setLoading(false)
                        syllabusViewModel.setHasError(false)
                        return@launch
                    }
                    "syllabus" -> {
                        val detail = WebClassHtmlParser.parseSyllabusDetail(html, url)
                        syllabusViewModel.setSyllabusDetail(detail)
                        syllabusViewModel.setCurrentPage(SyllabusPage.DETAIL)
                        syllabusViewModel.setLoading(false)
                        syllabusViewModel.setHasError(false)
                        return@launch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WebClass", "Syllabus parse error via fetch: $e")
            }
            
            // Fallback to WebView
            android.util.Log.d("WebClass", "Syllabus fetch failed, falling back to WebView loadUrl for $url")
            shouldRunSyllabusWebView = true
            syllabusWebViewRef?.loadUrl(url)
        }
    }

    fun requestSyllabusUrl(url: String) {
        if (url.isBlank()) return
        scope.launch {
            val speedUp = themePreferences.enableSpeedUpFlow.first()
            if (speedUp) {
                performSyllabusFetch(url)
            } else {
                shouldRunSyllabusWebView = true
                pendingSyllabusUrl = url
                lastSyllabusRequestedUrl = url
                syllabusLoadGeneration += 1
            }
        }
    }

    fun isExpectedSyllabusPage(finishedUrl: String, requestedUrl: String?): Boolean {
        if (requestedUrl.isNullOrBlank()) return true
        val finished = finishedUrl.substringBefore("#")
        val requested = requestedUrl.substringBefore("#")
        if (finished == requested) return true
        val requestedPage = listOf("PublicDepartments", "PublicSubjects", "PublicSyllabus")
            .firstOrNull { requested.contains(it, ignoreCase = true) }
        return requestedPage == null ||
            (requestedPage == "PublicDepartments" && finished.contains(requestedPage, ignoreCase = true))
    }

    // Helper to perform the home navigation/reset
    val navigateToHomeWithReset = {
        homeResetTrigger++
        navController.navigate("home") {
            popUpTo("home") {
                inclusive = false
                saveState = false // Force fresh state
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    // Update check on app startup
    var updateInfo by remember { mutableStateOf<com.webclass.plus.util.UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var hasCheckedUpdate by remember { mutableStateOf(false) }
    
    // Initial launch load for Fast Mode
    androidx.compose.runtime.LaunchedEffect(hasSeenWelcomeScreen, alwaysShowWelcomeScreen) {
        val speedUp = themePreferences.enableSpeedUpFlow.first()
        if (speedUp && hasSeenWelcomeScreen && !alwaysShowWelcomeScreen) {
            performHomeFetch("https://webclass.kosen-k.go.jp/webclass/index.php")
        }
    }

    // 笏笏 Command flow collectors for background WebViews 笏笏
    androidx.compose.runtime.LaunchedEffect(Unit) {
        homeViewModel.webViewCommand.collect { command ->
            val speedUp = themePreferences.enableSpeedUpFlow.first()
            when (command) {
                is com.webclass.plus.ui.viewmodels.HomeViewModel.WebViewCommand.Reload -> {
                    if (speedUp) {
                        performHomeFetch("https://webclass.kosen-k.go.jp/webclass/index.php")
                    } else {
                        webClassWebViewRef?.reload()
                    }
                }
                is com.webclass.plus.ui.viewmodels.HomeViewModel.WebViewCommand.LoadUrl -> {
                    if (speedUp) {
                        performHomeFetch(command.url)
                    } else {
                        webClassWebViewRef?.loadUrl(command.url)
                    }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        syllabusViewModel.webViewCommand.collect { command ->
            val speedUp = themePreferences.enableSpeedUpFlow.first()
            when (command) {
                is com.webclass.plus.ui.viewmodels.SyllabusViewModel.SyllabusWebViewCommand.Reload -> {
                    if (speedUp) {
                        performSyllabusFetch(lastSyllabusRequestedUrl ?: SYLLABUS_DEPARTMENTS_URL)
                    } else {
                        shouldRunSyllabusWebView = true
                        syllabusLoadGeneration += 1
                        val webView = syllabusWebViewRef
                        if (webView != null && webView.url != null && webView.url != "about:blank") {
                            webView.reload()
                        } else {
                            requestSyllabusUrl(lastSyllabusRequestedUrl ?: SYLLABUS_DEPARTMENTS_URL)
                        }
                    }
                }
                is com.webclass.plus.ui.viewmodels.SyllabusViewModel.SyllabusWebViewCommand.LoadUrl -> {
                    if (speedUp) {
                        performSyllabusFetch(command.url)
                    } else {
                        requestSyllabusUrl(command.url)
                    }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        isStudyHomeVisible,
        syllabusDepartments.isEmpty(),
        isMockMode
    ) {
        if (!isMockMode && isStudyHomeVisible && syllabusDepartments.isEmpty() && !hasRequestedSyllabusWarmup) {
            hasRequestedSyllabusWarmup = true
            syllabusViewModel.setHasError(false)
            syllabusViewModel.setLoading(true)
            requestSyllabusUrl(SYLLABUS_DEPARTMENTS_URL)
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        currentRoute,
        syllabusDepartments.isEmpty(),
        isMockMode
    ) {
        if (!isMockMode && currentRoute == "syllabus" && syllabusDepartments.isEmpty()) {
            syllabusViewModel.setHasError(false)
            syllabusViewModel.setLoading(true)
            requestSyllabusUrl(SYLLABUS_DEPARTMENTS_URL)
        }
    }

    // 笏笏 Microsoft Login overlay detection logic 笏笏
    androidx.compose.runtime.LaunchedEffect(isOnMicrosoftLogin, currentRoute) {
        if (isOnMicrosoftLogin && currentRoute != "splash") {
            loginDetectedTime = System.currentTimeMillis()
            delay(5000)
            if (isOnMicrosoftLogin && currentRoute != "splash") {
                homeViewModel.setShowWebView(true)
                homeViewModel.setLoading(false)
            }
        }
    }

    // 笏笏 Syllabus extraction helper functions 笏笏
    fun evaluateSyllabusJs(js: String, callback: (String) -> Unit) {
        val webView = syllabusWebViewRef
        if (webView == null) {
            callback("")
            return
        }
        webView.evaluateJavascript(js) { result ->
            val cleaned = result?.let {
                if (it.startsWith("\"") && it.endsWith("\"")) {
                    it.substring(1, it.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\\\", "\\")
                } else it
            } ?: ""
            callback(cleaned)
        }
    }

    fun extractSyllabusCurrentPage(
        loadGeneration: Int,
        commitFailure: Boolean,
        onResult: (Boolean) -> Unit = {}
    ) {
        fun isStaleLoad() = loadGeneration != syllabusLoadGeneration
        fun fail() {
            if (!isStaleLoad() && commitFailure) {
                syllabusViewModel.setLoading(false)
                syllabusViewModel.setHasError(true)
            }
            onResult(false)
        }

        evaluateSyllabusJs(com.webclass.plus.util.SyllabusJsBridge.detectSyllabusPageType()) { typeJson ->
            if (isStaleLoad()) {
                onResult(false)
                return@evaluateSyllabusJs
            }
            try {
                val typeMap = gson.fromJson<Map<String, String>>(typeJson, object : TypeToken<Map<String, String>>() {}.type)
                val pageType = typeMap["pageType"] ?: "unknown"

                if (pageType == "unknown") {
                    fail()
                    return@evaluateSyllabusJs
                }

                when (pageType) {
                    "departments" -> {
                        evaluateSyllabusJs(com.webclass.plus.util.SyllabusJsBridge.extractDepartments()) { deptJson ->
                            if (isStaleLoad()) {
                                onResult(false)
                                return@evaluateSyllabusJs
                            }
                            try {
                                val parsed = gson.fromJson<Map<String, List<SyllabusDepartment>>>(
                                    deptJson, object : TypeToken<Map<String, List<SyllabusDepartment>>>() {}.type
                                )
                                val departments = parsed["departments"] ?: emptyList()
                                if (departments.isEmpty()) {
                                    fail()
                                    return@evaluateSyllabusJs
                                }
                                syllabusViewModel.setDepartments(departments)
                                syllabusViewModel.setCurrentPage(SyllabusPage.DEPARTMENTS)
                                syllabusViewModel.setLoading(false)
                                syllabusViewModel.setHasError(false)
                                onResult(true)
                            } catch (e: Exception) {
                                fail()
                            }
                        }
                    }
                    "subjects" -> {
                        evaluateSyllabusJs(com.webclass.plus.util.SyllabusJsBridge.extractSubjects()) { subjJson ->
                            if (isStaleLoad()) {
                                onResult(false)
                                return@evaluateSyllabusJs
                            }
                            try {
                                val parsed = gson.fromJson<Map<String, List<SyllabusSubject>>>(
                                    subjJson, object : TypeToken<Map<String, List<SyllabusSubject>>>() {}.type
                                )
                                syllabusViewModel.setSubjects(parsed["subjects"] ?: emptyList())
                                syllabusViewModel.setCurrentPage(SyllabusPage.SUBJECTS)
                                syllabusViewModel.setLoading(false)
                                syllabusViewModel.setHasError(false)
                                onResult(true)
                            } catch (e: Exception) {
                                fail()
                            }
                        }
                    }
                    "syllabus" -> {
                        evaluateSyllabusJs(com.webclass.plus.util.SyllabusJsBridge.extractSyllabusDetail()) { detailJson ->
                            if (isStaleLoad()) {
                                onResult(false)
                                return@evaluateSyllabusJs
                            }
                            try {
                                syllabusViewModel.setSyllabusDetail(gson.fromJson(detailJson, SyllabusDetail::class.java))
                                syllabusViewModel.setCurrentPage(SyllabusPage.DETAIL)
                                syllabusViewModel.setLoading(false)
                                syllabusViewModel.setHasError(false)
                                onResult(true)
                            } catch (e: Exception) {
                                fail()
                            }
                        }
                    }
                    else -> fail()
                }
            } catch (e: Exception) {
                fail()
            }
        }
    }

    fun extractSyllabusCurrentPageWithRetry(
        retriesLeft: Int = 12,
        delayMs: Long = 120L,
        loadGeneration: Int = syllabusLoadGeneration
    ) {
        if (loadGeneration != syllabusLoadGeneration) return
        extractSyllabusCurrentPage(loadGeneration, commitFailure = retriesLeft == 0) { success ->
            if (!success && retriesLeft > 0 && loadGeneration == syllabusLoadGeneration) {
                scope.launch {
                    delay(delayMs)
                    extractSyllabusCurrentPageWithRetry(retriesLeft - 1, delayMs, loadGeneration)
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasCheckedUpdate) {
            hasCheckedUpdate = true
            scope.launch {
                val isCustom = developerPreferences.customUpdateSourceEnabledFlow.first()
                val customUrl = developerPreferences.customUpdateUrlFlow.first()
                val urlToUse = if (isCustom) customUrl else null
                val isSpoofed = developerPreferences.spoofVersionEnabledFlow.first()
                val spoofVer = developerPreferences.spoofVersionFlow.first()
                val versionToUse = if (isSpoofed) spoofVer else null
                
                val info = com.webclass.plus.util.UpdateChecker.checkForUpdates(urlToUse, versionToUse)
                if (info?.isUpdateAvailable == true) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { androidx.compose.material3.Text("アップデートがあります") },
            text = { 
                androidx.compose.material3.Text(
                    "新しいバージョン ${updateInfo?.latestVersion} が利用可能です。\n現在のバージョン: ${com.webclass.plus.util.UpdateChecker.CURRENT_VERSION}"
                ) 
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showUpdateDialog = false
                        val targetUrl = updateInfo?.downloadUrl ?: com.webclass.plus.util.UpdateChecker.UPDATE_PAGE_URL
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                        context.startActivity(intent)
                    }
                ) {
                    androidx.compose.material3.Text("アップデート")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) {
                    androidx.compose.material3.Text("閉じる")
                }
            }
        )
    }

    // 笏笏 What's New dialog (shown on first launch after update) 笏笏
    var showWhatsNew by remember { mutableStateOf(false) }
    var whatsNewEntries by remember { mutableStateOf<List<com.webclass.plus.data.VersionEntry>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val lastSeen = themePreferences.lastSeenVersionFlow.first()
        val currentVersion = com.webclass.plus.data.VersionHistory.latest.version
        val alwaysShowWhatsNew = developerPreferences.alwaysShowWhatsNewFlow.first()
        if (alwaysShowWhatsNew || lastSeen != currentVersion) {
            val newEntries = if (alwaysShowWhatsNew) {
                listOf(com.webclass.plus.data.VersionHistory.latest)
            } else {
                com.webclass.plus.data.VersionHistory.changesSince(lastSeen)
            }
            if (newEntries.isNotEmpty()) {
                whatsNewEntries = newEntries
                showWhatsNew = true
            }
        }
    }

    if (showWhatsNew && whatsNewEntries.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showWhatsNew = false
                scope.launch { themePreferences.setLastSeenVersion(com.webclass.plus.data.VersionHistory.latest.version) }
            },
            title = {
                    androidx.compose.material3.Text("新機能・更新内容")
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    whatsNewEntries.forEach { entry ->
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            androidx.compose.material3.Text(
                                "v${entry.version}  (${entry.date})",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                            entry.changes.forEach { change ->
                                androidx.compose.foundation.layout.Row(
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                                ) {
                                    androidx.compose.material3.Text(
                                        "・",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    androidx.compose.material3.Text(
                                        change,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showWhatsNew = false
                        scope.launch { themePreferences.setLastSeenVersion(com.webclass.plus.data.VersionHistory.latest.version) }
                    }
                ) {
                    androidx.compose.material3.Text("OK")
                }
            }
        )
    }





    val executeNavigation: (String) -> Unit = { route ->
        if (route == "home") {
            // Always navigate to home without resetting data
            navController.navigate(route) {
                popUpTo("home") {
                    inclusive = selectedRoute == "home" // pop back to home root if already on home tab
                    saveState = selectedRoute != "home"
                }
                launchSingleTop = true
                restoreState = true
            }
        } else if (route == "syllabus") {
            if (selectedRoute == "syllabus") {
                syllabusResetTrigger++
            } else {
                navController.navigate(route) {
                    popUpTo("home") {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else if (route == "tools") {
            if (selectedRoute == "tools") {
                toolsTabClickTrigger++
            } else {
                navController.navigate(route) {
                    popUpTo("home") {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else if (route == "user") {
            if (selectedRoute == "user" && currentRoute != "user") {
                // Already on user tab but on a sub-page 竊・pop back to user root
                navController.popBackStack("user", inclusive = false)
            } else {
                navController.navigate(route) {
                    popUpTo("home") {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        } else {
            // Normal navigation for other tabs
            navController.navigate(route) {
                popUpTo("home") {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Navigation Logic
    val onNavigate: (String) -> Unit = { route ->
        // Check if content is active (viewer screen)
        if (currentRoute.startsWith("viewer")) {
            pendingRoute = route
            showExitConfirmation = true
        } else if (selectedRoute == "tools" && route != "tools" && (isOthelloPlaying || isGomokuPlaying || isAirHockeyPlaying || isMineSweeperPlaying || isFlappySkyPlaying)) {
            pendingRoute = route
            showToolsExitConfirmation = true
        } else {
            executeNavigation(route)
        }
    }

    if (showExitConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { androidx.compose.material3.Text("コンテンツを終了しますか？") },
            text = { androidx.compose.material3.Text("実行中のコンテンツを終了します。学習状況が保存されない可能性があります。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showExitConfirmation = false
                        if (pendingRoute == "home") {
                            navigateToHomeWithReset()
                        } else if (pendingRoute != null) {
                            executeNavigation(pendingRoute!!)
                        }
                        pendingRoute = null
                    }
                ) {
                    androidx.compose.material3.Text("終了して戻る", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExitConfirmation = false }) {
                    androidx.compose.material3.Text("キャンセル")
                }
            }
        )
    }

    if (showToolsExitConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showToolsExitConfirmation = false },
            title = { androidx.compose.material3.Text("確認") },
            text = { androidx.compose.material3.Text("現在ゲーム中ですが、移動してもよろしいですか？") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showToolsExitConfirmation = false
                        isOthelloPlaying = false
                        isGomokuPlaying = false
                        isAirHockeyPlaying = false
                        isMineSweeperPlaying = false
                        isFlappySkyPlaying = false
                        if (pendingRoute != null) {
                            executeNavigation(pendingRoute!!)
                        }
                        pendingRoute = null
                    }
                ) {
                    androidx.compose.material3.Text("移動する", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showToolsExitConfirmation = false }) {
                    androidx.compose.material3.Text("キャンセル")
                }
            }
        )
    }

    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)

    val showNetworkLog by developerPreferences.showNetworkLogFlow.collectAsState(initial = false)


    val sharedEnterTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        if (enableLightweightMode) {
            androidx.compose.animation.EnterTransition.None
        } else if (enableHyperAnimation) {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeIn(tween(300))
        } else {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
        }
    }
    
    val sharedExitTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        if (enableLightweightMode) {
            androidx.compose.animation.ExitTransition.None
        } else if (enableHyperAnimation) {
            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeOut(tween(200))
        } else {
            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        }
    }
    
    val sharedPopEnterTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        if (enableLightweightMode) {
            androidx.compose.animation.EnterTransition.None
        } else if (enableHyperAnimation) {
            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeIn(tween(300))
        } else {
            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
        }
    }
    
    val sharedPopExitTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        if (enableLightweightMode) {
            androidx.compose.animation.ExitTransition.None
        } else if (enableHyperAnimation) {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeOut(tween(200))
        } else {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.BoxWithConstraints {
            val isWideScreen = when {
                forceTabletMode -> true
                forceSmartphoneMode -> false
                else -> maxWidth >= 600.dp
            }
            var isSidebarCollapsed by remember { mutableStateOf(false) }

            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen && showBottomBar) {
                    com.webclass.plus.ui.components.SideNavRail(
                        selectedRoute = selectedRoute,
                        showBookmarks = showBookmarks,
                        isCollapsed = isSidebarCollapsed,
                        onToggleCollapse = { isSidebarCollapsed = !isSidebarCollapsed },
                        onNavigate = onNavigate
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (!isWideScreen && showBottomBar) {
                                BottomNavBar(
                                    selectedRoute = selectedRoute,
                                    showBookmarks = showBookmarks,
                                    useWallpaper = useWallpaper,
                                    wallpaperBackgroundAlpha = wallpaperBackgroundAlpha,
                                    onNavigate = onNavigate
                                )
                            }
                        }
                    ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            enterTransition = {
                                if (enableLightweightMode) {
                                    androidx.compose.animation.EnterTransition.None
                                } else if (enableHyperAnimation) {
                                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.7f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter))
                                } else {
                                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300))
                                }
                            },
                            exitTransition = {
                                if (enableLightweightMode) {
                                    androidx.compose.animation.ExitTransition.None
                                } else if (enableHyperAnimation) {
                                    fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.2f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit))
                                } else {
                                    fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300))
                                }
                            },
                            popEnterTransition = {
                                if (enableLightweightMode) {
                                    androidx.compose.animation.EnterTransition.None
                                } else if (enableHyperAnimation) {
                                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 1.2f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter))
                                } else {
                                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 1.05f, animationSpec = tween(300))
                                }
                            },
                            popExitTransition = {
                                if (enableLightweightMode) {
                                    androidx.compose.animation.ExitTransition.None
                                } else if (enableHyperAnimation) {
                                    fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.7f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit))
                                } else {
                                    fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300))
                                }
                            }
                        ) {
                composable(
                    route = "splash",
                    exitTransition = { fadeOut(tween(0)) }
                ) {
                    showBottomBar = false
                    com.webclass.plus.ui.screens.SplashScreen(
                        enableHyperBootAnimation = enableHyperBootAnimation,
                        enableLightweightMode = enableLightweightMode,
                        onAnimationFinished = {
                            val target = if (!hasSeenWelcomeScreen || alwaysShowWelcomeScreen) "welcome" else "home"
                            navController.navigate(target) {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    )
                }

                composable("welcome") {
                    showBottomBar = false
                    com.webclass.plus.ui.screens.WelcomeScreen(
                        themePreferences = themePreferences,
                        onFinish = {
                            scope.launch { themePreferences.setHasSeenWelcomeScreen(true) }
                            navController.navigate("home") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    )
                }

                composable("home") {
                    showBottomBar = true
                    HomeScreen(
                        resetTrigger = homeResetTrigger,
                        onCourseClick = { courseId, title, autoOpenUrl, autoOpenTitle ->
                            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                            var route = "course/$courseId?title=$encodedTitle"
                            if (autoOpenUrl != null && autoOpenTitle != null) {
                                val encodedAutoUrl = java.net.URLEncoder.encode(autoOpenUrl, "UTF-8")
                                val encodedAutoTitle = java.net.URLEncoder.encode(autoOpenTitle, "UTF-8")
                                route += "&autoOpenUrl=$encodedAutoUrl&autoOpenTitle=$encodedAutoTitle"
                            }
                            navController.navigate(route)
                        },
                        onContentClick = { url, title ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=$encodedTitle")
                            }
                        },
                        onNavigateToTimetable = {
                            navController.navigate("timetable")
                        }
                    )
                }
                
                composable("timetable") {
                    showBottomBar = true
                    val timetable by homeViewModel.timetable.collectAsState()
                    
                    com.webclass.plus.ui.screens.TimetableScreen(
                        timetable = timetable,
                        onBack = { navController.navigateUp() },
                        onCourseClick = { courseId, courseTitle ->
                            val encodedTitle = java.net.URLEncoder.encode(courseTitle, "UTF-8")
                            val route = "course/$courseId?title=$encodedTitle"
                            navController.navigate(route)
                        }
                    )
                }

                composable(
                    route = "course/{courseId}?title={title}&autoOpenUrl={autoOpenUrl}&autoOpenTitle={autoOpenTitle}",
                    arguments = listOf(
                        androidx.navigation.navArgument("title") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        },
                        androidx.navigation.navArgument("autoOpenUrl") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        androidx.navigation.navArgument("autoOpenTitle") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    showBottomBar = true
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                    val courseTitle = backStackEntry.arguments?.getString("title") ?: "コース"
                    val autoOpenUrl = backStackEntry.arguments?.getString("autoOpenUrl")
                    val autoOpenTitle = backStackEntry.arguments?.getString("autoOpenTitle")
                    
                    com.webclass.plus.ui.screens.CourseContentsScreen(
                        courseId = courseId,
                        courseTitle = courseTitle,
                        autoOpenUrl = autoOpenUrl,
                        autoOpenTitle = autoOpenTitle,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onDetailClick = { url ->
                            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                            navController.navigate("detail?url=$encoded")
                        },
                        onHistoryClick = { url ->
                            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                            navController.navigate("history?url=$encoded")
                        },
                        onContentClick = { url, title ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=$encodedTitle")
                            }
                        },
                        onScoresClick = { id ->
                            val encodedTitle = java.net.URLEncoder.encode(courseTitle, "UTF-8")
                            navController.navigate("scores/$id?title=$encodedTitle")
                        },
                        onReportsClick = { id ->
                            val encodedTitle = java.net.URLEncoder.encode(courseTitle, "UTF-8")
                            navController.navigate("reports/$id?title=$encodedTitle")
                        }
                    )
                }
                
                composable(
                    route = "detail?url={url}",
                    arguments = listOf(
                        androidx.navigation.navArgument("url") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    showBottomBar = true
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    com.webclass.plus.ui.screens.ContentDetailScreen(
                        url = url,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = "scores/{courseId}?title={title}",
                    arguments = listOf(
                        androidx.navigation.navArgument("courseId") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("title") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    showBottomBar = true
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                    val courseTitle = backStackEntry.arguments?.getString("title") ?: "謌千ｸｾ"
                    com.webclass.plus.ui.screens.CourseScoresScreen(
                        courseId = courseId,
                        courseName = courseTitle,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "reports/{courseId}?title={title}",
                    arguments = listOf(
                        androidx.navigation.navArgument("courseId") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("title") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    showBottomBar = true
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                    val courseTitle = backStackEntry.arguments?.getString("title") ?: "マイレポート"
                    com.webclass.plus.ui.screens.CourseReportsScreen(
                        courseId = courseId,
                        courseName = courseTitle,
                        onNavigateBack = { navController.popBackStack() },
                        onTaskClick = { taskUrl, taskTitle ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(taskUrl))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(taskUrl, "UTF-8")
                                val encodedTitle = java.net.URLEncoder.encode(taskTitle, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=$encodedTitle")
                            }
                        },
                        onScoreDetailClick = { scoreUrl ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(scoreUrl))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(scoreUrl, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=謌千ｸｾ隧ｳ邏ｰ")
                            }
                        }
                    )
                }

                composable("bookmarks") {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.BookmarksScreen(
                        onItemClick = { url, title ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=$encodedTitle")
                            }
                        },
                        onDetailClick = { url ->
                            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                            navController.navigate("detail?url=$encoded")
                        }
                    )
                }

                composable("bus") {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.TimetableHubScreen(isWideScreen = isWideScreen)
                }

                composable("tools") {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.ToolsScreen(
                        navController = navController,
                        tabClickTrigger = toolsTabClickTrigger,
                        isOthelloPlaying = isOthelloPlaying,
                        onOthelloPlayingChanged = { isOthelloPlaying = it },
                        isGomokuPlaying = isGomokuPlaying,
                        onGomokuPlayingChanged = { isGomokuPlaying = it },
                        isAirHockeyPlaying = isAirHockeyPlaying,
                        onAirHockeyPlayingChanged = { isAirHockeyPlaying = it },
                        isMineSweeperPlaying = isMineSweeperPlaying,
                        onMineSweeperPlayingChanged = { isMineSweeperPlaying = it },
                        isFlappySkyPlaying = isFlappySkyPlaying,
                        onFlappySkyPlayingChanged = { isFlappySkyPlaying = it },
                        onNavigationOverlayEligibilityChanged = { toolsAllowsNavigationOverlay = it },
                        viewModel = toolsViewModel
                    )
                }

                composable("syllabus") {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.SyllabusScreen(
                        resetTrigger = syllabusResetTrigger
                    )
                }
                
                composable(
                    route = "history?url={url}",
                    arguments = listOf(
                        androidx.navigation.navArgument("url") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    showBottomBar = true
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    com.webclass.plus.ui.screens.ContentHistoryScreen(
                        url = url,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onLinkClick = { linkUrl ->
                            if (useBrowser) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(linkUrl))
                                context.startActivity(intent)
                            } else {
                                val encodedUrl = java.net.URLEncoder.encode(linkUrl, "UTF-8")
                                navController.navigate("viewer?url=$encodedUrl&title=コンテンツ")
                            }
                        }
                    )
                }
                
                composable(
                    route = "viewer?url={url}&title={title}&origin={origin}",
                    arguments = listOf(
                        androidx.navigation.navArgument("url") { 
                            defaultValue = "" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        },
                        androidx.navigation.navArgument("title") { 
                            defaultValue = "コンテンツ" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        },
                        androidx.navigation.navArgument("origin") { 
                            defaultValue = "home" 
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                        }
                    ),
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    val title = backStackEntry.arguments?.getString("title") ?: "コンテンツ"
                    
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        showBottomBar = true
                    }
                    
                    androidx.compose.runtime.key(url, title) {
                        com.webclass.plus.ui.screens.ContentViewerScreen(
                            url = url,
                            title = title,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onFullScreenChange = { isFull ->
                                showBottomBar = !isFull
                            }
                        )
                    }
                }
                
                composable("user") {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.UserScreen(
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToThemeSettings = { navController.navigate("theme_settings") },
                        onNavigateToInfo = { navController.navigate("info") },
                        onNavigateToWidgetSettings = { navController.navigate("widget_settings") },
                        onNavigateToDeveloperOptions = { navController.navigate("developer_options") },
                        onNavigateToExperimentalFeatures = { navController.navigate("experimental_features") },
                        onWebThemeEditorClick = { navController.navigate("theme_editor") },
                        onAppThemeEditorClick = { navController.navigate("app_theme_editor") }
                    )
                }

                composable(
                    "widget_settings",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.WidgetSettingsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    "info",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.AppInfoScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable(
                    "theme_settings",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    ThemeSettingsScreen(
                        onAppThemeEditorClick = {
                            navController.navigate("app_theme_editor")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    "settings",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    SettingsScreen(
                        onWebThemeEditorClick = {
                            navController.navigate("theme_editor")
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                
                composable(
                    "theme_editor",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = false
                    com.webclass.plus.ui.screens.ThemeEditorScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    "app_theme_editor",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = false
                    com.webclass.plus.ui.screens.AppColorThemeEditorScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                
                composable(
                    "developer_options",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.DeveloperOptionsScreen(
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    "experimental_features",
                    enterTransition = sharedEnterTransition,
                    exitTransition = sharedExitTransition,
                    popEnterTransition = sharedPopEnterTransition,
                    popExitTransition = sharedPopExitTransition
                ) {
                    showBottomBar = true
                    com.webclass.plus.ui.screens.ExperimentalFeaturesScreen(
                        onWebThemeEditorClick = {
                            navController.navigate("theme_editor")
                        },
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
                
                // TODO: Add routes for course contents, detail, history, results, prestart screens
            }
            }

            if (!isMockMode) {
                androidx.compose.runtime.LaunchedEffect(isLoginVisible, webClassWebViewRef) {
                    webClassWebViewRef?.visibility = if (isLoginVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                }

                if (isLoginVisible) {
                    BackHandler {
                        if (webClassWebViewRef?.canGoBack() == true) {
                            webClassWebViewRef?.goBack()
                            canGoBack = webClassWebViewRef?.canGoBack() == true
                            canGoForward = webClassWebViewRef?.canGoForward() == true
                        } else {
                            homeViewModel.setShowWebView(false)
                        }
                    }
                }

                val density = androidx.compose.ui.platform.LocalDensity.current
                val statusBarHeightDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                val topPadding = if (isLoginVisible) (56.dp + statusBarHeightDp) else 0.dp
                val bottomPadding = if (isLoginVisible) 56.dp else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isLoginVisible) 1f else 0f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding, bottom = bottomPadding)
                    ) {
                        WebClassWebView(
                            url = webClassInitialUrl,
                            modifier = Modifier.fillMaxSize(),
                            blockPostRequests = enableSpeedUp && !isLoginVisible,
                            onWebViewCreated = { webClassWebViewRef = it },
                            onPageFinished = { webView, url ->
                                scope.launch {
                                    if (url.contains("login.microsoftonline.com", ignoreCase = true)) {
                                        homeViewModel.setOnMicrosoftLogin(true)
                                        return@launch
                                    }
                                    if (url.contains("login.php", ignoreCase = true)) {
                                        homeViewModel.setCurrentUrl("https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")
                                        return@launch
                                    }

                                    webView.evaluateJavascript(JavaScriptBridge.extractHomeData()) { jsonResult ->
                                        try {
                                            if (jsonResult != null && jsonResult != "null") {
                                                val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                    jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                                } else {
                                                    jsonResult
                                                }
                                                
                                                val dataResult = gson.fromJson(cleanJson, HomeDataResult::class.java)
                                                val pageType = dataResult.pageType ?: "UNKNOWN"
                                                android.util.Log.d("WebClass", "Detected Page (bg): $pageType on $url")

                                                if (pageType == "LOGIN" || pageType == "LOGIN_PAGE" || url.contains("login", ignoreCase = true)) {
                                                    homeViewModel.setCurrentUrl("https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")
                                                } else if (pageType == "COURSE_LIST" || pageType == "HOME") {
                                                    dataResult.courses?.let { extractedCourses ->
                                                        homeViewModel.updateCourses(extractedCourses.distinctBy { it.code })
                                                    }
                                                    
                                                    dataResult.timetable?.let { extractedTimetable ->
                                                        homeViewModel.updateTimetable(extractedTimetable)
                                                    }
                                                    
                                                    dataResult.userName?.let { userName ->
                                                        val cleanUserName = userName.trim()
                                                        if (cleanUserName.isNotEmpty() && 
                                                            cleanUserName != "WebClass ユーザー" && 
                                                            !cleanUserName.contains("言語") && 
                                                            !cleanUserName.lowercase().contains("language")) {
                                                            android.util.Log.d("WebClass", "Extracted user name (bg): $cleanUserName")
                                                            userDataRepository.setUserName(cleanUserName)
                                                        }
                                                    }
                                                    
                                                    dataResult.scheduleSettings?.let { settings ->
                                                        homeViewModel.updateScheduleSettings(settings)
                                                    }
                                                    
                                                    dataResult.dashboardInfo?.let { info ->
                                                        homeViewModel.updateDashboardInfo(info)
                                                    }
                                                    
                                                    homeViewModel.setShowWebView(false)
                                                    homeViewModel.setOnMicrosoftLogin(false)
                                                    homeViewModel.setLoading(false)
                                                    android.webkit.CookieManager.getInstance().flush()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("WebClass", "Parse error bg unified: $e")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                    // 2. Control Overlays (Composed only when visible, preventing blocking of touches when hidden)
                    if (isLoginVisible) {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            modifier = Modifier.align(Alignment.TopCenter),
                            windowInsets = WindowInsets.statusBars,
                            title = {
                                Text(
                                    text = "WebClass ログイン",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            webClassWebViewRef?.goBack()
                                            canGoBack = webClassWebViewRef?.canGoBack() == true
                                            canGoForward = webClassWebViewRef?.canGoForward() == true
                                        },
                                        enabled = canGoBack
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = "戻る",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            webClassWebViewRef?.goForward()
                                            canGoBack = webClassWebViewRef?.canGoBack() == true
                                            canGoForward = webClassWebViewRef?.canGoForward() == true
                                        },
                                        enabled = canGoForward
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "進む",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            webClassWebViewRef?.reload()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "蜀崎ｪｭ縺ｿ霎ｼ縺ｿ",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

        // Network Log Overlay
        if (showNetworkLog) {
            val logEntries = com.webclass.plus.util.NetworkLogger.entries
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .padding(top = 48.dp, start = 4.dp)
                    .widthIn(max = 360.dp)
                    .heightIn(max = 200.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(6.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "Network Log (${logEntries.size})",
                    color = androidx.compose.ui.graphics.Color.Green,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(logEntries.size) { index ->
                        val entry = logEntries[index]
                        val displayUrl = if (entry.url.length > 60) entry.url.take(60) + "窶ｦ" else entry.url
                        androidx.compose.material3.Text(
                            text = "${entry.method} $displayUrl",
                            color = if (entry.method == "PAGE") androidx.compose.ui.graphics.Color.Yellow
                                    else androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (!isMockMode && shouldRunSyllabusWebView) {
            // Hidden Syllabus WebView. It is created only after the learning tab is visible
            // or the syllabus tab explicitly asks for data.
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.blockNetworkImage = true
                        settings.loadsImagesAutomatically = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null && url != "about:blank" && isExpectedSyllabusPage(url, lastSyllabusRequestedUrl)) {
                                    pendingSyllabusUrl = null
                                    extractSyllabusCurrentPageWithRetry(loadGeneration = syllabusLoadGeneration)
                                }
                            }
                        }
                        syllabusWebViewRef = this
                    }
                },
                update = { webView ->
                    val targetUrl = pendingSyllabusUrl
                    if (!targetUrl.isNullOrBlank() && webView.url != targetUrl) {
                        android.util.Log.d("Syllabus", "Loading syllabus page: $targetUrl")
                        webView.stopLoading()
                        webView.loadUrl(targetUrl)
                        pendingSyllabusUrl = null
                    } else if (!targetUrl.isNullOrBlank()) {
                        pendingSyllabusUrl = null
                    }
                },
                modifier = Modifier.size(1.dp).alpha(0f)
            )
        }
    }
}
