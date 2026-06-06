package com.familycheckin.demo

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoAppStateTest {
    @Test
    fun completingCheckOnlyTaskAwardsPointsAndMarksCompleted() {
        val clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        val state = DemoAppState(clock = clock)

        val taskId = state.tasks.first { it.id == "task-4" }.id
        state.completeTask(taskId)

        val task = state.tasks.first { it.id == taskId }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(28, state.currentPoints)
        assertEquals("Pack schoolbag completed", state.ledger.first().title)
    }

    @Test
    fun finishingStopwatchTaskRecordsElapsedSeconds() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )
        val taskId = "task-3"

        state.startTask(taskId, startedAt = Instant.parse("2026-05-27T10:00:00Z"))
        state.finishTask(taskId, endedAt = Instant.parse("2026-05-27T10:12:30Z"))

        val task = state.tasks.first { it.id == taskId }
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(750L, task.actualDurationSeconds)
        assertEquals(29, state.currentPoints)
    }

    @Test
    fun updatingTaskChangesTemplateValuesInPlace() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        state.updateTask(
            taskId = "task-8",
            title = "Clean desk again",
            mode = TaskMode.COUNTDOWN,
            deliveryRequirement = DeliveryRequirement.AUDIO,
            points = 4,
            targetMinutes = 15,
            scheduledTimeLabel = "21:30"
        )

        val task = state.tasks.first { it.id == "task-8" }
        assertEquals("Clean desk again", task.title)
        assertEquals(TaskMode.COUNTDOWN, task.mode)
        assertEquals(DeliveryRequirement.AUDIO, task.deliveryRequirement)
        assertEquals(4, task.points)
        assertEquals(15, task.targetMinutes)
        assertEquals("21:30", task.scheduledTimeLabel)
    }

    @Test
    fun deletingTaskRemovesItAndRelatedSubmissions() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        state.submitTaskDelivery("task-8", "photo", submittedAt = Instant.parse("2026-05-27T10:00:00Z"))
        assertTrue(state.submissionsForTask("task-8").isNotEmpty())

        state.deleteTask("task-8")

        assertNull(state.tasks.firstOrNull { it.id == "task-8" })
        assertTrue(state.submissionsForTask("task-8").isEmpty())
    }

    @Test
    fun approvingRedemptionDeductsPointsAndUpdatesStatus() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        state.addPointsManually(15, "Initial points")
        val created = state.submitRedemption(10)

        assertTrue(created)
        val requestId = state.redemptionRequests.first().id
        state.approveRedemption(requestId)

        assertEquals(32, state.currentPoints)
        assertEquals(RedemptionStatus.APPROVED, state.redemptionRequests.first().status)
        assertEquals(-10, state.ledger.first().delta)
    }

    @Test
    fun redemptionCanUseEntireCurrentBalance() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        val requested = state.currentPoints
        val created = state.submitRedemption(requested)

        assertTrue(created)
        assertEquals(requested, state.redemptionRequests.first().pointsRequested)
    }

    @Test
    fun parentRowsKeepChildOwnershipAndEveryDemoChildHasVisibleTasks() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        val childIds = state.parentChildren().map { it.memberId }.toSet()
        val rowsByChild = state.parentTaskRows().groupBy { it.childMemberId }

        assertEquals(setOf("child-1", "child-2", "child-3"), childIds)
        childIds.forEach { childId ->
            assertTrue((rowsByChild[childId]?.isNotEmpty() == true), "Expected demo rows for $childId")
        }
        assertNotNull(state.parentTaskRows().firstOrNull { it.id == "task-8" && it.childMemberId == "child-2" })
    }

    @Test
    fun addingTaskStoresSelectedChildOwnership() {
        val state = DemoAppState(
            clock = Clock.fixed(Instant.parse("2026-05-27T10:00:00Z"), ZoneOffset.UTC)
        )

        state.addTask(
            childMemberId = "child-3",
            title = "QA child task",
            mode = TaskMode.CHECK_ONLY,
            deliveryRequirement = DeliveryRequirement.NONE,
            points = 1,
            targetMinutes = null,
            scheduledTimeLabel = "21:30"
        )

        val created = state.tasks.last()
        assertEquals("child-3", created.childMemberId)
        assertEquals("Xiaoan", created.childName)
        assertEquals("task-13", created.id)
    }
}
