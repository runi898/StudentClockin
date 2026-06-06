package com.familycheckin.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familycheckin.ui.BackHeader
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.ScreenBackdrop
import com.familycheckin.ui.SectionSurface
import com.familycheckin.ui.StatusBanner

@Composable
fun ParentSignupScreen(
    statusMessage: String,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSubmit: (
        familyName: String,
        parentName: String,
        parentEmail: String,
        parentPassword: String,
        childName: String,
        childEmail: String,
        childPassword: String
    ) -> Unit
) {
    var familyName by remember { mutableStateOf("") }
    var parentName by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var parentPassword by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var childEmail by remember { mutableStateOf("") }
    var childPassword by remember { mutableStateOf("") }

    val canSubmit = familyName.isNotBlank() &&
        parentName.isNotBlank() &&
        parentEmail.isNotBlank() &&
        parentPassword.length >= 6 &&
        childName.isNotBlank() &&
        childEmail.isNotBlank() &&
        childPassword.length >= 6 &&
        !isBusy

    ScreenBackdrop(
        modifier = Modifier
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
                title = "注册家长账号",
                subtitle = "先创建家庭和家长账号，再同时开通第一个孩子账号。后续还能继续添加更多孩子。",
                onBack = onBack
            )

            StatusBanner(message = statusMessage)

            SectionSurface {
                Text("家庭信息", style = MaterialTheme.typography.titleLarge)
                SignupField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = "家庭名称"
                )
                SignupField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = "家长称呼"
                )
            }

            SectionSurface {
                Text("家长登录账号", style = MaterialTheme.typography.titleLarge)
                Text(
                    "建议填写常用邮箱，后续忘记密码时可通过家长账号找回。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                SignupField(
                    value = parentEmail,
                    onValueChange = { parentEmail = it.trim() },
                    label = "家长邮箱"
                )
                SignupField(
                    value = parentPassword,
                    onValueChange = { parentPassword = it },
                    label = "家长密码",
                    isPassword = true
                )
            }

            SectionSurface {
                Text("第一个孩子账号", style = MaterialTheme.typography.titleLarge)
                Text(
                    "孩子端也使用独立账号登录，后续统计和积分会按孩子账号分别保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FamilyPalette.InkSoft
                )
                SignupField(
                    value = childName,
                    onValueChange = { childName = it },
                    label = "孩子姓名"
                )
                SignupField(
                    value = childEmail,
                    onValueChange = { childEmail = it.trim() },
                    label = "孩子登录邮箱"
                )
                SignupField(
                    value = childPassword,
                    onValueChange = { childPassword = it },
                    label = "孩子登录密码",
                    isPassword = true
                )
            }

            Button(
                onClick = {
                    onSubmit(
                        familyName,
                        parentName,
                        parentEmail,
                        parentPassword,
                        childName,
                        childEmail,
                        childPassword
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit
            ) {
                Text(if (isBusy) "创建中..." else "创建家庭并开始使用")
            }
        }
    }
}

@Composable
private fun SignupField(
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
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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
