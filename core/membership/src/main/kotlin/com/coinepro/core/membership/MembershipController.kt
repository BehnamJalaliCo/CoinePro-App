package com.coinepro.core.membership

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MembershipUiState {
    data object Idle : MembershipUiState
    data object Loading : MembershipUiState
    data class Ready(val state: MembershipState) : MembershipUiState

    /** The read failed. [message] is the server's sentence where it sent one. */
    data class Unavailable(val message: String?) : MembershipUiState
}

/** Where a UID submission has got to. Separate from the status, because a submission can fail. */
sealed interface UidSubmission {
    data object Idle : UidSubmission
    data object Sending : UidSubmission
    data object Sent : UidSubmission
    data class Refused(val message: String?, val retryAfterSeconds: Int?) : UidSubmission
}

/**
 * The membership check, as a screen sees it.
 *
 * Read on demand rather than polled. Verification is a call to an exchange and the answer changes
 * when the reader does something — funds an account, submits a UID — not on a timer. The one place
 * a poll would help is [VERIFYING], and even there the server tells the reader to come back rather
 * than the app spending a request a second to watch a spinner.
 */
class MembershipController(
    private val gateway: MembershipGateway,
    private val scope: CoroutineScope,
) {
    private val stateMutable = MutableStateFlow<MembershipUiState>(MembershipUiState.Idle)
    private val submissionMutable = MutableStateFlow<UidSubmission>(UidSubmission.Idle)

    val state: StateFlow<MembershipUiState> = stateMutable.asStateFlow()
    val submission: StateFlow<UidSubmission> = submissionMutable.asStateFlow()

    fun refresh() {
        if (stateMutable.value is MembershipUiState.Loading) return
        stateMutable.value = MembershipUiState.Loading
        scope.launch {
            stateMutable.value = when (val result = gateway.status()) {
                is AppResult.Success -> MembershipUiState.Ready(result.value)
                is AppResult.Failure -> MembershipUiState.Unavailable(result.message)
            }
        }
    }

    /**
     * Submits a UID, once.
     *
     * Re-entry while one is in flight is dropped rather than queued. The route allows five
     * submissions per ten minutes per account, and a double tap that spends two of them is a
     * reader locked out of their own verification by the app.
     */
    fun submitUid(exchange: String, uid: String) {
        if (submissionMutable.value is UidSubmission.Sending) return
        if (uid.isBlank()) return
        submissionMutable.value = UidSubmission.Sending
        scope.launch {
            when (val result = gateway.submitUid(exchange, uid)) {
                is AppResult.Success -> {
                    submissionMutable.value = UidSubmission.Sent
                    // The submission's own answer is the new status — no second request. Asking
                    // again would race the server's own write and could show the previous state.
                    stateMutable.value = MembershipUiState.Ready(result.value)
                }
                is AppResult.Failure ->
                    submissionMutable.value = UidSubmission.Refused(result.message, result.retryAfterSeconds)
            }
        }
    }

    fun clearSubmission() {
        submissionMutable.value = UidSubmission.Idle
    }

    fun clear() {
        stateMutable.value = MembershipUiState.Idle
        submissionMutable.value = UidSubmission.Idle
    }
}
