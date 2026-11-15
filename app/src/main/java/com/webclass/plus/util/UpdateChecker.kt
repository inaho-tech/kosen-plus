package com.webclass.plus.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releasesを利用するアプリ更新チェッカー。 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val UPDATE_CHECK_URL = "https://api.github.com/repos/inaho-tech/kosen-plus/releases/latest"
    const val UPDATE_PAGE_URL = "https://github.com/inaho-tech/kosen-plus/releases"
    const val CURRENT_VERSION = "4.5.3"

    data class UpdateInfo(
        val latestVersion: String,
        val isUpdateAvailable: Boolean,
        val downloadUrl: String? = null,
        val releasePageUrl: String = UPDATE_PAGE_URL,
        val assetName: String? = null,
        val assetSize: Long? = null,
        val sha256: String? = null
    )

    suspend fun checkForUpdates(
        customUrl: String? = null,
        spoofedVersion: String? = null
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val targetUrl = normalizeReleaseApiUrl(customUrl)
        val currentVersion = spoofedVersion?.takeIf { it.isNotBlank() } ?: CURRENT_VERSION
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "KosenPlus-Android/$CURRENT_VERSION")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub Releases returned HTTP ${connection.responseCode}")
                return@withContext null
            }

            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latestVersion = semanticVersion(release.optString("tag_name"))
                ?: semanticVersion(release.optString("name"))
                ?: return@withContext null
            val releasePage = release.optString("html_url").ifBlank { UPDATE_PAGE_URL }

            val apkAssets = buildList {
                val assets = release.optJSONArray("assets") ?: return@buildList
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true) && asset.optString("state", "uploaded") == "uploaded") {
                        add(asset)
                    }
                }
            }
            val apk = apkAssets.maxByOrNull { asset ->
                val name = asset.optString("name").lowercase()
                (if ("release" in name) 4 else 0) +
                    (if ("debug" !in name) 2 else 0) +
                    (if ("universal" in name) 1 else 0)
            }
            val digest = apk?.optString("digest")
                ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.lowercase()

            UpdateInfo(
                latestVersion = latestVersion,
                isUpdateAvailable = compareVersions(latestVersion, currentVersion) > 0,
                downloadUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                releasePageUrl = releasePage,
                assetName = apk?.optString("name")?.takeIf { it.isNotBlank() },
                assetSize = apk?.optLong("size")?.takeIf { it > 0L },
                sha256 = digest
            )
        } catch (error: Exception) {
            Log.e(TAG, "GitHub Releases update check failed", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizeReleaseApiUrl(customUrl: String?): String {
        val value = customUrl?.trim().orEmpty()
        if (value.isBlank()) return UPDATE_CHECK_URL
        val githubRelease = Regex("""https://github\.com/([^/]+)/([^/]+)/releases(?:/.*)?""")
            .matchEntire(value.removeSuffix("/"))
        return if (githubRelease != null) {
            val (owner, repository) = githubRelease.destructured
            "https://api.github.com/repos/$owner/$repository/releases/latest"
        } else value
    }

    private fun semanticVersion(value: String): String? =
        Regex("""\d+(?:\.\d+){1,3}""").find(value)?.value

    private fun compareVersions(first: String, second: String): Int {
        val firstParts = first.split('.').map { it.toIntOrNull() ?: 0 }
        val secondParts = second.split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(firstParts.size, secondParts.size)) { index ->
            val difference = firstParts.getOrElse(index) { 0 } - secondParts.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}
