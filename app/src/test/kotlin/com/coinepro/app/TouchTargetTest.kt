package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.CoineProToggleChip
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every control a thumb has to hit is at least 48dp of *touchable* area.
 *
 * This exists because a design audit claimed several controls were half a target, and reading the
 * modifier chain is not enough to tell: `minimumInteractiveComponentSize()` sits outside a
 * `size(34.dp)` in this app's header action and in Material 3's own `IconButton`, and whether that
 * produces a 34dp target or a 48dp one is a question about how Compose expands touch bounds, not a
 * question about the source. So it is measured rather than argued.
 *
 * `touchBoundsInRoot` is the rectangle Compose actually hit-tests, which is the number that decides
 * whether a tap lands — not the drawn size, and not the reported layout size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class TouchTargetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Android's own bar, and the one the Play listing's accessibility review measures against. */
    private val minimum = 48.dp

    private fun assertEveryControlIsReachable(content: @Composable () -> Unit) {
        rule.setContent { CoineProTheme { content() } }
        val density = rule.density
        val clickable = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsActions.OnClick)) clickable += node
            node.children.forEach(::walk)
        }
        walk(rule.onRoot().fetchSemanticsNode())
        assertTrue("no clickable control was rendered — the test would pass vacuously", clickable.isNotEmpty())
        val floor = with(density) { minimum.toPx() }
        // A pixel of slack: 48dp at xxhdpi is 144px exactly, but a control centred on a fractional
        // boundary can round to 143 and that is not a control anybody misses.
        val tolerance = 1f
        clickable.forEach { node ->
            val bounds = node.touchBoundsInRoot
            val label = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .firstOrNull()
                ?: node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .firstOrNull()?.text
                ?: "unlabelled control"
            assertTrue(
                "«$label» is ${with(density) { bounds.width.toDp() }} × " +
                    "${with(density) { bounds.height.toDp() }} of touchable area, under $minimum",
                bounds.width >= floor - tolerance && bounds.height >= floor - tolerance,
            )
        }
    }

    @Test
    fun `the header action every list is refreshed from is reachable`() {
        assertEveryControlIsReachable {
            Row {
                CoineProHeaderAction(CoineProIcons.Refresh, "تازه‌سازی") {}
                CoineProHeaderAction(CoineProIcons.Settings, "تنظیمات") {}
            }
        }
    }

    @Test
    fun `a chip is reachable at both sizes`() {
        // The most-pressed control in the app: every timeframe, every filter, every symbol. Five
        // screens had hand-rolled their own at four points of vertical padding — about 23dp — and
        // they now all route through this one composable, so measuring it measures all of them.
        assertEveryControlIsReachable {
            Column {
                CoineProToggleChip(label = "روزانه", selected = true, onClick = {})
                CoineProToggleChip(label = "هفتگی", selected = false, onClick = {})
                CoineProToggleChip(label = "۵م", selected = true, onClick = {}, compact = true)
                CoineProToggleChip(label = "۱۵م", selected = false, onClick = {}, compact = true)
                CoineProChipRow(
                    options = listOf(
                        CoineProChip(id = "trend", label = "روند", count = 7),
                        CoineProChip(id = "volume", label = "حجم"),
                    ),
                    selectedId = "trend",
                    onSelect = {},
                    allLabel = "همه",
                )
            }
        }
    }
}
