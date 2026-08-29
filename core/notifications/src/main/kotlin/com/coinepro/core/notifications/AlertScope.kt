package com.coinepro.core.notifications

/**
 * What an alert is about: one instrument, or a list of them.
 *
 * ### The list case is not a convenience, it is a different feature
 *
 * "Tell me when anything in my watchlist moves five percent" cannot be built out of per-symbol
 * alerts, and the difference is membership. Forty symbols means forty alerts to create by hand, and
 * — the part that actually hurts — forty to remember when the list changes. Somebody who stars a
 * new coin on Tuesday does not think "and now I must go and re-create my alert"; they think the
 * alert covers their watchlist, because that is what they asked for.
 *
 * So membership is resolved **at evaluation time**, never captured. A symbol added to the list
 * afterwards is covered from the next evaluation; one removed stops being watched, and no stale
 * alert is left behind pointing at something the reader deliberately dropped. That is what
 * [resolve] is for, and it is why it takes the list's current contents as a parameter rather than
 * this type holding a copy of them.
 *
 * ### Why a sealed interface rather than a nullable list id
 *
 * A nullable field would make "symbol is empty and listId is null" representable, and something
 * would have to decide what that means. Here it is not representable.
 */
sealed interface AlertScope {

    /** Stable key for storage. Never localise it. */
    val id: String

    /**
     * The symbols this scope covers right now.
     *
     * [membersOf] is asked for a named list's current contents and is only called for the list
     * case, so a caller with no watchlists can pass a function returning nothing and the symbol
     * case still works.
     */
    fun resolve(membersOf: (String) -> List<String>): List<String>

    /** One instrument, named by its ticker. The common case and the default. */
    data class Symbol(val ticker: String) : AlertScope {

        init {
            require(ticker.isNotBlank()) { "An alert on a symbol needs a ticker." }
        }

        override val id: String get() = ID

        override fun resolve(membersOf: (String) -> List<String>): List<String> = listOf(ticker)

        companion object {
            const val ID = "symbol"
        }
    }

    /**
     * Every symbol in one named list, evaluated independently.
     *
     * Independently is the word that matters: this is not one alert that fires once for whichever
     * symbol moved first, it is the same question asked of each symbol on its own. Each has its own
     * firing state, so a move in one does not consume the alert for the other thirty-nine.
     *
     * [listId] is the watchlist's stable key rather than its name, so renaming a list does not
     * orphan every alert pointing at it. A list that has since been deleted resolves to nothing and
     * the alert simply never fires; deleting it out from under the reader would be the app throwing
     * away a choice it was not asked to reconsider.
     */
    data class Watchlist(val listId: String) : AlertScope {

        init {
            require(listId.isNotBlank()) { "An alert on a watchlist needs a list id." }
        }

        override val id: String get() = ID

        override fun resolve(membersOf: (String) -> List<String>): List<String> = membersOf(listId)

        companion object {
            const val ID = "watchlist"

            /**
             * The id of the one list this app has today.
             *
             * `WatchlistStore` keeps a single unnamed list, so every watchlist alert points here
             * until named lists exist. Written down as a constant rather than spelled out at each
             * call site, so the day there are several there is one place that stops being right.
             */
            const val DEFAULT_LIST_ID = "default"
        }
    }

    /**
     * The stored form of a scope.
     *
     * The same tolerance rule as everything else in this file's neighbourhood: an unreadable scope
     * decodes to null and the alert falls back to the single symbol its row already carries, rather
     * than taking the alerts screen down with it.
     */
    companion object {

        /** Between the case and its argument. ASCII unit separator, as in [AlertTriggerCodec]. */
        private const val PART = "\u001F"

        /** The stored form, or an empty string for no scope and for anything unwritable. */
        fun encode(scope: AlertScope?): String = when (scope) {
            null -> ""
            is Symbol -> Symbol.ID + PART + scope.ticker
            is Watchlist -> Watchlist.ID + PART + scope.listId
        }.let { if (it.any { character -> character == ';' || character == '|' }) "" else it }

        /** The scope a row was written with, or null for anything this version cannot read. */
        fun decode(raw: String?): AlertScope? {
            val text = raw?.takeIf(String::isNotBlank) ?: return null
            val argument = text.substringAfter(PART, missingDelimiterValue = "")
            if (argument.isBlank()) return null
            return when (text.substringBefore(PART)) {
                Symbol.ID -> Symbol(argument)
                Watchlist.ID -> Watchlist(argument)
                else -> null
            }
        }
    }
}
