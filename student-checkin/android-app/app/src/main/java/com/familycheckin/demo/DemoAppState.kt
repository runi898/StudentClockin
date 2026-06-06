package com.familycheckin.demo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.familycheckin.child.ChildProgressSummaryUi
import com.familycheckin.child.ChildTaskInsightUi
import com.familycheckin.child.ChildTaskWindowStatUi
import com.familycheckin.parent.ParentChildSummaryUi
import com.familycheckin.parent.ParentOverviewUi
import com.familycheckin.parent.ParentTaskFilter
import com.familycheckin.parent.ParentTaskRowUi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class TaskMode {
    CHECK_ONLY,
    COUNTDOWN,
    STOPWATCH
}

enum class DeliveryRequirement {
    NONE,
    PHOTO,
    VIDEO,
    AUDIO
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED
}

enum class RedemptionStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class DemoTask(
    val id: String,
    val childMemberId: String,
    val childName: String,
    val title: String,
    val mode: TaskMode,
    val deliveryRequirement: DeliveryRequirement,
    val points: Int,
    val targetMinutes: Int?,
    val scheduledTimeLabel: String?,
    val status: TaskStatus = TaskStatus.PENDING,
    val startedAt: Instant? = null,
    val completedAtLabel: String? = null,
    val actualDurationSeconds: Long = 0
)

data class DemoLedgerEntry(
    val id: String,
    val timeLabel: String,
    val title: String,
    val delta: Int,
    val balanceAfter: Int
)

data class DemoRedemptionRequest(
    val id: String,
    val childName: String,
    val pointsRequested: Int,
    val cashAmountCny: Double,
    val requestedAtLabel: String,
    val status: RedemptionStatus = RedemptionStatus.PENDING
)

data class DemoTaskSubmission(
    val id: String,
    val occurrenceId: String,
    val submissionType: String,
    val storagePath: String,
    val uploadedAtLabel: String
)

private data class DemoChild(
    val memberId: String,
    val childName: String,
    val trendText: String
)

class DemoAppState(
    private val clock: Clock = Clock.systemUTC()
) {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var nextTaskId = 12
    private val primaryChildId = "child-1"
    private val demoChildren = listOf(
        DemoChild(memberId = "child-1", childName = "Xiaoyu", trendText = "Last 7 days 89%"),
        DemoChild(memberId = "child-2", childName = "Xiaoning", trendText = "Last 7 days 91%"),
        DemoChild(memberId = "child-3", childName = "Xiaoan", trendText = "Last 7 days 94%")
    )

    val childName = "Xiaoyu"
    val flashMessage = mutableStateOf("Welcome to the demo app")
    val cashPerTenPoints = mutableStateOf(1)

    val tasks = mutableStateListOf(
        DemoTask("task-1", "child-1", "Xiaoyu", "Morning reading", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, 20, "07:10", TaskStatus.COMPLETED, completedAtLabel = "2026-05-28 07:28:10"),
        DemoTask("task-2", "child-1", "Xiaoyu", "English shadowing", TaskMode.COUNTDOWN, DeliveryRequirement.AUDIO, 2, 15, "07:40", TaskStatus.COMPLETED, completedAtLabel = "2026-05-28 07:56:42"),
        DemoTask("task-3", "child-1", "Xiaoyu", "Math drill", TaskMode.STOPWATCH, DeliveryRequirement.PHOTO, 2, null, "18:00"),
        DemoTask("task-4", "child-1", "Xiaoyu", "Pack schoolbag", TaskMode.CHECK_ONLY, DeliveryRequirement.PHOTO, 1, null, "20:30"),
        DemoTask("task-5", "child-2", "Xiaoning", "Handwriting", TaskMode.COUNTDOWN, DeliveryRequirement.PHOTO, 2, 20, "19:00"),
        DemoTask("task-6", "child-2", "Xiaoning", "Jump rope", TaskMode.STOPWATCH, DeliveryRequirement.VIDEO, 3, null, "17:30", TaskStatus.RUNNING, startedAt = Instant.now(clock).minusSeconds(480)),
        DemoTask("task-7", "child-2", "Xiaoning", "Poem recitation", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, null, "12:20", TaskStatus.COMPLETED, completedAtLabel = "2026-05-28 12:25:00"),
        DemoTask("task-8", "child-2", "Xiaoning", "Clean desk", TaskMode.CHECK_ONLY, DeliveryRequirement.PHOTO, 1, null, "21:00"),
        DemoTask("task-9", "child-3", "Xiaoan", "Reading log", TaskMode.STOPWATCH, DeliveryRequirement.NONE, 2, null, "20:00", TaskStatus.COMPLETED, completedAtLabel = "2026-05-28 20:28:00", actualDurationSeconds = 1680),
        DemoTask("task-10", "child-3", "Xiaoan", "Piano practice", TaskMode.COUNTDOWN, DeliveryRequirement.AUDIO, 3, 30, "18:30"),
        DemoTask("task-11", "child-3", "Xiaoan", "Wash up", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, null, "21:15", TaskStatus.COMPLETED, completedAtLabel = "2026-05-28 21:20:00"),
        DemoTask("task-12", "child-3", "Xiaoan", "Stretching", TaskMode.STOPWATCH, DeliveryRequirement.VIDEO, 2, null, "06:50", TaskStatus.PENDING, completedAtLabel = null, actualDurationSeconds = 0)
    )

    val ledger = mutableStateListOf(
        DemoLedgerEntry(UUID.randomUUID().toString(), "2026-05-28 21:20:00", "Wash up completed", 1, 27),
        DemoLedgerEntry(UUID.randomUUID().toString(), "2026-05-28 20:28:00", "Reading log completed", 2, 26),
        DemoLedgerEntry(UUID.randomUUID().toString(), "2026-05-28 12:25:00", "Poem recitation completed", 1, 24),
        DemoLedgerEntry(UUID.randomUUID().toString(), "2026-05-28 07:56:42", "English shadowing completed", 2, 23),
        DemoLedgerEntry(UUID.randomUUID().toString(), "2026-05-28 07:28:10", "Morning reading completed", 1, 21)
    )
    val redemptionRequests = mutableStateListOf<DemoRedemptionRequest>()
    val taskSubmissions = mutableStateListOf<DemoTaskSubmission>()

    val currentPoints: Int
        get() = ledger.firstOrNull()?.balanceAfter ?: 0

    fun childTasks(): List<DemoTask> = tasks.filter { it.childMemberId == primaryChildId }

    fun completeTask(taskId: String, completedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        if (task.status == TaskStatus.COMPLETED) return

        tasks[index] = task.copy(
            status = TaskStatus.COMPLETED,
            startedAt = null,
            completedAtLabel = formatInstant(completedAt)
        )
        addLedgerEntry("${task.title} completed", task.points, completedAt)
        flashMessage.value = "${task.title} completed, points +${task.points}"
    }

    fun startTask(taskId: String, startedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        if (task.status != TaskStatus.PENDING) return

        tasks[index] = task.copy(status = TaskStatus.RUNNING, startedAt = startedAt)
        flashMessage.value = "${task.title} started"
    }

    fun finishTask(taskId: String, endedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        val startedAt = task.startedAt ?: endedAt
        val durationSeconds = Duration.between(startedAt, endedAt).seconds.coerceAtLeast(0)

        tasks[index] = task.copy(
            status = TaskStatus.COMPLETED,
            startedAt = null,
            completedAtLabel = formatInstant(endedAt),
            actualDurationSeconds = durationSeconds
        )
        addLedgerEntry("${task.title} completed", task.points, endedAt)
        flashMessage.value = "${task.title} finished, duration ${durationSeconds}s"
    }

    fun submitTaskDelivery(
        taskId: String,
        submissionType: String,
        submittedAt: Instant = clock.instant()
    ) {
        taskSubmissions.add(
            0,
            DemoTaskSubmission(
                id = UUID.randomUUID().toString(),
                occurrenceId = taskId,
                submissionType = submissionType,
                storagePath = "demo://$taskId/${UUID.randomUUID()}",
                uploadedAtLabel = formatInstant(submittedAt)
            )
        )
        val task = tasks.firstOrNull { it.id == taskId } ?: return
        if (task.status == TaskStatus.RUNNING) {
            finishTask(taskId, submittedAt)
        } else {
            completeTask(taskId, submittedAt)
        }
        flashMessage.value = "${task.title} uploaded and auto-completed"
    }

    fun submitRedemption(points: Int, requestedAt: Instant = clock.instant()): Boolean {
        if (points < 10 || points > currentPoints) {
            flashMessage.value = "Redemption must be at least 10 and not exceed current points"
            return false
        }

        redemptionRequests.add(
            0,
            DemoRedemptionRequest(
                id = UUID.randomUUID().toString(),
                childName = childName,
                pointsRequested = points,
                cashAmountCny = (points / 10.0) * cashPerTenPoints.value,
                requestedAtLabel = formatInstant(requestedAt)
            )
        )
        flashMessage.value = "Redemption request submitted: $points points, waiting for parent review"
        return true
    }

    fun approveRedemption(requestId: String, reviewedAt: Instant = clock.instant()) {
        val index = redemptionRequests.indexOfFirst { it.id == requestId }
        if (index == -1) return

        val request = redemptionRequests[index]
        if (request.status != RedemptionStatus.PENDING) return
        if (request.pointsRequested > currentPoints) {
            flashMessage.value = "Not enough points to approve this redemption"
            return
        }

        redemptionRequests[index] = request.copy(status = RedemptionStatus.APPROVED)
        addLedgerEntry("Redemption approved", -request.pointsRequested, reviewedAt)
        flashMessage.value = "Approved ${request.childName}'s redemption"
    }

    fun rejectRedemption(requestId: String) {
        val index = redemptionRequests.indexOfFirst { it.id == requestId }
        if (index == -1) return

        val request = redemptionRequests[index]
        if (request.status != RedemptionStatus.PENDING) return

        redemptionRequests[index] = request.copy(status = RedemptionStatus.REJECTED)
        flashMessage.value = "Rejected ${request.childName}'s redemption"
    }

    fun addTask(
        childMemberId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        nextTaskId += 1
        val child = demoChildren.firstOrNull { it.memberId == childMemberId } ?: demoChildren.first()
        tasks.add(
            DemoTask(
                id = "task-$nextTaskId",
                childMemberId = child.memberId,
                childName = child.childName,
                title = title,
                mode = mode,
                deliveryRequirement = deliveryRequirement,
                points = points,
                targetMinutes = targetMinutes,
                scheduledTimeLabel = scheduledTimeLabel
            )
        )
        flashMessage.value = "Task created: $title"
    }

    fun updateTask(
        taskId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        tasks[index] = task.copy(
            title = title,
            mode = mode,
            deliveryRequirement = deliveryRequirement,
            points = points,
            targetMinutes = targetMinutes,
            scheduledTimeLabel = scheduledTimeLabel
        )
        flashMessage.value = "Task updated: $title"
    }

    fun deleteTask(taskId: String) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val removedTask = tasks.removeAt(index)
        taskSubmissions.removeAll { it.occurrenceId == taskId }
        flashMessage.value = "Task deleted: ${removedTask.title}"
    }

    fun resetDay() {
        tasks.replaceAll { task ->
            task.copy(
                status = TaskStatus.PENDING,
                startedAt = null,
                completedAtLabel = null,
                actualDurationSeconds = 0
            )
        }
        flashMessage.value = "Today reset at 00:00"
    }

    fun addPointsManually(points: Int, title: String, at: Instant = clock.instant()) {
        addLedgerEntry(title, points, at)
    }

    fun childSummary(): ChildProgressSummaryUi {
        val currentChildTasks = childTasks()
        val completed = currentChildTasks.count { it.status == TaskStatus.COMPLETED }
        val total = currentChildTasks.size
        val pending = currentChildTasks.count { it.status != TaskStatus.COMPLETED }
        return ChildProgressSummaryUi(
            todayCompleted = completed,
            todayTotal = total,
            pendingCount = pending,
            completionRateText = "${(completed * 100 / total.coerceAtLeast(1))}%",
            yesterdayText = "Yesterday 10 / 12",
            last7DaysText = "Last 7 days 89%",
            last30DaysText = "Last 30 days 92%"
        )
    }

    fun taskInsight(taskId: String): ChildTaskInsightUi? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        val base = (task.id.filter { it.isDigit() }.toIntOrNull() ?: 1).coerceAtLeast(1)
        return ChildTaskInsightUi(
            taskId = taskId,
            taskTitle = task.title,
            latestCompletionText = task.completedAtLabel ?: "最近暂无完成记录",
            yesterdayStat = demoWindowStat("昨天", (base + 1) % 2, 1),
            last7DaysStat = demoWindowStat("近 7 天", 5 + (base % 2), 7),
            last30DaysStat = demoWindowStat("近 30 天", 22 + (base % 5), 30),
            last180DaysStat = demoWindowStat("近半年", 144 + (base % 20), 180)
        )
    }

    fun parentChildren(): List<ParentChildSummaryUi> {
        return demoChildren.map { child ->
            val childTasks = tasks.filter { it.childMemberId == child.memberId }
            ParentChildSummaryUi(
                memberId = child.memberId,
                childName = child.childName,
                todayCompleted = childTasks.count { it.status == TaskStatus.COMPLETED },
                todayTotal = childTasks.size,
                pendingCount = childTasks.count { it.status == TaskStatus.PENDING },
                runningCount = childTasks.count { it.status == TaskStatus.RUNNING },
                pendingReviewCount = taskSubmissions.count { submission ->
                    tasks.firstOrNull { it.id == submission.occurrenceId }?.childMemberId == child.memberId
                },
                trendText = child.trendText
            )
        }
    }

    fun parentOverview(): ParentOverviewUi {
        return ParentOverviewUi(
            todaySummaryText = "${tasks.count { it.status == TaskStatus.COMPLETED }} / ${tasks.size}",
            pendingCount = tasks.count { it.status != TaskStatus.COMPLETED },
            reviewCount = taskSubmissions.size,
            yesterdayText = "Yesterday 25 / 28",
            last7DaysText = "Last 7 days 88%",
            last30DaysText = "Last 30 days 91%"
        )
    }

    fun parentTaskRows(): List<ParentTaskRowUi> {
        return tasks
            .sortedWith(compareBy<DemoTask>({ sortWeight(it.status) }, { it.scheduledTimeLabel ?: "99:99" }))
            .map {
                val latestSubmission = taskSubmissions.firstOrNull { submission -> submission.occurrenceId == it.id }
                ParentTaskRowUi(
                    id = it.id,
                    taskTemplateId = it.id,
                    childMemberId = it.childMemberId,
                    childName = it.childName,
                    title = it.title,
                    status = when (it.status) {
                        TaskStatus.PENDING -> ParentTaskFilter.PENDING
                        TaskStatus.RUNNING -> ParentTaskFilter.RUNNING
                        TaskStatus.COMPLETED -> ParentTaskFilter.COMPLETED
                    },
                    statusLabel = when (it.status) {
                        TaskStatus.PENDING -> "待完成"
                        TaskStatus.RUNNING -> "进行中"
                        TaskStatus.COMPLETED -> "已完成"
                    },
                    timeLabel = it.scheduledTimeLabel ?: "Today",
                    deliveryLabel = when (it.deliveryRequirement) {
                        DeliveryRequirement.NONE -> "No requirement"
                        DeliveryRequirement.PHOTO -> "Photo"
                        DeliveryRequirement.VIDEO -> "Video"
                        DeliveryRequirement.AUDIO -> "Audio"
                    },
                    pointsLabel = "+${it.points} pts",
                    note = listOfNotNull(
                        when (it.mode) {
                            TaskMode.CHECK_ONLY -> "Check only"
                            TaskMode.COUNTDOWN -> "Countdown ${it.targetMinutes ?: 0} min"
                            TaskMode.STOPWATCH -> "Manual timing"
                        },
                        latestSubmission?.let { submission -> "Uploaded ${submission.submissionType}" }
                    ).joinToString(" | "),
                    mode = when (it.mode) {
                        TaskMode.CHECK_ONLY -> "check_only"
                        TaskMode.COUNTDOWN -> "countdown"
                        TaskMode.STOPWATCH -> "stopwatch"
                    },
                    deliveryRequirement = when (it.deliveryRequirement) {
                        DeliveryRequirement.NONE -> "none"
                        DeliveryRequirement.PHOTO -> "photo"
                        DeliveryRequirement.VIDEO -> "video"
                        DeliveryRequirement.AUDIO -> "audio"
                    },
                    pointValue = it.points,
                    targetMinutes = it.targetMinutes,
                    scheduledTimeLocal = it.scheduledTimeLabel
                )
            }
    }

    fun submissionsForTask(taskId: String): List<DemoTaskSubmission> {
        return taskSubmissions.filter { it.occurrenceId == taskId }
    }

    private fun addLedgerEntry(title: String, delta: Int, at: Instant) {
        val nextBalance = currentPoints + delta
        ledger.add(
            0,
            DemoLedgerEntry(
                id = UUID.randomUUID().toString(),
                timeLabel = formatInstant(at),
                title = title,
                delta = delta,
                balanceAfter = nextBalance
            )
        )
    }

    private fun formatInstant(instant: Instant): String {
        return instant.atZone(zoneId).format(formatter)
    }

    private fun sortWeight(status: TaskStatus): Int {
        return when (status) {
            TaskStatus.RUNNING -> 0
            TaskStatus.PENDING -> 1
            TaskStatus.COMPLETED -> 2
        }
    }

    private fun demoWindowStat(label: String, completed: Int, total: Int): ChildTaskWindowStatUi {
        return ChildTaskWindowStatUi(
            label = label,
            completedCount = completed,
            totalCount = total,
            completionRateText = "${(completed * 100 / total.coerceAtLeast(1))}%"
        )
    }
}
