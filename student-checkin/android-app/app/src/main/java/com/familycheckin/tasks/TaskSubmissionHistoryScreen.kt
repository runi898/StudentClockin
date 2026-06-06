package com.familycheckin.tasks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.familycheckin.parent.ParentTaskRowUi
import com.familycheckin.server.ServerTaskSubmission
import com.familycheckin.server.publicUrl
import com.familycheckin.server.uploadedAtLabel
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface

@Composable
fun TaskSubmissionHistoryScreen(
    task: ParentTaskRowUi?,
    baseUrl: String,
    submissions: List<ServerTaskSubmission>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    ScreenBackdrop(
        modifier = Modifier
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
                BackHeader(
                    title = "任务详情",
                    subtitle = task?.title ?: "查看任务状态、要求和上传记录",
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SectionSurface {
                    Text("任务摘要", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "孩子",
                            value = task?.childName ?: "--",
                            note = "当前查看",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                        MetricTile(
                            title = "状态",
                            value = task?.statusLabel ?: "--",
                            note = "今日状态",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "时间",
                            value = task?.timeLabel ?: "--",
                            note = "计划 / 完成",
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "积分",
                            value = task?.pointsLabel ?: "--",
                            note = "完成后发放",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "上传要求：${task?.deliveryLabel ?: "无"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FamilyPalette.Ink
                    )
                    Text(
                        task?.note ?: "暂时没有更多备注。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
            }

            item {
                SectionSurface {
                    Text("交付记录", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (submissions.isEmpty()) {
                            "孩子还没有上传照片、视频或音频，后续上传后会直接显示在这里。"
                        } else {
                            "共 ${submissions.size} 条上传记录，家长可以直接预览照片或打开原文件。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
            }

            if (submissions.isEmpty()) {
                item {
                    SectionSurface {
                        Text("暂未上传", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "如果这是待完成任务，孩子完成后上传的照片、视频或音频会自动出现在这里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                    }
                }
            } else {
                items(submissions, key = { it.id }) { submission ->
                    val url = submission.publicUrl(baseUrl)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = FamilyPalette.Surface,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, FamilyPalette.Line)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${submission.submissionType.toSubmissionTitle()} · ${submission.uploadedAtLabel()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = FamilyPalette.Ink
                            )
                            if (submission.submissionType == "photo") {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "任务提交照片",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 320.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = FamilyPalette.InkSoft
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when (submission.submissionType) {
                                        "photo" -> "打开原图"
                                        "video" -> "打开视频"
                                        "audio" -> "打开音频"
                                        else -> "打开文件"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toSubmissionTitle(): String {
    return when (this) {
        "photo" -> "照片"
        "video" -> "视频"
        "audio" -> "音频"
        else -> "提交内容"
    }
}
