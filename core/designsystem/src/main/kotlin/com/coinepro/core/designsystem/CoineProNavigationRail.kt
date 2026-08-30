package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One destination on the rail.
 *
 * [key] is whatever the caller uses for identity — in the app that is the route, and comparing
 * routes is what tells the rail which item is current. The rail never parses it and never shows it.
 *
 * The two glyphs are a pair rather than one glyph and a tint, for the reason the bottom bar already
 * gives: weight marks the selection here, because the gold belongs to the page's primary action and
 * a gold tab would put a second gold object on every screen.
 */
@Immutable
data class CoineProRailItem(
    val key: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

/**
 * How wide the rail is, in its two forms.
 *
 * Public because a shell that draws its own scrim, or a screenshot that wants to assert the content
 * width, needs the same number the rail used — and a second copy of it typed at the call site is
 * the thing that goes stale.
 */
object CoineProRailWidth {
    /** Glyph over label. The form a tablet in either orientation gets. */
    val ICON: Dp = 80.dp

    /**
     * Glyph beside label, on windows wide enough to spare the width.
     *
     * This number is load-bearing beyond the rail:
     * [CoineProWindowClass.LABELLED_RAIL_WIDTH_DP] is
     * [CoineProWindowClass.EXPANDED_WIDTH_DP] **plus this**, so that a window wide enough to label
     * the rail is still wide enough behind it for a list and a detail. Widening this without moving
     * that threshold costs a twelve-inch tablet its second pane, and `WindowClassTest` asserts the
     * arithmetic rather than either number so the mistake fails a test instead of shipping.
     */
    val LABELLED: Dp = 240.dp
}

/**
 * The navigation rail: the bottom bar's replacement on anything wider than a phone.
 *
 * ### Why it is not the bottom bar turned sideways
 *
 * A bottom bar is five equal cells across a strip, and the thing that makes it legible is that the
 * five are close together — a reader's eye takes in the whole row and finds the bright one. Stand
 * that up and the same five items are sixty points apart down the tall edge of a tablet, where
 * "one of these is slightly brighter" stops being answerable at a glance. So the selected
 * destination here is a **plate**: a filled, rounded block behind the glyph and its label.
 *
 * That is a change of value, not of colour, so it does not spend the screen's one gold object —
 * which is the rule `CoineProSurfaces` is built on and the reason the bottom bar turns Material's
 * indicator pill off. What the bottom bar could not afford was the *height* a plate costs; a rail
 * has height and nothing else to spend it on.
 *
 * ### Where it sits, and why that is automatic
 *
 * The rail is drawn first inside the shell's `Row`, so it takes the **start** edge — the left in
 * English and the right in Persian, which is the default here. Nothing in this file names left or
 * right, and that is deliberate: a rail pinned to a physical edge is the single most visible way to
 * get a right-to-left layout wrong, and it is wrong in a way that looks like a deliberate choice
 * rather than a bug. Its divider is a sibling in the same `Row` for the same reason, so it is
 * always on the side facing the content.
 *
 * ### Why it scrolls
 *
 * Five destinations never fill a tablet, but the rail is also drawn on a large phone turned
 * sideways, where the *height* is about 411dp and the brand mark is on it as well. A rail whose
 * last destination is off the bottom of the glass is a destination the reader cannot reach at all,
 * and the scroll costs nothing on every device where it never engages.
 *
 * ### Why this lives in `core:designsystem` and not `core:navigation`
 *
 * `core:navigation` is a plain Kotlin-facing module with no Compose dependency, on purpose — it is
 * read by code that has no UI. So the rail takes [items] rather than the destination enum, and the
 * shell maps one to the other, which is the same arrangement `CoineProBottomBar` already uses for
 * its glyphs.
 */
@Composable
fun CoineProNavigationRail(
    items: List<CoineProRailItem>,
    /** Which item is current, by [CoineProRailItem.key]. Nothing matching selects nothing. */
    selectedKey: String?,
    onSelect: (CoineProRailItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether labels sit beside the glyphs rather than beneath them.
     *
     * Defaults to what the window says. Passed explicitly only by a preview or a render that wants
     * the other form on a window that would not have chosen it.
     */
    labelled: Boolean = coineProWindowClass().prefersLabelledRail,
    /**
     * What sits above the destinations — the brand mark, in the app.
     *
     * A slot rather than a fixed mark because `core:designsystem` must not decide that every rail
     * everywhere carries a logo, and because the screenshot renders want the rail without one.
     */
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val width = if (labelled) CoineProRailWidth.LABELLED else CoineProRailWidth.ICON
    val description = stringResource(R.string.a11y_navigation_rail)
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                // The stage, not a raised surface — the same decision the bottom bar took. A rail
                // on its own panel would put a second sheet on every screen, and on a tablet that
                // panel is a metre tall.
                .background(CoineProColors.Stage)
                .semantics { contentDescription = description }
                .padding(vertical = CoineProSpacing.One),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header?.let { mark ->
                mark()
                Spacer(Modifier.height(CoineProSpacing.Two))
            }
            // The destinations scroll; the mark above them does not.
            //
            // The scroll is on an inner column that takes the leftover height rather than on the
            // outer one, so the mark stays put while the list moves under it — and so that a rail
            // taller than its content still fills, which `verticalScroll` on its own does not do.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEach { item ->
                    RailItem(
                        item = item,
                        selected = item.key == selectedKey,
                        labelled = labelled,
                        onClick = { onSelect(item) },
                    )
                    Spacer(Modifier.height(RAIL_ITEM_GAP))
                }
            }
        }
        // The edge between the rail and the page. A sibling in this Row, so it is on the content
        // side in both directions without anything here naming a side.
        VerticalDivider(color = CoineProColors.Border)
    }
}

/**
 * One destination, in the two forms the rail has.
 *
 * The plate is the selection and the glyph's weight seconds it, so the state survives both a
 * reader who does not distinguish the two greys and a screenshot compared at low resolution. An
 * unselected item draws no background at all rather than a fainter one: a rail of five plates with
 * one slightly brighter is the bottom bar's problem again, on a bigger canvas.
 */
@Composable
private fun RailItem(
    item: CoineProRailItem,
    selected: Boolean,
    labelled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted
    val plate = if (selected) CoineProColors.SurfaceElevated else Color.Transparent
    val glyph = @Composable {
        Icon(
            painter = painterResource(if (selected) item.selectedIcon else item.icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(RAIL_GLYPH),
        )
    }
    val text = @Composable {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            // Semibold on the selected one, because the plate is a value difference and value is
            // the first thing a low-brightness screen outdoors loses.
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val shape = MaterialTheme.shapes.medium
    val target = Modifier
        .fillMaxWidth()
        .padding(horizontal = CoineProSpacing.One)
        .clip(shape)
        .background(plate)
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            // The label is the name of the destination and the glyph is decorative — see the null
            // description on the icon — so the row's own text is what a screen reader announces.
            onClick = onClick,
        )

    if (labelled) {
        Row(
            modifier = target.padding(
                horizontal = CoineProSpacing.OneHalf,
                vertical = CoineProSpacing.OneHalf,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            glyph()
            text()
        }
    } else {
        Column(
            modifier = target.padding(vertical = CoineProSpacing.One),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            glyph()
            text()
        }
    }
}

/**
 * The brand mark's berth at the top of the rail.
 *
 * A fixed box rather than the mark itself, so that a rail with a header and one without put their
 * first destination at two heights that differ by a whole number of steps rather than by whatever
 * the artwork happened to measure.
 */
@Composable
fun CoineProRailHeader(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(RAIL_HEADER_HEIGHT),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** Between two destinations. Four points: they are one group and must not read as five cards. */
private val RAIL_ITEM_GAP = 4.dp

/** Same as the bottom bar's, so a reader moving between a phone and a tablet meets one glyph size. */
private val RAIL_GLYPH = 26.dp

/** Tall enough for the lockup, and the same on both rail widths. */
private val RAIL_HEADER_HEIGHT = 56.dp
