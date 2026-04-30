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
import androidx.compose.material3.Text
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

    var accountName by remember { mutableStateOf("Capstone User") }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

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
    val recommendations = remember(summary) { buildRecommendationMocks(context, summary) }

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
                onEmailChange = { loginEmail = it },
                onPasswordChange = { loginPassword = it },
                onLogin = {
                    accountName = loginEmail.substringBefore("@").ifBlank { "Capstone User" }
                    currentScreen = AppScreen.Home
                },
                onOpenSignup = {
                    currentScreen = AppScreen.Signup
                }
            )

            AppScreen.Signup -> SignupScreen(
                modifier = Modifier.padding(innerPadding)
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
                        statusMessage = "샘플이 변경되었어. 분석을 다시 실행해."
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
                        statusMessage = "샘플이 변경되었어. 분석을 다시 실행해."
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
                                            createdAt = DateTimeFormatter.ofPattern("MM.dd HH:mm")
                                                .format(LocalDateTime.now()),
                                            assetName = selectedAsset,
                                            frameType = parsed.frameType,
                                            tags = parsed.tags
                                        )
                                    ) + historyEntries
                                }
                                nextState
                            },
                            onFailure = { error -> uiState.failStep("pose", error) }
                        )
                    }
                },
                onOpenRecommendations = { currentScreen = AppScreen.RecommendationList }
            )

            AppScreen.RecommendationList -> RecommendationListScreen(
                modifier = Modifier.padding(innerPadding),
                summary = summary,
                recommendations = recommendations,
                onSelect = {
                    selectedRecommendation = it
                    currentScreen = AppScreen.RecommendationDetail
                },
                onGoAnalysis = { currentScreen = AppScreen.Analysis }
            )

            AppScreen.RecommendationDetail -> RecommendationDetailScreen(
                modifier = Modifier.padding(innerPadding),
                item = selectedRecommendation,
                summary = summary,
                onBackToList = { currentScreen = AppScreen.RecommendationList }
            )

            AppScreen.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                historyEntries = historyEntries,
                onGoAnalysis = { currentScreen = AppScreen.Analysis }
            )

            AppScreen.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                accountName = accountName,
                notificationsEnabled = notificationsEnabled,
                wifiOnlySync = wifiOnlySync,
                onNotificationsChange = { notificationsEnabled = it },
                onWifiOnlySyncChange = { wifiOnlySync = it },
                onLogout = {
                    currentScreen = AppScreen.Login
                    loginPassword = ""
                }
            )
        }
    }
}

