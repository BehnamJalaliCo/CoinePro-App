package com.coinepro.core.aivision

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.network.serverTextOrNull
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
            _state.update { it.copy(error = UiMessage.of(MessageKey.AI_IMAGE_TYPE_UNSUPPORTED)) }
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
                _state.update { it.copy(uploading = false, error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED)) }
            } catch (_: AiVisionImageTooLargeException) {
                _state.update { it.copy(uploading = false, error = UiMessage.of(MessageKey.AI_IMAGE_TOO_LARGE)) }
            } catch (_: AiVisionUnsupportedMediaException) {
                _state.update { it.copy(uploading = false, error = UiMessage.of(MessageKey.AI_IMAGE_TYPE_UNSUPPORTED)) }
            } catch (error: AiVisionRequestRejectedException) {
                _state.update { it.copy(uploading = false, error = UiMessage.fromServer(error.message, MessageKey.AI_GENERATION_FAILED)) }
            } catch (_: AiVisionRateLimitedException) {
                _state.update { it.copy(uploading = false, error = UiMessage.of(MessageKey.AI_GENERATION_FAILED)) }
            } catch (error: Exception) {
                _state.update { it.copy(uploading = false, error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED)) }
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
            _state.update { it.copy(error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED)) }
            false
        } catch (error: Exception) {
            _state.update { it.copy(error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED)) }
            false
        }
    }

    private fun applyJob(job: AiVisionJob, uploading: Boolean) {
        val jobError = when {
            job.status == AiVisionJobStatus.DONE && job.result == null ->
                UiMessage.of(MessageKey.AI_RESULT_UNUSABLE)
            job.status == AiVisionJobStatus.FAILED ->
                UiMessage.fromServer(job.errorMessage, MessageKey.AI_GENERATION_FAILED)
            job.status == AiVisionJobStatus.EXPIRED ->
                UiMessage.fromServer(job.errorMessage, MessageKey.AI_JOB_EXPIRED)
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
