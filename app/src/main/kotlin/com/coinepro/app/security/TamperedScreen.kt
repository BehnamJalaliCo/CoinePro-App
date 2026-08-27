package com.coinepro.app.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.coinepro.app.R
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * What a repackaged copy of this app shows instead of the app.
 *
 * Everything about the wording is deliberate. It does not say "an error occurred", because nothing
 * went wrong — this is the app working. It does not blame the reader, who almost certainly
 * installed it from somewhere they were told to trust. It names the one safe action, which is to
 * remove it and get the real one, and it says the thing that actually matters to somebody holding
 * a trading app: **do not sign in here.** A copy that got this far is a copy that can read whatever
 * is typed into it.
 *
 * There is no "continue anyway". A button that lets somebody past this would be the first thing a
 * repackager tells them to press.
 */
@Composable
fun TamperedScreen(actualFingerprint: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tampered_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CoineProColors.Sell,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.tampered_body),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Sell) {
            Text(
                text = stringResource(R.string.tampered_fingerprint),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            Text(
                // Isolated: a hex fingerprint inside Persian text is reordered by the bidi
                // algorithm otherwise, and a fingerprint read out backwards is worse than none.
                text = BidiText.isolateLtr(actualFingerprint),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
            )
        }
        Text(
            text = stringResource(R.string.tampered_source),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
