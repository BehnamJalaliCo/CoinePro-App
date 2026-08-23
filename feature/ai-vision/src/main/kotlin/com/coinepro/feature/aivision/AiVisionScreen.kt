package com.coinepro.feature.aivision

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.aivision.AiVisionAssessment
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.aivision.AiVisionImageUpload
import com.coinepro.core.aivision.AiVisionJob
import com.coinepro.core.aivision.AiVisionJobStatus
import com.coinepro.core.aivision.AiVisionResult
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.model.SignalDirection
import kotlinx.coroutines.launch

@Composable
fun AiVisionScreen(
    controller: AiVisionController,
    onOpenSignal: (Long) -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prepared by remember { mutableStateOf<AiVisionImageUpload?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }

    fun prepare(uri: Uri) {
        scope.launch {
            preparing = true
            selectionError = null
            runCatching { prepareVisionImage(context, uri) }
                .onSuccess { prepared = it }
                .onFailure { selectionError = it.message ?: "Could not prepare the selected image." }
            preparing = false
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::prepare)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCamera = true
        } else {
            selectionError = "Camera permission was denied. Gallery / file remains available without camera permission."
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("AI Vision", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Capture or choose a chart screenshot. The image is re-encoded before upload so EXIF metadata is removed. Analysis progress and results come only from server job state.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showCamera) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    CameraCapturePanel(
                        onCaptured = { uri ->
                            showCamera = false
                            prepare(uri)
                        },
                        onError = { selectionError = it },
                        onCancel = { showCamera = false },
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Chart image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Camera permission is requested only if you choose Camera below. Gallery / file selection does not require camera permission, so denying Camera does not block image analysis from an existing file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::openCamera) { Text("Camera") }
                        TextButton(onClick = { documentPicker.launch(arrayOf("image/*")) }) {
                            Text("Gallery / file")
                        }
                    }
                    if (preparing) {
                        CircularProgressIndicator()
                        Text("Preparing image…")
                    }
                    prepared?.let { image ->
                        Text("Prepared JPEG · ${image.bytes.size / 1024} KB")
                        Text(
                            "Orientation normalized, image resized if needed, and original metadata removed before upload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { controller.submit(image) },
                            enabled = !state.uploading && state.job?.isPending != true,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.uploading) "Uploading…" else "Analyze chart")
                        }
                    }
                }
            }
        }

        selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        state.job?.let { job ->
            VisionJobCard(
                job = job,
                canRetry = prepared != null,
                onRefresh = controller::refreshCurrent,
                onRetry = { prepared?.let(controller::submit) },
                onDismiss = controller::dismissJob,
                onOpenSignal = onOpenSignal,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VisionJobCard(
    job: AiVisionJob,
    canRetry: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSignal: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Vision analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Status: ${job.status.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
            when (job.status) {
                AiVisionJobStatus.QUEUED,
                AiVisionJobStatus.RUNNING,
                -> {
                    CircularProgressIndicator()
                    Text(
                        "Waiting for the server. No fake percentage or locally invented completion state is shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRefresh) { Text("Refresh status") }
                }

                AiVisionJobStatus.DONE -> {
                    val result = job.result
                    if (result == null) {
                        Text("No validated structured result was returned.", color = MaterialTheme.colorScheme.error)
                    } else {
                        VisionResultCard(result, onOpenSignal)
                    }
                    TextButton(onClick = onDismiss) { Text("Analyze another image") }
                }

                AiVisionJobStatus.FAILED,
                AiVisionJobStatus.EXPIRED,
                -> {
                    Text(
                        job.errorMessage ?: if (job.status == AiVisionJobStatus.EXPIRED) {
                            "This analysis expired."
                        } else {
                            "Vision analysis failed."
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry, enabled = canRetry) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Choose another image") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisionResultCard(
    result: AiVisionResult,
    onOpenSignal: (Long) -> Unit,
) {
    val assessmentText = when (result.assessment) {
        AiVisionAssessment.ACTIONABLE -> "Actionable validated setup"
        AiVisionAssessment.LOW_CONFIDENCE -> "Low confidence"
        AiVisionAssessment.UNKNOWN -> "Unknown / unclear"
        AiVisionAssessment.UNSUPPORTED -> "Unsupported image"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(assessmentText, fontWeight = FontWeight.SemiBold)
        result.symbol?.let { Text("$it${result.timeframe?.let { tf -> " · $tf" }.orEmpty()}") }
        result.confidence?.let { Text("$it% confidence", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        result.trendBias?.let { LabeledText("Trend / bias", it) }
        result.marketStructure?.let { LabeledText("Market structure", it) }
        result.setup?.let { LabeledText("Setup", it) }

        if (result.assessment == AiVisionAssessment.ACTIONABLE) {
            val directionColor = when (result.direction) {
                SignalDirection.BUY -> CoineProColors.Buy
                SignalDirection.SELL -> CoineProColors.Sell
                else -> CoineProColors.TextSecondary
            }
            result.direction?.let { Text(it.name, color = directionColor, fontWeight = FontWeight.Bold) }
            result.entryZone?.let {
                FinancialRow("Entry low", it.low)
                FinancialRow("Entry high", it.high)
            }
            result.stopLoss?.let { FinancialRow("Stop loss", it, CoineProColors.Sell) }
            result.targets.forEach { target ->
                FinancialRow("TP${target.level}", target.price, CoineProColors.Buy)
            }
            result.risk?.let { LabeledText("Risk", it.replaceFirstChar(Char::uppercase)) }
            result.reasoning?.let { LabeledText("Reasoning", it) }
            val signalId = result.signalId
            if (result.canOpenValidatedSignal && signalId != null) {
                Button(
                    onClick = { onOpenSignal(signalId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open validated Signal")
                }
            }
            Text(
                "AI Vision never executes directly. Any eligible action continues through the persisted Signal flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            result.reasoning?.let { LabeledText("Why no trade setup", it) }
            Text(
                "No execution action is available for low-confidence, unknown, or unsupported analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FinancialRow(
    label: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color = CoineProColors.TextPrimary,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                MarketNumberFormatter.price(value, 6).trimEnd('0').trimEnd('.'),
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
