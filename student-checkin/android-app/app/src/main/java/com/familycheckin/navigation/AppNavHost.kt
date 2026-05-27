package com.familycheckin.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familycheckin.auth.LoginScreen
import com.familycheckin.parent.RedemptionApprovalScreen
import com.familycheckin.parent.RedemptionRequestUi
import com.familycheckin.parent.RedemptionStatsUi
import com.familycheckin.points.LedgerEntryUi
import com.familycheckin.points.PointsScreen

private object Routes {
    const val Login = "login"
    const val ChildPoints = "child_points"
    const val ParentRedemptions = "parent_redemptions"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Login
    ) {
        composable(Routes.Login) {
            LoginScreen(
                onLoginParent = { navController.navigate(Routes.ParentRedemptions) },
                onLoginChild = { navController.navigate(Routes.ChildPoints) },
                onForgotPassword = {}
            )
        }
        composable(Routes.ChildPoints) {
            PointsScreen(
                balance = 21,
                cashPerTenPoints = 1,
                ledger = listOf(
                    LedgerEntryUi("今天 08:12", "晨读 20 分钟", +1, 21),
                    LedgerEntryUi("昨天 19:35", "整理书包", +2, 20),
                    LedgerEntryUi("昨天 18:00", "兑换玩具贴纸", -10, 18)
                ),
                onRedeem = {}
            )
        }
        composable(Routes.ParentRedemptions) {
            RedemptionApprovalScreen(
                requests = listOf(
                    RedemptionRequestUi("req-1", "小宇", 10, 1.0, "今天 17:30"),
                    RedemptionRequestUi("req-2", "小星", 20, 2.0, "今天 18:00")
                ),
                stats = RedemptionStatsUi(
                    last7DaysCount = 3,
                    last7DaysCashCny = 4.0,
                    currentMonthCount = 6,
                    currentMonthCashCny = 8.0
                ),
                onApprove = {},
                onReject = {}
            )
        }
    }
}
