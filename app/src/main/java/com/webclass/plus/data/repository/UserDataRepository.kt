package com.webclass.plus.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.webclass.plus.util.DeveloperPreferences

class UserDataRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
    private val developerPreferences = DeveloperPreferences(context)
    
    private val _realUserName = MutableStateFlow(getRealUserName())
    private val _userName = MutableStateFlow(getRealUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()
    private val _email = MutableStateFlow(prefs.getString("email", "").orEmpty())
    val email: StateFlow<String> = _email.asStateFlow()

    init {
        MainScope().launch {
            combine(
                _realUserName,
                developerPreferences.spoofNameEnabledFlow,
                developerPreferences.spoofNameFlow
            ) { realName, isSpoofEnabled, spoofName ->
                if (isSpoofEnabled && spoofName.isNotBlank()) spoofName else realName
            }.collect { resolvedName ->
                _userName.value = resolvedName
            }
        }
    }
    
    private fun getRealUserName(): String {
        return prefs.getString("user_name", "WebClass ユーザー") ?: "WebClass ユーザー"
    }
    
    fun setUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
        _realUserName.value = name
    }

    fun setEmail(email: String) {
        prefs.edit().putString("email", email).apply()
        _email.value = email
    }
}
