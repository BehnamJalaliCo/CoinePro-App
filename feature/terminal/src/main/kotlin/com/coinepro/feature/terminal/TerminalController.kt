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
    /** The terminal's own address, with no credential in it. The origin every navigation is tested against. */
    val url: String? = null,
    /**
     * What is actually loaded: [url] with the token in the fragment.
     *
     * Separate from [url] so the origin check has something to compare against that never carries a
     * secret, and so nothing that logs a URL logs the token with it.
     */
    val launchUrl: String? = null,
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
/**
 * @param baseUrl where the terminal lives. A **function**, not a string, because the address is the
 * server's to state and it is read after sign-in: the value compiled into a build cannot know that
 * a host was decommissioned, and one was — the address this app used to carry stopped resolving,
 * so the button would have opened a browser error. The build value survives only as the fallback
 * for a deployment that does not report one.
 */
class TerminalController(
    private val baseUrl: () -> String?,
    private val tokens: AcademyTokenStore,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    /** Whether there is anywhere to send the reader. Decides if the entry is drawn at all. */
    val isConfigured: Boolean get() = normalisedUrl(baseUrl()) != null

    fun start() {
        val url = normalisedUrl(baseUrl())
        if (url == null) {
            _state.value = TerminalUiState(loading = false, error = TerminalError.NOT_CONFIGURED)
            return
        }
        if (_state.value.token != null) return
        _state.value = TerminalUiState(loading = true)
        scope.launch {
            runCatching { tokens.token() }
                .onSuccess { token ->
                    _state.value = TerminalUiState(
                        url = url,
                        launchUrl = launchUrl(url, token),
                        token = token,
                        loading = true,
                    )
                }
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
 * The address to open, with the academy token in the fragment.
 *
 * This replaces injecting a line of JavaScript that wrote the token into the page's `localStorage`.
 * The fragment is better for a reason worth stating: **it is never sent to a server.** Browsers do
 * not put the part after `#` in the request line or in `Referer`, so the token does not appear in
 * an access log, a proxy log or a CDN record — where a query parameter would appear in all three.
 *
 * The terminal reads it, keeps it in memory and clears the fragment from its own address bar.
 *
 * It also removes the sharpest edge the WebView had. Injection ran on `onPageStarted`, which fires
 * for whatever document is loading — so the guard that decides *which* document was the only thing
 * standing between the reader's token and any page that got itself loaded there. Now nothing is
 * injected at all: the credential travels in the URL the app chose, and a page the app did not
 * choose is never given one.
 *
 * Encoded, because a JWT's `+` and `/` are legal in a fragment but a token is not the app's to
 * assume anything about.
 */
internal fun launchUrl(url: String, token: String): String =
    url + "#t=" + java.net.URLEncoder.encode(token, "UTF-8")
