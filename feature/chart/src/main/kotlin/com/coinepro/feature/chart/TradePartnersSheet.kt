package com.coinepro.feature.chart

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection

/**
 * «معامله با کارگزار»: the venues this app can hand a reader to, and this app's own terminal.
 *
 * ### The order, which is the argument
 *
 * The reader's own account here comes **first** where there is one — a setup on this chart to open
 * in the terminal, or the terminal itself. Somebody who already trades through this app tapped the
 * card to place a trade, not to be sold a broker, and putting three sign-up buttons above the thing
 * they meant would be an advertisement wearing a control's clothes.
 *
 * Underneath it, the three venues, broker first. Each card carries the venue's own mark, one line
 * saying what kind of account it is, and one button. Nothing claims a spread, a fee or a bonus.
 *
 * ### Every link leaves the app
 *
 * A registration page is a third party's form asking for a reader's identity documents, and it is
 * the exact case a WebView in this process must not be used for — the reader would be typing a
 * passport number into a page whose address bar they cannot see, in this app's storage. So it goes
 * to the browser, through `https`-only [openPartner], where the address is visible and the session
 * is the reader's own.
 */
@Composable
internal fun TradePartnersSheetBody(
    /** The in-app route — the setup sheet, or the terminal — or null on a build with neither. */
    onTradeHere: (() -> Unit)?,
    /** What the in-app route is called on this chart, so the button names the thing it does. */
    tradeHereLabel: String,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        onTradeHere?.let { trade ->
            CoineProPrimaryButton(
                text = tradeHereLabel,
                onClick = trade,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(R.string.chart_partners_heading),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        TRADE_PARTNERS.forEach { partner ->
            PartnerCard(partner) { openPartner(context, partner.url) }
        }
        Text(
            text = stringResource(R.string.chart_partners_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * One venue: its mark, what it is, and the way in.
 *
 * The mark is laid out by **height** and never by width, because one of the three is a wordmark ten
 * times as wide as it is tall and the other two are square. `ContentScale.Fit` inside a fixed
 * height and an unbounded width gives each its own proportions, so nothing is stretched and the two
 * kinds of artwork sit on the same optical line.
 */
@Composable
private fun PartnerCard(partner: TradePartner, onOpen: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(partner.logo),
                    contentDescription = partner.name,
                    contentScale = ContentScale.Fit,
                    // Tinted only where the artwork is one flat colour. Tinting LBank's yellow mark
                    // or Ourbit's would replace a company's own colours with this app's ink, which
                    // is both wrong and, on a partner's logo, not ours to do.
                    colorFilter = if (partner.monochrome) {
                        ColorFilter.tint(CoineProColors.TextPrimary)
                    } else {
                        null
                    },
                    modifier = if (partner.carriesName) {
                        Modifier.height(WORDMARK_HEIGHT)
                    } else {
                        Modifier.size(MARK_SIZE)
                    },
                )
                if (!partner.carriesName) {
                    LtrDirection {
                        Text(
                            text = partner.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoineProColors.TextPrimary,
                        )
                    }
                }
            }
            Text(
                text = stringResource(partner.kind.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.chart_partners_open_account),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun TradePartnerKind.labelRes(): Int = when (this) {
    TradePartnerKind.BROKER -> R.string.chart_partners_broker
    TradePartnerKind.EXCHANGE -> R.string.chart_partners_exchange
}

/**
 * Opens a partner's page in the reader's own browser, and only over `https`.
 *
 * The scheme check is the same one `NewsHandoff` and `MembershipGate` make and for the same reason:
 * `ACTION_VIEW` on an `intent://` URI can start a component in another app on this phone. These
 * three addresses are literals in this build and could not be anything else today — the check is
 * here so that the day one of them comes from a server instead, this is not the place that has to
 * be remembered.
 *
 * A device with no browser does nothing rather than crashing. It is rare and it is real.
 */
private fun openPartner(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** The wordmark at 18 dp tall — the cap height of the name that would otherwise sit beside it. */
private val WORDMARK_HEIGHT = 18.dp

/** A square mark beside a name, at the size every other venue mark in this app is drawn. */
private val MARK_SIZE = 26.dp
