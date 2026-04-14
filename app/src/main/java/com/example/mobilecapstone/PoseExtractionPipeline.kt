package com.example.mobilecapstone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
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
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
        assetName: String,
        onStepStatusChanged: ((String, StepStatus) -> Unit)? = null
    ): ExtractionResult {
        val bitmap = loadSampleBitmap(context, assetName)
        val image = InputImage.fromBitmap(bitmap, 0)
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmentationOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        val detector = PoseDetection.getClient(poseOptions)
        val segmenter = Segmentation.getClient(segmentationOptions)
        val faceDetector = FaceDetection.getClient(faceOptions)
        try {
            onStepStatusChanged?.invoke("pose", StepStatus.RUNNING)
            val pose = Tasks.await(detector.process(image))
            val derivedMetrics = buildDerivedMetricsJson(pose)
            onStepStatusChanged?.invoke("pose", StepStatus.COMPLETED)

            onStepStatusChanged?.invoke("face", StepStatus.RUNNING)
            val faceDetection = detectFaceWithFallback(
                bitmap = bitmap,
                pose = pose,
                detector = faceDetector
            )
            val faceJson = buildFaceJson(faceDetection)
            onStepStatusChanged?.invoke("face", StepStatus.COMPLETED)

            onStepStatusChanged?.invoke("color", StepStatus.RUNNING)
            val colorToneJson = buildColorToneJson(faceDetection)
            onStepStatusChanged?.invoke("color", StepStatus.COMPLETED)

            onStepStatusChanged?.invoke("silhouette", StepStatus.RUNNING)
            val segmentationMask = Tasks.await(segmenter.process(image))
            val silhouetteJson = buildSilhouetteJson(
                pose = pose,
                segmentationMask = segmentationMask
            )
            onStepStatusChanged?.invoke("silhouette", StepStatus.COMPLETED)
            val output = JSONObject()
                .put(
                    "meta",
                    JSONObject()
                        .put("pipeline_version", "mlkit-style-v2")
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
                .put("face", faceJson)
                .put("color_tone", colorToneJson)
                .put("silhouette", silhouetteJson)
            val featureOutput = buildFeatureJson(
                pose = pose,
                derivedMetrics = derivedMetrics,
                silhouetteJson = silhouetteJson,
                faceJson = faceJson,
                colorToneJson = colorToneJson
            )

            val file = writeJsonFile(
                context = context,
                name = assetName.substringBeforeLast('.') + "_analysis_result.json",
                contents = output.toString(2)
            )
            return ExtractionResult(
                step = PipelineStep(id = "pose", title = "Style analysis", status = StepStatus.COMPLETED),
                jsonOutput = output.toString(2),
                featureJsonOutput = featureOutput.toString(2),
                outputFilePath = file.absolutePath,
                completedStepIds = setOf("pose", "face", "color", "silhouette")
            )
        } finally {
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
        silhouetteJson: JSONObject,
        faceJson: JSONObject,
        colorToneJson: JSONObject
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

    private fun detectFaceWithFallback(
        bitmap: Bitmap,
        pose: Pose,
        detector: com.google.mlkit.vision.face.FaceDetector
    ): FaceDetectionResult {
        val fullImage = InputImage.fromBitmap(bitmap, 0)
        val fullFace = Tasks.await(detector.process(fullImage))
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
        val croppedFace = Tasks.await(detector.process(croppedImage))
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
