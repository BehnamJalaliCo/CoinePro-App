package com.coinepro.core.aivision

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiVisionController(
    private val gateway: AiVisionGateway,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 2_000L,
) {
    private val _state = MutableStateFlow(AiVisionState())
    val state: StateFlow<AiVisionState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun submit(upload: AiVisionImageUpload) {
        if (!upload.isSupported) {
            _state.update { it.copy(error = "Choose a supported chart image under 6 MB.") }
            return
        }
        if (_state.value.uploading || _state.value.job?.isPending == true) return

        pollJob?.cancel()
        pollJob = null
        scope.launch {
            _state.update { it.copy(uploading = true, job = null, error = null) }
            try {
                val job = gateway.createJob(upload)
                applyJob(job, uploading = false)
                if (job.isPending) startPolling(job.id)
            } catch (_: AiVisionEntitlementRequiredException) {
                _state.update { it.copy(uploading = false, error = "AI Vision requires an active server entitlement.") }
            } catch (_: AiVisionImageTooLargeException) {
                _state.update { it.copy(uploading = false, error = "The prepared image is too large for AI Vision.") }
            } catch (_: AiVisionUnsupportedMediaException) {
                _state.update { it.copy(uploading = false, error = "The server does not support this image type.") }
            } catch (error: AiVisionRequestRejectedException) {
                _state.update { it.copy(uploading = false, error = error.message) }
            } catch (_: AiVisionRateLimitedException) {
                _state.update { it.copy(uploading = false, error = "AI Vision is temporarily rate limited. Try again later.") }
            } catch (error: Exception) {
                _state.update { it.copy(uploading = false, error = error.message ?: "AI Vision upload failed.") }
            }
        }
    }

    fun refreshCurrent() {
        val current = _state.value.job ?: return
        scope.launch { refreshJob(current.id, resumePolling = true) }
    }

    fun dismissJob() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(job = null, uploading = false, error = null) }
    }

    fun clear() {
        pollJob?.cancel()
        pollJob = null
        _state.value = AiVisionState()
    }

    private fun startPolling(jobId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(pollIntervalMs)
                val current = _state.value.job
                if (current?.id != jobId || !current.isPending) return@launch
                val shouldContinue = refreshJob(jobId, resumePolling = false)
                if (!shouldContinue) return@launch
            }
        }
    }

    private suspend fun refreshJob(jobId: String, resumePolling: Boolean): Boolean {
        return try {
            val job = gateway.job(jobId)
            applyJob(job, uploading = false)
            if (resumePolling && job.isPending) startPolling(job.id)
            job.isPending
        } catch (_: AiVisionJobExpiredException) {
            val current = _state.value.job
            if (current?.id == jobId) {
                applyJob(
                    current.copy(
                        status = AiVisionJobStatus.EXPIRED,
                        result = null,
                        errorCode = "expired",
                        errorMessage = "AI Vision job expired on the server.",
                    ),
                    uploading = false,
                )
            }
            false
        } catch (_: AiVisionEntitlementRequiredException) {
            _state.update { it.copy(error = "AI Vision status requires an active server entitlement.") }
            false
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Could not refresh AI Vision status.") }
            false
        }
    }

    private fun applyJob(job: AiVisionJob, uploading: Boolean) {
        val jobError = when {
            job.status == AiVisionJobStatus.DONE && job.result == null ->
                "Server returned DONE without a validated structured vision result."
            job.status == AiVisionJobStatus.FAILED ->
                job.errorMessage ?: "AI Vision analysis failed."
            job.status == AiVisionJobStatus.EXPIRED ->
                job.errorMessage ?: "AI Vision analysis expired."
            else -> null
        }
        _state.update {
            it.copy(
                uploading = uploading,
                job = job,
                error = jobError,
            )
        }
    }
}
