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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
internal fun RecommendationListScreen(
    modifier: Modifier = Modifier,
    summary: ResultSummary?,
    recommendations: List<RecommendationItem>,
    filters: RecommendationFilterState,
    colourOptions: List<String>,
    tagPreferenceWeights: Map<String, Double>,
    onFiltersChange: (RecommendationFilterState) -> Unit,
    onSearch: () -> Unit,
    onSelect: (RecommendationItem) -> Unit,
    onGoAnalysis: () -> Unit
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
            icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
            title = "추천 목록 화면",
            description = "백엔드 추천 API가 들어오면 이 화면의 더미 카드만 실제 데이터 카드로 바꾸면 돼."
        )

        if (summary == null) {
            PlaceholderFeatureCard(
                icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                title = "분석 결과가 필요해",
                description = "추천 목록을 보기 전에 분석 결과 화면에서 분석을 한 번 실행해."
            )
            FilledTonalButton(
                onClick = onGoAnalysis,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("분석 화면으로 이동")
            }
        } else {
            RecommendationFilterCard(
                filters = filters,
                resultCount = recommendations.size,
                colourOptions = colourOptions,
                onFiltersChange = onFiltersChange,
                onSearch = onSearch
            )

            if (recommendations.isEmpty()) {
                PlaceholderFeatureCard(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    title = "조건에 맞는 상품이 없어",
                    description = "가격 범위, 계절, 성별 조건을 조금 넓혀서 다시 검색해봐."
                )
            }

            recommendations.forEach { item ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) },
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SoftPill(text = item.price)
                        }
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = item.styleTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SoftPill(text = item.brandName)
                            SoftPill(text = item.productType)
                            SoftPill(text = item.season)
                            SoftPill(text = item.gender)
                            SoftPill(text = item.baseColour)
                            SoftPill(text = item.usage)
                            SoftPill(text = item.fit)
                            item.userRating?.let { userRating ->
                                SoftPill(text = "내 별점 ${userRating}점")
                            }
                            if (item.totalDwellTimeMs > 0L) {
                                SoftPill(text = "체류 ${item.totalDwellTimeMs / 1000}s")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSelect(item) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("상세보기")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationFilterCard(
    filters: RecommendationFilterState,
    resultCount: Int,
    colourOptions: List<String>,
    onFiltersChange: (RecommendationFilterState) -> Unit,
    onSearch: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("상품 검색 필터", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "서버 DB 필드 기준 검색 조건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SoftPill(text = "${resultCount}개")
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "가격대: ${filters.minPrice.formatWon()} ~ ${filters.maxPrice.formatWon()}",
                    style = MaterialTheme.typography.titleMedium
                )
                RangeSlider(
                    value = filters.minPrice.toFloat()..filters.maxPrice.toFloat(),
                    onValueChange = { value ->
                        onFiltersChange(
                            filters.copy(
                                minPrice = value.start.toInt(),
                                maxPrice = value.endInclusive.toInt()
                            )
                        )
                    },
                    valueRange = 0f..500_000f,
                    steps = 9
                )
            }

            FilterChipRow(
                title = "계절",
                options = listOf("All", "Spring", "Summer", "Fall", "Winter"),
                selected = filters.selectedSeason,
                onSelect = { onFiltersChange(filters.copy(selectedSeason = it)) }
            )

            FilterChipRow(
                title = "성별",
                options = listOf("All", "Men", "Women"),
                selected = filters.selectedGender,
                onSelect = { onFiltersChange(filters.copy(selectedGender = it)) }
            )

            FilterChipRow(
                title = "용도",
                options = listOf("All", "Sports", "Fashion", "Casual"),
                selected = filters.selectedUsage,
                onSelect = { onFiltersChange(filters.copy(selectedUsage = it)) }
            )

            FilterChipRow(
                title = "대표 색상",
                options = listOf("All") + colourOptions,
                selected = filters.selectedBaseColour,
                onSelect = { onFiltersChange(filters.copy(selectedBaseColour = it)) }
            )

            FilterChipRow(
                title = "브랜드",
                options = listOf("All", "Nike", "Studio Sajo"),
                selected = filters.selectedBrandName,
                onSelect = { onFiltersChange(filters.copy(selectedBrandName = it)) }
            )

            FilterChipRow(
                title = "상품 종류",
                options = listOf("All", "Tshirts", "Jackets", "Trousers", "Co-ords"),
                selected = filters.selectedArticleType,
                onSelect = { onFiltersChange(filters.copy(selectedArticleType = it)) }
            )

            FilterChipRow(
                title = "핏",
                options = listOf("All", "Regular Fit", "Slim Fit", "Comfort Fit"),
                selected = filters.selectedFit,
                onSelect = { onFiltersChange(filters.copy(selectedFit = it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = filters.discountedOnly,
                    onCheckedChange = { checked ->
                        onFiltersChange(filters.copy(discountedOnly = checked))
                    }
                )
                Text("할인 상품만", modifier = Modifier.weight(1f))
            }

            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Mock 서버 검색 결과 저장")
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                ChipButton(
                    label = option,
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

private fun Int.formatWon(): String {
    return "%,d원".format(this)
}

@Composable
internal fun RecommendationDetailScreen(
    modifier: Modifier = Modifier,
    item: RecommendationItem?,
    summary: ResultSummary?,
    onDwellMeasured: (RecommendationItem, Long) -> Unit,
    onBackToList: () -> Unit
) {
    val context = LocalContext.current
    val enterTime = remember(item?.id) { System.currentTimeMillis() }

    DisposableEffect(item?.id) {
        onDispose {
            item?.let { selected ->
                onDwellMeasured(selected, System.currentTimeMillis() - enterTime)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (item == null) {
            PlaceholderFeatureCard(
                icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
                title = "선택된 추천이 없어",
                description = "추천 목록 화면에서 카드 하나를 눌러 상세 화면으로 들어와."
            )
            OutlinedButton(
                onClick = onBackToList,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("추천 목록으로 돌아가기")
            }
            return@Column
        }

        ElevatedCard(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SoftPill(text = "추천 상세 화면")
                Text(text = item.title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SoftPill(text = item.price)
                item.userRating?.let { SoftPill(text = "내 별점 ${it}점") }
                if (item.totalDwellTimeMs > 0L) {
                    SoftPill(text = "누적 체류 ${item.totalDwellTimeMs / 1000}s")
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("추천 이유", style = MaterialTheme.typography.titleLarge)
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("스타일 팁", style = MaterialTheme.typography.titleMedium)
                Text(item.styleTip, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("상품 태그", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.matchedTags.forEach { tag ->
                        SoftPill(text = tokenLabel(context, tag))
                    }
                }
                if (summary != null) {
                    HorizontalDivider()
                    Text("현재 분석 기준", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "프레임: ${tokenLabel(context, summary.frameType)} · 허리: ${tokenLabel(context, summary.waistDefinition)} · 상하체: ${tokenLabel(context, summary.upperLowerBalance)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

