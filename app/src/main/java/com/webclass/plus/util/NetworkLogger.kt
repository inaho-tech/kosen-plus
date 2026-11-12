package com.webclass.plus.util

import androidx.compose.runtime.mutableStateListOf

/**
 * Singleton that records recent network requests for the developer overlay.
 * Keeps a rolling list of the most recent entries.
 */
object NetworkLogger {
    private const val MAX_ENTRIES = 30

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val method: String = "GET",
        val url: String
    )

    val entries = mutableStateListOf<LogEntry>()

    fun log(url: String, method: String = "GET") {
        entries.add(0, LogEntry(url = url, method = method))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun clear() {
        entries.clear()
    }
}
