package com.example.mobilecapstone

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    accountName: String,
    notificationsEnabled: Boolean,
    wifiOnlySync: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onWifiOnlySyncChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            title = "설정 화면",
            description = "동기화, 알림, 계정 관련 껍데기를 미리 잡아두면 백엔드 붙일 때 빨라져."
        )

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("계정", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = tokenLabel(LocalContext.current, accountName),
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = onEditProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("내 정보 수정")
                }
                HorizontalDivider()
                SettingRow(
                    title = "알림 받기",
                    description = "분석 완료나 추천 업데이트 알림",
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationsChange
                )
                SettingRow(
                    title = "Wi‑Fi에서만 동기화",
                    description = "오프라인 결과 업로드 시 셀룰러 사용 제한",
                    checked = wifiOnlySync,
                    onCheckedChange = onWifiOnlySyncChange
                )
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("로그아웃")
                }
            }
        }
    }
}

@Composable
internal fun ProfileEditScreen(
    modifier: Modifier = Modifier,
    currentHeight: String,
    currentWeight: String,
    editedHeight: String,
    editedWeight: String,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val parsedHeight = editedHeight.toIntOrNull()
    val parsedWeight = editedWeight.toIntOrNull()
    val heightValid = parsedHeight in 100..230
    val weightValid = parsedWeight in 30..200
    val canSave = heightValid && weightValid

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            title = "내 정보 수정",
            description = "추천 분석에 사용할 신체 정보를 확인하고 변경할 수 있어."
        )

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("현재 정보", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileValueCard(
                        label = "키",
                        value = currentHeight.ifBlank { "-" },
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileValueCard(
                        label = "몸무게",
                        value = currentWeight.ifBlank { "-" },
                        unit = "kg",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("변경할 정보", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = editedHeight,
                    onValueChange = { onHeightChange(it.filterWholeNumberInput()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("키") },
                    suffix = { Text("cm") },
                    singleLine = true,
                    isError = editedHeight.isNotBlank() && !heightValid,
                    supportingText = {
                        if (editedHeight.isNotBlank() && !heightValid) {
                            Text("100~230cm 범위로 입력해 주세요.")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = editedWeight,
                    onValueChange = { onWeightChange(it.filterWholeNumberInput()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("몸무게") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = editedWeight.isNotBlank() && !weightValid,
                    supportingText = {
                        if (editedWeight.isNotBlank() && !weightValid) {
                            Text("30~200kg 범위로 입력해 주세요.")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileChangePreview(
                        label = "키",
                        before = currentHeight.ifBlank { "-" },
                        after = editedHeight.ifBlank { "-" },
                        unit = "cm",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileChangePreview(
                        label = "몸무게",
                        before = currentWeight.ifBlank { "-" },
                        after = editedWeight.ifBlank { "-" },
                        unit = "kg",
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("저장")
                }
            }
        }
    }
}

@Composable
internal fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProfileValueCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ProfileChangePreview(
    label: String,
    before: String,
    after: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "$before $unit -> $after $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun String.filterWholeNumberInput(): String {
    return filter { it.isDigit() }.take(3)
}
