package com.familycheckin.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.AppHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.GhostPill
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.PrimaryPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
@Composable
fun ChildProfileScreen(
    childName: String,
    pointsBalance: Int,
    cashPerTenPoints: Int,
    onOpenPoints: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val balanceCash = pointsBalance * cashPerTenPoints / 10.0
    val maxRedeemCash = if (pointsBalance >= 10) balanceCash else 0.0

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
                    subtitle = "查看积分、兑换入口和账号切换。"
                )
            }

            item {
                SectionSurface {
                    Text(childName, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "当前积分",
                            value = pointsBalance.toString(),
                            note = "可直接兑换",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                        MetricTile(
                            title = "约合金额",
                            value = "¥${"%.2f".format(balanceCash)}",
                            note = "按当前汇率",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "当前规则：10 积分可兑换 $cashPerTenPoints 元，兑换时至少 10 积分，且不能超过你现有的积分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryPill(text = "积分与兑换", onClick = onOpenPoints)
                        GhostPill(text = "切换账号", onClick = onSwitchAccount)
                    }
                }
            }

            item {
                SectionSurface {
                    Text("账号说明", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "每个孩子使用独立账号登录，任务记录、积分明细和兑换记录都会单独保存，后续换设备登录也能继续使用。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "当前最多可按现有积分申请兑换 ￥${"%.2f".format(maxRedeemCash)}，实际提交仍需至少 10 积分，且不能超过你当前积分。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
