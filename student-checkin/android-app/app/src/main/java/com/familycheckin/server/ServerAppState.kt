package com.familycheckin.server

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.familycheckin.child.ChildProgressSummaryUi
import com.familycheckin.child.ChildTaskInsightUi
import com.familycheckin.child.ChildTaskWindowStatUi
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import com.familycheckin.parent.ParentChildSummaryUi
import com.familycheckin.parent.ParentDashboardFocus
import com.familycheckin.parent.ParentOverviewUi
import com.familycheckin.parent.ParentTaskFilter
import com.familycheckin.parent.ParentTaskRowUi
import com.familycheckin.parent.RedemptionRequestUi
import com.familycheckin.parent.RedemptionStatsUi
import com.familycheckin.points.LedgerEntryUi
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val beijingZoneId: ZoneId = ZoneId.of("Asia/Shanghai")
private val beijingDate: LocalDate
    get() = LocalDate.now(beijingZoneId)

private val beijingDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class ServerAppState(
    private val gateway: ServerGateway,
    private val offlineStore: OfflineStateStore? = null,
    private val pendingMediaStore: PendingMediaStore? = null
) {
    private companion object {
        const val TAG = "ServerAppState"
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingSyncInFlight = AtomicBoolean(false)

    private var parentSourceTasks: List<ServerTaskOccurrence> = emptyList()
    private var childSourceTasks: List<ServerTaskOccurrence> = emptyList()
    private var ledgerSourceEntries: List<ServerLedgerEntry> = emptyList()
    private var redemptionSourceEntries: List<ServerRedemptionRequest> = emptyList()
    private var familyChildrenSource: List<ServerFamilyChild> = emptyList()
    private var childAccountsSource: List<ServerChildAccount> = emptyList()
    private var notificationChannelsSource: List<ServerNotificationChannel> = emptyList()
    private var dailyReportSource: List<ServerDailyChildReport> = emptyList()
    private var submissionSource: List<ServerTaskSubmission> = emptyList()
    private var memberContextSnapshot: ServerMemberContext? = null
    private var currentFamilyId: String? = null
    private val pendingActions = mutableListOf<PendingSyncAction>()

    var currentRole by mutableStateOf<ServerRole?>(null)
        private set
    var childName by mutableStateOf("")
        private set
    var familyName by mutableStateOf("")
        private set
    var parentEmail by mutableStateOf("")
        private set
    var flashMessage by mutableStateOf("欢迎使用学生任务打卡")
        private set
    var cashPerTenPoints by mutableStateOf(1)
        private set
    var minRedeemPoints by mutableStateOf(10)
        private set
    var mediaRetentionDays by mutableStateOf(30)
        private set
    var timezone by mutableStateOf("Asia/Shanghai")
        private set
    var isBusy by mutableStateOf(false)
        private set

    val tasks = mutableStateListOf<DemoTask>()
    val ledger = mutableStateListOf<LedgerEntryUi>()
    val redemptionRequests = mutableStateListOf<RedemptionRequestUi>()
    val parentChildren = mutableStateListOf<ParentChildSummaryUi>()
    val parentTaskRows = mutableStateListOf<ParentTaskRowUi>()
    val taskSubmissions = mutableStateListOf<ServerTaskSubmission>()
    val childAccounts = mutableStateListOf<ServerChildAccount>()

    var notificationChannelId by mutableStateOf<String?>(null)
        private set
    var notificationChannelType by mutableStateOf("dingtalk")
        private set
    var notificationWebhookUrl by mutableStateOf("")
        private set
    var notificationEnabled by mutableStateOf(false)
        private set

    var childSummary by mutableStateOf(emptyChildSummary())
        private set
    var selectedChildTaskId by mutableStateOf<String?>(null)
        private set
    var selectedChildTaskInsight by mutableStateOf<ChildTaskInsightUi?>(null)
        private set
    var isChildTaskInsightLoading by mutableStateOf(false)
        private set

    var parentOverview by mutableStateOf(emptyParentOverview())
        private set
    var parentStats by mutableStateOf(RedemptionStatsUi(0, 0.0, 0, 0.0))
        private set
    var selectedChildId by mutableStateOf<String?>(null)
        private set
    var parentFocus by mutableStateOf(ParentDashboardFocus.PENDING)
        private set
    var parentFilter by mutableStateOf(ParentTaskFilter.ALL)
        private set

    val currentPoints: Int
        get() = ledger.firstOrNull()?.balanceAfter ?: 0

    fun initializeFromCache(): Boolean {
        val snapshot = offlineStore?.loadSnapshot() ?: return false
        pendingActions.clear()
        pendingActions += offlineStore.loadPendingActions(snapshot.memberContext.memberId)
        applySnapshot(snapshot)
        flashMessage = if (pendingActions.isEmpty()) {
            "已加载本地缓存"
        } else {
            "当前使用离线缓存，待同步 ${pendingActions.size} 项"
        }
        return true
    }

    fun switchAccount() {
        gateway.clearSession()
        val currentMemberId = memberContextSnapshot?.memberId
        offlineStore?.clearSnapshot(currentMemberId)
        offlineStore?.clearPendingActions(currentMemberId)
        pendingMediaStore?.clear()

        parentSourceTasks = emptyList()
        childSourceTasks = emptyList()
        ledgerSourceEntries = emptyList()
        redemptionSourceEntries = emptyList()
        familyChildrenSource = emptyList()
        childAccountsSource = emptyList()
        notificationChannelsSource = emptyList()
        dailyReportSource = emptyList()
        submissionSource = emptyList()
        memberContextSnapshot = null
        currentFamilyId = null
        pendingActions.clear()

        currentRole = null
        childName = ""
        familyName = ""
        parentEmail = ""
        flashMessage = "已退出当前账号，请重新登录"
        cashPerTenPoints = 1
        minRedeemPoints = 10
        mediaRetentionDays = 30
        timezone = "Asia/Shanghai"
        isBusy = false

        tasks.clear()
        ledger.clear()
        redemptionRequests.clear()
        parentChildren.clear()
        parentTaskRows.clear()
        taskSubmissions.clear()
        childAccounts.clear()
        notificationChannelId = null
        notificationChannelType = "dingtalk"
        notificationWebhookUrl = ""
        notificationEnabled = false
        childSummary = emptyChildSummary()
        parentOverview = emptyParentOverview()
        parentStats = RedemptionStatsUi(0, 0.0, 0, 0.0)
        selectedChildId = null
        parentFocus = ParentDashboardFocus.PENDING
        parentFilter = ParentTaskFilter.ALL
        clearSelectedTaskInsight()
    }

    suspend fun login(email: String, password: String): ServerRole? {
        return runBusy("正在登录...") {
            val context = gateway.signIn(email, password)
            applyMemberContext(context)
            pendingActions.clear()
            pendingActions += offlineStore?.loadPendingActions(context.memberId).orEmpty()
            syncPendingActions()
            when (currentRole) {
                ServerRole.CHILD -> {
                    refreshChildData()
                    flashMessage = "欢迎回来，$childName"
                }

                ServerRole.PARENT -> {
                    refreshParentData()
                    flashMessage = "家长端数据已同步"
                }

                null -> Unit
            }
            currentRole
        }
    }

    suspend fun restoreSession(): ServerRole? {
        return runBusy {
            try {
                val context = gateway.restoreSession() ?: return@runBusy currentRole
                applyMemberContext(context)
                pendingActions.clear()
                pendingActions += offlineStore?.loadPendingActions(context.memberId).orEmpty()
                syncPendingActions()
                when (currentRole) {
                    ServerRole.CHILD -> refreshChildData()
                    ServerRole.PARENT -> refreshParentData()
                    null -> Unit
                }
                currentRole
            } catch (error: IOException) {
                if (currentRole != null) {
                    flashMessage = if (pendingActions.isEmpty()) {
                        "网络未连接，正在使用本地缓存"
                    } else {
                        "网络未连接，待同步 ${pendingActions.size} 项"
                    }
                    currentRole
                } else {
                    throw error
                }
            }
        }
    }

    suspend fun signUpParent(
        familyName: String,
        parentName: String,
        parentEmail: String,
        parentPassword: String,
        firstChildName: String,
        firstChildEmail: String,
        firstChildPassword: String
    ): ServerRole? {
        return runBusy("正在创建家庭账号...") {
            val result = gateway.signUpParent(
                ParentSignUpRequest(
                    familyName = familyName,
                    parentName = parentName,
                    parentEmail = parentEmail,
                    parentPassword = parentPassword,
                    firstChildName = firstChildName,
                    firstChildEmail = firstChildEmail,
                    firstChildPassword = firstChildPassword,
                    cashCnyPer10Points = cashPerTenPoints.toDouble(),
                    minRedeemPoints = minRedeemPoints,
                    mediaRetentionDays = mediaRetentionDays,
                    timezone = timezone
                )
            )
            applyMemberContext(result.memberContext)
            refreshParentData()
            flashMessage = buildString {
                append("家庭创建成功")
                result.createdChild?.let {
                    append("，已创建孩子账号：${it.childName}（${it.email}）")
                }
            }
            currentRole
        }
    }

    suspend fun requestPasswordReset(email: String) {
        runBusy("正在提交密码重置请求...") {
            gateway.requestPasswordReset(email)
            flashMessage = "密码重置请求已提交"
        }
    }

    suspend fun refreshChildData() {
        runBusy {
            childSourceTasks = gateway.childTodaySnapshot()
            ledgerSourceEntries = gateway.pointLedger()
            dailyReportSource = gateway.dailyChildReports()
            submissionSource = gateway.taskSubmissions()
            applyChildState()
            persistSnapshot()
        }
    }

    suspend fun refreshParentData() {
        runBusy {
            loadParentData()
        }
    }

    suspend fun refreshParentDataSilently() {
        try {
            loadParentData()
        } catch (error: IOException) {
            errorLog("refreshParentDataSilently failed", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorLog("refreshParentDataSilently failed", error)
        }
    }

    private suspend fun loadParentData() {
        parentSourceTasks = gateway.parentTodaySnapshot()
        dailyReportSource = gateway.dailyChildReports()
        redemptionSourceEntries = gateway.parentRedemptionList()
        submissionSource = gateway.taskSubmissions()
        familyChildrenSource = gateway.familyChildren()
        childAccountsSource = gateway.childAccounts()
        notificationChannelsSource = gateway.notificationChannels()
        parentStats = gateway.parentRedemptionStats().toRedemptionStatsUi()
        applyParentState()
        persistSnapshot()
    }

    suspend fun createChildAccount(
        childName: String,
        childEmail: String,
        childPassword: String
    ) {
        runBusy("正在创建孩子账号...") {
            val created = gateway.createChildAccount(
                CreateChildAccountRequest(
                    childName = childName,
                    childEmail = childEmail,
                    childPassword = childPassword
                )
            )
            refreshParentData()
            flashMessage = "已创建孩子账号：${created.childName}（${created.email}）"
        }
    }

    suspend fun resetChildPassword(memberId: String, newPassword: String) {
        runBusy("正在重置孩子密码...") {
            gateway.resetChildPassword(memberId, newPassword)
            flashMessage = "孩子账号密码已重置"
        }
    }

    suspend fun deleteChildAccount(memberId: String) {
        runBusy("正在删除孩子账号...") {
            gateway.deleteChildAccount(memberId)
            refreshParentData()
            flashMessage = "孩子账号已删除"
        }
    }

    suspend fun saveNotificationSettings(
        channelType: String,
        webhookUrl: String,
        enabled: Boolean
    ) {
        runBusy("正在保存通知配置...") {
            try {
                gateway.saveNotificationChannel(
                    channelId = notificationChannelId,
                    channelType = channelType,
                    webhookUrl = webhookUrl,
                    isEnabled = enabled
                )
                refreshParentData()
                flashMessage = "通知设置已保存"
            } catch (error: IOException) {
                applyOptimisticNotificationSettings(channelType, webhookUrl, enabled)
                enqueuePendingAction(
                    PendingSyncAction.SaveNotificationSettings(
                        channelId = notificationChannelId,
                        channelType = channelType,
                        webhookUrl = webhookUrl,
                        isEnabled = enabled
                    )
                )
                flashMessage = "网络未连接，通知设置已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun updateFamilySettings(
        familyName: String,
        cashCnyPer10Points: Int,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) {
        runBusy("正在保存家庭设置...") {
            try {
                gateway.updateFamilySettings(
                    familyName = familyName,
                    cashCnyPer10Points = cashCnyPer10Points.toDouble(),
                    minRedeemPoints = minRedeemPoints,
                    mediaRetentionDays = mediaRetentionDays
                )
                applyOptimisticFamilyProfile(familyName, cashCnyPer10Points, minRedeemPoints, mediaRetentionDays)
                persistSnapshot()
                flashMessage = "家庭设置已保存"
            } catch (error: IOException) {
                applyOptimisticFamilyProfile(familyName, cashCnyPer10Points, minRedeemPoints, mediaRetentionDays)
                enqueuePendingAction(
                    PendingSyncAction.UpdateFamilySettings(
                        familyName = familyName,
                        cashCnyPer10Points = cashCnyPer10Points,
                        minRedeemPoints = minRedeemPoints,
                        mediaRetentionDays = mediaRetentionDays
                    )
                )
                flashMessage = "网络未连接，家庭设置已暂存，恢复网络后会自动同步"
            }
        }
    }

    fun selectChild(memberId: String) {
        selectedChildId = memberId
        rebuildParentTaskRows(parentSourceTasks)
    }

    suspend fun saveFamilyProfile(
        familyName: String,
        cashCnyPer10Points: Int,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) {
        runBusy("正在保存家庭设置...") {
            try {
                gateway.updateFamilySettings(
                    familyName = familyName,
                    cashCnyPer10Points = cashCnyPer10Points.toDouble(),
                    minRedeemPoints = minRedeemPoints,
                    mediaRetentionDays = mediaRetentionDays
                )
                applyOptimisticFamilyProfile(
                    familyName = familyName,
                    cashCnyPer10Points = cashCnyPer10Points,
                    minRedeemPoints = minRedeemPoints,
                    mediaRetentionDays = mediaRetentionDays
                )
                persistSnapshot()
                flashMessage = "家庭设置已保存"
            } catch (error: IOException) {
                applyOptimisticFamilyProfile(
                    familyName = familyName,
                    cashCnyPer10Points = cashCnyPer10Points,
                    minRedeemPoints = minRedeemPoints,
                    mediaRetentionDays = mediaRetentionDays
                )
                enqueuePendingAction(
                    PendingSyncAction.UpdateFamilySettings(
                        familyName = familyName,
                        cashCnyPer10Points = cashCnyPer10Points,
                        minRedeemPoints = minRedeemPoints,
                        mediaRetentionDays = mediaRetentionDays
                    )
                )
                flashMessage = "网络未连接，家庭设置已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun updateChildProfile(memberId: String, childName: String) {
        runBusy("正在保存孩子信息...") {
            try {
                gateway.updateChildProfile(memberId, childName)
                applyOptimisticChildProfile(memberId, childName)
                persistSnapshot()
                flashMessage = "孩子信息已更新"
            } catch (error: IOException) {
                applyOptimisticChildProfile(memberId, childName)
                enqueuePendingAction(
                    PendingSyncAction.UpdateChildProfile(
                        memberId = memberId,
                        childName = childName
                    )
                )
                flashMessage = "网络未连接，孩子信息已暂存，恢复网络后会自动同步"
            }
        }
    }

    fun selectParentFocus(focus: ParentDashboardFocus) {
        parentFocus = focus
        rebuildParentTaskRows(parentSourceTasks)
    }

    fun selectParentFilter(filter: ParentTaskFilter) {
        parentFilter = filter
        rebuildParentTaskRows(parentSourceTasks)
    }

    suspend fun completeTask(taskId: String) {
        runBusy("正在提交打卡...") {
            val task = tasks.firstOrNull { it.id == taskId } ?: error("未找到任务")
            try {
                gateway.completeTask(taskId)
                refreshChildData()
                sendFamilyNotification("${childName} 完成任务 ${task.title}，积分 +${task.points}")
                flashMessage = "任务已完成，积分已到账"
            } catch (error: IOException) {
                applyOptimisticCompleteTask(taskId, task.title, task.points)
                enqueuePendingAction(
                    PendingSyncAction.CompleteTask(
                        occurrenceId = taskId,
                        taskTitle = task.title,
                        points = task.points
                    )
                )
                flashMessage = "网络未连接，任务已离线完成，恢复网络后会自动同步"
            }
        }
    }

    suspend fun startTask(taskId: String) {
        runBusy("正在开始任务...") {
            try {
                gateway.startTask(taskId)
                refreshChildData()
                flashMessage = "任务已开始"
            } catch (error: IOException) {
                applyOptimisticStartTask(taskId)
                enqueuePendingAction(PendingSyncAction.StartTask(occurrenceId = taskId))
                flashMessage = "网络未连接，已离线开始任务，恢复网络后会自动同步"
            }
        }
    }

    suspend fun finishTask(taskId: String) {
        runBusy("正在结束任务...") {
            val task = tasks.firstOrNull { it.id == taskId } ?: error("未找到任务")
            val durationSeconds = currentDurationSeconds(taskId)
            try {
                gateway.finishTask(taskId, durationSeconds)
                refreshChildData()
                val durationText = if (durationSeconds > 0) "，用时 ${durationSeconds / 60} 分钟" else ""
                sendFamilyNotification("${childName} 完成任务 ${task.title}，积分 +${task.points}$durationText")
                flashMessage = "任务已结束"
            } catch (error: IOException) {
                applyOptimisticCompleteTask(taskId, task.title, task.points, durationSeconds)
                enqueuePendingAction(
                    PendingSyncAction.FinishTask(
                        occurrenceId = taskId,
                        actualDurationSeconds = durationSeconds,
                        taskTitle = task.title,
                        points = task.points
                    )
                )
                flashMessage = "网络未连接，任务已离线结束，恢复网络后会自动同步"
            }
        }
    }

    suspend fun completeTaskFast(taskId: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: error("Task not found")
        applyOptimisticCompleteTask(taskId, task.title, task.points)
        enqueuePendingAction(
            PendingSyncAction.CompleteTask(
                occurrenceId = taskId,
                taskTitle = task.title,
                points = task.points
            )
        )
        flashMessage = "任务已完成，正在后台同步"
        syncPendingActionsAsync()
    }

    suspend fun startTaskFast(taskId: String) {
        applyOptimisticStartTask(taskId)
        enqueuePendingAction(PendingSyncAction.StartTask(occurrenceId = taskId))
        flashMessage = "任务已开始，正在后台同步"
        syncPendingActionsAsync()
    }

    suspend fun finishTaskFast(taskId: String) {
        val task = tasks.firstOrNull { it.id == taskId } ?: error("Task not found")
        val durationSeconds = currentDurationSeconds(taskId)
        applyOptimisticCompleteTask(taskId, task.title, task.points, durationSeconds)
        enqueuePendingAction(
            PendingSyncAction.FinishTask(
                occurrenceId = taskId,
                actualDurationSeconds = durationSeconds,
                taskTitle = task.title,
                points = task.points
            )
        )
        flashMessage = "任务已结束，正在后台同步"
        syncPendingActionsAsync()
    }

    suspend fun submitTaskDelivery(
        taskId: String,
        submissionType: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ) {
        runBusy("正在上传交付内容...") {
            val task = tasks.firstOrNull { it.id == taskId } ?: error("未找到任务")
            val completionMode = if (task.status == TaskStatus.RUNNING) {
                DeliveryCompletionMode.FINISH
            } else {
                DeliveryCompletionMode.COMPLETE
            }
            val durationSeconds = if (completionMode == DeliveryCompletionMode.FINISH) {
                currentDurationSeconds(taskId)
            } else {
                0
            }

            try {
                val storagePath = buildStoragePath(taskId, fileName)
                gateway.uploadTaskFile(
                    storagePath = storagePath,
                    bytes = bytes,
                    contentType = contentType
                )
                gateway.createTaskSubmission(
                    occurrenceId = taskId,
                    submissionType = submissionType,
                    storagePath = storagePath
                )
                when (completionMode) {
                    DeliveryCompletionMode.COMPLETE -> gateway.completeTask(taskId)
                    DeliveryCompletionMode.FINISH -> gateway.finishTask(taskId, durationSeconds)
                }
                refreshChildData()
                sendFamilyNotification("${childName} 已提交${submissionType.toSubmissionLabel()}：${task.title}，积分 +${task.points}")
                flashMessage = "交付内容已上传，任务已自动完成"
            } catch (error: IOException) {
                val localMediaKey = pendingMediaStore?.save(fileName, bytes) ?: error("离线媒体存储不可用")
                applyOptimisticDelivery(taskId, submissionType, task.title, task.points, durationSeconds)
                enqueuePendingAction(
                    PendingSyncAction.SubmitTaskDelivery(
                        occurrenceId = taskId,
                        submissionType = submissionType,
                        fileName = fileName,
                        contentType = contentType,
                        localMediaKey = localMediaKey,
                        completionMode = completionMode,
                        actualDurationSeconds = durationSeconds,
                        taskTitle = task.title,
                        points = task.points
                    )
                )
                flashMessage = "网络未连接，交付内容已离线保存，恢复网络后会自动上传"
            }
        }
    }

    suspend fun submitRedemption(points: Int): Boolean {
        return runBusy("正在提交兑换申请...") {
            if (points < minRedeemPoints || points > currentPoints) {
                flashMessage = "兑换积分必须大于等于 $minRedeemPoints，且不能超过当前积分"
                return@runBusy false
            }

            try {
                gateway.submitRedemption(points)
                refreshChildData()
                sendFamilyNotification("${childName} 申请兑换 ${points} 积分，当前积分 ${currentPoints}")
                flashMessage = "兑换申请已提交，等待家长审核"
                true
            } catch (error: IOException) {
                applyOptimisticRedemptionRequest(points)
                enqueuePendingAction(PendingSyncAction.SubmitRedemption(pointsRequested = points))
                flashMessage = "网络未连接，兑换申请已暂存，恢复网络后会自动同步"
                true
            }
        } ?: false
    }

    suspend fun approveRedemption(requestId: String) {
        runBusy("正在通过兑换申请...") {
            val request = redemptionSourceEntries.firstOrNull { it.id == requestId } ?: error("未找到兑换申请")
            try {
                gateway.reviewRedemption(requestId, true)
                refreshParentData()
                sendFamilyNotification("${request.childName} 的兑换申请已通过，兑换 ${request.pointsRequested} 积分")
                flashMessage = "兑换申请已通过"
            } catch (error: IOException) {
                applyOptimisticRedemptionReview(requestId, approve = true)
                enqueuePendingAction(
                    PendingSyncAction.ReviewRedemption(
                        requestId = requestId,
                        approve = true,
                        childName = request.childName,
                        pointsRequested = request.pointsRequested
                    )
                )
                flashMessage = "网络未连接，审核结果已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun rejectRedemption(requestId: String) {
        runBusy("正在驳回兑换申请...") {
            val request = redemptionSourceEntries.firstOrNull { it.id == requestId } ?: error("未找到兑换申请")
            try {
                gateway.reviewRedemption(requestId, false)
                refreshParentData()
                sendFamilyNotification("${request.childName} 的兑换申请未通过，申请积分 ${request.pointsRequested}")
                flashMessage = "兑换申请已驳回"
            } catch (error: IOException) {
                applyOptimisticRedemptionReview(requestId, approve = false)
                enqueuePendingAction(
                    PendingSyncAction.ReviewRedemption(
                        requestId = requestId,
                        approve = false,
                        childName = request.childName,
                        pointsRequested = request.pointsRequested
                    )
                )
                flashMessage = "网络未连接，审核结果已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun addTask(
        childMemberId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        runBusy("正在创建任务...") {
            try {
                gateway.createQuickTask(
                    childMemberId = childMemberId,
                    name = title,
                    mode = mode.toServerValue(),
                    deliveryRequirement = deliveryRequirement.toServerValue(),
                    points = points,
                    targetMinutes = targetMinutes,
                    scheduledTime = scheduledTimeLabel
                )
                refreshParentData()
                flashMessage = "任务已创建：$title"
            } catch (error: IOException) {
                applyOptimisticAddTask(
                    childMemberId = childMemberId,
                    title = title,
                    mode = mode,
                    deliveryRequirement = deliveryRequirement,
                    points = points,
                    targetMinutes = targetMinutes,
                    scheduledTimeLabel = scheduledTimeLabel
                )
                enqueuePendingAction(
                    PendingSyncAction.AddTask(
                        childMemberId = childMemberId,
                        name = title,
                        mode = mode.toServerValue(),
                        deliveryRequirement = deliveryRequirement.toServerValue(),
                        points = points,
                        targetMinutes = targetMinutes,
                        scheduledTime = scheduledTimeLabel
                    )
                )
                flashMessage = "网络未连接，任务已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun updateTask(
        taskTemplateId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        runBusy("正在修改任务...") {
            try {
                gateway.updateTaskTemplate(
                    taskTemplateId = taskTemplateId,
                    name = title,
                    mode = mode.toServerValue(),
                    deliveryRequirement = deliveryRequirement.toServerValue(),
                    points = points,
                    targetMinutes = targetMinutes,
                    scheduledTime = scheduledTimeLabel
                )
                refreshParentData()
                flashMessage = "任务已更新：$title"
            } catch (error: IOException) {
                applyOptimisticUpdateTask(taskTemplateId, title, mode, deliveryRequirement, points, targetMinutes, scheduledTimeLabel)
                enqueuePendingAction(
                    PendingSyncAction.UpdateTask(
                        taskTemplateId = taskTemplateId,
                        name = title,
                        mode = mode.toServerValue(),
                        deliveryRequirement = deliveryRequirement.toServerValue(),
                        points = points,
                        targetMinutes = targetMinutes,
                        scheduledTime = scheduledTimeLabel
                    )
                )
                flashMessage = "网络未连接，任务修改已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun deleteTask(occurrenceId: String) {
        runBusy("正在删除任务...") {
            try {
                gateway.deleteTaskOccurrence(occurrenceId)
                refreshParentData()
                flashMessage = "任务已删除"
            } catch (error: IOException) {
                applyOptimisticDeleteTask(occurrenceId)
                enqueuePendingAction(PendingSyncAction.DeleteTask(occurrenceId = occurrenceId))
                flashMessage = "网络未连接，任务删除已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun resetDay() {
        runBusy("正在重置今天任务...") {
            try {
                gateway.resetTodayOccurrences()
                refreshParentData()
                flashMessage = "今天任务已重置"
            } catch (error: IOException) {
                applyOptimisticResetDay()
                enqueuePendingAction(PendingSyncAction.ResetDay)
                flashMessage = "网络未连接，今日重置已暂存，恢复网络后会自动同步"
            }
        }
    }

    suspend fun toggleChildTaskInsight(taskId: String) {
        if (selectedChildTaskId == taskId && selectedChildTaskInsight != null) {
            clearSelectedTaskInsight()
            return
        }

        try {
            selectedChildTaskId = taskId
            selectedChildTaskInsight = null
            isChildTaskInsightLoading = true
            val history = gateway.childTaskHistory(taskId)
            selectedChildTaskInsight = history.toChildTaskInsightUi(taskId)
        } catch (error: IOException) {
            flashMessage = "当前离线，无法拉取任务历史统计"
            clearSelectedTaskInsight()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorLog("toggleChildTaskInsight failed taskId=$taskId", error)
            flashMessage = error.message ?: "加载任务统计失败"
            clearSelectedTaskInsight()
        } finally {
            isChildTaskInsightLoading = false
        }
    }

    fun submissionsForTask(taskId: String): List<ServerTaskSubmission> {
        return taskSubmissions.filter { it.occurrenceId == taskId }
    }

    private fun applyMemberContext(context: ServerMemberContext) {
        memberContextSnapshot = context
        currentFamilyId = context.familyId
        currentRole = context.resolveServerRole()
        childName = context.displayName
        familyName = context.familyName.orEmpty()
        parentEmail = context.email
        cashPerTenPoints = context.cashCnyPer10Points.roundToInt()
        minRedeemPoints = context.minRedeemPoints
        mediaRetentionDays = context.mediaRetentionDays
        timezone = context.timezone ?: "Asia/Shanghai"
    }

    private fun updateMemberContextSettings(
        cashCnyPer10Points: Int = cashPerTenPoints,
        minRedeemPoints: Int = this.minRedeemPoints,
        mediaRetentionDays: Int = this.mediaRetentionDays
    ) {
        memberContextSnapshot = memberContextSnapshot?.copy(
            cashCnyPer10Points = cashCnyPer10Points.toDouble(),
            minRedeemPoints = minRedeemPoints,
            mediaRetentionDays = mediaRetentionDays,
            timezone = timezone
        )
    }

    private fun updateMemberContextFamilyProfile(
        familyName: String,
        cashCnyPer10Points: Int = this.cashPerTenPoints,
        minRedeemPoints: Int = this.minRedeemPoints,
        mediaRetentionDays: Int = this.mediaRetentionDays
    ) {
        this.familyName = familyName
        memberContextSnapshot = memberContextSnapshot?.copy(
            familyName = familyName,
            cashCnyPer10Points = cashCnyPer10Points.toDouble(),
            minRedeemPoints = minRedeemPoints,
            mediaRetentionDays = mediaRetentionDays,
            timezone = timezone
        )
    }

    private fun applySnapshot(snapshot: OfflineAppSnapshot) {
        applyMemberContext(snapshot.memberContext)
        childSourceTasks = snapshot.childSourceTasks
        parentSourceTasks = snapshot.parentSourceTasks
        ledgerSourceEntries = snapshot.ledgerEntries
        redemptionSourceEntries = snapshot.redemptions
        familyChildrenSource = snapshot.familyChildren
        childAccountsSource = snapshot.childAccounts
        notificationChannelsSource = snapshot.notificationChannels
        dailyReportSource = snapshot.dailyReports
        submissionSource = snapshot.submissions
        parentStats = snapshot.redemptionStats.toRedemptionStatsUi()

        when (currentRole) {
            ServerRole.CHILD -> applyChildState()
            ServerRole.PARENT -> applyParentState()
            null -> Unit
        }
    }

    private fun persistSnapshot() {
        val context = memberContextSnapshot ?: return
        offlineStore?.saveSnapshot(
            OfflineAppSnapshot(
                memberContext = context,
                childSourceTasks = childSourceTasks,
                parentSourceTasks = parentSourceTasks,
                ledgerEntries = ledgerSourceEntries,
                redemptions = redemptionSourceEntries,
                redemptionStats = ServerRedemptionStats(
                    last7DaysCount = parentStats.last7DaysCount,
                    last7DaysCashCny = parentStats.last7DaysCashCny,
                    currentMonthCount = parentStats.currentMonthCount,
                    currentMonthCashCny = parentStats.currentMonthCashCny
                ),
                familyChildren = familyChildrenSource,
                childAccounts = childAccountsSource,
                notificationChannels = notificationChannelsSource,
                dailyReports = dailyReportSource,
                submissions = submissionSource
            )
        )
    }

    private fun savePendingActions() {
        val memberId = memberContextSnapshot?.memberId
        if (memberId == null) {
            offlineStore?.clearPendingActions()
            return
        }
        offlineStore?.savePendingActions(memberId, pendingActions.toList())
    }

    private fun enqueuePendingAction(action: PendingSyncAction) {
        pendingActions += action
        savePendingActions()
        persistSnapshot()
    }

    private fun applyChildState() {
        tasks.replaceWith(childSourceTasks.map { it.toAppDemoTask() }.sortedForChild())
        ledger.replaceWith(ledgerSourceEntries.map { it.toAppLedgerEntryUi() })
        taskSubmissions.replaceWith(submissionSource)
        childSummary = dailyReportSource.toChildSummary(tasks.toList())
        clearSelectedTaskInsight()
    }

    private fun applyParentState() {
        val children = familyChildrenSource.map { child ->
            val childTasks = parentSourceTasks.filter { task ->
                task.belongsToChild(child.id, child.childDisplayName)
            }
            val childReports = dailyReportSource.filter { it.childMemberId == child.id }
            val childTaskIds = childTasks.map(ServerTaskOccurrence::id).toSet()
            ParentChildSummaryUi(
                memberId = child.id,
                childName = child.childDisplayName ?: "未命名孩子",
                todayCompleted = childTasks.count { it.status == "completed" },
                todayTotal = childTasks.size,
                pendingCount = childTasks.count { it.status == "pending" },
                runningCount = childTasks.count { it.status == "running" },
                pendingReviewCount = submissionSource.count { it.occurrenceId in childTaskIds },
                trendText = childReports.toCompletionRateText(7)
            )
        }.sortedBy { it.childName }

        tasks.replaceWith(parentSourceTasks.map { it.toAppDemoTask() })
        taskSubmissions.replaceWith(submissionSource)
        parentChildren.replaceWith(children)
        val childAccountItems = if (childAccountsSource.isNotEmpty()) {
            childAccountsSource.sortedBy { it.childName }
        } else {
            familyChildrenSource
                .filter { it.role == "child" }
                .map { child ->
                    ServerChildAccount(
                        memberId = child.id,
                        childName = child.childDisplayName ?: "未命名孩子",
                        email = "",
                        createdAt = ""
                    )
                }
                .sortedBy { it.childName }
        }
        childAccounts.replaceWith(childAccountItems)

        val notificationChannel = notificationChannelsSource.firstOrNull()
        notificationChannelId = notificationChannel?.id
        notificationChannelType = notificationChannel?.channelType ?: "dingtalk"
        notificationWebhookUrl = notificationChannel?.resolveWebhookUrl().orEmpty()
        notificationEnabled = notificationChannel?.isEnabled ?: false

        if (selectedChildId == null || children.none { it.memberId == selectedChildId }) {
            selectedChildId = children.firstOrNull()?.memberId
        }

        redemptionRequests.replaceWith(redemptionSourceEntries.map(ServerRedemptionRequest::toUi))
        parentOverview = dailyReportSource.toParentOverview(
            serverTasks = parentSourceTasks,
            reviewCount = submissionSource.map(ServerTaskSubmission::occurrenceId).distinct().size
        )
        rebuildParentTaskRows(parentSourceTasks)
    }

    private suspend fun syncPendingActions(): PendingSyncPassResult {
        if (pendingActions.isEmpty()) {
            return PendingSyncPassResult(processedAny = false, blockedByNetwork = false)
        }

        var processed = false
        for (action in pendingActions.toList()) {
            try {
                replayPendingAction(action)
                pendingActions.remove(action)
                savePendingActions()
                processed = true
            } catch (error: IOException) {
                flashMessage = "网络仍未恢复，已保留 ${pendingActions.size} 项待同步"
                return PendingSyncPassResult(processedAny = processed, blockedByNetwork = true)
            }
        }

        if (pendingActions.isEmpty()) {
            offlineStore?.clearPendingActions(memberContextSnapshot?.memberId)
            if (processed) {
                when (currentRole) {
                    ServerRole.CHILD -> refreshChildData()
                    ServerRole.PARENT -> refreshParentData()
                    null -> Unit
                }
            }
            flashMessage = "本地离线数据已同步到服务器"
        }
        return PendingSyncPassResult(processedAny = processed, blockedByNetwork = false)
    }

    private suspend fun replayPendingAction(action: PendingSyncAction) {
        when (action) {
            is PendingSyncAction.StartTask -> gateway.startTask(action.occurrenceId)
            is PendingSyncAction.CompleteTask -> gateway.completeTask(action.occurrenceId)
            is PendingSyncAction.FinishTask -> gateway.finishTask(
                action.occurrenceId,
                action.actualDurationSeconds
            )

            is PendingSyncAction.SubmitTaskDelivery -> {
                val bytes = pendingMediaStore?.read(action.localMediaKey)
                    ?: error("离线媒体文件不存在：${action.localMediaKey}")
                val storagePath = buildStoragePath(action.occurrenceId, action.fileName)
                gateway.uploadTaskFile(
                    storagePath = storagePath,
                    bytes = bytes,
                    contentType = action.contentType
                )
                gateway.createTaskSubmission(
                    occurrenceId = action.occurrenceId,
                    submissionType = action.submissionType,
                    storagePath = storagePath
                )
                when (action.completionMode) {
                    DeliveryCompletionMode.COMPLETE -> gateway.completeTask(action.occurrenceId)
                    DeliveryCompletionMode.FINISH -> gateway.finishTask(
                        action.occurrenceId,
                        action.actualDurationSeconds
                    )
                }
                pendingMediaStore?.delete(action.localMediaKey)
            }

            is PendingSyncAction.SubmitRedemption -> gateway.submitRedemption(action.pointsRequested)
            is PendingSyncAction.ReviewRedemption -> gateway.reviewRedemption(
                action.requestId,
                action.approve
            )

            is PendingSyncAction.SaveNotificationSettings -> gateway.saveNotificationChannel(
                channelId = action.channelId,
                channelType = action.channelType,
                webhookUrl = action.webhookUrl,
                isEnabled = action.isEnabled
            )

            is PendingSyncAction.UpdateFamilySettings -> gateway.updateFamilySettings(
                familyName = action.familyName,
                cashCnyPer10Points = action.cashCnyPer10Points.toDouble(),
                minRedeemPoints = action.minRedeemPoints,
                mediaRetentionDays = action.mediaRetentionDays
            )

            is PendingSyncAction.UpdateChildProfile -> gateway.updateChildProfile(
                memberId = action.memberId,
                childName = action.childName
            )

            is PendingSyncAction.AddTask -> gateway.createQuickTask(
                childMemberId = action.childMemberId,
                name = action.name,
                mode = action.mode,
                deliveryRequirement = action.deliveryRequirement,
                points = action.points,
                targetMinutes = action.targetMinutes,
                scheduledTime = action.scheduledTime
            )

            is PendingSyncAction.UpdateTask -> gateway.updateTaskTemplate(
                taskTemplateId = action.taskTemplateId,
                name = action.name,
                mode = action.mode,
                deliveryRequirement = action.deliveryRequirement,
                points = action.points,
                targetMinutes = action.targetMinutes,
                scheduledTime = action.scheduledTime
            )

            is PendingSyncAction.DeleteTask -> when {
                !action.occurrenceId.isNullOrBlank() -> gateway.deleteTaskOccurrence(action.occurrenceId)
                !action.taskTemplateId.isNullOrBlank() -> gateway.deleteTaskTemplate(action.taskTemplateId)
                else -> Unit
            }
            PendingSyncAction.ResetDay -> gateway.resetTodayOccurrences()
        }
    }

    private fun applyOptimisticStartTask(taskId: String) {
        val now = Instant.now()
        updateChildTask(taskId) { task ->
            if (task.status != "pending") {
                task
            } else {
                task.copy(
                    status = "running",
                    startedAt = now.toString(),
                    completedAt = null,
                    actualDurationSeconds = 0
                )
            }
        }
        applyChildOfflineState()
    }

    private fun applyOptimisticCompleteTask(
        taskId: String,
        taskTitle: String,
        points: Int,
        durationSeconds: Int? = null
    ) {
        val now = Instant.now()
        val before = childSourceTasks.firstOrNull { it.id == taskId }
        updateChildTask(taskId) { task ->
            task.copy(
                status = "completed",
                startedAt = task.startedAt,
                completedAt = now.toString(),
                actualDurationSeconds = durationSeconds ?: task.actualDurationSeconds
            )
        }
        if (before?.status != "completed") {
            addLocalTaskReward(taskTitle, points, now)
        }
        applyChildOfflineState()
    }

    private fun applyOptimisticDelivery(
        taskId: String,
        submissionType: String,
        taskTitle: String,
        points: Int,
        durationSeconds: Int
    ) {
        val now = Instant.now()
        submissionSource = listOf(
            ServerTaskSubmission(
                id = "offline-submission-${UUID.randomUUID()}",
                occurrenceId = taskId,
                submissionType = submissionType,
                storagePath = "offline://$taskId/${UUID.randomUUID()}",
                uploadedAt = now.toString()
            )
        ) + submissionSource.filterNot { it.occurrenceId == taskId }
        applyOptimisticCompleteTask(taskId, taskTitle, points, durationSeconds.takeIf { it > 0 })
    }

    private fun applyOptimisticRedemptionRequest(points: Int) {
        val now = Instant.now().toString()
        redemptionSourceEntries = listOf(
            ServerRedemptionRequest(
                id = "offline-redemption-${UUID.randomUUID()}",
                childName = childName,
                pointsRequested = points,
                cashAmountCny = computeCashAmount(points),
                requestedAt = now,
                status = "pending"
            )
        ) + redemptionSourceEntries
        applyParentOrChildRedemptionRefresh()
    }

    private fun applyOptimisticRedemptionReview(requestId: String, approve: Boolean) {
        redemptionSourceEntries = redemptionSourceEntries.map { request ->
            if (request.id == requestId) {
                request.copy(status = if (approve) "approved" else "rejected")
            } else {
                request
            }
        }
        applyParentOrChildRedemptionRefresh()
    }

    private fun applyParentOrChildRedemptionRefresh() {
        parentStats = recomputeParentStatsFromSource()
        when (currentRole) {
            ServerRole.PARENT -> applyParentState()
            ServerRole.CHILD -> persistSnapshot()
            null -> Unit
        }
        persistSnapshot()
    }

    private fun applyOptimisticNotificationSettings(
        channelType: String,
        webhookUrl: String,
        enabled: Boolean
    ) {
        notificationChannelType = channelType
        notificationWebhookUrl = webhookUrl
        notificationEnabled = enabled
        val channelId = notificationChannelId ?: "offline-channel"
        notificationChannelId = channelId
        notificationChannelsSource = listOf(
            ServerNotificationChannel(
                id = channelId,
                channelType = channelType,
                configJson = webhookConfig(webhookUrl),
                isEnabled = enabled
            )
        )
        applyParentState()
        persistSnapshot()
    }

    private fun applyOptimisticFamilySettings(
        cashCnyPer10Points: Int,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) {
        cashPerTenPoints = cashCnyPer10Points
        this.minRedeemPoints = minRedeemPoints
        this.mediaRetentionDays = mediaRetentionDays
        updateMemberContextSettings(cashCnyPer10Points, minRedeemPoints, mediaRetentionDays)
        persistSnapshot()
    }

    private fun applyOptimisticFamilyProfile(
        familyName: String,
        cashCnyPer10Points: Int,
        minRedeemPoints: Int,
        mediaRetentionDays: Int
    ) {
        cashPerTenPoints = cashCnyPer10Points
        this.minRedeemPoints = minRedeemPoints
        this.mediaRetentionDays = mediaRetentionDays
        updateMemberContextFamilyProfile(
            familyName = familyName,
            cashCnyPer10Points = cashCnyPer10Points,
            minRedeemPoints = minRedeemPoints,
            mediaRetentionDays = mediaRetentionDays
        )
        persistSnapshot()
    }

    private fun applyOptimisticChildProfile(memberId: String, childName: String) {
        val normalized = childName.trim()
        val previousName = childAccountsSource.firstOrNull { it.memberId == memberId }?.childName
            ?: familyChildrenSource.firstOrNull { it.id == memberId }?.childDisplayName

        childAccountsSource = childAccountsSource.map { account ->
            if (account.memberId == memberId) {
                account.copy(childName = normalized)
            } else {
                account
            }
        }
        familyChildrenSource = familyChildrenSource.map { child ->
            if (child.id == memberId) {
                child.copy(childDisplayName = normalized)
            } else {
                child
            }
        }
        parentSourceTasks = parentSourceTasks.map { task ->
            if (task.belongsToChild(memberId, previousName)) {
                task.copy(childName = normalized)
            } else {
                task
            }
        }
        applyParentState()
        persistSnapshot()
    }

    private fun applyOptimisticAddTask(
        childMemberId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        val taskTemplateId = "offline-template-${UUID.randomUUID()}"
        val childDisplayName = familyChildrenSource.firstOrNull { it.id == childMemberId }?.childDisplayName
        parentSourceTasks = parentSourceTasks + ServerTaskOccurrence(
            id = "offline-occurrence-${UUID.randomUUID()}",
            taskTemplateId = taskTemplateId,
            childMemberId = childMemberId,
            taskNameSnapshot = title,
            modeSnapshot = mode.toServerValue(),
            deliveryRequirementSnapshot = deliveryRequirement.toServerValue(),
            pointValueSnapshot = points,
            targetDurationSecondsSnapshot = targetMinutes?.times(60),
            scheduledTimeLocal = scheduledTimeLabel,
            status = "pending",
            childName = childDisplayName
        )
        applyParentState()
        persistSnapshot()
    }

    private fun applyOptimisticUpdateTask(
        taskTemplateId: String,
        title: String,
        mode: TaskMode,
        deliveryRequirement: DeliveryRequirement,
        points: Int,
        targetMinutes: Int?,
        scheduledTimeLabel: String?
    ) {
        parentSourceTasks = parentSourceTasks.map { task ->
            if (task.taskTemplateId == taskTemplateId) {
                task.copy(
                    taskNameSnapshot = title,
                    modeSnapshot = mode.toServerValue(),
                    deliveryRequirementSnapshot = deliveryRequirement.toServerValue(),
                    pointValueSnapshot = points,
                    targetDurationSecondsSnapshot = targetMinutes?.times(60),
                    scheduledTimeLocal = scheduledTimeLabel
                )
            } else {
                task
            }
        }
        applyParentState()
        persistSnapshot()
    }

    private fun applyOptimisticDeleteTask(occurrenceId: String) {
        val removedIds = buildSet {
            parentSourceTasks
                .filter { it.id == occurrenceId && it.status != "completed" }
                .forEach { add(it.id) }
            childSourceTasks
                .filter { it.id == occurrenceId && it.status != "completed" }
                .forEach { add(it.id) }
        }

        parentSourceTasks = parentSourceTasks.filterNot { it.id in removedIds }
        childSourceTasks = childSourceTasks.filterNot { it.id in removedIds }
        submissionSource = submissionSource.filter { submission ->
            parentSourceTasks.any { it.id == submission.occurrenceId } ||
                childSourceTasks.any { it.id == submission.occurrenceId }
        }
        when (currentRole) {
            ServerRole.PARENT -> applyParentState()
            ServerRole.CHILD -> applyChildState()
            null -> Unit
        }
        persistSnapshot()
    }

    private fun applyOptimisticResetDay() {
        parentSourceTasks = parentSourceTasks.map { task ->
            task.copy(
                status = "pending",
                startedAt = null,
                completedAt = null,
                actualDurationSeconds = 0
            )
        }
        childSourceTasks = childSourceTasks.map { task ->
            task.copy(
                status = "pending",
                startedAt = null,
                completedAt = null,
                actualDurationSeconds = 0
            )
        }
        submissionSource = emptyList()
        ledgerSourceEntries = ledgerSourceEntries
        rebuildTodayChildReport()
        when (currentRole) {
            ServerRole.PARENT -> applyParentState()
            ServerRole.CHILD -> applyChildState()
            null -> Unit
        }
        persistSnapshot()
    }

    private fun updateChildTask(
        taskId: String,
        transform: (ServerTaskOccurrence) -> ServerTaskOccurrence
    ) {
        childSourceTasks = childSourceTasks.map { task ->
            if (task.id == taskId) transform(task) else task
        }
    }

    private fun addLocalTaskReward(taskTitle: String, points: Int, at: Instant) {
        val nextBalance = (ledgerSourceEntries.firstOrNull()?.balanceAfter ?: 0) + points
        ledgerSourceEntries = listOf(
            ServerLedgerEntry(
                id = "offline-ledger-${UUID.randomUUID()}",
                createdAt = at.toString(),
                changeType = "task_reward",
                pointsDelta = points,
                balanceAfter = nextBalance,
                note = "$taskTitle completed"
            )
        ) + ledgerSourceEntries
    }

    private fun applyChildOfflineState() {
        rebuildTodayChildReport()
        applyChildState()
        persistSnapshot()
    }

    private fun rebuildTodayChildReport() {
        val memberId = memberContextSnapshot?.memberId ?: return
        val today = beijingDate.toString()
        val todayReport = ServerDailyChildReport(
            childMemberId = memberId,
            localDate = today,
            taskTotalCount = childSourceTasks.size,
            taskCompletedCount = childSourceTasks.count { it.status == "completed" },
            totalDurationSeconds = childSourceTasks.sumOf { it.actualDurationSeconds ?: 0 },
            pointsAwarded = ledgerSourceEntries
                .filter { entry ->
                    entry.pointsDelta > 0 && entry.createdAt.toBeijingDateOrNull()?.toString() == today
                }
                .sumOf(ServerLedgerEntry::pointsDelta),
            pointsRedeemed = 0,
            redemptionCashCny = 0.0
        )
        dailyReportSource = dailyReportSource
            .filterNot { it.childMemberId == memberId && it.localDate == today } +
            todayReport
    }

    private fun recomputeParentStatsFromSource(): RedemptionStatsUi {
        val today = beijingDate
        val last7Threshold = today.minusDays(6)
        val firstDayOfMonth = today.withDayOfMonth(1)
        val approved = redemptionSourceEntries.filter { it.status == "approved" }
        return RedemptionStatsUi(
            last7DaysCount = approved.count { request ->
                request.requestedAt.toBeijingDateOrNull()?.let { it >= last7Threshold } == true
            },
            last7DaysCashCny = approved
                .filter { request ->
                    request.requestedAt.toBeijingDateOrNull()?.let { it >= last7Threshold } == true
                }
                .sumOf(ServerRedemptionRequest::cashAmountCny),
            currentMonthCount = approved.count { request ->
                request.requestedAt.toBeijingDateOrNull()?.let { !it.isBefore(firstDayOfMonth) } == true
            },
            currentMonthCashCny = approved
                .filter { request ->
                    request.requestedAt.toBeijingDateOrNull()?.let { !it.isBefore(firstDayOfMonth) } == true
                }
                .sumOf(ServerRedemptionRequest::cashAmountCny)
        )
    }

    private fun rebuildParentTaskRows(sourceTasks: List<ServerTaskOccurrence>) {
        val selectedChild = parentChildren.firstOrNull { it.memberId == selectedChildId }
        val submissionOccurrenceIds = taskSubmissions.map(ServerTaskSubmission::occurrenceId).toSet()
        val childFiltered = if (selectedChild == null) {
            sourceTasks
        } else {
            sourceTasks.filter { it.belongsToChild(selectedChild.memberId, selectedChild.childName) }
        }
        val focusFiltered = when (parentFocus) {
            ParentDashboardFocus.TODAY -> childFiltered
            ParentDashboardFocus.PENDING -> childFiltered.filter { it.status != "completed" }
            ParentDashboardFocus.REVIEW -> childFiltered.filter { it.id in submissionOccurrenceIds }
        }
        val statusFiltered = when (parentFilter) {
            ParentTaskFilter.ALL -> focusFiltered
            ParentTaskFilter.PENDING -> focusFiltered.filter { it.status == "pending" }
            ParentTaskFilter.RUNNING -> focusFiltered.filter { it.status == "running" }
            ParentTaskFilter.COMPLETED -> focusFiltered.filter { it.status == "completed" }
            ParentTaskFilter.REVIEW -> focusFiltered.filter { it.id in submissionOccurrenceIds }
        }

        parentTaskRows.replaceWith(
            statusFiltered
                .sortedWith(compareBy<ServerTaskOccurrence>({ sortWeight(it.status) }, { it.scheduledTimeLocal ?: "99:99" }))
                .map { occurrence ->
                    val latestSubmission = taskSubmissions.firstOrNull { it.occurrenceId == occurrence.id }
                    ParentTaskRowUi(
                        id = occurrence.id,
                        taskTemplateId = occurrence.taskTemplateId.orEmpty(),
                        childMemberId = occurrence.childMemberId,
                        childName = occurrence.childName ?: childName,
                        title = occurrence.taskNameSnapshot,
                        status = occurrence.toAppParentTaskFilter(),
                        statusLabel = occurrence.status.toStatusLabel(),
                        timeLabel = occurrence.toTimeLabel(),
                        deliveryLabel = occurrence.deliveryRequirementSnapshot.toDeliveryLabel(),
                        pointsLabel = "+${occurrence.pointValueSnapshot} 积分",
                        note = buildTaskNote(occurrence, latestSubmission),
                        mode = occurrence.modeSnapshot,
                        deliveryRequirement = occurrence.deliveryRequirementSnapshot,
                        pointValue = occurrence.pointValueSnapshot,
                        targetMinutes = occurrence.targetDurationSecondsSnapshot?.div(60),
                        scheduledTimeLocal = occurrence.scheduledTimeLocal
                    )
                }
        )
    }

    private fun buildTaskNote(
        occurrence: ServerTaskOccurrence,
        latestSubmission: ServerTaskSubmission?
    ): String {
        val base = occurrence.toNoteLabel()
        val submissionText = latestSubmission?.let {
            "已上传${it.submissionType.toSubmissionLabel()}，时间 ${it.submissionUploadedAtLabel()}"
        }
        return listOfNotNull(base, submissionText).joinToString(" | ")
    }

    private fun buildStoragePath(taskId: String, fileName: String): String {
        val safeName = fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeLast(60)
            .ifBlank { "delivery.bin" }
        return "$taskId/${UUID.randomUUID()}-$safeName"
    }

    private fun computeCashAmount(points: Int): Double {
        return (points / 10.0) * cashPerTenPoints.toDouble()
    }

    private suspend fun sendFamilyNotification(message: String) {
        val familyId = currentFamilyId ?: return
        runCatching { gateway.sendNotification(familyId, message) }
            .onFailure { error -> errorLog("sendFamilyNotification failed", error) }
    }

    private fun currentDurationSeconds(taskId: String): Int {
        val startedAt = tasks.firstOrNull { it.id == taskId }?.startedAt ?: return 0
        return Duration.between(startedAt, Instant.now()).seconds.toInt().coerceAtLeast(0)
    }

    private fun clearSelectedTaskInsight() {
        selectedChildTaskId = null
        selectedChildTaskInsight = null
        isChildTaskInsightLoading = false
    }

    private fun syncPendingActionsAsync() {
        if (!pendingSyncInFlight.compareAndSet(false, true)) {
            return
        }
        syncScope.launch {
            try {
                while (true) {
                    val result = syncPendingActions()
                    if (pendingActions.isEmpty()) {
                        break
                    }
                    if (result.blockedByNetwork || !result.processedAny) {
                        break
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorLog("syncPendingActionsAsync failed", error)
            } finally {
                pendingSyncInFlight.set(false)
            }
        }
    }

    private suspend fun <T> runBusy(message: String? = null, block: suspend () -> T): T? {
        return try {
            isBusy = true
            if (message != null) {
                flashMessage = message
            }
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorLog("runBusy failed message=$message error=${error.message}", error)
            flashMessage = error.message ?: "请求失败"
            null
        } finally {
            isBusy = false
        }
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun errorLog(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
}

private fun emptyChildSummary(): ChildProgressSummaryUi {
    return ChildProgressSummaryUi(
        todayCompleted = 0,
        todayTotal = 0,
        pendingCount = 0,
        completionRateText = "0%",
        yesterdayText = "昨天 0 / 0",
        last7DaysText = "最近7天 0%",
        last30DaysText = "最近30天 0%"
    )
}

private fun ServerTaskOccurrence.belongsToChild(memberId: String, childDisplayName: String?): Boolean {
    return childMemberId == memberId || (childMemberId.isNullOrBlank() && childName == childDisplayName)
}

private fun emptyParentOverview(): ParentOverviewUi {
    return ParentOverviewUi(
        todaySummaryText = "0 / 0",
        pendingCount = 0,
        reviewCount = 0,
        yesterdayText = "昨天 0 / 0",
        last7DaysText = "最近7天 0%",
        last30DaysText = "最近30天 0%"
    )
}

private fun List<DemoTask>.sortedForChild(): List<DemoTask> {
    return sortedWith(
        compareBy<DemoTask>(
            { task ->
                when (task.status) {
                    TaskStatus.RUNNING -> 0
                    TaskStatus.PENDING -> 1
                    TaskStatus.COMPLETED -> 2
                }
            },
            { task -> task.scheduledTimeLabel ?: "99:99" }
        )
    )
}

private fun List<ServerDailyChildReport>.toChildSummary(tasks: List<DemoTask>): ChildProgressSummaryUi {
    val today = beijingDate
    val todayCompleted = tasks.count { it.status == TaskStatus.COMPLETED }
    val todayTotal = tasks.size
    val pendingCount = (todayTotal - todayCompleted).coerceAtLeast(0)
    val yesterday = firstOrNull { it.reportLocalDate() == today.minusDays(1) }

    return ChildProgressSummaryUi(
        todayCompleted = todayCompleted,
        todayTotal = todayTotal,
        pendingCount = pendingCount,
        completionRateText = formatRate(todayCompleted, todayTotal),
        yesterdayText = "昨天 ${yesterday?.taskCompletedCount ?: 0} / ${yesterday?.taskTotalCount ?: 0}",
        last7DaysText = "最近7天 ${toCompletionRateText(7)}",
        last30DaysText = "最近30天 ${toCompletionRateText(30)}"
    )
}

private fun ServerTaskHistoryDetail.toChildTaskInsightUi(taskId: String): ChildTaskInsightUi {
    val latestCompletion = occurrences
        .mapNotNull { it.completedAt }
        .maxByOrNull { OffsetDateTime.parse(it).toInstant() }

    return ChildTaskInsightUi(
        taskId = taskId,
        taskTitle = taskTitle,
        latestCompletionText = latestCompletion?.let(::formatBeijingHistoryDateTime) ?: "最近暂无完成记录",
        yesterdayStat = occurrences.toWindowStat("昨天", 1, exactYesterday = true),
        last7DaysStat = occurrences.toWindowStat("最近7天", 7),
        last30DaysStat = occurrences.toWindowStat("最近30天", 30),
        last180DaysStat = occurrences.toWindowStat("最近半年", 180)
    )
}

private fun List<ServerDailyChildReport>.toParentOverview(
    serverTasks: List<ServerTaskOccurrence>,
    reviewCount: Int
): ParentOverviewUi {
    val todayCompleted = serverTasks.count { it.status == "completed" }
    val todayTotal = serverTasks.size
    val pendingCount = serverTasks.count { it.status != "completed" }
    val yesterdayReports = filter { it.reportLocalDate() == beijingDate.minusDays(1) }
    val yesterdayCompleted = yesterdayReports.sumOf { it.taskCompletedCount }
    val yesterdayTotal = yesterdayReports.sumOf { it.taskTotalCount }

    return ParentOverviewUi(
        todaySummaryText = "$todayCompleted / $todayTotal",
        pendingCount = pendingCount,
        reviewCount = reviewCount,
        yesterdayText = "昨天 $yesterdayCompleted / $yesterdayTotal",
        last7DaysText = "最近7天 ${toCompletionRateText(7)}",
        last30DaysText = "最近30天 ${toCompletionRateText(30)}"
    )
}

private fun List<ServerTaskHistoryOccurrence>.toWindowStat(
    label: String,
    days: Int,
    exactYesterday: Boolean = false
): ChildTaskWindowStatUi {
    val selected = if (exactYesterday) {
        filter { it.historyLocalDate() == beijingDate.minusDays(1) }
    } else {
        val threshold = beijingDate.minusDays((days - 1).toLong())
        filter { it.historyLocalDate() >= threshold }
    }
    val total = selected.size
    val completed = selected.count { it.status == "completed" }
    return ChildTaskWindowStatUi(
        label = label,
        completedCount = completed,
        totalCount = total,
        completionRateText = formatRate(completed, total)
    )
}

private fun List<ServerDailyChildReport>.toCompletionRateText(days: Int): String {
    val threshold = beijingDate.minusDays((days - 1).toLong())
    val selected = filter { it.reportLocalDate() >= threshold }
    val total = selected.sumOf { it.taskTotalCount }
    val completed = selected.sumOf { it.taskCompletedCount }
    return formatRate(completed, total)
}

private data class PendingSyncPassResult(
    val processedAny: Boolean,
    val blockedByNetwork: Boolean
)

private fun formatRate(completed: Int, total: Int): String {
    if (total <= 0) return "0%"
    return "${((completed.toDouble() / total.toDouble()) * 100).roundToInt()}%"
}

private fun ServerMemberContext.resolveServerRole(): ServerRole {
    return when (role) {
        "parent" -> ServerRole.PARENT
        else -> ServerRole.CHILD
    }
}

private fun ServerRedemptionStats.toRedemptionStatsUi(): RedemptionStatsUi {
    return RedemptionStatsUi(
        last7DaysCount = last7DaysCount,
        last7DaysCashCny = last7DaysCashCny,
        currentMonthCount = currentMonthCount,
        currentMonthCashCny = currentMonthCashCny
    )
}

private fun ServerTaskOccurrence.toAppDemoTask(): DemoTask {
    val resolvedTitle = if (childName.isNullOrBlank()) {
        taskNameSnapshot
    } else {
        "$childName · $taskNameSnapshot"
    }

    return DemoTask(
        id = id,
        childMemberId = childMemberId ?: "",
        childName = childName ?: "",
        title = resolvedTitle,
        mode = modeSnapshot.toAppTaskMode(),
        deliveryRequirement = deliveryRequirementSnapshot.toAppDeliveryRequirement(),
        points = pointValueSnapshot,
        targetMinutes = targetDurationSecondsSnapshot?.div(60),
        scheduledTimeLabel = scheduledTimeLocal,
        status = status.toAppTaskStatus(),
        startedAt = startedAt?.let(::parseAppServerInstant),
        completedAtLabel = completedAt?.let(::formatAppServerDateTime),
        actualDurationSeconds = actualDurationSeconds?.toLong() ?: 0L
    )
}

private fun ServerTaskOccurrence.toAppParentTaskFilter(): ParentTaskFilter {
    return when (status) {
        "running" -> ParentTaskFilter.RUNNING
        "completed" -> ParentTaskFilter.COMPLETED
        else -> ParentTaskFilter.PENDING
    }
}

private fun ServerLedgerEntry.toAppLedgerEntryUi(): LedgerEntryUi {
    return LedgerEntryUi(
        timeLabel = formatAppServerDateTime(createdAt),
        title = note
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeAppLedgerNote)
            ?: defaultAppLedgerTitle(changeType),
        delta = pointsDelta,
        balanceAfter = balanceAfter
    )
}

private fun ServerNotificationChannel.resolveWebhookUrl(): String {
    return configJson["webhook_url"]?.toString()?.trim('"').orEmpty()
}

private fun ServerDailyChildReport.reportLocalDate(): LocalDate = parseAppServerLocalDate(localDate)

private fun ServerTaskHistoryOccurrence.historyLocalDate(): LocalDate = parseAppServerLocalDate(localDate)

private fun ServerTaskSubmission.submissionUploadedAtLabel(): String = formatAppServerDateTime(uploadedAt)

private fun String.toAppTaskMode(): TaskMode {
    return when (this) {
        "countdown" -> TaskMode.COUNTDOWN
        "stopwatch" -> TaskMode.STOPWATCH
        else -> TaskMode.CHECK_ONLY
    }
}

private fun String.toAppDeliveryRequirement(): DeliveryRequirement {
    return when (this) {
        "photo" -> DeliveryRequirement.PHOTO
        "video" -> DeliveryRequirement.VIDEO
        "audio" -> DeliveryRequirement.AUDIO
        else -> DeliveryRequirement.NONE
    }
}

private fun String.toAppTaskStatus(): TaskStatus {
    return when (this) {
        "running" -> TaskStatus.RUNNING
        "completed" -> TaskStatus.COMPLETED
        else -> TaskStatus.PENDING
    }
}

private fun defaultAppLedgerTitle(changeType: String): String {
    return when (changeType) {
        "task_reward" -> "任务完成"
        "redemption_approved" -> "积分兑换通过"
        "manual_adjust" -> "家长调整积分"
        else -> "积分变动"
    }
}

private fun normalizeAppLedgerNote(note: String): String {
    val trimmed = note.trim()
    val lower = trimmed.lowercase()
    return when {
        lower == "redemption approved" -> "积分兑换通过"
        lower.endsWith(" completed") -> "${trimmed.removeSuffix(" completed")} 已完成"
        else -> trimmed
    }
}

private fun formatAppServerDateTime(value: String): String {
    return parseAppServerInstant(value).atZone(beijingZoneId).format(beijingDateTimeFormatter)
}

private fun parseAppServerInstant(value: String): Instant {
    return OffsetDateTime.parse(value).toInstant()
}

private fun parseAppServerLocalDate(value: String): LocalDate {
    return runCatching { LocalDate.parse(value) }
        .getOrElse {
            OffsetDateTime.parse(value).toInstant().atZone(beijingZoneId).toLocalDate()
        }
}

private fun formatBeijingHistoryDateTime(value: String): String {
    return OffsetDateTime.parse(value)
        .toInstant()
        .atZone(beijingZoneId)
        .format(beijingDateTimeFormatter)
}

private fun sortWeight(status: String): Int {
    return when (status) {
        "running" -> 0
        "pending" -> 1
        "completed" -> 2
        else -> 3
    }
}

private fun String.toStatusLabel(): String {
    return when (this) {
        "running" -> "执行中"
        "completed" -> "已完成"
        else -> "未完成"
    }
}

private fun String.toDeliveryLabel(): String {
    return when (this) {
        "photo" -> "照片"
        "video" -> "视频"
        "audio" -> "音频"
        else -> "无要求"
    }
}

private fun String.toSubmissionLabel(): String {
    return when (this) {
        "photo" -> "照片"
        "video" -> "视频"
        "audio" -> "音频"
        else -> "提交内容"
    }
}

private fun ServerTaskOccurrence.toTimeLabel(): String {
    return when (status) {
        "running" -> "已开始 ${scheduledTimeLocal ?: "--:--"}"
        "completed" -> completedAt?.let { "完成于 ${it.substring(11, 16)}" } ?: "已完成"
        else -> scheduledTimeLocal ?: "今天"
    }
}

private fun ServerTaskOccurrence.toNoteLabel(): String {
    return when (status) {
        "running" -> "计时中 ${actualDurationSeconds ?: 0} 秒"
        "completed" -> {
            if ((actualDurationSeconds ?: 0) > 0) {
                "用时 ${actualDurationSeconds} 秒"
            } else {
                "已打卡"
            }
        }

        else -> "等待开始"
    }
}

private fun TaskMode.toServerValue(): String {
    return when (this) {
        TaskMode.CHECK_ONLY -> "check_only"
        TaskMode.COUNTDOWN -> "countdown"
        TaskMode.STOPWATCH -> "stopwatch"
    }
}

private fun DeliveryRequirement.toServerValue(): String {
    return when (this) {
        DeliveryRequirement.NONE -> "none"
        DeliveryRequirement.PHOTO -> "photo"
        DeliveryRequirement.VIDEO -> "video"
        DeliveryRequirement.AUDIO -> "audio"
    }
}

private fun String.toBeijingDateOrNull(): LocalDate? {
    return runCatching {
        OffsetDateTime.parse(this).toInstant().atZone(beijingZoneId).toLocalDate()
    }.getOrNull()
}

private fun webhookConfig(webhookUrl: String) =
    kotlinx.serialization.json.buildJsonObject {
        put("webhook_url", kotlinx.serialization.json.JsonPrimitive(webhookUrl))
    }

private fun <T> MutableList<T>.replaceWith(values: List<T>) {
    clear()
    addAll(values)
}
