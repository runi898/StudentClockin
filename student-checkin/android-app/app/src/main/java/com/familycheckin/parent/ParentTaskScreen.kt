package com.familycheckin.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.TaskMode
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.FilterPill
import com.familycheckin.ui.GhostPill
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.PrimaryPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ParentTaskScreen(
    children: List<ParentChildSummaryUi>,
    selectedChildId: String?,
    overview: ParentOverviewUi,
    focus: ParentDashboardFocus,
    filter: ParentTaskFilter,
    taskRows: List<ParentTaskRowUi>,
    statusMessage: String,
    onSelectChild: (String) -> Unit,
    onSelectFocus: (ParentDashboardFocus) -> Unit,
    onSelectFilter: (ParentTaskFilter) -> Unit,
    onOpenTaskSubmissions: (String) -> Unit,
    onOpenAddTaskEditor: () -> Unit = {},
    onAddTask: (String, TaskMode, DeliveryRequirement, Int, Int?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateTask: (String, String, TaskMode, DeliveryRequirement, Int, Int?, String?, String) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onResetDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val visibleTaskRows = taskRows.filter { row ->
        selectedChildId == null || row.childMemberId == null || row.childMemberId == selectedChildId
    }

    var editingTemplateId by remember { mutableStateOf<String?>(null) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var taskName by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("1") }
    var targetMinutesText by remember { mutableStateOf("") }
    var scheduledTimeText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TaskMode.CHECK_ONLY) }
    var deliveryRequirement by remember { mutableStateOf(DeliveryRequirement.NONE) }
    var deleteTarget by remember { mutableStateOf<ParentTaskRowUi?>(null) }

    val selectedTask = visibleTaskRows.firstOrNull { it.id == selectedTaskId } ?: visibleTaskRows.firstOrNull()

    val clearEditor = {
        editingTemplateId = null
        taskName = ""
        pointsText = "1"
        targetMinutesText = ""
        scheduledTimeText = ""
        mode = TaskMode.CHECK_ONLY
        deliveryRequirement = DeliveryRequirement.NONE
    }

    val beginEditing: (ParentTaskRowUi) -> Unit = { task ->
        selectedTaskId = task.id
        editingTemplateId = task.taskTemplateId
        taskName = task.title
        pointsText = task.pointValue.toString()
        targetMinutesText = task.targetMinutes?.toString().orEmpty()
        scheduledTimeText = task.scheduledTimeLocal.orEmpty()
        mode = task.mode.toTaskMode()
        deliveryRequirement = task.deliveryRequirement.toDeliveryRequirement()
    }

    LaunchedEffect(visibleTaskRows) {
        if (visibleTaskRows.isNotEmpty() && visibleTaskRows.none { it.id == selectedTaskId }) {
            selectedTaskId = visibleTaskRows.first().id
        }
        if (visibleTaskRows.isEmpty()) {
            selectedTaskId = null
        }
        if (editingTemplateId != null && visibleTaskRows.none { it.taskTemplateId == editingTemplateId }) {
            clearEditor()
        }
    }

    LaunchedEffect(editingTemplateId) {
        if (editingTemplateId != null) {
            listState.animateScrollToItem(index = 5)
        }
    }

    val points = pointsText.toIntOrNull()
    val targetMinutes = targetMinutesText.toIntOrNull()
    val editReady = taskName.isNotBlank() && points != null && points > 0

    ScreenBackdrop(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppHeader(
                    title = "任务管理",
                    subtitle = "先筛选孩子和状态，再通过顶部按钮进入新增任务页。"
                )
            }

            item {
                StatusBanner(message = statusMessage)
            }

            item {
                SectionSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("孩子与任务", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "新增任务会进入独立配置页，保存后任务列表仍在当前页面查看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = FamilyPalette.InkSoft
                            )
                        }
                        PrimaryPill(text = "添加任务", onClick = onOpenAddTaskEditor)
                    }
                    if (children.isNotEmpty()) {
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

            item {
                SectionSurface {
                    Text("任务概览", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusMetric(
                            modifier = Modifier.weight(1f),
                            title = "今日",
                            value = overview.todaySummaryText,
                            note = "完成 / 总数",
                            selected = focus == ParentDashboardFocus.TODAY,
                            onClick = { onSelectFocus(ParentDashboardFocus.TODAY) }
                        )
                        FocusMetric(
                            modifier = Modifier.weight(1f),
                            title = "待完成",
                            value = overview.pendingCount.toString(),
                            note = "还没完成",
                            selected = focus == ParentDashboardFocus.PENDING,
                            onClick = { onSelectFocus(ParentDashboardFocus.PENDING) }
                        )
                        FocusMetric(
                            modifier = Modifier.weight(1f),
                            title = "待查看",
                            value = overview.reviewCount.toString(),
                            note = "有交付记录",
                            selected = focus == ParentDashboardFocus.REVIEW,
                            onClick = { onSelectFocus(ParentDashboardFocus.REVIEW) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("昨天", overview.yesterdayText.removePrefix("昨天 "), "上一天", Modifier.weight(1f))
                        MetricTile("最近7天", overview.last7DaysText.removePrefix("最近7天 "), "完成率", Modifier.weight(1f))
                        MetricTile("最近30天", overview.last30DaysText.removePrefix("最近30天 "), "长期趋势", Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionSurface {
                    Text(detailTitle(focus), style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ParentTaskFilter.entries.toList()) { current ->
                            FilterPill(
                                text = current.label,
                                selected = current == filter,
                                onClick = { onSelectFilter(current) }
                            )
                        }
                    }
                }
            }

            if (editingTemplateId != null) {
                item {
                    SectionSurface {
                        Text("编辑任务模板", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "修改后，会同步更新同模板的今日与后续任务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                        TaskNameField(
                            value = taskName,
                            onValueChange = { taskName = it },
                            label = "任务名称"
                        )
                        SettingsField(
                            value = pointsText,
                            onValueChange = { pointsText = it.filter(Char::isDigit) },
                            label = "奖励积分"
                        )
                        SettingsField(
                            value = scheduledTimeText,
                            onValueChange = { scheduledTimeText = it.take(5) },
                            label = "固定时间（选填，例如 20:00）"
                        )
                        SettingsField(
                            value = targetMinutesText,
                            onValueChange = { targetMinutesText = it.filter(Char::isDigit) },
                            label = "目标分钟数（计时任务选填）"
                        )
                        Text("打卡模式", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TaskMode.entries.forEach { item ->
                                FilterPill(
                                    text = item.toLabel(),
                                    selected = item == mode,
                                    onClick = { mode = item }
                                )
                            }
                        }
                        Text("交付要求", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DeliveryRequirement.entries.forEach { item ->
                                FilterPill(
                                    text = item.toLabel(),
                                    selected = item == deliveryRequirement,
                                    onClick = { deliveryRequirement = item }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = editReady,
                                onClick = {
                                    onUpdateTask(
                                        editingTemplateId.orEmpty(),
                                        taskName.trim(),
                                        mode,
                                        deliveryRequirement,
                                        points ?: 1,
                                        targetMinutes,
                                        scheduledTimeText.trim().ifBlank { null },
                                        selectedTask?.id.orEmpty()
                                    )
                                    clearEditor()
                                }
                            ) {
                                Text("保存修改")
                            }
                            GhostPill(
                                text = "取消编辑",
                                onClick = clearEditor
                            )
                        }
                    }
                }
            }

            if (visibleTaskRows.isEmpty()) {
                item {
                    SectionSurface {
                        Text("当前筛选下没有任务", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "可以切换孩子、切换状态筛选，或者点击上方“添加任务”创建新任务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                    }
                }
            } else {
                items(visibleTaskRows, key = { "${it.id}-${it.taskTemplateId}" }) { row ->
                    ParentTaskCompactRow(
                        task = row,
                        selected = row.id == selectedTask?.id,
                        onClick = { selectedTaskId = row.id },
                        onOpenTaskSubmissions = onOpenTaskSubmissions,
                        onEdit = { beginEditing(row) },
                        onDelete = {
                            selectedTaskId = row.id
                            deleteTarget = row
                        }
                    )
                }
            }

            item {
                SectionSurface {
                    Text("快捷操作", style = MaterialTheme.typography.titleLarge)
                    GhostPill(text = "重置今天任务状态", onClick = onResetDay)
                }
            }
        }
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除任务") },
            text = { Text("删除后，今天未完成和后续生成的“${task.title}”都会被移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTask(task.taskTemplateId, task.id)
                        deleteTarget = null
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FocusMetric(
    modifier: Modifier,
    title: String,
    value: String,
    note: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) FamilyPalette.SurfaceAccent else Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) FamilyPalette.Accent else FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = FamilyPalette.InkSoft)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(note, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
        }
    }
}

@Composable
private fun ParentTaskCompactRow(
    task: ParentTaskRowUi,
    selected: Boolean,
    onClick: () -> Unit,
    onOpenTaskSubmissions: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) FamilyPalette.SurfaceAccent else FamilyPalette.Surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, if (selected) FamilyPalette.Accent else FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${task.childName} · ${task.timeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
                StatusToken(task.statusLabel)
            }
            Text(
                "${task.deliveryLabel} · ${task.pointsLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = FamilyPalette.InkSoft
            )
            Text(
                task.note,
                style = MaterialTheme.typography.bodySmall,
                color = FamilyPalette.InkSoft,
                maxLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryPill(text = "查看交付", onClick = { onOpenTaskSubmissions(task.id) })
                GhostPill(text = "编辑", onClick = onEdit)
                GhostPill(text = "删除", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ParentTaskDetailPanel(
    task: ParentTaskRowUi,
    onOpenTaskSubmissions: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryPill(text = "查看交付", onClick = { onOpenTaskSubmissions(task.id) })
            GhostPill(text = "编辑任务", onClick = onEdit)
            GhostPill(text = "删除任务", onClick = onDelete)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${task.childName} · ${task.timeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                Text(
                    "${task.deliveryLabel} · ${task.pointsLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
            }
            StatusToken(task.statusLabel)
        }
        Text(task.note, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft, maxLines = 2)
    }
}

@Composable
private fun StatusToken(text: String) {
    val color = when (text) {
        "进行中" -> FamilyPalette.AccentWarm
        "已完成" -> FamilyPalette.Success
        else -> FamilyPalette.SurfaceMuted
    }
    Surface(color = color, shape = MaterialTheme.shapes.medium) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = FamilyPalette.Ink
        )
    }
}

@Composable
private fun TaskNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = FamilyPalette.Surface,
            unfocusedContainerColor = FamilyPalette.Surface,
            focusedBorderColor = FamilyPalette.Accent,
            unfocusedBorderColor = FamilyPalette.Line
        )
    )
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = FamilyPalette.Surface,
            unfocusedContainerColor = FamilyPalette.Surface,
            focusedBorderColor = FamilyPalette.Accent,
            unfocusedBorderColor = FamilyPalette.Line,
            focusedLabelColor = FamilyPalette.Accent,
            unfocusedLabelColor = FamilyPalette.InkSoft
        )
    )
}

private fun detailTitle(focus: ParentDashboardFocus): String {
    return when (focus) {
        ParentDashboardFocus.TODAY -> "今日全部任务"
        ParentDashboardFocus.PENDING -> "待完成任务详情"
        ParentDashboardFocus.REVIEW -> "待查看交付"
    }
}

internal fun TaskMode.toLabel(): String {
    return when (this) {
        TaskMode.CHECK_ONLY -> "完成即打卡"
        TaskMode.COUNTDOWN -> "固定时长"
        TaskMode.STOPWATCH -> "自主计时"
    }
}

internal fun DeliveryRequirement.toLabel(): String {
    return when (this) {
        DeliveryRequirement.NONE -> "无要求"
        DeliveryRequirement.PHOTO -> "照片"
        DeliveryRequirement.VIDEO -> "视频"
        DeliveryRequirement.AUDIO -> "音频"
    }
}

private fun String.toTaskMode(): TaskMode {
    return when (this) {
        "countdown" -> TaskMode.COUNTDOWN
        "stopwatch" -> TaskMode.STOPWATCH
        else -> TaskMode.CHECK_ONLY
    }
}

private fun String.toDeliveryRequirement(): DeliveryRequirement {
    return when (this) {
        "photo" -> DeliveryRequirement.PHOTO
        "video" -> DeliveryRequirement.VIDEO
        "audio" -> DeliveryRequirement.AUDIO
        else -> DeliveryRequirement.NONE
    }
}
