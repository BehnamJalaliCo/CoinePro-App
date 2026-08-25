package com.coinepro.core.aisignal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiSignalController(
    private val gateway: AiSignalGateway,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 2_000L,
) {
    private val _state = MutableStateFlow(AiSignalState())
    val state: StateFlow<AiSignalState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun refreshQuota() {
        scope.launch {
            _state.update { it.copy(refreshingQuota = true, error = null) }
            try {
                val quota = gateway.quota()
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        quota = quota,
                        quotaExhausted = quota.exhausted,
                        entitlementRequired = false,
                    )
                }
            } catch (_: AiSignalEntitlementRequiredException) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        entitlementRequired = true,
                        quotaExhausted = false,
                        error = "AI Signals require an active server entitlement.",
                    )
                }
            } catch (_: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        quotaExhausted = true,
                        error = "AI Signal quota is exhausted.",
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        error = error.message ?: "AI Signal quota is unavailable.",
                    )
                }
            }
        }
    }

    fun submit(request: AiSignalRequest) {
        val safeSymbol = AiSignalProductScope.normalizeSymbol(request.symbol)
        if (safeSymbol == null) {
            _state.update { it.copy(error = "Unsupported AI Signal symbol.") }
            return
        }
        if (_state.value.entitlementRequired || _state.value.quotaExhausted || _state.value.submitting) return

        val safeRequest = request.copy(symbol = safeSymbol)
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            _state.update { it.copy(submitting = true, job = null, error = null) }
            try {
                val job = gateway.createJob(safeRequest)
                applyJob(job, submitting = false)
                if (job.isPending) startPolling(job.id, safeRequest)
            } catch (_: AiSignalEntitlementRequiredException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        entitlementRequired = true,
                        error = "AI Signals require an active server entitlement.",
                    )
                }
            } catch (_: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        quotaExhausted = true,
                        error = "AI Signal quota is exhausted.",
                    )
                }
            } catch (error: AiSignalRequestRejectedException) {
                _state.update { it.copy(submitting = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        error = error.message ?: "AI Signal request failed.",
                    )
                }
            }
        }
    }

    fun refreshCurrent() {
        val current = _state.value.job ?: return
        scope.launch { refreshJob(current.id, current.request, resumePolling = true) }
    }

    fun retryCurrent() {
        val request = _state.value.job?.request ?: return
        submit(request)
    }

    fun dismissJob() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(job = null, submitting = false, error = null) }
    }

    fun clear() {
        pollJob?.cancel()
        pollJob = null
        _state.value = AiSignalState()
    }

    private fun startPolling(jobId: String, request: AiSignalRequest) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(pollIntervalMs)
                val current = _state.value.job
                if (current?.id != jobId || !current.isPending) return@launch
                val shouldContinue = refreshJob(jobId, request, resumePolling = false)
                if (!shouldContinue) return@launch
            }
        }
    }

    private suspend fun refreshJob(
        jobId: String,
        request: AiSignalRequest,
        resumePolling: Boolean,
    ): Boolean {
        return try {
            val job = gateway.job(jobId, request)
            applyJob(job, submitting = false)
            if (resumePolling && job.isPending) startPolling(job.id, request)
            job.isPending
        } catch (_: AiSignalJobExpiredException) {
            val current = _state.value.job
            if (current?.id == jobId) {
                applyJob(
                    current.copy(
                        status = AiSignalJobStatus.EXPIRED,
                        result = null,
                        errorCode = "expired",
                        errorMessage = "AI Signal job expired on the server.",
                    ),
                    submitting = false,
                )
            }
            false
        } catch (_: AiSignalEntitlementRequiredException) {
            _state.update {
                it.copy(
                    entitlementRequired = true,
                    error = "AI Signal status requires an active server entitlement.",
                )
            }
            false
        } catch (error: Exception) {
            _state.update {
                it.copy(error = error.message ?: "Could not refresh AI Signal status.")
            }
            false
        }
    }

    private fun applyJob(job: AiSignalJob, submitting: Boolean) {
        val jobError = when {
            job.status == AiSignalJobStatus.DONE && job.result == null ->
                "Server returned DONE without a validated structured Signal. It cannot be opened or executed."
            job.status == AiSignalJobStatus.FAILED ->
                job.errorMessage ?: "AI Signal generation failed."
            job.status == AiSignalJobStatus.EXPIRED ->
                job.errorMessage ?: "AI Signal job expired."
            else -> null
        }
        _state.update { current ->
            val quota = job.quota ?: current.quota
            current.copy(
                submitting = submitting,
                job = job,
                quota = quota,
                quotaExhausted = quota?.exhausted ?: current.quotaExhausted,
                entitlementRequired = false,
                error = jobError,
            )
        }
    }
}
