package com.familycheckin.child

data class ChildProgressSummaryUi(
    val todayCompleted: Int,
    val todayTotal: Int,
    val pendingCount: Int,
    val completionRateText: String,
    val yesterdayText: String,
    val last7DaysText: String,
    val last30DaysText: String
)

data class ChildTaskWindowStatUi(
    val label: String,
    val completedCount: Int,
    val totalCount: Int,
    val completionRateText: String
)

data class ChildTaskInsightUi(
    val taskId: String,
    val taskTitle: String,
    val latestCompletionText: String,
    val yesterdayStat: ChildTaskWindowStatUi,
    val last7DaysStat: ChildTaskWindowStatUi,
    val last30DaysStat: ChildTaskWindowStatUi,
    val last180DaysStat: ChildTaskWindowStatUi
)
