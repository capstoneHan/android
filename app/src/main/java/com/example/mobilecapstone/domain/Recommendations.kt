package com.example.mobilecapstone

import android.content.Context

internal fun buildRecommendationMocks(
    context: Context,
    summary: ResultSummary?,
    filters: RecommendationFilterState = RecommendationFilterState()
): List<RecommendationItem> {
    if (summary == null) return emptyList()

    val shoulderHint = when (summary.shoulderProfile) {
        "broad_relative_to_hip" -> "상체 볼륨이 또렷해서 허리 라인을 살려주는 실루엣이 잘 어울려."
        "narrow_relative_to_hip" -> "상체 비율을 보완할 수 있게 어깨 포인트가 있는 아이템이 좋아."
        else -> "전체 밸런스가 좋아서 미니멀한 정석 실루엣이 잘 맞아."
    }

    val legHint = when (summary.upperLowerBalance) {
        "lower_body_emphasized" -> "하체 비율이 자연스럽게 살아나도록 스트레이트 팬츠와 짧은 상의를 추천해."
        "upper_body_emphasized" -> "상체 비율이 길어 보일 때는 하이웨이스트와 세로 라인 디테일이 균형 보정에 유리해."
        else -> "과한 보정보다 전체 선을 정리하는 쪽이 좋아."
    }

    return mockProductCatalog(context, summary, shoulderHint, legHint).filter { item ->
        item.rawPrice in filters.minPrice..filters.maxPrice &&
            (filters.selectedSeason == "All" || item.season == filters.selectedSeason || item.season == "All") &&
            (filters.selectedGender == "All" || item.gender == filters.selectedGender) &&
            (filters.selectedUsage == "All" || item.usage == filters.selectedUsage) &&
            (filters.selectedBaseColour == "All" || item.baseColour == filters.selectedBaseColour) &&
            (filters.selectedBrandName == "All" || item.brandName == filters.selectedBrandName) &&
            (filters.selectedArticleType == "All" || item.productType == filters.selectedArticleType) &&
            (filters.selectedFit == "All" || item.fit == filters.selectedFit) &&
            (!filters.discountedOnly || item.discountedPrice < item.rawPrice)
    }
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
            description = "$shoulderHint 서버 DB의 price, season, gender, colour, usage 필드를 매핑한 스포츠웨어 예시야.",
            styleTip = "분석 태그와 필터 조건을 백엔드로 보내면 이런 상품 카드로 내려받는 구조가 돼."
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
            description = "$shoulderHint 상체선이 정리되는 부드러운 라벤더 컬러 자켓이야.",
            styleTip = "화이트 이너와 매치하면 앱 전체 톤과도 잘 맞고 데모 화면에서도 보기 좋아."
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
            description = "$legHint 허리선과 다리선을 깨끗하게 정리해주는 기본 팬츠야.",
            styleTip = "촬영 데모용 화면에서도 실루엣 설명이 쉬운 클래식 아이템이야."
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
            description = "현재 프레임 타입 ${tokenLabel(context, summary.frameType)} 기준으로 과하지 않게 체형 밸런스를 맞춰줘.",
            styleTip = "백엔드 추천 API가 붙기 전까지는 이 카드 구조를 그대로 재사용하면 돼."
        )
    )
}
