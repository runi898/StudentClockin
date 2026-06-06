package com.familycheckin.child

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskStatus
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.PrimaryPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ChildDashboardScreen(
    childName: String,
    summary: ChildProgressSummaryUi,
    tasks: List<DemoTask>,
    statusMessage: String,
    onOpenTasks: () -> Unit,
    onOpenPoints: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    title = "$childName，今天继续加油",
                    subtitle = "先看整体进度，再进入任务区开始或结束今天的任务。"
                )
            }

            item {
                SectionSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "今日进度",
                            value = "${summary.todayCompleted}/${summary.todayTotal}",
                            note = "已完成 / 总任务",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                        MetricTile(
                            title = "待完成",
                            value = summary.pendingCount.toString(),
                            note = "还没打卡",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "近 7 天",
                            value = summary.last7DaysText,
                            note = "趋势",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "近 30 天",
                            value = summary.last30DaysText,
                            note = "趋势",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                StatusBanner(message = statusMessage)
            }

            item {
                SectionSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("今日任务总览", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${tasks.count { it.status == TaskStatus.COMPLETED }} 已完成 / ${tasks.size} 总数",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                    }
                    TaskSnapshotGrid(tasks = tasks)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryPill(text = "进入任务页", onClick = onOpenTasks)
                        PrimaryPill(text = "查看积分", onClick = onOpenPoints)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSnapshotGrid(tasks: List<DemoTask>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { task ->
                    SnapshotCard(task = task, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(task: DemoTask, modifier: Modifier = Modifier) {
    val statusColor = when (task.status) {
        TaskStatus.PENDING -> FamilyPalette.SurfaceMuted
        TaskStatus.RUNNING -> FamilyPalette.AccentWarm
        TaskStatus.COMPLETED -> FamilyPalette.Success
    }
    val statusLabel = when (task.status) {
        TaskStatus.PENDING -> "待完成"
        TaskStatus.RUNNING -> "进行中"
        TaskStatus.COMPLETED -> "已完成"
    }

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, color = FamilyPalette.Ink)
            Text(task.scheduledTimeLabel ?: "今天安排", style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
            Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = FamilyPalette.Ink
                )
            }
        }
    }
}
