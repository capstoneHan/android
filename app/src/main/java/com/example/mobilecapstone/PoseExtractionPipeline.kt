package com.example.mobilecapstone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ExtractionResult(
    val step: PipelineStep,
    val jsonOutput: String,
    val featureJsonOutput: String,
    val outputFilePath: String,
    val completedStepIds: Set<String> = setOf(step.id)
)

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED
}

data class PipelineStep(
    val id: String,
    val title: String,
    val status: StepStatus = StepStatus.PENDING
)

class AnalysisInputException(message: String) : IllegalArgumentException(message)

object PoseExtractionPipeline {
    private const val TAG = "PoseExtractionPipeline"
    private const val MAX_ANALYSIS_IMAGE_SIDE = 1280
    private const val ML_TASK_TIMEOUT_SECONDS = 30L
    private val pipelineLock = Any()

    val sampleAssets: List<String> = listOf(
        "female_average.jpeg",
        "female_plus.jpeg",
        "female_slim.jpeg",
        "male_average.jpeg",
        "male_slim.jpeg",
        "male_sturdy.jpeg",
    )

    fun initialSteps(): List<PipelineStep> = listOf(
        PipelineStep(id = "pose", title = "자세 인식"),
        PipelineStep(id = "silhouette", title = "실루엣 분석"),
        PipelineStep(id = "face", title = "얼굴형 분석"),
        PipelineStep(id = "color", title = "피부 톤 분석"),
        PipelineStep(id = "vibe", title = "스타일 태그 추출")
    )

    fun loadSampleBitmap(context: Context, assetName: String): Bitmap {
        context.assets.open(assetName).use { input ->
            val decoded = BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: error("Failed to decode asset: $assetName")
            return normalizedBitmap(decoded)
        }
    }

    fun loadImageBitmap(context: Context, imageUri: String): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(imageUri))
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
        return normalizedBitmap(decoded)
    }

    fun runPoseExtraction(
        context: Context,
        assetName: String,
        userBodyProfile: UserBodyProfile = UserBodyProfile(),
        onStepStatusChanged: ((String, StepStatus) -> Unit)? = null
    ): ExtractionResult = synchronized(pipelineLock) {
        runPoseExtractionLocked(
            context = context.applicationContext,
            bitmap = loadSampleBitmap(context, assetName),
            sourceName = assetName,
            userBodyProfile = userBodyProfile,
            onStepStatusChanged = onStepStatusChanged
        )
    }

    fun runPoseExtractionFromUri(
        context: Context,
        imageUri: String,
        userBodyProfile: UserBodyProfile = UserBodyProfile(),
        onStepStatusChanged: ((String, StepStatus) -> Unit)? = null
    ): ExtractionResult = synchronized(pipelineLock) {
        runPoseExtractionLocked(
            context = context.applicationContext,
            bitmap = loadImageBitmap(context, imageUri),
            sourceName = Uri.parse(imageUri).lastPathSegment ?: "selected_photo",
            userBodyProfile = userBodyProfile,
            onStepStatusChanged = onStepStatusChanged
        )
    }

    private fun runPoseExtractionLocked(
        context: Context,
        bitmap: Bitmap,
        sourceName: String,
        userBodyProfile: UserBodyProfile,
        onStepStatusChanged: ((String, StepStatus) -> Unit)? = null
    ): ExtractionResult {
        // 사진을 비트맵 변환하여 파이프라인에 넣을 수 있게 변환
        // 이미지 객체 생성
        val image = InputImage.fromBitmap(bitmap, 0)

        // 사진 한장만 분석
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()

        // 사진 한장만 분석
        val segmentationOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()

        // 얼굴 : 정확도 우선, 랜드마크(눈, 코, 입) 안찍기, 얼굴형 외곽 뽑기
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        // ML Kit 포즈 검출기 생성
        val detector = PoseDetection.getClient(poseOptions)
        // ML Kit 실루엣 분리기 생성
        val segmenter = Segmentation.getClient(segmentationOptions)
        // ML Kit 얼굴 검출기 생성
        val faceDetector = FaceDetection.getClient(faceOptions)
        // 파이프 라인 전체는 비동기, 파이프 라인 내부는 동기 방식으로
        // 디버깅을 쉽게하고 성능에 제약이 최소화되도록 파이프라인을 구성함
        try {
            onStepStatusChanged?.invoke("pose", StepStatus.RUNNING)
            val pose = awaitMlTask("pose") { detector.process(image) }
            validateFullBodyPose(pose)
            // pose로 체형 계산용 숫자를 뽑는과정
            val derivedMetrics = buildDerivedMetricsJson(pose)
            onStepStatusChanged?.invoke("pose", StepStatus.COMPLETED)

            // 얼굴 정보 추출
            onStepStatusChanged?.invoke("face", StepStatus.RUNNING)
            val faceDetection = detectFaceWithFallback(
                bitmap = bitmap,
                pose = pose,
                detector = faceDetector
            )

            // 얼굴 정보 JSON 화 하기
            val faceJson = buildFaceJson(faceDetection)
            onStepStatusChanged?.invoke("face", StepStatus.COMPLETED)

            // 색감 / 톤 추출
            onStepStatusChanged?.invoke("color", StepStatus.RUNNING)
            // 얼굴 영역 픽셀값을 기반으로 피부톤 분석
            val colorToneJson = buildColorToneJson(faceDetection)
            onStepStatusChanged?.invoke("color", StepStatus.COMPLETED)

            // 실루엣 추출
            onStepStatusChanged?.invoke("silhouette", StepStatus.RUNNING)
            // 사람 마스크 추출
            val segmentationMask = awaitMlTask("silhouette") { segmenter.process(image) }
            // 마스크 기반으로 어깨, 허리, 골반, 허벅지 폭 계산
            val silhouetteJson = buildSilhouetteJson(
                pose = pose,
                segmentationMask = segmentationMask,
                sourceImageWidth = bitmap.width,
                sourceImageHeight = bitmap.height
            )
            validateSilhouetteMetrics(silhouetteJson)
            onStepStatusChanged?.invoke("silhouette", StepStatus.COMPLETED)

            onStepStatusChanged?.invoke("vibe", StepStatus.RUNNING)
            val tagPayload = buildTagPayloadJson(
                derivedMetrics = derivedMetrics,
                silhouetteJson = silhouetteJson,
                faceJson = faceJson,
                colorToneJson = colorToneJson,
                userBodyProfile = userBodyProfile
            )
            onStepStatusChanged?.invoke("vibe", StepStatus.COMPLETED)

            // 추출된 원본 데이터 JSON 화
            val output = JSONObject()
                .put(
                    "meta",
                    JSONObject()
                        .put("pipeline_version", "mlkit-style-v2")
                        .put("generated_at", timestamp())
                        .put("source_image", sourceName)
                        .put("user_profile", buildUserProfileJson(userBodyProfile))
                )
                .put(
                    "pose",
                    JSONObject()
                        .put("landmark_count", pose.allPoseLandmarks.size)
                        .put("key_landmarks", buildKeyLandmarksJson(pose))
                        .put("derived_metrics", derivedMetrics)
                )
                .put("face", faceJson)
                .put("color_tone", colorToneJson)
                .put("silhouette", silhouetteJson)
                .put("tags", tagPayload)

            // 추천 / 후처리용 특징 데이터 JSON 화
            val featureOutput = buildFeatureJson(
                derivedMetrics = derivedMetrics,
                silhouetteJson = silhouetteJson,
                faceJson = faceJson,
                colorToneJson = colorToneJson,
                userBodyProfile = userBodyProfile,
                tagPayload = tagPayload
            )

            // 최종 결과 파일 저장
            val file = writeJsonFile(
                context = context,
                name = sourceName.substringBeforeLast('.').ifBlank { "selected_photo" } + "_analysis_result.json",
                contents = output.toString(2)
            )

            // 최종 결과 반환
            return ExtractionResult(
                step = PipelineStep(
                    id = "pose",
                    title = "스타일 분석",
                    status = StepStatus.COMPLETED
                ),
                jsonOutput = output.toString(2),
                featureJsonOutput = featureOutput.toString(2),
                outputFilePath = file.absolutePath,
                completedStepIds = setOf("pose", "face", "color", "silhouette", "vibe")
            )
        } finally {
            // 사용한 ML Kit detector 정리
            detector.close()
            segmenter.close()
            faceDetector.close()
        }
    }

    private fun buildKeyLandmarksJson(pose: Pose): JSONObject {
        return JSONObject()
            .put("nose", pointJson(pose.getPoseLandmark(PoseLandmark.NOSE)))
            .put("left_shoulder", pointJson(pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)))
            .put("right_shoulder", pointJson(pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)))
            .put("left_hip", pointJson(pose.getPoseLandmark(PoseLandmark.LEFT_HIP)))
            .put("right_hip", pointJson(pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)))
            .put("left_knee", pointJson(pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)))
            .put("right_knee", pointJson(pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)))
            .put("left_ankle", pointJson(pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)))
            .put("right_ankle", pointJson(pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)))
    }

    // pose에는 ML Kit가 잡은 랜드마크 좌표들이 들어 있음
    // 그 랜드마크들로 거리를 계산하여 숫자를 나타냄
    private fun buildDerivedMetricsJson(pose: Pose): JSONObject {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        // 좌우 어깨 랜드마크의 x축 거리
        val shoulderWidth = distanceX(leftShoulder, rightShoulder)

        // 좌우 골반 랜드마크의 x축 거리
        val hipWidth = distanceX(leftHip, rightHip)

        // 좌우 어깨의 평균 y값
        // 상체 시작선(어깨 중심선)처럼 사용
        val centerShoulderY = averageY(leftShoulder, rightShoulder)

        // 좌우 골반의 평균 y값
        // 상체와 하체를 나누는 기준선처럼 사용
        val centerHipY = averageY(leftHip, rightHip)

        // 좌우 발목의 평균 y값
        // 하체 끝점처럼 사용
        val centerAnkleY = averageY(leftAnkle, rightAnkle)

        return JSONObject()
            // 절대 어깨 너비
            .put("shoulder_width", shoulderWidth)

            // 절대 골반 너비
            .put("hip_width", hipWidth)

            // 어깨와 골반의 상대 비율
            // 1보다 크면 어깨가 골반보다 넓은 편
            .put("shoulder_to_hip_ratio", safeRatio(shoulderWidth, hipWidth))

            // 어깨 중심선부터 골반 중심선까지의 세로 길이
            // 상체 길이의 근사치
            .put("torso_height", absoluteDelta(centerShoulderY, centerHipY))

            // 골반 중심선부터 발목 중심선까지의 세로 길이
            // 다리 길이의 근사치
            .put("estimated_leg_length", absoluteDelta(centerHipY, centerAnkleY))

            // 코부터 발목 중심선까지의 세로 길이
            // 전신 높이의 근사치
            .put("estimated_full_body_height", absoluteDelta(nose?.position?.y, centerAnkleY))
    }

    private fun buildFeatureJson(
        derivedMetrics: JSONObject,
        silhouetteJson: JSONObject,
        faceJson: JSONObject,
        colorToneJson: JSONObject,
        userBodyProfile: UserBodyProfile,
        tagPayload: JSONObject
    ): JSONObject {
        val torsoHeight = derivedMetrics.optDouble("torso_height", 0.0)
        val legLength = derivedMetrics.optDouble("estimated_leg_length", 0.0)
        val fullBodyHeight = derivedMetrics.optDouble("estimated_full_body_height", 0.0)
        val torsoToLegRatio = safeRatio(torsoHeight.toFloat(), legLength.toFloat()).toDouble()
        val shoulderMaskWidth = silhouetteJson.optDouble("shoulder_width_mask", 0.0)
        val waistMaskWidth = silhouetteJson.optDouble("waist_width_mask", 0.0)
        val hipMaskWidth = silhouetteJson.optDouble("hip_width_mask", 0.0)
        val thighMaskWidth = silhouetteJson.optDouble("thigh_width_mask", 0.0)
        val shoulderToHipRatio = safeDoubleRatio(shoulderMaskWidth, hipMaskWidth)
        val waistToHipRatio = safeDoubleRatio(waistMaskWidth, hipMaskWidth)
        val waistToShoulderRatio = safeDoubleRatio(waistMaskWidth, shoulderMaskWidth)
        val silhouetteCoverage = silhouetteJson.optDouble("coverage_ratio", 0.0)
        val hipToShoulderRatio = silhouetteJson.optDouble("hip_to_shoulder_ratio", 0.0)
        val thighToHipRatio = silhouetteJson.optDouble("thigh_to_hip_ratio", 0.0)
        val shoulderWidthToHeightRatio = silhouetteJson.optDouble("shoulder_width_to_height_ratio", 0.0)
        val waistWidthToHeightRatio = silhouetteJson.optDouble("waist_width_to_height_ratio", 0.0)
        val hipWidthToHeightRatio = silhouetteJson.optDouble("hip_width_to_height_ratio", 0.0)
        val thighWidthToHeightRatio = silhouetteJson.optDouble("thigh_width_to_height_ratio", 0.0)
        val waistDefinition = waistDefinition(
            waistToHipRatio = waistToHipRatio,
            waistToShoulderRatio = waistToShoulderRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio,
            hipWidthToHeightRatio = hipWidthToHeightRatio
        )
        val silhouetteProfile = silhouetteProfile(
            waistToShoulderRatio = waistToShoulderRatio,
            waistToHipRatio = waistToHipRatio,
            hipToShoulderRatio = hipToShoulderRatio,
            thighToHipRatio = thighToHipRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio,
            hipWidthToHeightRatio = hipWidthToHeightRatio
        )
        val frameType = frameType(
            silhouetteProfile = silhouetteProfile,
            waistDefinition = waistDefinition,
            hipWidthToHeightRatio = hipWidthToHeightRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio
        )
        val upperLowerBalance = upperLowerBalance(torsoToLegRatio)
        val bodyMassJson = buildBodyMassJson(userBodyProfile)

        return JSONObject()
            .put("analysis_type", "normalized_body_features")
            .put("user_profile", buildUserProfileJson(userBodyProfile))
            .put(
                "body_ratio",
                JSONObject()
                    .put("shoulder_to_hip_ratio", shoulderToHipRatio)
                    .put("torso_to_leg_ratio", torsoToLegRatio)
                    .put("full_body_visibility", if (fullBodyHeight > 0.0) 1.0 else 0.0)
            )
            .put(
                "silhouette_features",
                JSONObject()
                    .put("frame_type", frameType)
                    .put("waist_definition", waistDefinition)
                    .put("body_volume", bodyVolumeProfile(waistWidthToHeightRatio, hipWidthToHeightRatio))
                    .put("upper_body_volume", volumeBand(shoulderMaskWidth, fullBodyHeight))
                    .put("lower_body_volume", volumeBand(thighMaskWidth, fullBodyHeight))
                    .put("coverage_ratio", silhouetteCoverage)
                    .put("shoulder_to_hip_ratio", shoulderToHipRatio)
                    .put("waist_to_hip_ratio", waistToHipRatio)
                    .put("waist_to_shoulder_ratio", waistToShoulderRatio)
                    .put("hip_to_shoulder_ratio", hipToShoulderRatio)
                    .put("thigh_to_hip_ratio", thighToHipRatio)
                    .put("shoulder_width_to_height_ratio", shoulderWidthToHeightRatio)
                    .put("waist_width_to_height_ratio", waistWidthToHeightRatio)
                    .put("hip_width_to_height_ratio", hipWidthToHeightRatio)
                    .put("thigh_width_to_height_ratio", thighWidthToHeightRatio)
                    .put("shoulder_width_mask", shoulderMaskWidth)
                    .put("waist_width_mask", waistMaskWidth)
                    .put("hip_width_mask", hipMaskWidth)
                    .put("thigh_width_mask", thighMaskWidth)
                    .put("shoulder_row_mask", silhouetteJson.optInt("shoulder_row_mask", 0))
                    .put("waist_row_mask", silhouetteJson.optInt("waist_row_mask", 0))
                    .put("hip_row_mask", silhouetteJson.optInt("hip_row_mask", 0))
                    .put("thigh_row_mask", silhouetteJson.optInt("thigh_row_mask", 0))
            )
            .put(
                "body_frame",
                JSONObject()
                    .put("shoulder_profile", shoulderProfile(shoulderToHipRatio))
                    .put("body_distribution", bodyDistribution(shoulderToHipRatio))
                    .put("upper_lower_balance", upperLowerBalance)
                    .put("silhouette_profile", silhouetteProfile)
            )
            .put("body_mass_features", bodyMassJson)
            .put(
                "face_features",
                JSONObject()
                    .put("shape", faceJson.optString("shape", "unknown"))
                    .put("shape_confidence", faceJson.optDouble("shape_confidence", 0.0))
                    .put("face_balance", faceJson.optString("face_balance", "unknown"))
            )
            .put(
                "color_features",
                JSONObject()
                    .put("undertone", colorToneJson.optString("undertone", "unknown"))
                    .put("clarity", colorToneJson.optString("clarity", "unknown"))
                    .put("dominant_skin_hex", colorToneJson.optString("dominant_skin_hex", "#000000"))
                    .put("brightness_band", colorToneJson.optString("brightness_band", "unknown"))
            )
            .put("tag_groups", tagPayload)
            .put("style_tags", tagPayload.optJSONArray("all_tags") ?: JSONArray())
    }

    private fun buildSilhouetteJson(
        pose: Pose,
        segmentationMask: com.google.mlkit.vision.segmentation.SegmentationMask,
        sourceImageWidth: Int,
        sourceImageHeight: Int
    ): JSONObject {
        val maskBuffer = segmentationMask.buffer
        maskBuffer.rewind()
        maskBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = maskBuffer.asFloatBuffer()
        val width = segmentationMask.width
        val height = segmentationMask.height

        val shoulderY = averageY(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        )
        val hipY = averageY(
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        )
        val kneeY = averageY(
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        )

        val shoulderRowMask = sourceYToMaskRow(shoulderY, sourceImageHeight, height)
        val hipBaseRowMask = sourceYToMaskRow(hipY, sourceImageHeight, height)
        val kneeBaseRowMask = sourceYToMaskRow(kneeY, sourceImageHeight, height)
        val (bodyTopRowMask, bodyBottomRowMask) = foregroundVerticalBounds(floatBuffer, width, height)

        val torsoHeightMask = (hipBaseRowMask - shoulderRowMask).coerceAtLeast(6)
        val shoulderSearchStart = (shoulderRowMask - max(2, torsoHeightMask / 10)).coerceAtLeast(0)
        val shoulderSearchEnd = (shoulderRowMask + max(2, torsoHeightMask / 8)).coerceAtMost(height - 1)
        val waistSearchStart = (shoulderRowMask + (torsoHeightMask * 0.18f).roundToInt()).coerceAtMost(height - 1)
        val waistSearchEnd = (shoulderRowMask + (torsoHeightMask * 0.72f).roundToInt()).coerceAtMost(height - 1)
        val hipSearchStart = (hipBaseRowMask - max(2, torsoHeightMask / 8)).coerceAtLeast(0)
        val hipSearchEnd = (hipBaseRowMask + max(2, torsoHeightMask / 10)).coerceAtMost(height - 1)
        val thighBaseRowMask = (hipBaseRowMask + ((kneeBaseRowMask - hipBaseRowMask) * 0.35f).roundToInt())
            .coerceIn(0, height - 1)
        val thighSearchStart = (thighBaseRowMask - max(2, torsoHeightMask / 12)).coerceAtLeast(0)
        val thighSearchEnd = (thighBaseRowMask + max(2, torsoHeightMask / 12)).coerceAtMost(height - 1)

        val shoulderMeasuredRowMask = findRowForExtremum(
            floatBuffer = floatBuffer,
            width = width,
            height = height,
            startRow = shoulderSearchStart,
            endRow = shoulderSearchEnd,
            mode = ExtremumMode.MAX
        )
        val waistRowMask = findRowForExtremum(
            floatBuffer = floatBuffer,
            width = width,
            height = height,
            startRow = waistSearchStart,
            endRow = waistSearchEnd,
            mode = ExtremumMode.MIN
        )
        val hipRowMask = findRowForExtremum(
            floatBuffer = floatBuffer,
            width = width,
            height = height,
            startRow = hipSearchStart,
            endRow = hipSearchEnd,
            mode = ExtremumMode.MAX
        )
        val thighRowMask = findRowForExtremum(
            floatBuffer = floatBuffer,
            width = width,
            height = height,
            startRow = thighSearchStart,
            endRow = thighSearchEnd,
            mode = ExtremumMode.MAX
        )

        val shoulderWidthMask = bandWidth(floatBuffer, width, height, shoulderMeasuredRowMask)
        val waistWidthMask = bandWidth(floatBuffer, width, height, waistRowMask)
        val hipWidthMask = bandWidth(floatBuffer, width, height, hipRowMask)
        val thighWidthMask = bandWidth(floatBuffer, width, height, thighRowMask)
        val coverageRatio = foregroundCoverage(floatBuffer, width, height)
        val bodyHeightMask = (bodyBottomRowMask - bodyTopRowMask).coerceAtLeast(1)

        return JSONObject()
            .put("mask_width", width)
            .put("mask_height", height)
            .put("source_image_width", sourceImageWidth)
            .put("source_image_height", sourceImageHeight)
            .put("body_top_row_mask", bodyTopRowMask)
            .put("body_bottom_row_mask", bodyBottomRowMask)
            .put("body_height_mask", bodyHeightMask)
            .put("coverage_ratio", coverageRatio)
            .put("shoulder_row_mask", shoulderMeasuredRowMask)
            .put("waist_row_mask", waistRowMask)
            .put("hip_row_mask", hipRowMask)
            .put("thigh_row_mask", thighRowMask)
            .put("shoulder_width_mask", shoulderWidthMask)
            .put("waist_width_mask", waistWidthMask)
            .put("hip_width_mask", hipWidthMask)
            .put("thigh_width_mask", thighWidthMask)
            .put("waist_to_hip_ratio", safeRatio(waistWidthMask, hipWidthMask))
            .put("waist_to_shoulder_ratio", safeRatio(waistWidthMask, shoulderWidthMask))
            .put("hip_to_shoulder_ratio", safeRatio(hipWidthMask, shoulderWidthMask))
            .put("thigh_to_hip_ratio", safeRatio(thighWidthMask, hipWidthMask))
            .put("shoulder_width_to_height_ratio", safeRatio(shoulderWidthMask, bodyHeightMask.toFloat()))
            .put("waist_width_to_height_ratio", safeRatio(waistWidthMask, bodyHeightMask.toFloat()))
            .put("hip_width_to_height_ratio", safeRatio(hipWidthMask, bodyHeightMask.toFloat()))
            .put("thigh_width_to_height_ratio", safeRatio(thighWidthMask, bodyHeightMask.toFloat()))
    }

    private fun buildTagPayloadJson(
        derivedMetrics: JSONObject,
        silhouetteJson: JSONObject,
        faceJson: JSONObject,
        colorToneJson: JSONObject,
        userBodyProfile: UserBodyProfile
    ): JSONObject {
        val shoulderMaskWidth = silhouetteJson.optDouble("shoulder_width_mask", 0.0)
        val hipMaskWidth = silhouetteJson.optDouble("hip_width_mask", 0.0)
        val shoulderToHipRatio = safeDoubleRatio(shoulderMaskWidth, hipMaskWidth)
        val waistToHipRatio = silhouetteJson.optDouble("waist_to_hip_ratio", 0.0)
        val waistToShoulderRatio = safeDoubleRatio(
            silhouetteJson.optDouble("waist_width_mask", 0.0),
            shoulderMaskWidth
        )
        val hipToShoulderRatio = silhouetteJson.optDouble("hip_to_shoulder_ratio", 0.0)
        val thighToHipRatio = silhouetteJson.optDouble("thigh_to_hip_ratio", 0.0)
        val waistWidthToHeightRatio = silhouetteJson.optDouble("waist_width_to_height_ratio", 0.0)
        val hipWidthToHeightRatio = silhouetteJson.optDouble("hip_width_to_height_ratio", 0.0)
        val frameType = frameType(
            silhouetteProfile = silhouetteProfile(
                waistToShoulderRatio = waistToShoulderRatio,
                waistToHipRatio = waistToHipRatio,
                hipToShoulderRatio = hipToShoulderRatio,
                thighToHipRatio = thighToHipRatio,
                waistWidthToHeightRatio = waistWidthToHeightRatio,
                hipWidthToHeightRatio = hipWidthToHeightRatio
            ),
            waistDefinition = waistDefinition(
                waistToHipRatio = waistToHipRatio,
                waistToShoulderRatio = waistToShoulderRatio,
                waistWidthToHeightRatio = waistWidthToHeightRatio,
                hipWidthToHeightRatio = hipWidthToHeightRatio
            ),
            hipWidthToHeightRatio = hipWidthToHeightRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio
        )
        val waistDefinition = waistDefinition(
            waistToHipRatio = waistToHipRatio,
            waistToShoulderRatio = waistToShoulderRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio,
            hipWidthToHeightRatio = hipWidthToHeightRatio
        )
        val silhouetteProfile = silhouetteProfile(
            waistToShoulderRatio = waistToShoulderRatio,
            waistToHipRatio = waistToHipRatio,
            hipToShoulderRatio = hipToShoulderRatio,
            thighToHipRatio = thighToHipRatio,
            waistWidthToHeightRatio = waistWidthToHeightRatio,
            hipWidthToHeightRatio = hipWidthToHeightRatio
        )
        val bodyDistribution = bodyDistribution(shoulderToHipRatio)
        val bodyVolume = bodyVolumeProfile(waistWidthToHeightRatio, hipWidthToHeightRatio)
        val bodyMassJson = buildBodyMassJson(userBodyProfile)
        val faceShape = faceJson.optString("shape", "unknown")
        val faceAspectRatio = faceJson.optJSONObject("metrics")?.optDouble("aspect_ratio", 0.0)?.toFloat() ?: 0f
        val undertone = colorToneJson.optString("undertone", "unknown")
        val clarity = colorToneJson.optString("clarity", "unknown")
        val brightness = colorToneJson.optString("brightness_band", "unknown")

        val bodyRatioTags = mutableListOf<String>()
        when (bodyDistribution) {
            "upper_body_developed" -> bodyRatioTags += "upper_body_emphasis"
            "lower_body_developed" -> bodyRatioTags += "lower_body_emphasis"
            "balanced" -> bodyRatioTags += "balanced_proportion"
        }

        val silhouetteTags = mutableListOf<String>()
        if (frameType != "unknown") {
            silhouetteTags += frameType
        }
        silhouetteTagForWaist(waistDefinition)?.let(silhouetteTags::add)
        silhouetteTagForVolume(bodyVolume)?.let(silhouetteTags::add)
        silhouetteTagForProfile(silhouetteProfile)?.let(silhouetteTags::add)

        val profileTags = mutableListOf<String>()
        profileTagForHeight(bodyMassJson.optString("height_band", "unknown"))?.let(profileTags::add)
        profileTagForBmi(bodyMassJson.optString("bmi_band", "unknown"))?.let(profileTags::add)
        profileTagForFit(
            bmiBand = bodyMassJson.optString("bmi_band", "unknown"),
            bodyVolume = bodyVolume
        )?.let(profileTags::add)

        val faceTags = mutableListOf<String>()
        faceTag(faceShape, faceAspectRatio)?.let(faceTags::add)

        val toneTags = mutableListOf<String>()
        toneTag(undertone, clarity, brightness)?.let(toneTags::add)

        val allTags = linkedSetOf<String>()
        listOf(bodyRatioTags, silhouetteTags, profileTags, faceTags, toneTags).forEach { group ->
            group.filterTo(allTags) { it.isNotBlank() }
        }

        return JSONObject()
            .put("body_ratio_tags", JSONArray(bodyRatioTags))
            .put("silhouette_tags", JSONArray(silhouetteTags))
            .put("profile_tags", JSONArray(profileTags))
            .put("face_tags", JSONArray(faceTags))
            .put("tone_tags", JSONArray(toneTags))
            .put("all_tags", JSONArray(allTags.toList()))
    }

    private fun validateFullBodyPose(pose: Pose) {
        val shoulderConfidence = averageLikelihood(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        )
        val hipConfidence = averageLikelihood(
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        )
        val kneeConfidence = averageLikelihood(
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        )
        val ankleConfidence = averageLikelihood(
            pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        )
        val visibleBodyScore = listOf(shoulderConfidence, hipConfidence, kneeConfidence, ankleConfidence)
            .count { it >= 0.35f }

        if (pose.allPoseLandmarks.size < 12 || shoulderConfidence < 0.35f || hipConfidence < 0.35f || visibleBodyScore < 3) {
            throw AnalysisInputException("사람의 전신을 충분히 인식하지 못했습니다. 머리부터 발까지 보이는 사진으로 다시 촬영하거나 선택해 주세요.")
        }
    }

    private fun validateSilhouetteMetrics(silhouetteJson: JSONObject) {
        val shoulderWidth = silhouetteJson.optDouble("shoulder_width_mask", 0.0)
        val hipWidth = silhouetteJson.optDouble("hip_width_mask", 0.0)
        val thighWidth = silhouetteJson.optDouble("thigh_width_mask", 0.0)
        val bodyHeight = silhouetteJson.optDouble("body_height_mask", 0.0)
        val coverage = silhouetteJson.optDouble("coverage_ratio", 0.0)

        if (bodyHeight <= 0.0 || coverage < 0.01 || shoulderWidth <= 0.0 || hipWidth <= 0.0 || thighWidth <= 0.0) {
            throw AnalysisInputException("사람 실루엣 값을 충분히 추출하지 못했습니다. 배경과 사람이 분리되고 전신이 보이는 사진으로 다시 선택해 주세요.")
        }
    }

    private fun pointJson(landmark: PoseLandmark?): Any {
        if (landmark == null) return JSONObject.NULL
        return JSONObject()
            .put("x", landmark.position.x)
            .put("y", landmark.position.y)
            .put("z", landmark.position3D.z)
            .put("in_frame_likelihood", landmark.inFrameLikelihood)
    }

    private fun buildFaceJson(face: Face?): JSONObject {
        if (face == null) {
            return JSONObject()
                .put("detected", false)
                .put("shape", "unknown")
                .put("shape_confidence", 0.0)
                .put("face_balance", "unknown")
        }
        return buildFaceJson(
            FaceDetectionResult(
                face = face,
                bitmap = null,
                source = "full_image",
                cropRect = null
            )
        )
    }

    private fun buildFaceJson(faceDetection: FaceDetectionResult?): JSONObject {
        val face = faceDetection?.face
        if (face == null) {
            return JSONObject()
                .put("detected", false)
                .put("shape", "unknown")
                .put("shape_confidence", 0.0)
                .put("face_balance", "unknown")
        }

        val contourPoints = face.getContour(FaceContour.FACE)?.points.orEmpty()
        val contourBounds = boundsOf(contourPoints)
        val width = contourBounds?.let { (it.right - it.left).toFloat() } ?: face.boundingBox.width().toFloat()
        val height = contourBounds?.let { (it.bottom - it.top).toFloat() } ?: face.boundingBox.height().toFloat()
        val aspectRatio = safeRatio(height, width)
        val cheekWidth = contourWidthAtNormalizedY(contourPoints, 0.48f, contourBounds)
        val foreheadWidth = contourWidthAtNormalizedY(contourPoints, 0.22f, contourBounds)
        val jawWidth = contourWidthAtNormalizedY(contourPoints, 0.78f, contourBounds)
        val chinTaper = safeRatio(jawWidth, cheekWidth)
        val foreheadToJawRatio = safeRatio(foreheadWidth, max(jawWidth, 1f))
        val (shape, confidence) = classifyFaceShape(
            aspectRatio = aspectRatio,
            chinTaper = chinTaper,
            foreheadToJawRatio = foreheadToJawRatio
        )

        return JSONObject()
            .put("detected", true)
            .put("detected_from", faceDetection.source)
            .put("bounding_box", JSONObject()
                .put("left", face.boundingBox.left)
                .put("top", face.boundingBox.top)
                .put("right", face.boundingBox.right)
                .put("bottom", face.boundingBox.bottom)
            )
            .put("crop_box", faceDetection.cropRect?.let {
                JSONObject()
                    .put("left", it.left)
                    .put("top", it.top)
                    .put("right", it.right)
                    .put("bottom", it.bottom)
            } ?: JSONObject.NULL)
            .put("shape", shape)
            .put("shape_confidence", confidence)
            .put("face_balance", faceBalance(shape, aspectRatio))
            .put("metrics", JSONObject()
                .put("aspect_ratio", aspectRatio)
                .put("cheek_width", cheekWidth)
                .put("forehead_width", foreheadWidth)
                .put("jaw_width", jawWidth)
                .put("chin_taper_ratio", chinTaper)
                .put("forehead_to_jaw_ratio", foreheadToJawRatio)
            )
    }

    private fun buildColorToneJson(faceDetection: FaceDetectionResult?): JSONObject {
        val face = faceDetection?.face
        val bitmap = faceDetection?.bitmap
        if (face == null || bitmap == null) {
            return JSONObject()
                .put("detected", false)
                .put("undertone", "unknown")
                .put("clarity", "unknown")
                .put("brightness_band", "unknown")
                .put("dominant_skin_hex", "#000000")
                .put("sample_count", 0)
        }

        val box = face.boundingBox
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top

        var sampleCount = 0
        var redSum = 0.0
        var greenSum = 0.0
        var blueSum = 0.0
        var satSum = 0.0
        var valueSum = 0.0
        val hsv = FloatArray(3)

        val sampleTop = top + (height * 0.20f).roundToInt()
        val sampleBottom = top + (height * 0.72f).roundToInt()
        val sampleLeft = left + (width * 0.18f).roundToInt()
        val sampleRight = left + (width * 0.82f).roundToInt()

        val centerX = (sampleLeft + sampleRight) / 2f
        val centerY = (sampleTop + sampleBottom) / 2f
        val radiusX = max((sampleRight - sampleLeft) / 2f, 1f)
        val radiusY = max((sampleBottom - sampleTop) / 2f, 1f)

        for (y in sampleTop until sampleBottom step 2) {
            for (x in sampleLeft until sampleRight step 2) {
                val normX = (x - centerX) / radiusX
                val normY = (y - centerY) / radiusY
                if ((normX * normX) + (normY * normY) > 0.92f) continue

                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                Color.colorToHSV(color, hsv)

                if (hsv[2] < 0.18f || hsv[2] > 0.97f) continue
                if (hsv[1] > 0.68f) continue

                sampleCount++
                redSum += r
                greenSum += g
                blueSum += b
                satSum += hsv[1]
                valueSum += hsv[2]
            }
        }

        if (sampleCount == 0) {
            return JSONObject()
                .put("detected", false)
                .put("undertone", "unknown")
                .put("clarity", "unknown")
                .put("brightness_band", "unknown")
                .put("dominant_skin_hex", "#000000")
                .put("sample_count", 0)
        }

        val avgRed = redSum / sampleCount
        val avgGreen = greenSum / sampleCount
        val avgBlue = blueSum / sampleCount
        val avgSaturation = satSum / sampleCount
        val avgValue = valueSum / sampleCount
        val warmthDelta = (avgRed - avgBlue) / 255.0

        return JSONObject()
            .put("detected", true)
            .put("sampled_from", faceDetection.source)
            .put("undertone", classifyUndertone(warmthDelta, avgGreen / 255.0))
            .put("clarity", classifyClarity(avgSaturation, avgValue))
            .put("brightness_band", classifyBrightness(avgValue))
            .put("dominant_skin_hex", rgbToHex(avgRed, avgGreen, avgBlue))
            .put("sample_count", sampleCount)
            .put("metrics", JSONObject()
                .put("avg_red", avgRed)
                .put("avg_green", avgGreen)
                .put("avg_blue", avgBlue)
                .put("avg_saturation", avgSaturation)
                .put("avg_value", avgValue)
                .put("warmth_delta", warmthDelta)
            )
    }

    private fun writeJsonFile(context: Context, name: String, contents: String): File {
        val outputDir = File(context.filesDir, "pipeline-results").apply { mkdirs() }
        val outputFile = File(outputDir, name)
        outputFile.writeText(contents)
        return outputFile
    }

    private fun normalizedBitmap(source: Bitmap): Bitmap {
        val longestSide = max(source.width, source.height)
        val scaled = if (longestSide > MAX_ANALYSIS_IMAGE_SIDE) {
            val scale = MAX_ANALYSIS_IMAGE_SIDE.toFloat() / longestSide
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        val normalized = if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, false)
        }

        if (source !== scaled && !source.isRecycled) {
            source.recycle()
        }
        if (scaled !== normalized && !scaled.isRecycled) {
            scaled.recycle()
        }
        return normalized
    }

    private fun <T> awaitMlTask(stepName: String, block: () -> Task<T>): T {
        val startedAt = System.currentTimeMillis()
        return try {
            Tasks.await(block(), ML_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            Log.d(TAG, "$stepName finished in ${System.currentTimeMillis() - startedAt}ms")
        }
    }

    private fun detectFaceWithFallback(
        bitmap: Bitmap,
        pose: Pose,
        detector: com.google.mlkit.vision.face.FaceDetector
    ): FaceDetectionResult {
        val fullImage = InputImage.fromBitmap(bitmap, 0)
        val fullFace = awaitMlTask("face_full") { detector.process(fullImage) }
            .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (fullFace != null) {
            return FaceDetectionResult(
                face = fullFace,
                bitmap = bitmap,
                source = "full_image",
                cropRect = null
            )
        }

        val cropRect = estimateFaceCropRect(bitmap, pose) ?: return FaceDetectionResult(
            face = null,
            bitmap = null,
            source = "none",
            cropRect = null
        )
        val cropBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
        val croppedImage = InputImage.fromBitmap(cropBitmap, 0)
        val croppedFace = awaitMlTask("face_crop") { detector.process(croppedImage) }
            .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        return FaceDetectionResult(
            face = croppedFace,
            bitmap = if (croppedFace != null) cropBitmap else null,
            source = if (croppedFace != null) "pose_guided_crop" else "none",
            cropRect = cropRect
        )
    }

    private fun estimateFaceCropRect(bitmap: Bitmap, pose: Pose): Rect? {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE) ?: return null
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null

        val shoulderWidth = distanceX(leftShoulder, rightShoulder)
        if (shoulderWidth <= 0f) return null

        val shoulderCenterX = (leftShoulder.position.x + rightShoulder.position.x) / 2f
        val shoulderCenterY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val faceCenterX = ((nose.position.x * 0.7f) + (shoulderCenterX * 0.3f))
        val faceCenterY = nose.position.y + ((shoulderCenterY - nose.position.y) * 0.28f)
        val cropWidth = shoulderWidth * 1.9f
        val cropHeight = shoulderWidth * 2.35f

        val left = (faceCenterX - cropWidth / 2f).roundToInt().coerceIn(0, bitmap.width - 2)
        val top = (faceCenterY - cropHeight * 0.42f).roundToInt().coerceIn(0, bitmap.height - 2)
        val right = (faceCenterX + cropWidth / 2f).roundToInt().coerceIn(left + 2, bitmap.width)
        val bottom = (top + cropHeight.roundToInt()).coerceIn(top + 2, bitmap.height)

        if (right - left < 32 || bottom - top < 32) return null
        return Rect(left, top, right, bottom)
    }

    private fun distanceX(first: PoseLandmark?, second: PoseLandmark?): Float {
        if (first == null || second == null) return 0f
        return abs(first.position.x - second.position.x)
    }

    private fun averageY(first: PoseLandmark?, second: PoseLandmark?): Float {
        if (first == null || second == null) return 0f
        return (first.position.y + second.position.y) / 2f
    }

    private fun absoluteDelta(first: Float?, second: Float?): Float {
        if (first == null || second == null) return 0f
        return abs(first - second)
    }

    private fun safeRatio(numerator: Float, denominator: Float): Float {
        if (denominator == 0f) return 0f
        return numerator / denominator
    }

    private fun bandWidth(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        height: Int,
        centerRow: Int,
        threshold: Float = 0.35f
    ): Float {
        if (width <= 0 || height <= 0) return 0f
        val radius = max(1, height / 80)
        val widths = mutableListOf<Float>()
        for (offset in -radius..radius) {
            val row = (centerRow + offset).coerceIn(0, height - 1)
            val rowWidth = singleRowWidth(floatBuffer, width, row, threshold)
            if (rowWidth > 0f) {
                widths += rowWidth
            }
        }
        if (widths.isEmpty()) return 0f
        return widths.average().toFloat()
    }

    private enum class ExtremumMode {
        MIN,
        MAX
    }

    private fun findRowForExtremum(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        height: Int,
        startRow: Int,
        endRow: Int,
        mode: ExtremumMode
    ): Int {
        val safeStart = startRow.coerceIn(0, height - 1)
        val safeEnd = endRow.coerceIn(safeStart, height - 1)
        var selectedRow = safeStart
        var selectedWidth = if (mode == ExtremumMode.MIN) Float.POSITIVE_INFINITY else 0f

        for (row in safeStart..safeEnd) {
            val widthAtRow = bandWidth(floatBuffer, width, height, row)
            if (widthAtRow <= 0f) continue

            val shouldReplace = when (mode) {
                ExtremumMode.MIN -> widthAtRow < selectedWidth
                ExtremumMode.MAX -> widthAtRow > selectedWidth
            }
            if (shouldReplace) {
                selectedWidth = widthAtRow
                selectedRow = row
            }
        }

        return selectedRow
    }

    private fun singleRowWidth(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        row: Int,
        threshold: Float
    ): Float {
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        for (x in 0 until width) {
            val confidence = floatBuffer.get((row * width) + x)
            if (confidence >= threshold) {
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
            }
        }
        if (minX == Int.MAX_VALUE || maxX == Int.MIN_VALUE) return 0f
        return (maxX - minX).toFloat()
    }

    private fun sourceYToMaskRow(sourceY: Float, sourceImageHeight: Int, maskHeight: Int): Int {
        if (sourceImageHeight <= 0 || maskHeight <= 0) return 0
        val normalized = (sourceY / sourceImageHeight.toFloat()).coerceIn(0f, 1f)
        return (normalized * (maskHeight - 1)).roundToInt().coerceIn(0, maskHeight - 1)
    }

    private fun foregroundCoverage(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        height: Int,
        threshold: Float = 0.35f
    ): Float {
        if (width <= 0 || height <= 0) return 0f
        var foregroundCount = 0
        val total = width * height
        for (index in 0 until total) {
            if (floatBuffer.get(index) >= threshold) {
                foregroundCount++
            }
        }
        return foregroundCount.toFloat() / total.toFloat()
    }

    private fun foregroundVerticalBounds(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        height: Int,
        threshold: Float = 0.35f
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        var minRow = height - 1
        var maxRow = 0

        for (row in 0 until height) {
            for (x in 0 until width) {
                if (floatBuffer.get((row * width) + x) >= threshold) {
                    minRow = minOf(minRow, row)
                    maxRow = maxOf(maxRow, row)
                    break
                }
            }
        }

        return if (maxRow < minRow) {
            0 to (height - 1)
        } else {
            minRow to maxRow
        }
    }

    private fun averageLikelihood(vararg landmarks: PoseLandmark?): Float {
        val valid = landmarks.mapNotNull { it?.inFrameLikelihood }
        if (valid.isEmpty()) return 0f
        return valid.sum() / valid.size
    }

    private fun boundsOf(points: List<PointF>): android.graphics.Rect? {
        if (points.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        points.forEach { point ->
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }
        return android.graphics.Rect(
            minX.roundToInt(),
            minY.roundToInt(),
            maxX.roundToInt(),
            maxY.roundToInt()
        )
    }

    private fun contourWidthAtNormalizedY(
        contourPoints: List<PointF>,
        normalizedY: Float,
        bounds: android.graphics.Rect?
    ): Float {
        if (contourPoints.isEmpty() || bounds == null) return 0f
        val targetY = bounds.top + (bounds.height() * normalizedY)
        val nearPoints = contourPoints.filter { abs(it.y - targetY) <= bounds.height() * 0.08f }
        if (nearPoints.size < 2) return 0f
        val minX = nearPoints.minOf { it.x }
        val maxX = nearPoints.maxOf { it.x }
        return maxX - minX
    }

    private fun classifyFaceShape(
        aspectRatio: Float,
        chinTaper: Float,
        foreheadToJawRatio: Float
    ): Pair<String, Double> {
        return when {
            aspectRatio >= 1.58f -> "oblong" to 0.72
            foreheadToJawRatio >= 1.18f && chinTaper <= 0.78f -> "heart" to 0.7
            aspectRatio <= 1.22f && chinTaper >= 0.9f -> "round" to 0.76
            aspectRatio <= 1.38f && chinTaper >= 0.88f -> "square" to 0.73
            aspectRatio in 1.28f..1.55f && chinTaper in 0.72f..0.9f -> "oval" to 0.78
            else -> "balanced" to 0.52
        }
    }

    private fun faceBalance(shape: String, aspectRatio: Float): String {
        return when {
            shape == "oblong" || aspectRatio >= 1.55f -> "length_emphasized"
            shape == "round" -> "soft_width_emphasized"
            shape == "square" -> "structured_width_emphasized"
            else -> "balanced"
        }
    }

    private fun classifyUndertone(warmthDelta: Double, greenRatio: Double): String {
        return when {
            warmthDelta >= 0.09 -> "warm"
            warmthDelta <= 0.01 && greenRatio < 0.58 -> "cool"
            else -> "neutral"
        }
    }

    private fun classifyClarity(avgSaturation: Double, avgValue: Double): String {
        return when {
            avgSaturation >= 0.34 && avgValue >= 0.58 -> "clear"
            avgSaturation <= 0.22 || avgValue <= 0.46 -> "muted"
            else -> "balanced"
        }
    }

    private fun classifyBrightness(avgValue: Double): String {
        return when {
            avgValue >= 0.72 -> "light"
            avgValue >= 0.5 -> "medium"
            else -> "deep"
        }
    }

    private fun rgbToHex(red: Double, green: Double, blue: Double): String {
        val r = red.roundToInt().coerceIn(0, 255)
        val g = green.roundToInt().coerceIn(0, 255)
        val b = blue.roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private data class FaceDetectionResult(
        val face: Face?,
        val bitmap: Bitmap?,
        val source: String,
        val cropRect: Rect?
    )

    private fun shoulderProfile(shoulderToHipRatio: Double): String {
        return when {
            shoulderToHipRatio >= 1.08 -> "broad_relative_to_hip"
            shoulderToHipRatio <= 0.92 -> "narrow_relative_to_hip"
            else -> "balanced"
        }
    }

    private fun bodyDistribution(shoulderToHipRatio: Double): String {
        return when {
            shoulderToHipRatio == 0.0 -> "unknown"
            shoulderToHipRatio >= 1.08 -> "upper_body_developed"
            shoulderToHipRatio <= 0.92 -> "lower_body_developed"
            else -> "balanced"
        }
    }

    private fun upperLowerBalance(torsoToLegRatio: Double): String {
        return when {
            torsoToLegRatio <= 0.76 -> "lower_body_emphasized"
            torsoToLegRatio >= 1.0 -> "upper_body_emphasized"
            else -> "balanced"
        }
    }

    private fun waistDefinition(
        waistToHipRatio: Double,
        waistToShoulderRatio: Double,
        waistWidthToHeightRatio: Double,
        hipWidthToHeightRatio: Double
    ): String {
        if (
            waistToHipRatio == 0.0 ||
            waistToShoulderRatio == 0.0 ||
            waistWidthToHeightRatio == 0.0 ||
            hipWidthToHeightRatio == 0.0
        ) {
            return "unknown"
        }

        val fullnessGap = hipWidthToHeightRatio - waistWidthToHeightRatio
        return when {
            waistToHipRatio <= 0.72 && fullnessGap >= 0.07 && waistWidthToHeightRatio <= 0.29 -> "defined"
            waistToHipRatio <= 0.80 && fullnessGap >= 0.045 && waistWidthToHeightRatio <= 0.33 -> "moderate"
            waistToHipRatio >= 0.90 && waistToShoulderRatio >= 0.98 && fullnessGap <= 0.06 -> "straight"
            fullnessGap < 0.04 && waistWidthToHeightRatio >= 0.30 -> "straight"
            else -> "straight"
        }.let { candidate ->
            when {
                candidate == "defined" && waistWidthToHeightRatio >= 0.30 -> "moderate"
                candidate == "moderate" && waistWidthToHeightRatio >= 0.34 -> "soft"
                candidate == "straight" && fullnessGap >= 0.06 -> "soft"
                candidate == "straight" && waistToHipRatio < 0.88 -> "soft"
                candidate == "soft" && waistToHipRatio <= 0.76 && fullnessGap >= 0.06 -> "moderate"
                else -> candidate
            }
        }
    }

    private fun frameType(
        silhouetteProfile: String,
        waistDefinition: String,
        hipWidthToHeightRatio: Double,
        waistWidthToHeightRatio: Double
    ): String {
        if (silhouetteProfile == "unknown" || waistDefinition == "unknown") return "unknown"
        return when {
            silhouetteProfile == "defined_curve" && waistDefinition in listOf("defined", "moderate") -> "defined_frame"
            silhouetteProfile == "straight_line" && waistDefinition == "straight" && waistWidthToHeightRatio >= 0.30 -> "straight_frame"
            hipWidthToHeightRatio >= 0.40 && waistDefinition == "straight" -> "straight_frame"
            else -> "balanced_frame"
        }
    }

    private fun silhouetteProfile(
        waistToShoulderRatio: Double,
        waistToHipRatio: Double,
        hipToShoulderRatio: Double,
        thighToHipRatio: Double,
        waistWidthToHeightRatio: Double,
        hipWidthToHeightRatio: Double
    ): String {
        if (
            waistToShoulderRatio == 0.0 ||
            waistToHipRatio == 0.0 ||
            waistWidthToHeightRatio == 0.0 ||
            hipWidthToHeightRatio == 0.0
        ) {
            return "unknown"
        }

        val fullnessGap = hipWidthToHeightRatio - waistWidthToHeightRatio
        return when {
            waistToHipRatio <= 0.78 &&
                fullnessGap >= 0.05 &&
                waistWidthToHeightRatio <= 0.30 &&
                thighToHipRatio <= 1.18 -> "defined_curve"
            waistToHipRatio >= 0.92 &&
                waistToShoulderRatio >= 1.0 &&
                fullnessGap <= 0.05 -> "straight_line"
            waistWidthToHeightRatio >= 0.31 &&
                fullnessGap <= 0.04 &&
                hipToShoulderRatio >= 1.10 -> "straight_line"
            waistToHipRatio <= 0.88 &&
                fullnessGap >= 0.035 &&
                hipToShoulderRatio >= 1.12 -> "balanced_line"
            else -> "balanced_line"
        }
    }

    private fun bodyVolumeProfile(
        waistWidthToHeightRatio: Double,
        hipWidthToHeightRatio: Double
    ): String {
        if (waistWidthToHeightRatio == 0.0 || hipWidthToHeightRatio == 0.0) return "unknown"
        val averageWidthRatio = (waistWidthToHeightRatio + hipWidthToHeightRatio) / 2.0
        return when {
            averageWidthRatio < 0.28 -> "slim_volume"
            averageWidthRatio > 0.36 -> "full_volume"
            else -> "balanced_volume"
        }
    }

    private fun silhouetteTagForWaist(waistDefinition: String): String? {
        return when (waistDefinition) {
            "defined" -> "waist_defined"
            "moderate" -> "moderate_waist_line"
            "soft" -> "soft_waist_curve"
            "straight" -> "straight_waist_line"
            else -> null
        }
    }

    private fun silhouetteTagForVolume(bodyVolume: String): String? {
        return when (bodyVolume) {
            "slim_volume" -> "slim_volume"
            "balanced_volume" -> "balanced_volume"
            "full_volume" -> "full_volume"
            else -> null
        }
    }

    private fun silhouetteTagForProfile(silhouetteProfile: String): String? {
        return when (silhouetteProfile) {
            "defined_curve" -> "defined_silhouette"
            "balanced_line" -> "balanced_silhouette"
            "straight_line" -> "straight_body_line"
            else -> null
        }
    }

    private fun buildUserProfileJson(userBodyProfile: UserBodyProfile): JSONObject {
        return JSONObject()
            .put("height_cm", userBodyProfile.heightCm ?: JSONObject.NULL)
            .put("weight_kg", userBodyProfile.weightKg ?: JSONObject.NULL)
    }

    private fun buildBodyMassJson(userBodyProfile: UserBodyProfile): JSONObject {
        val heightCm = userBodyProfile.heightCm
        val weightKg = userBodyProfile.weightKg
        val bmi = if (heightCm != null && weightKg != null && heightCm > 0.0 && weightKg > 0.0) {
            val heightM = heightCm / 100.0
            weightKg / (heightM * heightM)
        } else {
            0.0
        }

        return JSONObject()
            .put("bmi", bmi)
            .put("bmi_band", bmiBand(bmi))
            .put("height_band", heightBand(heightCm))
    }

    private fun bmiBand(bmi: Double): String {
        return when {
            bmi == 0.0 -> "unknown"
            bmi < 18.5 -> "low_body_mass"
            bmi < 23.0 -> "balanced_body_mass"
            bmi < 25.0 -> "soft_full_body_mass"
            else -> "full_body_mass"
        }
    }

    private fun heightBand(heightCm: Double?): String {
        val height = heightCm ?: return "unknown"
        return when {
            height <= 0.0 -> "unknown"
            height < 160.0 -> "compact_height"
            height <= 170.0 -> "average_height"
            else -> "tall_height"
        }
    }

    private fun profileTagForHeight(heightBand: String): String? {
        return when (heightBand) {
            "compact_height" -> "petite_proportion_friendly"
            "average_height" -> "standard_length_friendly"
            "tall_height" -> "long_length_friendly"
            else -> null
        }
    }

    private fun profileTagForBmi(bmiBand: String): String? {
        return when (bmiBand) {
            "low_body_mass" -> "slim_fit_friendly"
            "balanced_body_mass" -> "regular_fit_friendly"
            "soft_full_body_mass" -> "comfort_fit_friendly"
            "full_body_mass" -> "relaxed_fit_friendly"
            else -> null
        }
    }

    private fun profileTagForFit(bmiBand: String, bodyVolume: String): String? {
        return when {
            bmiBand == "low_body_mass" && bodyVolume == "slim_volume" -> "volume_layering_recommended"
            bmiBand in listOf("soft_full_body_mass", "full_body_mass") -> "clean_vertical_fit_recommended"
            bodyVolume == "full_volume" -> "structured_balance_recommended"
            else -> null
        }
    }

    private fun faceTag(faceShape: String, faceAspectRatio: Float): String? {
        val shapeTag = when (faceShape) {
            "round" -> "face_shape_round"
            "oval" -> "face_shape_oval"
            "heart" -> "face_shape_heart"
            "square" -> "face_shape_square"
            "oblong" -> "face_shape_oblong"
            else -> null
        }
        if (shapeTag != null) return shapeTag

        return when (faceBalance(faceShape, faceAspectRatio)) {
            "soft_width_emphasized" -> "soft_face_shape"
            "structured_width_emphasized" -> "angular_face_shape"
            "length_emphasized" -> "length_emphasized_face"
            else -> null
        }
    }

    private fun toneTag(
        undertone: String,
        clarity: String,
        brightness: String
    ): String? {
        return when {
            undertone == "cool" && clarity == "clear" -> "cool_clear_palette"
            undertone == "warm" && clarity == "muted" -> "warm_soft_palette"
            undertone == "neutral" -> "neutral_palette_flexible"
            undertone != "unknown" -> "${undertone}_tone"
            clarity != "unknown" -> "${clarity}_clarity"
            brightness != "unknown" -> "${brightness}_brightness"
            else -> null
        }
    }

    private fun shoulderRatioBand(value: Double): String {
        return when {
            value == 0.0 -> "unknown"
            value < 0.78 -> "strong_lower"
            value < 0.92 -> "soft_lower"
            value <= 1.08 -> "balanced"
            value <= 1.18 -> "soft_upper"
            else -> "strong_upper"
        }
    }

    private fun upperLowerBand(value: Double): String {
        return when {
            value == 0.0 -> "unknown"
            value < 0.74 -> "strong_lower"
            value < 0.80 -> "soft_lower"
            value <= 0.94 -> "balanced"
            value <= 1.00 -> "soft_upper"
            else -> "strong_upper"
        }
    }

    private fun waistHipBand(value: Double): String {
        return when {
            value == 0.0 -> "unknown"
            value < 0.78 -> "strong_defined"
            value < 0.83 -> "soft_defined"
            value <= 0.89 -> "balanced"
            value <= 0.94 -> "soft_straight"
            else -> "strong_straight"
        }
    }

    private fun waistShoulderBand(value: Double): String {
        return when {
            value == 0.0 -> "unknown"
            value < 0.54 -> "strong_defined"
            value < 0.60 -> "soft_defined"
            value <= 0.68 -> "balanced"
            value <= 0.73 -> "soft_straight"
            else -> "strong_straight"
        }
    }

    private fun volumeBand(width: Double, fullBodyHeight: Double): String {
        if (width == 0.0 || fullBodyHeight == 0.0) return "unknown"
        val ratio = width / fullBodyHeight
        return when {
            ratio <= 0.11 -> "low"
            ratio <= 0.18 -> "medium"
            else -> "high"
        }
    }

    private fun safeDoubleRatio(numerator: Double, denominator: Double): Double {
        if (denominator == 0.0) return 0.0
        return numerator / denominator
    }

    private fun timestamp(): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
    }
}
