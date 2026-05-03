package com.example.mobilecapstone

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FashionCapstoneApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.Login) }
    var uiState by remember { mutableStateOf(PipelineUiState()) }
    var selectedRecommendation by remember { mutableStateOf<RecommendationItem?>(null) }
    var historyEntries by remember { mutableStateOf(listOf<HistoryEntry>()) }

    var accountName by remember { mutableStateOf("사용자") }

    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

    var signupUsername by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupPasswordConfirm by remember { mutableStateOf("") }
    var signupHeight by remember { mutableStateOf("") }
    var signupWeight by remember { mutableStateOf("") }

    var authMessage by remember { mutableStateOf<String?>(null) }
    var authLoading by remember { mutableStateOf(false) }

    var notificationsEnabled by remember { mutableStateOf(true) }
    var wifiOnlySync by remember { mutableStateOf(true) }

    val rootTabs = remember {
        listOf(
            NavItem(AppScreen.Home, "홈") {
                Icon(Icons.Rounded.Home, contentDescription = null)
            },
            NavItem(AppScreen.Capture, "촬영") {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
            },
            NavItem(AppScreen.Analysis, "분석") {
                Icon(Icons.Rounded.Insights, contentDescription = null)
            },
            NavItem(AppScreen.History, "기록") {
                Icon(Icons.Rounded.History, contentDescription = null)
            },
            NavItem(AppScreen.Settings, "설정") {
                Icon(Icons.Rounded.Settings, contentDescription = null)
            }
        )
    }

    LaunchedEffect(uiState.selectedAsset) {
        val sample = withContext(Dispatchers.IO) {
            PoseExtractionPipeline.loadSampleBitmap(context, uiState.selectedAsset)
        }
        uiState = uiState.copy(sampleBitmap = sample)
    }

    val summary = remember(uiState.jsonOutput, uiState.featureJsonOutput) {
        ResultSummary.from(
            rawJson = uiState.jsonOutput,
            featureJson = uiState.featureJsonOutput
        )
    }

    val recommendations = remember(summary) {
        buildRecommendationMocks(context, summary)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentScreen != AppScreen.Login) {
                AppTopBar(
                    title = currentScreen.title,
                    showBack = currentScreen == AppScreen.Signup ||
                            currentScreen == AppScreen.RecommendationList ||
                            currentScreen == AppScreen.RecommendationDetail,
                    onBack = {
                        currentScreen = when (currentScreen) {
                            AppScreen.Signup -> AppScreen.Login
                            AppScreen.RecommendationDetail -> AppScreen.RecommendationList
                            AppScreen.RecommendationList -> AppScreen.Analysis
                            else -> AppScreen.Home
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen.showBottomBar) {
                FashionBottomBar(
                    rootTabs = rootTabs,
                    currentScreen = currentScreen,
                    onSelectScreen = { currentScreen = it }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.Login -> LoginScreen(
                email = loginEmail,
                password = loginPassword,
                isLoading = authLoading,
                message = authMessage,
                onEmailChange = {
                    loginEmail = it
                    authMessage = null
                },
                onPasswordChange = {
                    loginPassword = it
                    authMessage = null
                },
                onLogin = {
                    val validationMessage = validateLoginInput(
                        email = loginEmail,
                        password = loginPassword
                    )

                    if (validationMessage != null) {
                        authMessage = validationMessage
                        return@LoginScreen
                    }

                    scope.launch {
                        authLoading = true
                        authMessage = null

                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                AuthClient.api.login(
                                    LoginRequest(
                                        email = loginEmail.trim(),
                                        password = loginPassword
                                    )
                                )
                            }
                        }

                        result.fold(
                            onSuccess = { response ->
                                authLoading = false
                                authMessage = response.message

                                if (response.success) {
                                    accountName = response.username
                                        ?: loginEmail.substringBefore("@").ifBlank { "사용자" }

                                    loginPassword = ""
                                    currentScreen = AppScreen.Home
                                }
                            },
                            onFailure = { error ->
                                authLoading = false
                                authMessage = error.message ?: "로그인 요청에 실패했습니다."
                            }
                        )
                    }
                },
                onOpenSignup = {
                    authMessage = null
                    currentScreen = AppScreen.Signup
                }
            )

            AppScreen.Signup -> SignupScreen(
                modifier = Modifier.padding(innerPadding),
                username = signupUsername,
                email = signupEmail,
                password = signupPassword,
                passwordConfirm = signupPasswordConfirm,
                height = signupHeight,
                weight = signupWeight,
                isLoading = authLoading,
                message = authMessage,
                onUsernameChange = {
                    signupUsername = it
                    authMessage = null
                },
                onEmailChange = {
                    signupEmail = it
                    authMessage = null
                },
                onPasswordChange = {
                    signupPassword = it
                    authMessage = null
                },
                onPasswordConfirmChange = {
                    signupPasswordConfirm = it
                    authMessage = null
                },
                onHeightChange = {
                    signupHeight = it
                    authMessage = null
                },
                onWeightChange = {
                    signupWeight = it
                    authMessage = null
                },
                onSignup = {
                    val validationMessage = validateSignupInput(
                        username = signupUsername,
                        email = signupEmail,
                        password = signupPassword,
                        passwordConfirm = signupPasswordConfirm,
                        height = signupHeight,
                        weight = signupWeight
                    )

                    if (validationMessage != null) {
                        authMessage = validationMessage
                        return@SignupScreen
                    }

                    scope.launch {
                        authLoading = true
                        authMessage = null

                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                AuthClient.api.register(
                                    RegisterRequest(
                                        username = signupUsername.trim(),
                                        password = signupPassword,
                                        email = signupEmail.trim(),
                                        height = signupHeight.toInt(),
                                        weight = signupWeight.toInt()
                                    )
                                )
                            }
                        }

                        result.fold(
                            onSuccess = { response ->
                                authLoading = false
                                authMessage = response.message

                                if (response.success) {
                                    loginEmail = signupEmail.trim()
                                    loginPassword = ""

                                    signupPassword = ""
                                    signupPasswordConfirm = ""

                                    currentScreen = AppScreen.Login
                                }
                            },
                            onFailure = { error ->
                                authLoading = false
                                authMessage = error.message ?: "회원가입 요청에 실패했습니다."
                            }
                        )
                    }
                },
                onBackToLogin = {
                    authMessage = null
                    currentScreen = AppScreen.Login
                }
            )

            AppScreen.Home -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                accountName = accountName,
                selectedAsset = uiState.selectedAsset,
                summary = summary,
                historyCount = historyEntries.size,
                onStartCapture = { currentScreen = AppScreen.Capture },
                onOpenAnalysis = { currentScreen = AppScreen.Analysis },
                onOpenRecommendations = { currentScreen = AppScreen.RecommendationList },
                onOpenHistory = { currentScreen = AppScreen.History }
            )

            AppScreen.Capture -> CaptureScreen(
                modifier = Modifier.padding(innerPadding),
                selectedAsset = uiState.selectedAsset,
                sampleBitmap = uiState.sampleBitmap,
                sampleOptions = PoseExtractionPipeline.sampleAssets,
                onSelect = { assetName ->
                    uiState = uiState.copy(
                        selectedAsset = assetName,
                        jsonOutput = "",
                        featureJsonOutput = "",
                        outputFilePath = "",
                        statusMessage = "샘플이 변경되었습니다. 분석을 다시 실행해 주세요."
                    )
                },
                onGoAnalysis = { currentScreen = AppScreen.Analysis }
            )

            AppScreen.Analysis -> AnalysisResultScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                summary = summary,
                sampleOptions = PoseExtractionPipeline.sampleAssets,
                onSelectAsset = { assetName ->
                    uiState = uiState.copy(
                        selectedAsset = assetName,
                        jsonOutput = "",
                        featureJsonOutput = "",
                        outputFilePath = "",
                        statusMessage = "샘플이 변경되었습니다. 분석을 다시 실행해 주세요."
                    )
                },
                onRun = {
                    if (uiState.isRunning) return@AnalysisResultScreen

                    scope.launch {
                        uiState = uiState.beginRun()

                        val selectedAsset = uiState.selectedAsset

                        val outcome = withContext(Dispatchers.IO) {
                            runCatching {
                                PoseExtractionPipeline.runPoseExtraction(
                                    context = context,
                                    assetName = selectedAsset,
                                    onStepStatusChanged = { stepId, status ->
                                        scope.launch {
                                            uiState = uiState.updateStepStatus(stepId, status)
                                        }
                                    }
                                )
                            }
                        }

                        uiState = outcome.fold(
                            onSuccess = { result ->
                                val nextState = uiState.completeStep(result)

                                ResultSummary.from(
                                    result.jsonOutput,
                                    result.featureJsonOutput
                                )?.let { parsed ->
                                    historyEntries = listOf(
                                        HistoryEntry(
                                            createdAt = DateTimeFormatter
                                                .ofPattern("MM.dd HH:mm")
                                                .format(LocalDateTime.now()),
                                            assetName = selectedAsset,
                                            frameType = parsed.frameType,
                                            tags = parsed.tags
                                        )
                                    ) + historyEntries
                                }

                                nextState
                            },
                            onFailure = { error ->
                                uiState.failStep("pose", error)
                            }
                        )
                    }
                },
                onOpenRecommendations = {
                    currentScreen = AppScreen.RecommendationList
                }
            )

            AppScreen.RecommendationList -> RecommendationListScreen(
                modifier = Modifier.padding(innerPadding),
                summary = summary,
                recommendations = recommendations,
                onSelect = {
                    selectedRecommendation = it
                    currentScreen = AppScreen.RecommendationDetail
                },
                onGoAnalysis = {
                    currentScreen = AppScreen.Analysis
                }
            )

            AppScreen.RecommendationDetail -> RecommendationDetailScreen(
                modifier = Modifier.padding(innerPadding),
                item = selectedRecommendation,
                summary = summary,
                onBackToList = {
                    currentScreen = AppScreen.RecommendationList
                }
            )

            AppScreen.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                historyEntries = historyEntries,
                onGoAnalysis = {
                    currentScreen = AppScreen.Analysis
                }
            )

            AppScreen.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                accountName = accountName,
                notificationsEnabled = notificationsEnabled,
                wifiOnlySync = wifiOnlySync,
                onNotificationsChange = {
                    notificationsEnabled = it
                },
                onWifiOnlySyncChange = {
                    wifiOnlySync = it
                },
                onLogout = {
                    currentScreen = AppScreen.Login
                    loginPassword = ""
                    authMessage = null
                }
            )
        }
    }
}

private fun validateLoginInput(
    email: String,
    password: String
): String? {
    val trimmedEmail = email.trim()

    return when {
        trimmedEmail.isBlank() -> "이메일을 입력해 주세요."
        !trimmedEmail.contains("@") -> "올바른 이메일 형식이 아닙니다."
        password.isBlank() -> "비밀번호를 입력해 주세요."
        else -> null
    }
}

private fun validateSignupInput(
    username: String,
    email: String,
    password: String,
    passwordConfirm: String,
    height: String,
    weight: String
): String? {
    val parsedHeight = height.toIntOrNull()
    val parsedWeight = weight.toIntOrNull()

    return when {
        username.trim().isBlank() -> "사용자 이름을 입력해 주세요."
        email.trim().isBlank() -> "이메일을 입력해 주세요."
        !email.trim().contains("@") -> "올바른 이메일 형식이 아닙니다."
        password.isBlank() -> "비밀번호를 입력해 주세요."
        password.length < 4 -> "비밀번호는 4자 이상으로 입력해 주세요."
        password != passwordConfirm -> "비밀번호 확인이 일치하지 않습니다."
        parsedHeight == null -> "키는 숫자로 입력해 주세요."
        parsedHeight !in 100..230 -> "키는 100~230cm 범위로 입력해 주세요."
        parsedWeight == null -> "몸무게는 숫자로 입력해 주세요."
        parsedWeight !in 30..200 -> "몸무게는 30~200kg 범위로 입력해 주세요."
        else -> null
    }
}