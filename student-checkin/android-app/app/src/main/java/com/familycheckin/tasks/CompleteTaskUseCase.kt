package com.familycheckin.tasks

interface TaskCompletionRepository {
    fun completeOccurrence(occurrenceId: String, points: Int)
}

class FakeTaskCompletionRepository : TaskCompletionRepository {
    var lastAwardedPoints: Int? = null

    override fun completeOccurrence(occurrenceId: String, points: Int) {
        lastAwardedPoints = points
    }
}

class CompleteTaskUseCase(
    private val repository: TaskCompletionRepository
) {
    fun complete(occurrenceId: String, points: Int) {
        repository.completeOccurrence(occurrenceId, points)
    }
}
