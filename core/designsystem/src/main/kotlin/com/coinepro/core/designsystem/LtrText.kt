package com.coinepro.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Lays [content] out left-to-right inside an otherwise right-to-left screen.
 *
 * Use this for rows of financial figures — entry / stop / target columns, price ladders, order
 * books — where the *order of the columns* carries meaning that must not mirror. For a single
 * number inside a sentence, isolating the string with
 * [com.coinepro.core.common.BidiText.isolateLtr] is enough and cheaper; reach for this only when
 * layout, not just glyph order, has to stay fixed.
 */
@Composable
fun LtrLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier) { content() }
    }
}

/**
 * Same as [LtrLayout] but without imposing a [Row], for callers that bring their own container.
 */
@Composable
fun LtrDirection(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}
