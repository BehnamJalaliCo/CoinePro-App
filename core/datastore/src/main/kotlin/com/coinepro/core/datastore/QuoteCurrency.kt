package com.coinepro.core.datastore

/**
 * What a reader wants prices quoted in (U2).
 *
 * ### This is a preference about *listings*, not a converter
 *
 * The app holds no exchange rates and is not going to invent them. Choosing tether does not turn a
 * gold price into a tether price; it decides **which pair a list prefers** when the same asset is
 * quoted against several — `BTC/USDT` over `BTC/USDC` — and what the column heading says. A figure
 * is never re-denominated behind the reader, because a price that quietly changed units is the
 * worst thing a market screen can do.
 *
 * ### Why these four
 *
 * The three the venues in this app's scope actually quote against, plus the one a Persian reader
 * thinks in. `IRR` has no market on either backend, and that is exactly why it is offered: a reader
 * who picks it is telling the app which currency their head is in, which is worth knowing even
 * while no row can be drawn in it. Where nothing can be quoted in the chosen unit the list stays in
 * the venue's own and says so — the alternative is an empty screen.
 *
 * [id] is stored and must stay stable.
 */
enum class QuoteCurrency(val id: String, val code: String) {
    USDT("usdt", "USDT"),
    USD("usd", "USD"),
    IRR("irr", "IRR"),
    BTC("btc", "BTC"),
    ;

    companion object {
        /** Tether, because it is what both venues quote most of their book against. */
        val Default: QuoteCurrency = USDT

        fun fromId(id: String?): QuoteCurrency =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Default
    }
}
