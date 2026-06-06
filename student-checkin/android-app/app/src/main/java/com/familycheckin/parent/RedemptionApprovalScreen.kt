package com.familycheckin.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface

enum class RedemptionRequestStatusUi {
    PENDING,
    APPROVED,
    REJECTED
}

data class RedemptionRequestUi(
    val id: String,
    val childName: String,
    val pointsRequested: Int,
    val cashAmountCny: Double,
    val requestedAtLabel: String,
    val status: RedemptionRequestStatusUi
)

data class RedemptionStatsUi(
    val last7DaysCount: Int,
    val last7DaysCashCny: Double,
    val currentMonthCount: Int,
    val currentMonthCashCny: Double
)

@Composable
fun RedemptionApprovalScreen(
    requests: List<RedemptionRequestUi>,
    stats: RedemptionStatsUi,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onBack: () -> Unit,
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
                BackHeader(
                    title = "积分兑换审核",
                    subtitle = "查看每个孩子最近的兑换申请和金额统计。",
                    onBack = onBack
                )
            }

            item {
                SectionSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "近 7 天",
                            value = "${stats.last7DaysCount} 次",
                            note = "¥${"%.2f".format(stats.last7DaysCashCny)}",
                            modifier = Modifier.weight(1f),
                            emphasized = true
                        )
                        MetricTile(
                            title = "本月",
                            value = "${stats.currentMonthCount} 次",
                            note = "¥${"%.2f".format(stats.currentMonthCashCny)}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (requests.isEmpty()) {
                item {
                    SectionSurface {
                        Text("当前没有待处理申请", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "孩子发起新的积分兑换后，这里会自动出现。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FamilyPalette.InkSoft
                        )
                    }
                }
            } else {
                items(requests, key = { it.id }) { request ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = FamilyPalette.Surface,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, FamilyPalette.Line)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${request.childName} 申请兑换 ${request.pointsRequested} 积分",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "兑换金额 ¥${"%.2f".format(request.cashAmountCny)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FamilyPalette.Ink
                            )
                            Text(
                                text = request.requestedAtLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = FamilyPalette.InkSoft
                            )
                            if (request.status == RedemptionRequestStatusUi.PENDING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onApprove(request.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("通过")
                                    }
                                    Button(
                                        onClick = { onReject(request.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("驳回")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
