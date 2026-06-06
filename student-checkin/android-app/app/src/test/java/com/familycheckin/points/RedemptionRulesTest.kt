package com.familycheckin.points

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class RedemptionRulesTest {
    @Test
    fun redemptionMustRespectConfiguredMinimumAndAllowUsingFullBalance() {
        assertTrue(canRedeem(balance = 21, requested = 10, minRedeemPoints = 10))
        assertTrue(canRedeem(balance = 21, requested = 21, minRedeemPoints = 10))
        assertFalse(canRedeem(balance = 21, requested = 9, minRedeemPoints = 10))
        assertFalse(canRedeem(balance = 21, requested = 22, minRedeemPoints = 10))
    }

    @Test
    fun cashAmountUsesConfiguredRatio() {
        assertEquals(2.0, cashForPoints(requested = 10, cashPerTenPoints = 2))
        assertEquals(3.0, cashForPoints(requested = 15, cashPerTenPoints = 2))
    }
}
