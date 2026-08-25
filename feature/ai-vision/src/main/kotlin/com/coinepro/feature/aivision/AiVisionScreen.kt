package com.coinepro.feature.aivision

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
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
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProStreamingBar
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProThinkingDots
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
    // Resolved out here: the failure handlers below are not composable scopes.
    val prepareFailure = stringResource(R.string.vision_prepare_failed)
    val cameraDenied = stringResource(R.string.vision_camera_denied)

    fun prepare(uri: Uri) {
        scope.launch {
            preparing = true
            selectionError = null
            runCatching { prepareVisionImage(context, uri) }
                .onSuccess { prepared = it }
                .onFailure { selectionError = it.message ?: prepareFailure }
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
            selectionError = cameraDenied
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
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        Text(
            text = stringResource(R.string.vision_title),
            style = MaterialTheme.typography.headlineSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.vision_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )

        if (showCamera) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column {
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
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                    Text(
                        text = stringResource(R.string.vision_source_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.vision_permission_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        CoineProSecondaryButton(
                            text = stringResource(R.string.vision_camera),
                            onClick = ::openCamera,
                            modifier = Modifier.weight(1f),
                        )
                        CoineProSecondaryButton(
                            text = stringResource(R.string.vision_gallery),
                            onClick = { documentPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (preparing) {
                        CoineProSkeleton(Modifier.fillMaxWidth(), height = 14.dp)
                        Text(
                            text = stringResource(R.string.vision_preparing),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextSecondary,
                        )
                    }
                    prepared?.let { image ->
                        Text(
                            text = stringResource(
                                R.string.vision_prepared,
                                BidiText.isolateLtr("${image.bytes.size / 1024} KB"),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.vision_prepared_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                        )
                        val ready = !state.uploading && state.job?.isPending != true
                        CoineProPrimaryButton(
                            text = stringResource(
                                if (state.uploading) R.string.vision_uploading else R.string.vision_analyze,
                            ),
                            onClick = { if (ready) controller.submit(image) },
                            modifier = Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.45f),
                        )
                    }
                }
            }
        }

        // Both are messages someone else produced — the platform or the server — so both are shown
        // as they came rather than reworded into a friendlier local sentence.
        selectionError?.let { VisionNotice(it, CoineProColors.Sell) }
        state.error?.let { VisionNotice(it, CoineProColors.Sell) }

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

        Spacer(Modifier.height(CoineProSpacing.Three))
    }
}

@Composable
private fun VisionNotice(message: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        style = MaterialTheme.typography.bodySmall,
        color = accent,
    )
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
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
            Text(
                text = stringResource(R.string.vision_analysis_title),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
            when (job.status) {
                AiVisionJobStatus.QUEUED,
                AiVisionJobStatus.RUNNING,
                -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoineProThinkingDots()
                        Text(
                            text = stringResource(
                                if (job.status == AiVisionJobStatus.QUEUED) {
                                    R.string.vision_state_queued
                                } else {
                                    R.string.vision_state_running
                                },
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = CoineProColors.TextPrimary,
                        )
                    }
                    // Indeterminate on purpose. The server reports queued or running and never a
                    // percentage, so a filling bar would be a number the client made up about how
                    // close someone's analysis is to done.
                    CoineProStreamingBar(Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.vision_waiting_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.vision_refresh),
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AiVisionJobStatus.DONE -> {
                    val result = job.result
                    if (result == null) {
                        VisionNotice(stringResource(R.string.vision_no_result), CoineProColors.Sell)
                    } else {
                        VisionResultCard(result, onOpenSignal)
                    }
                    CoineProSecondaryButton(
                        text = stringResource(R.string.vision_another),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AiVisionJobStatus.FAILED,
                AiVisionJobStatus.EXPIRED,
                -> {
                    VisionNotice(
                        // The server's reason when it gave one. "It failed" with no cause sends a
                        // reader back to the camera to repeat the same unreadable screenshot.
                        message = job.errorMessage ?: stringResource(
                            if (job.status == AiVisionJobStatus.EXPIRED) {
                                R.string.vision_expired
                            } else {
                                R.string.vision_failed
                            },
                        ),
                        accent = CoineProColors.Sell,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        CoineProPrimaryButton(
                            text = stringResource(R.string.vision_try_again),
                            onClick = { if (canRetry) onRetry() },
                            modifier = Modifier.weight(1f).alpha(if (canRetry) 1f else 0.45f),
                        )
                        CoineProSecondaryButton(
                            text = stringResource(R.string.vision_another),
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
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
    val actionable = result.assessment == AiVisionAssessment.ACTIONABLE
    val assessmentColour = if (actionable) CoineProColors.Buy else CoineProColors.Warning

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        Text(
            text = stringResource(result.assessment.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = assessmentColour,
        )
        result.symbol?.let {
            Text(
                text = BidiText.isolateLtr(it + (result.timeframe?.let { tf -> " · $tf" }.orEmpty())),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
        }
        result.confidence?.let {
            Text(
                // The percent sign belongs inside the isolate; outside it, bidi reordering renders
                // "78%" as "%78".
                text = stringResource(R.string.vision_confidence, BidiText.isolateLtr("$it%")),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        result.trendBias?.let { LabeledText(stringResource(R.string.vision_trend), it) }
        result.marketStructure?.let { LabeledText(stringResource(R.string.vision_structure), it) }
        result.setup?.let { LabeledText(stringResource(R.string.vision_setup), it) }

        if (actionable) {
            val directionColor = when (result.direction) {
                SignalDirection.BUY -> CoineProColors.Buy
                SignalDirection.SELL -> CoineProColors.Sell
                else -> CoineProColors.TextSecondary
            }
            result.direction?.let {
                Text(
                    text = stringResource(it.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = directionColor,
                )
            }
            result.entryZone?.let {
                FinancialRow(stringResource(R.string.vision_entry_low), it.low)
                FinancialRow(stringResource(R.string.vision_entry_high), it.high)
            }
            result.stopLoss?.let { FinancialRow(stringResource(R.string.vision_stop), it, CoineProColors.Sell) }
            result.targets.forEach { target ->
                FinancialRow(stringResource(R.string.vision_target, target.level), target.price, CoineProColors.Buy)
            }
            result.risk?.let { LabeledText(stringResource(R.string.vision_risk), it) }
            result.reasoning?.let { LabeledText(stringResource(R.string.vision_reasoning), it) }
            val signalId = result.signalId
            if (result.canOpenValidatedSignal && signalId != null) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.vision_open_signal),
                    onClick = { onOpenSignal(signalId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.vision_no_direct_execution),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            result.reasoning?.let { LabeledText(stringResource(R.string.vision_why_no_setup), it) }
            Text(
                text = stringResource(R.string.vision_no_action),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
        // Model prose, shown as written: this is the part a reader judges the setup by.
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
    }
}

@androidx.annotation.StringRes
private fun AiVisionAssessment.labelRes(): Int = when (this) {
    AiVisionAssessment.ACTIONABLE -> R.string.vision_assessment_actionable
    AiVisionAssessment.LOW_CONFIDENCE -> R.string.vision_assessment_low_confidence
    AiVisionAssessment.UNKNOWN -> R.string.vision_assessment_unknown
    AiVisionAssessment.UNSUPPORTED -> R.string.vision_assessment_unsupported
}

@androidx.annotation.StringRes
private fun SignalDirection.labelRes(): Int = when (this) {
    SignalDirection.BUY -> R.string.vision_direction_buy
    SignalDirection.SELL -> R.string.vision_direction_sell
    SignalDirection.NEUTRAL -> R.string.vision_direction_neutral
}

@Composable
private fun FinancialRow(
    label: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color = CoineProColors.TextPrimary,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(
            text = BidiText.isolateLtr(
                BidiText.strip(MarketNumberFormatter.price(value, 6)).trimEnd('0').trimEnd('.'),
            ),
            style = CoineProTextStyles.RowFigure,
            color = color,
        )
    }
}
