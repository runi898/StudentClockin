package com.familycheckin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familycheckin.auth.LoginScreen
import com.familycheckin.child.ChildHomeScreen
import com.familycheckin.demo.DemoAppState
import com.familycheckin.demo.DemoRedemptionRequest
import com.familycheckin.demo.RedemptionStatus
import com.familycheckin.parent.ParentTaskScreen
import com.familycheckin.parent.RedemptionApprovalScreen
import com.familycheckin.parent.RedemptionRequestUi
import com.familycheckin.parent.RedemptionStatsUi
import com.familycheckin.points.LedgerEntryUi
import com.familycheckin.points.PointsScreen

private object Routes {
    const val Login = "login"
    const val ChildHome = "child_home"
    const val ChildPoints = "child_points"
    const val ParentTasks = "parent_tasks"
    const val ParentRedemptions = "parent_redemptions"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val appState = remember { DemoAppState() }

    NavHost(
        navController = navController,
        startDestination = Routes.Login
    ) {
        composable(Routes.Login) {
            LoginScreen(
                onLoginParent = { navController.navigate(Routes.ParentTasks) },
                onLoginChild = { navController.navigate(Routes.ChildHome) },
                onForgotPassword = {
                    appState.flashMessage.value = "已模拟向 $it 发送家长密码重置邮件"
                }
            )
        }
        composable(Routes.ChildHome) {
            ChildHomeScreen(
                childName = appState.childName,
                todayTasks = appState.tasks,
                statusMessage = appState.flashMessage.value,
                onOpenPoints = { navController.navigate(Routes.ChildPoints) },
                onCompleteTask = { appState.completeTask(it) },
                onStartTask = { appState.startTask(it) },
                onFinishTask = { appState.finishTask(it) }
            )
        }
        composable(Routes.ChildPoints) {
            PointsScreen(
                balance = appState.currentPoints,
                cashPerTenPoints = appState.cashPerTenPoints.value,
                ledger = appState.ledger.map {
                    LedgerEntryUi(
                        timeLabel = it.timeLabel,
                        title = it.title,
                        delta = it.delta,
                        balanceAfter = it.balanceAfter
                    )
                },
                onRedeem = { appState.submitRedemption(it) }
            )
        }
        composable(Routes.ParentTasks) {
            ParentTaskScreen(
                tasks = appState.tasks,
                statusMessage = appState.flashMessage.value,
                onAddTask = { title, mode, deliveryRequirement, points, duration, scheduledTime ->
                    appState.addTask(title, mode, deliveryRequirement, points, duration, scheduledTime)
                },
                onOpenRedemptions = { navController.navigate(Routes.ParentRedemptions) },
                onResetDay = { appState.resetDay() }
            )
        }
        composable(Routes.ParentRedemptions) {
            RedemptionApprovalScreen(
                requests = appState.redemptionRequests.map(::toRedemptionRequestUi),
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
                onReject = { appState.rejectRedemption(it) }
            )
        }
    }
}

private fun toRedemptionRequestUi(request: DemoRedemptionRequest): RedemptionRequestUi {
    val statusText = when (request.status) {
        RedemptionStatus.PENDING -> "待审核"
        RedemptionStatus.APPROVED -> "已通过"
        RedemptionStatus.REJECTED -> "已拒绝"
    }

    return RedemptionRequestUi(
        id = request.id,
        childName = request.childName,
        pointsRequested = request.pointsRequested,
        cashAmountCny = request.cashAmountCny,
        requestedAtLabel = "${request.requestedAtLabel} · $statusText"
    )
}
