package com.familycheckin.points

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
    ledger: List<LedgerEntryUi>,
    onRedeem: (Int) -> Unit
) {
    var requestedText by remember { mutableStateOf("10") }
    val requested = requestedText.toIntOrNull() ?: 0
    val cash = cashForPoints(requested = requested, cashPerTenPoints = cashPerTenPoints)
    val canSubmit = canRedeem(balance = balance, requested = requested)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("我的积分", style = MaterialTheme.typography.headlineSmall)
        Text("当前积分 $balance", style = MaterialTheme.typography.titleLarge)
        Text("可兑换 ¥${"%.2f".format(cash)}", style = MaterialTheme.typography.bodyLarge)
        Text("兑换比例: 10 积分 = ¥$cashPerTenPoints", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = requestedText,
            onValueChange = { requestedText = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("兑换积分") },
            supportingText = { Text("默认 10，可修改；需大于等于 10 且小于当前积分") },
            singleLine = true
        )
        Button(
            onClick = { onRedeem(requested) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("提交兑换")
        }
        Text("积分明细", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(ledger) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.timeLabel, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text(
                                text = if (entry.delta >= 0) "+${entry.delta}" else entry.delta.toString(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text("余额 ${entry.balanceAfter}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
