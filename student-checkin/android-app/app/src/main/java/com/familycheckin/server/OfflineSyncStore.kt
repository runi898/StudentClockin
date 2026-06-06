package com.familycheckin.server

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OfflineAppSnapshot(
    val memberContext: ServerMemberContext,
    val childSourceTasks: List<ServerTaskOccurrence> = emptyList(),
    val parentSourceTasks: List<ServerTaskOccurrence> = emptyList(),
    val ledgerEntries: List<ServerLedgerEntry> = emptyList(),
    val redemptions: List<ServerRedemptionRequest> = emptyList(),
    val redemptionStats: ServerRedemptionStats = ServerRedemptionStats(0, 0.0, 0, 0.0),
    val familyChildren: List<ServerFamilyChild> = emptyList(),
    val childAccounts: List<ServerChildAccount> = emptyList(),
    val notificationChannels: List<ServerNotificationChannel> = emptyList(),
    val dailyReports: List<ServerDailyChildReport> = emptyList(),
    val submissions: List<ServerTaskSubmission> = emptyList()
)

@Serializable
sealed class PendingSyncAction {
    @Serializable
    @SerialName("start_task")
    data class StartTask(val occurrenceId: String) : PendingSyncAction()

    @Serializable
    @SerialName("complete_task")
    data class CompleteTask(
        val occurrenceId: String,
        val taskTitle: String,
        val points: Int
    ) : PendingSyncAction()

    @Serializable
    @SerialName("finish_task")
    data class FinishTask(
        val occurrenceId: String,
        val actualDurationSeconds: Int,
        val taskTitle: String,
        val points: Int
    ) : PendingSyncAction()

    @Serializable
    @SerialName("submit_delivery")
    data class SubmitTaskDelivery(
        val occurrenceId: String,
        val submissionType: String,
        val fileName: String,
        val contentType: String,
        val localMediaKey: String,
        val completionMode: DeliveryCompletionMode,
        val actualDurationSeconds: Int,
        val taskTitle: String,
        val points: Int
    ) : PendingSyncAction()

    @Serializable
    @SerialName("submit_redemption")
    data class SubmitRedemption(val pointsRequested: Int) : PendingSyncAction()

    @Serializable
    @SerialName("review_redemption")
    data class ReviewRedemption(
        val requestId: String,
        val approve: Boolean,
        val childName: String,
        val pointsRequested: Int
    ) : PendingSyncAction()

    @Serializable
    @SerialName("save_notification_settings")
    data class SaveNotificationSettings(
        val channelId: String?,
        val channelType: String,
        val webhookUrl: String,
        val isEnabled: Boolean
    ) : PendingSyncAction()

    @Serializable
    @SerialName("update_family_settings")
    data class UpdateFamilySettings(
        val familyName: String,
        val cashCnyPer10Points: Int,
        val minRedeemPoints: Int,
        val mediaRetentionDays: Int
    ) : PendingSyncAction()

    @Serializable
    @SerialName("update_child_profile")
    data class UpdateChildProfile(
        val memberId: String,
        val childName: String
    ) : PendingSyncAction()

    @Serializable
    @SerialName("add_task")
    data class AddTask(
        val childMemberId: String,
        val name: String,
        val mode: String,
        val deliveryRequirement: String,
        val points: Int,
        val targetMinutes: Int?,
        val scheduledTime: String?
    ) : PendingSyncAction()

    @Serializable
    @SerialName("update_task")
    data class UpdateTask(
        val taskTemplateId: String,
        val name: String,
        val mode: String,
        val deliveryRequirement: String,
        val points: Int,
        val targetMinutes: Int?,
        val scheduledTime: String?
    ) : PendingSyncAction()

    @Serializable
    @SerialName("delete_task")
    data class DeleteTask(
        val occurrenceId: String? = null,
        val taskTemplateId: String? = null
    ) : PendingSyncAction()

    @Serializable
    @SerialName("reset_day")
    data object ResetDay : PendingSyncAction()
}

@Serializable
enum class DeliveryCompletionMode {
    COMPLETE,
    FINISH
}

interface OfflineStateStore {
    fun loadSnapshot(memberId: String? = null): OfflineAppSnapshot?
    fun saveSnapshot(snapshot: OfflineAppSnapshot)
    fun clearSnapshot(memberId: String? = null)
    fun loadPendingActions(memberId: String? = null): List<PendingSyncAction>
    fun savePendingActions(memberId: String, actions: List<PendingSyncAction>)
    fun clearPendingActions(memberId: String? = null)
}

class SharedPreferencesOfflineStateStore(context: Context) : OfflineStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadSnapshot(memberId: String?): OfflineAppSnapshot? {
        val resolvedMemberId = resolveMemberId(memberId)
        if (resolvedMemberId != null) {
            val scopedRaw = preferences.getString(snapshotKey(resolvedMemberId), null)
            val scopedSnapshot = scopedRaw?.let { raw ->
                runCatching { json.decodeFromString<OfflineAppSnapshot>(raw) }.getOrNull()
            }
            if (scopedSnapshot != null) {
                rememberActiveMember(resolvedMemberId)
                return scopedSnapshot
            }
        }

        val legacyRaw = preferences.getString(KEY_SNAPSHOT_LEGACY, null) ?: return null
        val legacySnapshot = runCatching {
            json.decodeFromString<OfflineAppSnapshot>(legacyRaw)
        }.getOrNull() ?: return null
        val legacyMemberId = legacySnapshot.memberContext.memberId
        preferences.edit()
            .putString(snapshotKey(legacyMemberId), json.encodeToString(legacySnapshot))
            .putString(KEY_ACTIVE_MEMBER_ID, legacyMemberId)
            .remove(KEY_SNAPSHOT_LEGACY)
            .apply()
        return if (resolvedMemberId == null || resolvedMemberId == legacyMemberId) {
            legacySnapshot
        } else {
            null
        }
    }

    override fun saveSnapshot(snapshot: OfflineAppSnapshot) {
        val memberId = snapshot.memberContext.memberId
        preferences.edit()
            .putString(snapshotKey(memberId), json.encodeToString(snapshot))
            .putString(KEY_ACTIVE_MEMBER_ID, memberId)
            .remove(KEY_SNAPSHOT_LEGACY)
            .apply()
    }

    override fun clearSnapshot(memberId: String?) {
        val resolvedMemberId = resolveMemberId(memberId)
        preferences.edit().apply {
            if (resolvedMemberId != null) {
                remove(snapshotKey(resolvedMemberId))
                if (preferences.getString(KEY_ACTIVE_MEMBER_ID, null) == resolvedMemberId) {
                    remove(KEY_ACTIVE_MEMBER_ID)
                }
            } else {
                remove(KEY_SNAPSHOT_LEGACY)
                remove(KEY_ACTIVE_MEMBER_ID)
            }
        }.apply()
    }

    override fun loadPendingActions(memberId: String?): List<PendingSyncAction> {
        val resolvedMemberId = resolveMemberId(memberId)
        if (resolvedMemberId != null) {
            val scopedRaw = preferences.getString(pendingActionsKey(resolvedMemberId), null)
            if (scopedRaw != null) {
                return runCatching {
                    json.decodeFromString<List<PendingSyncAction>>(scopedRaw)
                }.getOrDefault(emptyList())
            }
        }

        // Legacy queues were global and could leak actions across accounts. Drop them rather than replaying
        // unknown actions into the wrong child or parent session.
        if (preferences.contains(KEY_PENDING_ACTIONS_LEGACY)) {
            preferences.edit().remove(KEY_PENDING_ACTIONS_LEGACY).apply()
        }
        return emptyList()
    }

    override fun savePendingActions(memberId: String, actions: List<PendingSyncAction>) {
        preferences.edit()
            .putString(pendingActionsKey(memberId), json.encodeToString(actions))
            .putString(KEY_ACTIVE_MEMBER_ID, memberId)
            .remove(KEY_PENDING_ACTIONS_LEGACY)
            .apply()
    }

    override fun clearPendingActions(memberId: String?) {
        val resolvedMemberId = resolveMemberId(memberId)
        preferences.edit().apply {
            if (resolvedMemberId != null) {
                remove(pendingActionsKey(resolvedMemberId))
            } else {
                remove(KEY_PENDING_ACTIONS_LEGACY)
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "family_checkin_offline_state"
        const val KEY_ACTIVE_MEMBER_ID = "active_member_id"
        const val KEY_SNAPSHOT_LEGACY = "snapshot"
        const val KEY_PENDING_ACTIONS_LEGACY = "pending_actions"

        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
    }

    private fun resolveMemberId(memberId: String?): String? {
        return memberId?.takeIf { it.isNotBlank() }
            ?: preferences.getString(KEY_ACTIVE_MEMBER_ID, null)?.takeIf { it.isNotBlank() }
    }

    private fun rememberActiveMember(memberId: String) {
        preferences.edit().putString(KEY_ACTIVE_MEMBER_ID, memberId).apply()
    }

    private fun snapshotKey(memberId: String): String = "snapshot:$memberId"

    private fun pendingActionsKey(memberId: String): String = "pending_actions:$memberId"
}
