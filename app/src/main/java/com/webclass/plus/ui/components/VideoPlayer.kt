package com.webclass.plus.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    referer: String,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    val exoPlayer = remember(context, videoUrl) {
        val cookieManager = android.webkit.CookieManager.getInstance()
        val cookies = cookieManager.getCookie(videoUrl)
        val userAgent = android.webkit.WebSettings.getDefaultUserAgent(context)

        val headers = HashMap<String, String>()
        if (cookies != null) headers["Cookie"] = cookies
        headers["Referer"] = referer

        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build()
    }
    
    LaunchedEffect(videoUrl) {
        try {
            val uri = Uri.parse(videoUrl)
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Handle orientation/fullscreen changes
    LaunchedEffect(isFullScreen) {
        val activity = context as? android.app.Activity
        val configuration = context.resources.configuration
        val isTablet = configuration.smallestScreenWidthDp >= 600
        
        if (activity != null) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            
            if (isFullScreen) {
                // On phones, force landscape. On tablets, keep current orientation or allow sensor.
                if (!isTablet) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                
                // Hide system bars
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                // On phones, force portrait. On tablets, reset to unspecified/user.
                if (!isTablet) {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                     activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                
                // Show system bars
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Restore orientation behavior on exit
             val activity = context as? android.app.Activity
             val configuration = context.resources.configuration
             val isTablet = configuration.smallestScreenWidthDp >= 600

             if (activity != null) {
                 if (!isTablet) {
                     activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                 } else {
                     activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                 }
                 val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                 windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
             }
            exoPlayer.release()
        }
    }

    // Playback State
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(ExoPlayer.STATE_IDLE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPercentage by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    
    // UI Visibility State
    var areControlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    val onDownload = {
        try {
            val uri = Uri.parse(videoUrl)
            val request = DownloadManager.Request(uri).apply {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(videoUrl)
                if (cookies != null) {
                    addRequestHeader("Cookie", cookies)
                }
                val userAgent = WebSettings.getDefaultUserAgent(context)
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Referer", referer)
                
                setDescription("動画ファイルをダウンロード中...")
                
                var fileName = URLUtil.guessFileName(videoUrl, null, null)
                if (fileName.endsWith(".bin") || !fileName.contains(".")) {
                    fileName = "video.mp4"
                }
                setTitle(fileName)
                
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "ダウンロードを開始しました", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "ダウンロードに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Sync Loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            playbackState = exoPlayer.playbackState
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPercentage = exoPlayer.bufferedPercentage
            
            // Auto-hide controls if playing
            if (isPlaying && areControlsVisible) {
                // Ideally this should reset on interaction, simplistic approach here
            }
            
            kotlinx.coroutines.delay(200) // Poll every 200ms
        }
    }
    
    // Auto-hide controls timer
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            kotlinx.coroutines.delay(3000)
            areControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { areControlsVisible = !areControlsVisible } 
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Disable default controls
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        VideoControls(
            isVisible = areControlsVisible || !isPlaying, // Always show when paused
            isPlaying = isPlaying,
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercentage = bufferedPercentage,
            title = "動画", // Can pass actual title if available
            isFullScreen = isFullScreen,
            onPlayPause = {
                if (isPlaying) {
                    exoPlayer.pause()
                } else {
                    if (playbackState == ExoPlayer.STATE_ENDED) {
                        exoPlayer.seekTo(0)
                    }
                    exoPlayer.play()
                }
                areControlsVisible = true
            },
            onSeek = { pos ->
                exoPlayer.seekTo(pos)
                currentPosition = pos // Immediate feedback
                areControlsVisible = true
            },
            onForward = {
                exoPlayer.seekTo(currentPosition + 10000)
                areControlsVisible = true
            },
            onRewind = {
                exoPlayer.seekTo(currentPosition - 10000)
                areControlsVisible = true
            },
            onFullScreenToggle = { onFullScreenChange(!isFullScreen) },
            onClose = onClose,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { playbackSpeed = it },
            onDownload = onDownload
        )
    }
}
