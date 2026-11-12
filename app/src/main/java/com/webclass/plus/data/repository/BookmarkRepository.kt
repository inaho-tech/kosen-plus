package com.webclass.plus.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webclass.plus.data.models.Bookmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BookmarkRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(loadBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()
    
    private fun loadBookmarks(): List<Bookmark> {
        val json = prefs.getString("bookmark_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Bookmark>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val json = gson.toJson(bookmarks)
        prefs.edit().putString("bookmark_list", json).apply()
        _bookmarks.value = bookmarks
    }
    
    fun addBookmark(bookmark: Bookmark) {
        val current = _bookmarks.value.toMutableList()
        // Remove if already exists (to update timestamp)
        current.removeAll { it.id == bookmark.id }
        current.add(0, bookmark) // Add to beginning
        saveBookmarks(current)
    }
    
    fun removeBookmark(id: String) {
        val current = _bookmarks.value.filter { it.id != id }
        saveBookmarks(current)
    }
    
    fun isBookmarked(id: String): Boolean {
        return _bookmarks.value.any { it.id == id }
    }
    
    fun toggleBookmark(bookmark: Bookmark) {
        if (isBookmarked(bookmark.id)) {
            removeBookmark(bookmark.id)
        } else {
            addBookmark(bookmark)
        }
    }
}
