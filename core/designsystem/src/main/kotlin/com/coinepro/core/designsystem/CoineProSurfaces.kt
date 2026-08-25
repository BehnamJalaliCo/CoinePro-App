package com.coinepro.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The surfaces of the "آرام" direction.
 *
 * Two rules hold the whole direction together, and both are easy to break by accident:
 *
 * 1. A card is a plain opaque block with a large radius — no border, no shadow, no gradient. What
 *    separates it from its neighbour is the gap between them, not a line.
 * 2. Gold appears **once** per screen, on [CoineProPrimaryButton]. Every other control is neutral.
 *    A second gold object on the same screen is a design bug, not a variation.
 */

/** A block of content on the stage. */
@Composable
fun CoineProCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CoineProSpacing.CardHorizontal,
        vertical = CoineProSpacing.CardVertical,
    ),
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(if (elevated) CoineProColors.SurfaceElevated else CoineProColors.Surface, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The one gold object on the screen.
 *
 * Dark label on gold rather than light: the brand gold is a mid-tone, and near-white on it measures
 * 2.0:1. Against the stage colour the same pairing measures 9.0:1.
 */
@Composable
fun CoineProPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CoineProPillShape,
        color = CoineProColors.Gold,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = CoineProColors.OnAccent,
            textAlign = TextAlign.Center,
        )
    }
}

/** A neutral pill, for the actions beside the primary one. */
@Composable
fun CoineProSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CoineProPillShape,
        color = CoineProColors.SurfaceElevated,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A round token carrying an instrument's initial, in that instrument's own colour.
 *
 * [tint] is applied to the glyph at full strength and to the disc at low alpha, so a row of them
 * reads as a colour-coded list without any of them competing with the gold action.
 */
@Composable
fun CoineProAssetToken(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), CoineProPillShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The assistant's mark: the brand metal turned once around a circle.
 *
 * A sweep gradient rather than an icon so it resolves to the same gold stops as everything else,
 * and so it needs no raster asset at five densities.
 */
@Composable
fun CoineProAgentOrb(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    Canvas(modifier.size(size)) {
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    CoineProColors.GoldBright,
                    CoineProColors.Gold,
                    CoineProColors.GoldDeep,
                    CoineProColors.Gold,
                    CoineProColors.GoldBright,
                ),
                center = center,
            ),
        )
    }
}
