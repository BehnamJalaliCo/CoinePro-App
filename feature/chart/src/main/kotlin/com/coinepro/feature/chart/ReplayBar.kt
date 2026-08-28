package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR

/**
 * The replay transport, under the chart.
 *
 * The whole point of replay is that the reader cannot see what happened next, so this bar is
 * deliberately the only thing on screen that says replay is on — and it says it loudly, in the
 * brand accent, because a reader who forgets they are in replay is reading a live chart that is
 * hours stale. Leaving is one tap and it is never hidden behind a menu.
 *
 * The speed ladder is nine fixed steps rather than a slider: a slider invites hunting for a speed
 * instead of watching the chart. The scrub *is* a slider, because "somewhere around here" is
 * exactly what a reader means when they drag it.
 */
@Composable
internal fun ReplayBar(
    state: ReplayState,
    onToggle: () -> Unit,
    onStep: () -> Unit,
    onStepBack: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: (Double) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "بازپخش",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Accent,
            )
            Text(
                // Which bar of how many. The count is a prose figure, so Persian numerals, and it
                // is isolated as one run so the slash does not migrate across the pair in RTL.
                text = BidiText.isolateLtr(
                    "${(state.cursor + 1).toPersianDigits()} / ${state.bars.size.toPersianDigits()}",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            TransportButton(CoineProIcons.StepBack, "یک میله عقب", onStepBack)
            TransportButton(
                icon = if (state.playing) DesignR.drawable.icon_pause else DesignR.drawable.tv_play,
                label = if (state.playing) "توقف" else "پخش",
                onClick = onToggle,
                tint = CoineProColors.Accent,
            )
            TransportButton(CoineProIcons.StepForward, "یک میله جلو", onStep)
            TransportButton(DesignR.drawable.icon_x, "خروج از بازپخش", onExit)
        }

        Slider(
            value = state.progress,
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = CoineProColors.Accent,
                activeTrackColor = CoineProColors.Accent,
                inactiveTrackColor = CoineProColors.Border,
            ),
            modifier = Modifier.fillMaxWidth().height(20.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Replay.SPEEDS.forEach { speed ->
                val selected = speed == state.speed
                Text(
                    // The multiplier is a market-adjacent figure and stays Latin, like every other
                    // number a trader compares against another app.
                    text = BidiText.isolateLtr(
                        if (speed < 1) "${speed}×" else "${speed.toInt()}×",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .background(if (selected) CoineProColors.Accent else Color.Transparent)
                        .clickable { onSpeed(speed) }
                        .padding(horizontal = CoineProSpacing.One, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = CoineProColors.TextSecondary,
) {
    Box(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .clickable(onClick = onClick)
            .padding(CoineProSpacing.Half),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}
