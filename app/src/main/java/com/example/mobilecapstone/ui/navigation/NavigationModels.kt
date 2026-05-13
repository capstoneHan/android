package com.example.mobilecapstone

import androidx.compose.runtime.Composable
internal enum class AppScreen(
    val title: String,
    val showBottomBar: Boolean
) {
    Login("로그인", false),
    Signup("회원가입", false),
    Home("홈", true),
    Capture("촬영", true),
    Analysis("분석 결과", true),
    RecommendationList("추천 목록", false),
    RecommendationDetail("추천 상세", false),
    History("히스토리", true),
    Settings("설정", true),
    ProfileEdit("내 정보 수정", false)
}

internal data class NavItem(
    val screen: AppScreen,
    val label: String,
    val icon: @Composable () -> Unit
)

