package com.coinepro.feature.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.membership.MembershipState
import com.coinepro.core.membership.MembershipStatus
import com.coinepro.core.membership.MembershipUiState
import com.coinepro.core.membership.UidSubmission

/**
 * Where a reader stands on membership, and what to do next.
 *
 * The screen has one rule and it comes from the server: **`message_fa` is the only sentence shown.**
 * The `note` beside it carries triage — `referral_status=false`, a balance — and the server's own
 * web form once printed it to readers by mistake. Every state below draws the server's sentence and
 * decides only whether to offer an action.
 *
 * The state worth designing for is `pending_deposit`, and it is worth saying why. Of the readers
 * whose submissions were re-checked, none was rejected outright for being unverifiable and eight
 * were genuine sub-accounts whose balance had simply not reached the threshold. That is not a
 * failure state — it is the last step of a working sign-up, and a screen that renders it in the
 * same red as a refusal turns eight recoverable people into eight who think they were turned away.
 */
@Composable
fun MembershipScreen(
    controller: MembershipController,
    /** Exchanges that accept a UID. From the server; a superset of the copy-trading list. */
    uidExchanges: List<String>,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val submission by controller.submission.collectAsStateWithLifecycle()

    LaunchedEffect(controller) { controller.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item {
            Text(
                text = stringResource(R.string.membership_status_title),
                style = MaterialTheme.typography.headlineSmall,
                color = CoineProColors.TextPrimary,
            )
        }

        when (val current = state) {
            MembershipUiState.Idle, MembershipUiState.Loading -> item { CoineProThinkingDots() }

            is MembershipUiState.Unavailable -> item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        Text(
                            text = current.message ?: stringResource(R.string.membership_status_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoineProColors.TextSecondary,
                        )
                        CoineProSecondaryButton(
                            text = stringResource(R.string.membership_retry),
                            onClick = controller::refresh,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            is MembershipUiState.Ready -> {
                item { StatusCard(current.state) }
                if (current.state.awaitsReader) {
                    item {
                        UidForm(
                            exchanges = uidExchanges.ifEmpty { listOf(DEFAULT_EXCHANGE) },
                            submission = submission,
                            onSubmit = controller::submitUid,
                            onClearSubmission = controller::clearSubmission,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: MembershipState) {
    CoineProCard(modifier = Modifier.fillMaxWidth(), accent = state.status.accent()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(state.status.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = state.status.accent(),
            )
            // The server's sentence, exactly. Where it sent none, a neutral line about the state
            // rather than an invented explanation of somebody's account.
            Text(
                text = state.messageFa ?: stringResource(state.status.fallbackRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            state.uid?.let { uid ->
                Text(
                    text = stringResource(R.string.membership_uid_on_file, BidiText.isolateLtr(uid)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun UidForm(
    exchanges: List<String>,
    submission: UidSubmission,
    onSubmit: (String, String) -> Unit,
    onClearSubmission: () -> Unit,
) {
    var uid by rememberSaveable { mutableStateOf("") }
    var exchange by rememberSaveable { mutableStateOf(exchanges.first()) }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.membership_uid_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.membership_uid_help),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )

            if (exchanges.size > 1) {
                com.coinepro.core.designsystem.CoineProSegmentedControl(
                    options = exchanges.map { it to it.uppercase() },
                    selected = exchange,
                    onSelect = { exchange = it },
                )
            }

            CoineProTextField(
                value = uid,
                onValueChange = {
                    uid = it
                    // The previous refusal stops describing the text that is now in the box.
                    if (submission is UidSubmission.Refused) onClearSubmission()
                },
                label = stringResource(R.string.membership_uid_label),
                modifier = Modifier.fillMaxWidth(),
            )

            when (submission) {
                is UidSubmission.Refused -> Text(
                    text = submission.retryAfterSeconds?.let {
                        stringResource(R.string.membership_uid_retry_after, it.toPersianDigits())
                    } ?: submission.message ?: stringResource(R.string.membership_uid_refused),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.Sell,
                )
                UidSubmission.Sent -> Text(
                    text = stringResource(R.string.membership_uid_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.Buy,
                )
                else -> Unit
            }

            CoineProPrimaryButton(
                text = stringResource(R.string.membership_uid_submit),
                onClick = { onSubmit(exchange, uid) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uid.isNotBlank() && submission !is UidSubmission.Sending,
            )
        }
    }
}

/**
 * The colour each state is drawn in.
 *
 * `PENDING_DEPOSIT` is deliberately **not** the refusal colour. It means the reader is a real
 * sub-account who is one deposit away, and painting that red tells somebody who succeeded that they
 * failed.
 */
@Composable
private fun MembershipStatus.accent(): Color = when (this) {
    MembershipStatus.APPROVED -> CoineProColors.Buy
    MembershipStatus.PENDING_DEPOSIT -> CoineProColors.Warning
    MembershipStatus.REJECTED_REFERRAL -> CoineProColors.Sell
    MembershipStatus.ERROR -> CoineProColors.Warning
    else -> CoineProColors.Accent
}

@androidx.annotation.StringRes
private fun MembershipStatus.labelRes(): Int = when (this) {
    MembershipStatus.AWAITING_UID -> R.string.membership_state_awaiting_uid
    MembershipStatus.VERIFYING -> R.string.membership_state_verifying
    MembershipStatus.APPROVED -> R.string.membership_state_approved
    MembershipStatus.PENDING_DEPOSIT -> R.string.membership_state_pending_deposit
    MembershipStatus.REJECTED_REFERRAL -> R.string.membership_state_rejected_referral
    MembershipStatus.ERROR -> R.string.membership_state_error
    MembershipStatus.PENDING -> R.string.membership_state_pending
    MembershipStatus.UNKNOWN -> R.string.membership_state_unknown
}

/** Used only where the server sent no sentence. Never an explanation the app invented. */
@androidx.annotation.StringRes
private fun MembershipStatus.fallbackRes(): Int = when (this) {
    MembershipStatus.APPROVED -> R.string.membership_fallback_approved
    MembershipStatus.VERIFYING, MembershipStatus.PENDING -> R.string.membership_fallback_waiting
    MembershipStatus.ERROR -> R.string.membership_fallback_error
    else -> R.string.membership_fallback_generic
}

/** Where the server named no exchange. LBank is the only one this platform can trade on. */
private const val DEFAULT_EXCHANGE = "lbank"
