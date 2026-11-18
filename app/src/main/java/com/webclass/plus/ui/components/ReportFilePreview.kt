package com.webclass.plus.ui.components

import android.app.DownloadManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale

data class ReportFilePreviewRequest(
    val url: String,
    val fileName: String
)

private sealed interface PreviewContent {
    data class TextFile(val text: String, val language: String, val truncated: Boolean) : PreviewContent
    data class ImageFile(val file: File) : PreviewContent
    data class MediaFile(val url: String) : PreviewContent
    data class PdfFile(val url: String) : PreviewContent
    data class Unsupported(val type: String) : PreviewContent
}

private data class CachedReportFile(val file: File, val contentType: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFilePreviewDialog(
    request: ReportFilePreviewRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var content by remember(request) { mutableStateOf<PreviewContent?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var cachedFile by remember(request) { mutableStateOf<File?>(null) }
    var reloadKey by remember(request) { mutableIntStateOf(0) }

    LaunchedEffect(request, reloadKey) {
        content = null
        error = null
        cachedFile?.delete()
        cachedFile = null

        val extension = extensionOf(request.fileName, request.url)
        if (extension == "pdf") {
            content = PreviewContent.PdfFile(request.url)
            return@LaunchedEffect
        }
        if (extension in MEDIA_EXTENSIONS) {
            content = PreviewContent.MediaFile(request.url)
            return@LaunchedEffect
        }

        runCatching { fetchReportFile(context, request.url, extension) }
            .onSuccess { downloaded ->
                cachedFile = downloaded.file
                val type = downloaded.contentType.substringBefore(';').lowercase(Locale.ROOT)
                content = when {
                    type == "application/pdf" -> PreviewContent.PdfFile(request.url)
                    type.startsWith("image/") || extension in IMAGE_EXTENSIONS ->
                        PreviewContent.ImageFile(downloaded.file)
                    type.startsWith("audio/") || type.startsWith("video/") ->
                        PreviewContent.MediaFile(request.url)
                    isTextFile(type, extension) -> {
                        val bytes = downloaded.file.inputStream().use { it.readNBytes(MAX_TEXT_BYTES + 1) }
                        val truncated = bytes.size > MAX_TEXT_BYTES
                        val visibleBytes = if (truncated) bytes.copyOf(MAX_TEXT_BYTES) else bytes
                        PreviewContent.TextFile(
                            text = decodeText(visibleBytes, downloaded.contentType),
                            language = languageFor(extension),
                            truncated = truncated
                        )
                    }
                    else -> PreviewContent.Unsupported(type.ifBlank { extension.ifBlank { "不明な形式" } })
                }
            }
            .onFailure { throwable ->
                error = throwable.message ?: "ファイルを読み込めませんでした。"
            }
    }

    DisposableEffect(request) {
        onDispose { cachedFile?.delete() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = request.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = previewLabel(content),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "閉じる")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { downloadReportAttachment(context, request) }
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = "ダウンロード")
                        }
                        if (error != null) {
                            IconButton(onClick = { reloadKey++ }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "再読み込み")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                when {
                    error != null -> PreviewError(error = error!!, onRetry = { reloadKey++ })
                    content == null -> PreviewLoading()
                    content is PreviewContent.PdfFile -> PdfViewer(
                        pdfUrl = (content as PreviewContent.PdfFile).url,
                        title = request.fileName,
                        showTopBar = false
                    )
                    content is PreviewContent.ImageFile -> ImagePreview((content as PreviewContent.ImageFile).file)
                    content is PreviewContent.MediaFile -> VideoPlayer(
                        videoUrl = (content as PreviewContent.MediaFile).url,
                        referer = "https://webclass.kosen-k.go.jp/webclass/",
                        isFullScreen = false,
                        onFullScreenChange = {},
                        onClose = onDismiss
                    )
                    content is PreviewContent.TextFile -> TextPreview(content as PreviewContent.TextFile)
                    content is PreviewContent.Unsupported -> UnsupportedPreview((content as PreviewContent.Unsupported).type)
                }
            }
        }
    }
}

private fun downloadReportAttachment(context: Context, request: ReportFilePreviewRequest) {
    runCatching {
        val safeName = request.fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1F<>:\"/\\\\|?*]"), "_")
            .trim()
            .ifBlank { "report_attachment" }
        val downloadRequest = DownloadManager.Request(Uri.parse(request.url)).apply {
            CookieManager.getInstance().getCookie(request.url)?.takeIf { it.isNotBlank() }?.let {
                addRequestHeader("Cookie", it)
            }
            addRequestHeader("User-Agent", WebSettings.getDefaultUserAgent(context))
            addRequestHeader("Referer", "https://webclass.kosen-k.go.jp/webclass/")
            setTitle(safeName)
            setDescription("マイレポートの添付ファイルを保存しています")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(downloadRequest)
        Toast.makeText(context, "ダウンロードを開始しました", Toast.LENGTH_SHORT).show()
    }.onFailure { error ->
        Toast.makeText(
            context,
            "ダウンロードを開始できませんでした: ${error.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun PreviewLoading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text("プレビューを準備しています", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewError(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.error)
        Text("プレビューを表示できません", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("再試行") }
    }
}

@Composable
private fun UnsupportedPreview(type: String) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.Description, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text("このファイル形式はプレビューできません", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(type, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("ファイルは端末へダウンロードされていません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TextPreview(content: PreviewContent.TextFile) {
    val darkMode = isSystemInDarkTheme()
    val highlighted by produceState<AnnotatedString?>(
        initialValue = null,
        key1 = content.text,
        key2 = content.language,
        key3 = darkMode
    ) {
        value = withContext(Dispatchers.Default) {
            buildHighlightedText(content.text, content.language, darkMode)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (content.truncated) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "ファイルが大きいため、先頭4 MBを表示しています。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 12.dp)
        ) {
            if (content.language.isNotBlank()) {
                Text(
                    text = lineNumbers(content.text),
                    modifier = Modifier.padding(start = 12.dp, end = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
            Text(
                text = highlighted ?: AnnotatedString(content.text),
                modifier = Modifier.padding(start = if (content.language.isBlank()) 16.dp else 0.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

private fun buildHighlightedText(code: String, language: String, darkMode: Boolean): AnnotatedString {
    val syntaxLanguage = syntaxLanguageFor(language) ?: return AnnotatedString(code)
    val highlights = Highlights.Builder()
        .code(code)
        .language(syntaxLanguage)
        .theme(SyntaxThemes.atom(darkMode))
        .build()
        .getHighlights()

    return buildAnnotatedString {
        append(code)
        highlights.forEach { highlight ->
            val start = highlight.location.start.coerceIn(0, code.length)
            val end = highlight.location.end.coerceIn(start, code.length)
            if (start == end) return@forEach
            when (highlight) {
                is ColorHighlight -> addStyle(
                    SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF000000L or highlight.rgb.toLong())),
                    start,
                    end
                )
                is BoldHighlight -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}

private fun syntaxLanguageFor(language: String): SyntaxLanguage? = when (language) {
    "kotlin" -> SyntaxLanguage.KOTLIN
    "java" -> SyntaxLanguage.JAVA
    "c" -> SyntaxLanguage.C
    "cpp" -> SyntaxLanguage.CPP
    "csharp" -> SyntaxLanguage.CSHARP
    "python" -> SyntaxLanguage.PYTHON
    "javascript" -> SyntaxLanguage.JAVASCRIPT
    "typescript" -> SyntaxLanguage.TYPESCRIPT
    "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
    "perl" -> SyntaxLanguage.PERL
    "bash" -> SyntaxLanguage.SHELL
    "ruby" -> SyntaxLanguage.RUBY
    "rust" -> SyntaxLanguage.RUST
    "go" -> SyntaxLanguage.GO
    "php" -> SyntaxLanguage.PHP
    "swift" -> SyntaxLanguage.SWIFT
    "dart" -> SyntaxLanguage.DART
    else -> null
}

private fun lineNumbers(code: String): String = (1..code.lineSequence().count()).joinToString("\n")

@Composable
private fun ImagePreview(file: File) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    if (bitmap == null) {
        PreviewError("画像を読み取れませんでした。", onRetry = {})
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX = if (scale > 1f) offsetX + pan.x else 0f
                    offsetY = if (scale > 1f) offsetY + pan.y else 0f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "添付画像",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
        )
    }
}

private suspend fun fetchReportFile(
    context: android.content.Context,
    fileUrl: String,
    extension: String
): CachedReportFile = withContext(Dispatchers.IO) {
    val connection = URL(fileUrl).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 20_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context))
    connection.setRequestProperty("Referer", "https://webclass.kosen-k.go.jp/webclass/")
    CookieManager.getInstance().getCookie(fileUrl)?.takeIf { it.isNotBlank() }?.let {
        connection.setRequestProperty("Cookie", it)
    }
    try {
        val status = connection.responseCode
        if (status !in 200..299) error("サーバーからファイルを取得できませんでした（HTTP $status）。")
        val declaredLength = connection.contentLengthLong
        if (declaredLength > MAX_PREVIEW_BYTES) error("50 MBを超えるファイルはプレビューできません。")

        val suffix = extension.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }?.let { ".$it" } ?: ".tmp"
        val target = File.createTempFile("report_preview_", suffix, context.cacheDir)
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_PREVIEW_BYTES) {
                        target.delete()
                        error("50 MBを超えるファイルはプレビューできません。")
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        CachedReportFile(target, connection.contentType.orEmpty())
    } finally {
        connection.disconnect()
    }
}

private fun extensionOf(fileName: String, url: String): String =
    fileName.substringAfterLast('.', "").ifBlank {
        url.substringBefore('?').substringAfterLast('.').substringBefore('/')
    }.lowercase(Locale.ROOT)

private fun isTextFile(contentType: String, extension: String): Boolean =
    contentType.startsWith("text/") ||
        contentType in setOf("application/json", "application/xml", "application/javascript", "application/sql") ||
        extension in TEXT_EXTENSIONS

private fun decodeText(bytes: ByteArray, contentType: String): String {
    val declared = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
        .find(contentType)?.groupValues?.getOrNull(1)?.trim('"')
    val candidates = buildList {
        declared?.let { runCatching { Charset.forName(it) }.getOrNull() }?.let(::add)
        add(Charsets.UTF_8)
        runCatching { Charset.forName("Windows-31J") }.getOrNull()?.let(::add)
    }.distinct()
    return candidates.firstNotNullOfOrNull { charset ->
        val decoded = bytes.toString(charset)
        decoded.takeIf { '\uFFFD' !in it }
    } ?: bytes.toString(Charsets.UTF_8)
}

private fun languageFor(extension: String): String = when (extension) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "c", "h" -> "c"
    "cc", "cpp", "cxx", "hpp" -> "cpp"
    "cs" -> "csharp"
    "py" -> "python"
    "js", "jsx", "mjs", "cjs" -> "javascript"
    "ts", "tsx" -> "typescript"
    "coffee" -> "coffeescript"
    "pl", "pm" -> "perl"
    "sh", "bash", "zsh" -> "bash"
    "ps1" -> "powershell"
    "bat", "cmd" -> "dos"
    "rb" -> "ruby"
    "rs" -> "rust"
    "go" -> "go"
    "php" -> "php"
    "swift" -> "swift"
    "dart" -> "dart"
    else -> ""
}

private fun previewLabel(content: PreviewContent?): String = when (content) {
    null -> "読み込み中"
    is PreviewContent.TextFile -> if (content.language.isBlank()) "テキスト" else "${content.language} · シンタックスハイライト"
    is PreviewContent.ImageFile -> "画像"
    is PreviewContent.MediaFile -> "メディア"
    is PreviewContent.PdfFile -> "PDF"
    is PreviewContent.Unsupported -> "プレビュー非対応"
}

private const val MAX_TEXT_BYTES = 4 * 1024 * 1024
private const val MAX_PREVIEW_BYTES = 50L * 1024 * 1024

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val MEDIA_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "mp4", "m4v", "webm", "mov")
private val TEXT_EXTENSIONS = setOf(
    "txt", "log", "csv", "c", "h", "cc", "cpp", "cxx", "hpp", "cs", "java", "kt", "kts", "py",
    "js", "jsx", "mjs", "cjs", "ts", "tsx", "coffee", "pl", "pm", "html", "htm", "css", "scss", "sass", "json", "xml",
    "svg", "yaml", "yml", "md", "markdown", "sql", "sh", "bash", "zsh", "ps1", "bat", "cmd", "rb",
    "rs", "go", "php", "swift", "dart", "gradle", "toml", "ini", "properties"
)
