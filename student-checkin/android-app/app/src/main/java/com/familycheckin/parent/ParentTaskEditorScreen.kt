package com.familycheckin.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.demo.DeliveryRequirement
import com.familycheckin.demo.TaskMode
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.FilterPill
import com.familycheckin.ui.GhostPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ParentTaskEditorScreen(
    familyName: String,
    children: List<ParentChildSummaryUi>,
    selectedChildId: String?,
    statusMessage: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSubmit: (String, String, TaskMode, DeliveryRequirement, Int, Int?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var assignedChildId by remember(children, selectedChildId) {
        mutableStateOf(selectedChildId ?: children.firstOrNull()?.memberId)
    }
    var taskName by remember { mutableStateOf("") }
    var pointsText by remember { mutableStateOf("1") }
    var targetMinutesText by remember { mutableStateOf("") }
    var scheduledTimeText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(TaskMode.CHECK_ONLY) }
    var deliveryRequirement by remember { mutableStateOf(DeliveryRequirement.NONE) }

    val points = pointsText.toIntOrNull()
    val targetMinutes = targetMinutesText.toIntOrNull()
    val formReady =
        !assignedChildId.isNullOrBlank() && taskName.isNotBlank() && points != null && points > 0 && !isBusy

    ScreenBackdrop(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BackHeader(
                title = "新增任务",
                subtitle = "创建后会同步到 $familyName 当前选中的孩子账号",
                onBack = onBack
            )

            StatusBanner(message = statusMessage)

            SectionSurface {
                Text("任务配置", style = MaterialTheme.typography.titleLarge)
                Text(
                    "先选择任务分配对象，再设置任务模式、积分和交付要求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                Text("分配给谁", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    children.forEach { child ->
                        FilterPill(
                            text = child.childName,
                            selected = child.memberId == assignedChildId,
                            onClick = { assignedChildId = child.memberId }
                        )
                    }
                }
                EditorField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = "任务名称"
                )
                EditorField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = "奖励积分"
                )
                EditorField(
                    value = scheduledTimeText,
                    onValueChange = { scheduledTimeText = it.take(5) },
                    label = "固定时间（选填，例如 20:00）"
                )
                EditorField(
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
                        enabled = formReady,
                        onClick = {
                            onSubmit(
                                assignedChildId.orEmpty(),
                                taskName.trim(),
                                mode,
                                deliveryRequirement,
                                points ?: 1,
                                targetMinutes,
                                scheduledTimeText.trim().ifBlank { null }
                            )
                        }
                    ) {
                        Text(if (isBusy) "创建中..." else "保存并创建")
                    }
                    GhostPill(text = "取消", onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun EditorField(
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
