package com.coinepro.feature.membership

import com.coinepro.core.common.BrandConfig
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProBrandButton
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.MembershipTerms
import com.coinepro.core.membership.MembershipAction
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.membership.MembershipJourney
import com.coinepro.core.membership.MembershipStatus
import com.coinepro.core.membership.MembershipStep
import com.coinepro.core.membership.MembershipStepState
import com.coinepro.core.membership.UidSubmission
import com.coinepro.core.membership.membershipJourney

/**
 * The whole route from "I have just installed this" to "I can see signals", on one surface.
 *
 * ### What was here before, and why it had to go
 *
 * A reader who reached the signals tab without membership got one card. It said that a subscription
 * is bought through the Telegram bot or the web panel, and it offered a single button that asked the
 * server the same question again. For somebody who found this app in Google Play that is a dead end
 * in three separate ways at once: they have never heard of the channel, there is nothing in the app
 * that tells them what the bot is or which of two channels to join, and — worst — **the sentence is
 * not true of this product any more.** §۶ of the published terms and the Play data declaration both
 * say Pro Chart sells nothing: membership is free, and its condition is a funded exchange account
 * opened through the referral link and verified from the exchange's own API by UID. An app that
 * declares no purchases and then tells a reader to go and buy a subscription is not merely
 * unhelpful, it is the kind of contradiction a Play reviewer opens a policy ticket about.
 *
 * So this panel states the arrangement, shows where the reader stands in it, and puts the next
 * action *inside the step it belongs to*. The checklist is the interface rather than an illustration
 * beside one.
 *
 * ### Three rules it is built on
 *
 * **Evidence before the ask.** The track record is drawn above the steps when the server has one:
 * real closed signals with the percentages the ladder actually banked. A locked screen that shows
 * nothing of the product is asking for trust it has not earned, and the app already publishes this
 * to complete strangers on the guest home — so withholding it from a signed-in reader was never a
 * confidentiality decision, only an oversight. Live signals stay withheld; a closed trade is
 * history, and history is what an argument is made of.
 *
 * **Nothing is claimed that the server did not say.** Every tick beside a step comes out of
 * [membershipJourney], which deduces only from the status payload — see its documentation for what
 * each deduction rests on. A step the server has not spoken to is drawn as unknown, in the muted
 * ink, and says so in words. When the status itself cannot be read, *every* step goes unknown and
 * the panel says that too. The one sentence of prose about the reader's own account is the server's
 * `message_fa`, exactly as it wrote it, and the `note` beside it is never carried this far.
 *
 * **No branch ends without an action.** Every state resolves to a [MembershipAction], including the
 * ones this build has never heard of, and every action draws a control that does something real —
 * the referral links, the UID form, a re-check, or the reload that a newly approved reader needs.
 *
 * The registration links are the server's and are never compiled in. A link one release out of date
 * does not fail visibly: the exchange simply never records the account as Pro Chart's, so the reader
 * funds it, submits their UID, and is refused for a reason nothing on screen could explain. Where
 * the server sent none, the steps are still stated and the panel says the link has not arrived —
 * which is honest, and is not a promise the app cannot keep.
 */
@Composable
fun MembershipJourneyPanel(
    controller: MembershipController,
    modifier: Modifier = Modifier,
    /** The server's terms — referral links and the deposit threshold. Null before they arrive. */
    terms: MembershipTerms? = null,
    /** Exchanges that accept a UID; a superset of the copy-trading list, and the server's answer. */
    uidExchanges: List<String> = terms?.uidExchanges.orEmpty(),
    /** Closed signals with their real outcome. Null where the server had nothing gradeable. */
    trackRecord: GuestTrackRecord? = null,
    /**
     * What to do once the server says access is open.
     *
     * Non-null only on a surface with something to load — the signals list. On the membership tab
     * itself there is nothing behind the panel, and a button that reloads the screen the reader is
     * already looking at is a button that does nothing.
     */
    onAccessGranted: (() -> Unit)? = null,
    /**
     * Opens the terms inside the app.
     *
     * Null keeps the old behaviour — the published page in a browser — so a caller that has not
     * been wired to `:feature:legal` still works.
     */
    onOpenTerms: (() -> Unit)? = null,
    headline: String = stringResource(R.string.membership_access_title),
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val submission by controller.submission.collectAsStateWithLifecycle()
    val journey = remember(state) { membershipJourney(state) }
    val context = LocalContext.current

    // Read on arrival rather than polled. The answer changes when the reader funds an account or
    // submits a UID, not on a timer — and the controller drops a refresh that is already in flight,
    // so a recomposition cannot turn this into a request loop.
    LaunchedEffect(controller) { controller.refresh() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        HeaderCard(headline = headline, journey = journey, onAccessGranted = onAccessGranted)

        // The argument, before the ask. Absent entirely when the server has nothing gradeable —
        // an empty results card reads as a desk that has never traded.
        trackRecord?.let { TrackRecordCard(it) }

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                Text(
                    text = stringResource(R.string.membership_access_where),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                journey.steps.forEachIndexed { index, progress ->
                    StepRow(
                        number = index + 1,
                        step = progress.step,
                        state = progress.state,
                        terms = terms,
                        // Nothing is said about any step until the first read comes back. The
                        // steps themselves are facts and are drawn immediately; where the reader
                        // stands in them is not, and is left blank rather than guessed at.
                        showState = !journey.loading,
                    ) {
                        StepAction(
                            step = progress.step,
                            journey = journey,
                            terms = terms,
                            uidExchanges = uidExchanges,
                            submission = submission,
                            controller = controller,
                            onOpenUrl = context::openHttps,
                        )
                    }
                }
            }
        }

        CoineProSecondaryButton(
            text = stringResource(R.string.membership_access_terms),
            // In the app where the caller can show it. A browser is the fallback, not the route:
            // its address bar is the only place this app's hosting address was ever visible.
            onClick = { if (onOpenTerms != null) onOpenTerms() else context.openHttps(TERMS_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What this is, and the server's own sentence about this reader.
 *
 * The free-membership line is the app's own and is a statement of the published terms rather than a
 * marketing claim — it is the single fact that reframes the whole screen for somebody who arrived
 * expecting a paywall, and it has to be above the steps or it is read after the reader has already
 * decided what this screen is.
 */
@Composable
private fun HeaderCard(
    headline: String,
    journey: MembershipJourney,
    onAccessGranted: (() -> Unit)?,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth(), accent = journey.accent()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.membership_access_free),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            journey.status?.let { status ->
                Text(
                    text = stringResource(status.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = journey.accent(),
                )
            }
            // The server's own sentence, exactly as written, and only about this account. Where it
            // sent none and the status could not be read, the panel says the status is unknown —
            // which is the honest thing and is not the same as saying nothing at all.
            if (journey.loading) {
                CoineProThinkingDots()
            } else {
                val sentence = journey.serverMessage
                    ?: stringResource(R.string.membership_access_unreadable)
                        .takeIf { !journey.statusKnown }
                sentence?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
            journey.uidOnFile?.let { uid ->
                Text(
                    text = stringResource(R.string.membership_uid_on_file, BidiText.isolateLtr(uid)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
            // Which exchange the UID belongs to, where the server said. It looks like a detail and
            // is not: an Ourbit membership is a real one that copy trading can never run on, so a
            // reader waiting for the service to trade for them needs to see which of the two the
            // platform actually has on file for them.
            journey.exchangeOnFile?.let { exchange ->
                Text(
                    text = stringResource(
                        R.string.membership_exchange_on_file,
                        BidiText.isolateLtr(exchange.uppercase()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
            // Approval and the signal feed can disagree for a moment — one is a membership table,
            // the other a list route that read it a second earlier — so the reader gets the button
            // that closes the gap rather than a screen that insists they have no access.
            if (journey.action == MembershipAction.RELOAD && onAccessGranted != null) {
                Text(
                    text = stringResource(R.string.membership_access_open),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.Buy,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.membership_access_reload),
                    onClick = onAccessGranted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * One step, its state, and — where there is one — the control that advances it.
 *
 * The numeral is Persian because this is a prose count rather than a market figure, and it is drawn
 * inside the disc rather than as a list marker: Compose's own bullets and counters paint at a fixed
 * left offset, which is the wrong side of a right-to-left column.
 */
@Composable
private fun StepRow(
    number: Int,
    step: MembershipStep,
    state: MembershipStepState,
    terms: MembershipTerms?,
    showState: Boolean,
    action: @Composable () -> Unit,
) {
    val colour = if (showState) state.colour() else CoineProColors.TextMuted
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CoineProTint.fill(colour)),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = state.glyph().takeIf { showState }
            if (glyph == null) {
                Text(
                    text = number.toPersianDigits(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colour,
                )
            } else {
                Icon(
                    painter = painterResource(glyph),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colour,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Text(
                text = stringResource(step.titleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (showState && state == MembershipStepState.AHEAD) {
                    CoineProColors.TextDisabled
                } else {
                    CoineProColors.TextPrimary
                },
            )
            if (showState) {
                Text(
                    text = stringResource(state.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colour,
                )
            }
            // A step already behind the reader does not need explaining, and four paragraphs of
            // instruction they have finished with is what pushes the one that matters off screen.
            if (!showState || state != MembershipStepState.DONE) {
                Text(
                    text = step.detail(terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
            action()
        }
    }
}

/**
 * The control that belongs to one step, or nothing.
 *
 * Placed inside the step rather than collected into a bar at the foot of the card, because the
 * question the reader is answering is "what do I do *now*" and the answer is easier to trust when
 * it is attached to the sentence that explains it.
 */
@Composable
private fun StepAction(
    step: MembershipStep,
    journey: MembershipJourney,
    terms: MembershipTerms?,
    uidExchanges: List<String>,
    submission: UidSubmission,
    controller: MembershipController,
    onOpenUrl: (String) -> Unit,
) {
    val state = journey.steps.first { it.step == step }.state
    when (step) {
        // Never verifiable from inside the app: opening an exchange account happens at the
        // exchange, under their own identity checks. So the hop is made deliberately and with the
        // reason attached, and the referral link goes with the reader rather than being something
        // they are told to find.
        MembershipStep.EXCHANGE_ACCOUNT -> if (state != MembershipStepState.DONE) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                if (state == MembershipStepState.BLOCKED) {
                    Text(
                        text = stringResource(R.string.membership_access_open_exchange),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.Sell,
                    )
                    if (journey.uidFormOffered) {
                        Text(
                            text = stringResource(R.string.membership_access_exchange_uid_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                        )
                    }
                }
                ExchangeLinks(terms = terms, onOpenUrl = onOpenUrl)
            }
        }

        // Funding happens at the exchange too, and there is deliberately no button here pretending
        // otherwise: a "deposit" link that opened a registration page would send a reader who
        // already has an account to sign up for a second one.
        MembershipStep.FUNDING -> if (state == MembershipStepState.CURRENT) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Text(
                    text = stringResource(R.string.membership_access_fund_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.membership_access_recheck),
                    onClick = controller::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // The one step the app can finish on its own, so it is a form and not an instruction.
        MembershipStep.UID -> if (journey.uidFormOffered) {
            UidForm(
                exchanges = uidExchanges.ifEmpty { listOf(DEFAULT_EXCHANGE) },
                submission = submission,
                onSubmit = controller::submitUid,
                onClearSubmission = controller::clearSubmission,
            )
        }

        MembershipStep.VERIFICATION -> if (state == MembershipStepState.CURRENT) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Text(
                    text = stringResource(
                        if (journey.action == MembershipAction.WAIT) {
                            R.string.membership_access_waiting
                        } else {
                            R.string.membership_fallback_error
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.membership_access_recheck),
                    onClick = controller::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Sign-in owns no control of its own. It carries the retry only in the states where no
        // other step does — the status could not be read at all, or it is one this build has never
        // heard of — because a screen whose every step is unknown still has to offer something that
        // works, and two identical retry buttons on one card is a decision nobody asked for.
        MembershipStep.SIGN_IN -> if (
            journey.action == MembershipAction.RETRY && journey.current == null && !journey.loading
        ) {
            CoineProSecondaryButton(
                text = stringResource(R.string.membership_access_recheck),
                onClick = controller::refresh,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The two exchanges, in the same quiet treatment.
 *
 * Neither is filled gold. A filled bar above a plain outline is the shape an advertisement has, and
 * neither exchange is being advertised — what actually separates them is the sentence above each,
 * which says which product it is for. The order carries the rest of the meaning: copy trading
 * first, signals-only second.
 */
@Composable
private fun ExchangeLinks(terms: MembershipTerms?, onOpenUrl: (String) -> Unit) {
    if (terms == null || terms.isEmpty) {
        Text(
            text = stringResource(R.string.membership_links_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.Warning,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        terms.lbankReferralUrl?.let { url ->
            Text(
                text = stringResource(R.string.membership_lbank_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            CoineProBrandButton(
                logo = CoineProIcons.Brand.LBank,
                text = stringResource(R.string.membership_open_lbank),
                onClick = { onOpenUrl(url) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        terms.ourbitReferralUrl?.let { url ->
            Text(
                text = stringResource(R.string.membership_ourbit_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            CoineProBrandButton(
                logo = CoineProIcons.Brand.Ourbit,
                text = stringResource(R.string.membership_open_ourbit),
                onClick = { onOpenUrl(url) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        terms.noticeFa?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
        }
    }
}

/**
 * What the desk has actually done, for a reader who has been asked for nothing yet.
 *
 * The percentages are the server's, as the ladder banked them — the route forbids recomputing them
 * — and the disclaimer is not decoration: past performance is the one claim a screen like this can
 * make dishonestly without anybody noticing.
 */
@Composable
private fun TrackRecordCard(record: GuestTrackRecord) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.membership_record_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            record.winRate?.let { rate ->
                Text(
                    text = stringResource(
                        R.string.membership_record_summary,
                        record.wins.toPersianDigits(),
                        record.entries.size.toPersianDigits(),
                        // See the same line on the guest home: the sign is part of the run.
                        BidiText.percent(MarketNumberFormatter.price(rate, 1)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            record.entries.take(5).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = BidiText.isolateLtr(entry.symbol),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = MarketNumberFormatter.signedPercent(entry.percentGain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.win) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
            }
            Text(
                text = stringResource(R.string.membership_record_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * The UID form.
 *
 * Unchanged in behaviour from the membership tab's own — it is the same composable, moved here so
 * that the tab and the locked signals screen cannot drift into two different forms for one route.
 */
@Composable
private fun UidForm(
    exchanges: List<String>,
    submission: UidSubmission,
    onSubmit: (String, String) -> Unit,
    onClearSubmission: () -> Unit,
) {
    var uid by rememberSaveable { mutableStateOf("") }
    var exchange by rememberSaveable { mutableStateOf(exchanges.first()) }

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        Text(
            text = stringResource(R.string.membership_uid_help),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        if (exchanges.size > 1) {
            CoineProSegmentedControl(
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

/**
 * The colour a step's state is drawn in.
 *
 * `UNKNOWN` is muted rather than warned about: a step the server has not spoken to is not a problem
 * with the reader's account, and painting it amber would turn an ordinary sign-up into a screenful
 * of alarms.
 */
@Composable
private fun MembershipStepState.colour(): Color = when (this) {
    MembershipStepState.DONE -> CoineProColors.Buy
    MembershipStepState.CURRENT -> CoineProColors.Accent
    MembershipStepState.AHEAD -> CoineProColors.TextDisabled
    MembershipStepState.UNKNOWN -> CoineProColors.TextMuted
    MembershipStepState.BLOCKED -> CoineProColors.Sell
}

/** A glyph where the state has one; null keeps the numeral, which says where in the order it sits. */
private fun MembershipStepState.glyph(): Int? = when (this) {
    MembershipStepState.DONE -> CoineProIcons.Success
    MembershipStepState.BLOCKED -> CoineProIcons.Warning
    else -> null
}

@androidx.annotation.StringRes
private fun MembershipStepState.labelRes(): Int = when (this) {
    MembershipStepState.DONE -> R.string.membership_step_done
    MembershipStepState.CURRENT -> R.string.membership_step_current
    MembershipStepState.AHEAD -> R.string.membership_step_ahead
    MembershipStepState.UNKNOWN -> R.string.membership_step_unknown
    MembershipStepState.BLOCKED -> R.string.membership_step_blocked
}

@androidx.annotation.StringRes
private fun MembershipStep.titleRes(): Int = when (this) {
    MembershipStep.SIGN_IN -> R.string.membership_journey_sign_in
    MembershipStep.EXCHANGE_ACCOUNT -> R.string.membership_journey_exchange
    MembershipStep.FUNDING -> R.string.membership_journey_funding
    MembershipStep.UID -> R.string.membership_journey_uid
    MembershipStep.VERIFICATION -> R.string.membership_journey_verification
}

/**
 * The sentence under a step's title.
 *
 * The deposit threshold is the server's, read from the same value the verifier reads, and where the
 * server has not sent one the line names no number at all. A figure that disagrees with the check is
 * worse than no figure: the reader funds exactly what the app asked for and is refused anyway.
 */
@Composable
private fun MembershipStep.detail(terms: MembershipTerms?): String = when (this) {
    MembershipStep.SIGN_IN -> stringResource(R.string.membership_journey_sign_in_detail)
    MembershipStep.EXCHANGE_ACCOUNT -> stringResource(R.string.membership_journey_exchange_detail)
    MembershipStep.FUNDING -> terms?.minDepositUsdt
        ?.let {
            stringResource(
                R.string.membership_journey_funding_amount,
                MarketNumberFormatter.priceAuto(it),
            )
        }
        ?: stringResource(R.string.membership_journey_funding_plain)
    MembershipStep.UID -> stringResource(R.string.membership_journey_uid_detail)
    MembershipStep.VERIFICATION -> stringResource(R.string.membership_journey_verification_detail)
}

/**
 * The card's tint.
 *
 * `PENDING_DEPOSIT` is deliberately not the refusal colour. It means the reader is a real
 * sub-account one deposit away, and painting that red tells somebody who succeeded that they failed.
 */
@Composable
private fun MembershipJourney.accent(): Color = when (status) {
    MembershipStatus.APPROVED -> CoineProColors.Buy
    MembershipStatus.PENDING_DEPOSIT, MembershipStatus.ERROR -> CoineProColors.Warning
    MembershipStatus.REJECTED_REFERRAL -> CoineProColors.Sell
    else -> CoineProColors.Accent
}

@androidx.annotation.StringRes
internal fun MembershipStatus.labelRes(): Int = when (this) {
    MembershipStatus.AWAITING_UID -> R.string.membership_state_awaiting_uid
    MembershipStatus.VERIFYING -> R.string.membership_state_verifying
    MembershipStatus.APPROVED -> R.string.membership_state_approved
    MembershipStatus.PENDING_DEPOSIT -> R.string.membership_state_pending_deposit
    MembershipStatus.REJECTED_REFERRAL -> R.string.membership_state_rejected_referral
    MembershipStatus.ERROR -> R.string.membership_state_error
    MembershipStatus.PENDING -> R.string.membership_state_pending
    MembershipStatus.UNKNOWN -> R.string.membership_state_unknown
}

/**
 * Opens a link, and does nothing where the device has no browser.
 *
 * **https only, and the check is not decorative.** Every URL that reaches this function came from
 * the server, so a compromised response — or one tampered with by anything that could get past TLS —
 * could aim `ACTION_VIEW` at an `intent://` URI and start a component in another app on the reader's
 * phone, or at a `file://` one and hand a local path to a viewer.
 *
 * The scheme is compared after lowercasing because `Uri.parse` preserves the case it was given, and
 * `HTTPS://` is the same scheme to Android and a different string here.
 */
private fun Context.openHttps(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (!uri.scheme.equals("https", ignoreCase = true)) return
    // A host is required as well: `https:///path` parses, has the right scheme, and points nowhere.
    if (uri.host.isNullOrBlank()) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

/** Where the server named no exchange. LBank is the only one this platform can trade on. */
private const val DEFAULT_EXCHANGE = "lbank"

// Compiled in, and never rendered. The reader reaches the page through the named button
// above — the address itself is not on any screen, and printing it would put a personal
// hosting address in front of somebody who asked to read the terms.
private const val TERMS_URL = "${BrandConfig.LEGAL_BASE_URL}/terms/"
