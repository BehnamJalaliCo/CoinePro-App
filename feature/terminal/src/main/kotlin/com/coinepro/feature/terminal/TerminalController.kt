package com.coinepro.feature.terminal

import com.coinepro.core.marketdata.AcademyTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the terminal is not on screen. */
enum class TerminalError {
    /**
     * No terminal address is compiled into this build.
     *
     * Not a failure — a build that was never pointed at one. `COINEPRO_*_TERMINAL_URL` is empty by
     * default precisely so this reads as "not configured" rather than as a broken page.
     */
    NOT_CONFIGURED,

    /** The academy token could not be minted, so the terminal would load signed out. */
    NO_TOKEN,

    /** The server has the academy switched off entirely. */
    DISABLED,

    /** The page itself failed to load. */
    LOAD_FAILED,
}

data class TerminalUiState(
    val url: String? = null,
    /**
     * The academy token, to be planted in browser storage before the page boots.
     *
     * Held in state rather than fetched inside the WebView's own JavaScript, because the mint goes
     * through the app's authenticated client. The page reads it from `localStorage` under the key
     * its own client uses — this is not a hook added for the app, it is how the terminal has
     * always authenticated.
     */
    val token: String? = null,
    val loading: Boolean = true,
    val error: TerminalError? = null,
)

/**
 * The full web terminal, hosted rather than rebuilt.
 *
 * The native chart covers what most readers want. What it does not cover is `namascript`, which is
 * a scripting language evaluated with `new Function()` inside a Web Worker — there is no Kotlin
 * analogue short of writing a lexer, a parser, an interpreter and about seventy `ta.*` builtins
 * with per-call-site rolling state. The strategy tester and the object tree lean on it. So those
 * stay where they already work, behind one button, and an ordinary reader never sees a WebView.
 */
class TerminalController(
    private val baseUrl: String,
    private val tokens: AcademyTokenStore,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    /** Whether this build has anywhere to send the reader. Decides if the entry is drawn at all. */
    val isConfigured: Boolean get() = normalisedUrl(baseUrl) != null

    fun start() {
        val url = normalisedUrl(baseUrl)
        if (url == null) {
            _state.value = TerminalUiState(loading = false, error = TerminalError.NOT_CONFIGURED)
            return
        }
        if (_state.value.token != null) return
        _state.value = TerminalUiState(loading = true)
        scope.launch {
            runCatching { tokens.token() }
                .onSuccess { token -> _state.value = TerminalUiState(url = url, token = token, loading = true) }
                .onFailure { failure ->
                    _state.value = TerminalUiState(
                        loading = false,
                        error = if ((failure.message ?: "").contains("academy_disabled")) {
                            TerminalError.DISABLED
                        } else {
                            TerminalError.NO_TOKEN
                        },
                    )
                }
        }
    }

    fun onLoaded() {
        _state.value = _state.value.copy(loading = false)
    }

    fun onLoadFailed() {
        _state.value = _state.value.copy(loading = false, error = TerminalError.LOAD_FAILED)
    }

    fun retry() {
        _state.value = TerminalUiState()
        start()
    }
}

/**
 * The address to load, or null when there is nothing usable.
 *
 * HTTPS only. The page is handed a bearer token that opens the reader's whole academy account, and
 * over plain HTTP that token is readable by anything on the path — so a misconfigured `http://`
 * address is refused rather than downgraded silently.
 */
internal fun normalisedUrl(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!text.startsWith("https://", ignoreCase = true)) return null
    return text.trimEnd('/')
}

/**
 * The one line of JavaScript the app injects.
 *
 * It writes the academy token into the key the terminal's own API client reads — `cp_academy_token`
 * — which is exactly what a browser sign-in does. Nothing else is injected: no bridge object, no
 * hooks into the page's internals, nothing that would make the app's build and the terminal's
 * build have to move together.
 *
 * The token is embedded as a JSON string so a value containing a quote cannot end the literal and
 * run as code. Academy tokens are JWTs and contain none, which is exactly the assumption that stops
 * being true quietly.
 */
internal fun tokenInjectionScript(token: String): String {
    val escaped = token
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "")
        .replace("\r", "")
        .replace("<", "\\u003c")
    return """
        (function () {
          try {
            localStorage.setItem("cp_academy_token", "$escaped");
          } catch (e) {}
        })();
    """.trimIndent()
}
