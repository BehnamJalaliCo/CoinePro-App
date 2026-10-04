package com.coinepro.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The first-run screens are drawn over the app rather than in place of it, so the chart can load
 * its candles underneath. A tap on their empty glass therefore fell through to the chart and opened
 * «چرا این حرکت؟» behind the welcome (5.21.1). This makes the cover a cover: it takes every pointer
 * that lands on it, and its own buttons, deeper in the tree, still get theirs first.
 */
internal fun Modifier.coverTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}
