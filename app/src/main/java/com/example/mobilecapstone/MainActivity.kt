package com.example.mobilecapstone

import androidx.compose.material3.ExperimentalMaterial3Api
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.mobilecapstone.ui.theme.MobileCapstoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileCapstoneTheme {
                FashionCapstoneApp()
            }
        }
    }
}

private enum class AppScreen(
    val title: String,
    val showBottomBar: Boolean
) {
    Login("로그인", false),
    Home("홈", true),
    Capture("촬영", true),
    Analysis("분석 결과", true),
    RecommendationList("추천 목록", false),
    RecommendationDetail("추천 상세", false),
    History("히스토리", true),
    Settings("설정", true)
}

private data class NavItem(
    val screen: AppScreen,
    val label: String,
    val icon: @Composable () -> Unit
)

private data class RecommendationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val description: String,
    val styleTip: String
)

private data class HistoryEntry(
    val createdAt: String,
    val assetName: String,
    val frameType: String,
    val tags: List<String>
)

@Composable
private fun FashionCapstoneApp() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
    val recommendations = remember(summary) { buildRecommendationMocks(summary) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentScreen != AppScreen.Login) {
                AppTopBar(
                    title = currentScreen.title,
                    showBack = currentScreen == AppScreen.RecommendationList ||
                            currentScreen == AppScreen.RecommendationDetail,
                    onBack = {
                        currentScreen = when (currentScreen) {
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
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    rootTabs.forEach { item ->
                        NavigationBarItem(
                            selected = currentScreen == item.screen,
                            onClick = { currentScreen = item.screen },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
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
                        statusMessage = "샘플이 변경되었어. 분석을 다시 실행해."
                    )
                },
                onGoAnalysis = { currentScreen = AppScreen.Analysis }
            )

            AppScreen.Analysis -> AnalysisResultScreen(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                summary = summary,
                onRun = {
                    if (uiState.isRunning) return@AnalysisResultScreen
                    scope.launch {
                        uiState = uiState.startStep("pose")
                        val selectedAsset = uiState.selectedAsset
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching {
                                PoseExtractionPipeline.runPoseExtraction(
                                    context = context,
                                    assetName = selectedAsset
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
                                            assetName = selectedAsset.substringBeforeLast("."),
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
                onCopyRaw = {
                    clipboardManager.setText(AnnotatedString(uiState.displayOutput))
                },
                onCopyFeature = {
                    clipboardManager.setText(AnnotatedString(uiState.displayFeatureOutput))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "뒤로 가기"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun LoginScreen(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sajo Fit",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "온디바이스 체형 분석과 패션 추천을 위한 캡스톤 앱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "팀 로그인",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("이메일") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("비밀번호") },
                        singleLine = true
                    )
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("앱 시작하기")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    accountName: String,
    selectedAsset: String,
    summary: ResultSummary?,
    historyCount: Int,
    onStartCapture: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenRecommendations: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SoftPill(text = "환영해, ${humanizeToken(accountName)}")
                Text(
                    text = "오늘의 스타일 분석을 시작해볼까?",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "현재 테스트 입력은 ${selectedAsset.substringBeforeLast(".")} 샘플로 설정되어 있어.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartCapture,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("촬영 시작")
                    }
                    FilledTonalButton(
                        onClick = onOpenAnalysis,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("분석 보기")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "최근 프레임",
                value = summary?.frameType?.let(::humanizeToken) ?: "없음"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "추천 상태",
                value = if (summary == null) "준비 전" else "생성 가능"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "세션 기록",
                value = "${historyCount}건"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "태그 수",
                value = "${summary?.tags?.size ?: 0}개"
            )
        }

        SectionTitle("빠른 이동")
        QuickActionCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "CaptureScreen",
            description = "카메라 연결 전 단계지만 샘플 선택과 촬영 흐름을 미리 점검할 수 있어.",
            buttonLabel = "촬영 화면 열기",
            onClick = onStartCapture
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
            title = "AnalysisResultScreen",
            description = "현재 구현된 포즈 추출 기능과 JSON 결과를 앱다운 화면으로 확인해.",
            buttonLabel = "분석 화면 열기",
            onClick = onOpenAnalysis
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
            title = "RecommendationListScreen",
            description = "백엔드 연결 전까지는 더미 추천 카드로 전체 플로우를 검증할 수 있어.",
            buttonLabel = "추천 목록 보기",
            onClick = onOpenRecommendations
        )
        QuickActionCard(
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            title = "HistoryScreen",
            description = "세션 중 실행한 분석 결과를 리스트 형태로 쌓아보는 껍데기 화면이야.",
            buttonLabel = "기록 보기",
            onClick = onOpenHistory
        )
    }
}

@Composable
private fun CaptureScreen(
    modifier: Modifier = Modifier,
    selectedAsset: String,
    sampleBitmap: Bitmap?,
    sampleOptions: List<String>,
    onSelect: (String) -> Unit,
    onGoAnalysis: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "CaptureScreen",
            description = "실제 CameraX 연결 전까지는 샘플 이미지 선택으로 촬영 흐름을 대체해 둘 수 있어."
        )

        if (sampleBitmap != null) {
            ElevatedCard(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "선택된 테스트 이미지",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = selectedAsset,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Image(
                        bitmap = sampleBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "테스트 입력 선택",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "현재 선택: ${selectedAsset.substringBeforeLast(".")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sampleOptions.forEach { asset ->
                        ChipButton(
                            label = asset.substringBeforeLast("."),
                            selected = asset == selectedAsset,
                            onClick = { onSelect(asset) }
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onGoAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("선택한 샘플로 분석 화면 이동")
                }
            }
        }

        PlaceholderFeatureCard(
            icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
            title = "카메라 연결 예정",
            description = "여기에 CameraX 프리뷰, 촬영 버튼, 갤러리 선택 버튼을 붙이면 돼."
        )
        PlaceholderFeatureCard(
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = "촬영 가이드 예정",
            description = "전신이 화면 안에 들어오도록 안내하는 오버레이와 품질 체크 영역을 넣을 자리야."
        )
    }
}

@Composable
private fun AnalysisResultScreen(
    modifier: Modifier = Modifier,
    uiState: PipelineUiState,
    summary: ResultSummary?,
    onRun: () -> Unit,
    onCopyRaw: () -> Unit,
    onCopyFeature: () -> Unit,
    onOpenRecommendations: () -> Unit
) {
    var showRawJson by remember { mutableStateOf(false) }
    var showFeatureJson by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
            title = "AnalysisResultScreen",
            description = "현재 프로젝트의 실제 포즈 추출 기능을 그대로 유지하면서 앱다운 분석 화면으로 정리했어."
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
                    Text("선택된 이미지", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = uiState.selectedAsset,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                    StepStatus.RUNNING -> "실행 중"
                                    StepStatus.COMPLETED -> "완료"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SoftPill(text = step.status.name)
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
                            Text("포즈 추출 실행 중")
                        }
                    } else {
                        Text(
                            if ("pose" in uiState.completedSteps) {
                                "포즈 추출 다시 실행"
                            } else {
                                "포즈 추출 시작"
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
                        text = "아직 분석 결과가 없어. 위 버튼을 눌러 먼저 포즈 추출을 실행해.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "프레임",
                            value = humanizeToken(summary.frameType)
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
                            value = humanizeToken(summary.waistDefinition)
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = "다리 밸런스",
                            value = humanizeToken(summary.legBalance)
                        )
                    }

                    if (summary.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            summary.tags.forEach { tag ->
                                SoftPill(text = humanizeToken(tag))
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenRecommendations,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("추천 목록 화면으로 이동")
                    }
                }
            }
        }

        JsonCard(
            title = "원본 파이프라인 JSON",
            visible = showRawJson,
            value = uiState.displayOutput,
            onToggle = { showRawJson = !showRawJson },
            onCopy = onCopyRaw
        )

        JsonCard(
            title = "정규화 피처 JSON",
            visible = showFeatureJson,
            value = uiState.displayFeatureOutput,
            onToggle = { showFeatureJson = !showFeatureJson },
            onCopy = onCopyFeature
        )
    }
}

@Composable
private fun RecommendationListScreen(
    modifier: Modifier = Modifier,
    summary: ResultSummary?,
    recommendations: List<RecommendationItem>,
    onSelect: (RecommendationItem) -> Unit,
    onGoAnalysis: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
            title = "RecommendationListScreen",
            description = "백엔드 추천 API가 들어오면 이 화면의 더미 카드만 실제 데이터 카드로 바꾸면 돼."
        )

        if (summary == null) {
            PlaceholderFeatureCard(
                icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                title = "분석 결과가 필요해",
                description = "추천 목록을 보기 전에 AnalysisResultScreen에서 포즈 추출을 한 번 실행해."
            )
            FilledTonalButton(
                onClick = onGoAnalysis,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("분석 화면으로 이동")
            }
        } else {
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
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationDetailScreen(
    modifier: Modifier = Modifier,
    item: RecommendationItem?,
    summary: ResultSummary?,
    onBackToList: () -> Unit
) {
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
                SoftPill(text = "RecommendationDetailScreen")
                Text(text = item.title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SoftPill(text = item.price)
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
                if (summary != null) {
                    HorizontalDivider()
                    Text("현재 분석 기준", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "프레임: ${humanizeToken(summary.frameType)} · 허리: ${humanizeToken(summary.waistDefinition)} · 다리: ${humanizeToken(summary.legBalance)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier = Modifier,
    historyEntries: List<HistoryEntry>,
    onGoAnalysis: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.History, contentDescription = null) },
            title = "HistoryScreen",
            description = "백엔드 저장이 붙기 전에도 세션 히스토리를 미리 확인할 수 있는 화면이야."
        )

        if (historyEntries.isEmpty()) {
            PlaceholderFeatureCard(
                icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
                title = "기록이 아직 없어",
                description = "AnalysisResultScreen에서 분석을 실행하면 세션 히스토리가 여기에 추가돼."
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
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(entry.assetName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${entry.createdAt} · ${humanizeToken(entry.frameType)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                entry.tags.forEach { tag ->
                                    SoftPill(text = humanizeToken(tag))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    accountName: String,
    notificationsEnabled: Boolean,
    wifiOnlySync: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onWifiOnlySyncChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeroCard(
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            title = "SettingsScreen",
            description = "동기화, 알림, 계정 관련 껍데기를 미리 잡아두면 백엔드 붙일 때 빨라져."
        )

        ElevatedCard(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("계정", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = humanizeToken(accountName),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider()
                SettingRow(
                    title = "알림 받기",
                    description = "분석 완료나 추천 업데이트 알림",
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationsChange
                )
                SettingRow(
                    title = "Wi‑Fi에서만 동기화",
                    description = "오프라인 결과 업로드 시 셀룰러 사용 제한",
                    checked = wifiOnlySync,
                    onCheckedChange = onWifiOnlySyncChange
                )
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("로그아웃")
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScreenHeroCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(text = title, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceholderFeatureCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun JsonCard(
    title: String,
    visible: Boolean,
    value: String,
    onToggle: () -> Unit,
    onCopy: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onToggle) {
                        Text(if (visible) "숨기기" else "보기")
                    }
                    TextButton(onClick = onCopy) {
                        Text("복사")
                    }
                }
            }
            if (visible) {
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        text = value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    ElevatedCard(
        modifier = modifier.wrapContentHeight(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private data class ResultSummary(
    val landmarkCount: Int,
    val shoulderToHipRatio: Double,
    val frameType: String,
    val waistDefinition: String,
    val shoulderProfile: String,
    val legBalance: String,
    val tags: List<String>
) {
    companion object {
        fun from(rawJson: String, featureJson: String): ResultSummary? {
            if (rawJson.isBlank() || featureJson.isBlank()) return null
            return runCatching {
                val raw = JSONObject(rawJson)
                val feature = JSONObject(featureJson)

                val pose = raw.optJSONObject("pose")
                val derived = pose?.optJSONObject("derived_metrics")
                val silhouette = feature.optJSONObject("silhouette_features")
                val bodyFrame = feature.optJSONObject("body_frame")

                ResultSummary(
                    landmarkCount = pose?.optInt("landmark_count", 0) ?: 0,
                    shoulderToHipRatio = derived?.optDouble("shoulder_to_hip_ratio", 0.0) ?: 0.0,
                    frameType = silhouette?.optString("frame_type", "unknown") ?: "unknown",
                    waistDefinition = silhouette?.optString("waist_definition", "unknown") ?: "unknown",
                    shoulderProfile = bodyFrame?.optString("shoulder_profile", "unknown") ?: "unknown",
                    legBalance = bodyFrame?.optString("leg_balance", "unknown") ?: "unknown",
                    tags = jsonArrayToList(feature.optJSONArray("style_tags"))
                )
            }.getOrNull()
        }
    }
}

private fun buildRecommendationMocks(summary: ResultSummary?): List<RecommendationItem> {
    if (summary == null) return emptyList()

    val shoulderHint = when (summary.shoulderProfile) {
        "broad_relative_to_hip" -> "상체 볼륨이 또렷해서 허리 라인을 살려주는 실루엣이 잘 어울려."
        "narrow_relative_to_hip" -> "상체 비율을 보완할 수 있게 어깨 포인트가 있는 아이템이 좋아."
        else -> "전체 밸런스가 좋아서 미니멀한 정석 실루엣이 잘 맞아."
    }

    val legHint = when (summary.legBalance) {
        "leg_emphasized" -> "긴 다리 비율이 살아나도록 스트레이트 팬츠와 짧은 상의를 추천해."
        "torso_emphasized" -> "하이웨이스트와 세로 라인 디테일이 비율 보정에 유리해."
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
            description = "현재 프레임 타입 ${humanizeToken(summary.frameType)} 기준으로 과하지 않게 체형 밸런스를 맞춰줘.",
            styleTip = "백엔드 추천 API가 붙기 전까지는 이 카드 구조를 그대로 재사용하면 돼."
        )
    )
}

private fun jsonArrayToList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val items = mutableListOf<String>()
    for (index in 0 until array.length()) {
        items += array.optString(index)
    }
    return items
}

private fun humanizeToken(value: String): String {
    return value
        .substringBeforeLast(".")
        .split("_", "-", " ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { it.uppercase() }
        }
}

private fun Double.format(digits: Int): String {
    return java.lang.String.format(Locale.US, "%.${digits}f", this)
}

private data class PipelineUiState(
    val steps: List<PipelineStep> = PoseExtractionPipeline.initialSteps(),
    val completedSteps: Set<String> = emptySet(),
    val selectedAsset: String = PoseExtractionPipeline.sampleAssets.first(),
    val isRunning: Boolean = false,
    val statusMessage: String = "분석 준비 완료",
    val jsonOutput: String = "",
    val featureJsonOutput: String = "",
    val outputFilePath: String = "",
    val sampleBitmap: Bitmap? = null
) {
    val displayOutput: String
        get() = if (jsonOutput.isBlank()) {
            "{\n  \"status\": \"idle\"\n}"
        } else {
            jsonOutput
        }

    val displayFeatureOutput: String
        get() = if (featureJsonOutput.isBlank()) {
            "{\n  \"status\": \"idle\"\n}"
        } else {
            featureJsonOutput
        }

    fun startStep(stepId: String): PipelineUiState {
        return copy(
            isRunning = true,
            statusMessage = "${stepTitle(stepId)} 실행 중",
            steps = steps.map { step ->
                when (step.id) {
                    stepId -> step.copy(status = StepStatus.RUNNING)
                    else -> {
                        if (step.id in completedSteps) {
                            step.copy(status = StepStatus.COMPLETED)
                        } else {
                            step.copy(status = StepStatus.PENDING)
                        }
                    }
                }
            }
        )
    }

    fun completeStep(result: ExtractionResult): PipelineUiState {
        val updatedCompleted = completedSteps + result.step.id
        return copy(
            isRunning = false,
            statusMessage = "${result.step.title} 완료",
            jsonOutput = result.jsonOutput,
            featureJsonOutput = result.featureJsonOutput,
            outputFilePath = result.outputFilePath,
            completedSteps = updatedCompleted,
            steps = steps.map { step ->
                if (step.id == result.step.id || step.id in updatedCompleted) {
                    step.copy(status = StepStatus.COMPLETED)
                } else {
                    step.copy(status = StepStatus.PENDING)
                }
            }
        )
    }

    fun failStep(stepId: String, error: Throwable): PipelineUiState {
        val errorText = buildString {
            append("ERROR: ")
            append(error::class.qualifiedName ?: error::class.simpleName ?: "UnknownError")
            val message = error.message
            if (!message.isNullOrBlank()) {
                append("\n")
                append(message)
            }
            append("\n\n")
            append(error.stackTraceToString())
        }
        return copy(
            isRunning = false,
            statusMessage = "${stepTitle(stepId)} 실패",
            jsonOutput = errorText,
            featureJsonOutput = errorText,
            steps = steps.map { step ->
                if (step.id in completedSteps) {
                    step.copy(status = StepStatus.COMPLETED)
                } else {
                    step.copy(status = StepStatus.PENDING)
                }
            }
        )
    }

    private fun stepTitle(stepId: String): String {
        return steps.firstOrNull { it.id == stepId }?.title ?: stepId
    }
}
