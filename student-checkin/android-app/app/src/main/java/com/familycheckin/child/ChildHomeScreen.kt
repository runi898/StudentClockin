package com.familycheckin.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus

@Composable
fun ChildHomeScreen(
    childName: String,
    todayTasks: List<DemoTask>,
    statusMessage: String,
    onOpenPoints: () -> Unit,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    onFinishTask: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("$childName 的今日任务", style = MaterialTheme.typography.headlineSmall)
                Text("轻量排布，尽量一屏看完 12 个任务", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpenPoints) {
                Text("积分")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp
        ) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(todayTasks, key = { it.id }) { task ->
                Surface(tonalElevation = 1.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${modeLabel(task.mode)} / ${deliveryLabel(task.deliveryRequirement)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            buildString {
                                append("${task.points} 积分")
                                task.scheduledTimeLabel?.let { append(" · $it") }
                                task.targetMinutes?.let { append(" · ${it}分钟") }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(statusLabel(task), style = MaterialTheme.typography.bodySmall)

                        when (task.status) {
                            TaskStatus.PENDING -> {
                                Button(
                                    onClick = {
                                        if (task.mode == TaskMode.CHECK_ONLY) onCompleteTask(task.id)
                                        else onStartTask(task.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (task.mode == TaskMode.CHECK_ONLY) "完成打卡" else "开始")
                                }
                            }

                            TaskStatus.RUNNING -> {
                                Button(
                                    onClick = { onFinishTask(task.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("结束并打卡")
                                }
                            }

                            TaskStatus.COMPLETED -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("已完成")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun modeLabel(mode: TaskMode): String {
    return when (mode) {
        TaskMode.CHECK_ONLY -> "直接打卡"
        TaskMode.COUNTDOWN -> "倒计时"
        TaskMode.STOPWATCH -> "手动计时"
    }
}

private fun deliveryLabel(requirement: DeliveryRequirement): String {
    return when (requirement) {
        DeliveryRequirement.NONE -> "无交付"
        DeliveryRequirement.PHOTO -> "照片"
        DeliveryRequirement.VIDEO -> "视频"
        DeliveryRequirement.AUDIO -> "音频"
    }
}

private fun statusLabel(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> "待完成"
        TaskStatus.RUNNING -> "进行中"
        TaskStatus.COMPLETED -> {
            if (task.actualDurationSeconds > 0) {
                "已完成 · ${task.actualDurationSeconds / 60}分${task.actualDurationSeconds % 60}秒"
            } else {
                "已完成"
            }
        }
    }
}
