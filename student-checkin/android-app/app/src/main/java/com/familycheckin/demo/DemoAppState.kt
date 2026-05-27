package com.familycheckin.demo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
    val title: String,
    val mode: TaskMode,
    val deliveryRequirement: DeliveryRequirement,
    val points: Int,
    val targetMinutes: Int?,
    val scheduledTimeLabel: String?,
    val status: TaskStatus = TaskStatus.PENDING,
    val startedAt: Instant? = null,
    val completedAtLabel: String? = null,
    val actualDurationSeconds: Long = 0,
)

data class DemoLedgerEntry(
    val id: String,
    val timeLabel: String,
    val title: String,
    val delta: Int,
    val balanceAfter: Int,
)

data class DemoRedemptionRequest(
    val id: String,
    val childName: String,
    val pointsRequested: Int,
    val cashAmountCny: Double,
    val requestedAtLabel: String,
    val status: RedemptionStatus = RedemptionStatus.PENDING,
)

class DemoAppState(
    private val clock: Clock = Clock.systemUTC()
) {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var nextTaskId = 12

    val childName = "小宇"
    val flashMessage = mutableStateOf("欢迎进入学生任务打卡 MVP。")
    val cashPerTenPoints = mutableStateOf(1)

    val tasks = mutableStateListOf(
        DemoTask("task-1", "晨读 20 分钟", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, 20, "07:10"),
        DemoTask("task-2", "英语跟读", TaskMode.COUNTDOWN, DeliveryRequirement.AUDIO, 2, 15, "07:40"),
        DemoTask("task-3", "数学口算", TaskMode.STOPWATCH, DeliveryRequirement.PHOTO, 2, null, "18:00"),
        DemoTask("task-4", "整理书包", TaskMode.CHECK_ONLY, DeliveryRequirement.PHOTO, 1, null, "20:30"),
        DemoTask("task-5", "练字", TaskMode.COUNTDOWN, DeliveryRequirement.PHOTO, 2, 20, "19:00"),
        DemoTask("task-6", "跳绳", TaskMode.STOPWATCH, DeliveryRequirement.VIDEO, 3, null, "17:30"),
        DemoTask("task-7", "背古诗", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, null, "12:20"),
        DemoTask("task-8", "收拾书桌", TaskMode.CHECK_ONLY, DeliveryRequirement.PHOTO, 1, null, "21:00"),
        DemoTask("task-9", "阅读打卡", TaskMode.STOPWATCH, DeliveryRequirement.NONE, 2, null, "20:00"),
        DemoTask("task-10", "钢琴练习", TaskMode.COUNTDOWN, DeliveryRequirement.AUDIO, 3, 30, "18:30"),
        DemoTask("task-11", "洗漱准备", TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, null, "21:15"),
        DemoTask("task-12", "体育拉伸", TaskMode.STOPWATCH, DeliveryRequirement.VIDEO, 2, null, "06:50"),
    )

    val ledger = mutableStateListOf<DemoLedgerEntry>()
    val redemptionRequests = mutableStateListOf<DemoRedemptionRequest>()

    val currentPoints: Int
        get() = ledger.firstOrNull()?.balanceAfter ?: 0

    fun completeTask(taskId: String, completedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        if (task.status == TaskStatus.COMPLETED) return

        tasks[index] = task.copy(
            status = TaskStatus.COMPLETED,
            startedAt = null,
            completedAtLabel = formatInstant(completedAt),
        )
        addLedgerEntry("任务完成", task.points, completedAt)
        flashMessage.value = "${task.title} 已完成，积分 +${task.points}"
    }

    fun startTask(taskId: String, startedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        if (task.status != TaskStatus.PENDING) return

        tasks[index] = task.copy(status = TaskStatus.RUNNING, startedAt = startedAt)
        flashMessage.value = "${task.title} 已开始计时"
    }

    fun finishTask(taskId: String, endedAt: Instant = clock.instant()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return

        val task = tasks[index]
        val durationSeconds = Duration.between(task.startedAt ?: endedAt, endedAt)
            .seconds
            .coerceAtLeast(0)

        tasks[index] = task.copy(
            status = TaskStatus.COMPLETED,
            startedAt = null,
            completedAtLabel = formatInstant(endedAt),
            actualDurationSeconds = durationSeconds,
        )
        addLedgerEntry("任务完成", task.points, endedAt)
        flashMessage.value = "${task.title} 已结束，用时 ${durationSeconds / 60} 分 ${durationSeconds % 60} 秒"
    }

    fun submitRedemption(points: Int, requestedAt: Instant = clock.instant()): Boolean {
        if (points < 10 || points >= currentPoints) {
            flashMessage.value = "兑换失败：至少 10 积分，且必须小于当前积分。"
            return false
        }

        redemptionRequests.add(
            0,
            DemoRedemptionRequest(
                id = UUID.randomUUID().toString(),
                childName = childName,
                pointsRequested = points,
                cashAmountCny = (points / 10.0) * cashPerTenPoints.value,
                requestedAtLabel = formatInstant(requestedAt),
            )
        )
        flashMessage.value = "已提交兑换申请 ${points} 积分"
        return true
    }

    fun approveRedemption(requestId: String, reviewedAt: Instant = clock.instant()) {
        val index = redemptionRequests.indexOfFirst { it.id == requestId }
        if (index == -1) return

        val request = redemptionRequests[index]
        if (request.status != RedemptionStatus.PENDING) return
        if (request.pointsRequested > currentPoints) {
            flashMessage.value = "当前积分不足，无法通过兑换。"
            return
        }

        redemptionRequests[index] = request.copy(status = RedemptionStatus.APPROVED)
        addLedgerEntry("积分兑换通过", -request.pointsRequested, reviewedAt)
        flashMessage.value = "已通过 ${request.childName} 的兑换申请"
    }

    fun rejectRedemption(requestId: String) {
        val index = redemptionRequests.indexOfFirst { it.id == requestId }
        if (index == -1) return

        val request = redemptionRequests[index]
        if (request.status != RedemptionStatus.PENDING) return

        redemptionRequests[index] = request.copy(status = RedemptionStatus.REJECTED)
        flashMessage.value = "已拒绝 ${request.childName} 的兑换申请"
    }

    fun addTask(
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        nextTaskId += 1
        tasks.add(
            DemoTask(
                id = "task-$nextTaskId",
                title = title,
                mode = mode,
                deliveryRequirement = deliveryRequirement,
                points = points,
                targetMinutes = targetMinutes,
                scheduledTimeLabel = scheduledTimeLabel,
            )
        )
        flashMessage.value = "已新增任务：$title"
    }

    fun resetDay() {
        tasks.replaceAll { task ->
            task.copy(
                status = TaskStatus.PENDING,
                startedAt = null,
                completedAtLabel = null,
                actualDurationSeconds = 0,
            )
        }
        flashMessage.value = "已模拟北京时间 00:00 重置今日任务状态"
    }

    fun addPointsManually(points: Int, title: String, at: Instant = clock.instant()) {
        addLedgerEntry(title, points, at)
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
                balanceAfter = nextBalance,
            )
        )
    }

    private fun formatInstant(instant: Instant): String {
        return instant.atZone(zoneId).format(formatter)
    }
}
