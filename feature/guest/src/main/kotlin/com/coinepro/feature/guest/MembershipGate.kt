package com.coinepro.feature.guest

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
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
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
 * One thing is deliberately absent: the registration link itself. It has to come from the server —
 * `docs/REQUEST4_ACCOUNT_DELETION.md` §3 asks for the route — because a link compiled into an app
 * is a link that is wrong the day it changes, and a wrong one means the exchange never records the
 * account as linked. Losing that silently is worse than one more tap.
 */
@Composable
fun MembershipGate(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** Names the surface the reader was reaching for, so the card answers their actual question. */
    headline: String = stringResource(R.string.membership_headline),
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

            listOf(
                R.string.membership_step_register,
                R.string.membership_step_fund,
                R.string.membership_step_uid,
                R.string.membership_step_verified,
            ).forEachIndexed { index, line ->
                Text(
                    // Numbered in the string rather than by a list marker: a Compose bullet or
                    // counter paints at a fixed left offset, which is the wrong side in RTL.
                    text = "${index + 1}. " + stringResource(line),
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
                text = stringResource(R.string.membership_referral_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )

            CoineProPrimaryButton(
                text = stringResource(R.string.membership_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.membership_read_terms),
                onClick = { context.open(TERMS_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Opens a link, and does nothing where the device has no browser. The address is not a secret. */
internal fun Context.open(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

private const val TERMS_URL = "https://behnamjalalico.github.io/CoinePro-App/terms/"
