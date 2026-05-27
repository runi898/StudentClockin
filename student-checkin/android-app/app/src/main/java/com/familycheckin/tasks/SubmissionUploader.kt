package com.familycheckin.tasks

interface SubmissionUploader {
    suspend fun upload(
        occurrenceId: String,
        submissionType: String,
        localUri: String
    ): String
}
