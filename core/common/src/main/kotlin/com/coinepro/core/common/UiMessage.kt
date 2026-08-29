package com.coinepro.core.common

/**
 * A user-facing message produced outside the UI layer.
 *
 * Controllers live in Android library modules and cannot reach `stringResource`, so they must not
 * build display copy themselves. They describe *which* message to show and the UI resolves it in
 * the active language.
 *
 * The split between [Local] and [Server] is deliberate and load-bearing: copy CoinePro owns gets
 * translated, and text the server authored is shown exactly as it arrived. Translating server text
 * locally would mean restating provider state in our own words, which is the one thing this app is
 * built not to do.
 */
sealed interface UiMessage {
    /** Copy CoinePro owns. Resolved against the active language. */
    data class Local(val key: MessageKey) : UiMessage

    /**
     * Text authored by the server, such as a job's `errorMessage`. Displayed verbatim, never
     * translated. Exception text is deliberately not routed here — a socket or HTTP failure string
     * is diagnostic output, not product copy, so it maps to a [Local] fallback instead.
     */
    data class Server(val text: String) : UiMessage

    /** A [detail] message shown behind an owned lead-in, e.g. a stale-cache warning. */
    data class Prefixed(val prefix: MessageKey, val detail: UiMessage) : UiMessage

    companion object {
        fun of(key: MessageKey): UiMessage = Local(key)

        /** Server copy when present, otherwise our own [fallback]. */
        fun fromServer(text: String?, fallback: MessageKey): UiMessage =
            text?.trim()?.takeIf { it.isNotEmpty() }?.let(::Server) ?: Local(fallback)
    }
}

/**
 * Identifies an owned message. Each entry maps to exactly one string resource in the UI layer.
 *
 * The enum grows as each controller is converted; a screen and its controller are converted
 * together, so an unused key here means a conversion was left half-finished.
 */
enum class MessageKey {
    SIGNALS_UNAVAILABLE,
    SIGNAL_HISTORY_UNAVAILABLE,
    SIGNAL_DETAILS_UNAVAILABLE,
    CACHED_HISTORY_SHOWN,
    NOTIFICATION_CENTER_UNAVAILABLE,
    NOTIFICATION_PREFERENCES_NOT_SAVED,
    ALERT_SYMBOL_UNSUPPORTED,
    ALERT_VALUE_INVALID,
    ALERT_NOT_CREATED,
    ALERT_NOT_UPDATED,
    ALERT_NOT_DELETED,

    /**
     * A stored session the server could not confirm — the network is down, or it answered
     * something other than an auth failure.
     *
     * Owned copy and not the exception's text. This was three hard-coded English sentences written
     * straight into `SessionState` and rendered verbatim to a Persian reader, which is exactly what
     * this whole type exists to stop: a controller cannot reach `stringResource`, so it names the
     * message and the UI resolves it in the reader's language.
     */
    SESSION_NOT_REVALIDATED,

    /** The server did not name a Telegram bot, so there is nothing to sign in against. */
    TELEGRAM_SIGN_IN_NOT_CONFIGURED,

    /** The sign-in configuration could not be fetched at all. Retrying is the right suggestion. */
    TELEGRAM_SIGN_IN_CONFIG_UNAVAILABLE,

    /** The market catalogue failed to load. */
    MARKETS_UNAVAILABLE,

    /**
     * The AI keys.
     *
     * These replace nine authored **English** sentences that three controllers were writing
     * straight into UI state and three screens were rendering verbatim — "AI Signal job expired on
     * the server.", "Write a message before sending." — to an audience whose default language is
     * Persian. Not exception text: sentences somebody wrote, in the wrong language, for the reader.
     */
    AI_JOB_EXPIRED,
    AI_ENTITLEMENT_REQUIRED,
    AI_RESULT_UNUSABLE,
    AI_GENERATION_FAILED,
    AI_SYMBOL_UNSUPPORTED,
    AI_MESSAGE_EMPTY,
    AI_MESSAGE_TOO_LONG,
    AI_IMAGE_TOO_LARGE,
    AI_IMAGE_TYPE_UNSUPPORTED,
    AI_CONVERSATION_CHANGED,
}

/**
 * Maps a thrown failure onto owned copy.
 *
 * The exception's own message is intentionally discarded: it is an English platform string such as
 * `failed to connect to /10.0.2.2:443`, which is worse than a translated sentence for a reader who
 * cannot act on it either way.
 */
fun Throwable.toUiMessage(fallback: MessageKey): UiMessage = UiMessage.Local(fallback)
