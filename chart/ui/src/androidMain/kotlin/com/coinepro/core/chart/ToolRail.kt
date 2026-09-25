package com.coinepro.core.chart

import com.coinepro.core.designsystem.CoineProLazyRow
import com.coinepro.core.designsystem.CoineProMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rowMotion

/**
 * The drawing tools.
 *
 * Ninety-odd of them in twelve groups, and that scale is what decides the whole layout. Three
 * arrangements were available and only one survives contact with a phone:
 *
 * * The web terminal's **vertical rail** becomes a four-screen scroll of 24dp targets here. It works
 *   on a desktop because a mouse is precise and the rail sits permanently beside the chart; neither
 *   is true on a phone.
 * * An **accordion** of twelve groups hides eleven group names behind a tap and turns finding a tool
 *   into a two-step search — open the right drawer, then look inside it — while leaving the reader
 *   wondering what is collapsed.
 * * A **chip row over a grid** keeps every group name visible, costs one tap, and carries a search
 *   field for the reader who knows the name. That is this.
 *
 * Three things were added as the list roughly doubled, and each of them exists because that grid
 * stopped being scannable at about sixty:
 *
 * * A **favourites row** the reader fills themselves. Nobody uses ninety tools; everybody uses
 *   six, and which six is personal enough that no default is right. The star acts on whatever is
 *   armed, so pinning is one tap after using a tool rather than a mode of its own.
 * * A **pinned group heading**, so a reader four screens down still knows whether they are looking
 *   at Gann or at Elliott. `LazyVerticalGrid` has no sticky header of its own, so the heading is a
 *   strip above the grid that follows whichever row is at the top — the same effect, and it does
 *   not fight the grid's spans.
 * * The **volume group disappears entirely** on a feed that reports no volume. Greying it out would
 *   be honest too, but three permanently dead cells in a rail this long is three cells a reader
 *   scans past forever; the group's own «؟» is where the explanation belongs.
 *
 * The search earns its place: at this many tools, typing «فیب» beats any amount of scanning, and
 * it is the only path that works for somebody who knows a tool by name but not by glyph.
 */
@Composable
fun ToolRail(
    selected: String?,
    onSelect: (DrawingTool) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
    /**
     * Whether the feed reports volume at all.
     *
     * False on the MT5 forex side, and the whole volume group is dropped rather than offered as
     * three tools that would draw nothing.
     *
     * The default is **false**, and that is a deliberate reversal. It defaulted to true and neither
     * call site passed anything, so the KDoc above promised a gate that had never once closed: on a
     * forex feed a reader could arm «VWAP لنگرانداخته», tap, and watch the renderer return without
     * drawing — a tool that fails silently is worse than a tool that is not offered. A default of
     * false makes the promise true for a caller that says nothing, and a caller that knows its feed
     * has volume says so.
     */
    hasVolume: Boolean = false,
    /** Tool ids pinned to the top of the rail. See [DrawingState.favourites]. */
    favourites: Set<String> = emptySet(),
    onToggleFavourite: ((DrawingTool) -> Unit)? = null,
    /** The tool last armed in each group; first in its group and marked. See [DrawingState.lastUsed]. */
    lastUsed: Map<ToolGroup, String> = emptyMap(),
    magnet: MagnetMode = MagnetMode.OFF,
    /** Advance the magnet one step: off, weak, strong. Null hides the action. */
    onCycleMagnet: (() -> Unit)? = null,
    keepDrawing: Boolean = false,
    onKeepDrawing: ((Boolean) -> Unit)? = null,
    lockedAll: Boolean = false,
    onLockAll: ((Boolean) -> Unit)? = null,
    hidden: Set<DrawingLayer> = emptySet(),
    onHide: ((DrawingLayer, Boolean) -> Unit)? = null,
    onHideAll: ((Boolean) -> Unit)? = null,
    /** Clear every drawing — TradingView's «Remove all objects». Null hides the tile. */
    onRemoveAll: (() -> Unit)? = null,
    /** The magnet set outright, for the «⋮» beside its tile. Null leaves the tile cycling only. */
    onSetMagnet: ((MagnetMode) -> Unit)? = null,
    /** One zoom step on the chart under the sheet. Null hides the pair. */
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
) {
    var group by remember { mutableStateOf<ToolGroup?>(null) }
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    val catalogue = remember(hasVolume) {
        if (hasVolume) DrawingTools.ALL else DrawingTools.ALL.filterNot { it.group == ToolGroup.VOLUME }
    }
    val offered = remember(catalogue) { catalogue.map { it.id }.toSet() }

    // Typing overrides the chips rather than intersecting with them. Somebody who types «کمان»
    // wants the arc tool — not "the arc tool if it happens to be in the group I last tapped". An
    // empty result the reader cannot explain is the worst outcome of combining two filters quietly.
    val searching = query.isNotBlank()
    val tools = when {
        searching -> DrawingTools.matching(query).filter { it.id in offered }
        group != null -> DrawingTools.inGroup(group!!).filter { it.id in offered }
        else -> catalogue
    }
    val grouped = !searching && group == null
    val rows = remember(tools, grouped, lastUsed) { railRows(tools, grouped, lastUsed) }

    // The modes, as TradingView's phone lays them out: a grid of tiles at the head of the
    // «Tools» tab rather than a row of glyphs above the search. See [modeTiles].
    val modes = modeTiles(
        catalogue = catalogue,
        selected = selected,
        onSelect = onSelect,
        magnet = magnet,
        onCycleMagnet = onCycleMagnet,
        keepDrawing = keepDrawing,
        onKeepDrawing = onKeepDrawing,
        lockedAll = lockedAll,
        onLockAll = onLockAll,
        hidden = hidden,
        onHide = onHide,
        onHideAll = onHideAll,
        onRemoveAll = onRemoveAll,
        onSetMagnet = onSetMagnet,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
    )
    // The mode tiles are the grid's first item when the list is unfiltered, so the pinned heading
    // reads one row behind the grid's own index — and names nothing while the tiles are at the top.
    val leading = if (grouped && modes.isNotEmpty()) 1 else 0
    val heading by remember(rows, leading) {
        derivedStateOf { rows.getOrNull(gridState.firstVisibleItemIndex - leading)?.group }
    }

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        CoineProSheetSearch(
            value = query,
            onValueChange = { query = it },
            placeholder = tr("جست‌وجوی ابزار", "Search tools"),
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        if (onToggleFavourite != null || favourites.isNotEmpty()) {
            FavouritesRow(
                favourites = catalogue.filter { it.id in favourites },
                armed = selected?.let { id -> catalogue.firstOrNull { it.id == id } },
                onSelect = onSelect,
                onToggleFavourite = onToggleFavourite,
            )
        }
        if (!searching) {
            RailTabs(
                groups = DrawingTools.GROUPS.filter { it != ToolGroup.VOLUME || hasVolume },
                selected = group,
                onSelect = { group = it },
            )
            Spacer(Modifier.height(CoineProSpacing.One))
        }

        if (tools.isEmpty()) {
            CoineProSheetEmpty(tr("ابزاری با این نام پیدا نشد.", "No tool by that name."))
            return@Column
        }

        // The pinned heading. Only when the list actually spans groups: printing "فیبوناچی" over a
        // grid the reader just filtered *to* فیبوناچی is a line of noise.
        if (grouped) {
            heading?.let { current ->
                Text(
                    text = current.label(inEnglish()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CoineProColors.SurfaceElevated)
                        .padding(
                            horizontal = CoineProSpacing.Gutter,
                            vertical = CoineProSpacing.Half,
                        ),
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(TOOLS_ACROSS),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = CoineProSpacing.OneHalf,
                vertical = CoineProSpacing.One,
            ),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            // The mode tiles lead the unfiltered list, three across and full-span, exactly where
            // the phone app's «Tools» tab puts Measure, Eraser and Keep drawing.
            if (grouped && modes.isNotEmpty()) {
                item(key = "__modes", span = { GridItemSpan(TOOLS_ACROSS) }) {
                    ModeTileGrid(tiles = modes)
                }
            }
            for (row in rows) {
                when (row) {
                    is RailRow.Heading -> item(
                        key = "h-${row.group.name}",
                        span = { GridItemSpan(TOOLS_ACROSS) },
                    ) {
                        Text(
                            text = row.group.label(inEnglish()),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            modifier = Modifier.padding(
                                top = CoineProSpacing.One,
                                bottom = CoineProSpacing.Half,
                            ),
                        )
                    }
                    is RailRow.Cell -> item(key = row.tool.id) {
                        ToolCell(
                            tool = row.tool,
                            selected = row.tool.id == selected,
                            favourite = row.tool.id in favourites,
                            promoted = lastUsed[row.tool.group] == row.tool.id,
                            onClick = { onSelect(row.tool) },
                            onHelp = row.tool.helpId?.let { id -> onHelp?.let { { it(id) } } },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One entry in the flat list the grid walks.
 *
 * Flattened before the grid rather than decided inside it, for one reason: the pinned heading has to
 * name the group of whatever row is at the top of the viewport, and that means asking "what is at
 * index n" — a question a `for` loop emitting items into a lazy scope cannot answer.
 */
internal sealed interface RailRow {
    val group: ToolGroup

    data class Heading(override val group: ToolGroup) : RailRow

    data class Cell(val tool: DrawingTool) : RailRow {
        override val group: ToolGroup get() = tool.group
    }
}

internal fun railRows(tools: List<DrawingTool>, grouped: Boolean, lastUsed: Map<ToolGroup, String> = emptyMap()): List<RailRow> {
    val rows = ArrayList<RailRow>(tools.size + DrawingTools.GROUPS.size)
    var last: ToolGroup? = null
    // The group's last-used tool leads its group: a flyout that opens on the tool it last armed.
    // The catalogue's order is kept otherwise — the tool is lifted to the head of its group's run,
    // not the groups re-sorted — so a search result or a filtered rail reads as it did.
    val used = if (lastUsed.isEmpty()) emptyMap() else tools.filter { lastUsed[it.group] == it.id }.associateBy { it.group }
    val promoted = if (used.isEmpty()) tools else buildList(tools.size) {
        val led = HashSet<ToolGroup>()
        var run: ToolGroup? = null
        for (tool in tools) {
            if (tool.group != run) {
                run = tool.group
                if (led.add(tool.group)) used[tool.group]?.let(::add)
            }
            if (used[tool.group] !== tool) add(tool)
        }
    }
    for (tool in promoted) {
        if (grouped && tool.group != last) {
            last = tool.group
            rows += RailRow.Heading(tool.group)
        }
        rows += RailRow.Cell(tool)
    }
    return rows
}

/**
 * The modes, as tiles.
 *
 * Magnet, keep-drawing, lock-all, the eraser, the ruler, the layer switches and «remove all» are
 * not tools — arming most of them draws nothing — and they used to sit in a row of eighteen-point
 * glyphs above the search. The phone app's Drawings sheet puts them where the owner circled them:
 * a grid of 72 pt tiles at the head of the «Tools» tab, three across, the first row on grey
 * plates and the rest outlined, the one that is *on* inverted, and a «⋮» beside the tiles that
 * carry a menu. This builds that list; [ModeTileGrid] lays it out.
 *
 * A tile whose caller offers no handler is left out rather than dimmed, so the grid is exactly as
 * long as the modes this screen actually has.
 */
@Composable
private fun modeTiles(
    catalogue: List<DrawingTool>,
    selected: String?,
    onSelect: (DrawingTool) -> Unit,
    magnet: MagnetMode,
    onCycleMagnet: (() -> Unit)?,
    keepDrawing: Boolean,
    onKeepDrawing: ((Boolean) -> Unit)?,
    lockedAll: Boolean,
    onLockAll: ((Boolean) -> Unit)?,
    hidden: Set<DrawingLayer>,
    onHide: ((DrawingLayer, Boolean) -> Unit)?,
    onHideAll: ((Boolean) -> Unit)?,
    onRemoveAll: (() -> Unit)?,
    onSetMagnet: ((MagnetMode) -> Unit)?,
    onZoomIn: (() -> Unit)?,
    onZoomOut: (() -> Unit)?,
): List<ModeTile> {
    val tiles = ArrayList<ModeTile>(MODE_TILE_COUNT)
    // The ruler and the eraser are tools in the catalogue and modes on the phone app's sheet;
    // here they are both, so a reader finds them where either app would put them.
    catalogue.firstOrNull { it.id == MEASURE_TOOL }?.let { tool ->
        tiles += ModeTile(tool.icon.drawableRes(), tr("اندازه‌گیری", "Measure"), on = selected == tool.id) { onSelect(tool) }
    }
    catalogue.firstOrNull { it.id == ERASER_TOOL }?.let { tool ->
        tiles += ModeTile(tool.icon.drawableRes(), tr("پاک‌کن", "Eraser"), on = selected == tool.id) { onSelect(tool) }
    }
    onKeepDrawing?.let { set ->
        tiles += ModeTile(DesignR.drawable.tv_tool_keepdrawing, tr("ماندن روی ابزار", "Stay in drawing mode"), on = keepDrawing) {
            set(!keepDrawing)
        }
    }
    onHide?.let { set ->
        val drawingsHidden = DrawingLayer.DRAWINGS in hidden
        tiles += ModeTile(
            icon = if (drawingsHidden) DesignR.drawable.icon_eye_slash else DesignR.drawable.icon_eye,
            label = if (drawingsHidden) tr("نمایش رسم‌ها", "Show drawings") else tr("پنهان‌کردن رسم‌ها", "Hide drawings"),
            on = drawingsHidden,
            menu = buildList {
                add(
                    layerEntry(
                        tr("نمایش اندیکاتورها", "Show indicators"),
                        tr("پنهان‌کردن اندیکاتورها", "Hide indicators"),
                        DrawingLayer.INDICATORS,
                        hidden,
                        set,
                    ),
                )
                add(
                    layerEntry(
                        tr("نمایش موقعیت‌ها", "Show positions"),
                        tr("پنهان‌کردن موقعیت‌ها", "Hide positions"),
                        DrawingLayer.POSITIONS,
                        hidden,
                        set,
                    ),
                )
                onHideAll?.let { all ->
                    val allHidden = hidden.size == DrawingLayer.entries.size
                    add(ModeMenuEntry(if (allHidden) tr("نمایش همه", "Show all") else tr("پنهان‌کردن همه", "Hide all")) { all(!allHidden) })
                }
            },
        ) { set(DrawingLayer.DRAWINGS, !drawingsHidden) }
    }
    onLockAll?.let { set ->
        tiles += ModeTile(
            icon = if (lockedAll) DesignR.drawable.tv_lock else DesignR.drawable.tv_unlock,
            label = if (lockedAll) tr("باز کردن قفل همه", "Unlock all drawings") else tr("قفل همه‌ی رسم‌ها", "Lock all drawings"),
            on = lockedAll,
        ) { set(!lockedAll) }
    }
    onCycleMagnet?.let { cycle ->
        tiles += ModeTile(
            icon = DesignR.drawable.tv_magnet,
            label = when (magnet) {
                MagnetMode.OFF -> tr("آهنربا خاموش", "Magnet off")
                MagnetMode.WEAK -> tr("آهنربای ضعیف", "Weak magnet")
                MagnetMode.STRONG -> tr("آهنربای قوی", "Strong magnet")
            },
            on = magnet != MagnetMode.OFF,
            menu = onSetMagnet?.let { set ->
                listOf(
                    ModeMenuEntry(tr("خاموش", "Off")) { set(MagnetMode.OFF) },
                    ModeMenuEntry(tr("آهنربای ضعیف", "Weak magnet")) { set(MagnetMode.WEAK) },
                    ModeMenuEntry(tr("آهنربای قوی", "Strong magnet")) { set(MagnetMode.STRONG) },
                )
            }.orEmpty(),
            onClick = cycle,
        )
    }
    onRemoveAll?.let { clear ->
        tiles += ModeTile(DesignR.drawable.tv_trash2, tr("حذف همه‌ی ترسیم‌ها", "Remove all drawings"), on = false, onClick = clear)
    }
    onZoomIn?.let { zoom ->
        tiles += ModeTile(DesignR.drawable.tv_zoom_in, tr("بزرگ‌نمایی", "Zoom in"), on = false, onClick = zoom)
    }
    onZoomOut?.let { zoom ->
        tiles += ModeTile(DesignR.drawable.tv_zoom_out, tr("کوچک‌نمایی", "Zoom out"), on = false, onClick = zoom)
    }
    return tiles
}

/** One layer's row in the «⋮» menu. */
private fun layerEntry(
    show: String,
    hide: String,
    layer: DrawingLayer,
    hidden: Set<DrawingLayer>,
    onHide: (DrawingLayer, Boolean) -> Unit,
): ModeMenuEntry {
    val isHidden = layer in hidden
    return ModeMenuEntry(if (isHidden) show else hide) { onHide(layer, !isHidden) }
}

/**
 * The rail's own words, in the reader's language (MOBILE-07).
 *
 * Pairs rather than resource ids because `:chart-ui` has no resource table — its sources also build
 * into the browser, which reads only the feature modules' `res/` — and a Persian literal an English
 * reader saw on every tile is the defect this closes.
 */
@Composable
private fun tr(fa: String, en: String): String = if (inEnglish()) en else fa

/** One mode tile: a glyph, a label, whether it is in force, what a tap does, and its side menu. */
private class ModeTile(
    val icon: Int,
    val label: String,
    val on: Boolean,
    val menu: List<ModeMenuEntry> = emptyList(),
    val onClick: () -> Unit,
)

private class ModeMenuEntry(val label: String, val act: () -> Unit)

/**
 * The mode tiles, three across with an 8 pt gutter, a trailing short row keeping the tile width.
 * The first row sits on grey plates and the rest are outlined — the phone app's own arrangement.
 */
@Composable
private fun ModeTileGrid(tiles: List<ModeTile>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(MODE_TILE_GAP),
    ) {
        tiles.chunked(TOOLS_ACROSS).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(MODE_TILE_GAP)) {
                row.forEach { tile ->
                    Box(modifier = Modifier.weight(1f)) { ModeTileCell(tile = tile, plate = rowIndex == 0) }
                }
                repeat(TOOLS_ACROSS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One 72 pt tile. Inverted — near-black with the stage's ink — while its mode is on, which is how
 * the phone app shows «Keep drawing» in force; a «⋮» column behind a hairline where the tile has
 * a menu, opening the menu in place.
 */
@Composable
private fun ModeTileCell(tile: ModeTile, plate: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    val ink = if (tile.on) CoineProColors.Stage else CoineProColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MODE_TILE_HEIGHT)
            .clip(CoineProShapes.large)
            .background(
                when {
                    tile.on -> CoineProColors.TextPrimary
                    plate -> CoineProColors.SurfaceElevated
                    else -> CoineProColors.Surface
                },
            )
            .then(
                if (plate || tile.on) Modifier else Modifier.border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.large),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .coineProControl(onClick = tile.onClick)
                .padding(horizontal = CoineProSpacing.Half),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = ink,
            )
            Spacer(Modifier.height(CoineProSpacing.Half))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tile.menu.isNotEmpty()) {
            VerticalDivider(
                color = if (tile.on) CoineProColors.Stage.copy(alpha = 0.3f) else CoineProColors.BorderSubtle,
                thickness = 1.dp,
                modifier = Modifier.fillMaxHeight().padding(vertical = CoineProSpacing.One),
            )
            Box(
                modifier = Modifier
                    .width(MODE_MENU_WIDTH)
                    .fillMaxHeight()
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.tv_more_horizontal),
                    contentDescription = tr("گزینه‌های بیشتر", "More options"),
                    modifier = Modifier.size(18.dp).rotate(MENU_GLYPH_TURN),
                    tint = ink,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    tile.menu.forEach { entry ->
                        CoineProMenuItem(
                            text = entry.label,
                            onClick = {
                                menuOpen = false
                                entry.act()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The group tabs, as the phone app sets them: bold text, the chosen one on a grey pill and the
 * rest in the muted ink with no edge at all. «همه» leads, for the unfiltered list.
 */
@Composable
private fun RailTabs(groups: List<ToolGroup>, selected: ToolGroup?, onSelect: (ToolGroup?) -> Unit) {
    CoineProLazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        item(key = "__all") {
            RailTab(label = tr("همه", "All"), selected = selected == null) { onSelect(null) }
        }
        items(groups.size, key = { groups[it].name }) { index ->
            val candidate = groups[index]
            RailTab(label = candidate.label(inEnglish()), selected = candidate == selected) { onSelect(candidate) }
        }
    }
}

@Composable
private fun RailTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(TAB_HEIGHT)
            // The chosen tab is a pill on the raised rung — the reference's — not a rounded square.
            .clip(CoineProPillShape)
            .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent)
            .coineProControl(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // A tab, not a heading (MOBILE-13): the label size of every other tab in the app, and
            // the chosen one a step heavier rather than a headline.
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            maxLines = 1,
        )
    }
}

/** The catalogue ids the Measure and Eraser tiles arm. */
private const val MEASURE_TOOL = "ruler"
private const val ERASER_TOOL = "eraser"

/** The mode tiles share the tool tiles' 88 with 8 between, a 40 pt «⋮» column; 40 pt tabs. */
private val MODE_TILE_HEIGHT = 88.dp
private val MODE_TILE_GAP = 8.dp
private val MODE_MENU_WIDTH = 40.dp
private val TAB_HEIGHT = 40.dp
private const val MODE_TILE_COUNT = 9

/** The horizontal «…» glyph stood on end, since the icon set has no vertical one. */
private const val MENU_GLYPH_TURN = 90f

/**
 * The reader's own shortlist, above everything else.
 *
 * The star acts on the **armed** tool rather than opening a picker, because the moment a reader
 * knows a tool is worth pinning is the moment they have just used it. Long-pressing a pinned tool
 * takes it back out, which is the only other thing this row has to do.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouritesRow(
    favourites: List<DrawingTool>,
    armed: DrawingTool?,
    onSelect: (DrawingTool) -> Unit,
    onToggleFavourite: ((DrawingTool) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Half, end = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (favourites.isEmpty()) {
            Text(
                text = tr("ابزار انتخاب‌شده را با ستاره اینجا سنجاق کنید.", "Star the selected tool to pin it here."),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.weight(1f).padding(horizontal = CoineProSpacing.One),
            )
        } else {
            CoineProLazyRow(modifier = Modifier.weight(1f)) {
                items(favourites.size, key = { "fav-" + favourites[it].id }) { index ->
                    val tool = favourites[index]
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CoineProShapes.small)
                            .combinedClickable(
                                onClick = { onSelect(tool) },
                                onLongClick = onToggleFavourite?.let { { it(tool) } },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(tool.icon.drawableRes()),
                            contentDescription = tool.label(inEnglish()),
                            modifier = Modifier.size(20.dp),
                            tint = CoineProColors.TextSecondary,
                        )
                    }
                }
            }
        }
        onToggleFavourite?.let { toggle ->
            val pinned = armed != null && favourites.any { it.id == armed.id }
            RailAction(
                icon = if (pinned) DesignR.drawable.icon_filled_star else DesignR.drawable.icon_star,
                label = if (pinned) tr("برداشتن از برگزیده‌ها", "Remove from favourites") else tr("افزودن به برگزیده‌ها", "Add to favourites"),
                tint = if (pinned) CoineProColors.Gold else null,
                enabled = armed != null,
            ) { armed?.let(toggle) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCell(
    tool: DrawingTool,
    selected: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onHelp: (() -> Unit)?,
    /** The group's last-used tool: first in its group and marked, so the reader's own habit leads. */
    promoted: Boolean = false,
) {
    Box(
        modifier = Modifier
            // A fixed height, so a two-line name does not make its cell taller than the three
            // beside it. Rows in a grid size to their tallest cell, and the result was a ragged
            // wall with holes in it.
            .height(CELL_HEIGHT)
            // TradingView's tool tiles: a grey plate with no edge, and the armed one inverted —
            // near-black with white ink — rather than outlined. Measured off the phone app's
            // Drawings sheet: 16 dp corners, the plate one step up from the sheet.
            .clip(CoineProShapes.large)
            .background(if (selected) CoineProColors.TextPrimary else CoineProColors.SurfaceElevated)
            .combinedClickable(onClick = onClick, onLongClick = onHelp),
    ) {
    // The «؟», in the corner of the tile, and it is back because it went missing.
    //
    // «در کنار ابزارها ۹۲ مورد هر کدوم یدونه (؟) آموزشی داشتند که الان نیست، ولی اندیکاتورها سر
    // جاشه.» Exactly right: `IndicatorPicker` draws a [HelpDot] on every row and this grid drew
    // none — the help was reachable only by holding a tile down, which is a gesture nobody
    // discovers on a control they have never used. The long press still works; the dot is what
    // says the help is there at all. Every tool with an entry gets one, which today is ninety-two
    // of them.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CoineProSpacing.Half),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tool.icon.drawableRes()),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = when {
                selected -> CoineProColors.Stage
                // A pinned tool is marked in the grid as well as listed at the top, so a reader
                // scrolling past one can see it is already on their shortlist and does not pin it
                // twice looking for the row to change.
                favourite -> CoineProColors.Gold
                else -> CoineProColors.TextPrimary
            },
        )
        Spacer(Modifier.height(CoineProSpacing.Half))
        Text(
            text = tool.label(inEnglish()),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CoineProColors.Stage else CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // Two lines and then an ellipsis. "گسترش زمانی فیبوناچی" does not fit a quarter of a
            // phone at any size worth reading, and a cell that grows to fit it breaks the grid.
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (promoted && !selected) {
        // A small gold pip in the leading corner: «آخرین», without a word taking the tile's height.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CoineProSpacing.Half)
                .size(6.dp)
                .clip(CircleShape)
                .background(CoineProColors.Gold)
                .semantics { contentDescription = "tool-last-used-${tool.id}" },
        )
    }
    onHelp?.let { help ->
        HelpDot(
            onClick = help,
            modifier = Modifier.align(Alignment.TopEnd),
            size = 24.dp,
            glyph = 15.dp,
            // On the armed tile the plate is near-black, so the dot takes the stage's own
            // colour rather than disappearing into it.
            tint = if (selected) CoineProColors.Stage else CoineProColors.TextMuted,
        )
    }
    }
}

/**
 * The strip that says which tool is armed, and offers the ways out of it.
 *
 * A drawing tool is a *mode*, and a mode with no visible state is how people end up drawing trend
 * lines they did not mean to. This says what is armed, how many taps are left, and gives one tap
 * back to the cursor.
 */
@Composable
fun ActiveToolBar(
    tool: DrawingTool?,
    /** How many points are already placed on the drawing in progress. */
    placed: Int,
    onCancel: () -> Unit,
    onUndo: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
) {
    if (tool == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Icon(
            painter = painterResource(tool.icon.drawableRes()),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = CoineProColors.Accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.label(inEnglish()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
            if (tool.points > 0) {
                Text(
                    // A prose count, so Persian digits — unlike a price, which stays Latin.
                    text = tr(
                        "نقطه‌ی ${(placed + 1).toPersianDigits()} از ${(tool.points).toPersianDigits()}",
                        "Point ${placed + 1} of ${tool.points}",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
            // What the tool cannot do, where that is not obvious from its name. One tool needs it
            // today: the image frame, which cannot load a picture because nothing in the chart
            // layer can open a file. Said here, where the reader has just armed it and is about to
            // find out, rather than left for them to conclude from an empty rectangle.
            DrawingActions.toolNote(tool.id)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onUndo != null && placed > 0) {
            RailAction(DesignR.drawable.icon_arrows_clockwise, tr("واگرد", "Undo"), onClick = onUndo)
        }
        tool.helpId?.let { id ->
            onHelp?.let { RailAction(DesignR.drawable.tv_help_circle, tr("راهنما", "Help")) { it(id) } }
        }
        RailAction(DesignR.drawable.icon_x, tr("بستن", "Close"), onClick = onCancel)
    }
}

/**
 * The marks the icon tool offers, as a row a thumb picks one from.
 *
 * The icon tool stores its glyph in [Drawing.text], so this is a text field with ten answers rather
 * than a parallel field and a parallel codec — see [DrawingActions.ICON_GLYPHS]. A free keyboard is
 * the wrong shape for it: an icon is one mark, and a sentence typed into an icon tool is drawn at
 * label size inside a diamond built for a single glyph.
 *
 * Shown beside the text field rather than instead of it, so a reader who wants a mark this row does
 * not carry can still type one.
 */
@Composable
fun DrawingIconPicker(
    selected: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CoineProLazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        items(DrawingActions.ICON_GLYPHS.size, key = { "glyph-$it" }) { index ->
            val glyph = DrawingActions.ICON_GLYPHS[index]
            val chosen = glyph == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CoineProShapes.small)
                    .background(if (chosen) CoineProColors.SurfaceElevated else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (chosen) CoineProColors.Accent else CoineProColors.Border,
                        shape = CoineProShapes.small,
                    )
                    .clickable { onPick(glyph) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (chosen) CoineProColors.Accent else CoineProColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RailAction(
    icon: Int,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            // Forty-eight, which is Material's minimum and Android's own accessibility floor. The
            // glyph stays eighteen; it is the *hit rect* that has to reach a thumb, and a row of
            // 32dp targets beside each other is the shape that produces "I keep tapping the wrong
            // one" in reviews of every app in this category.
            .size(48.dp)
            .clip(CircleShape)
            .coineProControl(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = when {
                !enabled -> CoineProColors.TextDisabled
                tint != null -> tint
                else -> CoineProColors.TextMuted
            },
        )
    }
}

/**
 * The placed drawings, listed so they can be found and removed.
 *
 * A chart with twenty drawings on it needs a way to delete the one behind the others, and hunting
 * for it by tapping is not one.
 */
@Composable
fun DrawingList(
    drawings: List<Drawing>,
    onSelect: (Drawing) -> Unit,
    onDelete: (Drawing) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Lock or unlock one drawing, or null where the caller does not offer it.
     *
     * The list is the right home for this rather than a long-press on the chart: a reader locking
     * a line has *finished* with it, and reaching for it on a crowded chart is the gesture the
     * lock exists to protect them from.
     */
    onSetLocked: ((Drawing, Boolean) -> Unit)? = null,
) {
    if (drawings.isEmpty()) {
        CoineProSheetEmpty(tr("هنوز چیزی روی چارت رسم نشده است.", "Nothing is drawn on this chart yet."), modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface)
            .heightIn(max = LIST_MAX_HEIGHT),
    ) {
        items(drawings.size, key = { drawings[it].id }) { index ->
            Column(modifier = rowMotion().fillMaxWidth()) {
                val drawing = drawings[index]
                val tool = DrawingTools[drawing.toolId]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(drawing) }
                        .padding(
                            horizontal = CoineProSpacing.Gutter,
                            vertical = CoineProSpacing.OneHalf,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                ) {
                    if (tool != null) {
                        Icon(
                            painter = painterResource(tool.icon.drawableRes()),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(drawing.colour),
                        )
                    }
                    Text(
                        text = tool?.label(inEnglish()) ?: drawing.toolId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    onSetLocked?.let { setLocked ->
                        RailAction(
                            if (drawing.locked) DesignR.drawable.tv_lock else DesignR.drawable.tv_unlock,
                            if (drawing.locked) tr("باز کردن قفل", "Unlock") else tr("قفل کردن", "Lock"),
                            tint = if (drawing.locked) CoineProColors.Gold else null,
                        ) { setLocked(drawing, !drawing.locked) }
                    }
                    // Delete is refused on a locked drawing by `DrawingActions.delete`; the button is
                    // dimmed here so the reader is told why rather than finding out by tapping.
                    RailAction(
                        DesignR.drawable.tv_trash2,
                        tr("حذف", "Remove"),
                        enabled = !drawing.locked,
                    ) { onDelete(drawing) }
                }
            }
        }
    }
}

/**
 * Three across — the reference's Drawings sheet, and the design brief's «3-column grid of 96 × 88
 * tiles». Four was this app's own answer and it was a reasonable one; three gives every Persian
 * label its whole width, which is what the two-line ellipsis was there to apologise for.
 */
private const val TOOLS_ACROSS = 3

/** Eighty-eight, the reference's tile: a 24 dp glyph over a 12 sp label, and 16 dp corners. */
private val CELL_HEIGHT = 88.dp

/** A sheet's list is capped, so the sheet does not quietly become the whole screen. */
private val LIST_MAX_HEIGHT = 320.dp
