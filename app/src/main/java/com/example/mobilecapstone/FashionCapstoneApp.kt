package com.example.mobilecapstone

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.mobilecapstone.data.AnalysisRecordDatabase
import com.example.mobilecapstone.data.AnalysisRecordEntity
import com.example.mobilecapstone.data.ItemFeedbackEntity
import com.example.mobilecapstone.data.MockRecommendationServer
import com.example.mobilecapstone.data.TagPreferenceEntity
import com.example.mobilecapstone.data.toEntity
import com.example.mobilecapstone.data.toModel
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FashionCapstoneApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.Login) }
    var uiState by remember { mutableStateOf(PipelineUiState()) }
    var currentRecordId by remember { mutableStateOf<String?>(null) }
    var recommendationFilters by remember { mutableStateOf(RecommendationFilterState()) }
    var selectedRecommendation by remember { mutableStateOf<RecommendationItem?>(null) }
    var selectedRecommendationRecordId by remember { mutableStateOf<String?>(null) }
    var recommendationDetailReturnScreen by remember { mutableStateOf(AppScreen.RecommendationList) }
    var shouldTrackRecommendationDwell by remember { mutableStateOf(false) }

    val database = remember { AnalysisRecordDatabase.getInstance(context) }
    val analysisRecordDao = remember { database.analysisRecordDao() }
    val recommendationItemDao = remember { database.recommendationItemDao() }
    val itemFeedbackDao = remember { database.itemFeedbackDao() }
    val tagPreferenceDao = remember { database.tagPreferenceDao() }
    val mockRecommendationServer = remember { MockRecommendationServer() }

    val localRecords by analysisRecordDao.observeRecords().collectAsState(initial = emptyList())
    val storedRecommendationEntities by recommendationItemDao.observeAll().collectAsState(initial = emptyList())
    val feedbackRecords by itemFeedbackDao.observeAll().collectAsState(initial = emptyList())
    val tagPreferences by tagPreferenceDao.observeAll().collectAsState(initial = emptyList())

    var accountName by remember { mutableStateOf("사용자") }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var signupUsername by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupPasswordConfirm by remember { mutableStateOf("") }
    var signupHeight by remember { mutableStateOf("") }
    var signupWeight by remember { mutableStateOf("") }
    var profileHeightInput by remember { mutableStateOf("") }
    var profileWeightInput by remember { mutableStateOf("") }
    var profileEditHeightInput by remember { mutableStateOf("") }
    var profileEditWeightInput by remember { mutableStateOf("") }

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

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            uiState = uiState.copy(
                selectedImageUri = uri.toString(),
                selectedImageLabel = "카메라 촬영 사진",
                jsonOutput = "",
                featureJsonOutput = "",
                outputFilePath = "",
                statusMessage = "촬영한 사진이 선택되었습니다. 분석을 실행해 주세요."
            )
            currentScreen = AppScreen.Analysis
        }
        pendingCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uiState = uiState.copy(
                selectedImageUri = uri.toString(),
                selectedImageLabel = "갤러리 선택 사진",
                jsonOutput = "",
                featureJsonOutput = "",
                outputFilePath = "",
                statusMessage = "갤러리 사진이 선택되었습니다. 분석을 실행해 주세요."
            )
            currentScreen = AppScreen.Analysis
        }
    }

    LaunchedEffect(uiState.selectedAsset, uiState.selectedImageUri) {
        val sample = withContext(Dispatchers.IO) {
            uiState.selectedImageUri?.let { imageUri ->
                PoseExtractionPipeline.loadImageBitmap(context, imageUri)
            } ?: PoseExtractionPipeline.loadSampleBitmap(context, uiState.selectedAsset)
        }
        uiState = uiState.copy(sampleBitmap = sample)
    }

    val summary = remember(uiState.jsonOutput, uiState.featureJsonOutput) {
        ResultSummary.from(
            rawJson = uiState.jsonOutput,
            featureJson = uiState.featureJsonOutput
        )
    }
    val feedbackByProduct = remember(feedbackRecords) {
        feedbackRecords.associateBy { it.recordId to it.productId }
    }
    val tagPreferenceWeights = remember(tagPreferences) {
        tagPreferences.associate { it.tag to it.weight }
    }
    val recommendations = remember(summary, recommendationFilters, currentRecordId, feedbackByProduct) {
        val recordId = currentRecordId
        buildRecommendationMocks(context, summary, recommendationFilters).map { item ->
            val feedback = recordId?.let { feedbackByProduct[it to item.id] }
            item.copy(
                userRating = feedback?.userRating,
                totalDwellTimeMs = feedback?.totalDwellTimeMs ?: 0L
            )
        }
    }
    val colourFilterOptions = remember(summary, recommendationFilters) {
        buildRecommendationMocks(
            context = context,
            summary = summary,
            filters = recommendationFilters.copy(selectedBaseColour = "All")
        )
            .map { it.baseColour }
            .filter { it.isNotBlank() && it != "NA" }
            .distinct()
            .sorted()
    }
    val historyEntries = remember(localRecords, storedRecommendationEntities, feedbackByProduct) {
        val recommendationsByRecord = storedRecommendationEntities.groupBy { it.recordId }
        localRecords.map { record ->
            val createdAt = DateTimeFormatter.ofPattern("MM.dd HH:mm").format(
                Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault())
            )
            val recordRecommendations = recommendationsByRecord[record.recordId].orEmpty().map { entity ->
                entity.toModel(feedbackByProduct[record.recordId to entity.productId])
            }
            HistoryEntry(
                recordId = record.recordId,
                createdAt = createdAt,
                imageLabel = record.imageLabel,
                imageUri = record.imageUri,
                frameType = record.frameType,
                tags = tagsFromJson(record.tagsJson),
                heightCm = record.heightCm,
                weightKg = record.weightKg,
                recommendations = recordRecommendations
            )
        }
    }

    fun saveAnalysisHistorySnapshot(summary: ResultSummary?, items: List<RecommendationItem>) {
        val parsed = summary ?: return
        val recordId = currentRecordId ?: UUID.randomUUID().toString().also { currentRecordId = it }
        val selectedAsset = uiState.selectedAsset
        val selectedImageUri = uiState.selectedImageUri
        val imageLabel = uiState.selectedImageLabel
        val userBodyProfile = UserBodyProfile(
            heightCm = profileHeightInput.toDoubleOrNull(),
            weightKg = profileWeightInput.toDoubleOrNull()
        )

        scope.launch(Dispatchers.IO) {
            val storedImagePath = persistAnalysisImage(
                context = context,
                recordId = recordId,
                selectedAsset = selectedAsset,
                selectedImageUri = selectedImageUri
            )
            analysisRecordDao.upsert(
                AnalysisRecordEntity(
                    recordId = recordId,
                    imageUri = storedImagePath,
                    imageLabel = imageLabel,
                    tagsJson = tagsToJson(parsed.tags),
                    heightCm = userBodyProfile.heightCm,
                    weightKg = userBodyProfile.weightKg,
                    frameType = parsed.frameType,
                    createdAt = System.currentTimeMillis()
                )
            )
            recommendationItemDao.deleteByRecordId(recordId)
            mockRecommendationServer.deleteRecommendations(recordId)
            mockRecommendationServer.saveRecommendations(recordId, items)
            recommendationItemDao.upsertAll(
                items.map { item ->
                    item.toEntity(
                        recordId = recordId,
                        createdAt = System.currentTimeMillis()
                    )
                }
            )
        }
    }

    suspend fun adjustTagPreferences(tags: List<String>, delta: Double) {
        tags.distinct().filter { it.isNotBlank() }.forEach { tag ->
            val current = tagPreferenceDao.getByTag(tag)
            val nextWeight = ((current?.weight ?: 0.0) + delta).coerceIn(-1.0, 1.0)
            tagPreferenceDao.upsert(
                TagPreferenceEntity(
                    tag = tag,
                    weight = nextWeight,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun submitRating(recordId: String, item: RecommendationItem, rating: Int) {
        scope.launch(Dispatchers.IO) {
            val existing = itemFeedbackDao.getFeedback(recordId, item.id)
            itemFeedbackDao.upsert(
                ItemFeedbackEntity(
                    recordId = recordId,
                    productId = item.id,
                    userRating = rating,
                    totalDwellTimeMs = existing?.totalDwellTimeMs ?: 0L,
                    viewCount = existing?.viewCount ?: 0,
                    lastViewedAt = existing?.lastViewedAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val delta = ((rating / 5.0) - 0.5) * 0.2
            adjustTagPreferences(item.matchedTags, delta)
        }
    }

    fun recordDetailDwell(recordId: String, item: RecommendationItem, dwellTimeMs: Long) {
        if (dwellTimeMs <= 0L) return
        scope.launch(Dispatchers.IO) {
            val existing = itemFeedbackDao.getFeedback(recordId, item.id)
            val previousDwell = existing?.totalDwellTimeMs ?: 0L
            itemFeedbackDao.upsert(
                ItemFeedbackEntity(
                    recordId = recordId,
                    productId = item.id,
                    userRating = existing?.userRating,
                    totalDwellTimeMs = previousDwell + dwellTimeMs,
                    viewCount = (existing?.viewCount ?: 0) + 1,
                    lastViewedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            val dwellScore = min(dwellTimeMs / 30_000.0, 1.0)
            adjustTagPreferences(item.matchedTags, dwellScore * 0.08)
        }
    }

    fun navigateBack() {
        currentScreen = when (currentScreen) {
            AppScreen.RecommendationDetail -> recommendationDetailReturnScreen
            AppScreen.RecommendationList -> AppScreen.Analysis
            AppScreen.Capture,
            AppScreen.Analysis,
            AppScreen.History,
            AppScreen.Settings -> AppScreen.Home
            AppScreen.ProfileEdit -> AppScreen.Settings
            AppScreen.Signup,
            AppScreen.Home,
            AppScreen.Login -> currentScreen
        }
    }

    BackHandler(
        enabled = currentScreen != AppScreen.Login &&
            currentScreen != AppScreen.Signup &&
            currentScreen != AppScreen.Home
    ) {
        navigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentScreen != AppScreen.Login) {
                AppTopBar(
                    title = currentScreen.title,
                    showBack = currentScreen == AppScreen.Signup ||
                        currentScreen == AppScreen.RecommendationList ||
                        currentScreen == AppScreen.RecommendationDetail ||
                        currentScreen == AppScreen.ProfileEdit,
                    onBack = ::navigateBack
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
                    // TODO: 백엔드 로그인 API 연결 시 아래 임시 우회 코드를 제거하고 서버 로그인 로직을 복원하세요.
                    accountName = loginEmail.substringBefore("@").ifBlank { "사용자" }
                    loginPassword = ""
                    authMessage = null
                    authLoading = false
                    currentScreen = AppScreen.Home

                    /*
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
                    */
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
                                    profileHeightInput = signupHeight
                                    profileWeightInput = signupWeight
                                    loginEmail = ""
                                    loginPassword = ""

                                    signupUsername = ""
                                    signupEmail = ""
                                    signupPassword = ""
                                    signupPasswordConfirm = ""
                                    signupHeight = ""
                                    signupWeight = ""

                                    authMessage = null
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
                selectedImageLabel = uiState.selectedImageLabel,
                sampleBitmap = uiState.sampleBitmap,
                onTakePhoto = {
                    val uri = createCameraImageUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                onPickFromGallery = {
                    galleryLauncher.launch("image/*")
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
                        val userBodyProfile = UserBodyProfile(
                            heightCm = profileHeightInput.toDoubleOrNull(),
                            weightKg = profileWeightInput.toDoubleOrNull()
                        )
                        uiState = uiState.beginRun()

                        val selectedAsset = uiState.selectedAsset
                        val selectedImageUri = uiState.selectedImageUri
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching {
                                if (selectedImageUri != null) {
                                    PoseExtractionPipeline.runPoseExtractionFromUri(
                                        context = context,
                                        imageUri = selectedImageUri,
                                        userBodyProfile = userBodyProfile,
                                        onStepStatusChanged = { stepId, status ->
                                            scope.launch {
                                                uiState = uiState.updateStepStatus(stepId, status)
                                            }
                                        }
                                    )
                                } else {
                                    PoseExtractionPipeline.runPoseExtraction(
                                        context = context,
                                        assetName = selectedAsset,
                                        userBodyProfile = userBodyProfile,
                                        onStepStatusChanged = { stepId, status ->
                                            scope.launch {
                                                uiState = uiState.updateStepStatus(stepId, status)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        uiState = outcome.fold(
                            onSuccess = { result ->
                                val nextState = uiState.completeStep(result)

                                if (ResultSummary.from(
                                    result.jsonOutput,
                                    result.featureJsonOutput
                                ) != null) {
                                    currentRecordId = UUID.randomUUID().toString()
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
                filters = recommendationFilters,
                colourOptions = colourFilterOptions,
                tagPreferenceWeights = tagPreferenceWeights,
                onFiltersChange = { recommendationFilters = it },
                onSelect = {
                    selectedRecommendation = it
                    selectedRecommendationRecordId = null
                    recommendationDetailReturnScreen = AppScreen.RecommendationList
                    shouldTrackRecommendationDwell = false
                    currentScreen = AppScreen.RecommendationDetail
                },
                onOpenHistory = {
                    saveAnalysisHistorySnapshot(summary, recommendations)
                    currentScreen = AppScreen.History
                },
                onGoAnalysis = {
                    currentScreen = AppScreen.Analysis
                }
            )

            AppScreen.RecommendationDetail -> RecommendationDetailScreen(
                modifier = Modifier.padding(innerPadding),
                item = selectedRecommendation,
                summary = summary,
                onDwellMeasured = { item, dwellTimeMs ->
                    if (shouldTrackRecommendationDwell) {
                        selectedRecommendationRecordId?.let { recordId ->
                            recordDetailDwell(recordId, item, dwellTimeMs)
                        }
                    }
                },
                onBackToList = {
                    currentScreen = recommendationDetailReturnScreen
                }
            )

            AppScreen.History -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                historyEntries = historyEntries,
                onRate = ::submitRating,
                onSelectRecommendation = { entry, item ->
                    selectedRecommendation = item
                    selectedRecommendationRecordId = entry.recordId
                    recommendationDetailReturnScreen = AppScreen.History
                    shouldTrackRecommendationDwell = true
                    currentScreen = AppScreen.RecommendationDetail
                },
                onDeleteRecord = { entry ->
                    scope.launch(Dispatchers.IO) {
                        analysisRecordDao.deleteById(entry.recordId)
                        recommendationItemDao.deleteByRecordId(entry.recordId)
                        mockRecommendationServer.deleteRecommendations(entry.recordId)
                        entry.imageUri?.let { path ->
                            runCatching { File(path).delete() }
                        }
                    }
                },
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
                onEditProfile = {
                    profileEditHeightInput = profileHeightInput
                    profileEditWeightInput = profileWeightInput
                    currentScreen = AppScreen.ProfileEdit
                },
                onLogout = {
                    currentScreen = AppScreen.Login
                    loginPassword = ""
                    authMessage = null
                }
            )

            AppScreen.ProfileEdit -> ProfileEditScreen(
                modifier = Modifier.padding(innerPadding),
                currentHeight = profileHeightInput,
                currentWeight = profileWeightInput,
                editedHeight = profileEditHeightInput,
                editedWeight = profileEditWeightInput,
                onHeightChange = {
                    profileEditHeightInput = it
                },
                onWeightChange = {
                    profileEditWeightInput = it
                },
                onSave = {
                    profileHeightInput = profileEditHeightInput
                    profileWeightInput = profileEditWeightInput
                    uiState = uiState.copy(
                        jsonOutput = "",
                        featureJsonOutput = "",
                        outputFilePath = "",
                        statusMessage = "신체 정보가 변경되었습니다. 분석을 다시 실행해 주세요."
                    )
                    currentScreen = AppScreen.Settings
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

private fun createCameraImageUri(context: Context): Uri {
    val imageDir = File(context.cacheDir, "camera")
    imageDir.mkdirs()
    val imageFile = File.createTempFile("capture_", ".jpg", imageDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun persistAnalysisImage(
    context: Context,
    recordId: String,
    selectedAsset: String,
    selectedImageUri: String?
): String {
    val imageDir = File(context.filesDir, "analysis_records")
    imageDir.mkdirs()
    val imageFile = File(imageDir, "$recordId.jpg")

    val bitmap = selectedImageUri?.let { uri ->
        PoseExtractionPipeline.loadImageBitmap(context, uri)
    } ?: PoseExtractionPipeline.loadSampleBitmap(context, selectedAsset)

    FileOutputStream(imageFile).use { target ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, target)
    }

    return imageFile.absolutePath
}
