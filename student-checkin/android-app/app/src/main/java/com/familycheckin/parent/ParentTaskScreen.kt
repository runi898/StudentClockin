package com.familycheckin.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode

@Composable
fun ParentTaskScreen(
    tasks: List<DemoTask>,
    statusMessage: String,
    onAddTask: (String, TaskMode, DeliveryRequirement, Int, Int?, String?) -> Unit,
    onOpenRedemptions: () -> Unit,
    onResetDay: () -> Unit
) {
    var taskName by remember { mutableStateOf("新增任务") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("家长任务管理", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onOpenRedemptions) {
                Text("兑换审核")
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

        OutlinedTextField(
            value = taskName,
            onValueChange = { taskName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("任务名称") },
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onAddTask(taskName, TaskMode.CHECK_ONLY, DeliveryRequirement.NONE, 1, null, "20:00")
            }) {
                Text("加直接打卡")
            }
            Button(onClick = {
                onAddTask(taskName, TaskMode.COUNTDOWN, DeliveryRequirement.PHOTO, 2, 20, "19:00")
            }) {
                Text("加倒计时")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onAddTask(taskName, TaskMode.STOPWATCH, DeliveryRequirement.VIDEO, 3, null, "18:00")
            }) {
                Text("加手动计时")
            }
            Button(onClick = onResetDay) {
                Text("重置今日任务")
            }
        }

        Text("当前任务列表", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${task.points} 积分 · ${modeLabel(task.mode)} · ${deliveryLabel(task.deliveryRequirement)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            buildString {
                                append("状态 ${task.status}")
                                task.scheduledTimeLabel?.let { append(" · 提醒 $it") }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
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
        DeliveryRequirement.NONE -> "无要求"
        DeliveryRequirement.PHOTO -> "照片"
        DeliveryRequirement.VIDEO -> "视频"
        DeliveryRequirement.AUDIO -> "音频"
    }
}
