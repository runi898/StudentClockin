package com.familycheckin.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class OccurrenceDateTest {
    @Test
    fun shanghaiMidnightUsesNextLocalDate() {
        val actual = occurrenceLocalDate("2026-05-27T16:00:00Z")
        assertEquals("2026-05-28", actual)
    }
}
