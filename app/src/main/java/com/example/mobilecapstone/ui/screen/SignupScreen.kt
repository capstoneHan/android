package com.example.mobilecapstone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SignupScreen(
    modifier: Modifier = Modifier,
    username: String,
    email: String,
    password: String,
    passwordConfirm: String,
    height: String,
    weight: String,
    isLoading: Boolean,
    message: String?,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSignup: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null
                )
            },
            title = "회원가입",
            description = "기본 계정 정보와 키, 몸무게를 입력하면 맞춤 추천에 활용할 수 있습니다."
        )

        ElevatedCard(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "계정 정보",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("사용자 이름") },
                    singleLine = true,
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("이메일") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("비밀번호") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("비밀번호 확인") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    enabled = !isLoading
                )

                Text(
                    text = "신체 정보",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = height,
                    onValueChange = { value ->
                        onHeightChange(value.filter { it.isDigit() }.take(3))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("키(cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = weight,
                    onValueChange = { value ->
                        onWeightChange(value.filter { it.isDigit() }.take(3))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("몸무게(kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    enabled = !isLoading
                )

                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.contains("성공")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                Button(
                    onClick = onSignup,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("회원가입")
                    }
                }

                FilledTonalButton(
                    onClick = onBackToLogin,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("로그인 화면으로 돌아가기")
                }
            }
        }
    }
}