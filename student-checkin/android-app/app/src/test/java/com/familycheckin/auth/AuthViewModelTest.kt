package com.familycheckin.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthViewModelTest {
    @Test
    fun passwordResetIntentUsesParentEmail() {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.requestPasswordReset("parent@example.com")

        assertEquals("parent@example.com", repository.lastResetEmail)
    }
}
