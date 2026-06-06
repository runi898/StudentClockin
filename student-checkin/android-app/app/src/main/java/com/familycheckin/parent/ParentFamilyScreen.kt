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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.familycheckin.server.ServerChildAccount
import com.familycheckin.server.createdAtLabel
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.FilterPill
import com.familycheckin.ui.GhostPill
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ParentFamilyScreen(
    familyName: String,
    parentEmail: String,
    cashPerTenPoints: Int,
    minRedeemPoints: Int,
    mediaRetentionDays: Int,
    childAccounts: List<ServerChildAccount>,
    notificationChannelType: String,
    notificationWebhookUrl: String,
    notificationEnabled: Boolean,
    statusMessage: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveSettings: (cashPerTenPoints: Int, minRedeemPoints: Int, mediaRetentionDays: Int) -> Unit,
    onCreateChild: (childName: String, childEmail: String, childPassword: String) -> Unit,
    onResetChildPassword: (memberId: String, newPassword: String) -> Unit,
    onDeleteChild: (memberId: String) -> Unit,
    onSaveNotification: (channelType: String, webhookUrl: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ParentFamilyScreen(
        familyName = familyName,
        parentEmail = parentEmail,
        cashPerTenPoints = cashPerTenPoints,
        minRedeemPoints = minRedeemPoints,
        mediaRetentionDays = mediaRetentionDays,
        childAccounts = childAccounts,
        notificationChannelType = notificationChannelType,
        notificationWebhookUrl = notificationWebhookUrl,
        notificationEnabled = notificationEnabled,
        statusMessage = statusMessage,
        isBusy = isBusy,
        onBack = onBack,
        onSaveSettings = { _, cash, minPoints, retentionDays ->
            onSaveSettings(cash, minPoints, retentionDays)
        },
        onUpdateChildProfile = { _, _ -> },
        onCreateChild = onCreateChild,
        onResetChildPassword = onResetChildPassword,
        onDeleteChild = onDeleteChild,
        onSaveNotification = onSaveNotification,
        modifier = modifier
    )
}

@Composable
fun ParentFamilyScreen(
    familyName: String,
    parentEmail: String,
    cashPerTenPoints: Int,
    minRedeemPoints: Int,
    mediaRetentionDays: Int,
    childAccounts: List<ServerChildAccount>,
    notificationChannelType: String,
    notificationWebhookUrl: String,
    notificationEnabled: Boolean,
    statusMessage: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveSettings: (familyName: String, cashPerTenPoints: Int, minRedeemPoints: Int, mediaRetentionDays: Int) -> Unit,
    onUpdateChildProfile: (memberId: String, childName: String) -> Unit,
    onCreateChild: (childName: String, childEmail: String, childPassword: String) -> Unit,
    onResetChildPassword: (memberId: String, newPassword: String) -> Unit,
    onDeleteChild: (memberId: String) -> Unit,
    onSaveNotification: (channelType: String, webhookUrl: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var familyNameText by remember { mutableStateOf(familyName) }
    var cashText by remember { mutableStateOf(cashPerTenPoints.toString()) }
    var minPointsText by remember { mutableStateOf(minRedeemPoints.toString()) }
    var retentionText by remember { mutableStateOf(mediaRetentionDays.toString()) }
    var childName by remember { mutableStateOf("") }
    var childEmail by remember { mutableStateOf("") }
    var childPassword by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<String?>(childAccounts.firstOrNull()?.memberId) }
    var editableChildName by remember { mutableStateOf("") }
    var resetPasswordText by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf(notificationChannelType) }
    var webhookUrl by remember { mutableStateOf(notificationWebhookUrl) }
    var notificationsEnabled by remember { mutableStateOf(notificationEnabled) }
    var deleteTarget by remember { mutableStateOf<ServerChildAccount?>(null) }

    LaunchedEffect(familyName, cashPerTenPoints, minRedeemPoints, mediaRetentionDays) {
        familyNameText = familyName
        cashText = cashPerTenPoints.toString()
        minPointsText = minRedeemPoints.toString()
        retentionText = mediaRetentionDays.toString()
    }

    LaunchedEffect(notificationChannelType, notificationWebhookUrl, notificationEnabled) {
        channelType = notificationChannelType
        webhookUrl = notificationWebhookUrl
        notificationsEnabled = notificationEnabled
    }

    LaunchedEffect(childAccounts) {
        if (childAccounts.none { it.memberId == selectedAccountId }) {
            selectedAccountId = childAccounts.firstOrNull()?.memberId
        }
    }

    val selectedAccount = childAccounts.firstOrNull { it.memberId == selectedAccountId }

    LaunchedEffect(selectedAccount?.memberId, childAccounts) {
        editableChildName = selectedAccount?.childName.orEmpty()
        resetPasswordText = ""
    }

    val settingsReady = familyNameText.isNotBlank() &&
        cashText.toIntOrNull() != null &&
        minPointsText.toIntOrNull() != null &&
        retentionText.toIntOrNull() != null &&
        !isBusy
    val childProfileReady = selectedAccount != null &&
        editableChildName.isNotBlank() &&
        editableChildName.trim() != selectedAccount.childName &&
        !isBusy
    val childCreateReady = childName.isNotBlank() &&
        childEmail.isNotBlank() &&
        childPassword.length >= 6 &&
        !isBusy
    val resetReady = selectedAccount != null && resetPasswordText.length >= 6 && !isBusy
    val notificationReady = (!notificationsEnabled || webhookUrl.isNotBlank()) && !isBusy

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
                title = "家庭账号管理",
                subtitle = "修改家庭名称、积分规则、孩子账号与通知方式",
                onBack = onBack
            )

            StatusBanner(message = statusMessage)

            SectionSurface {
                Text("家庭信息与规则", style = MaterialTheme.typography.titleLarge)
                AccountLine("家长账号", parentEmail)
                AccountLine("孩子数量", childAccounts.size.toString())
                SettingsField(
                    value = familyNameText,
                    onValueChange = { familyNameText = it },
                    label = "家庭名称"
                )
                SettingsField(
                    value = cashText,
                    onValueChange = { cashText = it.filter(Char::isDigit) },
                    label = "10 积分可兑换金额（元）"
                )
                SettingsField(
                    value = minPointsText,
                    onValueChange = { minPointsText = it.filter(Char::isDigit) },
                    label = "最少兑换积分"
                )
                SettingsField(
                    value = retentionText,
                    onValueChange = { retentionText = it.filter(Char::isDigit) },
                    label = "交付内容保留天数"
                )
                Button(
                    onClick = {
                        onSaveSettings(
                            familyNameText.trim(),
                            cashText.toInt(),
                            minPointsText.toInt(),
                            retentionText.toInt()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settingsReady
                ) {
                    Text(if (isBusy) "保存中..." else "保存家庭设置")
                }
            }

            SectionSurface {
                Text("通知配置", style = MaterialTheme.typography.titleLarge)
                Text(
                    "配置钉钉机器人或 Webhook 后，孩子完成任务、积分变动、兑换申请都可以实时通知到你。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        text = "钉钉",
                        selected = channelType == "dingtalk",
                        onClick = { channelType = "dingtalk" }
                    )
                    FilterPill(
                        text = "Webhook",
                        selected = channelType == "webhook",
                        onClick = { channelType = "webhook" }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(
                        text = "已启用",
                        selected = notificationsEnabled,
                        onClick = { notificationsEnabled = true }
                    )
                    FilterPill(
                        text = "已关闭",
                        selected = !notificationsEnabled,
                        onClick = { notificationsEnabled = false }
                    )
                }
                SettingsField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it.trim() },
                    label = if (channelType == "dingtalk") "钉钉机器人 Webhook" else "Webhook 地址"
                )
                Button(
                    onClick = { onSaveNotification(channelType, webhookUrl, notificationsEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = notificationReady
                ) {
                    Text(if (isBusy) "保存中..." else "保存通知配置")
                }
            }

            SectionSurface {
                Text("孩子账号", style = MaterialTheme.typography.titleLarge)
                Text(
                    "可修改孩子显示名称，也可以重置密码或删除账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                if (childAccounts.isEmpty()) {
                    Text("还没有孩子账号，请先创建一个。", color = FamilyPalette.InkSoft)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        childAccounts.forEach { account ->
                            FilterPill(
                                text = account.childName,
                                selected = account.memberId == selectedAccountId,
                                onClick = { selectedAccountId = account.memberId }
                            )
                        }
                    }
                    selectedAccount?.let { account ->
                        ChildAccountCard(account = account)
                        Text("修改孩子名称", style = MaterialTheme.typography.titleMedium)
                        SettingsField(
                            value = editableChildName,
                            onValueChange = { editableChildName = it },
                            label = "孩子名称"
                        )
                        Button(
                            onClick = { onUpdateChildProfile(account.memberId, editableChildName.trim()) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = childProfileReady
                        ) {
                            Text(if (isBusy) "保存中..." else "保存孩子信息")
                        }
                        SettingsField(
                            value = resetPasswordText,
                            onValueChange = { resetPasswordText = it },
                            label = "新的登录密码（至少 6 位）",
                            isPassword = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onResetChildPassword(account.memberId, resetPasswordText)
                                    resetPasswordText = ""
                                },
                                modifier = Modifier.weight(1f),
                                enabled = resetReady
                            ) {
                                Text("重置密码")
                            }
                            GhostPill(
                                text = "删除账号",
                                enabled = !isBusy,
                                onClick = { deleteTarget = account }
                            )
                        }
                    }
                }
            }

            SectionSurface {
                Text("新增孩子账号", style = MaterialTheme.typography.titleLarge)
                SettingsField(
                    value = childName,
                    onValueChange = { childName = it },
                    label = "孩子名称"
                )
                SettingsField(
                    value = childEmail,
                    onValueChange = { childEmail = it.trim() },
                    label = "孩子登录邮箱"
                )
                SettingsField(
                    value = childPassword,
                    onValueChange = { childPassword = it },
                    label = "孩子登录密码",
                    isPassword = true
                )
                Button(
                    onClick = {
                        onCreateChild(childName.trim(), childEmail.trim(), childPassword)
                        childName = ""
                        childEmail = ""
                        childPassword = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = childCreateReady
                ) {
                    Text(if (isBusy) "创建中..." else "创建孩子账号")
                }
            }
        }
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除孩子账号") },
            text = { Text("删除后，${account.childName} 将无法继续登录，已有历史数据会保留在家庭统计中。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChild(account.memberId)
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
private fun AccountLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = FamilyPalette.InkSoft, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = FamilyPalette.Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChildAccountCard(account: ServerChildAccount) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(account.childName, style = MaterialTheme.typography.titleMedium)
        if (account.email.isNotBlank()) {
            Text(account.email, style = MaterialTheme.typography.bodySmall, color = FamilyPalette.InkSoft)
        }
        Text(
            "创建时间 ${account.createdAtLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = FamilyPalette.InkSoft
        )
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
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
