package com.familycheckin.server

import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerUiMapperTest {
    @Test
    fun occurrenceSnapshotMapsToDemoTask() {
        val occurrence = ServerTaskOccurrence(
            id = "occ-1",
            taskNameSnapshot = "数学口算",
            modeSnapshot = "stopwatch",
            deliveryRequirementSnapshot = "photo",
            pointValueSnapshot = 2,
            targetDurationSecondsSnapshot = 900,
            scheduledTimeLocal = "18:00",
            status = "completed",
            startedAt = "2026-05-27T10:00:00Z",
            completedAt = "2026-05-27T10:12:30Z",
            actualDurationSeconds = 750
        )

        val task = occurrence.toDemoTask()

        assertEquals("occ-1", task.id)
        assertEquals("数学口算", task.title)
        assertEquals(TaskMode.STOPWATCH, task.mode)
        assertEquals(DeliveryRequirement.PHOTO, task.deliveryRequirement)
        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals(15, task.targetMinutes)
        assertEquals(750, task.actualDurationSeconds)
    }
}
