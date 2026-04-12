package com.example.mobilecapstone

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileCapstoneTheme {
                PosePipelineApp()
            }
        }
    }
}

@Composable
private fun PosePipelineApp() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(PipelineUiState()) }

    LaunchedEffect(uiState.selectedAsset) {
        val sample = withContext(Dispatchers.IO) {
            PoseExtractionPipeline.loadSampleBitmap(context, uiState.selectedAsset)
        }
        uiState = uiState.copy(sampleBitmap = sample)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Sequential Model Runner",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Each button press runs exactly one model step. Right now only pose extraction is implemented with ML Kit.",
                    style = MaterialTheme.typography.bodyMedium
                )

                uiState.sampleBitmap?.let { bitmap ->
                    ImageCard(bitmap = bitmap)
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Sample selector",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Current image: ${uiState.selectedAsset}")
                        SelectionButtonRow(
                            options = PoseExtractionPipeline.sampleAssets,
                            selected = uiState.selectedAsset,
                            onSelect = { assetName ->
                                if (!uiState.isRunning) {
                                    uiState = uiState.copy(
                                        selectedAsset = assetName,
                                        jsonOutput = "",
                                        featureJsonOutput = "",
                                        outputFilePath = "",
                                        statusMessage = "Idle"
                                    )
                                }
                            }
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Pipeline steps",
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.steps.forEach { step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(step.title)
                                Text(step.status.name)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (uiState.isRunning) return@Button
                        scope.launch {
                            uiState = uiState.startStep("pose")
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching {
                                    PoseExtractionPipeline.runPoseExtraction(
                                        context = context,
                                        assetName = uiState.selectedAsset
                                    )
                                }
                            }
                            uiState = outcome.fold(
                                onSuccess = { result -> uiState.completeStep(result) },
                                onFailure = { error ->
                                    uiState.failStep("pose", error)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRunning
                ) {
                    Text(
                        when {
                            uiState.isRunning -> "Running pose extraction..."
                            uiState.completedSteps.contains("pose") -> "Run pose extraction again"
                            else -> "Run pose extraction"
                        }
                    )
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Current status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        HorizontalDivider()
                        Text(uiState.statusMessage)
                        if (uiState.outputFilePath.isNotBlank()) {
                            Text("Saved file: ${uiState.outputFilePath}")
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "JSON output",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uiState.displayOutput))
                                }
                            ) {
                                Text("Copy")
                            }
                        }
                        HorizontalDivider()
                        SelectionContainer {
                            Text(
                                text = uiState.displayOutput,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Normalized feature JSON",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uiState.displayFeatureOutput))
                                }
                            ) {
                                Text("Copy")
                            }
                        }
                        HorizontalDivider()
                        SelectionContainer {
                            Text(
                                text = uiState.displayFeatureOutput,
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

            if (uiState.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionButtonRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    Button(
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                        enabled = option != selected
                    ) {
                        Text(option.substringBeforeLast('.'))
                    }
                }
                if (rowOptions.size == 1) {
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .width(0.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageCard(bitmap: Bitmap) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sample image",
                style = MaterialTheme.typography.titleMedium
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Sample pose input",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

private data class PipelineUiState(
    val steps: List<PipelineStep> = PoseExtractionPipeline.initialSteps(),
    val completedSteps: Set<String> = emptySet(),
    val selectedAsset: String = PoseExtractionPipeline.sampleAssets.first(),
    val isRunning: Boolean = false,
    val statusMessage: String = "Idle",
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
            statusMessage = "Running ${stepTitle(stepId)}",
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
            statusMessage = "${result.step.title} completed",
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
            statusMessage = "Failed ${stepTitle(stepId)}",
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
