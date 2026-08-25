package com.coinepro.core.account

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the home screen knows about the reader's account.
 *
 * Briefing and portfolio are separate states rather than one combined "home" state, because they
 * fail separately and a reader must be able to tell which failed. A portfolio that loads while the
 * briefing does not is a working screen with one quiet card, not a broken screen.
 */
sealed interface BriefingState {
    data object Idle : BriefingState
    data object Loading : BriefingState

    /** The server answered and had nothing to say. Not a failure — the resting state is correct. */
    data object Nothing : BriefingState
    data class Ready(val briefing: AccountBriefing) : BriefingState

    /** [reason] is the server's own wording when it gave one, and absent when it did not answer. */
    data class Unavailable(val reason: String?) : BriefingState
}

sealed interface PortfolioState {
    data object Idle : PortfolioState
    data object Loading : PortfolioState

    /**
     * A portfolio arrived. It may still carry a null total — that is the server saying the bridge
     * reported nothing, which the screen draws as a dash rather than as a zero balance.
     */
    data class Ready(val portfolio: AccountPortfolio) : PortfolioState
    data class Unavailable(val reason: String?) : PortfolioState
}

class AccountController(
    private val gateway: AccountGateway,
    private val scope: CoroutineScope,
) {
    private val briefingMutable = MutableStateFlow<BriefingState>(BriefingState.Idle)
    private val portfolioMutable = MutableStateFlow<PortfolioState>(PortfolioState.Idle)
    private val kycMutable = MutableStateFlow<KycStatus?>(null)

    val briefing: StateFlow<BriefingState> = briefingMutable.asStateFlow()
    val portfolio: StateFlow<PortfolioState> = portfolioMutable.asStateFlow()
    val kyc: StateFlow<KycStatus?> = kycMutable.asStateFlow()

    /**
     * Refreshes both cards.
     *
     * They are launched independently rather than sequenced, so a slow briefing does not hold up a
     * balance the reader opened the app to see.
     */
    fun refresh() {
        refreshBriefing()
        refreshPortfolio()
    }

    fun refreshBriefing() {
        if (briefingMutable.value is BriefingState.Loading) return
        briefingMutable.value = BriefingState.Loading
        scope.launch {
            briefingMutable.value = when (val result = gateway.briefing()) {
                is AppResult.Success -> result.value
                    ?.let(BriefingState::Ready)
                    ?: BriefingState.Nothing
                is AppResult.Failure -> BriefingState.Unavailable(result.message)
            }
        }
    }

    fun refreshPortfolio() {
        if (portfolioMutable.value is PortfolioState.Loading) return
        portfolioMutable.value = PortfolioState.Loading
        scope.launch {
            portfolioMutable.value = when (val result = gateway.portfolio()) {
                is AppResult.Success -> PortfolioState.Ready(result.value)
                is AppResult.Failure -> PortfolioState.Unavailable(result.message)
            }
        }
    }

    /**
     * Refreshes the verification status.
     *
     * A failure leaves the previous value alone rather than clearing it. Losing the status would
     * read on screen as "not verified", which is a claim about the reader's account that a failed
     * request is in no position to make.
     */
    fun refreshKyc() {
        scope.launch {
            (gateway.kyc() as? AppResult.Success)?.let { kycMutable.value = it.value }
        }
    }

    // Submitting level-1 verification is deliberately not exposed here yet. The gateway can do it,
    // but a submission needs somewhere to put a refusal — the server rejects an invalid national ID
    // with wording the reader has to see — and that belongs with the screen that will collect the
    // fields. A method that dropped those refusals silently would be worse than none.
}
