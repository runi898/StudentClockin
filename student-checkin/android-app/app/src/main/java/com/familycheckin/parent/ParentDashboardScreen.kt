package com.familycheckin.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.FilterPill
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.PrimaryPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ParentDashboardScreen(
    familyName: String,
    overview: ParentOverviewUi,
    children: List<ParentChildSummaryUi>,
    selectedChildId: String?,
    redemptionStats: RedemptionStatsUi,
    statusMessage: String,
    onSelectChild: (String) -> Unit,
    onOpenTasks: (ParentDashboardFocus, ParentTaskFilter) -> Unit,
    onOpenFamilyManage: () -> Unit,
    onOpenRedemptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedChild = children.firstOrNull { it.memberId == selectedChildId } ?: children.firstOrNull()

    ScreenBackdrop(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppHeader(
                    title = familyName.ifBlank { "家庭总览" },
                    subtitle = "先看今天的整体进度，再一键切到任务详情处理待完成、进行中和待查看。"
                )
            }

            item {
                StatusBanner(message = statusMessage)
            }

            item {
                SectionSurface {
                    Text("今天整体", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "今日完成",
                            value = overview.todaySummaryText,
                            note = "点击查看全部任务",
                            modifier = Modifier.weight(1f),
                            emphasized = true,
                            onClick = { onOpenTasks(ParentDashboardFocus.TODAY, ParentTaskFilter.ALL) }
                        )
                        MetricTile(
                            title = "待完成",
                            value = overview.pendingCount.toString(),
                            note = "点击筛选未完成",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTasks(ParentDashboardFocus.PENDING, ParentTaskFilter.PENDING) }
                        )
                        MetricTile(
                            title = "待查看",
                            value = overview.reviewCount.toString(),
                            note = "点击查看交付",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenTasks(ParentDashboardFocus.REVIEW, ParentTaskFilter.REVIEW) }
                        )
                    }
                }
            }

            if (children.isNotEmpty()) {
                item {
                    SectionSurface {
                        Text("孩子切换", style = MaterialTheme.typography.titleLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(children, key = { it.memberId }) { child ->
                                FilterPill(
                                    text = child.childName,
                                    selected = child.memberId == selectedChildId,
                                    onClick = { onSelectChild(child.memberId) }
                                )
                            }
                        }
                    }
                }
            }

            selectedChild?.let { child ->
                item {
                    SectionSurface {
                        Text("${child.childName} 的今日状态", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                title = "已完成",
                                value = "${child.todayCompleted}/${child.todayTotal}",
                                note = "今日全部任务",
                                modifier = Modifier.weight(1f),
                                emphasized = true,
                                onClick = { onOpenTasks(ParentDashboardFocus.TODAY, ParentTaskFilter.COMPLETED) }
                            )
                            MetricTile(
                                title = "未完成",
                                value = child.pendingCount.toString(),
                                note = "点击查看待做",
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenTasks(ParentDashboardFocus.PENDING, ParentTaskFilter.PENDING) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                title = "执行中",
                                value = child.runningCount.toString(),
                                note = "点击查看计时中",
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenTasks(ParentDashboardFocus.PENDING, ParentTaskFilter.RUNNING) }
                            )
                            MetricTile(
                                title = "待查看",
                                value = child.pendingReviewCount.toString(),
                                note = "点击查看交付",
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenTasks(ParentDashboardFocus.REVIEW, ParentTaskFilter.REVIEW) }
                            )
                        }
                        Text(
                            "最近7天趋势：${child.trendText.normalizeTrendText()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                        PrimaryPill(
                            text = "查看 ${child.childName} 的任务详情",
                            onClick = { onOpenTasks(ParentDashboardFocus.TODAY, ParentTaskFilter.ALL) }
                        )
                    }
                }
            }

            item {
                SectionSurface {
                    Text("兑换统计", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "最近7天",
                            value = "${redemptionStats.last7DaysCount} 次",
                            note = "¥${"%.2f".format(redemptionStats.last7DaysCashCny)}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "本月",
                            value = "${redemptionStats.currentMonthCount} 次",
                            note = "¥${"%.2f".format(redemptionStats.currentMonthCashCny)}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryPill(text = "兑换审核", onClick = onOpenRedemptions)
                        PrimaryPill(text = "家庭设置", onClick = onOpenFamilyManage)
                    }
                }
            }
        }
    }
}

private fun String.normalizeTrendText(): String {
    return trim()
        .removePrefix("最近7天 ")
        .removePrefix("Last 7 days ")
        .trim()
}
