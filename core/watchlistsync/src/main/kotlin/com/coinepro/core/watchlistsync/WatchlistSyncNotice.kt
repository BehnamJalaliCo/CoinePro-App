package com.coinepro.core.watchlistsync

import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.toPersianDigits

/**
 * What the last sync did, in the reader's terms.
 *
 * Every one of these is a sentence the reader may see, and the set is small on purpose. A sync
 * that reports six kinds of network failure is a sync the reader has to interpret; what they
 * actually need to know is one of four things — it worked, it worked and something moved, nothing
 * could be reached, or something must be done before it can work at all.
 *
 * There is no `FAILED` that shouts. This audience is offline most of the time and installs the app
 * from channels that are not Play; a red banner every time a sync could not reach a server would
 * be a red banner most days, and a warning that is always on is a warning nobody reads. A failed
 * sync leaves the local watchlist exactly as it was — which is a fully working watchlist — so the
 * honest volume for it is a quiet line, not an alarm.
 */
enum class WatchlistSyncNotice {
    /** The server already held exactly this. Nothing moved in either direction. */
    UP_TO_DATE,

    /** This device's copy was sent. Nothing came back that it did not already have. */
    UPLOADED,

    /** Merged, and lists or symbols came in from another device. The counts say how much. */
    MERGED,

    /**
     * A list deleted on another device is now gone from this one too.
     *
     * Reported ahead of [MERGED] when a sync did both, because it is the only outcome of a sync
     * that takes something away, and a reader who sees a list vanish without being told why has
     * been given a reason to distrust the feature permanently. What arrived in the same sync is
     * visible in the switcher; what left is not.
     */
    REMOVED,

    /** No verdict was reached. The local watchlist is untouched and still works. */
    OFFLINE,

    /** The server refused for a reason that is not the reader's to fix. Trying later is right. */
    REFUSED,

    /** Past the server's cap. Nothing was written — see [WatchlistSyncTooLargeException]. */
    TOO_LARGE,

    /** This platform serves no watchlist document. The control should not be offered at all. */
    UNSUPPORTED,
}

/**
 * The sentence for the current state.
 *
 * On the state rather than on the notice, because two of them need a fact the notice alone does
 * not carry: whether the server named its cap. Being told «بزرگ‌تر از سقف ۶۴ کیلوبایتی» is
 * actionable, and being told a figure this app invented because the refusal did not carry one is
 * worse than being told no figure at all.
 *
 * Public, and the only place a sync state becomes words, so that a second surface showing the same
 * state cannot word it differently — which is how a reader ends up told the sync failed in one
 * place and that it is up to date in another.
 */
fun WatchlistSyncState.messageRes(): Int = when (notice) {
    WatchlistSyncNotice.UP_TO_DATE -> R.string.watchlist_sync_state_current
    WatchlistSyncNotice.UPLOADED -> R.string.watchlist_sync_state_uploaded
    WatchlistSyncNotice.MERGED -> R.string.watchlist_sync_state_merged
    WatchlistSyncNotice.REMOVED -> R.string.watchlist_sync_state_removed
    WatchlistSyncNotice.OFFLINE -> R.string.watchlist_sync_state_offline
    WatchlistSyncNotice.REFUSED -> R.string.watchlist_sync_state_refused
    WatchlistSyncNotice.TOO_LARGE ->
        if (maxBytes == null) {
            R.string.watchlist_sync_state_too_large_unknown
        } else {
            R.string.watchlist_sync_state_too_large
        }
    WatchlistSyncNotice.UNSUPPORTED -> R.string.watchlist_sync_state_unsupported
    null -> R.string.watchlist_sync_state_never
}

/**
 * The numbers that go into [messageRes], already written in the digits that language uses.
 *
 * Formatted here rather than handed to `stringResource` as an `Int`, because Android would render
 * an `Int` in Latin digits under every locale — and every number in these sentences is a **prose
 * count**, which this app writes in Persian digits for a Persian reader. A price or a percentage
 * would go the other way and stay Latin in both languages, so that it can be read against LBank or
 * TradingView without converting in the head. None of these is one.
 *
 * The kilobyte figure is rounded **down**, so the number the reader is shown is one their document
 * is genuinely over rather than one it might still be under.
 */
fun WatchlistSyncState.noticeArguments(language: AppLanguage): List<String> {
    fun count(value: Int): String =
        if (language == AppLanguage.PERSIAN) value.toPersianDigits() else value.toString()
    return when (notice) {
        WatchlistSyncNotice.MERGED -> listOf(count(listsAdopted), count(symbolsAdopted))
        WatchlistSyncNotice.REMOVED -> listOf(count(listsDropped))
        WatchlistSyncNotice.TOO_LARGE -> maxBytes?.let { listOf(count(it / 1024)) }.orEmpty()
        else -> emptyList()
    }
}
