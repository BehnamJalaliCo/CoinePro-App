package com.coinepro.core.aisignal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiVisionController(
    private val gateway: AiVisionGateway,
    private val scope: CoroutineScope,
    private val pollDelayMs: Long = 2_000,
) {
    private val mutableState = MutableStateFlow(AiVisionState())
    val state: StateFlow<AiVisionState> = mutableState.asStateFlow()
    private var polling: Job? = null

    fun selectImage(image: AiVisionImage) {
        if (mutableState.value.job?.isPending == true) return
        mutableState.value = mutableState.value.copy(selectedImage = image, job = null, error = null)
    }

    fun clearImage() {
        if (mutableState.value.job?.isPending == true) return
        mutableState.value = mutableState.value.copy(selectedImage = null)
    }

    fun refreshQuota() = scope.launch {
        runCatching { gateway.quota() }
            .onSuccess { quota -> mutableState.value = mutableState.value.copy(quota = quota, quotaExhausted = quota.exhausted, error = null) }
            .onFailure(::handleFailure)
    }

    fun submit(request: AiVisionRequest) {
        val image = mutableState.value.selectedImage ?: run {
            mutableState.value = mutableState.value.copy(error = "Select a chart image first")
            return
        }
        if (mutableState.value.submitting || mutableState.value.job?.isPending == true) return
        scope.launch {
            mutableState.value = mutableState.value.copy(submitting = true, error = null)
            try {
                val job = gateway.createJob(image, request)
                mutableState.value = mutableState.value.copy(
                    selectedImage = if (job.isPending) image else null,
                    submitting = false,
                    job = job,
                    quota = job.quota ?: mutableState.value.quota,
                    quotaExhausted = job.quota?.exhausted ?: mutableState.value.quotaExhausted,
                )
                if (job.isPending) startPolling(job.id) else clearSensitiveImage()
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(submitting = false)
                clearSensitiveImage()
                handleFailure(error)
            }
        }
    }

    fun refreshCurrent() = scope.launch {
        val id = mutableState.value.job?.id ?: return@launch
        refreshJob(id)
    }

    fun dismissJob() {
        polling?.cancel()
        mutableState.value = mutableState.value.copy(job = null, selectedImage = null, error = null)
    }

    fun onSignedOut() {
        polling?.cancel()
        mutableState.value = AiVisionState()
    }

    private fun startPolling(id: String) {
        polling?.cancel()
        polling = scope.launch {
            while (true) {
                delay(pollDelayMs)
                if (!refreshJob(id)) break
            }
        }
    }

    private suspend fun refreshJob(id: String): Boolean = try {
        val job = gateway.job(id)
        mutableState.value = mutableState.value.copy(job = job, quota = job.quota ?: mutableState.value.quota, error = null)
        if (!job.isPending) clearSensitiveImage()
        job.isPending
    } catch (error: Throwable) {
        clearSensitiveImage()
        handleFailure(error)
        false
    }

    private fun clearSensitiveImage() {
        mutableState.value = mutableState.value.copy(selectedImage = null)
    }

    private fun handleFailure(error: Throwable) {
        when (error) {
            is AiSignalEntitlementRequiredException -> mutableState.value = mutableState.value.copy(entitlementRequired = true, error = error.message)
            is AiSignalQuotaExhaustedException -> mutableState.value = mutableState.value.copy(quotaExhausted = true, error = error.message)
            else -> mutableState.value = mutableState.value.copy(error = error.message ?: "AI Vision request failed")
        }
    }
}
