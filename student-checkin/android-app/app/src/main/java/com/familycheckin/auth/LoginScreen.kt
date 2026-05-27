package com.familycheckin.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onLoginParent: () -> Unit,
    onLoginChild: () -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val email = remember { mutableStateOf("parent@example.com") }
    val password = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "学生任务打卡",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "同一个 App 支持家长端和孩子端登录，数据统一保存在服务器。",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = email.value,
            onValueChange = { email.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("账号 / 邮箱") },
            singleLine = true
        )
        OutlinedTextField(
            value = password.value,
            onValueChange = { password.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true
        )
        Button(
            onClick = onLoginParent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("进入家长端")
        }
        Button(
            onClick = onLoginChild,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("进入孩子端")
        }
        Button(
            onClick = { onForgotPassword(email.value) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("忘记密码")
        }
    }
}
