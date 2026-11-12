package com.webclass.plus.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebClassWebView(
    url: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (WebView, String) -> Unit = { _, _ -> },
    onNewTabRequested: (String) -> Unit = {},
    onViewableFileRequested: (url: String, mimeType: String?, contentDisposition: String?) -> Boolean = { _, _, _ -> false },
    lightweight: Boolean = false,
    htmlOnly: Boolean = false,
    blockPostRequests: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val currentTheme by themePreferences.themeFlow.collectAsState(initial = com.webclass.plus.ui.theme.AppTheme.SYSTEM)
    val enableThemeEditor by themePreferences.enableThemeEditorFlow.collectAsState(initial = false)
    val customThemeCss by themePreferences.customThemeCssFlow.collectAsState(initial = "")
    val enableSpeedUp by themePreferences.enableSpeedUpFlow.collectAsState(initial = false)
    val enableWebViewDarkMode by themePreferences.enableWebViewDarkModeFlow.collectAsState(initial = true)
    val customDarkMode by themePreferences.customDarkModeFlow.collectAsState(initial = false)
    val systemDarkMode = isSystemInDarkTheme()
    val applyWebViewDarkMode = enableWebViewDarkMode && when (currentTheme) {
        com.webclass.plus.ui.theme.AppTheme.DARK -> true
        com.webclass.plus.ui.theme.AppTheme.SYSTEM -> systemDarkMode
        com.webclass.plus.ui.theme.AppTheme.CUSTOM -> customDarkMode
        else -> false
    }
    val latestBlockPostRequests = rememberUpdatedState(blockPostRequests || (enableSpeedUp && lightweight))
    val latestWebViewDarkMode = rememberUpdatedState(applyWebViewDarkMode)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(applyWebViewDarkMode, webViewRef) {
        webViewRef?.let { webView ->
            if (!lightweight && webView.url != null) {
                if (applyWebViewDarkMode) {
                    webView.animate().cancel()
                    webView.setBackgroundColor(Color.rgb(24, 26, 27))
                    webView.alpha = 0f
                    com.webclass.plus.util.DarkReaderInjector.apply(webView, true) {
                        webView.animate().alpha(1f).setDuration(120L).start()
                    }
                } else {
                    webView.animate().cancel()
                    webView.alpha = 1f
                    webView.setBackgroundColor(Color.TRANSPARENT)
                    com.webclass.plus.util.DarkReaderInjector.apply(webView, false)
                }
            }
        }
    }

    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val showNetworkLog by developerPreferences.showNetworkLogFlow.collectAsState(initial = false)

    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                var pageLoadGeneration = 0

                // Set explicit layout params to prevent sizing issues
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Configure WebView. Background-only instances keep optional features off.
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = !lightweight
                    useWideViewPort = !lightweight
                    builtInZoomControls = !lightweight
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    blockNetworkImage = lightweight
                    loadsImagesAutomatically = !lightweight
                    
                    // File access settings - important for some content types
                    allowFileAccess = true
                    allowContentAccess = true
                    
                    // Cache settings
                    cacheMode = WebSettings.LOAD_DEFAULT
                    
                    // Media playback settings
                    mediaPlaybackRequiresUserGesture = lightweight
                    
                    // Enable cookies
                    userAgentString = userAgentString.replace("; wv", "") // Try to look like a regular browser
                    // Enable support for multiple windows (target=_blank)
                    setSupportMultipleWindows(!lightweight)
                }
                
                // Set layer type to software for better compatibility on some devices
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                // Enable Third Party Cookies
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (lightweight) return false
                        if (fileUploadCallback != null) {
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = null
                        }
                        fileUploadCallback = filePathCallback
                        
                        try {
                            val intent = fileChooserParams?.createIntent()
                            if (intent != null) {
                                fileChooserLauncher.launch(intent)
                            } else {
                                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                fileChooserLauncher.launch(fallbackIntent)
                            }
                        } catch (e: Exception) {
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = null
                            return false
                        }
                        return true
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        if (lightweight) return false
                        if (view == null || resultMsg == null) return false
                        
                        // Always use the transport mechanism — it resolves URLs
                        // in the correct frame context, even for nested iframes.
                        var handled = false
                        val tempWebView = WebView(view.context)
                        tempWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                if (!handled) {
                                    handled = true
                                    val newUrl = request?.url?.toString()
                                    if (newUrl != null) {
                                        onNewTabRequested(newUrl)
                                    }
                                }
                                tempWebView.post { tempWebView.destroy() }
                                return true
                            }
                            
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                // Fallback: capture URL if shouldOverrideUrlLoading wasn't called
                                if (!handled && url != null && url != "about:blank") {
                                    handled = true
                                    onNewTabRequested(url)
                                    tempWebView.stopLoading()
                                    tempWebView.post { tempWebView.destroy() }
                                }
                            }
                        }
                        
                        val transport = resultMsg.obj as? WebView.WebViewTransport
                        transport?.webView = tempWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requestedUrl = request?.url?.toString() ?: return false
                        if (!lightweight && request.isForMainFrame &&
                            onViewableFileRequested(requestedUrl, null, null)
                        ) {
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        pageLoadGeneration += 1
                        if (view != null && !lightweight && latestWebViewDarkMode.value) {
                            val expectedGeneration = pageLoadGeneration
                            view.animate().cancel()
                            view.setBackgroundColor(Color.rgb(24, 26, 27))
                            view.alpha = 0f
                            // Never leave the page hidden if script execution is interrupted.
                            view.postDelayed({
                                if (pageLoadGeneration == expectedGeneration && view.alpha == 0f) {
                                    view.animate().alpha(1f).setDuration(120L).start()
                                }
                            }, 1800L)
                        }
                        url?.let {
                            onPageStarted(it)
                            if (showNetworkLog) com.webclass.plus.util.NetworkLogger.log(it, "PAGE")
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view != null && url != null) {
                            val finishedGeneration = pageLoadGeneration
                            if (!lightweight) {
                                // Inject Theme
                                val css = com.webclass.plus.util.WebViewStyles.getThemeCss(
                                    currentTheme,
                                    if (enableThemeEditor) customThemeCss else ""
                                )
                                val js = com.webclass.plus.util.WebViewStyles.injectCss(css)
                                view.evaluateJavascript(js, null)
                                com.webclass.plus.util.DarkReaderInjector.apply(view, latestWebViewDarkMode.value) {
                                    if (pageLoadGeneration == finishedGeneration) {
                                        view.animate().cancel()
                                        view.animate().alpha(1f).setDuration(120L).start()
                                    }
                                }
                            }
                            
                            // Call original listener
                            onPageFinished(view, url)
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val reqUrl = request?.url?.toString()
                        if (showNetworkLog && reqUrl != null) {
                            com.webclass.plus.util.NetworkLogger.log(
                                reqUrl,
                                request.method ?: "GET"
                            )
                        }
                        if (latestBlockPostRequests.value && request?.method?.equals("POST", ignoreCase = true) == true) {
                            return android.webkit.WebResourceResponse(
                                "text/plain", "utf-8",
                                java.io.ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        if (htmlOnly && reqUrl != null) {
                            // Block session.php polling requests
                            if (reqUrl.contains("session.php")) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain", "utf-8",
                                    java.io.ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            // Block all sub-resources (favicon, CSS, JS, images, etc.)
                            val isMainFrame = request?.isForMainFrame == true
                            if (!isMainFrame) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain", "utf-8",
                                    java.io.ByteArrayInputStream(ByteArray(0))
                                )
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: android.webkit.HttpAuthHandler?,
                        host: String?,
                        realm: String?
                    ) {
                        if (view == null || handler == null) {
                            super.onReceivedHttpAuthRequest(view, handler, host, realm)
                            return
                        }
                        
                        val builder = android.app.AlertDialog.Builder(view.context)
                        builder.setTitle("認証が必要")
                        builder.setMessage("${host ?: ""} へのアクセスにはユーザー名とパスワードが必要です。")
                        
                        val density = view.context.resources.displayMetrics.density
                        val padHorizontal = (24 * density).toInt()
                        val padVertical = (8 * density).toInt()
                        val layout = android.widget.LinearLayout(view.context).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
                        }
                        
                        val usernameInput = android.widget.EditText(view.context).apply {
                            hint = "ユーザー名"
                            inputType = android.text.InputType.TYPE_CLASS_TEXT
                        }
                        
                        val passwordInput = android.widget.EditText(view.context).apply {
                            hint = "パスワード"
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }
                        
                        val space = android.view.View(view.context).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (8 * density).toInt()
                            )
                        }
                        
                        layout.addView(usernameInput)
                        layout.addView(space)
                        layout.addView(passwordInput)
                        builder.setView(layout)
                        
                        builder.setPositiveButton("ログイン") { dialog, _ ->
                            val username = usernameInput.text.toString()
                            val password = passwordInput.text.toString()
                            handler.proceed(username, password)
                            dialog.dismiss()
                        }
                        
                        builder.setNegativeButton("キャンセル") { dialog, _ ->
                            handler.cancel()
                            dialog.dismiss()
                        }
                        
                        builder.setOnCancelListener {
                            handler.cancel()
                        }
                        
                        builder.show()
                    }
                }

                if (!lightweight) {
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                        if (onViewableFileRequested(downloadUrl, mimetype, contentDisposition)) {
                            return@setDownloadListener
                        }
                        try {
                            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                setMimeType(mimetype)
                                
                                // Retrieve cookies for authenticated download
                                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                                addRequestHeader("Cookie", cookies)
                                addRequestHeader("User-Agent", userAgent)
                                
                                setDescription("ファイルをダウンロード中...")
                                
                                val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                                setTitle(fileName)
                                
                                // Set visibility of notifications
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                
                                // Set destination to Downloads directory
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    fileName
                                )
                            }
                            
                            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            downloadManager.enqueue(request)
                            
                            Toast.makeText(context, "ダウンロードを開始しました", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "ダウンロードに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                onWebViewCreated(this)
                webViewRef = this
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url && url.isNotEmpty()) {
                webView.loadUrl(url)
            }
        }
    )
}
