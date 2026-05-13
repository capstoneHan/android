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
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
internal fun AnalysisResultScreen(
    modifier: Modifier = Modifier,
    uiState: PipelineUiState,
    summary: ResultSummary?,
    onRun: () -> Unit,
    onOpenRecommendations: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
            title = "분석 결과 화면",
            description = "사진에서 추출한 체형, 얼굴형, 컬러 톤 정보를 기반으로 추천 태그를 확인합니다."
        )

        uiState.sampleBitmap?.let { bitmap ->
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("선택된 이미지", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = uiState.selectedImageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(20.dp))
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("파이프라인 상태", style = MaterialTheme.typography.titleLarge)
                uiState.steps.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(step.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when (step.status) {
                                    StepStatus.PENDING -> "준비 중"
                                    StepStatus.RUNNING -> "진행 중"
                                    StepStatus.COMPLETED -> "완료"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StepStatusPill(status = step.status)
                    }
                }

                Button(
                    onClick = onRun,
                    enabled = !uiState.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    if (uiState.isRunning) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("분석 실행 중")
                        }
                    } else {
                        Text(
                            if ("pose" in uiState.completedSteps) {
                                "분석 다시 실행"
                            } else {
                                "분석 시작"
                            }
                        )
                    }
                }

                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.outputFilePath.isNotBlank()) {
                    Text(
                        text = "저장 경로: ${uiState.outputFilePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("요약 카드", style = MaterialTheme.typography.titleLarge)

                if (summary == null) {
                    Text(
                        text = "아직 분석 결과가 없습니다. 위 버튼을 눌러 분석을 먼저 실행해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "프레임",
                            value = tokenLabel(context, summary.frameType)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "어깨 비율",
                            value = summary.shoulderToHipRatio.format(2)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "허리 라인",
                            value = tokenLabel(context, summary.waistDefinition)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "상하체 밸런스",
                            value = tokenLabel(context, summary.upperLowerBalance)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "얼굴형",
                            value = tokenLabel(context, summary.faceShape)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "피부 톤",
                            value = "${tokenLabel(context, summary.skinUndertone)} / ${tokenLabel(context, summary.skinClarity)}"
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "실루엣",
                            value = tokenLabel(context, summary.silhouetteProfile)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "태그 수",
                            value = "${summary.tags.size}개"
                        )
                    }

                    if (summary.tags.isNotEmpty()) {
                        TagSummaryBlock(
                            title = "핵심 태그",
                            values = summary.tags.take(4).map { tokenLabel(context, it) }
                        )
                    }

                    if (summary.bodyRatioTags.isNotEmpty()) {
                        TagSummaryBlock(
                            title = "비율 태그",
                            values = summary.bodyRatioTags.map { tokenLabel(context, it) }
                        )
                    }

                    if (summary.silhouetteTags.isNotEmpty()) {
                        TagSummaryBlock(
                            title = "실루엣 태그",
                            values = summary.silhouetteTags.map { tokenLabel(context, it) }
                        )
                    }

                    Button(
                        onClick = onOpenRecommendations,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.Checkroom, contentDescription = null)
                        Text("추천 상품 확인하기")
                    }
                }
            }
        }
    }
}

