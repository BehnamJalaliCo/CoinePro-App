package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestTrackRecordState

/** The two destinations a guest reaches that genuinely need an account behind them. */
enum class GuestGate { SIGNALS, AI }

/**
 * What a guest finds on the two tabs that need an account.
 *
 * The rest of the app is open — markets, search, the chart, the studio, the journal, paper trading,
 * NamaScript, the news on the home screen — so these two are the whole of what is behind the line,
 * and the screen says which two rather than implying the line is somewhere vaguer.
 *
 * On the signals tab it does one more thing, and it is the important one: it shows the **real track
 * record**, the closed signals with the percentages the ladder actually banked. A gate that says
 * "sign in to see the signals" is asking for trust it has not earned; a gate that first shows what
 * the signals did, and *then* offers the account, is making an argument.
 *
 * There is no blurred content behind either of these, and there never should be. Something dressed
 * up to look withheld is an advertisement.
 */
@Composable
fun GuestGateScreen(
    gate: GuestGate,
    controller: GuestController,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val record by controller.trackRecord.collectAsStateWithLifecycle()

    DisposableEffect(controller, gate) {
        if (gate == GuestGate.SIGNALS) controller.refreshTrackRecord()
        onDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProPageHeading(
            title = stringResource(
                when (gate) {
                    GuestGate.SIGNALS -> R.string.guest_signals_title
                    GuestGate.AI -> R.string.guest_ai_title
                },
            ),
            eyebrow = stringResource(R.string.guest_gate_eyebrow),
        )
        CoineProCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Text(
                    text = stringResource(
                        when (gate) {
                            GuestGate.SIGNALS -> R.string.guest_signals_body
                            GuestGate.AI -> R.string.guest_ai_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.guest_gate_action),
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (gate == GuestGate.SIGNALS) {
            (record as? GuestTrackRecordState.Ready)?.let { ready ->
                Text(
                    text = stringResource(R.string.guest_record_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                TrackRecordSummary(
                    record = ready.record,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
            }
        }
    }
}
