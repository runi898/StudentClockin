package com.familycheckin.points

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.MetricTile
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

data class LedgerEntryUi(
    val timeLabel: String,
    val title: String,
    val delta: Int,
    val balanceAfter: Int
)

@Composable
fun PointsScreen(
    balance: Int,
    cashPerTenPoints: Int,
    minRedeemPoints: Int,
    statusMessage: String,
    ledger: List<LedgerEntryUi>,
    onRedeem: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var requestedText by remember { mutableStateOf(minRedeemPoints.toString()) }
    val requested = requestedText.toIntOrNull() ?: 0
    val cash = cashForPoints(requested = requested, cashPerTenPoints = cashPerTenPoints)
    val balanceCash = cashForPoints(requested = balance, cashPerTenPoints = cashPerTenPoints)
    val canSubmit = canRedeem(
        balance = balance,
        requested = requested,
        minRedeemPoints = minRedeemPoints
    )

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
                    title = "积分",
                    subtitle = "直接看总积分、可兑换金额和全部积分明细。",
                    onBack = onBack
                )
            }

            item {
                StatusBanner(message = statusMessage)
            }

            item {
                SectionSurface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(
                            title = "当前积分",
                            value = balance.toString(),
                            note = "实时可用",
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
                        "兑换汇率：10 积分 = $cashPerTenPoints 元。家长审核通过后才会真正扣减积分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
            }

            item {
                SectionSurface {
                    Text("申请兑换", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = requestedText,
                        onValueChange = { requestedText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("本次兑换积分") },
                        supportingText = {
                            Text(
                                "至少 $minRedeemPoints 积分，且不能超过当前积分。预计兑换 ¥${"%.2f".format(cash)}。"
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FamilyPalette.Surface,
                            unfocusedContainerColor = FamilyPalette.Surface,
                            focusedBorderColor = FamilyPalette.Accent,
                            unfocusedBorderColor = FamilyPalette.Line
                        )
                    )
                    Button(
                        onClick = { onRedeem(requested) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("提交兑换申请")
                    }
                    Text(
                        "提交后会进入待家长审核状态，实际扣分以家长审核结果为准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FamilyPalette.InkSoft
                    )
                }
            }

            item {
                Text("积分明细", style = MaterialTheme.typography.titleLarge, color = FamilyPalette.Ink)
            }

            items(ledger) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FamilyPalette.Surface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, FamilyPalette.Line)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = FamilyPalette.Ink)
                            Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (entry.delta >= 0) "+${entry.delta}" else entry.delta.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = FamilyPalette.Ink
                            )
                            Text("余额 ${entry.balanceAfter}", style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
                        }
                    }
                }
            }
        }
    }
}
