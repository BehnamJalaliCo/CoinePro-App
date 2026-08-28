package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * What a completed action says back.
 *
 * ### The gap this closes
 *
 * Before this file, no successful action in the whole app produced any feedback at all. Starring a
 * market, saving a chart layout, creating an alert, copying a wallet address, sending a message to
 * support — each one did its work in silence, and silence is indistinguishable from a tap that
 * missed. So readers tap twice, and the second tap un-stars the market they just starred.
 *
 * Failures were half-served: a screen that could hold an error banner did, but an action that
 * failed from inside a sheet had nowhere to put the news and dropped it.
 *
 * ### Why not Material's `Snackbar`
 *
 * Material's needs a `SnackbarHostState` threaded to every call site through a `Scaffold`, and
 * this app does not use `Scaffold` — its shell is a `Box` with its own bars. Rather than retro-fit
 * a scaffold to seventeen screens so a two-second message can appear, the host is a
 * [staticCompositionLocalOf] set once at the top of the tree, and any composable anywhere below
 * calls `LocalToaster.current.show(...)`. That is the same reach with none of the plumbing.
 *
 * ### One at a time, and the newest wins
 *
 * No queue. A queue means a reader who taps three things watches three messages play out after
 * they have moved on, and the message they are shown is about something they no longer have on
 * screen. The most recent action is the one they are thinking about, so it replaces whatever was
 * showing.
 */
@Stable
class CoineProToaster internal constructor() {

    internal var current by mutableStateOf<CoineProToast?>(null)
        private set

    /** Sequence, so an identical message shown twice still restarts the timer. */
    internal var sequence by mutableStateOf(0)
        private set

    /** Show [toast], replacing anything on screen. */
    fun show(toast: CoineProToast) {
        current = toast
        sequence++
    }

    /** The common case: a sentence and a tone. */
    fun show(message: String, tone: ToastTone = ToastTone.NEUTRAL) {
        show(CoineProToast(message = message, tone = tone))
    }

    /** Dismiss whatever is showing. Idempotent. */
    fun dismiss() {
        current = null
    }
}

/** A single message. */
data class CoineProToast(
    val message: String,
    val tone: ToastTone = ToastTone.NEUTRAL,
    /**
     * One action at most, and it is nearly always an undo.
     *
     * A message with two buttons is a dialog that took the reader's consent for granted. If a
     * choice is needed, ask it with [CoineProConfirmDialog] before doing the thing.
     */
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** How long it stays. Defaults follow [tone] — a failure is read more slowly than a success. */
    val durationMillis: Long? = null,
)

/**
 * How a message reads.
 *
 * Three, not five. A reader distinguishes "it worked", "it did not" and "here is a fact"; finer
 * grades are a palette exercise nobody parses at a glance.
 */
enum class ToastTone {
    /** It worked. */
    SUCCESS,

    /** It did not. Stays longest, because it is the one that may need acting on. */
    FAILURE,

    /** A statement of fact with no verdict — "you are offline", "copied". */
    NEUTRAL,
}

val LocalToaster = staticCompositionLocalOf { CoineProToaster() }

/** Creates the one toaster for a tree and provides it. Call once, at the top of the app. */
@Composable
fun ProvideToaster(content: @Composable () -> Unit) {
    val toaster = remember { CoineProToaster() }
    CompositionLocalProvider(LocalToaster provides toaster, content = content)
}

/**
 * Draws whatever the toaster is holding.
 *
 * Placed inside a [Box] by the app shell, aligned to the bottom above the navigation bar — not the
 * top, which is where a system notification lands and where a reader's eyes are not.
 */
@Composable
fun BoxScope.CoineProToastHost(
    modifier: Modifier = Modifier,
    toaster: CoineProToaster = LocalToaster.current,
) {
    val toast = toaster.current
    val sequence = toaster.sequence

    // Keyed on the sequence rather than the toast, so replacing a message with the same text still
    // restarts the countdown instead of inheriting the elapsed part of the old one.
    LaunchedEffect(sequence) {
        val showing = toast ?: return@LaunchedEffect
        delay(showing.durationMillis ?: showing.tone.defaultDurationMillis())
        // Only clear what we were told to clear. A newer message that arrived while this was
        // waiting has already bumped the sequence and started its own effect.
        if (toaster.sequence == sequence) toaster.dismiss()
    }

    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        // Held rather than read through the null check above: the exit animation outlives the
        // state, and reading `toaster.current` here would draw an empty bar on the way out.
        val shown = remember(sequence) { toast }
        if (shown != null) ToastBar(shown, onDismiss = toaster::dismiss)
    }
}

@Composable
private fun ToastBar(toast: CoineProToast, onDismiss: () -> Unit) {
    val accent = toast.tone.color()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = BAR_MARGIN),
        shape = MaterialTheme.shapes.medium,
        color = CoineProTint.fill(accent, CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProTint.edge(accent)),
        shadowElevation = BAR_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CoineProSpacing.CardHorizontal,
                vertical = BAR_VERTICAL,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        ) {
            Box(
                modifier = Modifier
                    .size(MARK_PLATE)
                    .clip(CircleShape)
                    .background(CoineProTint.fill(accent, CoineProColors.Surface)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(toast.tone.icon()),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(MARK_GLYPH),
                )
            }
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            val label = toast.actionLabel
            val action = toast.onAction
            if (label != null && action != null) {
                val haptics = rememberCoineProHaptics()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            haptics.select()
                            action()
                            onDismiss()
                        }
                        .padding(horizontal = ACTION_HORIZONTAL, vertical = ACTION_VERTICAL),
                )
            }
        }
    }
}

@Composable
private fun ToastTone.color(): Color = when (this) {
    ToastTone.SUCCESS -> CoineProColors.Buy
    ToastTone.FAILURE -> CoineProColors.Sell
    ToastTone.NEUTRAL -> CoineProColors.Gold
}

private fun ToastTone.icon(): Int = when (this) {
    ToastTone.SUCCESS -> CoineProIcons.Success
    ToastTone.FAILURE -> CoineProIcons.Warning
    ToastTone.NEUTRAL -> CoineProIcons.Info
}

/**
 * Two and a half seconds for good news, four for bad.
 *
 * A success confirms something the reader already believes happened, so it only has to be seen. A
 * failure has to be *read*, and four seconds is about the floor for a Persian sentence a reader is
 * not expecting.
 */
private fun ToastTone.defaultDurationMillis(): Long = when (this) {
    ToastTone.SUCCESS -> 2_500
    ToastTone.NEUTRAL -> 3_000
    ToastTone.FAILURE -> 4_000
}

private val BAR_MARGIN = 12.dp
private val BAR_VERTICAL = 10.dp
private val BAR_ELEVATION = 6.dp
private val MARK_PLATE = 26.dp
private val MARK_GLYPH = 15.dp
private val ACTION_HORIZONTAL = 8.dp
private val ACTION_VERTICAL = 4.dp
