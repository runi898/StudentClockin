package com.familycheckin.parent

enum class ParentDashboardFocus {
    TODAY,
    PENDING,
    REVIEW
}

enum class ParentTaskFilter(val label: String) {
    ALL("全部"),
    PENDING("待完成"),
    RUNNING("进行中"),
    COMPLETED("已完成"),
    REVIEW("待查看")
}

data class ParentChildSummaryUi(
    val memberId: String,
    val childName: String,
    val todayCompleted: Int,
    val todayTotal: Int,
    val pendingCount: Int,
    val runningCount: Int,
    val pendingReviewCount: Int,
    val trendText: String
)

data class ParentOverviewUi(
    val todaySummaryText: String,
    val pendingCount: Int,
    val reviewCount: Int,
    val yesterdayText: String,
    val last7DaysText: String,
    val last30DaysText: String
)

data class ParentTaskRowUi(
    val id: String,
    val taskTemplateId: String,
    val childMemberId: String?,
    val childName: String,
    val title: String,
    val status: ParentTaskFilter,
    val statusLabel: String,
    val timeLabel: String,
    val deliveryLabel: String,
    val pointsLabel: String,
    val note: String,
    val mode: String,
    val deliveryRequirement: String,
    val pointValue: Int,
    val targetMinutes: Int?,
    val scheduledTimeLocal: String?
)
