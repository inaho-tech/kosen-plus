package com.webclass.plus.util

import android.content.Context
import android.webkit.WebView

/** Applies the bundled Dark Reader script without loading code from a remote CDN at runtime. */
object DarkReaderInjector {
    private const val ASSET_PATH = "vendor/darkreader/darkreader-4.9.134.min.js"

    @Volatile
    private var cachedScript: String? = null

    private fun script(context: Context): String = cachedScript ?: synchronized(this) {
        cachedScript ?: context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }.also { cachedScript = it }
    }

    fun apply(webView: WebView, enabled: Boolean, onApplied: () -> Unit = {}) {
        if (!enabled) {
            webView.evaluateJavascript(
                "if (window.DarkReader) { window.DarkReader.disable(); }",
                { onApplied() }
            )
            return
        }

        val library = runCatching { script(webView.context.applicationContext) }.getOrNull()
        if (library == null) {
            onApplied()
            return
        }
        webView.evaluateJavascript(library) {
            webView.evaluateJavascript(
                """
                (function() {
                    if (!window.DarkReader) return;
                    window.DarkReader.setFetchMethod(function(url) { return window.fetch(url); });
                    window.DarkReader.enable({ brightness: 100, contrast: 90, sepia: 0 });
                })();
                """.trimIndent(),
                { webView.postDelayed({ onApplied() }, 120L) }
            )
        }
    }
}
