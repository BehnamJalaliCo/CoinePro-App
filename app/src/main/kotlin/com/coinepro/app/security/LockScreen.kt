package com.coinepro.app.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint

/**
 * What is on screen while the app is locked.
 *
 * ### It is a curtain, not a warning
 *
 * The prompt is raised the moment this appears, so most readers never look at this screen for
 * longer than the dialog takes to animate in. It exists for the case they dismissed the prompt —
 * by accident, or to go and clean a fingerprint sensor — and needs exactly one thing on it: the
 * way back in. A locked screen with no button is a trap, and it is the trap this kind of feature
 * usually ships with.
 *
 * ### The copy does not accuse anybody
 *
 * «حسابتان باز است؛ فقط این پنجره بسته است» is there because the alternative reading of a sudden
 * lock screen is that something went wrong with the account — a session expired, a login
 * elsewhere, a suspension. Saying plainly that nothing happened to the account is worth a line.
 *
 * ### Nothing of the app composes behind it
 *
 * The gate swaps this in *instead of* the shell rather than drawing it on top. A screenshot taken
 * from the recents list, or a stray recomposition, cannot show a balance that is supposed to be
 * behind a fingerprint.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Four),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            Box(
                modifier = Modifier
                    .size(PLATE)
                    .clip(CircleShape)
                    .background(CoineProTint.fill(CoineProColors.Gold, CoineProColors.Surface))
                    .border(1.dp, CoineProTint.edge(CoineProColors.Gold), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoineProIcons.Locked),
                    contentDescription = null,
                    tint = CoineProColors.Gold,
                    modifier = Modifier.size(GLYPH),
                )
            }
            Text(
                text = stringResource(R.string.lock_screen_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.lock_screen_body),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.lock_screen_action),
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CoineProSpacing.One),
            )
        }
    }
}

private val PLATE = 64.dp
private val GLYPH = 30.dp
