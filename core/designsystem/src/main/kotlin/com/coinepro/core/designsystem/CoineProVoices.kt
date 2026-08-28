package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The app speaks two visual languages, and which one a screen speaks is decided by what the screen
 * *is* rather than by taste.
 *
 * **«طلایی» — the gold voice — is for a screen that shows one thing.** A chart, a signal, a lesson,
 * a portfolio. It gives that thing a heading, a large figure and room around it, and closes the
 * heading with a fading gold rule. The point is that the reader's attention has one place to land.
 *
 * **«ترمینال» — the terminal voice — is for a screen that is a list.** Markets, signals, activity,
 * news. It puts the same three things in the same three places on every row and gets out of the
 * way. The point is that a reader scanning a column never has to re-learn where to look.
 *
 * The owner chose both off a design canvas and set the rule between them, so the split is settled.
 * What this file adds is that neither voice has to be re-implemented per screen: a screen declares
 * which one it speaks by which components it reaches for.
 */

/* ---------------------------------------------------------------- the gold voice */

/**
 * A content screen's heading: an eyebrow, a title, and the gold rule that closes them.
 *
 * [eyebrow] is the category and [title] is the subject — «سیگنال» over «BTCUSDT خرید», rather than
 * one line trying to be both. Two short lines read faster than one long one and they survive a
 * long Persian title without wrapping into the content below.
 */
@Composable
fun CoineProPageHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                eyebrow?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Gold,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke(this)
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.One))
    }
}

/**
 * The one figure a content screen exists to show, at the size that says so.
 *
 * [caption] rides beside it rather than under it, because the pair is read as one phrase — "two
 * thousand six hundred, up one and a half percent" — and stacking them makes the reader's eye
 * travel twice for one sentence.
 */
@Composable
fun CoineProHeroFigure(
    figure: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColour: Color = CoineProColors.TextMuted,
) {
    Row(
        modifier = modifier.padding(horizontal = CoineProSpacing.Gutter),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        LtrDirection {
            Text(
                text = figure,
                style = CoineProTextStyles.Balance,
                color = CoineProColors.TextPrimary,
            )
        }
        caption?.let {
            LtrDirection {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = captionColour,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * A row of small readings under a hero figure.
 *
 * Three is the number that works: two looks like a comparison the screen is not making, and four
 * are too narrow for a Persian label at this type size.
 */
@Composable
fun CoineProReadingRow(
    readings: List<CoineProReading>,
    modifier: Modifier = Modifier,
) {
    if (readings.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        readings.forEach { reading ->
            CoineProCard(
                modifier = Modifier.weight(1f),
                shape = CoineProShapes.small,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(CoineProSpacing.OneHalf),
            ) {
                Text(
                    text = reading.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reading.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = reading.tone ?: CoineProColors.TextPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One cell of [CoineProReadingRow]. A null [tone] is the ordinary text colour. */
data class CoineProReading(val label: String, val value: String, val tone: Color? = null)

/* ------------------------------------------------------------ the terminal voice */

/**
 * A list screen's header: a title, and at most two icon actions beside it.
 *
 * Deliberately smaller than [CoineProPageHeading] and with no rule under it. A list's first row is
 * the content, and a heading that competed with it would push the thing the reader came for below
 * the fold.
 */
@Composable
fun CoineProListHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        actions?.invoke(this)
    }
}

/** A 34dp square icon action for [CoineProListHeader]. */
@Composable
fun CoineProHeaderAction(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // Drawn at 34 and touchable at 48. This is the refresh button at the head of every
            // list in the app, so it is the target a reader misses most often.
            .minimumInteractiveComponentSize()
            .size(34.dp)
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The tab strip a terminal screen filters with.
 *
 * A filled tray with the selected tab raised out of it, rather than an underline: at this width
 * five Persian labels leave no room for an indicator that has to sit under the text, and a raised
 * block reads as "you are here" without one.
 */
@Composable
fun <T> CoineProSegmentTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = modifier
            .padding(horizontal = CoineProSpacing.Two)
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CoineProShapes.extraSmall)
                    .background(if (active) CoineProColors.SurfaceElevated else CoineProColors.Surface)
                    // Only a change is worth a tick. Pressing the tab you are already on has
                    // changed nothing, and a buzz that says otherwise teaches the reader to
                    // distrust the ones that do mean something.
                    .clickable {
                        if (!active) haptics.select()
                        onSelect(value)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

/**
 * The line naming a list's columns.
 *
 * Three words, once, above rows that never change shape. Without it the middle of a dense row is a
 * decoration; with it, the reader knows what they are looking at.
 */
@Composable
fun CoineProColumnHeadings(
    start: String,
    middle: String?,
    end: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = MaterialTheme.typography.labelSmall
        Text(start, style = style, color = CoineProColors.TextDisabled, fontWeight = FontWeight.Normal)
        middle?.let { Text(it, style = style, color = CoineProColors.TextDisabled, fontWeight = FontWeight.Normal) }
        Text(end, style = style, color = CoineProColors.TextDisabled, fontWeight = FontWeight.Normal)
    }
}

/**
 * One dense row: an identity on the reading edge, something in the middle, figures at the far end.
 *
 * [leadingWidth] is fixed rather than wrapped, and that is the whole reason this is a component: a
 * ticker that pushed the middle column sideways would make the column unreadable, and every list in
 * the app would find its own slightly different answer to that.
 */
@Composable
fun CoineProDenseRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    leadingWidth: androidx.compose.ui.unit.Dp = 96.dp,
    titleLtr: Boolean = false,
    middle: @Composable (() -> Unit)? = null,
    figure: String? = null,
    note: String? = null,
    noteTone: Color = CoineProColors.TextMuted,
    /** Draws the note as a filled pill, the way a change percentage reads in a market list. */
    notePill: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = CoineProSpacing.Two, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.width(leadingWidth)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.let {
                    if (titleLtr) it.copy(textDirection = TextDirection.Ltr) else it
                },
                color = CoineProColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextDisabled,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { middle?.invoke() }
        Column(horizontalAlignment = Alignment.End) {
            figure?.let {
                LtrDirection {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextPrimary,
                    )
                }
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = noteTone,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .let { base ->
                            if (notePill) {
                                base
                                    .clip(CoineProShapes.extraSmall)
                                    .background(noteTone.copy(alpha = NOTE_PILL_ALPHA))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            } else {
                                base
                            }
                        },
                )
            }
        }
    }
}

/** The hairline between two dense rows. Inset, so it divides the content rather than the screen. */
@Composable
fun CoineProRowDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier.padding(horizontal = CoineProSpacing.Two),
        thickness = 1.dp,
        color = CoineProColors.BorderSubtle,
    )
}

/** How solid a note pill's fill is behind its own colour. */
private const val NOTE_PILL_ALPHA = 0.12f

/** Kept so a caller can build a column of rows without importing the layout package. */
@Composable
fun CoineProListSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}
