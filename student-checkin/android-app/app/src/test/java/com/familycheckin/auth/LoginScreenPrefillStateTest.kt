package com.familycheckin.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class LoginScreenPrefillStateTest {
    @Test
    fun `uses recent email and remembered password when enabled`() {
        val state = buildLoginPrefillState(
            recentEmail = "parent@example.com",
            rememberedPassword = "example-password",
            rememberedPasswordEnabled = true
        )

        assertEquals("parent@example.com", state.first)
        assertEquals("example-password", state.second)
        assertEquals(true, state.third)
    }

    @Test
    fun `clears password when remember password is disabled`() {
        val state = buildLoginPrefillState(
            recentEmail = "parent@example.com",
            rememberedPassword = "example-password",
            rememberedPasswordEnabled = false
        )

        assertEquals("parent@example.com", state.first)
        assertEquals("", state.second)
        assertEquals(false, state.third)
    }

    @Test
    fun `hides coroutine cancellation banner on login screen`() {
        assertEquals(false, shouldShowLoginStatusBanner("The coroutine scope left the composition"))
    }
}
