package com.webclass.plus.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility to check for app updates from the update server
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val UPDATE_CHECK_URL = "https://script.google.com/macros/s/AKfycbz-sNyMq4xpzUQIp5G3hVWBcg3b2Z_a2OAN0H0gA1fVGOoeuEwJPX7dHOnmwzMZJ0gOFw/exec"
    const val UPDATE_PAGE_URL = UPDATE_CHECK_URL
    
    // Current app version - update this when releasing new versions
    const val CURRENT_VERSION = "4.5.2"
    
    data class UpdateInfo(
        val latestVersion: String,
        val isUpdateAvailable: Boolean,
        val downloadUrl: String? = null
    )
    
    /**
     * Fetches the latest version from the update server
     * Returns null if the check fails
     */
    /**
     * Fetches the latest version from the update server
     * Returns null if the check fails
     */
    suspend fun checkForUpdates(customUrl: String? = null, spoofedVersion: String? = null): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val targetUrl = if (!customUrl.isNullOrBlank()) customUrl else UPDATE_CHECK_URL
            val currentVer = if (!spoofedVersion.isNullOrBlank()) spoofedVersion else CURRENT_VERSION
            Log.d(TAG, "Checking updates from: $targetUrl (version: $currentVer)")
            val url = URL(targetUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "WebClassPlus-Android")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Parse version from HTML content
                // Looking for pattern like: <p>1.7.0をインストール...</p>
                // Or in the userHtml field: \\x3cp\\x3e1.7.0をインストール...
                val versionRegex = """(\d+\.\d+\.\d+)をインストール""".toRegex()
                val matchResult = versionRegex.find(response)
                
                // Also extract the download URL (drive.google.com link)
                // The URL is escaped with \\/ instead of / in the JSON response
                // Pattern: https:\\/\\/drive.google.com\\/file\\/d\\/...\\/view?usp\\x3dsharing
                // Match until we hit a closing tag or quote that's not part of the URL
                val urlRegex = """https:\\?/?\\?/?drive\.google\.com[^\s"<]*sharing""".toRegex()
                val urlMatch = urlRegex.find(response)
                var downloadUrl = urlMatch?.value
                // Unescape the URL: replace \\/ with / and \\x3d with = etc.
                if (downloadUrl != null) {
                    downloadUrl = downloadUrl
                        .replace("\\/", "/")
                        .replace("\\x3d", "=")
                        .replace("\\x26", "&")
                    Log.d(TAG, "Extracted download URL: $downloadUrl")
                }
                
                if (matchResult != null) {
                    val latestVersion = matchResult.groupValues[1]
                    Log.d(TAG, "Latest version: $latestVersion, Current: $currentVer, URL: $downloadUrl")
                    
                    val isUpdateAvailable = compareVersions(latestVersion, currentVer) > 0
                    return@withContext UpdateInfo(latestVersion, isUpdateAvailable, downloadUrl)
                } else {
                    Log.w(TAG, "Could not find version in response")
                }
            } else {
                Log.w(TAG, "HTTP error: $responseCode")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
        
        null
    }
    
    /**
     * Compares two version strings
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
