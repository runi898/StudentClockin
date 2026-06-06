package com.familycheckin.auth

import android.content.Context

data class AuthSessionSnapshot(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?
)

class AuthSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): AuthSessionSnapshot? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return AuthSessionSnapshot(
            accessToken = accessToken,
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = if (preferences.contains(KEY_EXPIRES_AT)) {
                preferences.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }
            } else {
                null
            }
        )
    }

    fun save(snapshot: AuthSessionSnapshot) {
        preferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, snapshot.accessToken)
            putString(KEY_REFRESH_TOKEN, snapshot.refreshToken)
            if (snapshot.expiresAtEpochSeconds != null) {
                putLong(KEY_EXPIRES_AT, snapshot.expiresAtEpochSeconds)
            } else {
                remove(KEY_EXPIRES_AT)
            }
        }.commit()
    }

    fun clear() {
        preferences.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
        }.commit()
    }

    private companion object {
        const val PREFS_NAME = "family_checkin_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at_epoch_seconds"
    }
}
