package com.webclass.plus.ui.components

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.CookieManager
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewer(
    pdfUrl: String,
    detectedPdfUrls: List<String> = emptyList(),
    onPdfUrlSelected: (String) -> Unit = {},
    title: String = "PDFプレビュー",
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var tempFile by remember { mutableStateOf<File?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var decryptedFile by remember { mutableStateOf<File?>(null) }
    var isPasswordRequired by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageAspectRatios by remember { mutableStateOf<List<Float>>(emptyList()) }
    
    val bitmapCache = remember(pdfUrl) {
        object : LruCache<Int, Bitmap>(8) {}
    }
    
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Floating Page Indicator state
    val currentPage by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty() && pageCount > 0) {
                val isAtBottom = !listState.canScrollForward || (visibleItems.lastOrNull()?.index == pageCount - 1)
                if (isAtBottom) {
                    pageCount
                } else {
                    val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                    val closestItem = visibleItems.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(itemCenter - viewportCenter)
                    }
                    if (closestItem != null) {
                        (closestItem.index + 1).coerceAtMost(pageCount)
                    } else {
                        val index = listState.firstVisibleItemIndex
                        (index + 1).coerceAtMost(pageCount)
                    }
                }
            } else {
                1
            }
        }
    }

    fun attemptDecryption(password: String) {
        val file = tempFile ?: return
        isLoading = true
        errorMessage = null
        passwordError = null
        
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Initialize PDFBox-Android resource loader (idempotent)
                    PDFBoxResourceLoader.init(context)
                    
                    val document = PDDocument.load(file, password)
                    document.setAllSecurityToBeRemoved(true)
                    val decFile = File(context.cacheDir, "decrypted_" + file.name)
                    document.save(decFile)
                    document.close()
                    
                    // Open the decrypted file with PdfRenderer
                    val pfd = ParcelFileDescriptor.open(decFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    
                    // Pre-calculate page aspect ratios safely
                    val ratios = ArrayList<Float>(renderer.pageCount)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        ratios.add(page.width.toFloat() / page.height.toFloat())
                        page.close()
                    }
                    
                    // Close previous renderer & descriptor if any
                    pdfRenderer?.close()
                    fileDescriptor?.close()
                    decryptedFile?.delete()
                    
                    // Update states
                    decryptedFile = decFile
                    fileDescriptor = pfd
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    pageAspectRatios = ratios
                    bitmapCache.evictAll()
                    
                    isPasswordRequired = false
                    passwordError = null
                    isLoading = false
                } catch (e: InvalidPasswordException) {
                    isLoading = false
                    passwordError = "パスワードが正しくありません。"
                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                    passwordError = "エラーが発生しました: ${e.localizedMessage}"
                }
            }
        }
    }

    // Download & Init PDF Renderer
    LaunchedEffect(pdfUrl, reloadTrigger) {
        isLoading = true
        errorMessage = null
        isPasswordRequired = false
        passwordError = null
        
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            tempFile?.delete()
            decryptedFile?.delete()
            pdfRenderer = null
            fileDescriptor = null
            tempFile = null
            decryptedFile = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        withContext(Dispatchers.IO) {
            try {
                // 1. Download file with cookies for WebClass auth
                val file = downloadFileWithCookies(context, pdfUrl)
                if (file != null && file.exists() && file.length() > 0) {
                    tempFile = file
                    
                    // 2. Open PDF with PdfRenderer
                    try {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        fileDescriptor = pfd
                        val renderer = PdfRenderer(pfd)
                        pdfRenderer = renderer
                        pageCount = renderer.pageCount
                        
                        // Pre-calculate page aspect ratios safely
                        val ratios = ArrayList<Float>(renderer.pageCount)
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            ratios.add(page.width.toFloat() / page.height.toFloat())
                            page.close()
                        }
                        pageAspectRatios = ratios
                        isLoading = false
                    } catch (e: SecurityException) {
                        isPasswordRequired = true
                        isLoading = false
                    }
                } else {
                    errorMessage = "ファイルのダウンロードに失敗しました。\nログイン期限が切れている可能性があります。"
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "PDFのロード中にエラーが発生しました: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    val currentTempFile = rememberUpdatedState(tempFile)
    val currentDecryptedFile = rememberUpdatedState(decryptedFile)

    // Clean up resources on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
                currentTempFile.value?.delete()
                currentDecryptedFile.value?.delete()
                bitmapCache.evictAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = if (showTopBar) {
            {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    val displayTitle = pdfUrl.substringAfterLast("/").substringBefore("?").ifEmpty { title }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    
                    if (detectedPdfUrls.size > 1) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MenuBook, contentDescription = "他のファイル")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            detectedPdfUrls.forEachIndexed { idx, url ->
                                val isCurrent = url == pdfUrl
                                val fileName = url.substringAfterLast("/").substringBefore("?").ifEmpty { "ファイル ${idx + 1}" }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = fileName,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onPdfUrlSelected(url)
                                    }
                                )
                            }
                        }
                    }
                    
                    // Refresh button
                    IconButton(
                        onClick = {
                            reloadTrigger++
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "更新")
                    }
                    
                    // Direct Browser/System Download button
                    IconButton(
                        onClick = {
                            tempFile?.let { file ->
                                val rawName = pdfUrl.substringAfterLast("/").substringBefore("?")
                                val fileName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                                val finalFileName = fileName.ifEmpty { "download.pdf" }
                                
                                scope.launch {
                                    saveFileToDownloads(context, file, finalFileName)
                                }
                            } ?: run {
                                Toast.makeText(context, "ファイルのロード完了を待ってください", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "ダウンロード")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
            }
        } else {
            {}
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .onGloballyPositioned { coordinates ->
                    containerWidthPx = coordinates.size.width
                    containerHeightPx = coordinates.size.height
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "PDFファイルをダウンロード中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isPasswordRequired -> {
                    PasswordPromptView(
                        passwordError = passwordError,
                        onSubmit = { pwd ->
                            attemptDecryption(pwd)
                        },
                        onCancel = {
                            onPdfUrlSelected("")
                        }
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { onPdfUrlSelected("") }) {
                            Text("閉じる")
                        }
                    }
                }
                pdfRenderer != null -> {
                    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                        scale = newScale
                        
                        if (newScale > 1f) {
                            val maxX = (containerWidthPx.toFloat() * (newScale - 1f)) / 2f
                            val maxY = (containerHeightPx.toFloat() * (newScale - 1f)) / 2f
                            
                            val newX = (offset.x + offsetChange.x).coerceIn(-maxX, maxX)
                            val newY = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
                            
                            offset = Offset(newX, newY)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        }
                                    }
                                )
                            }
                            .transformable(state = transformState)
                    ) {
                        // Smooth, immersive vertical list of PDF Pages
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentPadding = PaddingValues(0.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(pageCount, key = { it }) { index ->
                                val aspectRatio = pageAspectRatios.getOrNull(index) ?: 0.707f
                                Surface(
                                    shape = RoundedCornerShape(0.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    PdfPageImage(
                                        pdfRenderer = pdfRenderer!!,
                                        pageIndex = index,
                                        containerWidthPx = containerWidthPx,
                                        aspectRatio = aspectRatio,
                                        bitmapCache = bitmapCache
                                    )
                                }
                            }
                        }

                        // Floating Page Indicator Pill
                        AnimatedVisibility(
                            visible = pageCount > 0,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = "$currentPage / $pageCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageImage(
    pdfRenderer: PdfRenderer,
    pageIndex: Int,
    containerWidthPx: Int,
    aspectRatio: Float,
    bitmapCache: LruCache<Int, Bitmap>,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex, containerWidthPx) { 
        mutableStateOf<Bitmap?>(bitmapCache.get(pageIndex)) 
    }
    
    LaunchedEffect(pageIndex, containerWidthPx) {
        if (containerWidthPx <= 0) return@LaunchedEffect
        if (bitmapCache.get(pageIndex) != null) {
            bitmap = bitmapCache.get(pageIndex)
            return@LaunchedEffect
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Thread-safety: Synchronize rendering of pages to avoid native crash on concurrent PdfRenderer access
                val bmp = synchronized(pdfRenderer) {
                    val page = pdfRenderer.openPage(pageIndex)
                    
                    // Proportionally scale page to container width
                    val scale = containerWidthPx.toFloat() / page.width.toFloat()
                    val targetWidth = containerWidthPx
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    
                    val b = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    
                    // Draw white canvas under rendering
                    val canvas = Canvas(b)
                    canvas.drawColor(Color.WHITE)
                    
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    b
                }
                
                bitmapCache.put(pageIndex, bmp)
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

/**
 * Downloads a remote file using WebClass session cookies
 */
private suspend fun downloadFileWithCookies(context: Context, fileUrl: String): File? = withContext(Dispatchers.IO) {
    try {
        val url = URL(fileUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        // Feed cookies from WebView CookieManager to download authorized file
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(fileUrl)
        if (!cookies.isNullOrEmpty()) {
            connection.setRequestProperty("Cookie", cookies)
        }
        
        connection.connect()
        
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val cacheDir = context.cacheDir
            val tempFile = File.createTempFile("webclass_pdf_", ".pdf", cacheDir)
            
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext tempFile
        }
        return@withContext null
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}

/**
 * Saves the locally downloaded authenticated file to the public Downloads folder
 */
private suspend fun saveFileToDownloads(context: Context, sourceFile: File, fileName: String) = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    java.io.FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ダウンロードフォルダに保存しました: $fileName", Toast.LENGTH_LONG).show()
                }
            } else {
                throw Exception("Failed to insert MediaStore record")
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, fileName)
            java.io.FileInputStream(sourceFile).use { input ->
                java.io.FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "ダウンロードフォルダに保存しました: $fileName", Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存エラー: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun PasswordPromptView(
    passwordError: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lock Icon with premium background decoration
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title and description
        Text(
            text = "保護されたPDFファイル",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "このPDFファイルはパスワードで保護されています。閲覧するにはパスワードを入力してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error message if any
        if (passwordError != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = passwordError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Password Input field
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text("パスワード") },
            placeholder = { Text("パスワードを入力") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "パスワードを非表示" else "パスワードを表示"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("閉じる")
            }

            Button(
                onClick = { if (passwordInput.isNotEmpty()) onSubmit(passwordInput) },
                enabled = passwordInput.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("開く")
            }
        }
    }
}


