package com.webclass.plus.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateInstaller {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long?) {
        val fraction: Float?
            get() = totalBytes?.takeIf { it > 0L }
                ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
    }

    suspend fun downloadApk(
        context: Context,
        update: UpdateChecker.UpdateInfo,
        onProgress: (Progress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val downloadUrl = update.downloadUrl ?: error("このReleaseにAPKがありません。")
        val parsedUrl = URL(downloadUrl)
        require(parsedUrl.protocol.equals("https", ignoreCase = true)) { "安全なHTTPS URLではありません。" }

        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(updateDirectory, safeFileName(update.assetName ?: "KosenPlus-${update.latestVersion}.apk"))
        val temporaryFile = File(updateDirectory, "${finalFile.name}.part")
        temporaryFile.delete()

        val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
            setRequestProperty("User-Agent", "KosenPlus-Android/${UpdateChecker.CURRENT_VERSION}")
        }
        try {
            if (connection.responseCode !in 200..299) error("APKの取得に失敗しました（HTTP ${connection.responseCode}）。")
            val totalBytes = update.assetSize ?: connection.contentLengthLong.takeIf { it > 0L }
            var downloaded = 0L
            var lastReportedAt = 0L
            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val now = System.currentTimeMillis()
                        if (now - lastReportedAt >= 100L) {
                            withContext(Dispatchers.Main.immediate) { onProgress(Progress(downloaded, totalBytes)) }
                            lastReportedAt = now
                        }
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) { onProgress(Progress(downloaded, totalBytes)) }

            update.sha256?.let { expected ->
                val actual = sha256(temporaryFile)
                check(actual.equals(expected, ignoreCase = true)) { "APKの整合性を確認できませんでした。" }
            }
            if (finalFile.exists()) finalFile.delete()
            check(temporaryFile.renameTo(finalFile)) { "APKを保存できませんでした。" }
            finalFile
        } catch (error: Exception) {
            temporaryFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".apk", true)) it else "$it.apk" }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
