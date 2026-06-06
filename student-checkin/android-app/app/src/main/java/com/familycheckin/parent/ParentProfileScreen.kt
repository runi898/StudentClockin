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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.GhostPill
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface

@Composable
fun ParentProfileScreen(
    familyName: String,
    parentEmail: String,
    childCount: Int,
    redemptionStats: RedemptionStatsUi,
    onOpenFamilyManage: () -> Unit,
    onOpenRedemptions: () -> Unit,
    onSwitchAccount: () -> Unit,
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
                    title = "我的",
                    subtitle = "管理家庭账号、孩子账户和积分兑换审核。"
                )
            }

            item {
                SectionSurface {
                    Text(familyName.ifBlank { "家庭账号" }, style = MaterialTheme.typography.titleLarge)
                    Text(parentEmail, style = MaterialTheme.typography.bodyMedium, color = FamilyPalette.InkSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "孩子账号",
                            value = childCount.toString(),
                            note = "独立登录",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                        MetricTile(
                            title = "本月兑换",
                            value = "${redemptionStats.currentMonthCount} 次",
                            note = "¥${"%.2f".format(redemptionStats.currentMonthCashCny)}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SectionSurface {
                    Text("常用入口", style = MaterialTheme.typography.titleLarge)
                    ActionRow(title = "家庭设置与孩子账号", subtitle = "积分汇率、最小兑换、保留天数、创建孩子账号", onClick = onOpenFamilyManage)
                    ActionRow(title = "兑换审核", subtitle = "查看孩子兑换记录与最近统计", onClick = onOpenRedemptions)
                    GhostPill(text = "切换账号", onClick = onSwitchAccount)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, FamilyPalette.Line)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = FamilyPalette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
            }
            Text("进入", style = MaterialTheme.typography.labelMedium, color = FamilyPalette.Accent)
        }
    }
}
