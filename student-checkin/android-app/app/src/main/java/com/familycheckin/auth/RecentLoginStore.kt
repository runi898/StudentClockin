package com.familycheckin.auth

import android.content.Context

class RecentLoginStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun recentEmail(): String? {
        return preferences.getString(KEY_RECENT_EMAIL, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun rememberedPassword(): String? {
        if (!shouldRememberPassword()) return null
        return preferences.getString(KEY_REMEMBERED_PASSWORD, null)?.takeIf { it.isNotBlank() }
    }

    fun shouldRememberPassword(): Boolean {
        return preferences.getBoolean(KEY_REMEMBER_PASSWORD, false)
    }

    fun saveRecentEmail(email: String) {
        preferences.edit().putString(KEY_RECENT_EMAIL, email.trim()).commit()
    }

    fun saveLoginPreference(email: String, password: String, rememberPassword: Boolean) {
        preferences.edit().apply {
            putString(KEY_RECENT_EMAIL, email.trim())
            putBoolean(KEY_REMEMBER_PASSWORD, rememberPassword)
            if (rememberPassword) {
                putString(KEY_REMEMBERED_PASSWORD, password)
            } else {
                remove(KEY_REMEMBERED_PASSWORD)
            }
        }.commit()
    }

    private companion object {
        const val PREFS_NAME = "family_checkin_auth"
        const val KEY_RECENT_EMAIL = "recent_email"
        const val KEY_REMEMBER_PASSWORD = "remember_password"
        const val KEY_REMEMBERED_PASSWORD = "remembered_password"
    }
}
