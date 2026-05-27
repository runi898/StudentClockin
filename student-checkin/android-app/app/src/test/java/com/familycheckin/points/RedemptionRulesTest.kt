package com.familycheckin.points

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedemptionRulesTest {
    @Test
    fun redemptionMustBeAtLeastTenAndLessThanBalance() {
        assertTrue(canRedeem(balance = 21, requested = 10))
        assertFalse(canRedeem(balance = 21, requested = 21))
    }
}
