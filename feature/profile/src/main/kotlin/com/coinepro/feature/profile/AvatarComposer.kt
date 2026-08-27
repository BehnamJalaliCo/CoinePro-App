package com.coinepro.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarMark
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec

/** The five shelves the composer offers, in the order a reader is most likely to want them. */
private enum class ComposerTab { MINE, CRYPTO, FOREX, METAL, MARK }

/**
 * Where a reader builds the thing that represents them.
 *
 * The whole panel is **live**: the preview at the top is the real [CoineProAvatar] at profile size,
 * drawn from the working spec, so the mark that moves is moving while it is being chosen and the
 * ring being tried is the ring that will ship. A composer that previews with a static mock is a
 * composer whose result is a small surprise every time.
 *
 * Nothing commits until «ذخیره». Cancelling a half-built avatar has to leave the old one exactly as
 * it was, which means the working copy is local state here and the store is written once.
 *
 * The panel is a body rather than a sheet so the screenshot tests can render it — see
 * `CoineProSheetBody` for why every sheet in this app is split that way.
 */
@Composable
fun AvatarComposerBody(
    current: AvatarSpec,
    initial: String,
    onSave: (AvatarSpec) -> Unit,
    onCancel: () -> Unit,
    /** Opens the system picker. The caller owns it, because it needs an Activity result. */
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A photo already imported this session, if any.
     *
     * Held by the caller rather than read from [current], so a reader who picks a picture, tries a
     * coin, and comes back to the picture does not have to import it twice.
     */
    photoPath: String? = null,
) {
    var working by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver(
            save = { spec: AvatarSpec -> AvatarSpec.encode(spec) },
            restore = { encoded: String -> AvatarSpec.decode(encoded) },
        ),
    ) { mutableStateOf(current) }
    var tab by rememberSaveable { mutableStateOf(current.tab()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CoineProAvatar(spec = working, initial = initial, size = 104.dp)
        }

        RingRow(selected = working.ring, onSelect = { working = working.copy(ring = it) })

        CoineProSegmentTabs(
            options = listOf(
                ComposerTab.MINE to stringResource(R.string.avatar_tab_mine),
                ComposerTab.CRYPTO to stringResource(R.string.avatar_tab_crypto),
                ComposerTab.FOREX to stringResource(R.string.avatar_tab_forex),
                ComposerTab.METAL to stringResource(R.string.avatar_tab_metal),
                ComposerTab.MARK to stringResource(R.string.avatar_tab_mark),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            ComposerTab.MINE -> MineTab(
                working = working,
                initial = initial,
                photoPath = photoPath,
                onPickPhoto = onPickPhoto,
                onSelect = { working = working.copy(base = it) },
            )
            ComposerTab.CRYPTO -> SymbolGrid(
                symbols = AvatarCatalog.CRYPTO,
                working = working,
                onSelect = { working = working.copy(base = AvatarBase.Symbol(it)) },
            )
            ComposerTab.FOREX -> SymbolGrid(
                symbols = AvatarCatalog.FOREX,
                working = working,
                onSelect = { working = working.copy(base = AvatarBase.Symbol(it)) },
            )
            ComposerTab.METAL -> SymbolGrid(
                symbols = AvatarCatalog.METALS,
                working = working,
                onSelect = { working = working.copy(base = AvatarBase.Symbol(it)) },
            )
            ComposerTab.MARK -> MarkGrid(
                working = working,
                onSelect = { working = working.copy(base = AvatarBase.Mark(it)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProPrimaryButton(
                text = stringResource(R.string.avatar_save),
                onClick = { onSave(working) },
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.avatar_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Which shelf a spec came off, so reopening the composer lands where the reader last was. */
private fun AvatarSpec.tab(): ComposerTab = when (val current = base) {
    is AvatarBase.Mark -> ComposerTab.MARK
    is AvatarBase.Symbol -> when {
        current.symbol.startsWith("X") && current.symbol.length == 6 -> ComposerTab.METAL
        current.symbol.length == 6 -> ComposerTab.FOREX
        else -> ComposerTab.CRYPTO
    }
    else -> ComposerTab.MINE
}

/**
 * The ring, as six discs rather than a colour wheel.
 *
 * A wheel would let somebody paint their edge a colour this app already uses to mean something
 * else — a purple ring beside a green one says nothing, where blue-beside-green says "analysis,
 * not execution". Six choices, each already meaningful, is a smaller freedom and a better one.
 */
@Composable
private fun RingRow(selected: AvatarRing, onSelect: (AvatarRing) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        OFFERED_RINGS.forEach { ring ->
            val colour = ring.swatch()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(CircleShape)
                    .background(
                        if (ring == AvatarRing.NONE) {
                            CoineProColors.SurfaceElevated
                        } else {
                            CoineProTint.fill(colour, CoineProColors.SurfaceElevated)
                        },
                    )
                    .border(
                        width = if (ring == selected) 2.dp else 1.dp,
                        color = if (ring == selected) colour else CoineProColors.Border,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(ring) },
                contentAlignment = Alignment.Center,
            ) {
                if (ring == AvatarRing.NONE) {
                    Text(
                        text = stringResource(R.string.avatar_ring_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                } else {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(colour))
                }
            }
        }
    }
}

@Composable
private fun AvatarRing.swatch(): Color = when (this) {
    AvatarRing.NONE -> CoineProColors.Border
    AvatarRing.GOLD -> CoineProColors.Accent
    AvatarRing.PREMIUM -> CoineProColors.Premium
    AvatarRing.ANALYSIS -> CoineProColors.Analysis
    AvatarRing.BUY -> CoineProColors.Buy
    AvatarRing.SELL -> CoineProColors.Sell
}

/**
 * The reader's own two: their letter, and their picture.
 *
 * The letter is first and is never removed as an option, because it is the one that is always
 * right — somebody who does not want to be a photograph or a coin has to have somewhere to land.
 */
@Composable
private fun MineTab(
    working: AvatarSpec,
    initial: String,
    photoPath: String?,
    onPickPhoto: () -> Unit,
    onSelect: (AvatarBase) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Cell(
                selected = working.base is AvatarBase.Initial,
                onClick = { onSelect(AvatarBase.Initial) },
            ) {
                CoineProAvatar(
                    spec = AvatarSpec(AvatarBase.Initial, AvatarRing.NONE),
                    initial = initial,
                    size = 46.dp,
                )
            }
            photoPath?.let { path ->
                Cell(
                    selected = (working.base as? AvatarBase.Photo)?.path == path,
                    onClick = { onSelect(AvatarBase.Photo(path)) },
                ) {
                    CoineProAvatar(
                        spec = AvatarSpec(AvatarBase.Photo(path), AvatarRing.NONE),
                        initial = initial,
                        size = 46.dp,
                    )
                }
            }
        }
        CoineProSecondaryButton(
            text = stringResource(
                if (photoPath == null) R.string.avatar_pick_photo else R.string.avatar_pick_another,
            ),
            onClick = onPickPhoto,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.avatar_photo_note),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun SymbolGrid(symbols: List<String>, working: AvatarSpec, onSelect: (String) -> Unit) {
    val chosen = (working.base as? AvatarBase.Symbol)?.symbol
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 62.dp),
        // Bounded, because this sits inside a scrolling column and a grid measured against an
        // infinite height crashes rather than degrades.
        modifier = Modifier.fillMaxWidth().heightIn(max = GRID_MAX),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.Gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        items(symbols, key = { it }) { symbol ->
            Cell(selected = symbol == chosen, onClick = { onSelect(symbol) }) {
                CoineProAvatar(
                    spec = AvatarSpec(AvatarBase.Symbol(symbol), AvatarRing.NONE),
                    size = 46.dp,
                )
            }
        }
    }
}

@Composable
private fun MarkGrid(working: AvatarSpec, onSelect: (AvatarMark) -> Unit) {
    val chosen = (working.base as? AvatarBase.Mark)?.mark
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 62.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = GRID_MAX),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.Gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        items(AvatarCatalog.MARKS, key = { it.name }) { mark ->
            Cell(selected = mark == chosen, onClick = { onSelect(mark) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CoineProAvatar(
                        spec = AvatarSpec(AvatarBase.Mark(mark), AvatarRing.NONE),
                        size = 42.dp,
                    )
                    Text(
                        text = stringResource(mark.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One selectable tile. The selection is a border, not a fill: the artwork keeps its own ground. */
@Composable
private fun Cell(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CoineProColors.Accent else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

internal fun AvatarMark.labelRes(): Int = when (this) {
    AvatarMark.ROCKET -> R.string.avatar_mark_rocket
    AvatarMark.BULL -> R.string.avatar_mark_bull
    AvatarMark.BEAR -> R.string.avatar_mark_bear
    AvatarMark.CANDLE -> R.string.avatar_mark_candle
    AvatarMark.DIAMOND -> R.string.avatar_mark_diamond
    AvatarMark.FLAME -> R.string.avatar_mark_flame
    AvatarMark.BOLT -> R.string.avatar_mark_bolt
    AvatarMark.TREND -> R.string.avatar_mark_trend
    AvatarMark.SHIELD -> R.string.avatar_mark_shield
    AvatarMark.GLOBE -> R.string.avatar_mark_globe
}

/**
 * The rings a reader may pick, which is every one but [AvatarRing.PREMIUM].
 *
 * Premium is `#D4AF37` and the brand gold is `#D8A848`. Side by side in a picker they are the same
 * swatch, so offering both is a control asking for a distinction the eye cannot make — and worse,
 * it would let anybody wear the ring the design system reserves for a subscription. It stays in the
 * enum because the app sets it; it is not on the shelf because it is not a choice.
 */
private val OFFERED_RINGS = listOf(
    AvatarRing.NONE,
    AvatarRing.GOLD,
    AvatarRing.ANALYSIS,
    AvatarRing.BUY,
    AvatarRing.SELL,
)

/** Four rows of tiles. Past that the composer's own buttons leave the screen. */
private val GRID_MAX = 244.dp
