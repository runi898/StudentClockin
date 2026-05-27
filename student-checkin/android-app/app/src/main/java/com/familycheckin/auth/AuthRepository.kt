package com.familycheckin.auth

interface AuthRepository {
    suspend fun requestPasswordReset(email: String)
}

class FakeAuthRepository : AuthRepository {
    var lastResetEmail: String? = null

    override suspend fun requestPasswordReset(email: String) {
        lastResetEmail = email
    }
}
