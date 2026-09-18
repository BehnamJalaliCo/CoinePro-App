package com.coinepro.app

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.ProChartLockup
import com.coinepro.core.designsystem.continuousMotionAllowed
import com.coinepro.core.designsystem.rememberCoineProHaptics
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * **The first screens anybody sees** (run ΤΦΥ U1; rebuilt in run Ξ, items 10–14).
 *
 * Six frames: the brand, then five slides, auto-advancing, with the two buttons **fixed from the
 * very first frame**. That last part is the whole design and it is the thing every onboarding gets
 * wrong: a reader who has decided in two seconds must not have to sit through three more slides to
 * find the way in, and a button that appears on slide five teaches them that the app is going to
 * make them wait.
 *
 * ### What run Ξ changed, and why the first version was not enough
 *
 * The slides shipped with one primitive each — a sine wave, a grid of squares, three circles — on a
 * flat surface, each looping forever. Three faults, and they compound:
 *
 *  * **One primitive is a diagram, not a picture.** A single stroked path on an empty ground reads
 *    as a placeholder however carefully it is drawn. Each slide is now a **scene** in three layers:
 *    a soft radial glow behind, a mid-layer that carries the subject, and a foreground element that
 *    says what the subject is *for* — the explain card on the chart, the play head on the replay,
 *    the keyhole on the lock.
 *  * **A loop has no meaning.** A picture that moves forever is wallpaper: the eye tunes it out in
 *    a second and it costs a frame every sixteen milliseconds for the rest of the slide. Every
 *    animation here now **runs once, per slide**, and stops: the candles draw themselves left to
 *    right, the tool grid fills cell by cell, the alert ring pulses outward once, the replay bars
 *    sweep, the lock opens. When it has finished, the picture is finished, and the phone is idle.
 *  * **The type was too small for a first impression.** 20/28 with a 15/22 under it is a card's
 *    heading. It is now 28/36 — `displaySmall`, which the type scale already had — with the same
 *    15/22 under it at 70 %, and the block sits at the optical third rather than the middle,
 *    because a centred block on a tall phone reads as low.
 *
 * ### Drawn, not bundled
 *
 * The brief that first asked for these wanted «one vector illustration (SVG ≤ 8 KB, animated in
 * Compose)». What ships is the same thing with the file taken out: each scene is drawn in Compose
 * from primitives, so it is vector, it animates, it costs **zero bytes of assets** rather than eight
 * kilobytes each, and it takes its colours from this file rather than from an export. That is a
 * deviation from the letter of the line, and it is written down here rather than glossed.
 *
 * ### Near-black in both themes, like the launch is white in both
 *
 * `LaunchSplash` is black on white whichever theme the phone is in, for a reason that applies here
 * twice over: the welcome runs **before** the reader has been asked what theme they want — the
 * starter questions come after it — so there is no answer of theirs to follow, and a brand sequence
 * that changes colour with a setting nobody has set yet is a sequence that flashes. So the whole
 * screen is wrapped in the dark palette and drawn on the near-black stage with one accent, the brand
 * gold. The buttons, the dots and the small print inherit it and cannot disagree with the ground.
 *
 * ### One column, whatever the glass is
 *
 * Every measurement on this screen is a fraction of a **column**, not of the window, and the column
 * is capped at [CONTENT_MAX_WIDTH] (run Ψ). Without the cap a 1 280 dp tablet drew a 790 dp square
 * illustration, a headline set on a 1 248 dp measure — six times the length a line of type is
 * readable at — and two full-width pills at the foot, which is the shape of a phone screen stretched
 * rather than a tablet screen designed. The cap is the same idea as a reading measure in print, and
 * it is applied once, at the top, so the scene, the type, the dots and the buttons cannot disagree
 * about what «the width» means. On a phone the cap is never reached and nothing changes.
 *
 * ### Auto-advance stops when a thumb lands
 *
 * A finger on the glass means the reader is reading. The clock is suspended from the **down** — not
 * from the drag start, which is the mistake the first version made, so a thumb resting still on a
 * slide did not stop it — and it does not simply restart on the lift either: there are three
 * seconds of stillness first, and then the slide's own dwell. Somebody who just touched the screen
 * is the last person whose page should move under them.
 *
 * **The carousel takes the reduced-motion guard too**, and that is the part worth stating rather
 * than assuming. A reader who turned animations off did not only ask for fewer moving pixels — they
 * asked for the page to stop changing under them. With the animator scale at zero the slides do not
 * advance on their own and every scene is drawn finished; the swipe and the dots still work, so
 * nothing becomes unreachable and every one of the five is still one gesture away.
 */
@Composable
fun WelcomeSlides(
    /** «شروع» — into the app. The reader has said no to an account and that is the ordinary path. */
    onStart: () -> Unit,
    /** «ورود به حساب» — for somebody who already has one. Never the primary, never absent. */
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The brand sequence has no theme of the reader's to follow. See the note above.
    CoineProTheme(darkTheme = true) {
        WelcomeSequence(onStart = onStart, onSignIn = onSignIn, modifier = modifier)
    }
}

@Composable
private fun WelcomeSequence(
    onStart: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    // One reading of the device's animator scale for the whole screen, so the carousel and the five
    // scenes cannot disagree about it. See the note above.
    val moves = continuousMotionAllowed()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val count = WelcomeSlide.entries.size

    var slide by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var opening by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }

    // The sixth frame (item 14): the mark and the name, held, so the sequence opens on the brand
    // rather than on a drawing of a candle. A hold rather than an animation, which is why it is not
    // behind the motion guard — a reader who turned animations off still gets six frames, the first
    // of which is still.
    LaunchedEffect(Unit) {
        delay(BRAND_HOLD_MS)
        opening = false
    }

    // The clock.
    LaunchedEffect(slide, held, moves, opening, touched) {
        if (opening || held || !moves) return@LaunchedEffect
        if (touched) {
            // Three seconds of stillness after the lift before the clock is running again, and only
            // then the slide's own dwell. Restarting the dwell alone would move the page four
            // seconds after somebody had deliberately stopped it.
            delay(RESUME_MS)
            touched = false
            return@LaunchedEffect
        }
        delay(DWELL_MS)
        forward = true
        slide = (slide + 1) % count
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CoineProColors.Stage.lifted(), CoineProColors.Stage),
                ),
            )
            .systemBarsPadding()
            .testTag(WELCOME_TAG)
            .pointerInput(count, rtl) {
                awaitEachGesture {
                    // `requireUnconsumed = false`, and the clock stops **here** rather than at the
                    // drag start: a thumb resting on a slide is a reader reading it. Run Ξ's whole
                    // lesson, applied to the one other carousel in the app — and nothing consumes
                    // above the touch slop, so the buttons under the slides still take their taps.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    held = true
                    touched = true
                    var travelled = 0f
                    val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        travelled = over
                    }
                    if (first == null) {
                        held = false
                        return@awaitEachGesture
                    }
                    horizontalDrag(first.id) { change ->
                        travelled += change.positionChange().x
                        change.consume()
                    }
                    held = false
                    if (abs(travelled) < SWIPE_ARM.toPx()) return@awaitEachGesture
                    haptics.select()
                    // A drag towards the reading edge goes back: leftwards in English, rightwards
                    // in Persian. Naming the direction rather than the sign is what keeps the
                    // gesture agreeing with the dots, which a `Row` already mirrors.
                    val towardsStart = if (rtl) travelled > 0f else travelled < 0f
                    forward = towardsStart
                    slide = (slide + if (towardsStart) 1 else count - 1) % count
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The optical third, not the middle (item 11). A block centred in the free space of
                // a 20:9 phone sits visibly low, because the eye reads the buttons at the foot as
                // part of the page and the type as its subject.
                Spacer(modifier = Modifier.weight(OPTICAL_ABOVE))
                AnimatedContent(
                    targetState = if (opening) BRAND_FRAME else slide,
                    transitionSpec = {
                        // Shared axis: the two frames travel together on one line, a quarter of the
                        // width, on the system's default spatial spring — which settles in about
                        // the 400 ms the brief asks for. A duration on a spring is its settle, not
                        // a parameter, and a tween here would be a slide that cannot be caught.
                        val sign = if (forward) 1 else -1
                        val mirror = if (rtl) -1 else 1
                        val travel = sign * mirror
                        val entering = fadeIn(tween(SHARED_AXIS_FADE_MS, easing = CoineProMotionSpecs.Enter)) +
                            slideInHorizontally(CoineProMotionSpecs.defaultSpatialFor()) { full ->
                                travel * full / SHARED_AXIS_FRACTION
                            }
                        val leaving = fadeOut(tween(SHARED_AXIS_FADE_MS, easing = CoineProMotionSpecs.Exit)) +
                            slideOutHorizontally(CoineProMotionSpecs.defaultSpatialFor()) { full ->
                                -travel * full / SHARED_AXIS_FRACTION
                            }
                        entering togetherWith leaving
                    },
                    label = "welcome-frame",
                    modifier = Modifier.fillMaxWidth(),
                ) { frame ->
                    if (frame == BRAND_FRAME) {
                        BrandFrame()
                    } else {
                        SlideBody(slide = WelcomeSlide.entries[frame], moves = moves)
                    }
                }
                Spacer(modifier = Modifier.weight(OPTICAL_BELOW))
            }
        }

        // The dots belong to the five slides and the brand frame is not one of them, so they arrive
        // with slide one. The row keeps its height throughout, or the whole page would jump 6 dp.
        Box(
            modifier = Modifier.height(DOT),
            contentAlignment = Alignment.Center,
        ) {
            Dots(count = count, current = slide, visible = !opening)
        }
        Spacer(modifier = Modifier.size(CoineProSpacing.Three))

        Column(
            modifier = Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three),
            // Sixteen between the two buttons (item 12), and twenty-four from the second one to the
            // small print — which is the `padding` below rather than a third gap, so the terms line
            // cannot drift when a button changes height.
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            CoineProPrimaryButton(
                text = stringResource(R.string.welcome_start),
                onClick = onStart,
                // Fifty-six, here and nowhere else. The shared button is 46 dp by a decision worth
                // keeping — it appears a hundred times and was the loudest object on every screen
                // that had one — but this is the one screen where the button *is* the screen, and a
                // 46 dp pill under a 28 sp headline reads as an afterthought. The pressed state,
                // the darkening and the haptic come from the component itself.
                modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.welcome_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
            )
            // One line, and it is the only small print on the screen. It is not a checkbox: a
            // reader who presses «شروع» has agreed to nothing they have to be asked twice about,
            // and the terms are two taps away in the menu for somebody who wants them.
            Text(
                text = stringResource(R.string.welcome_terms),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
        }
    }
}

/**
 * The brand frame — item 14.
 *
 * The mark in gold beside the name in the theme's ink, which is what [ProChartLockup] draws, at the
 * same place on the page the scenes occupy, so the hand-over into slide one is a cross-fade of one
 * object into another rather than a jump between two layouts.
 */
@Composable
private fun BrandFrame() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BRAND_FRAME_ASPECT),
        contentAlignment = Alignment.Center,
    ) {
        ProChartLockup(wordmarkWidth = BRAND_WORDMARK_WIDTH)
    }
}

/**
 * One slide: the scene, the headline, the line under it.
 *
 * The reveal is owned **here** rather than by the screen, and that is what makes item 10's «runs
 * once per slide» true rather than approximately true: `AnimatedContent` composes a new instance of
 * this for every frame, so the `Animatable` is new, `LaunchedEffect(Unit)` fires once, and the
 * outgoing slide keeps the finished picture it had while it slides away.
 */
@Composable
private fun SlideBody(slide: WelcomeSlide, moves: Boolean) {
    val reveal = remember { Animatable(if (moves) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (moves) reveal.animateTo(1f, tween(REVEAL_MS, easing = CoineProMotionSpecs.Enter))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WelcomeArt(
            slide = slide,
            phase = reveal.value,
            modifier = Modifier
                .fillMaxWidth(ART_WIDTH_FRACTION)
                .aspectRatio(1f),
        )
        Spacer(modifier = Modifier.size(CoineProSpacing.Four))
        Text(
            text = stringResource(slide.headline),
            // 28/36 SemiBold, item 11 — and the type scale already had it, so this is the scale's
            // own step rather than a size typed on one screen.
            style = MaterialTheme.typography.displaySmall,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(CoineProSpacing.Three))
        Text(
            text = stringResource(slide.body),
            // 15/22, and the 70 % is an alpha on the ink rather than a fourth grey in the palette:
            // the subtitle is the headline quieter, not a different colour.
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(SUBTITLE_OPACITY),
        )
    }
}

/** The five, in order. Each headline is six words or fewer — U1's rule, and it is a real one. */
enum class WelcomeSlide(@StringRes val headline: Int, @StringRes val body: Int) {
    /** A chart that talks. */
    CHART(R.string.welcome_chart_headline, R.string.welcome_chart_body),

    /** 240+ tools and NamaScript. */
    TOOLS(R.string.welcome_tools_headline, R.string.welcome_tools_body),

    /** Alerts and market depth. */
    ALERTS(R.string.welcome_alerts_headline, R.string.welcome_alerts_body),

    /** Practice without risk. */
    PRACTICE(R.string.welcome_practice_headline, R.string.welcome_practice_body),

    /** Free, Persian, no deposit. */
    FREE(R.string.welcome_free_headline, R.string.welcome_free_body),
}

/**
 * One slide's scene, drawn rather than loaded.
 *
 * Three layers every time — a radial glow behind, a mid-layer carrying the subject, a foreground
 * element that says what the subject is for — in two colours: the brand gold and the theme's ink.
 * One accent and no second hue, which is the rule the rest of the product is drawn by and the reason
 * five different pictures read as one set.
 *
 * [phase] runs 0 to 1 once and stops. At 1 every scene is its finished picture, which is what a
 * reader with animations off sees from the first frame.
 */
@Composable
private fun WelcomeArt(
    slide: WelcomeSlide,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    val gold = CoineProColors.Gold
    val ink = CoineProColors.TextPrimary
    val muted = CoineProColors.Border
    Canvas(modifier = modifier) {
        when (slide) {
            WelcomeSlide.CHART -> drawChartScene(phase, gold, ink, muted)
            WelcomeSlide.TOOLS -> drawToolScene(phase, gold, ink, muted)
            WelcomeSlide.ALERTS -> drawAlertScene(phase, gold, ink, muted)
            WelcomeSlide.PRACTICE -> drawReplayScene(phase, gold, ink, muted)
            WelcomeSlide.FREE -> drawLockScene(phase, gold, ink, muted)
        }
    }
}

/**
 * The layer behind every scene: a soft radial fall-off in the brand gold.
 *
 * Drawn as a radial **brush** rather than as a shadow, which matters for the house rules as much as
 * for the picture: `Modifier.blur` and a tinted `spotColor` are both banned — a blurred panel is a
 * render-effect pass per frame and a coloured shadow is the thing that makes an interface look
 * sprayed on. A gradient inside a drawing is the third case the rules allow, where the gradient *is*
 * the shape rather than decoration applied to a surface.
 */
private fun DrawScope.glow(centre: Offset, radius: Float, gold: Color, strength: Float = 1f) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(gold.copy(alpha = GLOW_ALPHA * strength), Color.Transparent),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * **A chart that explains itself.** Candles draw themselves left to right; the card arrives last.
 *
 * The closes are written down rather than computed from a sine, because a wave is the one shape no
 * market ever prints and a reader who looks at charts all day recognises it instantly as a drawing
 * of a chart rather than a chart.
 */
private fun DrawScope.drawChartScene(phase: Float, gold: Color, ink: Color, muted: Color) {
    glow(Offset(size.width * 0.66f, size.height * 0.42f), size.minDimension * 0.52f, gold)

    // Mid-layer: two hairlines, so the candles stand on something.
    listOf(0.32f, 0.78f).forEach { fraction ->
        drawLine(
            color = muted,
            start = Offset(0f, size.height * fraction),
            end = Offset(size.width, size.height * fraction),
            strokeWidth = size.minDimension * HAIRLINE,
        )
    }

    val slot = size.width / CANDLES
    val body = slot * 0.46f
    val drawn = phase * CANDLES
    CANDLE_OPENS.indices.forEach { index ->
        val appearance = (drawn - index).coerceIn(0f, 1f)
        if (appearance <= 0f) return@forEach
        val centre = slot * (index + 0.5f)
        val open = size.height * CANDLE_OPENS[index]
        val close = size.height * CANDLE_CLOSES[index]
        val high = size.height * CANDLE_HIGHS[index]
        val low = size.height * CANDLE_LOWS[index]
        // The last candle is the live one and takes the accent; the rest are ink, which is how the
        // chart itself is drawn — one colour for history, the accent for now.
        val colour = if (index == CANDLE_OPENS.lastIndex) gold else ink
        val paint = colour.copy(alpha = appearance * (if (index == CANDLE_OPENS.lastIndex) 1f else 0.9f))
        drawLine(
            color = paint,
            start = Offset(centre, high),
            end = Offset(centre, low),
            strokeWidth = size.minDimension * WICK,
        )
        drawRoundRect(
            color = paint,
            topLeft = Offset(centre - body / 2f, minOf(open, close)),
            size = Size(body, maxOf(abs(close - open), size.minDimension * MIN_BODY)),
            cornerRadius = CornerRadius(body * 0.22f),
        )
    }

    // Foreground: the card that carries the reason. It is the whole promise of the slide, so it
    // arrives at the end rather than being there from the first frame.
    val card = ((phase - CARD_AT) / (1f - CARD_AT)).coerceIn(0f, 1f)
    if (card <= 0f) return
    val width = size.width * 0.44f
    val height = size.height * 0.2f
    val left = size.width * 0.5f
    val top = size.height * 0.08f
    drawRoundRect(
        color = gold.copy(alpha = card * 0.14f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(height * 0.3f),
    )
    drawRoundRect(
        color = gold.copy(alpha = card),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(height * 0.3f),
        style = Stroke(width = size.minDimension * HAIRLINE * 2f),
    )
    listOf(0.34f to 0.62f, 0.62f to 0.4f).forEach { (y, run) ->
        drawLine(
            color = ink.copy(alpha = card * 0.75f),
            start = Offset(left + width * 0.14f, top + height * y),
            end = Offset(left + width * (0.14f + run), top + height * y),
            strokeWidth = size.minDimension * 0.012f,
            cap = StrokeCap.Round,
        )
    }
}

/** **240+ tools, and your own.** The grid fills cell by cell; the last cell is the one you write. */
private fun DrawScope.drawToolScene(phase: Float, gold: Color, ink: Color, muted: Color) {
    glow(center, size.minDimension * 0.5f, gold)

    val gap = size.width * 0.055f
    val cell = (size.width - gap * (GRID + 1)) / GRID
    val filled = phase * GRID * GRID
    repeat(GRID) { row ->
        repeat(GRID) { column ->
            val index = row * GRID + column
            val appearance = (filled - index).coerceIn(0f, 1f)
            val topLeft = Offset(gap + column * (cell + gap), gap + row * (cell + gap))
            val radius = CornerRadius(cell * 0.26f)
            drawRoundRect(
                color = muted,
                topLeft = topLeft,
                size = Size(cell, cell),
                cornerRadius = radius,
                style = Stroke(width = size.minDimension * HAIRLINE * 2f),
            )
            if (appearance <= 0f) return@repeat
            drawRoundRect(
                color = gold.copy(alpha = appearance * 0.2f),
                topLeft = topLeft,
                size = Size(cell, cell),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = gold.copy(alpha = appearance),
                topLeft = topLeft,
                size = Size(cell, cell),
                cornerRadius = radius,
                style = Stroke(width = size.minDimension * HAIRLINE * 2f),
            )
            // The last cell is «and your own»: a plus rather than a fill, because the point of
            // NamaScript is the tool that is not in the grid yet.
            if (index == GRID * GRID - 1) {
                val middle = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f)
                val arm = cell * 0.24f
                listOf(
                    Offset(middle.x - arm, middle.y) to Offset(middle.x + arm, middle.y),
                    Offset(middle.x, middle.y - arm) to Offset(middle.x, middle.y + arm),
                ).forEach { (from, to) ->
                    drawLine(
                        color = ink.copy(alpha = appearance),
                        start = from,
                        end = to,
                        strokeWidth = size.minDimension * 0.016f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** **Alerts and the order book.** One ring leaves the bell, once, and the line it crossed stays. */
private fun DrawScope.drawAlertScene(phase: Float, gold: Color, ink: Color, muted: Color) {
    glow(center, size.minDimension * 0.46f, gold, strength = 1f - phase * 0.4f)

    // Mid-layer: the price the alert is set at, and the level it belongs to.
    drawLine(
        color = muted,
        start = Offset(0f, size.height * 0.74f),
        end = Offset(size.width, size.height * 0.74f),
        strokeWidth = size.minDimension * HAIRLINE,
    )
    drawLine(
        color = gold.copy(alpha = 0.55f),
        start = Offset(size.width * 0.08f, size.height * 0.62f),
        end = Offset(size.width * 0.92f, size.height * 0.62f),
        strokeWidth = size.minDimension * 0.01f,
        cap = StrokeCap.Round,
    )

    // The ring: outward once, fading as it goes, and then gone. Not a loop — an alert that repeated
    // forever would be a picture of the one thing an alert must never do.
    val ring = phase.coerceIn(0f, 1f)
    if (ring > 0f) {
        drawCircle(
            color = gold.copy(alpha = (1f - ring) * 0.55f),
            radius = size.minDimension * (0.16f + ring * 0.3f),
            center = Offset(size.width / 2f, size.height * 0.4f),
            style = Stroke(width = size.minDimension * 0.014f),
        )
    }

    // Foreground: the bell, drawn as a dome on a base with its clapper under it.
    val bell = Offset(size.width / 2f, size.height * 0.42f)
    val span = size.minDimension * 0.16f
    val stroke = Stroke(width = size.minDimension * 0.022f, cap = StrokeCap.Round)
    drawArc(
        color = ink,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(bell.x - span, bell.y - span),
        size = Size(span * 2f, span * 2f),
        style = stroke,
    )
    listOf(-span, span).forEach { side ->
        drawLine(
            color = ink,
            start = Offset(bell.x + side, bell.y),
            end = Offset(bell.x + side, bell.y + span * 0.72f),
            strokeWidth = size.minDimension * 0.022f,
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        color = ink,
        start = Offset(bell.x - span * 1.25f, bell.y + span * 0.72f),
        end = Offset(bell.x + span * 1.25f, bell.y + span * 0.72f),
        strokeWidth = size.minDimension * 0.022f,
        cap = StrokeCap.Round,
    )
    drawCircle(color = gold, radius = span * 0.2f, center = Offset(bell.x, bell.y + span * 1.02f))
}

/** **Practise without risk.** The play head sweeps once and the bars behind it turn live. */
private fun DrawScope.drawReplayScene(phase: Float, gold: Color, ink: Color, muted: Color) {
    val head = size.width * (0.08f + phase * 0.84f)
    glow(Offset(head, size.height * 0.5f), size.minDimension * 0.42f, gold)

    drawLine(
        color = muted,
        start = Offset(0f, size.height * 0.76f),
        end = Offset(size.width, size.height * 0.76f),
        strokeWidth = size.minDimension * HAIRLINE,
    )

    val slot = size.width / BARS
    REPLAY_HEIGHTS.indices.forEach { index ->
        val x = slot * (index + 0.5f)
        val tall = size.height * REPLAY_HEIGHTS[index]
        drawLine(
            color = if (x <= head) ink else muted,
            start = Offset(x, size.height * 0.76f),
            end = Offset(x, size.height * 0.76f - tall),
            strokeWidth = slot * 0.4f,
            cap = StrokeCap.Round,
        )
    }

    // Foreground: the head itself, with the play glyph riding on top of it.
    drawLine(
        color = gold,
        start = Offset(head, size.height * 0.2f),
        end = Offset(head, size.height * 0.82f),
        strokeWidth = size.minDimension * 0.012f,
    )
    val tip = size.minDimension * 0.05f
    val glyph = Path().apply {
        moveTo(head - tip * 0.7f, size.height * 0.2f - tip)
        lineTo(head + tip * 0.9f, size.height * 0.2f)
        lineTo(head - tip * 0.7f, size.height * 0.2f + tip)
        close()
    }
    drawPath(glyph, gold)
}

/** **Free, in Persian, no deposit.** The shackle swings open once and stays open. */
private fun DrawScope.drawLockScene(phase: Float, gold: Color, ink: Color, muted: Color) {
    glow(center, size.minDimension * 0.44f, gold, strength = 0.7f + phase * 0.3f)

    val bodyWidth = size.minDimension * 0.42f
    val bodyHeight = size.minDimension * 0.34f
    val bodyLeft = (size.width - bodyWidth) / 2f
    val bodyTop = size.height * 0.52f
    val shackleSpan = bodyWidth * 0.56f
    val hinge = Offset(bodyLeft + bodyWidth / 2f + shackleSpan / 2f, bodyTop)

    // Mid-layer: the shackle, hinged on its right leg. Closed at rest, swung out by the reveal —
    // the one motion on this slide, and it ends where a lock that has been opened ends.
    rotate(degrees = -SHACKLE_OPEN * phase, pivot = hinge) {
        drawArc(
            color = muted,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(hinge.x - shackleSpan, bodyTop - shackleSpan * 0.9f),
            size = Size(shackleSpan, shackleSpan),
            style = Stroke(width = size.minDimension * 0.036f, cap = StrokeCap.Round),
        )
        listOf(hinge.x - shackleSpan, hinge.x).forEach { x ->
            drawLine(
                color = muted,
                start = Offset(x, bodyTop - shackleSpan * 0.4f),
                end = Offset(x, bodyTop),
                strokeWidth = size.minDimension * 0.036f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Foreground: the body, and the keyhole in the accent.
    drawRoundRect(
        color = ink.copy(alpha = 0.1f),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyHeight * 0.26f),
    )
    drawRoundRect(
        color = ink,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyHeight * 0.26f),
        style = Stroke(width = size.minDimension * 0.022f),
    )
    val keyhole = Offset(bodyLeft + bodyWidth / 2f, bodyTop + bodyHeight * 0.44f)
    drawCircle(color = gold, radius = bodyHeight * 0.13f, center = keyhole)
    drawLine(
        color = gold,
        start = keyhole,
        end = Offset(keyhole.x, keyhole.y + bodyHeight * 0.28f),
        strokeWidth = size.minDimension * 0.02f,
        cap = StrokeCap.Round,
    )
}

/**
 * The page dots, whose width is the state (item 13).
 *
 * A spring rather than a tween, because a width travelling is spatial motion and a reader can
 * outrun it with a second swipe — which is exactly the case a spring handles and a curve restarting
 * from rest does not.
 */
@Composable
private fun Dots(count: Int, current: Int, visible: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        repeat(count) { index ->
            val wide = index == current
            val width by animateDpAsState(
                targetValue = if (wide) DOT_WIDE else DOT,
                animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
                label = "dot-width",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(DOT)
                    .alpha(if (visible) 1f else 0f)
                    .clip(CoineProPillShape)
                    .background(if (wide) CoineProColors.Accent else CoineProColors.Border),
            )
        }
    }
}

/** A colour one step lighter than itself — the top of the stage's gradient. */
private fun Color.lifted(): Color = Color(
    red = (red + STAGE_LIFT).coerceAtMost(1f),
    green = (green + STAGE_LIFT).coerceAtMost(1f),
    blue = (blue + STAGE_LIFT).coerceAtMost(1f),
    alpha = alpha,
)

/** The frame before slide one. Not a member of [WelcomeSlide], because it is not a slide. */
private const val BRAND_FRAME = -1

/** How long the brand is held before the first slide. Item 14's number. */
private const val BRAND_HOLD_MS = 600L

/** Long enough to read six words and a line under them, short enough not to be a wait. */
private const val DWELL_MS = 3_200L

/** The stillness a touch buys before the clock is running again. Item 13's number. */
private const val RESUME_MS = 3_000L

/** One run of a scene's reveal. It happens once and then the picture is finished. */
private const val REVEAL_MS = 900

/** The fade half of the shared axis. The travel half is a spring; a fade has no momentum. */
private const val SHARED_AXIS_FADE_MS = 200

/** How much of the width the two frames travel past each other. A quarter is Material's own. */
private const val SHARED_AXIS_FRACTION = 4

/** 70 %, item 11 — an alpha on the ink rather than a fourth grey. */
private const val SUBTITLE_OPACITY = 0.7f

/**
 * The optical third (item 11), as the two weights the free space is split by.
 *
 * Thirty-six per cent of what is left over goes above the block and sixty-four below it, which puts
 * the headline a little above the middle of the page rather than a little below — where a block
 * centred in the space between the status bar and the buttons actually lands on a tall phone. The
 * numbers are weights rather than a fraction of the screen so the split survives a small phone,
 * where the scene and three lines of Persian leave almost nothing to divide.
 */
private const val OPTICAL_ABOVE = 0.72f
private const val OPTICAL_BELOW = 1.28f

/** How far the gradient lifts the stage at the top of the screen. Two per cent of full scale. */
private const val STAGE_LIFT = 0.02f

/** The glow's strength at its centre. Soft enough that nobody can name it as a circle. */
private const val GLOW_ALPHA = 0.22f

private const val CANDLES = 9f
private const val GRID = 4
private const val BARS = 11f
private const val HAIRLINE = 0.006f
private const val WICK = 0.008f
private const val MIN_BODY = 0.012f

/** Where in the reveal the chart slide's explain card arrives. */
private const val CARD_AT = 0.62f

/** How far the lock's shackle swings, in degrees. */
private const val SHACKLE_OPEN = 42f

/**
 * A short series, written down rather than generated.
 *
 * Fractions of the picture's height, top-down, so a smaller number is a higher price. It is a
 * plausible hour of a market rather than a wave: two up bars, a pull-back, a range, then the push
 * the last candle is in the middle of.
 */
private val CANDLE_OPENS = floatArrayOf(0.62f, 0.58f, 0.52f, 0.55f, 0.5f, 0.52f, 0.49f, 0.45f, 0.41f)
private val CANDLE_CLOSES = floatArrayOf(0.58f, 0.52f, 0.55f, 0.5f, 0.52f, 0.49f, 0.45f, 0.41f, 0.3f)
private val CANDLE_HIGHS = floatArrayOf(0.55f, 0.5f, 0.49f, 0.48f, 0.47f, 0.47f, 0.43f, 0.39f, 0.26f)
private val CANDLE_LOWS = floatArrayOf(0.66f, 0.61f, 0.58f, 0.58f, 0.55f, 0.54f, 0.51f, 0.47f, 0.43f)

/** The replay slide's bars, as fractions of the picture's height. */
private val REPLAY_HEIGHTS =
    floatArrayOf(0.2f, 0.3f, 0.26f, 0.4f, 0.34f, 0.46f, 0.38f, 0.5f, 0.42f, 0.54f, 0.47f)

/** How much of the column a scene takes. Leaves room for the words under it on a small phone. */
private const val ART_WIDTH_FRACTION = 0.62f

/**
 * The widest this screen's content column is ever drawn (run Ψ).
 *
 * Four hundred and forty-eight, which is a large phone's width and a little more: wide enough that
 * nothing on a phone is ever narrowed, narrow enough that a 28 sp headline on a tablet sits on a
 * measure somebody can read in one movement of the eye. The illustration, the type, the dots and
 * the two buttons all take their width from this, so a tablet gets the phone's composition centred
 * on a larger stage rather than the phone's composition stretched across it.
 */
private val CONTENT_MAX_WIDTH = 448.dp

/** The brand frame occupies the scene's own box, so the hand-over is a cross-fade, not a jump. */
private const val BRAND_FRAME_ASPECT = 1.6f

private val BRAND_WORDMARK_WIDTH = 200.dp

/** Item 12's height, on this screen only. See the note at the call site. */
private val BUTTON_HEIGHT = 56.dp

private val DOT = 6.dp
private val DOT_WIDE = 20.dp
private val SWIPE_ARM = 48.dp

/** So a test can find the slides without knowing what is drawn on them. */
const val WELCOME_TAG: String = "welcome-slides"
