package com.familycheckin.auth

import kotlinx.coroutines.runBlocking

class AuthViewModel(
    private val repository: AuthRepository
) {
    fun requestPasswordReset(email: String) {
        runBlocking {
            repository.requestPasswordReset(email)
        }
    }
}
