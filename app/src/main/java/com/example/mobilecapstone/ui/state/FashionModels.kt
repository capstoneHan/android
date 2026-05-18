package com.example.mobilecapstone

import android.content.Context
import android.graphics.Bitmap
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
internal data class RecommendationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val description: String,
    val styleTip: String,
    val rawPrice: Int = 0,
    val discountedPrice: Int = rawPrice,
    val brandName: String = "",
    val season: String = "All",
    val gender: String = "All",
    val baseColour: String = "NA",
    val usage: String = "Fashion",
    val rating: Int = 0,
    val productType: String = "",
    val fit: String = "",
    val imageUrl: String = "",
    val productTags: List<String> = emptyList(),
    val matchedTags: List<String> = emptyList(),
    val matchScore: Double = 0.0,
    val topRecommendation: Boolean = false,
    val userRating: Int? = null,
    val totalDwellTimeMs: Long = 0L
)

internal data class RecommendationFilterState(
    val minPrice: Int = 0,
    val maxPrice: Int = 500_000,
    val selectedSeason: Set<String> = setOf("All"),
    val selectedGender: Set<String> = setOf("All"),
    val selectedUsage: Set<String> = setOf("All"),
    val selectedBaseColour: Set<String> = setOf("All"),
    val selectedBrandName: Set<String> = setOf("All"),
    val selectedArticleType: Set<String> = setOf("All"),
    val selectedStyleTag: Set<String> = setOf("All"),
    val selectedFit: Set<String> = setOf("All"),
    val discountedOnly: Boolean = false
) {
    fun toServerQuery(
        tags: List<String>,
        tagPreferenceWeights: Map<String, Double> = emptyMap()
    ): JSONObject {
        return JSONObject()
            .put("min_price", minPrice)
            .put("max_price", maxPrice)
            .put("season", selectedSeason.singleServerValue() ?: JSONObject.NULL)
            .put("gender", selectedGender.singleServerValue() ?: JSONObject.NULL)
            .put("usage", selectedUsage.singleServerValue() ?: JSONObject.NULL)
            .put("base_colour", selectedBaseColour.singleServerValue() ?: JSONObject.NULL)
            .put("brand_name", selectedBrandName.singleServerValue() ?: JSONObject.NULL)
            .put("article_type", selectedArticleType.singleServerValue() ?: JSONObject.NULL)
            .put("style_tag", selectedStyleTag.singleServerValue() ?: JSONObject.NULL)
            .put("fit", selectedFit.singleServerValue() ?: JSONObject.NULL)
            .put("seasons", JSONArray(selectedSeason.serverValues()))
            .put("genders", JSONArray(selectedGender.serverValues()))
            .put("usages", JSONArray(selectedUsage.serverValues()))
            .put("base_colours", JSONArray(selectedBaseColour.serverValues()))
            .put("brand_names", JSONArray(selectedBrandName.serverValues()))
            .put("article_types", JSONArray(selectedArticleType.serverValues()))
            .put("style_tags_filter", JSONArray(selectedStyleTag.serverValues()))
            .put("fits", JSONArray(selectedFit.serverValues()))
            .put("discounted_only", discountedOnly)
            .put(
                "tag_preferences",
                JSONArray(
                    tagPreferenceWeights.map { (tag, weight) ->
                        JSONObject()
                            .put("tag", tag)
                            .put("weight", weight)
                    }
                )
            )
            .put("style_tags", JSONArray(tags))
    }
}

internal fun Set<String>.serverValues(): List<String> {
    return filter { it != "All" }
}

internal fun Set<String>.singleServerValue(): String? {
    return serverValues().singleOrNull()
}

internal fun Set<String>.containsFilterValue(value: String): Boolean {
    return contains("All") || contains(value)
}

internal fun Set<String>.containsAnyFilterValue(values: Iterable<String>): Boolean {
    return contains("All") || values.any { contains(it) }
}

internal fun Set<String>.toggleFilterOption(option: String, options: List<String>): Set<String> {
    if (option == "All") return setOf("All")

    val concreteOptions = options.filter { it != "All" }.toSet()
    val selected = (this - "All").toMutableSet()
    if (selected.contains(option)) {
        selected.remove(option)
    } else {
        selected.add(option)
    }

    return if (selected.isEmpty() || concreteOptions.all { selected.contains(it) }) {
        setOf("All")
    } else {
        selected.toSet()
    }
}

internal data class HistoryEntry(
    val recordId: String,
    val createdAt: String,
    val imageLabel: String,
    val imageUri: String?,
    val frameType: String,
    val tags: List<String>,
    val heightCm: Double?,
    val weightKg: Double?,
    val recommendations: List<RecommendationItem>
)

data class UserBodyProfile(
    val heightCm: Double? = null,
    val weightKg: Double? = null
)

internal data class ResultSummary(
    val landmarkCount: Int,
    val shoulderToHipRatio: Double,
    val torsoToLegRatio: Double,
    val waistToHipRatio: Double,
    val waistToShoulderRatio: Double,
    val hipToShoulderRatio: Double,
    val thighToHipRatio: Double,
    val shoulderWidthToHeightRatio: Double,
    val waistWidthToHeightRatio: Double,
    val hipWidthToHeightRatio: Double,
    val thighWidthToHeightRatio: Double,
    val shoulderWidthMask: Double,
    val waistWidthMask: Double,
    val hipWidthMask: Double,
    val thighWidthMask: Double,
    val shoulderRowMask: Int,
    val waistRowMask: Int,
    val hipRowMask: Int,
    val thighRowMask: Int,
    val frameType: String,
    val waistDefinition: String,
    val shoulderProfile: String,
    val silhouetteProfile: String,
    val upperLowerBalance: String,
    val faceShape: String,
    val skinUndertone: String,
    val skinClarity: String,
    val heightCm: Double,
    val weightKg: Double,
    val bmi: Double,
    val bmiBand: String,
    val heightBand: String,
    val bodyRatioTags: List<String>,
    val silhouetteTags: List<String>,
    val profileTags: List<String>,
    val faceTags: List<String>,
    val toneTags: List<String>,
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
                val bodyRatio = feature.optJSONObject("body_ratio")
                val silhouette = feature.optJSONObject("silhouette_features")
                val bodyFrame = feature.optJSONObject("body_frame")
                val userProfile = feature.optJSONObject("user_profile")
                val bodyMass = feature.optJSONObject("body_mass_features")
                val faceFeatures = feature.optJSONObject("face_features")
                val colorFeatures = feature.optJSONObject("color_features")
                val tagGroups = feature.optJSONObject("tag_groups")

                ResultSummary(
                    landmarkCount = pose?.optInt("landmark_count", 0) ?: 0,
                    shoulderToHipRatio = bodyRatio?.optDouble(
                        "shoulder_to_hip_ratio",
                        derived?.optDouble("shoulder_to_hip_ratio", 0.0) ?: 0.0
                    ) ?: 0.0,
                    torsoToLegRatio = bodyRatio?.optDouble("torso_to_leg_ratio", 0.0) ?: 0.0,
                    waistToHipRatio = silhouette?.optDouble("waist_to_hip_ratio", 0.0) ?: 0.0,
                    waistToShoulderRatio = silhouette?.optDouble("waist_to_shoulder_ratio", 0.0) ?: 0.0,
                    hipToShoulderRatio = silhouette?.optDouble("hip_to_shoulder_ratio", 0.0) ?: 0.0,
                    thighToHipRatio = silhouette?.optDouble("thigh_to_hip_ratio", 0.0) ?: 0.0,
                    shoulderWidthToHeightRatio = silhouette?.optDouble("shoulder_width_to_height_ratio", 0.0) ?: 0.0,
                    waistWidthToHeightRatio = silhouette?.optDouble("waist_width_to_height_ratio", 0.0) ?: 0.0,
                    hipWidthToHeightRatio = silhouette?.optDouble("hip_width_to_height_ratio", 0.0) ?: 0.0,
                    thighWidthToHeightRatio = silhouette?.optDouble("thigh_width_to_height_ratio", 0.0) ?: 0.0,
                    shoulderWidthMask = silhouette?.optDouble("shoulder_width_mask", 0.0) ?: 0.0,
                    waistWidthMask = silhouette?.optDouble("waist_width_mask", 0.0) ?: 0.0,
                    hipWidthMask = silhouette?.optDouble("hip_width_mask", 0.0) ?: 0.0,
                    thighWidthMask = silhouette?.optDouble("thigh_width_mask", 0.0) ?: 0.0,
                    shoulderRowMask = silhouette?.optInt("shoulder_row_mask", 0) ?: 0,
                    waistRowMask = silhouette?.optInt("waist_row_mask", 0) ?: 0,
                    hipRowMask = silhouette?.optInt("hip_row_mask", 0) ?: 0,
                    thighRowMask = silhouette?.optInt("thigh_row_mask", 0) ?: 0,
                    frameType = silhouette?.optString("frame_type", "unknown") ?: "unknown",
                    waistDefinition = silhouette?.optString("waist_definition", "unknown") ?: "unknown",
                    shoulderProfile = bodyFrame?.optString("shoulder_profile", "unknown") ?: "unknown",
                    silhouetteProfile = bodyFrame?.optString("silhouette_profile", "unknown") ?: "unknown",
                    upperLowerBalance = bodyFrame?.optString("upper_lower_balance", "unknown") ?: "unknown",
                    faceShape = faceFeatures?.optString("shape", "unknown") ?: "unknown",
                    skinUndertone = colorFeatures?.optString("undertone", "unknown") ?: "unknown",
                    skinClarity = colorFeatures?.optString("clarity", "unknown") ?: "unknown",
                    heightCm = userProfile?.optDouble("height_cm", 0.0) ?: 0.0,
                    weightKg = userProfile?.optDouble("weight_kg", 0.0) ?: 0.0,
                    bmi = bodyMass?.optDouble("bmi", 0.0) ?: 0.0,
                    bmiBand = bodyMass?.optString("bmi_band", "unknown") ?: "unknown",
                    heightBand = bodyMass?.optString("height_band", "unknown") ?: "unknown",
                    bodyRatioTags = jsonArrayToList(tagGroups?.optJSONArray("body_ratio_tags")),
                    silhouetteTags = jsonArrayToList(tagGroups?.optJSONArray("silhouette_tags")),
                    profileTags = jsonArrayToList(tagGroups?.optJSONArray("profile_tags")),
                    faceTags = jsonArrayToList(tagGroups?.optJSONArray("face_tags")),
                    toneTags = jsonArrayToList(tagGroups?.optJSONArray("tone_tags")),
                    tags = jsonArrayToList(feature.optJSONArray("style_tags"))
                )
            }.getOrNull()
        }
    }
}

internal fun jsonArrayToList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val items = mutableListOf<String>()
    for (index in 0 until array.length()) {
        items += array.optString(index)
    }
    return items
}

internal fun tagsToJson(tags: List<String>): String {
    return JSONArray(tags).toString()
}

internal fun tagsFromJson(rawJson: String): List<String> {
    return runCatching { jsonArrayToList(JSONArray(rawJson)) }.getOrDefault(emptyList())
}

internal fun humanizeToken(value: String): String {
    return value
        .substringBeforeLast(".")
        .split("_", "-", " ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { it.uppercase() }
        }
}

internal fun assetLabel(assetName: String): String {
    return when (assetName.substringBeforeLast(".")) {
        "female_average" -> "여성 평균 체형 샘플"
        "female_plus" -> "여성 볼륨 체형 샘플"
        "female_slim" -> "여성 슬림 체형 샘플"
        "male_average" -> "남성 평균 체형 샘플"
        "male_slim" -> "남성 슬림 체형 샘플"
        "male_sturdy" -> "남성 탄탄한 체형 샘플"
        else -> humanizeToken(assetName)
    }
}

internal fun tokenLabel(context: Context, value: String): String {
    val normalized = value.trim().lowercase(Locale.US).replace(" ", "_").replace("-", "_")
    val directLabel = when (normalized) {
        "all" -> "전체"
        "men" -> "남성"
        "women" -> "여성"
        "unisex" -> "공용"
        "spring" -> "봄"
        "summer" -> "여름"
        "fall", "autumn" -> "가을"
        "winter" -> "겨울"
        "casual" -> "캐주얼"
        "sports" -> "스포츠"
        "fashion" -> "패션"
        "tshirts" -> "티셔츠"
        "shirts" -> "셔츠"
        "tops" -> "상의"
        "jeans" -> "청바지"
        "trousers" -> "팬츠"
        "track_pants" -> "트랙 팬츠"
        "shorts" -> "반바지"
        "skirts" -> "스커트"
        "jackets" -> "재킷"
        "coats" -> "코트"
        "blazers" -> "블레이저"
        "black" -> "블랙"
        "white" -> "화이트"
        "blue" -> "블루"
        "beige" -> "베이지"
        "lavender" -> "라벤더"
        "red" -> "레드"
        "green" -> "그린"
        "grey", "gray" -> "그레이"
        "regular_fit" -> "레귤러 핏"
        "slim_fit" -> "슬림 핏"
        "comfort_fit" -> "컴포트 핏"
        "sajo_catalog" -> "사조 카탈로그"
        "na" -> "정보 없음"
        else -> null
    }
    if (directLabel != null) return directLabel

    val resId = when (value) {
        "defined_frame" -> R.string.token_defined_frame
        "balanced_frame" -> R.string.token_balanced_frame
        "straight_frame" -> R.string.token_straight_frame
        "defined" -> R.string.token_defined
        "moderate" -> R.string.token_moderate
        "medium" -> R.string.token_medium
        "soft" -> R.string.token_soft
        "straight" -> R.string.token_straight
        "lower_body_emphasized" -> R.string.token_lower_body_emphasized
        "upper_body_emphasized" -> R.string.token_upper_body_emphasized
        "balanced" -> R.string.token_balanced
        "round" -> R.string.token_round
        "oval" -> R.string.token_oval
        "heart" -> R.string.token_heart
        "square" -> R.string.token_square
        "oblong" -> R.string.token_oblong
        "warm" -> R.string.token_warm
        "cool" -> R.string.token_cool
        "neutral" -> R.string.token_neutral
        "clear" -> R.string.token_clear
        "muted" -> R.string.token_muted
        "light" -> R.string.token_light
        "deep" -> R.string.token_deep
        "defined_curve" -> R.string.token_defined_curve
        "balanced_line" -> R.string.token_balanced_line
        "straight_line" -> R.string.token_straight_line
        "upper_body_emphasis" -> R.string.tag_upper_body_emphasis
        "structured_top_candidate" -> R.string.tag_structured_top_candidate
        "lower_body_emphasis" -> R.string.tag_lower_body_emphasis
        "shoulder_expansion_recommended" -> R.string.tag_shoulder_expansion_recommended
        "balanced_proportion" -> R.string.tag_balanced_proportion
        "balanced_upper_lower_frame" -> R.string.tag_balanced_upper_lower_frame
        "long_leg_balance" -> R.string.tag_long_leg_balance
        "high_waist_friendly" -> R.string.tag_high_waist_friendly
        "leg_lengthening_recommended" -> R.string.tag_leg_lengthening_recommended
        "vertical_line_friendly" -> R.string.tag_vertical_line_friendly
        "balanced_leg_torso_ratio" -> R.string.tag_balanced_leg_torso_ratio
        "waist_defined" -> R.string.tag_waist_defined
        "moderate_waist_line" -> R.string.tag_moderate_waist_line
        "waist_definition_recommended" -> R.string.tag_waist_definition_recommended
        "soft_waist_curve" -> R.string.tag_soft_waist_curve
        "straight_waist_line" -> R.string.tag_straight_waist_line
        "slim_volume" -> R.string.tag_slim_volume
        "balanced_volume" -> R.string.tag_balanced_volume
        "full_volume" -> R.string.tag_full_volume
        "relaxed_fit_friendly" -> R.string.tag_relaxed_fit_friendly
        "defined_silhouette" -> R.string.tag_defined_silhouette
        "clean_line_friendly" -> R.string.tag_clean_line_friendly
        "balanced_silhouette" -> R.string.tag_balanced_silhouette
        "straight_body_line" -> R.string.tag_straight_body_line
        "soft_face_shape" -> R.string.tag_soft_face_shape
        "angular_face_shape" -> R.string.tag_angular_face_shape
        "length_emphasized_face" -> R.string.tag_length_emphasized_face
        "face_shape_round" -> R.string.tag_face_shape_round
        "face_shape_oval" -> R.string.tag_face_shape_oval
        "face_shape_heart" -> R.string.tag_face_shape_heart
        "face_shape_square" -> R.string.tag_face_shape_square
        "face_shape_oblong" -> R.string.tag_face_shape_oblong
        "soft_width_emphasized" -> R.string.tag_soft_width_emphasized
        "structured_width_emphasized" -> R.string.tag_structured_width_emphasized
        "length_emphasized" -> R.string.tag_length_emphasized
        "warm_tone" -> R.string.tag_warm_tone
        "cool_tone" -> R.string.tag_cool_tone
        "neutral_tone" -> R.string.tag_neutral_tone
        "clear_clarity" -> R.string.tag_clear_clarity
        "muted_clarity" -> R.string.tag_muted_clarity
        "balanced_clarity" -> R.string.tag_balanced_clarity
        "light_brightness" -> R.string.tag_light_brightness
        "medium_brightness" -> R.string.tag_medium_brightness
        "deep_brightness" -> R.string.tag_deep_brightness
        "cool_clear_palette" -> R.string.tag_cool_clear_palette
        "warm_soft_palette" -> R.string.tag_warm_soft_palette
        "neutral_palette_flexible" -> R.string.tag_neutral_palette_flexible
        else -> null
    }
    return resId?.let(context::getString) ?: humanizeToken(value)
}

internal fun Double.format(digits: Int): String {
    return java.lang.String.format(Locale.US, "%.${digits}f", this)
}

internal data class PipelineUiState(
    val steps: List<PipelineStep> = PoseExtractionPipeline.initialSteps(),
    val completedSteps: Set<String> = emptySet(),
    val selectedAsset: String = PoseExtractionPipeline.sampleAssets.first(),
    val selectedImageUri: String? = null,
    val selectedImageLabel: String = assetLabel(selectedAsset),
    val isRunning: Boolean = false,
    val statusMessage: String = "분석 준비 완료",
    val jsonOutput: String = "",
    val featureJsonOutput: String = "",
    val outputFilePath: String = "",
    val sampleBitmap: Bitmap? = null
) {
    fun beginRun(): PipelineUiState {
        return copy(
            isRunning = true,
            statusMessage = "분석을 시작합니다.",
            jsonOutput = "",
            featureJsonOutput = "",
            outputFilePath = "",
            completedSteps = emptySet(),
            steps = steps.map { it.copy(status = StepStatus.PENDING) }
        )
    }

    fun updateStepStatus(stepId: String, status: StepStatus): PipelineUiState {
        if (steps.none { it.id == stepId }) return this
        val updatedCompleted = when (status) {
            StepStatus.COMPLETED -> completedSteps + stepId
            else -> completedSteps
        }
        return copy(
            isRunning = true,
            statusMessage = when (status) {
                StepStatus.PENDING -> "${stepTitle(stepId)} 대기 중"
                StepStatus.RUNNING -> "${stepTitle(stepId)} 진행 중"
                StepStatus.COMPLETED -> "${stepTitle(stepId)} 완료됨"
            },
            completedSteps = updatedCompleted,
            steps = steps.map { step ->
                when (step.id) {
                    stepId -> step.copy(status = status)
                    else -> {
                        if (step.id in updatedCompleted) {
                            step.copy(status = StepStatus.COMPLETED)
                        } else {
                            step.copy(status = StepStatus.PENDING)
                        }
                    }
                }
            }
        )
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
        val updatedCompleted = completedSteps + result.completedStepIds
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
        val userMessage = error.message?.takeIf { it.isNotBlank() } ?: "분석에 실패했습니다. 사진을 다시 선택해 주세요."
        val errorText = if (error is AnalysisInputException) {
            userMessage
        } else {
            buildString {
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
        }
        return copy(
            isRunning = false,
            statusMessage = if (error is AnalysisInputException) {
                userMessage
            } else {
                "${stepTitle(stepId)} 실패"
            },
            jsonOutput = errorText,
            featureJsonOutput = errorText,
            outputFilePath = "",
            completedSteps = emptySet(),
            steps = steps.map { step ->
                if (error is AnalysisInputException) {
                    step.copy(status = StepStatus.PENDING)
                } else if (step.id in completedSteps) {
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


