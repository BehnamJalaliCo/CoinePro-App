package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    /**
     * A meaning, tinted into the card.
     *
     * Null is the ordinary card and stays a plain block. A colour turns it into the tinted variant
     * — the surface pulled 8% toward it, with a hairline pulled 34% — which is how a warning, a
     * selected row and a premium block are told apart from the cards around them without any of
     * them being *coloured*. See [CoineProTint] for why those two numbers and not alpha.
     */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = if (elevated) CoineProColors.SurfaceElevated else CoineProColors.Surface
    val fill = if (accent == null) base else CoineProTint.fill(accent, base)
    Column(
        modifier = modifier
            .background(fill, shape)
            .let { plain ->
                if (accent == null) {
                    plain
                } else {
                    plain.border(1.dp, CoineProTint.edge(accent), shape)
                }
            }
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The one gold object on the screen.
 *
 * Dark label on gold rather than light: the brand gold is a mid-tone, and near-white on it measures
 * 2.0:1. Against the stage colour the same pairing measures 9.0:1.
 *
 * When [enabled] is false the button dims rather than disappearing. A form's action that vanishes
 * while the form is incomplete leaves a reader looking for what they did wrong; one that is visibly
 * present but dim says the same thing without the search.
 */
@Composable
fun CoineProPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * A glyph before the label, in the label's own colour.
     *
     * Optional and off by default, because most primary actions on this app's screens are the only
     * button in view and an icon on a lone button decorates rather than distinguishes. It earns its
     * place where buttons sit in a row and the reader is choosing between them — which is what Home
     * does, and what a row of three identical pills was doing badly.
     *
     * Tinted with the text rather than drawn as artwork: this is one of ours. Another company's
     * mark goes on [CoineProBrandButton], which uses `Image` precisely so it is never tinted.
     */
    @DrawableRes icon: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Surface(
        onClick = {
            haptics.commit()
            onClick()
        },
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressScale(interaction, CoineProPress.CTA),
        enabled = enabled,
        shape = CoineProPillShape,
        // The screen's own accent, not a fixed gold. One button component, four identities: gold
        // where an action executes, blue where it analyses, green where it is social, premium gold
        // where it costs money. The alternative is a variant parameter every call site has to get
        // right, and the one that forgets ships a gold "execute" on an analysis screen.
        color = CoineProColors.pageAccent,
        interactionSource = interaction,
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            ink = CoineProColors.onPageAccent,
        )
    }
}

/** A neutral pill, for the actions beside the primary one. */
@Composable
fun CoineProSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A glyph before the label. See [CoineProPrimaryButton]'s. */
    @DrawableRes icon: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Surface(
        onClick = {
            haptics.select()
            onClick()
        },
        modifier = modifier.pressScale(interaction, CoineProPress.CONTROL),
        shape = CoineProPillShape,
        color = CoineProColors.SurfaceElevated,
        interactionSource = interaction,
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            ink = CoineProColors.TextPrimary,
        )
    }
}

/**
 * The inside of a button, shared so the two cannot drift apart.
 *
 * The padding is horizontally tighter when a glyph is present: an icon and a label need the same
 * *optical* margin as a label alone, and keeping the number identical makes the iconed button look
 * as though its text has been pushed off-centre.
 */
@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, ink: Color) {
    Row(
        modifier = Modifier.padding(
            horizontal = if (icon == null) 20.dp else 16.dp,
            vertical = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ink,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
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

/** Dim enough to read as unavailable, light enough that the label stays legible. */
private const val DISABLED_ALPHA = 0.45f

/**
 * A hairline that starts gold and fades out.
 *
 * The one rule in the «طلایی» direction that carries meaning rather than decoration: it closes the
 * header and says the page below it is the same subject. Solid on the reading edge and gone by the
 * far edge, so it reads as an underline for the heading rather than as a divider across the page —
 * a full-width gold line at this weight would be the loudest thing on screen.
 *
 * The gradient is deliberate and allow-listed: it is a rule, not a card, a header or a button, and
 * the fade *is* the shape. See `scripts/quality/check-motion-policy.sh`.
 */
@Composable
fun CoineProGoldRule(
    modifier: Modifier = Modifier,
    colour: Color = CoineProColors.Gold,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stops = listOf(colour.copy(alpha = 0.55f), colour.copy(alpha = 0.04f))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(if (rtl) stops.asReversed() else stops),
            ),
    )
}

/**
 * A price series as a single stroke, small enough to sit in a list row.
 *
 * No axis, no labels, no grid: at this size a scale would be unreadable and the shape is the whole
 * message — is this going up, and how steadily. Scaled to its own extremes rather than to a shared
 * one, because the row beside it is a different instrument at a different price and a common scale
 * would flatten every line but the most volatile.
 *
 * An empty or single-point series draws nothing rather than a flat line. A flat line is a claim
 * that the price did not move; nothing is the honest picture of "not loaded yet".
 */
@Composable
fun CoineProSparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    colour: Color = CoineProColors.TextMuted,
    widthDp: Float = 1.4f,
) {
    if (values.size < 2) {
        Box(modifier = modifier)
        return
    }
    val density = LocalDensity.current.density
    Canvas(modifier = modifier) {
        val low = values.min()
        val high = values.max()
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * step
            val y = ((high - value) / span).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = colour,
            style = Stroke(
                width = widthDp * density,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
