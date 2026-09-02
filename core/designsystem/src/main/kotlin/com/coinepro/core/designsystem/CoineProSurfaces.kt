package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.abs
import kotlin.math.pow

/**
 * The surfaces of the "آرام" direction.
 *
 * Two rules hold the whole direction together, and both are easy to break by accident:
 *
 * 1. A card is an opaque block with a large radius, separated from the page by its **ground** and,
 *    only where the ground cannot do it, by a hairline. No shadow in the dark theme, no gradient,
 *    ever. What separates it from its neighbour is still the gap.
 * 2. Gold appears **once** per screen, on [CoineProPrimaryButton]. Every other control is neutral.
 *    A second gold object on the same screen is a design bug, not a variation.
 *
 * ### The hairline, and the two times this rule has now been wrong
 *
 * It first said "no border", and the argument was that gap is enough to separate two cards. That is
 * true and it answers the wrong question. Two cards are separated by the gap; a card is separated
 * from the *page* by nothing at all, and in the light theme "nothing at all" was a three-percent
 * difference in value. So a screen was a white sheet with slightly-less-white regions printed on
 * it, which is precisely the reading the owner gave it: dry, dead, nothing sitting on anything.
 *
 * The correction was to give every card an edge, unconditionally, and that is the second version
 * that is wrong — because the diagnosis was right about the symptom and wrong about the cause. The
 * card had no ground; drawing a line around a card that has no ground gives you an outline, and an
 * interface of outlined rectangles is the look every cheap template has. The reference the owner
 * put beside this app does not outline its cards. It gives them a ground of ΔL* 4.7 against the
 * page and then draws nothing at all.
 *
 * So the ground was fixed first — see [CoineProPalette.surface] — and the rule here is now
 * conditional and states its own condition: **a card draws a hairline exactly when its own fill
 * does not separate it from the page.** [SEPARATING_LIGHTNESS] is the threshold, in CIE L*, which
 * is the only scale on which "far enough apart to see" means the same thing on a near-black stage
 * and a white one. On the current palettes that resolves to:
 *
 * ```
 *   dark   surface  ΔL*  2.4  → hairline.  #10141B on #0B0E11 is a difference an OLED panel
 *                               resolves at about one value step; it needs the edge.
 *   light  surface  ΔL*  4.5  → no hairline. The ground carries it.
 *   either raised   ΔL*  0.0  → hairline, in the light theme, because raised *is* white on white.
 * ```
 *
 * It is not a switch on the theme, and that matters: a palette that moves brings the answer with
 * it, and a card whose fill is a tint rather than a rung is measured like everything else.
 *
 * ### The pressed state
 *
 * A card that is a button moves its **fill**, not only its scale. The scale is 1% and a 360dp card
 * travels less than a pixel on it; a change in value is what the eye actually reads as contact, and
 * it reads the same on a card of any size. `surfacePressed` is the rung the palette already keeps
 * for exactly this and nothing else was using it.
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
     *
     * A tinted card always carries its edge. The tint is the meaning and the edge is what makes it
     * legible as one rather than as a card somebody spilled something on; that is a different job
     * from the structural hairline below and it is not subject to the same test.
     */
    accent: Color? = null,
    /**
     * Whether the card may carry a hairline **at all**.
     *
     * It is a veto rather than an instruction, and the difference is the change: true no longer
     * means "draw an edge", it means "draw one if the ground cannot do the job" — which on the
     * light theme is now nowhere and on the dark theme is the ordinary card. Pass false for a card
     * drawn *inside* another card, where a second concentric outline reads as a mistake rather than
     * as depth, and the ground is somebody else's problem.
     */
    outlined: Boolean = true,
    /**
     * Lifted out of the page rather than resting on it — the one card on a screen that is the
     * subject, or a panel floating over content.
     *
     * Takes `surfaceRaised` and, in the light theme only, a single very soft shadow. Only in the
     * light theme because a black shadow on a near-black stage is invisible and still costs a
     * render pass every frame; in the dark theme the fill and the hairline do the work, which is
     * the same reason dark interfaces everywhere signal elevation with value rather than with
     * shadow.
     */
    raised: Boolean = false,
    /**
     * Makes the whole card the target.
     *
     * Null keeps the card inert, which is right for most of them. Where a card *is* the button —
     * a signal that opens, a lesson that starts — it should compress under a thumb like every
     * other control in the app instead of being the one surface that does not move.
     */
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = when {
        raised -> CoineProColors.SurfaceRaised
        elevated -> CoineProColors.SurfaceElevated
        else -> CoineProColors.Surface
    }
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    // The pressed rung, tinted the same way the resting one is, so a warning card under a thumb is
    // still a warning card rather than briefly a neutral one.
    val ground = if (isPressed && onClick != null) CoineProColors.SurfacePressed else base
    val target = if (accent == null) ground else CoineProTint.fill(accent, ground)
    val fill by animateColorAsState(
        targetValue = target,
        animationSpec = CoineProMotionSpecs.press(),
        label = "cardFill",
    )
    val edge = when {
        accent != null -> CoineProTint.edge(accent)
        raised -> CoineProColors.Border
        else -> CoineProColors.BorderSubtle
    }
    // Measured against the page rather than assumed from the theme — see this file's header. A
    // tinted card keeps its edge whatever its ground does, because there the edge carries meaning.
    val hairline = outlined && (accent != null || !separatesFromStage(base))
    val lightTheme = !LocalCoineProPalette.current.isDark
    val haptics = rememberCoineProHaptics()
    Column(
        modifier = modifier
            .let { if (raised && lightTheme) it.shadow(RAISED_SHADOW, shape) else it }
            .let { base2 ->
                onClick?.let { action ->
                    base2
                        .pressScale(interaction, CoineProPress.CARD)
                        .clip(shape)
                        .clickable(interaction, null) {
                            haptics.select()
                            action()
                        }
                } ?: base2
            }
            .background(fill, shape)
            .let { plain -> if (hairline) plain.border(1.dp, edge, shape) else plain }
            // The bevel — see [cardSheen]. Dark theme only, and only on a card that carries a
            // hairline: it is the hairline's top edge caught in light, and a card the ground
            // already separates has no edge to catch anything.
            .let { edged -> if (hairline && !lightTheme && accent == null) edged.cardSheen(shape) else edged }
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The one line that makes a dark card read as machined rather than printed.
 *
 * A near-black card on a near-black stage has a hairline around it and nothing else, and the owner's
 * word for the result was flat. What every physical surface has that a flat one does not is a
 * *lit* edge: the top rim, one value lighter than the face, where the light lands. This draws that
 * rim — a single one-pixel stroke inside the top edge, white at eight percent, clipped to the
 * card's own corners so it follows the curve rather than cutting across it.
 *
 * It is not a gradient and it is not a shadow: the surface discipline in `check-motion-policy.sh`
 * bans both on a card, and this is neither. It is not drawn in the light theme, where a white card
 * on a white page has no rim to light and the ground does the separating. And it is eight percent
 * rather than more because the point is that nobody notices it; they notice that the card sits.
 */
private fun Modifier.cardSheen(shape: Shape): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val rim = Path().apply { addOutline(outline) }
    clipPath(rim) {
        drawLine(
            color = SHEEN,
            start = Offset(0f, SHEEN_INSET),
            end = Offset(size.width, SHEEN_INSET),
            strokeWidth = SHEEN_WIDTH,
        )
    }
}

/** White at eight percent: the least a panel resolves, which is the most a rim should be. */
private val SHEEN = Color(0x14FFFFFF)

/** Inside the hairline, so the two read as one lit edge rather than as a double line. */
private const val SHEEN_INSET = 1.5f
private const val SHEEN_WIDTH = 1f

/**
 * Whether a fill is far enough from the page for the page to be what separates it.
 *
 * CIE L* rather than the raw luminance the rest of the app compares with, and the reason is the
 * whole point of having a threshold at all: relative luminance is linear in light, and the eye is
 * not. The dark theme's card sits 0.002 of luminance above its stage and the light theme's sits
 * 0.041 below its own — twenty times as far by that measure, and about twice as far to a reader.
 * L* is the scale on which one number means the same thing at both ends, so it is the scale the
 * rule is written on.
 */
@Composable
@ReadOnlyComposable
private fun separatesFromStage(fill: Color): Boolean =
    abs(perceptualLightness(fill) - perceptualLightness(CoineProColors.Stage)) >=
        SEPARATING_LIGHTNESS

/** CIE L*, from relative luminance. The linear branch below the knee is the standard one. */
private fun perceptualLightness(colour: Color): Float {
    val y = colour.luminance()
    return if (y <= 0.008856f) 903.3f * y else 116f * y.pow(1f / 3f) - 16f
}

/**
 * How far apart, in L*, two surfaces have to be before one reads as sitting on the other.
 *
 * Three and a half, and it is a measured number rather than a taste. TradingView's light theme —
 * the reference the owner set this app against — separates a tile from its page by ΔL* 4.7 and
 * draws no border; this app's light card is now 4.5 and clears the bar. Its dark card is 2.4 and
 * does not, which is correct: on a near-black stage a two-step difference is inside the range a
 * panel's own gamma and a reader's own room light move it by, and there the hairline is the only
 * thing that is certain.
 */
private const val SEPARATING_LIGHTNESS = 3.5f

/**
 * How far a raised card lifts, in the light theme.
 *
 * Three points, which is barely a shadow at all — the point is that the card's bottom edge stops
 * being a hairline and starts being a soft transition, not that anybody notices a drop shadow.
 * Anything deeper and this stops being a flat system.
 */
private val RAISED_SHADOW = 3.dp

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
        modifier = modifier.pressScale(interaction, CoineProPress.CTA),
        enabled = enabled,
        shape = CoineProPillShape,
        // The screen's own accent, not a fixed gold. One button component, four identities: gold
        // where an action executes, blue where it analyses, green where it is social, premium gold
        // where it costs money. The alternative is a variant parameter every call site has to get
        // right, and the one that forgets ships a gold "execute" on an analysis screen.
        //
        // Disabled is its own pair rather than the accent behind 45% alpha. Dimming the whole
        // button moves the fill and the label toward the page *together*, so the label's contrast
        // against its own fill collapsed with it: 2.62:1 in the dark theme and 2.15:1 in the
        // light, which is a button whose text cannot be read at the exact moment the reader is
        // trying to work out why they cannot press it. A neutral fill says unavailable more
        // plainly than a faded gold does, and it keeps the sentence legible while it says it.
        color = if (enabled) CoineProColors.pageAccent else CoineProColors.SurfaceElevated,
        // A rim one step darker than the fill, which is what a gold object has and a gold
        // rectangle does not. `GoldDeep` is the mark's own shadow stop, and the palette names
        // exactly this use for it: "borders on gold surfaces". On the blue and green accents the
        // same darkening is taken from the fill itself, so every accent gets the same edge.
        border = if (enabled) {
            BorderStroke(1.dp, CoineProColors.pageAccent.rim())
        } else {
            BorderStroke(1.dp, CoineProColors.BorderSubtle)
        },
        interactionSource = interaction,
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            ink = if (enabled) CoineProColors.onPageAccent else CoineProColors.TextMuted,
        )
    }
}

/**
 * A neutral pill, for the actions beside the primary one.
 *
 * It carries a hairline and the primary does not, and the asymmetry is the point: a filled gold
 * pill defines its own edge, while a grey pill on a grey card is a shape only if something draws
 * one. Without it the row of secondary actions under a balance read as three smudges — which is
 * what it looked like, because that is what it was.
 */
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
        border = BorderStroke(1.dp, CoineProColors.BorderSubtle),
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
        // Twelve, not sixteen. With the label at 15sp that is a 46dp button — a comfortable
        // target and the height every reference app puts a primary action at. At sixteen it was
        // 56dp, which is taller than a list row, and it appears 105 times in this app: it was the
        // loudest object on every screen that had one.
        modifier = Modifier.padding(
            horizontal = if (icon == null) 18.dp else 14.dp,
            vertical = 12.dp,
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
 * The fill pulled a quarter of the way toward black — the edge a solid object has.
 *
 * A function of the fill rather than a fixed colour, so a blue analysis button and a green social
 * button get the same edge the gold one does, and the gold one's edge resolves to the mark's own
 * `GoldDeep` within a few values.
 */
private fun Color.rim(): Color = lerp(this, Color.Black, RIM_SHIFT)

private const val RIM_SHIFT = 0.28f

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
            .background(tint.copy(alpha = 0.14f), CoineProPillShape)
            // The same ring an instrument logo gets, and for the same reason: a 14% disc on a
            // surface one step below it is a shape whose edge the panel has to guess at.
            .border(1.dp, CoineProColors.assetRing, CoineProPillShape),
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

/**
 * A hairline that starts at the screen's accent and fades out.
 *
 * The one rule in the «طلایی» direction that carries meaning rather than decoration: it closes the
 * header and says the page below it is the same subject. Solid on the reading edge and gone by the
 * far edge, so it reads as an underline for the heading rather than as a divider across the page —
 * a full-width accent line at this weight would be the loudest thing on screen.
 *
 * The default was a fixed [CoineProColors.Gold], and that is what made gold look decorative rather
 * than meaningful: an analysis screen drew a blue button under a gold rule, so the reader was shown
 * two accents on one page and no rule for which was which. It follows [PageAccent] now, like every
 * other accented object, and it takes the *ink* variant because a 1dp line has to survive being
 * read on white — the brand mid-tone at 55% alpha on a white stage is not a line, it is a hint.
 *
 * The gradient is deliberate and allow-listed: it is a rule, not a card, a header or a button, and
 * the fade *is* the shape. See `scripts/quality/check-motion-policy.sh`.
 */
@Composable
fun CoineProGoldRule(
    modifier: Modifier = Modifier,
    colour: Color = CoineProColors.pageAccentInk,
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
 * The trade card's rim: a hairline that runs rose to violet to blue around a plate.
 *
 * Measured off TradingView's phone app, where it edges the one card on the analysis hub that leads
 * to a broker — «Trade with your broker». It is the second gradient this file allows on a surface
 * and, like [CoineProGoldRule], it is a *rule* rather than a fill: 1.5 pt of edge on a grey plate,
 * never a wash behind text. Kept here rather than beside the card that uses it because the
 * motion-policy gate allow-lists gradients by file, and this is the file whose job it is to say
 * which surfaces may carry one.
 */
fun Modifier.spectrumRim(shape: Shape, width: Dp = SPECTRUM_RIM_WIDTH): Modifier =
    border(width, Brush.horizontalGradient(SPECTRUM_RIM_STOPS), shape)

/** Rose, violet, blue — the three colours read off the card's edge at 3×. */
private val SPECTRUM_RIM_STOPS = listOf(Color(0xFFF23C7B), Color(0xFF7F6AFF), Color(0xFF3478FF))
private val SPECTRUM_RIM_WIDTH = 1.5.dp

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
