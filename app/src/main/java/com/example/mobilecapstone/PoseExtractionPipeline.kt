package com.example.mobilecapstone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

data class ExtractionResult(
    val step: PipelineStep,
    val jsonOutput: String,
    val featureJsonOutput: String,
    val outputFilePath: String
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

object PoseExtractionPipeline {
    val sampleAssets: List<String> = listOf(
        "female_average.jpeg",
        "female_plus.jpeg",
        "female_slim.jpeg",
        "male_average.jpeg",
        "male_slim.jpeg",
        "male_sturdy.jpeg",
    )

    fun initialSteps(): List<PipelineStep> = listOf(
        PipelineStep(id = "pose", title = "Pose extraction"),
        PipelineStep(id = "face", title = "Face shape extraction"),
        PipelineStep(id = "vibe", title = "Vibe tag extraction"),
        PipelineStep(id = "color", title = "Color tone extraction"),
        PipelineStep(id = "silhouette", title = "Silhouette extraction")
    )

    fun loadSampleBitmap(context: Context, assetName: String): Bitmap {
        context.assets.open(assetName).use { input ->
            return BitmapFactory.decodeStream(input)
        }
    }

    fun runPoseExtraction(
        context: Context,
        assetName: String
    ): ExtractionResult {
        val bitmap = loadSampleBitmap(context, assetName)
        val image = InputImage.fromBitmap(bitmap, 0)
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmentationOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()

        val detector = PoseDetection.getClient(poseOptions)
        val segmenter = Segmentation.getClient(segmentationOptions)
        try {
            val pose = Tasks.await(detector.process(image))
            val segmentationMask = Tasks.await(segmenter.process(image))
            val derivedMetrics = buildDerivedMetricsJson(pose)
            val silhouetteJson = buildSilhouetteJson(
                pose = pose,
                segmentationMask = segmentationMask
            )
            val output = JSONObject()
                .put(
                    "meta",
                    JSONObject()
                        .put("pipeline_version", "mlkit-pose-v1")
                        .put("generated_at", timestamp())
                        .put("source_image", assetName)
                )
                .put(
                    "pose",
                    JSONObject()
                        .put("landmark_count", pose.allPoseLandmarks.size)
                        .put("key_landmarks", buildKeyLandmarksJson(pose))
                        .put("derived_metrics", derivedMetrics)
                )
                .put("silhouette", silhouetteJson)
            val featureOutput = buildFeatureJson(
                pose = pose,
                derivedMetrics = derivedMetrics,
                silhouetteJson = silhouetteJson
            )

            val file = writeJsonFile(
                context = context,
                name = assetName.substringBeforeLast('.') + "_pose_extraction_result.json",
                contents = output.toString(2)
            )
            return ExtractionResult(
                step = PipelineStep(id = "pose", title = "Pose extraction", status = StepStatus.COMPLETED),
                jsonOutput = output.toString(2),
                featureJsonOutput = featureOutput.toString(2),
                outputFilePath = file.absolutePath
            )
        } finally {
            detector.close()
            segmenter.close()
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

    private fun buildDerivedMetricsJson(pose: Pose): JSONObject {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val shoulderWidth = distanceX(leftShoulder, rightShoulder)
        val hipWidth = distanceX(leftHip, rightHip)
        val centerShoulderY = averageY(leftShoulder, rightShoulder)
        val centerHipY = averageY(leftHip, rightHip)
        val centerAnkleY = averageY(leftAnkle, rightAnkle)

        return JSONObject()
            .put("shoulder_width", shoulderWidth)
            .put("hip_width", hipWidth)
            .put("shoulder_to_hip_ratio", safeRatio(shoulderWidth, hipWidth))
            .put("torso_height", absoluteDelta(centerShoulderY, centerHipY))
            .put("estimated_leg_length", absoluteDelta(centerHipY, centerAnkleY))
            .put("estimated_full_body_height", absoluteDelta(nose?.position?.y, centerAnkleY))
    }

    private fun buildFeatureJson(
        pose: Pose,
        derivedMetrics: JSONObject,
        silhouetteJson: JSONObject
    ): JSONObject {
        val shoulderToHipRatio = derivedMetrics.optDouble("shoulder_to_hip_ratio", 0.0)
        val torsoHeight = derivedMetrics.optDouble("torso_height", 0.0)
        val legLength = derivedMetrics.optDouble("estimated_leg_length", 0.0)
        val fullBodyHeight = derivedMetrics.optDouble("estimated_full_body_height", 0.0)
        val torsoToLegRatio = safeRatio(torsoHeight.toFloat(), legLength.toFloat()).toDouble()
        val visibilityConfidence = averageLikelihood(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
            pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        ).toDouble()
        val shoulderMaskWidth = silhouetteJson.optDouble("shoulder_width_mask", 0.0)
        val waistMaskWidth = silhouetteJson.optDouble("waist_width_mask", 0.0)
        val hipMaskWidth = silhouetteJson.optDouble("hip_width_mask", 0.0)
        val thighMaskWidth = silhouetteJson.optDouble("thigh_width_mask", 0.0)
        val silhouetteCoverage = silhouetteJson.optDouble("coverage_ratio", 0.0)
        val waistToHipRatio = if (hipMaskWidth == 0.0) 0.0 else waistMaskWidth / hipMaskWidth
        val torsoThicknessRatio = if (shoulderMaskWidth == 0.0) 0.0 else waistMaskWidth / shoulderMaskWidth

        val tags = mutableListOf<String>()
        if (shoulderToHipRatio >= 1.45) {
            tags += "structured_top_candidate"
            tags += "waist_definition_recommended"
        } else if (shoulderToHipRatio <= 1.1) {
            tags += "shoulder_expansion_recommended"
        } else {
            tags += "balanced_upper_lower_frame"
        }

        if (torsoToLegRatio <= 0.78) {
            tags += "long_leg_balance"
        } else {
            tags += "leg_lengthening_recommended"
        }

        if (fullBodyHeight > 0.0 && visibilityConfidence >= 0.95) {
            tags += "full_body_visible"
        }
        if (waistToHipRatio in 0.0..0.86) {
            tags += "waist_defined"
        }
        tags += frameTypeTag(torsoThicknessRatio, silhouetteCoverage)

        return JSONObject()
            .put("analysis_type", "normalized_body_features")
            .put(
                "body_ratio",
                JSONObject()
                    .put("shoulder_to_hip_ratio", shoulderToHipRatio)
                    .put("torso_to_leg_ratio", torsoToLegRatio)
                    .put("full_body_visibility", safeRatio(fullBodyHeight.toFloat(), fullBodyHeight.toFloat()))
            )
            .put(
                "silhouette_features",
                JSONObject()
                    .put("frame_type", frameType(torsoThicknessRatio, silhouetteCoverage))
                    .put("waist_definition", waistDefinition(waistToHipRatio))
                    .put("upper_body_volume", volumeBand(shoulderMaskWidth, fullBodyHeight))
                    .put("lower_body_volume", volumeBand(thighMaskWidth, fullBodyHeight))
                    .put("coverage_ratio", silhouetteCoverage)
            )
            .put(
                "body_frame",
                JSONObject()
                    .put("shoulder_profile", shoulderProfile(shoulderToHipRatio))
                    .put("leg_balance", legBalance(torsoToLegRatio))
                    .put("visibility_confidence", visibilityConfidence)
            )
            .put("style_tags", org.json.JSONArray(tags))
    }

    private fun buildSilhouetteJson(
        pose: Pose,
        segmentationMask: com.google.mlkit.vision.segmentation.SegmentationMask
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
        val waistY = shoulderY + ((hipY - shoulderY) * 0.55f)
        val thighY = hipY + ((kneeY - hipY) * 0.35f)

        val shoulderWidthMask = rowWidth(floatBuffer, width, height, shoulderY)
        val waistWidthMask = rowWidth(floatBuffer, width, height, waistY)
        val hipWidthMask = rowWidth(floatBuffer, width, height, hipY)
        val thighWidthMask = rowWidth(floatBuffer, width, height, thighY)
        val coverageRatio = foregroundCoverage(floatBuffer, width, height)

        return JSONObject()
            .put("mask_width", width)
            .put("mask_height", height)
            .put("coverage_ratio", coverageRatio)
            .put("shoulder_width_mask", shoulderWidthMask)
            .put("waist_width_mask", waistWidthMask)
            .put("hip_width_mask", hipWidthMask)
            .put("thigh_width_mask", thighWidthMask)
            .put("waist_to_hip_ratio", safeRatio(waistWidthMask, hipWidthMask))
            .put("waist_to_shoulder_ratio", safeRatio(waistWidthMask, shoulderWidthMask))
    }

    private fun pointJson(landmark: PoseLandmark?): Any {
        if (landmark == null) return JSONObject.NULL
        return JSONObject()
            .put("x", landmark.position.x)
            .put("y", landmark.position.y)
            .put("z", landmark.position3D.z)
            .put("in_frame_likelihood", landmark.inFrameLikelihood)
    }

    private fun writeJsonFile(context: Context, name: String, contents: String): File {
        val outputDir = File(context.filesDir, "pipeline-results").apply { mkdirs() }
        val outputFile = File(outputDir, name)
        outputFile.writeText(contents)
        return outputFile
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

    private fun rowWidth(
        floatBuffer: java.nio.FloatBuffer,
        width: Int,
        height: Int,
        y: Float,
        threshold: Float = 0.35f
    ): Float {
        if (width <= 0 || height <= 0) return 0f
        val row = y.roundToInt().coerceIn(0, height - 1)
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

    private fun averageLikelihood(vararg landmarks: PoseLandmark?): Float {
        val valid = landmarks.mapNotNull { it?.inFrameLikelihood }
        if (valid.isEmpty()) return 0f
        return valid.sum() / valid.size
    }

    private fun shoulderProfile(shoulderToHipRatio: Double): String {
        return when {
            shoulderToHipRatio >= 1.45 -> "broad_relative_to_hip"
            shoulderToHipRatio <= 1.1 -> "narrow_relative_to_hip"
            else -> "balanced"
        }
    }

    private fun legBalance(torsoToLegRatio: Double): String {
        return when {
            torsoToLegRatio <= 0.78 -> "leg_emphasized"
            torsoToLegRatio >= 1.0 -> "torso_emphasized"
            else -> "balanced"
        }
    }

    private fun waistDefinition(waistToHipRatio: Double): String {
        return when {
            waistToHipRatio == 0.0 -> "unknown"
            waistToHipRatio <= 0.82 -> "defined"
            waistToHipRatio <= 0.92 -> "medium"
            else -> "soft"
        }
    }

    private fun frameType(torsoThicknessRatio: Double, coverageRatio: Double): String {
        return when {
            torsoThicknessRatio == 0.0 -> "unknown"
            torsoThicknessRatio <= 0.58 && coverageRatio <= 0.20 -> "slim_frame"
            torsoThicknessRatio >= 0.7 || coverageRatio >= 0.28 -> "fuller_frame"
            else -> "balanced_frame"
        }
    }

    private fun frameTypeTag(torsoThicknessRatio: Double, coverageRatio: Double): String {
        return frameType(torsoThicknessRatio, coverageRatio)
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

    private fun timestamp(): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
    }
}
