package com.coinepro.feature.ai

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.aisignal.AiSignalJobStatus
import com.coinepro.core.aisignal.AiSignalProductScope
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.aisignal.AiVisionController
import com.coinepro.core.aisignal.AiVisionImage
import com.coinepro.core.aisignal.AiVisionImageSource
import com.coinepro.core.aisignal.AiVisionRequest

@Composable
fun AiVisionScreen(controller: AiVisionController, onOpenSignal: (Long) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var symbol by remember { mutableStateOf(AiSignalProductScope.defaultSymbols.first()) }
    var timeframe by remember { mutableStateOf(AiSignalTimeframe.H1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readImage(context, it, AiVisionImageSource.GALLERY) }?.let(controller::selectImage)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            val stream = java.io.ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            runCatching { AiVisionImage(stream.toByteArray(), "image/jpeg", AiVisionImageSource.CAMERA) }.getOrNull()
        }?.let(controller::selectImage)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("AI Vision", style = MaterialTheme.typography.headlineSmall)
        Text("Upload a chart screenshot. You choose the market context; the app never guesses a symbol from pixels.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch("image/*") }, enabled = state.job?.isPending != true) { Text("Gallery") }
            Button(onClick = { camera.launch(null) }, enabled = state.job?.isPending != true) { Text("Camera") }
        }
        Text(if (state.selectedImage != null) "Image ready for private analysis" else "No image selected")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("XAUUSD", "XAGUSD", "BTCUSDT", "ETHUSDT").forEach { option ->
                FilterChip(selected = symbol == option, onClick = { symbol = option }, label = { Text(option) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiSignalTimeframe.entries.forEach { option ->
                FilterChip(selected = timeframe == option, onClick = { timeframe = option }, label = { Text(option.label) })
            }
        }
        Button(
            onClick = { controller.submit(AiVisionRequest(symbol, timeframe)) },
            enabled = state.selectedImage != null && state.job?.isPending != true && !state.submitting && !state.entitlementRequired && !state.quotaExhausted,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.submitting) "Uploading…" else "Analyze chart") }

        state.job?.let { job ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Vision status: ${job.status.name}")
                    if (job.isPending) TextButton(onClick = controller::refreshCurrent) { Text("Refresh") }
                    job.result?.let { result ->
                        Text("${result.symbol} · ${result.timeframe} · ${result.trend}")
                        Text("Confidence: ${result.confidence}%")
                        Text("Entry: ${result.entry}   SL: ${result.stopLoss}")
                        result.targets.forEach { Text("TP${it.level}: ${it.price}") }
                        Text(result.explanation)
                        Button(onClick = { onOpenSignal(result.signalId) }, modifier = Modifier.fillMaxWidth()) { Text("Open validated Signal") }
                        Text("Execution remains in Signal Detail. Vision output cannot execute directly.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (job.status == AiSignalJobStatus.FAILED || job.status == AiSignalJobStatus.EXPIRED) {
                        Text(job.errorMessage ?: "Analysis did not complete.", color = MaterialTheme.colorScheme.error)
                        Text("Select the image again before retrying.")
                    }
                    if (!job.isPending) TextButton(onClick = controller::dismissJob) { Text("New analysis") }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun readImage(context: Context, uri: Uri, source: AiVisionImageSource): AiVisionImage? = runCatching {
    val mime = context.contentResolver.getType(uri) ?: return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= AiVisionImage.MAX_IMAGE_BYTES) { "Image too large" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: return null
    AiVisionImage(bytes, mime, source)
}.getOrNull()
