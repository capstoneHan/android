package com.example.mobilecapstone

import android.content.Context

internal fun buildRecommendationMocks(
    context: Context,
    summary: ResultSummary?,
    filters: RecommendationFilterState = RecommendationFilterState()
): List<RecommendationItem> {
    if (summary == null) return emptyList()

    val shoulderHint = when (summary.shoulderProfile) {
        "broad_relative_to_hip" -> "상체 비율을 정돈할 수 있는 깔끔한 실루엣의 상품입니다."
        "narrow_relative_to_hip" -> "어깨선을 자연스럽게 보완할 수 있는 구조적인 상품입니다."
        else -> "전체적인 균형을 유지하기 좋은 미니멀한 스타일의 상품입니다."
    }

    val legHint = when (summary.upperLowerBalance) {
        "lower_body_emphasized" -> "하체 비율을 자연스럽게 정돈할 수 있는 여유 있는 핏을 추천합니다."
        "upper_body_emphasized" -> "하이웨이스트 또는 세로 라인 디테일로 균형을 보완할 수 있습니다."
        else -> "과한 보정보다는 전체 라인을 정돈하는 스타일을 추천합니다."
    }

    return mockProductCatalog(context, summary, shoulderHint, legHint).filter { item ->
        item.matchesRecommendationFilters(filters)
    }
}

internal fun filterRecommendationItems(
    items: List<RecommendationItem>,
    filters: RecommendationFilterState
): List<RecommendationItem> {
    return items.filter { item -> item.matchesRecommendationFilters(filters) }
}

private fun RecommendationItem.matchesRecommendationFilters(filters: RecommendationFilterState): Boolean {
    return rawPrice in filters.minPrice..filters.maxPrice &&
        (selectedSeasonMatches(filters) || season == "All") &&
        filters.selectedGender.containsFilterValue(gender) &&
        filters.selectedUsage.containsFilterValue(usage) &&
        filters.selectedBaseColour.containsFilterValue(baseColour) &&
        filters.selectedBrandName.containsFilterValue(brandName) &&
        filters.selectedArticleType.containsFilterValue(productType) &&
        filters.selectedStyleTag.containsAnyFilterValue(matchedTags + productTags) &&
        filters.selectedFit.containsFilterValue(fit) &&
        (!filters.discountedOnly || discountedPrice < rawPrice)
}

private fun RecommendationItem.selectedSeasonMatches(filters: RecommendationFilterState): Boolean {
    return filters.selectedSeason.containsFilterValue(season)
}

private fun mockProductCatalog(
    context: Context,
    summary: ResultSummary,
    shoulderHint: String,
    legHint: String
): List<RecommendationItem> {
    return listOf(
        RecommendationItem(
            id = "1163",
            title = "Nike Sahara Team India Fanwear Round Neck Jersey",
            subtitle = "Nike · Tshirts · Men · Blue",
            price = "89,500원",
            rawPrice = 89_500,
            discountedPrice = 89_500,
            brandName = "Nike",
            season = "Summer",
            gender = "Men",
            baseColour = "Blue",
            usage = "Sports",
            rating = 1,
            productType = "Tshirts",
            fit = "Regular Fit",
            imageUrl = "http://assets.myntassets.com/h_480,q_95,w_360/v1/images/style/properties/Nike-Sahara-Team-India-Fanwear-Round-Neck-Jersey_2d27392cc7d7730e8fee0755fd41d30c_images.jpg",
            matchedTags = (summary.tags.take(2) + listOf("sports_style", "regular_fit_friendly")).distinct(),
            matchScore = 0.87,
            description = "$shoulderHint 분석 태그와 상품 메타데이터가 함께 매칭된 스포츠웨어입니다.",
            styleTip = "활동성이 필요한 상황에서 편하게 착용할 수 있는 추천 상품입니다."
        ),
        RecommendationItem(
            id = "local-1",
            title = "Soft Lavender Tailored Jacket",
            subtitle = "Studio Sajo · Jackets · Women · Lavender",
            price = "49,000원",
            rawPrice = 49_000,
            discountedPrice = 39_000,
            brandName = "Studio Sajo",
            season = "Winter",
            gender = "Women",
            baseColour = "Lavender",
            usage = "Fashion",
            rating = 4,
            productType = "Jackets",
            fit = "Slim Fit",
            matchedTags = (summary.tags.take(3) + listOf("structured_top_candidate")).distinct(),
            matchScore = 0.82,
            description = "$shoulderHint 상체 라인을 정돈해 주는 부드러운 컬러의 재킷입니다.",
            styleTip = "데님 또는 슬랙스와 함께 매치하면 깔끔한 데일리 스타일을 연출할 수 있습니다."
        ),
        RecommendationItem(
            id = "local-2",
            title = "Clean White Straight Slacks",
            subtitle = "Studio Sajo · Trousers · Unisex · White",
            price = "27,000원",
            rawPrice = 27_000,
            discountedPrice = 27_000,
            brandName = "Studio Sajo",
            season = "All",
            gender = "Unisex",
            baseColour = "White",
            usage = "Casual",
            rating = 5,
            productType = "Trousers",
            fit = "Regular Fit",
            matchedTags = (summary.tags.takeLast(3) + listOf("relaxed_fit_friendly", "leg_lengthening_recommended")).distinct(),
            matchScore = 0.79,
            description = "$legHint 허리선과 다리 라인을 깔끔하게 정리해 주는 기본 팬츠입니다.",
            styleTip = "다양한 상의와 조합하기 쉬워 반복 착용에 적합한 아이템입니다."
        ),
        RecommendationItem(
            id = "local-3",
            title = "Minimal Knit Set-up",
            subtitle = "Studio Sajo · Co-ords · Women · Beige",
            price = "38,000원",
            rawPrice = 38_000,
            discountedPrice = 32_000,
            brandName = "Studio Sajo",
            season = "Winter",
            gender = "Women",
            baseColour = "Beige",
            usage = "Fashion",
            rating = 3,
            productType = "Co-ords",
            fit = "Comfort Fit",
            matchedTags = (summary.tags + listOf("comfort_fit_friendly")).distinct().take(5),
            matchScore = 0.74,
            description = "현재 프레임 타입 ${tokenLabel(context, summary.frameType)} 기준으로 체형 밸런스를 자연스럽게 맞춰 주는 상품입니다.",
            styleTip = "부드러운 니트 소재와 여유 있는 핏으로 편안한 스타일을 구성할 수 있습니다."
        )
    )
}
