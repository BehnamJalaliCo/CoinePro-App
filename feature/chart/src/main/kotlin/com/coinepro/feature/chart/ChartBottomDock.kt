package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.R as DesignR

/**
 * TradingView's bottom panel, under the chart on a desktop window (5.23.0): the script editor, the
 * strategy tester and paper trading as tabs on one strip, the chart above them still live.
 *
 * Measured against TradingView's (and the old Pro Chart terminal's `BottomDock`): a 36 dp strip
 * that is all there is until a tab is chosen; an open panel 320 dp tall by default, dragged by its
 * top edge between [DOCK_MIN] and [DOCK_MAX], a double-click on that edge putting it back; the tab in
 * force underlined in the accent; minimise, maximise and close at the far end. The strip reads left
 * to right where the toolbar does ([ltrChrome]); what a panel holds keeps the reader's direction.
 */
@Composable
internal fun ChartBottomDock(
    panels: List<ChartSidePanel>,
    modifier: Modifier = Modifier,
    ltrChrome: Boolean = false,
) {
    if (panels.isEmpty()) return
    val reader = LocalLayoutDirection.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var height by rememberSaveable { mutableFloatStateOf(DOCK_DEFAULT.value) }
    var maximised by rememberSaveable { mutableStateOf(false) }
    val open = panels.firstOrNull { it.id == openId }
    val density = LocalDensity.current
    // Never so tall that the chart above it stops being one.
    val ceiling = minOf(DOCK_MAX.value, com.coinepro.core.designsystem.coineProWindowClass().heightDp - CHART_FLOOR)
        .coerceAtLeast(DOCK_MIN.value)

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Stage).testTag(BOTTOM_DOCK_TAG)) {
        // The top edge: a drag handle while a panel is open, a hairline otherwise.
        if (open != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DOCK_HANDLE)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, drag ->
                            change.consume()
                            maximised = false
                            val dp = with(density) { drag.toDp().value }
                            height = (height - dp).coerceIn(DOCK_MIN.value, ceiling)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            maximised = false
                            height = DOCK_DEFAULT.value
                        })
                    }
                    .semantics { contentDescription = "dock-resize" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(40.dp).height(2.dp).background(CoineProColors.Border))
            }
        } else {
            HorizontalDivider(color = CoineProColors.Border)
        }
        CompositionLocalProvider(LocalLayoutDirection provides if (ltrChrome) LayoutDirection.Ltr else reader) {
            Row(
                modifier = Modifier.fillMaxWidth().height(DOCK_STRIP).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val accent = CoineProColors.Accent
                panels.forEach { panel ->
                    val active = panel.id == openId
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            // The underline is drawn, not laid out: a child as wide as its parent
                            // would make the tab take the whole strip.
                            .drawBehind {
                                if (active) {
                                    val line = 2.dp.toPx()
                                    drawRect(accent, topLeft = Offset(0f, size.height - line), size = Size(size.width, line))
                                }
                            }
                            .chromePlate(interaction, active = active) {
                                openId = if (active) null else panel.id
                            }
                            .semantics { contentDescription = "dock-tab-${panel.id}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(panel.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (open != null) {
                    DockKey(
                        icon = if (maximised) DesignR.drawable.tv_minimize2 else DesignR.drawable.tv_maximize2,
                        description = "dock-maximise",
                    ) { maximised = !maximised }
                    DockKey(icon = DesignR.drawable.icon_caret_down, description = "dock-minimise") { openId = null }
                } else {
                    DockKey(icon = DesignR.drawable.icon_caret_up, description = "dock-restore") {
                        openId = panels.first().id
                    }
                }
            }
        }
        if (open != null) {
            HorizontalDivider(color = CoineProColors.Border)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (maximised) ceiling.dp else height.coerceAtMost(ceiling).dp),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides reader) {
                    Box(Modifier.fillMaxSize()) { open.content() }
                }
            }
        }
    }
}

@Composable
private fun DockKey(icon: Int, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(28.dp)
            .chromePlate(interaction, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

internal const val BOTTOM_DOCK_TAG = "chart-bottom-dock"

private val DOCK_STRIP = 36.dp
private val DOCK_HANDLE = 8.dp
private val DOCK_DEFAULT = 320.dp
private val DOCK_MIN = 200.dp
private val DOCK_MAX = 600.dp

/** What the chart keeps above an open panel, in dp: the toolbar, the bottom bar and a plot. */
private const val CHART_FLOOR = 320f
