package com.familycheckin.child

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.StatusBanner
import java.time.Duration
import java.time.Instant

@Composable
fun ChildTaskBoardScreen(
    childName: String,
    summary: ChildProgressSummaryUi,
    todayTasks: List<DemoTask>,
    statusMessage: String,
    onOpenDelivery: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    onFinishTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenBackdrop(
        modifier = modifier.statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                HomeTaskHeader(
                    childName = childName,
                    summary = summary,
                    totalTasks = todayTasks.size
                )
            }

            if (statusMessage.isNotBlank() && !statusMessage.startsWith("欢迎")) {
                item {
                    StatusBanner(message = statusMessage)
                }
            }

            items(todayTasks, key = { it.id }) { task ->
                HomeTaskCard(
                    task = task,
                    onOpenDelivery = onOpenDelivery,
                    onCompleteTask = onCompleteTask,
                    onStartTask = onStartTask,
                    onFinishTask = onFinishTask
                )
            }
        }
    }
}

@Composable
private fun HomeTaskHeader(
    childName: String,
    summary: ChildProgressSummaryUi,
    totalTasks: Int
) {
    Surface(
        color = Color.White.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, FamilyPalette.Line.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppHeader(
                title = "今日任务",
                subtitle = "$childName · 共 $totalTasks 项任务"
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HomeHeaderBadge(
                    label = "已完成",
                    value = "${summary.todayCompleted}/${summary.todayTotal}",
                    modifier = Modifier.weight(1f)
                )
                HomeHeaderBadge(
                    label = "待完成",
                    value = summary.pendingCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                HomeHeaderBadge(
                    label = "完成率",
                    value = summary.completionRateText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = FamilyPalette.Surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FamilyPalette.InkSoft
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = FamilyPalette.Ink
            )
        }
    }
}

@Composable
private fun HomeTaskCard(
    task: DemoTask,
    onOpenDelivery: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    onFinishTask: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = FamilyPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = homeTaskMeta(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = FamilyPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = homeTaskTimeText(task),
                    style = MaterialTheme.typography.labelSmall,
                    color = FamilyPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeTaskStatusTag(task.status)
                Spacer(modifier = Modifier.weight(1f, fill = true))
                Button(
                    onClick = {
                        when (task.status) {
                            TaskStatus.PENDING -> {
                                when {
                                    task.deliveryRequirement != DeliveryRequirement.NONE && task.mode == TaskMode.CHECK_ONLY -> onOpenDelivery(task.id)
                                    task.mode == TaskMode.CHECK_ONLY -> onCompleteTask(task.id)
                                    else -> onStartTask(task.id)
                                }
                            }

                            TaskStatus.RUNNING -> {
                                if (task.deliveryRequirement != DeliveryRequirement.NONE) {
                                    onOpenDelivery(task.id)
                                } else {
                                    onFinishTask(task.id)
                                }
                            }

                            TaskStatus.COMPLETED -> Unit
                        }
                    },
                    modifier = Modifier
                        .width(112.dp)
                        .height(34.dp),
                    enabled = task.status != TaskStatus.COMPLETED,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (task.status == TaskStatus.RUNNING) FamilyPalette.Accent else FamilyPalette.AccentStrong,
                        contentColor = Color.White,
                        disabledContainerColor = FamilyPalette.SurfaceMuted,
                        disabledContentColor = FamilyPalette.InkSoft
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = homeActionLabel(task),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTaskStatusTag(status: TaskStatus) {
    val (label, background) = when (status) {
        TaskStatus.PENDING -> "未完成" to FamilyPalette.SurfaceMuted
        TaskStatus.RUNNING -> "执行中" to FamilyPalette.AccentWarm
        TaskStatus.COMPLETED -> "已完成" to FamilyPalette.Success
    }

    Surface(
        color = background,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = FamilyPalette.Ink
        )
    }
}

private fun homeActionLabel(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> when {
            task.deliveryRequirement != DeliveryRequirement.NONE && task.mode == TaskMode.CHECK_ONLY -> "上传打卡"
            task.mode == TaskMode.CHECK_ONLY -> "完成打卡"
            else -> "开始任务"
        }

        TaskStatus.RUNNING -> if (task.deliveryRequirement != DeliveryRequirement.NONE) "上传完成" else "结束任务"
        TaskStatus.COMPLETED -> "已完成"
    }
}

private fun homeTaskMeta(task: DemoTask): String {
    return buildString {
        append(
            when (task.mode) {
                TaskMode.CHECK_ONLY -> "直接打卡"
                TaskMode.COUNTDOWN -> "倒计时"
                TaskMode.STOPWATCH -> "自主计时"
            }
        )
        append(" · ")
        append(
            when (task.deliveryRequirement) {
                DeliveryRequirement.NONE -> "无上传"
                DeliveryRequirement.PHOTO -> "照片"
                DeliveryRequirement.VIDEO -> "视频"
                DeliveryRequirement.AUDIO -> "音频"
            }
        )
        append(" · +")
        append(task.points)
        append("积分")
    }
}

private fun homeTaskTimeText(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> task.scheduledTimeLabel?.let { "计划 $it" } ?: "等待开始"
        TaskStatus.RUNNING -> {
            val elapsed = task.startedAt?.let { Duration.between(it, Instant.now()).seconds } ?: 0
            "进行中 ${elapsed.coerceAtLeast(0)} 秒"
        }

        TaskStatus.COMPLETED -> task.completedAtLabel?.let { "完成 $it" } ?: "今日已完成"
    }
}
