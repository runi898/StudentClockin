package com.familycheckin.server

import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import com.familycheckin.parent.ParentTaskFilter
import com.familycheckin.parent.RedemptionRequestStatusUi
import com.familycheckin.parent.RedemptionRequestUi
import com.familycheckin.parent.RedemptionStatsUi
import com.familycheckin.points.LedgerEntryUi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val beijingZoneId: ZoneId = ZoneId.of("Asia/Shanghai")
private val beijingDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

enum class ServerRole {
    CHILD,
    PARENT
}

@Serializable
data class ServerMemberContext(
    @SerialName("profile_id")
    val profileId: String,
    @SerialName("family_id")
    val familyId: String,
    @SerialName("member_id")
    val memberId: String,
    val role: String,
    @SerialName("display_name")
    val displayName: String,
    val email: String,
    @SerialName("cash_cny_per_10_points")
    val cashCnyPer10Points: Double,
    @SerialName("min_redeem_points")
    val minRedeemPoints: Int,
    @SerialName("family_name")
    val familyName: String? = null,
    val timezone: String? = null,
    @SerialName("media_retention_days")
    val mediaRetentionDays: Int = 30
)

@Serializable
data class ServerTaskOccurrence(
    val id: String,
    @SerialName("task_template_id")
    val taskTemplateId: String? = null,
    @SerialName("child_member_id")
    val childMemberId: String? = null,
    @SerialName("task_name_snapshot")
    val taskNameSnapshot: String,
    @SerialName("mode_snapshot")
    val modeSnapshot: String,
    @SerialName("delivery_requirement_snapshot")
    val deliveryRequirementSnapshot: String,
    @SerialName("point_value_snapshot")
    val pointValueSnapshot: Int,
    @SerialName("target_duration_seconds_snapshot")
    val targetDurationSecondsSnapshot: Int? = null,
    @SerialName("scheduled_time_local")
    val scheduledTimeLocal: String? = null,
    val status: String,
    @SerialName("started_at")
    val startedAt: String? = null,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("actual_duration_seconds")
    val actualDurationSeconds: Int? = null,
    @SerialName("child_name")
    val childName: String? = null
)

@Serializable
data class ServerLedgerEntry(
    val id: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("change_type")
    val changeType: String,
    @SerialName("points_delta")
    val pointsDelta: Int,
    @SerialName("balance_after")
    val balanceAfter: Int,
    val note: String? = null
)

@Serializable
data class ServerRedemptionRequest(
    val id: String,
    @SerialName("child_name")
    val childName: String,
    @SerialName("points_requested")
    val pointsRequested: Int,
    @SerialName("cash_amount_cny")
    val cashAmountCny: Double,
    @SerialName("requested_at")
    val requestedAt: String,
    val status: String
)

@Serializable
data class ServerRedemptionStats(
    @SerialName("last_7_days_count")
    val last7DaysCount: Int,
    @SerialName("last_7_days_cash_cny")
    val last7DaysCashCny: Double,
    @SerialName("current_month_count")
    val currentMonthCount: Int,
    @SerialName("current_month_cash_cny")
    val currentMonthCashCny: Double
)

@Serializable
data class ServerFamilyChild(
    val id: String,
    @SerialName("child_display_name")
    val childDisplayName: String? = null,
    val role: String
)

@Serializable
data class ServerChildAccount(
    @SerialName("member_id")
    val memberId: String,
    @SerialName("child_name")
    val childName: String,
    val email: String,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class ServerNotificationChannel(
    val id: String,
    @SerialName("channel_type")
    val channelType: String,
    @SerialName("config_json")
    val configJson: JsonObject,
    @SerialName("is_enabled")
    val isEnabled: Boolean
)

val ServerNotificationChannel.webhookUrl: String
    get() = configJson["webhook_url"]?.jsonPrimitive?.content.orEmpty()

@Serializable
data class ServerMemberFamilyContext(
    @SerialName("family_name")
    val familyName: String? = null,
    val timezone: String? = null,
    @SerialName("media_retention_days")
    val mediaRetentionDays: Int = 30
)

@Serializable
data class ServerDailyChildReport(
    @SerialName("child_member_id")
    val childMemberId: String,
    @SerialName("local_date")
    val localDate: String,
    @SerialName("task_total_count")
    val taskTotalCount: Int,
    @SerialName("task_completed_count")
    val taskCompletedCount: Int,
    @SerialName("total_duration_seconds")
    val totalDurationSeconds: Int = 0,
    @SerialName("points_awarded")
    val pointsAwarded: Int = 0,
    @SerialName("points_redeemed")
    val pointsRedeemed: Int = 0,
    @SerialName("redemption_cash_cny")
    val redemptionCashCny: Double = 0.0
)

@Serializable
data class ServerTaskHistorySeed(
    val id: String = "",
    @SerialName("task_template_id")
    val taskTemplateId: String,
    @SerialName("task_name_snapshot")
    val taskNameSnapshot: String = ""
)

@Serializable
data class ServerTaskHistoryOccurrence(
    @SerialName("local_date")
    val localDate: String,
    val status: String,
    @SerialName("completed_at")
    val completedAt: String? = null
)

data class ServerTaskHistoryDetail(
    val taskTitle: String,
    val occurrences: List<ServerTaskHistoryOccurrence>
)

@Serializable
data class ServerTaskSubmission(
    val id: String,
    @SerialName("occurrence_id")
    val occurrenceId: String,
    @SerialName("submission_type")
    val submissionType: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("uploaded_at")
    val uploadedAt: String
)

data class ParentSignUpRequest(
    val familyName: String,
    val parentName: String,
    val parentEmail: String,
    val parentPassword: String,
    val firstChildName: String,
    val firstChildEmail: String,
    val firstChildPassword: String,
    val cashCnyPer10Points: Double = 1.0,
    val minRedeemPoints: Int = 10,
    val mediaRetentionDays: Int = 30,
    val timezone: String = "Asia/Shanghai"
)

data class CreateChildAccountRequest(
    val childName: String,
    val childEmail: String,
    val childPassword: String
)

data class ServerSignUpResult(
    val memberContext: ServerMemberContext,
    val createdChild: ServerChildAccount?
)

fun ServerMemberContext.toServerRole(): ServerRole {
    return when (role) {
        "parent" -> ServerRole.PARENT
        else -> ServerRole.CHILD
    }
}

fun ServerTaskOccurrence.toDemoTask(): DemoTask {
    val title = if (childName.isNullOrBlank()) {
        taskNameSnapshot
    } else {
        "$childName · $taskNameSnapshot"
    }

    return DemoTask(
        id = id,
        childMemberId = childMemberId ?: "",
        childName = childName ?: "",
        title = title,
        mode = modeSnapshot.toTaskMode(),
        deliveryRequirement = deliveryRequirementSnapshot.toDeliveryRequirement(),
        points = pointValueSnapshot,
        targetMinutes = targetDurationSecondsSnapshot?.div(60),
        scheduledTimeLabel = scheduledTimeLocal,
        status = status.toTaskStatus(),
        startedAt = startedAt?.let(::parseServerInstant),
        completedAtLabel = completedAt?.let(::formatBeijingDateTime),
        actualDurationSeconds = actualDurationSeconds?.toLong() ?: 0L
    )
}

fun ServerTaskOccurrence.toParentTaskFilter(): ParentTaskFilter {
    return when (status) {
        "running" -> ParentTaskFilter.RUNNING
        "completed" -> ParentTaskFilter.COMPLETED
        else -> ParentTaskFilter.PENDING
    }
}

fun ServerLedgerEntry.toLedgerEntryUi(): LedgerEntryUi {
    return LedgerEntryUi(
        timeLabel = formatBeijingDateTime(createdAt),
        title = note
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeLedgerNote)
            ?: defaultLedgerTitle(changeType),
        delta = pointsDelta,
        balanceAfter = balanceAfter
    )
}

fun ServerRedemptionRequest.toUi(): RedemptionRequestUi {
    val statusUi = when (status) {
        "approved" -> RedemptionRequestStatusUi.APPROVED
        "rejected" -> RedemptionRequestStatusUi.REJECTED
        else -> RedemptionRequestStatusUi.PENDING
    }
    val statusText = when (statusUi) {
        RedemptionRequestStatusUi.APPROVED -> "已通过"
        RedemptionRequestStatusUi.REJECTED -> "已驳回"
        RedemptionRequestStatusUi.PENDING -> "待审核"
    }

    return RedemptionRequestUi(
        id = id,
        childName = childName,
        pointsRequested = pointsRequested,
        cashAmountCny = cashAmountCny,
        requestedAtLabel = "${formatBeijingDateTime(requestedAt)} · $statusText",
        status = statusUi
    )
}

fun ServerRedemptionStats.toUi(): RedemptionStatsUi {
    return RedemptionStatsUi(
        last7DaysCount = last7DaysCount,
        last7DaysCashCny = last7DaysCashCny,
        currentMonthCount = currentMonthCount,
        currentMonthCashCny = currentMonthCashCny
    )
}

fun ServerDailyChildReport.toLocalDate(): LocalDate = parseServerLocalDate(localDate)

fun ServerTaskHistoryOccurrence.toLocalDate(): LocalDate = parseServerLocalDate(localDate)

fun ServerTaskSubmission.publicUrl(baseUrl: String): String {
    return "${baseUrl.trimEnd('/')}/storage/v1/object/public/task-deliveries/$storagePath"
}

fun ServerTaskSubmission.uploadedAtLabel(): String = formatBeijingDateTime(uploadedAt)

fun ServerChildAccount.createdAtLabel(): String =
    createdAt.takeIf { it.isNotBlank() }?.let(::formatBeijingDateTime).orEmpty()

private fun String.toTaskMode(): TaskMode {
    return when (this) {
        "countdown" -> TaskMode.COUNTDOWN
        "stopwatch" -> TaskMode.STOPWATCH
        else -> TaskMode.CHECK_ONLY
    }
}

private fun String.toDeliveryRequirement(): DeliveryRequirement {
    return when (this) {
        "photo" -> DeliveryRequirement.PHOTO
        "video" -> DeliveryRequirement.VIDEO
        "audio" -> DeliveryRequirement.AUDIO
        else -> DeliveryRequirement.NONE
    }
}

private fun String.toTaskStatus(): TaskStatus {
    return when (this) {
        "running" -> TaskStatus.RUNNING
        "completed" -> TaskStatus.COMPLETED
        else -> TaskStatus.PENDING
    }
}

private fun defaultLedgerTitle(changeType: String): String {
    return when (changeType) {
        "task_reward" -> "任务完成"
        "redemption_approved" -> "积分兑换通过"
        "manual_adjust" -> "家长调整积分"
        else -> "积分变动"
    }
}

private fun normalizeLedgerNote(note: String): String {
    val trimmed = note.trim()
    val lower = trimmed.lowercase()
    return when {
        lower == "redemption approved" -> "积分兑换通过"
        lower.endsWith(" completed") -> "${trimmed.removeSuffix(" completed")} 已完成"
        else -> trimmed
    }
}

private fun formatBeijingDateTime(value: String): String {
    return parseServerInstant(value).atZone(beijingZoneId).format(beijingDateTimeFormatter)
}

private fun parseServerInstant(value: String): Instant {
    return OffsetDateTime.parse(value).toInstant()
}

private fun parseServerLocalDate(value: String): LocalDate {
    return runCatching { LocalDate.parse(value) }
        .getOrElse {
            OffsetDateTime.parse(value).toInstant().atZone(beijingZoneId).toLocalDate()
        }
}
