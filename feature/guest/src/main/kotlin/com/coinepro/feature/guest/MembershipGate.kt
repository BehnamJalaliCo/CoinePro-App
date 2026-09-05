package com.coinepro.feature.guest

import com.coinepro.core.common.BrandConfig
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.guest.MembershipTerms
import com.coinepro.core.designsystem.CoineProBrandButton
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * What a reader without an account is told when they reach signals, copy trading or the AI.
 *
 * This is not a paywall and it is important that it does not read like one, because there is no
 * wall and nothing to pay: CoinePro sells nothing and charges nothing. Membership is an account
 * check rather than a purchase — the reader's exchange account has to be linked to CoinePro for the
 * service to identify them at all — which is why the four steps below are the whole of it.
 *
 * The card states the conditions and stops there. How the arrangement with the exchanges works is
 * the terms' business, not a marketing surface's: a card that explains a commercial relationship to
 * somebody who asked to see a signal has changed the subject.
 *
 * The steps are the server's, read from `app/vip/service.py` — the 50 USDT is `VIP_MIN_DEPOSIT`,
 * not a number chosen here — and they are stated in full rather than summarised, because the first
 * one is the one that cannot be undone. An account opened without the referral link is not a
 * sub-account in the exchange's own system and cannot be verified afterwards; a reader who learns
 * that after registering has lost the thing the screen was for.
 *
 * The registration links come from the server and are never compiled in. A link that is one release
 * out of date does not fail visibly: the exchange simply never records the account as CoinePro's,
 * so the reader funds it, submits their UID, and is refused for a reason nothing on screen can
 * explain. Where the server has not sent one, the card states the four steps and shows no button —
 * which is still useful, and is not a promise the app cannot keep.
 */
@Composable
fun MembershipGate(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Names the surface the reader was reaching for, so the card answers their actual question. */
    headline: String = stringResource(R.string.membership_headline),
    /** The server's terms, or null before they have arrived or where the server sent none. */
    terms: MembershipTerms? = null,
    /** Opens the terms in the app. Null falls back to the published page in a browser. */
    onOpenTerms: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    CoineProCard(modifier = modifier.fillMaxWidth(), accent = CoineProColors.Accent) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.membership_free),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )

            // The deposit threshold is the server's, read from the same value the verifier reads.
            // A number that disagrees with the check is worse than no number: the reader funds
            // exactly what the app asked for and is refused anyway.
            val fund = terms?.minDepositUsdt
                ?.let { stringResource(R.string.membership_step_fund_amount, MarketNumberFormatter.priceAuto(it)) }
                ?: stringResource(R.string.membership_step_fund)

            listOf(
                stringResource(R.string.membership_step_register),
                fund,
                stringResource(R.string.membership_step_uid),
                stringResource(R.string.membership_step_verified),
            ).forEachIndexed { index, line ->
                Text(
                    // Numbered in the string rather than by a list marker: a Compose bullet or
                    // counter paints at a fixed left offset, which is the wrong side in RTL. The
                    // numeral is Persian because this is a prose count, not a market figure — the
                    // rule the whole app follows, and the one a hand-written "1." quietly breaks.
                    text = (index + 1).toPersianDigits() + ". " + line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }

            Text(
                text = stringResource(R.string.membership_copytrade_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            Text(
                // The server's own wording where it sent one. It can be changed without a release,
                // which matters for a sentence whose exact terms are a commercial arrangement.
                text = terms?.noticeFa ?: stringResource(R.string.membership_referral_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )

            // Two exchanges, two different products, and the difference is stated rather than
            // left for somebody to discover after they have funded an account.
            //
            // **Both in the same quiet treatment, on the owner's call.** LBank used to be gold on
            // the reading that it is the only one copy trading runs on, so it should look like the
            // recommendation. In the app that read as a paid placement — a filled gold bar against
            // a plain outline is the shape an advertisement has — and neither exchange is being
            // advertised here. What actually separates them is already on screen and is the honest
            // separator: the sentence above each says which product it is for, and each button
            // carries its own mark, so the choice is made by reading one line and recognising one
            // logo rather than by inferring a ranking from a fill colour.
            //
            // The order still carries the meaning: copy trading first, signals-only second.
            // The server tells the app which exchange does which; see `MembershipTerms.uidExchanges`,
            // which is deliberately a superset of `copyTradeExchanges`.
            terms?.lbankReferralUrl?.let { url ->
                Text(
                    text = stringResource(R.string.membership_lbank_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
                CoineProBrandButton(
                    logo = CoineProIcons.Brand.LBank,
                    text = stringResource(R.string.membership_open_lbank),
                    onClick = { context.open(url) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            terms?.ourbitReferralUrl?.let { url ->
                Text(
                    text = stringResource(R.string.membership_ourbit_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
                CoineProBrandButton(
                    logo = CoineProIcons.Brand.Ourbit,
                    text = stringResource(R.string.membership_open_ourbit),
                    onClick = { context.open(url) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CoineProPrimaryButton(
                text = stringResource(R.string.membership_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.membership_read_terms),
                onClick = { if (onOpenTerms != null) onOpenTerms() else context.open(TERMS_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Opens a link, and does nothing where the device has no browser.
 *
 * **https only, and the check is not decorative.** One caller passes a constant; the other passes
 * `CommunityChannel.url`, which arrives from the server. `ACTION_VIEW` on an arbitrary URI is a
 * request to hand the string to whatever app claims that scheme, so a server that was compromised —
 * or a response tampered with in transit by anything that could get past TLS — could aim this at an
 * `intent://` URI and start a component in another app on the reader's phone, or at a `file://` one
 * and hand a local path to a viewer.
 *
 * The scheme is compared after lowercasing because `Uri.parse` preserves the case it was given, and
 * `HTTPS://` is the same scheme to Android and a different string here.
 */
internal fun Context.open(url: String) {
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

// Compiled in, and never rendered. The reader reaches the page through the named button
// above — the address itself is not on any screen, and printing it would put a personal
// hosting address in front of somebody who asked to read the terms.
private const val TERMS_URL = "${BrandConfig.LEGAL_BASE_URL}/terms/"
