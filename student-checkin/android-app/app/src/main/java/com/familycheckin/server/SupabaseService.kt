package com.familycheckin.server

import com.familycheckin.auth.AuthSessionSnapshot
import com.familycheckin.auth.AuthSessionStore
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface ServerGateway {
    suspend fun signIn(email: String, password: String): ServerMemberContext
    suspend fun signUpParent(request: ParentSignUpRequest): ServerSignUpResult
    suspend fun requestPasswordReset(email: String)
    suspend fun restoreSession(): ServerMemberContext?
    fun clearSession()
    suspend fun childTodaySnapshot(): List<ServerTaskOccurrence>
    suspend fun parentTodaySnapshot(): List<ServerTaskOccurrence>
    suspend fun pointLedger(): List<ServerLedgerEntry>
    suspend fun parentRedemptionList(): List<ServerRedemptionRequest>
    suspend fun parentRedemptionStats(): ServerRedemptionStats
    suspend fun familyChildren(): List<ServerFamilyChild>
    suspend fun childAccounts(): List<ServerChildAccount>
    suspend fun notificationChannels(): List<ServerNotificationChannel>
    suspend fun dailyChildReports(limitDays: Int = 30): List<ServerDailyChildReport>
    suspend fun childTaskHistory(occurrenceId: String, lookbackDays: Int = 180): ServerTaskHistoryDetail
    suspend fun taskSubmissions(): List<ServerTaskSubmission>
    suspend fun createTaskSubmission(
        occurrenceId: String,
        submissionType: String,
        storagePath: String
    )

    suspend fun uploadTaskFile(
        storagePath: String,
        bytes: ByteArray,
        contentType: String
    )

    suspend fun startTask(occurrenceId: String)
    suspend fun completeTask(occurrenceId: String)
    suspend fun finishTask(occurrenceId: String, actualDurationSeconds: Int)
    suspend fun submitRedemption(pointsRequested: Int)
    suspend fun reviewRedemption(requestId: String, approve: Boolean)
    suspend fun sendNotification(familyId: String, messageText: String)
    suspend fun createChildAccount(request: CreateChildAccountRequest): ServerChildAccount
    suspend fun resetChildPassword(memberId: String, newPassword: String)
    suspend fun deleteChildAccount(memberId: String)
    suspend fun saveNotificationChannel(
        channelId: String?,
        channelType: String,
        webhookUrl: String,
        isEnabled: Boolean
    )
    suspend fun updateFamilySettings(
        familyName: String,
        cashCnyPer10Points: Double,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    )
    suspend fun updateChildProfile(memberId: String, childName: String)

    suspend fun createQuickTask(
        childMemberId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    )

    suspend fun updateTaskTemplate(
        taskTemplateId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    )


    suspend fun deleteTaskTemplate(taskTemplateId: String)
    suspend fun deleteTaskOccurrence(occurrenceId: String)

    suspend fun resetTodayOccurrences()
}

class SupabaseService(
    private val baseUrl: String,
    private val apiKey: String,
    private val sessionStore: AuthSessionStore? = null,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ServerGateway {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override fun clearSession() {
        accessToken = null
        refreshToken = null
        sessionStore?.clear()
    }

    override suspend fun signIn(email: String, password: String): ServerMemberContext {
        val response = postJson<AuthSessionResponse>(
            path = "/auth/v1/token?grant_type=password",
            body = buildJsonObject {
                put("email", email)
                put("password", password)
            },
            useSessionToken = false
        )
        applySession(response)
        return currentMemberContext()
    }

    override suspend fun signUpParent(request: ParentSignUpRequest): ServerSignUpResult {
        val signUpResponse = postJson<AuthSessionResponse>(
            path = "/auth/v1/signup",
            body = buildJsonObject {
                put("email", request.parentEmail)
                put("password", request.parentPassword)
            },
            useSessionToken = false
        )

        applySession(signUpResponse)
        if (accessToken.isNullOrBlank()) {
            signIn(request.parentEmail, request.parentPassword)
        }

        rpcUnit(
            "complete_parent_onboarding",
            buildJsonObject {
                put("p_family_name", request.familyName)
                put("p_parent_display_name", request.parentName)
                put("p_cash_cny_per_10_points", request.cashCnyPer10Points)
                put("p_min_redeem_points", request.minRedeemPoints)
                put("p_media_retention_days", request.mediaRetentionDays)
                put("p_timezone", request.timezone)
            }
        )

        val context = currentMemberContext()
        val createdChild = createChildAccount(
            CreateChildAccountRequest(
                childName = request.firstChildName,
                childEmail = request.firstChildEmail,
                childPassword = request.firstChildPassword
            )
        )
        return ServerSignUpResult(memberContext = context, createdChild = createdChild)
    }

    override suspend fun requestPasswordReset(email: String) {
        postUnit(
            path = "/auth/v1/recover",
            body = buildJsonObject { put("email", email) },
            useSessionToken = false
        )
    }

    override suspend fun restoreSession(): ServerMemberContext? {
        val snapshot = sessionStore?.load() ?: return null
        accessToken = snapshot.accessToken
        refreshToken = snapshot.refreshToken
        return try {
            currentMemberContext()
        } catch (firstError: Exception) {
            try {
                val currentRefreshToken = refreshToken ?: snapshot.refreshToken ?: throw firstError
                refreshSession(currentRefreshToken)
                currentMemberContext()
            } catch (refreshError: Exception) {
                if (firstError is IOException || refreshError is IOException) {
                    throw if (refreshError is IOException) refreshError else firstError
                }
                clearSession()
                null
            }
        }
    }

    override suspend fun childTodaySnapshot(): List<ServerTaskOccurrence> {
        return rpcList("child_today_snapshot")
    }

    override suspend fun parentTodaySnapshot(): List<ServerTaskOccurrence> {
        return rpcList("parent_today_snapshot")
    }

    override suspend fun pointLedger(): List<ServerLedgerEntry> {
        return getList(
            path = "/rest/v1/point_ledger?select=id,created_at,change_type,points_delta,balance_after,note&order=created_at.desc"
        )
    }

    override suspend fun parentRedemptionList(): List<ServerRedemptionRequest> {
        return rpcList("parent_redemption_list")
    }

    override suspend fun parentRedemptionStats(): ServerRedemptionStats {
        return rpcList<ServerRedemptionStats>("parent_redemption_stats").firstOrNull()
            ?: ServerRedemptionStats(0, 0.0, 0, 0.0)
    }

    override suspend fun familyChildren(): List<ServerFamilyChild> {
        return getList(
            path = "/rest/v1/family_members?select=id,child_display_name,role&role=eq.child&is_active=is.true&order=child_display_name.asc"
        )
    }

    override suspend fun childAccounts(): List<ServerChildAccount> {
        return rpcList("parent_child_accounts")
    }

    override suspend fun notificationChannels(): List<ServerNotificationChannel> {
        return getList(
            path = "/rest/v1/notification_channels?select=id,channel_type,config_json,is_enabled&order=created_at.asc"
        )
    }

    override suspend fun dailyChildReports(limitDays: Int): List<ServerDailyChildReport> {
        return getList(
            path = "/rest/v1/daily_child_reports?select=child_member_id,local_date,task_total_count,task_completed_count,total_duration_seconds,points_awarded,points_redeemed,redemption_cash_cny&order=local_date.desc&limit=$limitDays"
        )
    }

    override suspend fun childTaskHistory(occurrenceId: String, lookbackDays: Int): ServerTaskHistoryDetail {
        val seed = getList<ServerTaskHistorySeed>(
            path = "/rest/v1/task_occurrences?select=task_template_id,task_name_snapshot&id=eq.$occurrenceId&limit=1"
        ).firstOrNull() ?: error("未找到任务历史信息")
        val fromDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays((lookbackDays - 1).toLong())
        val history = getList<ServerTaskHistoryOccurrence>(
            path = "/rest/v1/task_occurrences?select=local_date,status,completed_at&task_template_id=eq.${seed.taskTemplateId}&local_date=gte.$fromDate&order=local_date.desc"
        )
        return ServerTaskHistoryDetail(
            taskTitle = seed.taskNameSnapshot,
            occurrences = history
        )
    }

    override suspend fun taskSubmissions(): List<ServerTaskSubmission> {
        return getList(
            path = "/rest/v1/task_submissions?select=id,occurrence_id,submission_type,storage_path,uploaded_at&order=uploaded_at.desc"
        )
    }

    override suspend fun createTaskSubmission(
        occurrenceId: String,
        submissionType: String,
        storagePath: String
    ) {
        postUnit(
            path = "/rest/v1/task_submissions",
            body = buildJsonObject {
                put("occurrence_id", occurrenceId)
                put("submission_type", submissionType)
                put("storage_path", storagePath)
            }
        )
    }

    override suspend fun uploadTaskFile(
        storagePath: String,
        bytes: ByteArray,
        contentType: String
    ) {
        val token = accessToken ?: apiKey
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/storage/v1/object/task-deliveries/$storagePath")
            .headers(
                mapOf(
                    "apikey" to apiKey,
                    "Authorization" to "Bearer $token",
                    "x-upsert" to "true",
                    "Content-Type" to contentType
                ).toHeaders()
            )
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        executeText(request)
    }

    override suspend fun startTask(occurrenceId: String) {
        rpcUnit(
            "child_start_task",
            buildJsonObject { put("p_occurrence_id", occurrenceId) }
        )
    }

    override suspend fun completeTask(occurrenceId: String) {
        rpcUnit(
            "child_complete_task",
            buildJsonObject { put("p_occurrence_id", occurrenceId) }
        )
    }

    override suspend fun finishTask(occurrenceId: String, actualDurationSeconds: Int) {
        rpcUnit(
            "child_finish_task",
            buildJsonObject {
                put("p_occurrence_id", occurrenceId)
                put("p_actual_duration_seconds", actualDurationSeconds)
            }
        )
    }

    override suspend fun submitRedemption(pointsRequested: Int) {
        rpcUnit(
            "child_submit_redemption",
            buildJsonObject { put("p_points_requested", pointsRequested) }
        )
    }

    override suspend fun reviewRedemption(requestId: String, approve: Boolean) {
        rpcUnit(
            "parent_review_redemption",
            buildJsonObject {
                put("p_request_id", requestId)
                put("p_approve", approve)
            }
        )
    }

    override suspend fun sendNotification(familyId: String, messageText: String) {
        postUnit(
            path = "/functions/v1/send-notifications",
            body = buildJsonObject {
                put("family_id", familyId)
                put("message_text", messageText)
            }
        )
    }

    override suspend fun createChildAccount(request: CreateChildAccountRequest): ServerChildAccount {
        return postFunctionJson(
            functionName = "create-child-account",
            body = buildJsonObject {
                put("child_name", request.childName)
                put("child_email", request.childEmail)
                put("child_password", request.childPassword)
            }
        )
    }

    override suspend fun resetChildPassword(memberId: String, newPassword: String) {
        postUnit(
            path = "/functions/v1/manage-child-account",
            body = buildJsonObject {
                put("action", "reset_password")
                put("member_id", memberId)
                put("new_password", newPassword)
            }
        )
    }

    override suspend fun deleteChildAccount(memberId: String) {
        postUnit(
            path = "/functions/v1/manage-child-account",
            body = buildJsonObject {
                put("action", "delete_child")
                put("member_id", memberId)
            }
        )
    }

    override suspend fun saveNotificationChannel(
        channelId: String?,
        channelType: String,
        webhookUrl: String,
        isEnabled: Boolean
    ) {
        val body = buildJsonObject {
            channelId?.takeIf { it.isNotBlank() }?.let { put("id", it) }
            put("channel_type", channelType)
            put("config_json", buildJsonObject { put("webhook_url", webhookUrl) })
            put("is_enabled", isEnabled)
        }
        if (channelId.isNullOrBlank()) {
            postUnit(path = "/rest/v1/notification_channels", body = body)
        } else {
            val request = requestBuilder("/rest/v1/notification_channels?id=eq.$channelId")
                .method("PATCH", body.toRequestBody(json))
                .build()
            executeText(request)
        }
    }

    override suspend fun updateFamilySettings(
        familyName: String,
        cashCnyPer10Points: Double,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) {
        rpcUnit(
            "parent_update_family_settings",
            buildJsonObject {
                put("p_family_name", familyName.trim())
                put("p_cash_cny_per_10_points", cashCnyPer10Points)
                put("p_min_redeem_points", minRedeemPoints)
                put("p_media_retention_days", mediaRetentionDays)
            }
        )
    }

    override suspend fun updateChildProfile(memberId: String, childName: String) {
        postUnit(
            path = "/functions/v1/manage-child-account",
            body = buildJsonObject {
                put("action", "rename_child")
                put("member_id", memberId)
                put("child_name", childName.trim())
            }
        )
    }

    override suspend fun createQuickTask(
        childMemberId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    ) {
        rpcUnit(
            "parent_create_quick_task",
            buildJsonObject {
                put("p_child_member_id", childMemberId)
                put("p_name", name)
                put("p_mode", mode)
                put("p_delivery_requirement", deliveryRequirement)
                put("p_points", points)
                targetMinutes?.let { put("p_target_minutes", it) }
                scheduledTime?.let { put("p_scheduled_time", it) }
            }
        )
    }

    override suspend fun updateTaskTemplate(
        taskTemplateId: String,
        name: String,
        mode: String,
        deliveryRequirement: String,
        points: Int,
        targetMinutes: Int?,
        scheduledTime: String?
    ) {
        rpcUnit(
            "parent_update_task_template",
            buildJsonObject {
                put("p_task_template_id", taskTemplateId)
                put("p_name", name)
                put("p_mode", mode)
                put("p_delivery_requirement", deliveryRequirement)
                put("p_points", points)
                targetMinutes?.let { put("p_target_minutes", it) }
                scheduledTime?.let { put("p_scheduled_time", it) }
            }
        )
    }


    override suspend fun deleteTaskTemplate(taskTemplateId: String) {
        rpcUnit(
            "parent_delete_task_template",
            buildJsonObject { put("p_task_template_id", taskTemplateId) }
        )
    }

    override suspend fun deleteTaskOccurrence(occurrenceId: String) {
        rpcUnit(
            "parent_delete_task_occurrence",
            buildJsonObject { put("p_occurrence_id", occurrenceId) }
        )
    }

    override suspend fun resetTodayOccurrences() {
        rpcUnit("parent_reset_today_occurrences")
    }

    private suspend fun refreshSession(refreshTokenValue: String) {
        val response = postJson<AuthSessionResponse>(
            path = "/auth/v1/token?grant_type=refresh_token",
            body = buildJsonObject {
                put("refresh_token", refreshTokenValue)
            },
            useSessionToken = false
        )
        applySession(response, fallbackRefreshToken = refreshTokenValue)
    }

    private fun applySession(
        response: AuthSessionResponse,
        fallbackRefreshToken: String? = null
    ) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken ?: fallbackRefreshToken
        val currentAccessToken = accessToken
        if (!currentAccessToken.isNullOrBlank()) {
            sessionStore?.save(
                AuthSessionSnapshot(
                    accessToken = currentAccessToken,
                    refreshToken = refreshToken,
                    expiresAtEpochSeconds = response.expiresAt
                )
            )
        }
    }

    private suspend fun currentMemberContext(): ServerMemberContext {
        val context = rpcList<ServerMemberContext>("current_member_context").firstOrNull()
            ?: error("未获取到账户身份信息")
        val familyContext = rpcList<ServerMemberFamilyContext>("member_family_context").firstOrNull()
        return context.copy(
            familyName = familyContext?.familyName,
            timezone = familyContext?.timezone,
            mediaRetentionDays = familyContext?.mediaRetentionDays ?: context.mediaRetentionDays
        )
    }

    private suspend inline fun <reified T> rpcList(
        functionName: String,
        body: JsonObject = buildJsonObject {}
    ): List<T> {
        return postJson(
            path = "/rest/v1/rpc/$functionName",
            body = body
        )
    }

    private suspend inline fun <reified T> getList(path: String): List<T> {
        val request = requestBuilder(path).get().build()
        return executeJson(request)
    }

    private suspend fun rpcUnit(
        functionName: String,
        body: JsonObject = buildJsonObject {}
    ) {
        postUnit(
            path = "/rest/v1/rpc/$functionName",
            body = body
        )
    }

    private suspend inline fun <reified T> postFunctionJson(
        functionName: String,
        body: JsonObject
    ): T {
        val request = requestBuilder("/functions/v1/$functionName")
            .post(body.toRequestBody(json))
            .build()
        return executeJson(request)
    }

    private suspend inline fun <reified T> postJson(
        path: String,
        body: JsonObject,
        useSessionToken: Boolean = true
    ): T {
        val request = requestBuilder(path, useSessionToken)
            .post(body.toRequestBody(json))
            .build()
        return executeJson(request)
    }

    private suspend fun postUnit(
        path: String,
        body: JsonObject = buildJsonObject {},
        useSessionToken: Boolean = true
    ) {
        val request = requestBuilder(path, useSessionToken)
            .post(body.toRequestBody(json))
            .build()
        executeText(request)
    }

    private fun requestBuilder(path: String, useSessionToken: Boolean = true): Request.Builder {
        val token = if (useSessionToken) {
            accessToken ?: apiKey
        } else {
            apiKey
        }

        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
    }

    private suspend inline fun <reified T> executeJson(request: Request): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(extractErrorMessage(bodyText, response.code))
            }
            json.decodeFromString<T>(bodyText)
        }
    }

    private suspend fun executeText(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(extractErrorMessage(bodyText, response.code))
            }
            bodyText
        }
    }

    private fun JsonObject.toRequestBody(json: Json) =
        json.encodeToString(this).toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun extractErrorMessage(bodyText: String, code: Int): String {
        if (bodyText.isBlank()) {
            return "服务请求失败($code)"
        }
        return bodyText.replace('\n', ' ').take(240)
    }
}

@Serializable
private data class AuthSessionResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_at")
    val expiresAt: Long? = null
)
