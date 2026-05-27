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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class RedemptionRequestUi(
    val id: String,
    val childName: String,
    val pointsRequested: Int,
    val cashAmountCny: Double,
    val requestedAtLabel: String
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
    onReject: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("兑换审核", style = MaterialTheme.typography.headlineSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("近 7 天: ${stats.last7DaysCount} 次 / ¥${"%.2f".format(stats.last7DaysCashCny)}")
                Text("本月: ${stats.currentMonthCount} 次 / ¥${"%.2f".format(stats.currentMonthCashCny)}")
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(requests) { request ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${request.childName} 申请兑换 ${request.pointsRequested} 积分")
                        Text("金额 ¥${"%.2f".format(request.cashAmountCny)}")
                        Text(request.requestedAtLabel, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onApprove(request.id) }) {
                                Text("通过")
                            }
                            Button(onClick = { onReject(request.id) }) {
                                Text("拒绝")
                            }
                        }
                    }
                }
            }
        }
    }
}
