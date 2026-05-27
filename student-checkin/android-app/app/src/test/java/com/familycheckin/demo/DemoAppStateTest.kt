package com.familycheckin.demo

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoAppStateTest {
    @Test
    fun completingCheckOnlyTaskAwardsPointsAndMarksCompleted() {
        val clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        val state = DemoAppState(clock = clock)

        val taskId = state.tasks.first { it.mode == TaskMode.CHECK_ONLY }.id
        state.completeTask(taskId)

        val task = state.tasks.first { it.id == taskId }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(1, state.currentPoints)
        assertEquals("任务完成", state.ledger.first().title)
    }

    @Test
    fun finishingStopwatchTaskRecordsElapsedSeconds() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )
        val taskId = state.tasks.first { it.mode == TaskMode.STOPWATCH }.id

        state.startTask(taskId, startedAt = Instant.parse("2026-05-27T10:00:00Z"))
        state.finishTask(taskId, endedAt = Instant.parse("2026-05-27T10:12:30Z"))

        val task = state.tasks.first { it.id == taskId }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(750L, task.actualDurationSeconds)
        assertEquals(2, state.currentPoints)
    }

    @Test
    fun approvingRedemptionDeductsPointsAndUpdatesStatus() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        state.addPointsManually(15, "初始积分")
        val created = state.submitRedemption(10)

        assertTrue(created)
        val requestId = state.redemptionRequests.first().id
        state.approveRedemption(requestId)

        assertEquals(5, state.currentPoints)
        assertEquals(RedemptionStatus.APPROVED, state.redemptionRequests.first().status)
        assertEquals(-10, state.ledger.first().delta)
    }
}
