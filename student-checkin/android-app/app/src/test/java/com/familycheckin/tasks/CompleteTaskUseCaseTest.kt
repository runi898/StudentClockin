package com.familycheckin.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class CompleteTaskUseCaseTest {
    @Test
    fun completionAwardsConfiguredPoints() {
        val repository = FakeTaskCompletionRepository()
        val useCase = CompleteTaskUseCase(repository)

        useCase.complete(occurrenceId = "occ-1", points = 5)

        assertEquals(5, repository.lastAwardedPoints)
    }
}
