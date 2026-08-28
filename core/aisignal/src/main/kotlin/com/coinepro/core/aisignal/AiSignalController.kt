package com.coinepro.core.aisignal

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
                        error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED),
                    )
                }
            } catch (_: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        quotaExhausted = true,
                        error = UiMessage.of(MessageKey.AI_GENERATION_FAILED),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED),
                    )
                }
            }
        }
    }

    fun submit(request: AiSignalRequest) {
        val safeSymbol = AiSignalProductScope.normalizeSymbol(request.symbol)
        if (safeSymbol == null) {
            _state.update { it.copy(error = UiMessage.of(MessageKey.AI_SYMBOL_UNSUPPORTED)) }
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
                        error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED),
                    )
                }
            } catch (_: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        quotaExhausted = true,
                        error = UiMessage.of(MessageKey.AI_GENERATION_FAILED),
                    )
                }
            } catch (error: AiSignalRequestRejectedException) {
                _state.update { it.copy(submitting = false, error = UiMessage.fromServer(error.message, MessageKey.AI_GENERATION_FAILED)) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED),
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
                    error = UiMessage.of(MessageKey.AI_ENTITLEMENT_REQUIRED),
                )
            }
            false
        } catch (error: Exception) {
            _state.update {
                it.copy(error = UiMessage.fromServer(error.serverTextOrNull(), MessageKey.AI_GENERATION_FAILED))
            }
            false
        }
    }

    private fun applyJob(job: AiSignalJob, submitting: Boolean) {
        val jobError = when {
            job.status == AiSignalJobStatus.DONE && job.result == null ->
                UiMessage.of(MessageKey.AI_RESULT_UNUSABLE)
            job.status == AiSignalJobStatus.FAILED ->
                UiMessage.fromServer(job.errorMessage, MessageKey.AI_GENERATION_FAILED)
            job.status == AiSignalJobStatus.EXPIRED ->
                UiMessage.fromServer(job.errorMessage, MessageKey.AI_JOB_EXPIRED)
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
