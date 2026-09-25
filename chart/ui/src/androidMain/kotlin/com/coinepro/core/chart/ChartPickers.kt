package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.designsystem.proseDigits
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart-type list.
 *
 * Eighteen rows in three groups, and the grouping is the whole point. Five of the eighteen have no
 * clock on the x axis at all — a Renko brick appears when price moves, not when time passes — and
 * that is the single fact a reader needs before choosing one. It was previously printed as a
 * subtitle under each of the five, which said the same sentence five times; said once, over a
 * heading, it is a distinction rather than a repetition.
 *
 * The third group is the derived types — volume candles, footprint, TPO. They are on the clock like
 * candles are, so they do not belong under the price-driven heading, but they are not a plain
 * rendering of the feed either: each one reads volume per price row and draws a second dimension
 * inside the bar. Separating them keeps the first group honest as "the bars, drawn".
 *
 * [hasVolume] hides footprint and TPO entirely. The MT5 forex feed reports no volume, and a
 * footprint over fabricated zeros is not a degraded chart, it is a confident lie — so the type is
 * not offered rather than offered and empty.
 *
 * Every row carries a «؟». That is not decoration and it is not optional: this list offers Kagi and
 * Point & Figure beside candles, and a professional audience still contains people who have never
 * used them — the whole reason they are worth offering is that somebody can find out what they are
 * without leaving the app to search.
 *
 * [onHelp] receives the entry id. A screen that has no help catalogue loaded passes null and the
 * «؟» disappears rather than opening an empty sheet.
 */
@Composable
fun ChartTypePicker(
    selected: ChartType,
    onSelect: (ChartType) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
    hasVolume: Boolean = true,
) {
    val english = inEnglish()
    val derivedTypes = setOf(ChartType.VOLUME_CANDLES, ChartType.FOOTPRINT, ChartType.TPO)
    val offered = ChartCatalog.chartTypesFor(hasVolume)
    val timed = offered.filter { it.type.isTimeBased && it.type !in derivedTypes }
    val untimed = offered.filterNot { it.type.isTimeBased }
    val derived = offered.filter { it.type in derivedTypes }
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Surface),
        contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
    ) {
        item(key = "h-timed") { GroupHeader(if (english) "Time-based" else "زمان‌محور") }
        items(timed, key = { it.type }) { option ->
            PickerRow(
                label = option.label(english),
                icon = option.icon.drawableRes(),
                selected = option.type == selected,
                accent = null,
                onClick = { onSelect(option.type) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
        item(key = "h-untimed") {
            GroupHeader(
                if (english) {
                    "Price-driven — each bar is built by price moving, not by time passing"
                } else {
                    "قیمت‌محور — هر میله با حرکت قیمت ساخته می‌شود، نه با گذر زمان"
                },
            )
        }
        items(untimed, key = { it.type }) { option ->
            PickerRow(
                label = option.label(english),
                icon = option.icon.drawableRes(),
                selected = option.type == selected,
                accent = null,
                onClick = { onSelect(option.type) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
        if (derived.isNotEmpty()) {
            item(key = "h-derived") {
                GroupHeader(
                    if (english) {
                        "Derived — the same bars, drawn to say something else"
                    } else {
                        "مشتق از داده — همان میله‌ها، با هندسه‌ای که چیز دیگری را می‌گوید"
                    },
                )
            }
            items(derived, key = { it.type }) { option ->
                PickerRow(
                    label = option.label(english),
                    icon = option.icon.drawableRes(),
                    selected = option.type == selected,
                    accent = null,
                    onClick = { onSelect(option.type) },
                    onHelp = onHelp?.let { { it(option.helpId) } },
                )
            }
        }
    }
}

/**
 * The indicator list.
 *
 * Same chrome as the drawing tools — a search field over a filter row over the list — because these
 * two sheets sit one tap apart on the same toolbar and a reader should not have to learn each of
 * them separately. The tools get a grid because a drawing tool has a picture of itself; indicators
 * get a list because they do not, and a grid of twenty identical wave glyphs would be a puzzle.
 *
 * The filter is by pane, which is the useful distinction rather than an alphabet: a reader adding a
 * third overlay to the price is making a different decision from one opening a fourth pane below
 * it, and the list should say which they are about to do.
 *
 * Given room — TradingView's 840 px dialog — the families become a column beside the list rather
 * than a row over it (DIALOGS-14): nine chips in a 528 px dialog put two families past the edge,
 * and a column never runs out of glass. A desktop dialog too narrow for the column wraps its chips.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IndicatorPicker(
    active: Set<String>,
    onToggle: (IndicatorOption) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
    /**
     * Whether the loaded feed reports volume.
     *
     * Fourteen of the eighty-three studies read a volume column, and the MT5 forex feed has none.
     * Offering them there would put a flat line of zeros on the chart, which reads as «this market
     * had no participants» rather than as «this feed does not carry that». Hiding them is the
     * honest answer, and it is why this parameter exists rather than a disabled row.
     */
    hasVolume: Boolean = true,
    /**
     * The lookbacks the reader has changed, and how to change them. Both null means no stepper —
     * which is what a picker used as a read-only list wants.
     */
    periods: Map<String, Int> = emptyMap(),
    onSetPeriod: ((String, Int) -> Unit)? = null,
    /** The starred ids, and the way to star one. Null on the star hides it and the two chips. */
    favourites: List<String> = emptyList(),
    onToggleFavourite: ((String) -> Unit)? = null,
    /** The most recently switched-on ids, newest first. See `IndicatorFavouritesStore`. */
    recent: List<String> = emptyList(),
    /**
     * Whether the search field takes the keyboard as the sheet opens.
     *
     * **False everywhere in this app** as of 4.74.0, and the recording is why. The reference does
     * raise the keyboard, and on the owner's phone the result was that the sheet opened with its
     * bottom half covered: the catalogue was under the keyboard, what was left above it was the two
     * rows of «my scripts», and the reader's conclusion was that the indicators were not all being
     * shown. A reader who came to *browse* eighty-three studies has not asked to type, and a reader
     * who has taps the field. Nothing is lost and the whole list is visible.
     */
    autoFocusSearch: Boolean = false,
    /**
     * One more section at the foot of the list, inside the same scroll.
     *
     * A slot rather than a second composable under this one, because this list is a `LazyColumn`:
     * anything drawn beside it gets its own scroll and its own share of a fixed height, which is
     * what put «my scripts» above the catalogue and the catalogue under the keyboard. As an item it
     * is simply the last thing the reader scrolls to. See `ScriptPickerSection`.
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    var chip by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val english = inEnglish()
    val dense = coineProWindowClass().showsTwoPanes

    // Typing overrides the chips rather than intersecting with them, exactly as in the tool rail.
    // Somebody who types «مکدی» wants MACD, not "MACD if it happens to be in the pane I last
    // tapped" — and an empty result the reader cannot explain is the worst outcome of two filters
    // combining quietly.
    val searching = query.isNotBlank()
    val offered = ChartCatalog.indicatorsFor(hasVolume)
    val byId = remember(offered) { offered.associateBy { it.id } }
    val shown = when {
        searching -> ChartCatalog.matchingIndicators(query).filter { it in offered }
        chip == CHIP_FAVOURITES -> favourites.mapNotNull(byId::get)
        chip == CHIP_RECENT -> recent.mapNotNull(byId::get)
        chip != null -> offered.filter { ChartCatalog.categoryOf(it.id).name == chip }
        else -> offered
    }
    // The reference's filters: Favourites and Recent first, then the families. The two personal
    // ones exist only where something feeds them — a preview has no store.
    val personal = if (onToggleFavourite != null) {
        listOf(
            CoineProChip(id = CHIP_FAVOURITES, label = if (english) "Favorites" else "برگزیده‌ها", count = favourites.size),
            CoineProChip(id = CHIP_RECENT, label = if (english) "Recent" else "اخیر", count = recent.size),
        )
    } else {
        emptyList()
    }
    val families = IndicatorCategory.entries.map { candidate ->
        CoineProChip(
            id = candidate.name,
            label = candidate.label(english),
            count = offered.count { ChartCatalog.categoryOf(it.id) == candidate },
        )
    }
    val allLabel = if (english) "All" else "همه"

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        CoineProSheetSearch(
            value = query,
            onValueChange = { query = it },
            placeholder = if (english) "Search indicators" else "جست‌وجوی اندیکاتور",
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            autoFocus = autoFocusSearch,
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val column = maxWidth >= CATEGORY_COLUMN_MIN_DIALOG
            if (column) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CategoryColumn(
                        personal = personal,
                        families = families,
                        allLabel = allLabel,
                        allCount = offered.size,
                        selectedId = chip,
                        // A family chosen while searching clears the search, so the choice is
                        // what the list then shows.
                        onSelect = { id ->
                            chip = id
                            query = ""
                        },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        IndicatorList(
                            shown = shown,
                            grouped = !searching && chip == null,
                            chip = chip,
                            english = english,
                            dense = dense,
                            active = active,
                            onToggle = onToggle,
                            onHelp = onHelp,
                            periods = periods,
                            onSetPeriod = onSetPeriod,
                            favourites = favourites,
                            onToggleFavourite = onToggleFavourite,
                            trailing = trailing,
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!searching) {
                        if (dense) {
                            // A pointer cannot swipe a row sideways, so on a dialog too narrow for the
                            // column every family wraps onto a second line rather than hiding.
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
                                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                            ) {
                                CoineProToggleChip(label = allLabel, selected = chip == null, onClick = { chip = null }, compact = true, neutral = true)
                                (personal + families).forEach { option ->
                                    CoineProToggleChip(
                                        label = option.label,
                                        selected = option.id == chip,
                                        onClick = { chip = option.id },
                                        count = option.count,
                                        compact = true,
                                        neutral = true,
                                    )
                                }
                            }
                        } else {
                            // Neutral, like every filter inside a chart dialog (DIALOGS-26): the
                            // page's gold stays for the one action worth pressing.
                            CoineProChipRow(
                                options = personal + families,
                                selectedId = chip,
                                onSelect = { id -> chip = id },
                                allLabel = allLabel,
                                neutral = true,
                            )
                        }
                        Spacer(Modifier.height(CoineProSpacing.One))
                    }
                    IndicatorList(
                        shown = shown,
                        grouped = !searching && chip == null,
                        chip = chip,
                        english = english,
                        dense = dense,
                        active = active,
                        onToggle = onToggle,
                        onHelp = onHelp,
                        periods = periods,
                        onSetPeriod = onSetPeriod,
                        favourites = favourites,
                        onToggleFavourite = onToggleFavourite,
                        trailing = trailing,
                    )
                }
            }
        }
    }
}

/**
 * The families as TradingView's left column: one 36 dp row each, the count at the far end, a raised
 * plate under the one in force. The personal pair sits above a hairline, as the reference's
 * «Personal» group sits above «Built-in».
 */
@Composable
private fun CategoryColumn(
    personal: List<CoineProChip>,
    families: List<CoineProChip>,
    allLabel: String,
    allCount: Int,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CATEGORY_COLUMN_WIDTH)
            .verticalScroll(rememberScrollState())
            .padding(start = CoineProSpacing.OneHalf, end = CoineProSpacing.One, bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryRow(allLabel, allCount, selectedId == null) { onSelect(null) }
        personal.forEach { option ->
            CategoryRow(option.label, option.count, option.id == selectedId) { onSelect(option.id) }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = CoineProSpacing.Half, horizontal = CoineProSpacing.One),
            color = CoineProColors.BorderSubtle,
        )
        families.forEach { option ->
            CategoryRow(option.label, option.count, option.id == selectedId) { onSelect(option.id) }
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CATEGORY_ROW_HEIGHT)
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.SurfaceRaised else Color.Transparent)
            .coineProControl(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.proseDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/** The list itself, under the chips or beside the column. */
@Composable
private fun IndicatorList(
    shown: List<IndicatorOption>,
    grouped: Boolean,
    chip: String?,
    english: Boolean,
    dense: Boolean,
    active: Set<String>,
    onToggle: (IndicatorOption) -> Unit,
    onHelp: ((String) -> Unit)?,
    periods: Map<String, Int>,
    onSetPeriod: ((String, Int) -> Unit)?,
    favourites: List<String>,
    onToggleFavourite: ((String) -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    if (shown.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CoineProSheetEmpty(
                when (chip) {
                    CHIP_FAVOURITES -> if (english) {
                        "No favorites yet. The star beside any row brings it here."
                    } else {
                        "هنوز اندیکاتوری ستاره نزده‌اید. ستاره‌ی کنار هر ردیف آن را اینجا می‌آورد."
                    }
                    CHIP_RECENT -> if (english) "You have not switched an indicator on yet." else "هنوز اندیکاتوری روشن نکرده‌اید."
                    else -> if (english) "No indicator by that name." else "اندیکاتوری با این نام پیدا نشد."
                },
            )
            // The reader's own studies are still offered. A filter that matched nothing in the
            // catalogue is exactly the moment somebody is looking for the script they wrote.
            trailing?.let { section ->
                Column(modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter)) { section() }
            }
        }
        return
    }

    // A heading only when the list actually spans both panes. Printing «روی قیمت» over a list
    // the reader just filtered *to* «روی قیمت» is a line of noise.
    val entries = remember(shown, grouped) { indicatorListEntries(shown, grouped) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
    ) {
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is IndicatorListEntry.Heading -> GroupHeader(entry.pane.label(english))
                is IndicatorListEntry.Study -> {
                    val option = entry.option
                    PickerRow(
                        label = option.label(english),
                        icon = option.icon.drawableRes(),
                        selected = option.id in active,
                        accent = Color(option.colour),
                        onClick = { onToggle(option) },
                        // A handful of the eighty-three have no entry in the shipped catalogue yet.
                        // They get no «؟» rather than one that opens nothing.
                        onHelp = option.helpId?.let { id -> onHelp?.let { { it(id) } } },
                        starred = option.id in favourites,
                        onToggleStar = onToggleFavourite?.let { toggle -> { toggle(option.id) } },
                        // Only on a switched-on indicator, and only where there is one lookback to
                        // change. A stepper on eighty-three rows at once would be a wall of numbers on a
                        // list whose job is choosing; a reader sets the length of the thing they
                        // have already decided to use.
                        period = ChartCatalog.periodOf(option.id)
                            ?.takeIf { option.id in active && onSetPeriod != null }
                            ?.let { bounds ->
                                PeriodControl(
                                    value = periods[option.id] ?: bounds.default,
                                    bounds = bounds,
                                    onChange = { next -> onSetPeriod?.invoke(option.id, next) },
                                )
                            },
                        dense = dense,
                    )
                }
            }
        }
        trailing?.let { section ->
            item(key = "trailing") {
                Column(modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter)) { section() }
            }
        }
    }
}

/** One line of the indicator list: a pane's heading or a study. */
internal sealed interface IndicatorListEntry {
    val key: String

    data class Heading(val pane: IndicatorPane) : IndicatorListEntry {
        override val key: String get() = "h-${pane.name}"
    }

    data class Study(val option: IndicatorOption) : IndicatorListEntry {
        override val key: String get() = option.id
    }
}

/**
 * The list's lines, with each pane's heading once (DIALOGS-01).
 *
 * The catalogue interleaves the panes — the second pack's price studies follow the first pack's
 * oscillators — and a heading emitted on every change of pane put «در پنل جدا» into the list twice
 * under one key, which a `LazyColumn` answers by throwing mid-scroll. Grouped, the studies are
 * gathered by pane first (catalogue order kept inside each); either way an id appears once, so a
 * favourites store holding a duplicate cannot crash the list either.
 */
internal fun indicatorListEntries(shown: List<IndicatorOption>, grouped: Boolean): List<IndicatorListEntry> {
    val unique = shown.distinctBy { it.id }
    if (!grouped) return unique.map { IndicatorListEntry.Study(it) }
    return IndicatorPane.entries.flatMap { pane ->
        val inPane = unique.filter { it.pane == pane }
        if (inPane.isEmpty()) emptyList() else listOf(IndicatorListEntry.Heading(pane)) + inPane.map { IndicatorListEntry.Study(it) }
    }
}

/** What the heading calls each pane. */
private fun IndicatorPane.label(english: Boolean): String = when (this) {
    IndicatorPane.PRICE -> if (english) "On price" else "روی قیمت"
    IndicatorPane.SEPARATE -> if (english) "Separate pane" else "در پنل جدا"
    IndicatorPane.STRUCTURE -> if (english) "Market structure" else "ساختار بازار"
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            top = CoineProSpacing.OneHalf,
            bottom = CoineProSpacing.Half,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRow(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    accent: Color?,
    onClick: () -> Unit,
    onHelp: (() -> Unit)?,
    period: PeriodControl? = null,
    starred: Boolean = false,
    onToggleStar: (() -> Unit)? = null,
    /**
     * A pointer's list (DIALOGS-13): TradingView's 32–40 px pitch rather than the thumb's 48, so a
     * dialog shows fourteen studies instead of nine. Defaults to the window's answer.
     */
    dense: Boolean = coineProWindowClass().showsTwoPanes,
) {
    val english = inEnglish()
    // On a phone the stepper takes a second line under the name rather than squeezing it: beside
    // the check, the star and the «؟» it left «میانگین متحرک ساده» two lines tall (DIALOGS-17).
    val stepperBelow = period != null && !dense
    // A selected row is a filled, hairlined card rather than a tick alone at the far end. On a
    // fifty-row list the reader scans down the left of the labels, and a mark parked on the other
    // side of the screen is the last thing they see. The whole row changing state is the first.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The plate's edge on the sheet's gutter, the search field's edge above it (MOBILE-02).
            .padding(
                horizontal = CoineProSpacing.Gutter,
                vertical = if (dense) 0.dp else ROW_GAP,
            )
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = accent?.copy(alpha = SELECTED_BORDER_ALPHA) ?: CoineProColors.Accent,
                        shape = CoineProShapes.small,
                    )
                } else {
                    Modifier
                },
            )
            // A long press is the reference's way to the indicator's own page; the «؟» stays
            // for the reader who does not know that.
            .combinedClickable(onClick = onClick, onLongClick = onHelp)
            .padding(
                horizontal = CoineProSpacing.OneHalf,
                vertical = if (dense) DENSE_ROW_PAD else CoineProSpacing.OneHalf,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) CoineProSpacing.One else CoineProSpacing.OneHalf),
        ) {
            // TradingView's own glyph. On an indicator it is tinted with that indicator's line colour,
            // so the icon and the swatch are one thing rather than two: the row says "this draws a
            // channel, in this colour" in a single mark.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(if (dense) 18.dp else 20.dp),
                tint = when {
                    accent != null && selected -> accent
                    accent != null -> accent.copy(alpha = INACTIVE_ICON_ALPHA)
                    selected -> CoineProColors.Accent
                    else -> CoineProColors.TextMuted
                },
            )
            Text(
                text = label,
                style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!stepperBelow) period?.let { PeriodStepper(it, accent ?: CoineProColors.Accent) }
            if (selected) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_check_circle),
                    contentDescription = if (english) "Selected" else "انتخاب‌شده",
                    modifier = Modifier.size(if (dense) 16.dp else 18.dp),
                    tint = accent ?: CoineProColors.Accent,
                )
            }
            onToggleStar?.let { toggle ->
                Box(
                    modifier = Modifier
                        .size(if (dense) DENSE_CONTROL else 28.dp)
                        .clip(CircleShape)
                        .coineProControl(onClick = toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (starred) DesignR.drawable.icon_filled_star else DesignR.drawable.icon_star),
                        contentDescription = when {
                            english && starred -> "Remove from favorites"
                            english -> "Add to favorites"
                            starred -> "برداشتن از برگزیده‌ها"
                            else -> "افزودن به برگزیده‌ها"
                        },
                        modifier = Modifier.size(if (dense) 16.dp else 18.dp),
                        tint = if (starred) CoineProColors.Gold else CoineProColors.TextMuted,
                    )
                }
            }
            if (onHelp != null) {
                if (dense) HelpDot(onClick = onHelp, size = DENSE_CONTROL, glyph = 16.dp) else HelpDot(onClick = onHelp)
            }
        }
        if (stepperBelow) {
            Row(
                modifier = Modifier.padding(start = 20.dp + CoineProSpacing.OneHalf, top = CoineProSpacing.Half),
            ) {
                PeriodStepper(period!!, accent ?: CoineProColors.Accent)
            }
        }
    }
}

/** One indicator's lookback, and how to move it. See [PeriodStepper]. */
private data class PeriodControl(
    val value: Int,
    val bounds: IndicatorPeriod,
    val onChange: (Int) -> Unit,
)

/**
 * Minus, the number, plus.
 *
 * ### The step is not one
 *
 * Nobody moves an average from 20 to 21. The lengths people use are 9, 14, 20, 21, 50, 100, 200 —
 * so the step scales with the value: single bars up to 20, fives to 50, tens to 100, twenties
 * beyond. Reaching 200 from 20 is then eleven taps rather than a hundred and eighty, and every
 * value on the way is one somebody actually uses.
 *
 * ### The digits are Latin
 *
 * A period is a market figure — it is drawn onto the chart's own legend as «EMA 50», beside
 * prices — and this control has to read the same as the label it produces. The app's rule, and one
 * of the few places in a Persian-first interface where Latin numerals are the correct answer.
 */
/** The lookback stepper on its own, for the indicator settings sheet's Inputs tab. */
@Composable
fun IndicatorPeriodStepper(value: Int, bounds: IndicatorPeriod, accent: Color, onChange: (Int) -> Unit) {
    PeriodStepper(PeriodControl(value = value, bounds = bounds, onChange = onChange), accent)
}

/**
 * A stepper for any of an indicator's knobs — a bar count stepped by one, a multiplier stepped by
 * its own [IndicatorParameter.step] and shown to the decimals that step needs. Same buttons, same
 * Latin figures as the length's stepper, for the same reason: the number is drawn on the legend.
 */
@Composable
fun IndicatorParameterStepper(value: Double, spec: IndicatorParameter, accent: Color, onChange: (Double) -> Unit) {
    val decimals = if (spec.integer) 0 else decimalsOf(spec.step)
    fun shown(number: Double): String = if (decimals == 0) number.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.${decimals}f", number)
    fun moved(up: Boolean): Double {
        val next = if (up) value + spec.step else value - spec.step
        val rounded = if (decimals == 0) next.roundToInt().toDouble() else Math.round(next * 10.0.pow(decimals)) / 10.0.pow(decimals)
        return rounded.coerceIn(spec.min, spec.max)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton(
            glyph = DesignR.drawable.icon_caret_left,
            enabled = value > spec.min,
            accent = accent,
            onClick = { onChange(moved(up = false)) },
        )
        Text(
            text = shown(value),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.widthIn(min = PERIOD_WIDTH).semantics { contentDescription = "indicator-param-${spec.key}" },
            textAlign = TextAlign.Center,
        )
        StepperButton(
            glyph = DesignR.drawable.icon_caret_right,
            enabled = value < spec.max,
            accent = accent,
            onClick = { onChange(moved(up = true)) },
        )
    }
}

/** How many decimals a step needs: 0.1 → one, 0.01 → two, 0.5 → one. */
private fun decimalsOf(step: Double): Int {
    var decimals = 0
    var scaled = step
    while (decimals < 4 && kotlin.math.abs(scaled - Math.round(scaled)) > 1e-9) {
        scaled *= 10.0
        decimals++
    }
    return decimals
}

@Composable
private fun PeriodStepper(control: PeriodControl, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepperButton(
            glyph = DesignR.drawable.icon_caret_left,
            enabled = control.value > control.bounds.min,
            accent = accent,
            // Left is *down* in both directions, because this is a number line and not a
            // reading order: the minus sits where the smaller values are, mirrored with the
            // layout by the drawable itself.
            onClick = { control.onChange(step(control.value, up = false, bounds = control.bounds)) },
        )
        Text(
            text = control.value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.widthIn(min = PERIOD_WIDTH),
            textAlign = TextAlign.Center,
        )
        StepperButton(
            glyph = DesignR.drawable.icon_caret_right,
            enabled = control.value < control.bounds.max,
            accent = accent,
            onClick = { control.onChange(step(control.value, up = true, bounds = control.bounds)) },
        )
    }
}

@Composable
private fun StepperButton(
    @DrawableRes glyph: Int,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(STEPPER_TAP)
            .clip(CircleShape)
            .coineProControl(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (enabled) accent else CoineProColors.TextDisabled,
        )
    }
}

/** The next value up or down, on the coarsening ladder described in [PeriodStepper]. */
private fun step(value: Int, up: Boolean, bounds: IndicatorPeriod): Int {
    val size = when {
        value < 20 -> 1
        value < 50 -> 5
        value < 100 -> 10
        else -> 20
    }
    // Going down from a boundary uses the *smaller* side's step, so the ladder is symmetric:
    // 50 steps down to 45 rather than to 40, and back up to 50.
    val downSize = when {
        value <= 20 -> 1
        value <= 50 -> 5
        value <= 100 -> 10
        else -> 20
    }
    val next = if (up) value + size else value - downSize
    return next.coerceIn(bounds.min, bounds.max)
}

/**
 * The «؟».
 *
 * TradingView's circled question mark rather than a Persian «؟» set in text. The typed character
 * was wrong twice over: at this size it reads as punctuation belonging to the label rather than as
 * a control, and it inherits the text font, so it sat at a different weight and baseline from every
 * other mark in the row.
 */
@Composable
internal fun HelpDot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The touch target. Smaller on a tool tile, where it shares a 88 dp square with the mark. */
    size: Dp = 28.dp,
    /** The glyph inside it. */
    glyph: Dp = 18.dp,
    // Brighter than the muted text it sat in before, which made it look disabled — it is a
    // control, and on a list of eighteen chart types it is the one that answers the question
    // the reader actually has. A tile that has inverted to near-black passes its own ink.
    tint: Color = CoineProColors.TextSecondary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .coineProControl(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_help_circle),
            contentDescription = if (inEnglish()) "Help" else "راهنما",
            modifier = Modifier.size(glyph),
            tint = tint,
        )
    }
}

/** The two personal chips' ids, kept apart from the family names they sit beside. */
private const val CHIP_FAVOURITES = "__favourites"
private const val CHIP_RECENT = "__recent"

/** TradingView's category column: 200 px wide, one 36 px row per family. */
private val CATEGORY_COLUMN_WIDTH = 200.dp
private val CATEGORY_ROW_HEIGHT = 36.dp

/** The narrowest picker that takes the column: 200 for it and a list still wider than a phone's. */
private val CATEGORY_COLUMN_MIN_DIALOG = 640.dp

/** A dense row's vertical padding: 6 + a 22 sp line + 6 = TradingView's 34 px row. */
private val DENSE_ROW_PAD = 6.dp

/** The star and «؟» boxes on a dense row: 24, with a 16 dp glyph. */
private val DENSE_CONTROL = 24.dp

/** An unselected indicator keeps its colour, faintly, so the list still colour-codes itself. */
private const val INACTIVE_ICON_ALPHA = 0.45f

/**
 * A selected row's hairline, at a fraction of the indicator's own colour.
 *
 * Full strength would put a saturated rectangle around every active indicator and turn a list into
 * a set of competing boxes; this is the same 0.34 the design tokens use for a tinted surface.
 */
private const val SELECTED_BORDER_ALPHA = 0.34f

/** Between rows, so a selected card has air around it rather than touching its neighbours. */
private val ROW_GAP = 3.dp

/** Wide enough for three digits, so the row does not shuffle between 9 and 200. */
private val PERIOD_WIDTH = 26.dp

/** The stepper's tap target. Small for a control, but it sits inside a 48dp row. */
private val STEPPER_TAP = 30.dp
