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
import androidx.compose.ui.unit.dp
@Composable
internal fun HomeScreen(
    modifier: Modifier = Modifier,
    accountName: String,
    selectedAsset: String,
    summary: ResultSummary?,
    historyCount: Int,
    onStartCapture: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SoftPill(text = "환영해, ${tokenLabel(context, accountName)}")
                Text(
                    text = "오늘의 스타일 분석을 시작해볼까?",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "현재 테스트 입력은 ${selectedAsset.substringBeforeLast(".")} 샘플로 설정되어 있어.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartCapture,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("촬영 시작")
                    }
                    FilledTonalButton(
                        onClick = onOpenAnalysis,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("분석 보기")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "최근 프레임",
                value = summary?.frameType?.let { tokenLabel(context, it) } ?: "없음"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "추천 상태",
                value = if (summary == null) "준비 중" else "생성 가능"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "세션 기록",
                value = "${historyCount}건"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "태그 수",
                value = "${summary?.tags?.size ?: 0}개"
            )
        }

        SectionTitle("빠른 이동")
        QuickActionCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "촬영 화면",
            description = "카메라 연결 전 단계지만 샘플 선택과 촬영 흐름을 미리 점검할 수 있어.",
            buttonLabel = "촬영 화면 열기",
            onClick = onStartCapture
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
            title = "분석 결과 화면",
            description = "현재 구현된 분석 기능과 JSON 결과를 앱 화면에서 확인할 수 있어.",
            buttonLabel = "분석 화면 열기",
            onClick = onOpenAnalysis
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
            title = "추천 목록 화면",
            description = "백엔드 연결 전까지는 더미 추천 카드로 전체 플로우를 검증할 수 있어.",
            buttonLabel = "추천 목록 보기",
            onClick = onOpenRecommendations
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            title = "기록 화면",
            description = "세션 중 실행한 분석 결과를 리스트 형태로 쌓아보는 껍데기 화면이야.",
            buttonLabel = "기록 보기",
            onClick = onOpenHistory
        )
    }
}

