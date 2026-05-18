package com.example.mobilecapstone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Delete
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
internal fun HistoryScreen(
    modifier: Modifier = Modifier,
    historyEntries: List<HistoryEntry>,
    onRate: (String, RecommendationItem, Int) -> Unit,
    onSelectRecommendation: (HistoryEntry, RecommendationItem) -> Unit,
    onDeleteRecord: (HistoryEntry) -> Unit,
    onGoAnalysis: () -> Unit
) {
    val context = LocalContext.current
    var pendingDeleteEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var ratingTarget by remember { mutableStateOf<Pair<String, RecommendationItem>?>(null) }
    var expandedRecommendationSets by remember { mutableStateOf(setOf<String>()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            title = "기록 화면",
            description = "이전 분석 기록과 추천 상품, 개인화 피드백을 한곳에서 확인합니다."
        )

        if (historyEntries.isEmpty()) {
            PlaceholderFeatureCard(
                icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
                title = "기록이 아직 없습니다",
                description = "분석 결과 화면에서 분석을 실행하면 기록이 저장됩니다."
            )
            FilledTonalButton(
                onClick = onGoAnalysis,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("분석하러 가기")
            }
        } else {
            historyEntries.forEach { entry ->
                val recordBitmap = remember(entry.imageUri) {
                    entry.imageUri?.let { path -> BitmapFactory.decodeFile(path) }
                }
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { pendingDeleteEntry = entry }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "기록 삭제",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        recordBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Text(entry.imageLabel, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${entry.createdAt} · ${tokenLabel(context, entry.frameType)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = buildString {
                                append("기록 ID: ")
                                append(entry.recordId.take(8))
                                if (entry.heightCm != null || entry.weightKg != null) {
                                    append(" · ")
                                    append(entry.heightCm?.format(1) ?: "-")
                                    append("cm / ")
                                    append(entry.weightKg?.format(1) ?: "-")
                                    append("kg")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                entry.tags.forEach { tag ->
                                    SoftPill(text = tokenLabel(context, tag))
                                }
                            }
                        }
                        if (entry.recommendations.isNotEmpty()) {
                            val recommendationsExpanded = expandedRecommendationSets.contains(entry.recordId)
                            HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "추천 세트",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${entry.recommendations.size}개 상품",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        expandedRecommendationSets =
                                            if (recommendationsExpanded) {
                                                expandedRecommendationSets - entry.recordId
                                            } else {
                                                expandedRecommendationSets + entry.recordId
                                            }
                                    }
                                ) {
                                    Text(if (recommendationsExpanded) "접기" else "펼치기")
                                    Icon(
                                        imageVector = if (recommendationsExpanded) {
                                            Icons.Rounded.KeyboardArrowUp
                                        } else {
                                            Icons.Rounded.KeyboardArrowDown
                                        },
                                        contentDescription = null
                                    )
                                }
                            }
                            if (recommendationsExpanded) {
                                entry.recommendations.forEach { recommendation ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(recommendation.title)
                                                append(" · ")
                                                append(recommendation.price)
                                                recommendation.userRating?.let { rating ->
                                                    append(" · 내 별점 ")
                                                    append(rating)
                                                    append("점")
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(onClick = { onSelectRecommendation(entry, recommendation) }) {
                                            Text("상세")
                                        }
                                        TextButton(onClick = { ratingTarget = entry.recordId to recommendation }) {
                                            Text("별점")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("기록을 삭제할까요?") },
            text = {
                Text(
                    text = "삭제한 분석 기록은 다시 복구할 수 없습니다. 연결된 추천 기록도 함께 삭제됩니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteEntry = null
                        onDeleteRecord(entry)
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) {
                    Text("취소")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    ratingTarget?.let { target ->
        val recordId = target.first
        val item = target.second
        HistoryRatingDialog(
            item = item,
            onDismiss = { ratingTarget = null },
            onSubmit = { rating ->
                onRate(recordId, item, rating)
                ratingTarget = null
            }
        )
    }
}

@Composable
private fun HistoryRatingDialog(
    item: RecommendationItem,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var selectedRating by remember(item.id) { mutableStateOf(item.userRating ?: 5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("별점 주기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..5).forEach { rating ->
                        ChipButton(
                            label = "${rating}점",
                            selected = selectedRating == rating,
                            onClick = { selectedRating = rating }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedRating) }) {
                Text("제출")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
