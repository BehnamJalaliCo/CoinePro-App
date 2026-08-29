package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The AI screen's state: the allowance, what may be asked for, the job in flight, and why one failed.
 *
 * [catalog] is optional and its absence is not a failure — a build wired without one falls back to
 * `AiSignalProductScope`, which is a first screenful rather than a ceiling. What it must never do is
 * silently narrow the offer: [AiSymbolUniverse.origin] says which of the three lists is in force and
 * the screen shows it, because "nine markets" and "four hundred and forty-one markets" mean
 * different things and a reader is entitled to know which one they are looking at.
 */
class AiSignalController(
    private val gateway: AiSignalGateway,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 2_000L,
    private val catalog: AiSymbolCatalog? = null,
    private val platform: MarketPlatform = MarketPlatform.TRADEYAR,
) {
    private val _state = MutableStateFlow(
        AiSignalState(universe = AiSymbolUniverse.fallback(platform)),
    )
    val state: StateFlow<AiSignalState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var catalogJob: Job? = null

    /** Markets discovered from the snapshot endpoint, kept so a later quota can re-rank against them. */
    private var catalogue: List<SymbolMeta> = emptyList()

    /**
     * Load the catalogue once, at most.
     *
     * Not folded into [refreshQuota]: the two answer different questions of different servers'
     * endpoints, and a catalogue that fails must not take the allowance down with it. A reader whose
     * snapshot call timed out still has a working quota, a working button and the fallback list.
     */
    fun loadSymbols() {
        val source = catalog ?: return
        if (catalogue.isNotEmpty() || catalogJob?.isActive == true) return
        _state.update { it.copy(universe = it.universe.copy(loading = true)) }
        catalogJob = scope.launch {
            val loaded = runCatching { source.markets() }.getOrNull().orEmpty()
            catalogue = loaded
            _state.update { it.copy(universe = resolveUniverse(it.quota, loading = false)) }
        }
    }

    fun refreshQuota() {
        loadSymbols()
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
                        universe = resolveUniverse(quota, loading = it.universe.loading),
                        // An exhausted allowance is a fact about tomorrow, not a failed request, so
                        // it is stated by the quota line and the disabled button rather than pushed
                        // into the error slot where it would read as something having gone wrong.
                        error = null,
                    )
                }
            } catch (error: AiSignalEntitlementRequiredException) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        entitlementRequired = true,
                        quotaExhausted = false,
                        error = error.toUiError(AiSignalFailure.ENTITLEMENT_REQUIRED),
                    )
                }
            } catch (error: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        quotaExhausted = true,
                        error = error.toUiError(AiSignalFailure.QUOTA_EXHAUSTED, it.quota?.resetAt),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        refreshingQuota = false,
                        error = error.toUiError(AiSignalFailure.QUOTA_UNAVAILABLE),
                    )
                }
            }
        }
    }

    fun submit(request: AiSignalRequest) {
        val safeSymbol = AiSignalProductScope.normalizeSymbol(request.symbol)
        if (safeSymbol == null) {
            _state.update { it.copy(error = AiSignalError.of(AiSignalFailure.SYMBOL_UNSUPPORTED)) }
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
            } catch (error: AiSignalEntitlementRequiredException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        entitlementRequired = true,
                        error = error.toUiError(AiSignalFailure.ENTITLEMENT_REQUIRED),
                    )
                }
            } catch (error: AiSignalQuotaExhaustedException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        quotaExhausted = true,
                        error = error.toUiError(AiSignalFailure.QUOTA_EXHAUSTED, it.quota?.resetAt),
                    )
                }
            } catch (error: AiSignalRequestRejectedException) {
                _state.update {
                    it.copy(submitting = false, error = error.toUiError(AiSignalFailure.REQUEST_REJECTED))
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(submitting = false, error = error.toUiError(AiSignalFailure.NETWORK_UNAVAILABLE))
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
        catalogJob?.cancel()
        catalogJob = null
        catalogue = emptyList()
        _state.value = AiSignalState(universe = AiSymbolUniverse.fallback(platform))
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
        } catch (error: AiSignalJobExpiredException) {
            val current = _state.value.job
            if (current?.id == jobId) {
                applyJob(
                    current.copy(
                        status = AiSignalJobStatus.EXPIRED,
                        result = null,
                        errorCode = error.serverCode ?: "expired",
                        // Deliberately not a sentence. The screen owns the Persian copy for an
                        // expired job; this used to carry the authored English "AI Signal job
                        // expired on the server." and the screen showed it verbatim.
                        errorMessage = error.serverMessage,
                    ),
                    submitting = false,
                )
            }
            false
        } catch (error: AiSignalEntitlementRequiredException) {
            _state.update {
                it.copy(
                    entitlementRequired = true,
                    error = error.toUiError(AiSignalFailure.ENTITLEMENT_REQUIRED),
                )
            }
            false
        } catch (error: Exception) {
            _state.update { it.copy(error = error.toUiError(AiSignalFailure.NETWORK_UNAVAILABLE)) }
            false
        }
    }

    private fun applyJob(job: AiSignalJob, submitting: Boolean) {
        val jobError = when {
            job.status == AiSignalJobStatus.DONE && job.result == null ->
                AiSignalError(AiSignalFailure.RESULT_UNUSABLE, job.errorMessage, job.errorCode)
            job.status == AiSignalJobStatus.FAILED ->
                AiSignalError(AiSignalFailure.GENERATION_FAILED, job.errorMessage, job.errorCode)
            job.status == AiSignalJobStatus.EXPIRED ->
                AiSignalError(AiSignalFailure.JOB_EXPIRED, job.errorMessage, job.errorCode)
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
                // A create response carries the quota, and on CoinePro-FX that is also where the
                // accepted symbol and timeframe lists arrive — so the offer is re-resolved here as
                // well as after a quota call, rather than only on the screen's first paint.
                universe = resolveUniverse(quota, loading = current.universe.loading),
                error = jobError,
            )
        }
    }

    private fun resolveUniverse(quota: AiSignalQuota?, loading: Boolean) = AiSymbolUniverse.resolve(
        platform = platform,
        stated = quota?.symbols.orEmpty(),
        catalogue = catalogue,
        loading = loading,
    )
}

/**
 * A thrown failure as the screen will show it.
 *
 * The server's own sentence wins over this app's copy wherever there was one — both backends write
 * their refusals in Persian and `ApiErrors` has already discarded FastAPI's English defaults. Where
 * there was none, [fallback] names a reason the screen has a Persian sentence for. What never
 * happens any more is the third case: an exception's own English `message` reaching a reader.
 */
private fun Throwable.toUiError(
    fallback: AiSignalFailure,
    resetAt: String? = null,
): AiSignalError = when (this) {
    is AiSignalException -> AiSignalError(
        reason = fallback,
        serverText = serverMessage,
        serverCode = serverCode,
        resetAt = resetAt,
        serverField = (this as? AiSignalRequestRejectedException)?.field,
    )
    else -> AiSignalError(reason = fallback, resetAt = resetAt)
}
