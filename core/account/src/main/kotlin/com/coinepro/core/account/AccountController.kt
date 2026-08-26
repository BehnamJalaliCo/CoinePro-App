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

/**
 * Where the reader is in deleting their account.
 *
 * [Unsupported] is deliberately not a [Refused]. The route not existing on this deployment is a
 * fact about the server, not a rejection of the reader, and the screen answers it by showing the
 * other way to be deleted rather than an error.
 */
sealed interface AccountDeletion {
    data object Idle : AccountDeletion
    data object Deleting : AccountDeletion

    /** Gone. The caller signs out on this — the token it holds now names nobody. */
    data object Done : AccountDeletion

    /** The server has no deletion route. Show the out-of-app route instead. */
    data object Unsupported : AccountDeletion

    /** [reason] is the server's own wording, and null where it did not answer at all. */
    data class Refused(val reason: String?) : AccountDeletion
}

class AccountController(
    private val gateway: AccountGateway,
    private val scope: CoroutineScope,
) {
    private val briefingMutable = MutableStateFlow<BriefingState>(BriefingState.Idle)
    private val portfolioMutable = MutableStateFlow<PortfolioState>(PortfolioState.Idle)
    private val kycMutable = MutableStateFlow<KycStatus?>(null)
    private val kycSubmissionMutable = MutableStateFlow<KycSubmission>(KycSubmission.Idle)
    private val deletionMutable = MutableStateFlow<AccountDeletion>(AccountDeletion.Idle)

    val briefing: StateFlow<BriefingState> = briefingMutable.asStateFlow()
    val portfolio: StateFlow<PortfolioState> = portfolioMutable.asStateFlow()
    val kyc: StateFlow<KycStatus?> = kycMutable.asStateFlow()
    val kycSubmission: StateFlow<KycSubmission> = kycSubmissionMutable.asStateFlow()
    val deletion: StateFlow<AccountDeletion> = deletionMutable.asStateFlow()

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

    /**
     * Submits level-1 verification.
     *
     * The refusal is the point. A server rejects an invalid national id or an unreadable birth date
     * in its own words, and those words are the only useful thing the reader gets — so they are
     * carried into [kycSubmission] verbatim rather than replaced with a generic failure. The app has
     * no better explanation for why a particular id was refused, and inventing one in the service's
     * voice would be worse than saying nothing.
     */
    fun submitKycLevel1(fullName: String, nationalId: String, birthDate: String, phone: String) {
        if (kycSubmissionMutable.value is KycSubmission.Sending) return
        kycSubmissionMutable.value = KycSubmission.Sending
        scope.launch {
            when (val result = gateway.submitKycLevel1(fullName, nationalId, birthDate, phone)) {
                is AppResult.Success -> {
                    kycMutable.value = result.value
                    kycSubmissionMutable.value = KycSubmission.Accepted
                }
                is AppResult.Failure ->
                    kycSubmissionMutable.value = KycSubmission.Refused(result.message)
            }
        }
    }

    /** Clears a finished submission so the screen can be reopened without its last outcome. */
    /**
     * Deletes the account, once.
     *
     * Re-entry while a deletion is in flight is dropped rather than queued. A second DELETE would
     * arrive after the first succeeded and be answered 401 by a server that no longer knows this
     * token — which would put "your session expired" in front of someone whose account was in fact
     * deleted correctly.
     */
    fun deleteAccount() {
        if (deletionMutable.value is AccountDeletion.Deleting) return
        deletionMutable.value = AccountDeletion.Deleting
        scope.launch {
            deletionMutable.value = when (val result = gateway.deleteAccount()) {
                is AppResult.Success -> when (result.value) {
                    DeletionOutcome.DELETED -> AccountDeletion.Done
                    DeletionOutcome.UNSUPPORTED -> AccountDeletion.Unsupported
                }
                is AppResult.Failure -> AccountDeletion.Refused(result.message)
            }
        }
    }

    fun clearDeletion() {
        deletionMutable.value = AccountDeletion.Idle
    }

    fun clearKycSubmission() {
        kycSubmissionMutable.value = KycSubmission.Idle
    }
}

/** Where a level-1 submission has got to. */
sealed interface KycSubmission {
    data object Idle : KycSubmission
    data object Sending : KycSubmission

    /** The server took it. What happens next is in [AccountController.kyc], not here. */
    data object Accepted : KycSubmission

    /** [message] is the server's own wording, shown as written; null when it gave none. */
    data class Refused(val message: String?) : KycSubmission
}
