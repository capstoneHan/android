package com.example.mobilecapstone

import android.content.Context
internal fun buildRecommendationMocks(context: Context, summary: ResultSummary?): List<RecommendationItem> {
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

    return listOf(
        RecommendationItem(
            id = "1",
            title = "Soft Lavender Tailored Jacket",
            subtitle = "Studio Sajo · 테일러드 아우터",
            price = "₩89,000",
            description = "$shoulderHint 상체선이 정리되는 부드러운 라벤더 컬러 자켓이야.",
            styleTip = "화이트 이너와 매치하면 앱 전체 톤과도 잘 맞고 데모 화면에서도 보기 좋아."
        ),
        RecommendationItem(
            id = "2",
            title = "Clean White Straight Slacks",
            subtitle = "Studio Sajo · 스트레이트 팬츠",
            price = "₩59,000",
            description = "$legHint 허리선과 다리선을 깨끗하게 정리해주는 기본 팬츠야.",
            styleTip = "촬영 데모용 화면에서도 실루엣 설명이 쉬운 클래식 아이템이야."
        ),
        RecommendationItem(
            id = "3",
            title = "Minimal Knit Set-up",
            subtitle = "Studio Sajo · 니트 세트업",
            price = "₩76,000",
            description = "현재 프레임 타입 ${tokenLabel(context, summary.frameType)} 기준으로 과하지 않게 체형 밸런스를 맞춰줘.",
            styleTip = "백엔드 추천 API가 붙기 전까지는 이 카드 구조를 그대로 재사용하면 돼."
        )
    )
}

