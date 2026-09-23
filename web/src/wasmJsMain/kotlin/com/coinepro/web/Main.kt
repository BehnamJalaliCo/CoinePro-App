package com.coinepro.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

/** The page's entry point: one Compose surface over `<div id="terminal">`, and the terminal in it. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "terminal") {
        TerminalApp()
    }
}
