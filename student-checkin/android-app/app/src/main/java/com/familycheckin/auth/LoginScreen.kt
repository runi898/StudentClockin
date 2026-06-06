package com.familycheckin.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.familycheckin.BuildConfig
import com.familycheckin.ui.FamilyPalette
import com.familycheckin.ui.StatusBanner

internal fun buildLoginPrefillState(
    recentEmail: String?,
    rememberedPassword: String?,
    rememberedPasswordEnabled: Boolean
): Triple<String, String, Boolean> {
    return Triple(
        recentEmail.orEmpty(),
        if (rememberedPasswordEnabled) rememberedPassword.orEmpty() else "",
        rememberedPasswordEnabled
    )
}

internal fun shouldShowLoginStatusBanner(statusMessage: String): Boolean {
    if (statusMessage.isBlank()) return false
    if (statusMessage.startsWith("欢迎")) return false
    if (statusMessage == "The coroutine scope left the composition") return false
    return true
}

@Composable
fun LoginScreen(
    statusMessage: String,
    isBusy: Boolean = false,
    recentEmail: String? = null,
    rememberedPassword: String? = null,
    rememberedPasswordEnabled: Boolean = false,
    onLogin: (String, String, Boolean) -> Unit,
    onForgotPassword: (String) -> Unit,
    onOpenSignUp: (() -> Unit)? = null,
    onOpenChildDemo: (() -> Unit)? = null,
    onOpenParentDemo: (() -> Unit)? = null
) {
    val prefillState = buildLoginPrefillState(
        recentEmail = recentEmail,
        rememberedPassword = rememberedPassword,
        rememberedPasswordEnabled = rememberedPasswordEnabled
    )
    var email by remember { mutableStateOf(prefillState.first) }
    var password by remember { mutableStateOf(prefillState.second) }
    var rememberPassword by remember { mutableStateOf(prefillState.third) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(prefillState) {
        email = prefillState.first
        password = prefillState.second
        rememberPassword = prefillState.third
        passwordVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FamilyPalette.Canvas)
    ) {
        LoginBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.34f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "家庭任务成长",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = FamilyPalette.Ink
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "学生任务打卡",
                    style = MaterialTheme.typography.headlineLarge,
                    color = FamilyPalette.Ink
                )
                Text(
                    text = "家长统一管理账号与任务，孩子按要求完成后打卡、计时、上传交付。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FamilyPalette.Ink.copy(alpha = 0.76f)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 34.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 22.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "登录",
                    style = MaterialTheme.typography.titleLarge,
                    color = FamilyPalette.Ink
                )

                if (shouldShowLoginStatusBanner(statusMessage)) {
                    StatusBanner(message = statusMessage)
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账号") },
                    placeholder = { Text("请输入邮箱账号") },
                    singleLine = true,
                    colors = loginFieldColors()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    placeholder = { Text("请输入登录密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = FamilyPalette.InkSoft
                            )
                        }
                    },
                    colors = loginFieldColors()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberPassword = !rememberPassword },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it }
                    )
                    Text(
                        text = "记住密码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FamilyPalette.Ink
                    )
                }

                Button(
                    onClick = { onLogin(email, password, rememberPassword) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !isBusy && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FamilyPalette.AccentStrong,
                        contentColor = Color.White,
                        disabledContainerColor = FamilyPalette.Line,
                        disabledContentColor = FamilyPalette.InkSoft
                    )
                ) {
                    Text(if (isBusy) "登录中..." else "登录")
                }

                OutlinedButton(
                    onClick = { onForgotPassword(email) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && email.isNotBlank()
                ) {
                    Text("忘记密码")
                }

                if (onOpenSignUp != null && !BuildConfig.DEMO_MODE) {
                    OutlinedButton(
                        onClick = onOpenSignUp,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text("注册家长账号")
                    }
                }

                if (BuildConfig.DEMO_MODE && onOpenChildDemo != null && onOpenParentDemo != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onOpenChildDemo,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("演示孩子端")
                        }
                        OutlinedButton(
                            onClick = onOpenParentDemo,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("演示家长端")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE7EDE8),
                        Color(0xFFF1E5D8),
                        Color(0xFFF8F3EC)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-88).dp, y = 28.dp)
                .size(280.dp)
                .blur(10.dp)
                .background(Color(0x553E7D5D), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 78.dp, y = (-120).dp)
                .size(320.dp)
                .blur(16.dp)
                .background(Color(0x66F0C395), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 42.dp, y = 18.dp)
                .size(width = 230.dp, height = 360.dp)
                .graphicsLayer {
                    rotationZ = 18f
                    alpha = 0.48f
                }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xBBFFFFFF), Color(0x55FFFFFF))
                    ),
                    shape = RoundedCornerShape(48.dp)
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 120.dp)
                .size(520.dp)
                .blur(18.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF4DCC8), Color(0x00F4DCC8))
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = FamilyPalette.Surface,
    unfocusedContainerColor = FamilyPalette.Surface,
    focusedBorderColor = FamilyPalette.Accent,
    unfocusedBorderColor = FamilyPalette.Line,
    focusedLabelColor = FamilyPalette.Accent,
    unfocusedLabelColor = FamilyPalette.InkSoft
)
