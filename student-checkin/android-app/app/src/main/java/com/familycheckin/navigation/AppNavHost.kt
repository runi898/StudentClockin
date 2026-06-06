package com.familycheckin.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familycheckin.BuildConfig
import com.familycheckin.auth.AuthSessionStore
import com.familycheckin.auth.LoginScreen
import com.familycheckin.auth.ParentSignupScreen
import com.familycheckin.auth.RecentLoginStore
import com.familycheckin.child.ChildHomeScreen
import com.familycheckin.child.ChildTaskBoardScreen
import com.familycheckin.child.ChildProfileScreen
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoAppState
import com.familycheckin.demo.DemoRedemptionRequest
import com.familycheckin.demo.RedemptionStatus
import com.familycheckin.parent.ParentChildSummaryUi
import com.familycheckin.parent.ParentDashboardFocus
import com.familycheckin.parent.ParentDashboardScreen
import com.familycheckin.parent.ParentFamilyScreen
import com.familycheckin.parent.ParentProfileScreen
import com.familycheckin.parent.ParentTaskEditorScreen
import com.familycheckin.parent.ParentTaskFilter
import com.familycheckin.parent.ParentTaskRowUi
import com.familycheckin.parent.ParentTaskScreen
import com.familycheckin.parent.RedemptionApprovalScreen
import com.familycheckin.parent.RedemptionRequestUi
import com.familycheckin.parent.RedemptionRequestStatusUi
import com.familycheckin.parent.RedemptionStatsUi
import com.familycheckin.points.LedgerEntryUi
import com.familycheckin.points.PointsScreen
import com.familycheckin.server.FilePendingMediaStore
import com.familycheckin.server.ServerAppState
import com.familycheckin.server.ServerRole
import com.familycheckin.server.ServerTaskSubmission
import com.familycheckin.server.SharedPreferencesOfflineStateStore
import com.familycheckin.server.SupabaseService
import com.familycheckin.tasks.TaskDeliveryScreen
import com.familycheckin.tasks.TaskSubmissionHistoryScreen
import com.familycheckin.ui.FamilyPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val Login = "login"
    const val ParentSignUp = "parent_signup"
    const val ChildHome = "child_home"
    const val ChildPoints = "child_points"
    const val ChildDelivery = "child_delivery"
    const val ParentTasks = "parent_tasks"
    const val ParentTaskCreate = "parent_task_create"
    const val ParentFamily = "parent_family"
    const val ParentRedemptions = "parent_redemptions"
    const val ParentSubmissions = "parent_submissions"
}

private enum class ShellTab(val label: String) {
    HOME("首页"),
    TASKS("任务"),
    PROFILE("我的")
}

@Composable
fun AppNavHost() {
    if (BuildConfig.DEMO_MODE) {
        DemoAppNavHost()
    } else {
        ServerAppNavHost()
    }
}

@Composable
private fun DemoAppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val appState = remember { DemoAppState() }
    val recentLoginStore = remember { RecentLoginStore(context) }
    var selectedChildId by remember { mutableStateOf(appState.parentChildren().firstOrNull()?.memberId) }
    var focus by remember { mutableStateOf(ParentDashboardFocus.TODAY) }
    var filter by remember { mutableStateOf(ParentTaskFilter.ALL) }

    NavHost(navController = navController, startDestination = Routes.Login) {
        composable(Routes.Login) {
            LoginScreen(
                statusMessage = "当前为演示模式",
                recentEmail = recentLoginStore.recentEmail(),
                rememberedPassword = recentLoginStore.rememberedPassword(),
                rememberedPasswordEnabled = recentLoginStore.shouldRememberPassword(),
                onLogin = { email, password, rememberPassword ->
                    recentLoginStore.saveLoginPreference(email, password, rememberPassword)
                    navController.navigate(Routes.ParentTasks)
                },
                onOpenChildDemo = { navController.navigate(Routes.ChildHome) },
                onOpenParentDemo = { navController.navigate(Routes.ParentTasks) },
                onForgotPassword = {
                    appState.flashMessage.value = "演示模式不会发送重置邮件：$it"
                }
            )
        }
        composable(Routes.ChildHome) {
            var selectedTaskId by remember { mutableStateOf<String?>(null) }
            ChildShellScaffold(
                childName = appState.childName,
                summary = appState.childSummary(),
                todayTasks = appState.childTasks(),
                statusMessage = appState.flashMessage.value,
                selectedTaskId = selectedTaskId,
                selectedTaskInsight = selectedTaskId?.let { appState.taskInsight(it) },
                isTaskInsightLoading = false,
                onTaskClick = { taskId ->
                    selectedTaskId = if (selectedTaskId == taskId) null else taskId
                },
                pointsBalance = appState.currentPoints,
                cashPerTenPoints = appState.cashPerTenPoints.value,
                onOpenPoints = { navController.navigate(Routes.ChildPoints) },
                onSwitchAccount = { navController.navigate(Routes.Login) },
                onOpenDelivery = { taskId -> navController.navigate("${Routes.ChildDelivery}/$taskId") },
                onCompleteTask = { appState.completeTask(it) },
                onStartTask = { appState.startTask(it) },
                onFinishTask = { appState.finishTask(it) }
            )
        }
        composable(Routes.ChildPoints) {
            PointsScreen(
                balance = appState.currentPoints,
                cashPerTenPoints = appState.cashPerTenPoints.value,
                minRedeemPoints = 10,
                statusMessage = appState.flashMessage.value,
                ledger = appState.ledger.map {
                    LedgerEntryUi(
                        timeLabel = it.timeLabel,
                        title = it.title,
                        delta = it.delta,
                        balanceAfter = it.balanceAfter
                    )
                },
                onRedeem = { appState.submitRedemption(it) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("${Routes.ChildDelivery}/{taskId}") { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty()
            val task = appState.tasks.firstOrNull { it.id == taskId }
            TaskDeliveryScreen(
                taskTitle = task?.title ?: "任务交付",
                requirement = task?.deliveryRequirement ?: DeliveryRequirement.NONE,
                statusMessage = appState.flashMessage.value,
                isBusy = false,
                onSubmit = { payload ->
                    appState.submitTaskDelivery(taskId, payload.submissionType)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ParentTasks) {
            ParentShellScaffold(
                familyName = "演示家庭",
                parentEmail = "demo-parent@example.com",
                children = appState.parentChildren(),
                selectedChildId = selectedChildId,
                overview = appState.parentOverview(),
                focus = focus,
                filter = filter,
                taskRows = filterDemoParentRows(
                    rows = appState.parentTaskRows(),
                    selectedChildId = selectedChildId,
                    children = appState.parentChildren(),
                    focus = focus,
                    filter = filter
                ),
                statusMessage = appState.flashMessage.value,
                redemptionStats = RedemptionStatsUi(
                    last7DaysCount = appState.redemptionRequests.count { it.status != RedemptionStatus.REJECTED },
                    last7DaysCashCny = appState.redemptionRequests
                        .filter { it.status == RedemptionStatus.APPROVED }
                        .sumOf { it.cashAmountCny },
                    currentMonthCount = appState.redemptionRequests.size,
                    currentMonthCashCny = appState.redemptionRequests
                        .filter { it.status == RedemptionStatus.APPROVED }
                        .sumOf { it.cashAmountCny }
                ),
                onSelectChild = { selectedChildId = it },
                onSelectFocus = { focus = it },
                onSelectFilter = { filter = it },
                onOpenTaskSubmissions = { taskId -> navController.navigate("${Routes.ParentSubmissions}/$taskId") },
                onOpenAddTaskEditor = { navController.navigate(Routes.ParentTaskCreate) },
                onUpdateTask = { taskTemplateId, title, mode, deliveryRequirement, points, duration, scheduledTime, occurrenceId ->
                    appState.updateTask(taskTemplateId, title, mode, deliveryRequirement, points, duration, scheduledTime)
                },
                onDeleteTask = { taskTemplateId, occurrenceId ->
                    appState.deleteTask(occurrenceId)
                },
                onOpenFamilyManage = { navController.navigate(Routes.ParentFamily) },
                onOpenRedemptions = { navController.navigate(Routes.ParentRedemptions) },
                onSwitchAccount = { navController.navigate(Routes.Login) },
                onResetDay = { appState.resetDay() }
            )
        }
        composable(Routes.ParentTaskCreate) {
            ParentTaskEditorScreen(
                familyName = "演示家庭",
                children = appState.parentChildren(),
                selectedChildId = selectedChildId,
                statusMessage = appState.flashMessage.value,
                isBusy = false,
                onBack = { navController.popBackStack() },
                onSubmit = { childMemberId, title, mode, deliveryRequirement, points, duration, scheduledTime ->
                    appState.addTask(childMemberId, title, mode, deliveryRequirement, points, duration, scheduledTime)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.ParentFamily) {
            ParentFamilyScreen(
                familyName = "演示家庭",
                parentEmail = "demo-parent@example.com",
                cashPerTenPoints = appState.cashPerTenPoints.value,
                minRedeemPoints = 10,
                mediaRetentionDays = 30,
                childAccounts = emptyList(),
                notificationChannelType = "dingtalk",
                notificationWebhookUrl = "",
                notificationEnabled = false,
                onResetChildPassword = { _, _ -> appState.flashMessage.value = "演示模式不支持重置孩子密码" },
                onDeleteChild = { _ -> appState.flashMessage.value = "演示模式不支持删除孩子账号" },
                onSaveNotification = { _, _, _ -> appState.flashMessage.value = "演示模式不支持保存通知配置" },
                statusMessage = appState.flashMessage.value,
                isBusy = false,
                onBack = { navController.popBackStack() },
                onSaveSettings = { _, _, _ -> appState.flashMessage.value = "演示模式不会保存设置" },
                onCreateChild = { _, _, _ -> appState.flashMessage.value = "演示模式不会真的创建账号" }
            )
        }
        composable("${Routes.ParentSubmissions}/{taskId}") { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty()
            val submissions = appState.submissionsForTask(taskId).map {
                ServerTaskSubmission(
                    id = it.id,
                    occurrenceId = it.occurrenceId,
                    submissionType = it.submissionType,
                    storagePath = it.storagePath,
                    uploadedAt = "2026-05-28T00:00:00Z"
                )
            }
            TaskSubmissionHistoryScreen(
                task = appState.parentTaskRows().firstOrNull { it.id == taskId },
                baseUrl = "https://demo.invalid",
                submissions = submissions,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ParentRedemptions) {
            RedemptionApprovalScreen(
                requests = appState.redemptionRequests.map(::toDemoRedemptionRequestUi),
                stats = RedemptionStatsUi(
                    last7DaysCount = appState.redemptionRequests.count { it.status != RedemptionStatus.REJECTED },
                    last7DaysCashCny = appState.redemptionRequests
                        .filter { it.status == RedemptionStatus.APPROVED }
                        .sumOf { it.cashAmountCny },
                    currentMonthCount = appState.redemptionRequests.size,
                    currentMonthCashCny = appState.redemptionRequests
                        .filter { it.status == RedemptionStatus.APPROVED }
                        .sumOf { it.cashAmountCny }
                ),
                onApprove = { appState.approveRedemption(it) },
                onReject = { appState.rejectRedemption(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun ServerAppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentLoginStore = remember { RecentLoginStore(context) }
    val sessionStore = remember { AuthSessionStore(context) }
    val offlineStore = remember { SharedPreferencesOfflineStateStore(context) }
    val pendingMediaStore = remember { FilePendingMediaStore(context) }
    val service = remember {
        SupabaseService(
            baseUrl = BuildConfig.SUPABASE_URL,
            apiKey = BuildConfig.SUPABASE_API_KEY,
            sessionStore = sessionStore
        )
    }
    val appState = remember {
        ServerAppState(
            gateway = service,
            offlineStore = offlineStore,
            pendingMediaStore = pendingMediaStore
        )
    }

    LaunchedEffect(Unit) {
        appState.initializeFromCache()
        appState.restoreSession()
    }

    LaunchedEffect(appState.currentRole) {
        while (true) {
            delay(30000)
            if (appState.currentRole != null) {
                appState.restoreSession()
            }
        }
    }

    LaunchedEffect(appState.currentRole) {
        when (appState.currentRole) {
            ServerRole.CHILD -> navController.navigate(Routes.ChildHome) {
                popUpTo(Routes.Login) { inclusive = true }
            }

            ServerRole.PARENT -> {
                if (appState.parentFocus == ParentDashboardFocus.PENDING) {
                    appState.selectParentFocus(ParentDashboardFocus.TODAY)
                }
                navController.navigate(Routes.ParentTasks) {
                    popUpTo(Routes.Login) { inclusive = true }
                }
            }

            null -> Unit
        }
    }

    NavHost(navController = navController, startDestination = Routes.Login) {
        composable(Routes.Login) {
            LoginScreen(
                statusMessage = appState.flashMessage,
                isBusy = appState.isBusy,
                recentEmail = recentLoginStore.recentEmail(),
                rememberedPassword = recentLoginStore.rememberedPassword(),
                rememberedPasswordEnabled = recentLoginStore.shouldRememberPassword(),
                onLogin = { email, password, rememberPassword ->
                    scope.launch {
                        val role = appState.login(email, password)
                        if (role != null) {
                            recentLoginStore.saveLoginPreference(email, password, rememberPassword)
                        }
                    }
                },
                onForgotPassword = { email -> scope.launch { appState.requestPasswordReset(email) } },
                onOpenSignUp = { navController.navigate(Routes.ParentSignUp) }
            )
        }
        composable(Routes.ParentSignUp) {
            ParentSignupScreen(
                statusMessage = appState.flashMessage,
                isBusy = appState.isBusy,
                onBack = { navController.popBackStack() },
                onSubmit = { familyName, parentName, parentEmail, parentPassword, childName, childEmail, childPassword ->
                    scope.launch {
                        val role = appState.signUpParent(
                            familyName = familyName,
                            parentName = parentName,
                            parentEmail = parentEmail,
                            parentPassword = parentPassword,
                            firstChildName = childName,
                            firstChildEmail = childEmail,
                            firstChildPassword = childPassword
                        )
                        if (role != null) {
                            recentLoginStore.saveRecentEmail(parentEmail)
                        }
                    }
                }
            )
        }
        composable(Routes.ChildHome) {
            ChildShellScaffold(
                childName = appState.childName,
                summary = appState.childSummary,
                todayTasks = appState.tasks,
                statusMessage = appState.flashMessage,
                selectedTaskId = appState.selectedChildTaskId,
                selectedTaskInsight = appState.selectedChildTaskInsight,
                isTaskInsightLoading = appState.isChildTaskInsightLoading,
                onTaskClick = { taskId -> scope.launch { appState.toggleChildTaskInsight(taskId) } },
                pointsBalance = appState.currentPoints,
                cashPerTenPoints = appState.cashPerTenPoints,
                onOpenPoints = { navController.navigate(Routes.ChildPoints) },
                onSwitchAccount = {
                    appState.switchAccount()
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenDelivery = { taskId -> navController.navigate("${Routes.ChildDelivery}/$taskId") },
                onCompleteTask = { taskId -> scope.launch { appState.completeTaskFast(taskId) } },
                onStartTask = { taskId -> scope.launch { appState.startTaskFast(taskId) } },
                onFinishTask = { taskId -> scope.launch { appState.finishTaskFast(taskId) } }
            )
        }
        composable(Routes.ChildPoints) {
            PointsScreen(
                balance = appState.currentPoints,
                cashPerTenPoints = appState.cashPerTenPoints,
                minRedeemPoints = appState.minRedeemPoints,
                statusMessage = appState.flashMessage,
                ledger = appState.ledger,
                onRedeem = { points -> scope.launch { appState.submitRedemption(points) } },
                onBack = { navController.popBackStack() }
            )
        }
        composable("${Routes.ChildDelivery}/{taskId}") { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty()
            val task = appState.tasks.firstOrNull { it.id == taskId }
            TaskDeliveryScreen(
                taskTitle = task?.title ?: "任务交付",
                requirement = task?.deliveryRequirement ?: DeliveryRequirement.NONE,
                statusMessage = appState.flashMessage,
                isBusy = appState.isBusy,
                onSubmit = { payload ->
                    scope.launch {
                        appState.submitTaskDelivery(
                            taskId = taskId,
                            submissionType = payload.submissionType,
                            fileName = payload.fileName,
                            contentType = payload.contentType,
                            bytes = payload.bytes
                        )
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ParentTasks) {
            ParentShellScaffold(
                familyName = appState.familyName,
                parentEmail = appState.parentEmail,
                children = appState.parentChildren,
                selectedChildId = appState.selectedChildId,
                overview = appState.parentOverview,
                focus = appState.parentFocus,
                filter = appState.parentFilter,
                taskRows = appState.parentTaskRows,
                statusMessage = appState.flashMessage,
                redemptionStats = appState.parentStats,
                onSelectChild = { appState.selectChild(it) },
                onSelectFocus = { appState.selectParentFocus(it) },
                onSelectFilter = { appState.selectParentFilter(it) },
                onOpenTaskSubmissions = { taskId -> navController.navigate("${Routes.ParentSubmissions}/$taskId") },
                onOpenAddTaskEditor = { navController.navigate(Routes.ParentTaskCreate) },
                onAddTask = { title, mode, deliveryRequirement, points, duration, scheduledTime ->
                    scope.launch {
                        val childMemberId = appState.selectedChildId
                            ?: appState.parentChildren.firstOrNull()?.memberId
                            ?: return@launch
                        appState.addTask(childMemberId, title, mode, deliveryRequirement, points, duration, scheduledTime)
                    }
                },
                onUpdateTask = { taskTemplateId, title, mode, deliveryRequirement, points, duration, scheduledTime, occurrenceId ->
                    scope.launch {
                        appState.updateTask(taskTemplateId, title, mode, deliveryRequirement, points, duration, scheduledTime)
                    }
                },
                onDeleteTask = { taskTemplateId, occurrenceId ->
                    scope.launch { appState.deleteTask(occurrenceId) }
                },
                onOpenFamilyManage = { navController.navigate(Routes.ParentFamily) },
                onOpenRedemptions = { navController.navigate(Routes.ParentRedemptions) },
                onSwitchAccount = {
                    appState.switchAccount()
                    navController.navigate(Routes.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onRefreshParentData = { appState.refreshParentDataSilently() },
                onResetDay = { scope.launch { appState.resetDay() } }
            )
        }
        composable(Routes.ParentTaskCreate) {
            ParentTaskEditorScreen(
                familyName = appState.familyName,
                children = appState.parentChildren,
                selectedChildId = appState.selectedChildId,
                statusMessage = appState.flashMessage,
                isBusy = appState.isBusy,
                onBack = { navController.popBackStack() },
                onSubmit = { childMemberId, title, mode, deliveryRequirement, points, duration, scheduledTime ->
                    scope.launch {
                        appState.addTask(childMemberId, title, mode, deliveryRequirement, points, duration, scheduledTime)
                        navController.popBackStack()
                    }
                }
            )
        }
        composable(Routes.ParentFamily) {
            ParentFamilyScreen(
                familyName = appState.familyName,
                parentEmail = appState.parentEmail,
                cashPerTenPoints = appState.cashPerTenPoints,
                minRedeemPoints = appState.minRedeemPoints,
                mediaRetentionDays = appState.mediaRetentionDays,
                childAccounts = appState.childAccounts,
                notificationChannelType = appState.notificationChannelType,
                notificationWebhookUrl = appState.notificationWebhookUrl,
                notificationEnabled = appState.notificationEnabled,
                statusMessage = appState.flashMessage,
                isBusy = appState.isBusy,
                onBack = { navController.popBackStack() },
                onSaveSettings = { familyName, cash, minPoints, retentionDays ->
                    scope.launch {
                        appState.updateFamilySettings(familyName, cash, minPoints, retentionDays)
                    }
                },
                onUpdateChildProfile = { memberId, childName ->
                    scope.launch { appState.updateChildProfile(memberId, childName) }
                },
                onCreateChild = { childName, childEmail, childPassword ->
                    scope.launch {
                        appState.createChildAccount(childName, childEmail, childPassword)
                    }
                },
                onResetChildPassword = { memberId, newPassword ->
                    scope.launch { appState.resetChildPassword(memberId, newPassword) }
                },
                onDeleteChild = { memberId ->
                    scope.launch { appState.deleteChildAccount(memberId) }
                },
                onSaveNotification = { channelType, webhookUrl, enabled ->
                    scope.launch { appState.saveNotificationSettings(channelType, webhookUrl, enabled) }
                }
            )
        }
        composable("${Routes.ParentSubmissions}/{taskId}") { entry ->
            val taskId = entry.arguments?.getString("taskId").orEmpty()
            TaskSubmissionHistoryScreen(
                task = appState.parentTaskRows.firstOrNull { it.id == taskId },
                baseUrl = BuildConfig.SUPABASE_URL,
                submissions = appState.submissionsForTask(taskId),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ParentRedemptions) {
            RedemptionApprovalScreen(
                requests = appState.redemptionRequests,
                stats = appState.parentStats,
                onApprove = { requestId -> scope.launch { appState.approveRedemption(requestId) } },
                onReject = { requestId -> scope.launch { appState.rejectRedemption(requestId) } },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun ChildShellScaffold(
    childName: String,
    summary: com.familycheckin.child.ChildProgressSummaryUi,
    todayTasks: List<com.familycheckin.demo.DemoTask>,
    statusMessage: String,
    selectedTaskId: String?,
    selectedTaskInsight: com.familycheckin.child.ChildTaskInsightUi?,
    isTaskInsightLoading: Boolean,
    onTaskClick: (String) -> Unit,
    pointsBalance: Int,
    cashPerTenPoints: Int,
    onOpenPoints: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenDelivery: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    onFinishTask: (String) -> Unit
) {
    var currentTab by remember { mutableStateOf(ShellTab.HOME) }

    Scaffold(
        containerColor = FamilyPalette.Canvas,
        bottomBar = {
            AppBottomBar(
                currentTab = currentTab,
                tabs = listOf(ShellTab.HOME, ShellTab.PROFILE),
                onSelect = { currentTab = it }
            )
        }
    ) { padding ->
        when (currentTab) {
            ShellTab.HOME -> ChildHomeScreen(
                childName = childName,
                summary = summary,
                todayTasks = todayTasks,
                statusMessage = statusMessage,
                selectedTaskId = selectedTaskId,
                selectedTaskInsight = selectedTaskInsight,
                isTaskInsightLoading = isTaskInsightLoading,
                onTaskClick = onTaskClick,
                onOpenDelivery = onOpenDelivery,
                onCompleteTask = onCompleteTask,
                onStartTask = onStartTask,
                onFinishTask = onFinishTask,
                modifier = Modifier.padding(padding),
                title = "今日任务"
            )

            ShellTab.TASKS -> ChildHomeScreen(
                childName = childName,
                summary = summary,
                todayTasks = todayTasks,
                statusMessage = statusMessage,
                selectedTaskId = selectedTaskId,
                selectedTaskInsight = selectedTaskInsight,
                isTaskInsightLoading = isTaskInsightLoading,
                onTaskClick = onTaskClick,
                onOpenDelivery = onOpenDelivery,
                onCompleteTask = onCompleteTask,
                onStartTask = onStartTask,
                onFinishTask = onFinishTask,
                modifier = Modifier.padding(padding),
                title = "任务",
                subtitle = "${summary.todayCompleted} / ${summary.todayTotal} 已完成"
            )

            ShellTab.PROFILE -> ChildProfileScreen(
                childName = childName,
                pointsBalance = pointsBalance,
                cashPerTenPoints = cashPerTenPoints,
                onOpenPoints = onOpenPoints,
                onSwitchAccount = onSwitchAccount,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ParentShellScaffold(
    familyName: String,
    parentEmail: String,
    children: List<ParentChildSummaryUi>,
    selectedChildId: String?,
    overview: com.familycheckin.parent.ParentOverviewUi,
    focus: com.familycheckin.parent.ParentDashboardFocus,
    filter: com.familycheckin.parent.ParentTaskFilter,
    taskRows: List<com.familycheckin.parent.ParentTaskRowUi>,
    statusMessage: String,
    redemptionStats: com.familycheckin.parent.RedemptionStatsUi,
    onSelectChild: (String) -> Unit,
    onSelectFocus: (com.familycheckin.parent.ParentDashboardFocus) -> Unit,
    onSelectFilter: (com.familycheckin.parent.ParentTaskFilter) -> Unit,
    onOpenTaskSubmissions: (String) -> Unit,
    onOpenAddTaskEditor: () -> Unit = {},
    onAddTask: (String, com.familycheckin.demo.TaskMode, com.familycheckin.demo.DeliveryRequirement, Int, Int?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateTask: (String, String, com.familycheckin.demo.TaskMode, com.familycheckin.demo.DeliveryRequirement, Int, Int?, String?, String) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onOpenFamilyManage: () -> Unit,
    onOpenRedemptions: () -> Unit,
    onSwitchAccount: () -> Unit,
    onRefreshParentData: suspend () -> Unit = {},
    onResetDay: () -> Unit
) {
    var currentTab by remember { mutableStateOf(ShellTab.HOME) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            onRefreshParentData()
        }
    }

    Scaffold(
        containerColor = FamilyPalette.Canvas,
        bottomBar = {
            AppBottomBar(
                currentTab = currentTab,
                onSelect = { currentTab = it }
            )
        }
    ) { padding ->
        when (currentTab) {
            ShellTab.HOME -> ParentDashboardScreen(
                familyName = familyName,
                overview = overview,
                children = children,
                selectedChildId = selectedChildId,
                redemptionStats = redemptionStats,
                statusMessage = statusMessage,
                onSelectChild = onSelectChild,
                onOpenTasks = { selectedFocus, selectedFilter ->
                    onSelectFocus(selectedFocus)
                    onSelectFilter(selectedFilter)
                    currentTab = ShellTab.TASKS
                },
                onOpenFamilyManage = onOpenFamilyManage,
                onOpenRedemptions = onOpenRedemptions,
                modifier = Modifier.padding(padding)
            )

            ShellTab.TASKS -> ParentTaskScreen(
                children = children,
                selectedChildId = selectedChildId,
                overview = overview,
                focus = focus,
                filter = filter,
                taskRows = taskRows,
                statusMessage = statusMessage,
                onSelectChild = onSelectChild,
                onSelectFocus = onSelectFocus,
                onSelectFilter = onSelectFilter,
                onOpenTaskSubmissions = onOpenTaskSubmissions,
                onOpenAddTaskEditor = onOpenAddTaskEditor,
                onAddTask = onAddTask,
                onUpdateTask = onUpdateTask,
                onDeleteTask = onDeleteTask,
                onResetDay = onResetDay,
                modifier = Modifier.padding(padding)
            )

            ShellTab.PROFILE -> ParentProfileScreen(
                familyName = familyName,
                parentEmail = parentEmail,
                childCount = children.size,
                redemptionStats = redemptionStats,
                onOpenFamilyManage = onOpenFamilyManage,
                onOpenRedemptions = onOpenRedemptions,
                onSwitchAccount = onSwitchAccount,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun AppBottomBar(
    currentTab: ShellTab,
    tabs: List<ShellTab> = listOf(ShellTab.HOME, ShellTab.TASKS, ShellTab.PROFILE),
    onSelect: (ShellTab) -> Unit
) {
    NavigationBar(
        containerColor = FamilyPalette.Surface,
        tonalElevation = 0.dp
    ) {
        tabs.map { tab ->
            val icon = when (tab) {
                ShellTab.HOME -> Icons.Outlined.Home
                ShellTab.TASKS -> Icons.AutoMirrored.Outlined.ListAlt
                ShellTab.PROFILE -> Icons.Outlined.Person
            }
            tab to icon
        }.forEach { (tab, icon) ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FamilyPalette.Accent,
                    selectedTextColor = FamilyPalette.Accent,
                    indicatorColor = FamilyPalette.SurfaceAccent,
                    unselectedIconColor = FamilyPalette.InkSoft,
                    unselectedTextColor = FamilyPalette.InkSoft
                )
            )
        }
    }
}

private fun filterDemoParentRows(
    rows: List<ParentTaskRowUi>,
    selectedChildId: String?,
    children: List<ParentChildSummaryUi>,
    focus: ParentDashboardFocus,
    filter: ParentTaskFilter
): List<ParentTaskRowUi> {
    return rows.filter { row ->
        val childMatch = selectedChildId == null || row.childMemberId == null || row.childMemberId == selectedChildId
        val focusMatch = when (focus) {
            ParentDashboardFocus.TODAY -> true
            ParentDashboardFocus.PENDING -> row.status != ParentTaskFilter.COMPLETED
            ParentDashboardFocus.REVIEW -> row.note.contains("Uploaded")
        }
        val filterMatch = when (filter) {
            ParentTaskFilter.ALL -> true
            ParentTaskFilter.PENDING -> row.status == ParentTaskFilter.PENDING
            ParentTaskFilter.RUNNING -> row.status == ParentTaskFilter.RUNNING
            ParentTaskFilter.COMPLETED -> row.status == ParentTaskFilter.COMPLETED
            ParentTaskFilter.REVIEW -> row.note.contains("Uploaded")
        }
        childMatch && focusMatch && filterMatch
    }
}

private fun toDemoRedemptionRequestUi(request: DemoRedemptionRequest): RedemptionRequestUi {
    val status = when (request.status) {
        RedemptionStatus.PENDING -> RedemptionRequestStatusUi.PENDING
        RedemptionStatus.APPROVED -> RedemptionRequestStatusUi.APPROVED
        RedemptionStatus.REJECTED -> RedemptionRequestStatusUi.REJECTED
    }
    val statusText = when (status) {
        RedemptionRequestStatusUi.PENDING -> "待审核"
        RedemptionRequestStatusUi.APPROVED -> "已通过"
        RedemptionRequestStatusUi.REJECTED -> "已驳回"
    }
    return RedemptionRequestUi(
        id = request.id,
        childName = request.childName,
        pointsRequested = request.pointsRequested,
        cashAmountCny = request.cashAmountCny,
        requestedAtLabel = "${request.requestedAtLabel} · $statusText",
        status = status
    )
}
