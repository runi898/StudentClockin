package com.familycheckin.child

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.DemoTask
import com.familycheckin.demo.TaskMode
import com.familycheckin.demo.TaskStatus
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.FilterPill
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.StatusBanner
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ChildTaskFilter(val label: String) {
    ALL("全部任务"),
    COMPLETED("已完成"),
    PENDING("未完成"),
    RUNNING("执行中")
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun ChildHomeScreen(
    childName: String,
    summary: ChildProgressSummaryUi,
    todayTasks: List<DemoTask>,
    statusMessage: String,
    selectedTaskId: String? = null,
    selectedTaskInsight: ChildTaskInsightUi? = null,
    isTaskInsightLoading: Boolean = false,
    onTaskClick: (String) -> Unit = {},
    onOpenDelivery: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    onFinishTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "今日任务",
    subtitle: String = ""
) {
    val context = LocalContext.current
    val orderStore = remember(childName) { ChildTaskOrderStore(context, childName) }
    var filter by remember { mutableStateOf(ChildTaskFilter.ALL) }
    var orderedIds by remember(childName) { mutableStateOf(emptyList<String>()) }
    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(todayTasks.map(DemoTask::id)) {
        val currentIds = todayTasks.map(DemoTask::id)
        val base = if (orderedIds.isEmpty()) orderStore.load(currentIds) else orderedIds
        val normalized = normalizeTaskOrder(base, currentIds)
        orderedIds = normalized
        orderStore.save(normalized)
    }

    val taskMap = todayTasks.associateBy(DemoTask::id)
    val orderedTasks = orderedIds.mapNotNull(taskMap::get) +
        todayTasks.filterNot { task -> task.id in orderedIds }
    val filteredTasks = orderedTasks.filter { task ->
        when (filter) {
            ChildTaskFilter.ALL -> true
            ChildTaskFilter.COMPLETED -> task.status == TaskStatus.COMPLETED
            ChildTaskFilter.PENDING -> task.status == TaskStatus.PENDING
            ChildTaskFilter.RUNNING -> task.status == TaskStatus.RUNNING
        }
    }
    val selectedTask = orderedTasks.firstOrNull { it.id == selectedTaskId }

    fun moveTask(taskId: String, direction: Int) {
        val current = orderedIds.ifEmpty { todayTasks.map(DemoTask::id) }.toMutableList()
        val currentIndex = current.indexOf(taskId)
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + direction).coerceIn(0, current.lastIndex)
        if (targetIndex == currentIndex) return
        val item = current.removeAt(currentIndex)
        current.add(targetIndex, item)
        orderedIds = current
        orderStore.save(current)
    }

    fun dismissTaskSheet() {
        selectedTaskId?.let(onTaskClick)
    }

    fun handlePrimaryAction(task: DemoTask) {
        dismissTaskSheet()
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
    }

    ScreenBackdrop(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TaskPageHeader(
                    childName = childName,
                    title = title,
                    summary = summary,
                    subtitle = subtitle
                )
            }

            item {
                TaskStatsPanel(summary = summary)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChildTaskFilter.entries.forEach { current ->
                            FilterPill(
                                text = current.label,
                                selected = current == filter,
                                onClick = { filter = current }
                            )
                        }
                    }
                    Text(
                        text = "点任务看详情，右侧按钮直接打卡；长按左侧拖动手柄可排序。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
            }

            if (statusMessage.isNotBlank() && !statusMessage.startsWith("欢迎")) {
                item {
                    StatusBanner(message = statusMessage)
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    EmptyTaskCard(filter = filter)
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskListItem(
                        task = task,
                        isSelected = selectedTaskId == task.id,
                        isDragging = draggingTaskId == task.id,
                        dragOffsetY = if (draggingTaskId == task.id) draggingOffset else 0f,
                        onTaskClick = {
                            if (selectedTaskId == task.id) dismissTaskSheet() else onTaskClick(task.id)
                        },
                        onPrimaryAction = { handlePrimaryAction(task) },
                        onDragStart = {
                            draggingTaskId = task.id
                            draggingOffset = 0f
                        },
                        onDragOffsetChange = { draggingOffset = it },
                        onDragEnd = {
                            draggingTaskId = null
                            draggingOffset = 0f
                        },
                        onMoveUp = { moveTask(task.id, -1) },
                        onMoveDown = { moveTask(task.id, 1) }
                    )
                }
            }
        }

        if (selectedTask != null) {
            ModalBottomSheet(
                onDismissRequest = ::dismissTaskSheet,
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                TaskActionSheet(
                    task = selectedTask,
                    onPrimaryAction = { handlePrimaryAction(selectedTask) }
                )
            }
        }
    }
}

@Composable
private fun TaskPageHeader(
    childName: String,
    title: String,
    summary: ChildProgressSummaryUi,
    subtitle: String
) {
    AppHeader(
        title = title,
        subtitle = subtitle.ifBlank { "$childName · 今天已完成 ${summary.todayCompleted}/${summary.todayTotal} 项" }
    )
}

@Composable
private fun TaskStatsPanel(summary: ChildProgressSummaryUi) {
    Surface(
        color = Color.White.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                    note = "今天剩余任务",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricTile(
                    title = "昨天",
                    value = summary.yesterdayText.removePrefix("昨天 "),
                    note = "昨日完成情况",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "最近7天",
                    value = summary.last7DaysText.removePrefix("最近7天 "),
                    note = "一周完成率",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "最近30天",
                    value = summary.last30DaysText.removePrefix("最近30天 "),
                    note = "一月完成率",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyTaskCard(filter: ChildTaskFilter) {
    val message = when (filter) {
        ChildTaskFilter.ALL -> "今天还没有任务。"
        ChildTaskFilter.COMPLETED -> "今天还没有已完成的任务。"
        ChildTaskFilter.PENDING -> "当前没有未完成的任务。"
        ChildTaskFilter.RUNNING -> "当前没有执行中的任务。"
    }

    Surface(
        color = Color.White.copy(alpha = 0.95f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = FamilyPalette.InkSoft
        )
    }
}

@Composable
private fun TaskListItem(
    task: DemoTask,
    isSelected: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onTaskClick: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDragStart: () -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 84.dp.toPx() }
    val maxVisualOffset = with(density) { 34.dp.toPx() }
    val moveCooldownMs = 190L
    val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "task-scale")
    val overlayAlpha by animateFloatAsState(if (isDragging) 1f else 0f, label = "task-overlay")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = scale
                scaleY = scale
            },
        color = when {
            isDragging -> Color.White
            isSelected -> FamilyPalette.CanvasSoft
            else -> Color.White.copy(alpha = 0.97f)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = if (isDragging) 2.dp else 1.dp,
            color = when {
                isDragging -> FamilyPalette.Accent
                isSelected -> FamilyPalette.Accent.copy(alpha = 0.35f)
                else -> FamilyPalette.Line
            }
        ),
        shadowElevation = if (isDragging) 18.dp else if (isSelected) 10.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTaskClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DragHandle(
                isDragging = isDragging,
                onDragStart = onDragStart,
                onDragOffsetChange = onDragOffsetChange,
                onDragEnd = onDragEnd,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                dragThresholdPx = dragThresholdPx,
                maxVisualOffset = maxVisualOffset,
                moveCooldownMs = moveCooldownMs
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TaskStatusTag(task.status)
                    Text(
                        text = trailingTime(task),
                        style = MaterialTheme.typography.labelSmall,
                        color = FamilyPalette.InkSoft
                    )
                }

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = FamilyPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (isDragging) "拖动中，松手后会停在当前位置" else rowMeta(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDragging) FamilyPalette.Accent else FamilyPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (task.status == TaskStatus.COMPLETED) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "已完成",
                        tint = FamilyPalette.Accent
                    )
                }

                Button(
                    onClick = onPrimaryAction,
                    enabled = task.status != TaskStatus.COMPLETED,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (task.status == TaskStatus.RUNNING) FamilyPalette.Accent else FamilyPalette.AccentStrong,
                        contentColor = Color.White,
                        disabledContainerColor = FamilyPalette.SurfaceMuted,
                        disabledContentColor = FamilyPalette.InkSoft
                    )
                ) {
                    Text(
                        text = compactActionLabel(task),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .alpha(overlayAlpha)
                .background(FamilyPalette.Accent.copy(alpha = 0.14f))
        )
    }
}

@Composable
private fun DragHandle(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragThresholdPx: Float,
    maxVisualOffset: Float,
    moveCooldownMs: Long
) {
    Surface(
        modifier = Modifier
            .width(34.dp)
            .height(72.dp)
            .pointerInput(Unit) {
                var dragDistance = 0f
                var lastMoveAt = 0L
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragDistance = 0f
                        lastMoveAt = 0L
                        onDragStart()
                    },
                    onDragEnd = {
                        onDragOffsetChange(0f)
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragOffsetChange(0f)
                        onDragEnd()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragDistance += dragAmount.y
                    onDragOffsetChange(dragDistance.coerceIn(-maxVisualOffset, maxVisualOffset))
                    val canMove = lastMoveAt == 0L || change.uptimeMillis - lastMoveAt >= moveCooldownMs
                    if (abs(dragDistance) >= dragThresholdPx && canMove) {
                        if (dragDistance > 0f) onMoveDown() else onMoveUp()
                        dragDistance = 0f
                        onDragOffsetChange(0f)
                        lastMoveAt = change.uptimeMillis
                    }
                }
            },
        color = if (isDragging) FamilyPalette.Accent.copy(alpha = 0.12f) else FamilyPalette.SurfaceMuted,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = "拖动排序",
                tint = if (isDragging) FamilyPalette.Accent else FamilyPalette.InkSoft
            )
            Text(
                text = if (isDragging) "拖动" else "排序",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragging) FamilyPalette.Accent else FamilyPalette.InkSoft
            )
        }
    }
}

@Composable
private fun TaskActionSheet(
    task: DemoTask,
    onPrimaryAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TaskStatusTag(task.status)

        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            color = FamilyPalette.Ink
        )

        Text(
            text = cardMeta(task),
            style = MaterialTheme.typography.bodyMedium,
            color = FamilyPalette.InkSoft
        )

        Surface(
            color = FamilyPalette.CanvasSoft,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, FamilyPalette.Line)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetInfoRow(label = "任务模式", value = modeLabel(task.mode))
                SheetInfoRow(label = "交付要求", value = deliveryLabel(task.deliveryRequirement))
                SheetInfoRow(label = "奖励积分", value = "+${task.points} 积分")
                task.scheduledTimeLabel?.let { SheetInfoRow(label = "计划时间", value = it) }
                task.targetMinutes?.let { SheetInfoRow(label = "目标时长", value = "$it 分钟") }
                SheetInfoRow(label = "当前状态", value = taskTimeText(task))
            }
        }

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            enabled = task.status != TaskStatus.COMPLETED,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (task.status == TaskStatus.RUNNING) FamilyPalette.Accent else FamilyPalette.AccentStrong,
                contentColor = Color.White,
                disabledContainerColor = FamilyPalette.SurfaceMuted,
                disabledContentColor = FamilyPalette.InkSoft
            )
        ) {
            Text(actionLabel(task))
        }
    }
}

@Composable
private fun SheetInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FamilyPalette.InkSoft
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = FamilyPalette.Ink
        )
    }
}

@Composable
private fun TaskStatusTag(status: TaskStatus) {
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = FamilyPalette.Ink
        )
    }
}

private fun actionLabel(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> when {
            task.deliveryRequirement != DeliveryRequirement.NONE && task.mode == TaskMode.CHECK_ONLY -> "上传并打卡"
            task.mode == TaskMode.CHECK_ONLY -> "完成打卡"
            else -> "开始任务"
        }

        TaskStatus.RUNNING -> if (task.deliveryRequirement != DeliveryRequirement.NONE) "上传完成内容" else "结束任务"
        TaskStatus.COMPLETED -> "已完成"
    }
}

private fun compactActionLabel(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> when {
            task.deliveryRequirement != DeliveryRequirement.NONE -> "上传"
            task.mode == TaskMode.CHECK_ONLY -> "打卡"
            else -> "开始"
        }

        TaskStatus.RUNNING -> if (task.deliveryRequirement != DeliveryRequirement.NONE) "完成" else "结束"
        TaskStatus.COMPLETED -> "完成"
    }
}

private fun rowMeta(task: DemoTask): String {
    return buildString {
        append(modeLabel(task.mode))
        append(" · ")
        append(deliveryLabel(task.deliveryRequirement))
        append(" · +")
        append(task.points)
        append(" 积分")
    }
}

private fun cardMeta(task: DemoTask): String {
    return buildString {
        append(modeLabel(task.mode))
        append(" · ")
        append(deliveryLabel(task.deliveryRequirement))
        append(" · +")
        append(task.points)
        append(" 积分")
    }
}

private fun modeLabel(mode: TaskMode): String {
    return when (mode) {
        TaskMode.CHECK_ONLY -> "直接打卡"
        TaskMode.COUNTDOWN -> "倒计时"
        TaskMode.STOPWATCH -> "自主计时"
    }
}

private fun deliveryLabel(requirement: DeliveryRequirement): String {
    return when (requirement) {
        DeliveryRequirement.NONE -> "无上传"
        DeliveryRequirement.PHOTO -> "照片"
        DeliveryRequirement.VIDEO -> "视频"
        DeliveryRequirement.AUDIO -> "音频"
    }
}

private fun trailingTime(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> task.scheduledTimeLabel ?: "待开始"
        TaskStatus.RUNNING -> "进行中"
        TaskStatus.COMPLETED -> "已完成"
    }
}

private fun taskTimeText(task: DemoTask): String {
    return when (task.status) {
        TaskStatus.PENDING -> task.scheduledTimeLabel?.let { "计划时间 $it" } ?: "等待开始"
        TaskStatus.RUNNING -> {
            val elapsedSeconds = task.startedAt?.let { Duration.between(it, Instant.now()).seconds } ?: 0
            "已执行 ${elapsedSeconds.coerceAtLeast(0)} 秒"
        }

        TaskStatus.COMPLETED -> task.completedAtLabel?.let { "完成时间 $it" } ?: "今天已完成"
    }
}

private fun normalizeTaskOrder(currentOrder: List<String>, taskIds: List<String>): List<String> {
    val valid = currentOrder.filter { it in taskIds }
    val missing = taskIds.filterNot { it in valid }
    return valid + missing
}
