package com.webclass.plus.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Composable
fun SilentCameraScreen(isWideScreen: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val frameLock = remember { Any() }
    val latestFrame = remember { AtomicReference<Bitmap?>(null) }
    val lastFrameAt = remember { AtomicLong(0L) }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var galleryRefresh by remember { mutableIntStateOf(0) }
    var showGallery by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showGallery) { showGallery = false }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    val hasPermission = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRefresh++
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            synchronized(frameLock) {
                latestFrame.getAndSet(null)?.recycle()
            }
        }
    }

    LaunchedEffect(hasPermission, lensFacing, permissionRefresh, showGallery) {
        if (!hasPermission || showGallery) {
            if (showGallery) {
                runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            }
            return@LaunchedEffect
        }
        cameraError = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analyzerExecutor) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastFrameAt.get() < 120L) return@setAnalyzer
                        lastFrameAt.set(now)

                        val plane = image.planes[0]
                        val paddedWidth = plane.rowStride / plane.pixelStride
                        val buffer = plane.buffer
                        buffer.rewind()
                        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(buffer)
                        val cropped = if (paddedWidth == image.width) {
                            padded
                        } else {
                            Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
                        }
                        val matrix = Matrix().apply {
                            postRotate(image.imageInfo.rotationDegrees.toFloat())
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                postScale(-1f, 1f)
                            }
                        }
                        val oriented = if (image.imageInfo.rotationDegrees != 0 || lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
                                .also { if (it !== cropped) cropped.recycle() }
                        } else {
                            cropped
                        }
                        synchronized(frameLock) {
                            latestFrame.getAndSet(oriented)?.recycle()
                        }
                    } catch (_: Exception) {
                        // A later frame can still be captured; keep the preview running.
                    } finally {
                        image.close()
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (error: Exception) {
                cameraError = "カメラを開始できませんでした：${error.localizedMessage ?: "不明なエラー"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = if (isWideScreen) 40.dp else 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "アプリからシャッター音を再生せず、プレビュー画像を保存します。端末・地域の仕様により音が鳴る場合があります。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(onClick = { showGallery = !showGallery }) {
                        Icon(
                            if (showGallery) Icons.Default.PhotoCamera else Icons.Default.Collections,
                            contentDescription = if (showGallery) "カメラに戻る" else "ギャラリー"
                        )
                    }
                }
            }

            if (showGallery) {
                SilentCameraGallery(
                    refreshKey = galleryRefresh,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)
                )
            } else if (!hasPermission) {
                ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("カメラの使用を許可してください", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "撮影した画像は端末内にだけ保存します。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                            Text("カメラを許可")
                        }
                    }
                }
            } else {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        cameraError?.let { message ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Spacer(Modifier.size(52.dp))
                    FilledIconButton(
                        onClick = {
                            val snapshot = synchronized(frameLock) {
                                latestFrame.get()?.copy(Bitmap.Config.ARGB_8888, false)
                            }
                            if (snapshot == null) {
                                scope.launch { snackbarHostState.showSnackbar("カメラの準備中です") }
                            } else {
                                isSaving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { saveCameraBitmap(context, snapshot) }
                                    }
                                    snapshot.recycle()
                                    isSaving = false
                                    snackbarHostState.showSnackbar(
                                        result.fold(
                                            onSuccess = {
                                                galleryRefresh++
                                                "写真に保存しました"
                                            },
                                            onFailure = { "保存できませんでした：${it.localizedMessage ?: "不明なエラー"}" }
                                        )
                                    )
                                }
                            }
                        },
                        enabled = !isSaving && cameraError == null,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "撮影", modifier = Modifier.size(34.dp))
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "前後カメラを切り替え")
                    }
                }
                Text(
                    "保存先：Pictures/PlusKosen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class SilentCameraImage(
    val uri: Uri,
    val name: String,
    val dateAddedSeconds: Long
)

@Composable
private fun SilentCameraGallery(refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val images by produceState<List<SilentCameraImage>>(emptyList(), refreshKey) {
        value = withContext(Dispatchers.IO) { loadSilentCameraImages(context) }
    }
    var selectedImage by remember { mutableStateOf<SilentCameraImage?>(null) }

    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("ギャラリー", style = MaterialTheme.typography.titleMedium)
                Text(
                    "無音カメラで撮影した画像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            if (images.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("撮影した画像はまだありません")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images, key = { it.uri.toString() }) { image ->
                        SilentCameraThumbnail(image = image, onClick = { selectedImage = image })
                    }
                }
            }
        }
    }

    selectedImage?.let { image ->
        SilentCameraImageDialog(image = image, onDismiss = { selectedImage = null })
    }
}

@Composable
private fun SilentCameraThumbnail(image: SilentCameraImage, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, image.uri) {
        value = withContext(Dispatchers.IO) { loadCameraBitmap(context, image.uri, thumbnail = true) }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap == null) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = image.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun SilentCameraImageDialog(image: SilentCameraImage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, image.uri) {
        value = withContext(Dispatchers.IO) { loadCameraBitmap(context, image.uri, thumbnail = false) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                if (bitmap == null) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = image.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = "閉じる")
            }
        }
    }
}

private fun loadSilentCameraImages(context: Context): List<SilentCameraImage> {
    val result = mutableListOf<SilentCameraImage>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Images.Media.RELATIVE_PATH}=?" to arrayOf("${Environment.DIRECTORY_PICTURES}/PlusKosen/")
    } else {
        @Suppress("DEPRECATION")
        val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PlusKosen").absolutePath
        "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("$path%")
    }
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        args,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            result += SilentCameraImage(
                uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()),
                name = cursor.getString(nameColumn) ?: "撮影画像",
                dateAddedSeconds = cursor.getLong(dateColumn)
            )
        }
    }
    return result
}

private fun loadCameraBitmap(context: Context, uri: Uri, thumbnail: Boolean): Bitmap? {
    return if (thumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, android.util.Size(400, 400), null)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            if (!thumbnail) {
                val maxEdge = maxOf(info.size.width, info.size.height)
                if (maxEdge > 2048) {
                    val scale = 2048f / maxEdge
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        }
    } else {
        context.contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)
    }
}

private fun saveCameraBitmap(context: Context, bitmap: Bitmap) {
    val fileName = "PlusKosen_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PlusKosen")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            context.contentResolver.openOutputStream(uri).use { stream ->
                checkNotNull(stream)
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    } else {
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PlusKosen")
        check(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, fileName)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
        }
    }
}
