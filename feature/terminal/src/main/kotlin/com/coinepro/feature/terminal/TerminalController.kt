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
    // A configured address carrying credentials is refused rather than stripped: it means the build
    // was pointed somewhere odd, and [terminalHost] would then disagree with what a reader sees.
    if (text.substringAfter("://").substringBefore('/').contains('@')) return null
    return text.trimEnd('/')
}

/**
 * The one host the terminal WebView may ever be on.
 *
 * This exists because the check it replaces was `target.startsWith(url)`, and a prefix test on a URL
 * string is not an origin test. With the address normalised to `https://terminal.example` — no
 * trailing slash — all three of these passed it:
 *
 * ```
 * https://terminal.example.evil.tld/steal          the host is evil.tld
 * https://terminal.example@evil.tld/steal          the host is evil.tld; the rest is userinfo
 * https://terminal.example-not-really.tld/steal    a different registrable domain entirely
 * ```
 *
 * That mattered more than a navigation going astray, because `onPageStarted` plants the academy
 * token into whatever document is loading. A page reached through one of those would have been
 * handed the reader's token.
 *
 * So the comparison is on the parsed host, exactly, the way `DeepLinkValidation` already compares
 * the reset host. Any URL that fails to parse, or carries userinfo, or is not https, is not the
 * terminal.
 */
internal fun terminalHost(url: String?): String? {
    val text = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!text.startsWith("https://", ignoreCase = true)) return null
    val authority = text.removePrefix("https://").removePrefix("HTTPS://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    // Userinfo is the whole trick in the second example above, so its presence disqualifies the URL
    // rather than being parsed past.
    if (authority.contains('@')) return null
    val host = authority.substringBefore(':').lowercase()
    return host.takeIf { it.isNotEmpty() && !it.contains('\\') }
}

/**
 * Whether a URL the WebView is about to open is the terminal itself.
 *
 * Scheme and host, both exact. Not the path: the terminal routes within itself and pinning a path
 * prefix here would break its own navigation the next time it grew a page.
 */
internal fun isTerminalUrl(target: String?, terminal: String?): Boolean {
    val expected = terminalHost(terminal) ?: return false
    val actual = terminalHost(target) ?: return false
    return actual == expected
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
