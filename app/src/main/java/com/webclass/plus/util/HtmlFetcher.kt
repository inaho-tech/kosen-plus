package com.webclass.plus.util

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object HtmlFetcher {
    suspend fun fetchHtml(urlString: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false // Do not follow 302 login redirects
                
                // Retrieve current cookies from Android's CookieManager
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(urlString)
                if (!cookies.isNullOrEmpty()) {
                    setRequestProperty("Cookie", cookies)
                }
                
                // Mock a common WebView User-Agent to avoid issues
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }

            val responseCode = conn.responseCode
            
            // If redirected (302/301), it's likely a login page redirection. Consider it a failure.
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                return@withContext null
            }
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            // Sync any newly received cookies back to WebView's CookieManager
            val cookieHeaders = conn.headerFields["Set-Cookie"]
            cookieHeaders?.forEach { cookie ->
                CookieManager.getInstance().setCookie(urlString, cookie)
            }
            CookieManager.getInstance().flush()

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            
            val html = sb.toString()
            
            // Check if the html content is a login page. If so, return null to trigger login fallback.
            if (html.contains("login.php") || html.contains("login.microsoftonline.com") || html.contains("SAML")) {
                return@withContext null
            }
            
            html
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn?.disconnect()
        }
    }
}
