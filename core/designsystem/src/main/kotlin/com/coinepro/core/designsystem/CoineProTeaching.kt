package com.coinepro.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The teaching banner: one sentence at the top of a screen saying what the screen is for.
 *
 * ### The problem this solves
 *
 * Most of the people this app is built for have never used a charting terminal. They can read a
 * price and they cannot read a *screen* — a depth ladder, a screener, a heat map and a backtest all
 * look like the same wall of numbers to somebody meeting them for the first time, and none of them
 * says what it is. The usual answers are worse than nothing: a full-screen tour nobody finishes, a
 * coach-mark that covers the content it is describing, or a help centre in the settings that only
 * the people who already understand the app ever open.
 *
 * What actually works is one plain sentence, in place, at the moment of confusion, that can be put
 * away for good. That is all this is.
 *
 * ### The shape of the copy, and why it is two lines and not one
 *
 * Every surface gets a **lead** — what this screen does, in the present tense, as an action the
 * reader performs or a fact the screen reports. Not what it offers, not what it is "powerful" at.
 *
 * Most also get a **pitfall**, and this is the line that earns the banner its place. It is the one
 * thing a first-time reader gets *wrong*, said plainly: that a signal is a report and not advice,
 * that the last candle has not closed yet, that paper trading uses real prices and fake money, that
 * a heat map cell's size is not market cap. A feature list would be marketing. A misunderstanding,
 * named, is the difference between a person who uses a screen and a person who misreads it for a
 * year and blames themselves.
 *
 * Both lines live in `strings.xml`, so the Persian is the source and English is a translation
 * rather than the other way round.
 *
 * ### Dismissal is permanent, which is only safe because it is reversible
 *
 * [CoineProTeachingBanner] has a close control and closing it is forever — see `TeachingStore` for
 * why a banner that comes back is worse than no banner. That is a strong promise, so it is paired
 * with [CoineProTeachingAction]: a «؟» the screen keeps in its header, which brings the sentence
 * back. Dismissing is therefore never destructive, and neither control needs a confirmation.
 *
 * ### Where the dismissals live
 *
 * Not here. This module cannot see `core:datastore` and should not: what it needs is the *state*,
 * not the storage. [TeachingDismissals] is that seam — the host provides one backed by
 * `TeachingStore` through [LocalTeachingDismissals], and everything in this file reads it. Without a
 * host the default is [SessionTeachingDismissals], which remembers for the life of the process, so
 * a preview and a screen in a test harness both behave correctly instead of throwing.
 *
 * ### Cost on a screen that has not opted in
 *
 * Zero. There is no ambient scanner and nothing walks the surface catalogue: a screen that does not
 * call [CoineProTeachingBanner] composes nothing, reads nothing and subscribes to nothing.
 * [LocalTeachingDismissals] is a `staticCompositionLocalOf`, so providing it costs no recomposition
 * scope, and the host collects the store's flow **once** for the whole app rather than once per
 * screen.
 *
 * ### Motion and surface
 *
 * The banner expands and fades in, and collapses on its way out — finite, state-driven, and
 * therefore not the kind of loop `check-motion-policy.sh` is about. Compose's own animations already
 * honour the system animator scale, so with animations turned off the banner simply appears and
 * disappears, which is the correct behaviour rather than a special case. No blur, no gradient, no
 * coloured shadow: it is [CoineProColors.SurfaceElevated] with the same hairline every other surface
 * in this system carries, and it deliberately does not use gold, the analysis blue, or any other
 * colour that already means something here. Teaching is not a status.
 */
@Composable
fun CoineProTeachingBanner(
    surface: TeachingSurface,
    modifier: Modifier = Modifier,
) {
    val dismissals = LocalTeachingDismissals.current
    CoineProTeachingBanner(
        surface = surface,
        // `ready` is false only while the persisted set is still being read off disk, which is a
        // few milliseconds at cold start. Drawing the banner during that window and hiding it once
        // the answer arrives is a flash, and a mechanism whose whole job is not to nag should not
        // open by flickering at someone who dismissed it last week.
        visible = dismissals.ready && surface.key !in dismissals.dismissed,
        onDismiss = { dismissals.dismiss(surface.key) },
        modifier = modifier,
    )
}

/**
 * The banner and the way back to it, as **one line** a screen adds.
 *
 * ### Why this exists beside the banner rather than instead of it
 *
 * Twenty-three screens needed teaching, and the ordinary shape — a banner in the body plus a «؟» in
 * the header's action slot — is two edits per screen into two different places, and a third of
 * these screens have no action slot to put it in. Two dozen bespoke insertions is two dozen chances
 * to get the padding wrong, and the one that was wrong would be the one nobody reviewed.
 *
 * So this owns both halves and its own gutter. A screen writes one line under its header and gets
 * the banner while it is unread and, once it is dismissed, a quiet «این صفحه چیست؟» in its place —
 * which is the same promise [CoineProTeachingAction] makes, kept without needing a header.
 *
 * The restore line is deliberately small, muted and right-aligned rather than a second control with
 * a border: after dismissal the reader has said they do not want this, and the way back should be
 * available without being a thing on the screen. It occupies one text line rather than collapsing
 * to nothing, because a control that vanishes entirely is one a reader cannot find again.
 *
 * A screen that genuinely has a header action slot may still use [CoineProTeachingBanner] plus
 * [CoineProTeachingAction] directly; nothing here forbids it.
 */
@Composable
fun CoineProTeachingStrip(
    surface: TeachingSurface,
    modifier: Modifier = Modifier,
    /** False where the caller's container already supplies the horizontal gutter — a `LazyColumn`
     * with `contentPadding`, or a sheet. Double gutters on a phone are visible. */
    gutter: Boolean = true,
) {
    val dismissals = LocalTeachingDismissals.current
    val showing = dismissals.ready && surface.key !in dismissals.dismissed
    val padded = if (gutter) {
        modifier.padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
    } else {
        modifier.padding(vertical = CoineProSpacing.Half)
    }
    if (showing) {
        CoineProTeachingBanner(
            surface = surface,
            visible = true,
            onDismiss = { dismissals.dismiss(surface.key) },
            modifier = padded,
        )
        return
    }
    // Nothing at all until the disk read lands, for the reason the ordinary overload gives: a strip
    // that appeared and then changed shape a frame later is a flicker on every cold start.
    if (!dismissals.ready) return
    Box(modifier = padded.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = stringResource(R.string.teaching_what_is_this),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier
                .clip(CoineProShapes.small)
                .clickable { dismissals.restore(surface.key) }
                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        )
    }
}

/**
 * The banner with its state passed in, for a preview, a test, or a screen that owns the decision
 * itself.
 *
 * The ordinary overload above is the one screens call.
 */
@Composable
fun CoineProTeachingBanner(
    surface: TeachingSurface,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // Expanding rather than sliding over, for the reason [CoineProOfflineBar] gives: the banner
        // takes its own row and pushes the screen down, so it never covers the first line of what
        // the reader came to read.
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shape = MaterialTheme.shapes.medium
        val haptics = rememberCoineProHaptics()
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.SurfaceElevated, shape)
                .border(1.dp, CoineProColors.BorderSubtle, shape)
                .padding(start = CoineProSpacing.OneHalf, end = CoineProSpacing.Half)
                .padding(vertical = CoineProSpacing.One),
            // Top, not centre: the pitfall line makes this two lines tall, and a centred glyph
            // beside two lines of Persian floats in the middle of nothing.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Icon(
                painter = painterResource(CoineProIcons.Info),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
                modifier = Modifier.padding(top = GLYPH_NUDGE).size(GLYPH),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TWO),
            ) {
                Text(
                    text = stringResource(surface.lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                )
                surface.pitfall?.let { pitfall ->
                    Text(
                        text = stringResource(pitfall),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .pressScale(interaction, CoineProPress.CONTROL)
                    .size(TOUCH)
                    .clip(CircleShape)
                    .clickable(interaction, null) {
                        haptics.select()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoineProIcons.Close),
                    contentDescription = stringResource(R.string.teaching_dismiss),
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(GLYPH),
                )
            }
        }
    }
}

/**
 * The «؟» that brings a dismissed banner back.
 *
 * A screen that shows a banner should keep one of these in its header, permanently. It is what makes
 * the dismiss control safe to press: the explanation is put away, not deleted, and getting it back
 * is one tap in the place the reader is already looking rather than a trip into settings.
 *
 * It is a [CoineProHeaderAction], so it is the same 34dp square with the same hairline, the same
 * 48dp target and the same press as the refresh button it sits beside on every list screen. That is
 * the point: a reader does not have to learn a new control, and the header does not reflow — the
 * «؟» is present whether or not the banner is showing, and while it is showing, tapping it is a
 * harmless no-op rather than a control that vanished.
 */
@Composable
fun CoineProTeachingAction(surface: TeachingSurface) {
    val dismissals = LocalTeachingDismissals.current
    CoineProHeaderAction(
        icon = CoineProIcons.Help,
        label = stringResource(R.string.teaching_what_is_this),
        onClick = { dismissals.restore(surface.key) },
    )
}

/** The information glyph and the close glyph, which are the same size so the row reads as balanced. */
private val GLYPH = 16.dp

/** The close control's target. Below the 48dp ideal because the banner is a two-line strip. */
private val TOUCH = 32.dp

/** Optical alignment of the leading glyph with the cap height of the first line. */
private val GLYPH_NUDGE = 2.dp

/** Between the lead and the pitfall. Tighter than any spacing token: they are one paragraph. */
private val TWO = 2.dp

/**
 * Every screen that teaches, its key, and the two sentences it teaches with.
 *
 * ### The key is the persistence contract
 *
 * [key] is what `TeachingStore` writes to disk, so **renaming one un-dismisses that banner for
 * everybody who had already put it away**. That is a bug when it happens by accident and a feature
 * when it is meant: if a screen's explanation turns out to have been wrong, bumping its key to
 * `markets.2` re-teaches the screen exactly once, for everybody, which no other mechanism here can
 * do. Rename deliberately or not at all.
 *
 * The shape a key may take is `TeachingStore.usable`'s rule, restated on this side of the boundary
 * because neither module can see the other: lowercase letters, digits, underscore, hyphen and dot,
 * and short. `CoineProTeachingTest` pins it.
 *
 * ### Why an enum
 *
 * A screen names the surface it is on and cannot misspell it, the set is exhaustive and reviewable
 * in one place, and adding a screen is one line beside its two strings rather than a key invented at
 * a call site.
 */
enum class TeachingSurface(
    /** The token this surface's dismissal is stored under. See the class note before changing one. */
    val key: String,
    /** What this screen does. One sentence, present tense. */
    @get:StringRes val lead: Int,
    /**
     * The one thing a first-time reader gets wrong.
     *
     * Nullable because a screen simple enough to have no common misreading should not have a second
     * line invented for it — but almost every screen here does have one, and a surface added without
     * a pitfall should be a deliberate answer to "what do people get wrong?", not a blank left
     * because writing it was hard.
     */
    @get:StringRes val pitfall: Int? = null,
) {
    HOME("home", R.string.teaching_home_lead, R.string.teaching_home_pitfall),
    MARKETS("markets", R.string.teaching_markets_lead, R.string.teaching_markets_pitfall),
    CHART("chart", R.string.teaching_chart_lead, R.string.teaching_chart_pitfall),
    SIGNALS("signals", R.string.teaching_signals_lead, R.string.teaching_signals_pitfall),
    AI("ai", R.string.teaching_ai_lead, R.string.teaching_ai_pitfall),
    TOOLS("tools", R.string.teaching_tools_lead, R.string.teaching_tools_pitfall),
    NEWS("news", R.string.teaching_news_lead, R.string.teaching_news_pitfall),
    CALENDAR("calendar", R.string.teaching_calendar_lead, R.string.teaching_calendar_pitfall),
    SCREENER("screener", R.string.teaching_screener_lead, R.string.teaching_screener_pitfall),
    HEATMAP("heatmap", R.string.teaching_heatmap_lead, R.string.teaching_heatmap_pitfall),
    WATCHLIST("watchlist", R.string.teaching_watchlist_lead, R.string.teaching_watchlist_pitfall),
    ALERTS("alerts", R.string.teaching_alerts_lead, R.string.teaching_alerts_pitfall),
    JOURNAL("journal", R.string.teaching_journal_lead, R.string.teaching_journal_pitfall),
    PAPER_TRADE("paper_trade", R.string.teaching_paper_trade_lead, R.string.teaching_paper_trade_pitfall),
    BACKTEST("backtest", R.string.teaching_backtest_lead, R.string.teaching_backtest_pitfall),
    SCRIPT("script", R.string.teaching_script_lead, R.string.teaching_script_pitfall),
    PORTFOLIO("portfolio", R.string.teaching_portfolio_lead, R.string.teaching_portfolio_pitfall),
    CONNECTIONS("connections", R.string.teaching_connections_lead, R.string.teaching_connections_pitfall),
    MEMBERSHIP("membership", R.string.teaching_membership_lead, R.string.teaching_membership_pitfall),
    ACTIVITY("activity", R.string.teaching_activity_lead, R.string.teaching_activity_pitfall),
    ACADEMY("academy", R.string.teaching_academy_lead, R.string.teaching_academy_pitfall),
    DOM("dom", R.string.teaching_dom_lead, R.string.teaching_dom_pitfall),
    SEARCH("search", R.string.teaching_search_lead, R.string.teaching_search_pitfall),
}

/**
 * Which teaching banners are put away, and how to change that.
 *
 * The seam between this module and whatever is storing the answer. [dismissed] must be backed by
 * snapshot state — a `mutableStateOf`, or a `collectAsState` the host holds — because the banner
 * reads it during composition and has to recompose when it changes.
 */
@Stable
interface TeachingDismissals {

    /** The keys whose banner has been dismissed. Snapshot-backed; see the interface note. */
    val dismissed: Set<String>

    /**
     * Whether [dismissed] is the real answer yet.
     *
     * False only while a persisted set is still being read. Everything in this file draws nothing
     * until it is true, which is what keeps a dismissed banner from flashing on at cold start. An
     * implementation that has its answer immediately leaves this alone.
     */
    val ready: Boolean get() = true

    /** Puts this screen's banner away, permanently. */
    fun dismiss(key: String)

    /** Brings it back. */
    fun restore(key: String)
}

/**
 * The fallback when no host has provided one: dismissals last for the life of the process.
 *
 * A preview, a screenshot render and a screen hosted outside the app all get a banner that behaves
 * — it closes when you close it — without any of them needing a `DataStore`. What it deliberately
 * does not do is pretend to persist: the app must provide the `TeachingStore`-backed implementation,
 * and if it ever stops doing so the symptom is a banner that returns after a relaunch, which is
 * visible, rather than dismissals that vanish silently.
 *
 * Process-wide rather than remembered per composition, so navigating away from a screen and back
 * does not resurrect a banner the reader just closed.
 */
object SessionTeachingDismissals : TeachingDismissals {

    private var keys by mutableStateOf(emptySet<String>())

    override val dismissed: Set<String> get() = keys

    override fun dismiss(key: String) {
        keys = keys + key
    }

    override fun restore(key: String) {
        keys = keys - key
    }

    /** For tests, and for a preview that wants to start from a clean slate. */
    fun forgetAll() {
        keys = emptySet()
    }
}

/**
 * Where the banner finds the dismissals.
 *
 * Static, so a screen that never reads it pays nothing and providing it creates no recomposition
 * scope. The default is [SessionTeachingDismissals]; the app provides a persisted one at the root of
 * the composition.
 */
val LocalTeachingDismissals = staticCompositionLocalOf<TeachingDismissals> { SessionTeachingDismissals }
