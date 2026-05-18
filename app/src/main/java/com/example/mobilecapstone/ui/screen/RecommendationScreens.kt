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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
@Composable
internal fun RecommendationListScreen(
    modifier: Modifier = Modifier,
    summary: ResultSummary?,
    recommendations: List<RecommendationItem>,
    filters: RecommendationFilterState,
    colourOptions: List<String>,
    styleTagOptions: List<String>,
    tagPreferenceWeights: Map<String, Double>,
    onFiltersChange: (RecommendationFilterState) -> Unit,
    onSelect: (RecommendationItem) -> Unit,
    onOpenHistory: () -> Unit,
    onGoAnalysis: () -> Unit
) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .padding(bottom = if (summary == null) 0.dp else 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeroCard(
                icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
                title = "추천 목록 화면",
                description = "분석 태그와 검색 조건을 바탕으로 사용자에게 맞는 추천 상품을 확인합니다."
            )

            if (summary == null) {
                PlaceholderFeatureCard(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    title = "분석 결과가 필요합니다",
                    description = "추천 상품을 확인하려면 먼저 사진 분석을 실행해 주세요."
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
                    styleTagOptions = styleTagOptions,
                    onFiltersChange = onFiltersChange
                )

                if (recommendations.isEmpty()) {
                    PlaceholderFeatureCard(
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        title = "조건에 맞는 상품이 없습니다",
                        description = "가격 범위, 계절, 성별 조건을 넓혀 다시 조회해 주세요."
                    )
                }

                recommendations.forEach { item ->
                    Box(modifier = Modifier.fillMaxWidth()) {
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
                                RecommendationNetworkImage(
                                    imageUrl = item.imageUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                )
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
                                            text = localizedProductTitle(context, item),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = localizedProductSubtitle(context, item),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SoftPill(text = item.price)
                                }
                                Text(
                                    text = localizedDescription(item),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = localizedStyleTip(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SoftPill(text = tokenLabel(context, item.brandName))
                                    SoftPill(text = tokenLabel(context, item.productType))
                                    SoftPill(text = tokenLabel(context, item.season))
                                    SoftPill(text = tokenLabel(context, item.gender))
                                    SoftPill(text = tokenLabel(context, item.baseColour))
                                    SoftPill(text = tokenLabel(context, item.usage))
                                    SoftPill(text = tokenLabel(context, item.fit))
                                    item.userRating?.let { userRating ->
                                        SoftPill(text = "내 별점 ${userRating}점")
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
                        if (item.topRecommendation) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                                    .size(13.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error
                            ) {}
                        }
                    }
                }
            }
        }

        if (summary != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                FilledTonalButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("기록 화면에서 추천 세트 확인")
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
    styleTagOptions: List<String>,
    onFiltersChange: (RecommendationFilterState) -> Unit
) {
    var pendingPriceRange by remember(filters.minPrice, filters.maxPrice) {
        mutableStateOf(filters.minPrice.toFloat()..filters.maxPrice.toFloat())
    }
    val pendingMinPrice = pendingPriceRange.start.toInt().roundPriceStep()
    val pendingMaxPrice = pendingPriceRange.endInclusive.toInt().roundPriceStep()

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
                    Text("추천 필터", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "가격, 계절, 색상 등 원하는 조건을 선택해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "현재 조건에 맞는 추천 상품 ${resultCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "가격대 ${pendingMinPrice.formatWon()} - ${pendingMaxPrice.formatWon()}",
                    style = MaterialTheme.typography.titleMedium
                )
                RangeSlider(
                    value = pendingPriceRange,
                    onValueChange = { value ->
                        pendingPriceRange = value
                    },
                    onValueChangeFinished = {
                        onFiltersChange(
                            filters.copy(
                                minPrice = pendingMinPrice.coerceAtMost(pendingMaxPrice),
                                maxPrice = pendingMaxPrice.coerceAtLeast(pendingMinPrice)
                            )
                        )
                    },
                    valueRange = 0f..500_000f
                )
            }

            FilterChipRow(
                title = "계절",
                options = listOf("All", "Spring", "Summer", "Fall", "Winter"),
                selected = filters.selectedSeason,
                onSelect = { option, options ->
                    onFiltersChange(filters.copy(selectedSeason = filters.selectedSeason.toggleFilterOption(option, options)))
                }
            )

            FilterChipRow(
                title = "성별",
                options = listOf("All", "Men", "Women"),
                selected = filters.selectedGender,
                onSelect = { option, options ->
                    onFiltersChange(filters.copy(selectedGender = filters.selectedGender.toggleFilterOption(option, options)))
                }
            )

            FilterChipRow(
                title = "용도",
                options = listOf("All", "Sports", "Fashion", "Casual"),
                selected = filters.selectedUsage,
                onSelect = { option, options ->
                    onFiltersChange(filters.copy(selectedUsage = filters.selectedUsage.toggleFilterOption(option, options)))
                }
            )

            FilterChipRow(
                title = "대표 색상",
                options = listOf("All") + colourOptions,
                selected = filters.selectedBaseColour,
                onSelect = { option, options ->
                    onFiltersChange(filters.copy(selectedBaseColour = filters.selectedBaseColour.toggleFilterOption(option, options)))
                }
            )

            FilterChipRow(
                title = "상품 종류",
                options = listOf("All", "Tshirts", "Shirts", "Jeans", "Trousers", "Shorts", "Track Pants", "Jackets", "Coats"),
                selected = filters.selectedArticleType,
                onSelect = { option, options ->
                    onFiltersChange(filters.copy(selectedArticleType = filters.selectedArticleType.toggleFilterOption(option, options)))
                }
            )

            if (styleTagOptions.isNotEmpty()) {
                FilterChipRow(
                    title = "분석 태그",
                    options = listOf("All") + styleTagOptions,
                    selected = filters.selectedStyleTag,
                    onSelect = { option, options ->
                        onFiltersChange(filters.copy(selectedStyleTag = filters.selectedStyleTag.toggleFilterOption(option, options)))
                    }
                )
            }

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

        }
    }
}

@Composable
private fun FilterChipRow(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onSelect: (String, List<String>) -> Unit
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
                    selected = selected.contains(option),
                    onClick = { onSelect(option, options) }
                )
            }
        }
    }
}

private fun Int.formatWon(): String {
    return "%,d원".format(this)
}

private fun Int.roundPriceStep(): Int {
    return ((this + 500) / 1_000 * 1_000).coerceIn(0, 500_000)
}

private fun localizedProductTitle(context: android.content.Context, item: RecommendationItem): String {
    val color = tokenLabel(context, item.baseColour)
    val type = tokenLabel(context, item.productType)
    val id = item.id.takeIf { it.isNotBlank() } ?: item.title
    return listOf(color, type)
        .filter { it.isNotBlank() && it != "NA" && it != "All" }
        .joinToString(" ")
        .ifBlank { item.title.ifBlank { "추천 상품" } } + " #$id"
}

private fun localizedProductSubtitle(context: android.content.Context, item: RecommendationItem): String {
    return listOf(item.brandName, item.productType, item.gender, item.baseColour, item.season)
        .map { tokenLabel(context, it) }
        .filter { it.isNotBlank() && it != "All" && it != "NA" }
        .joinToString(" - ")
}

private fun localizedDescription(item: RecommendationItem): String {
    if (item.description.isBlank() || item.description == "Recommended from backend product catalog.") {
        return "분석 태그와 개인화 가중치를 기준으로 추천된 상품입니다."
    }
    return item.description
}

private fun localizedStyleTip(item: RecommendationItem): String {
    if (item.styleTip.isBlank() || item.styleTip == "Matched with analysis tags and local personalization weights.") {
        return "색상, 계절, 상품 종류가 현재 분석 결과와 잘 맞는 후보입니다."
    }
    return item.styleTip
}

@Composable
private fun RecommendationNetworkImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        bitmap = null
        if (imageUrl.isBlank()) return@LaunchedEffect

        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL(imageUrl).openStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Checkroom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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

    // TODO: When backend product detail is ready, load the canonical product
    // detail by item.id before rendering this screen. Keep dwell/rating local.
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
                title = "선택된 추천 상품이 없습니다",
                description = "추천 목록 화면에서 상품을 선택해 상세 정보를 확인해 주세요."
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
                RecommendationNetworkImage(
                    imageUrl = item.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(22.dp))
                )
                SoftPill(text = "추천 상세 화면")
                Text(text = localizedProductTitle(context, item), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = localizedProductSubtitle(context, item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SoftPill(text = item.price)
                item.userRating?.let { SoftPill(text = "내 별점 ${it}점") }
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
                Text(localizedDescription(item), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("스타일 팁", style = MaterialTheme.typography.titleMedium)
                Text(localizedStyleTip(item), style = MaterialTheme.typography.bodyMedium)
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

