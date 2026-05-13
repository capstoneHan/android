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
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
@Composable
internal fun CaptureScreen(
    modifier: Modifier = Modifier,
    selectedAsset: String,
    selectedImageLabel: String,
    sampleBitmap: Bitmap?,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onGoAnalysis: () -> Unit
) {
    var pendingInputAction by remember { mutableStateOf<PhotoInputAction?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "촬영 화면",
            description = "전신 사진을 촬영하거나 갤러리에서 선택해 분석을 준비합니다."
        )

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "사진 입력",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { pendingInputAction = PhotoInputAction.Camera },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Text("촬영")
                    }
                    FilledTonalButton(
                        onClick = { pendingInputAction = PhotoInputAction.Gallery },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Text("갤러리")
                    }
                }
            }
        }

        if (sampleBitmap != null) {
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "선택된 테스트 이미지",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = selectedImageLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Image(
                        bitmap = sampleBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            }
        }

        PlaceholderFeatureCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "촬영 가이드",
            description = "정확한 분석을 위해 전신이 화면 안에 들어오도록 촬영해 주세요."
        )
        PlaceholderFeatureCard(
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = "이미지 품질 안내",
            description = "흔들림이 적고 배경과 신체가 잘 구분되는 사진을 권장합니다."
        )
    }

    pendingInputAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingInputAction = null },
            icon = {
                Icon(
                    imageVector = when (action) {
                        PhotoInputAction.Camera -> Icons.Rounded.CameraAlt
                        PhotoInputAction.Gallery -> Icons.Rounded.PhotoLibrary
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "전신 사진을 준비해 주세요",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "정확한 분석을 위해 머리부터 발끝까지 화면 안에 보이는 사진을 사용해 주세요. 몸이 가려지거나 사람이 아닌 이미지인 경우 분석이 제한될 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingInputAction = null
                        when (action) {
                            PhotoInputAction.Camera -> onTakePhoto()
                            PhotoInputAction.Gallery -> onPickFromGallery()
                        }
                    }
                ) {
                    Text(
                        when (action) {
                            PhotoInputAction.Camera -> "촬영하기"
                            PhotoInputAction.Gallery -> "사진 선택"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingInputAction = null }) {
                    Text("취소")
                }
            },
            containerColor = Color.White,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

private enum class PhotoInputAction {
    Camera,
    Gallery
}
