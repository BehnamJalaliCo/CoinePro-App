package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything a paper-trading screen renders, in one value.
 *
 * The marks are carried alongside the book rather than folded into it because they are not the
 * simulator's state — they are the market's, they change several times a second, and a book that
 * changed every time a price ticked could not be compared against the one on disk to decide whether
 * a write is needed.
 */
data class PaperTradeUiState(
    val book: PaperBook = PaperBook(),
    val quotes: Map<String, PaperQuote> = emptyMap(),
    /**
     * Whether the stored book has been read yet.
     *
     * False for the first frame after launch. The screen shows nothing rather than an empty account
     * during it: «موجودی ۱۰٬۰۰۰» printed for a tenth of a second in front of a reader whose account
     * is at 8,240 is a number they will remember and not believe again.
     */
    val loaded: Boolean = false,
) {
    val marks: Map<String, Double?>
        get() = quotes.mapValues { (_, quote) -> quote.last.takeIf { it.isFinite() && it > 0.0 } }

    val equity: Double get() = book.equity(marks)
    val unrealised: Double get() = book.unrealised(marks)
    val freeMargin: Double get() = book.freeMargin(marks)
    val marginLevelPercent: Double? get() = book.marginLevelPercent(marks)

    /** True where a position is open whose symbol has no price at all. The screen has to say so. */
    val markIncomplete: Boolean get() = !book.equityIsComplete(marks)

    /** True where any price the book is marked against is one its own feed calls stale. */
    val stale: Boolean get() = book.tracked.any { quotes[it]?.stale == true }

    fun quoteFor(symbol: String): PaperQuote? = quotes[PaperEngine.normalise(symbol)]
}

/**
 * The paper account, as the app holds it.
 *
 * A thin shell on purpose. Every decision about money is in [PaperEngine], which is pure; this owns
 * only the three things a pure function cannot — the clock, the coroutine scope and the store — and
 * one rule of its own: **a write happens when the stored form of the book changes, and not when a
 * price ticks.** Without that rule the app would write a preference file several times a second for
 * a reader who is doing nothing but watching.
 *
 * ### Prices arrive; they are never fetched
 *
 * [onQuotes] is the only way the market gets in, and the screen feeds it from the same
 * `MarketDataController` state the market list and the chart read. There is deliberately no gateway
 * here: a second source would let a paper position and the chart above it disagree about one
 * instrument, and the reader would have no way to tell which of the two had lied.
 */
class PaperTradeController(
    private val store: PaperLedgerStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * The Room table this feature used before it had a book of its own.
     *
     * Read once, on first run, and never written. See [PaperMigration] — a reader who took forty
     * paper trades under the old screen keeps them, and keeping them costs one query at launch.
     */
    private val legacy: PaperTradeDao? = null,
) {

    /**
     * The constructor the app has always called.
     *
     * Kept so that adding a real store is a one-line change in dependency injection rather than a
     * change that has to land in the same commit as this module. What it costs until that line is
     * written is stated plainly: [InMemoryPaperLedgerStore] does not survive the process, so the
     * account resets when Android kills the app. See `## WIRING NEEDED` in this feature's report.
     */
    constructor(
        dao: PaperTradeDao,
        scope: CoroutineScope,
        now: () -> Long = System::currentTimeMillis,
    ) : this(InMemoryPaperLedgerStore(), scope, now, dao)

    private val mutable = MutableStateFlow(PaperTradeUiState())
    val state: StateFlow<PaperTradeUiState> = mutable.asStateFlow()

    /** The last thing written, so an unchanged book is not written again. */
    private var stored: String? = null

    init {
        scope.launch {
            val raw = store.text.first()
            stored = raw
            val book = if (raw == null) {
                val rows = runCatching { legacy?.trades()?.first() }.getOrNull().orEmpty()
                PaperMigration.fromLegacy(rows, PaperRules(), now())
            } else {
                PaperBookCodec.decode(raw, PaperBook(account = PaperAccount(openedAtEpochMillis = now())))
            }
            mutable.value = mutable.value.copy(book = book, loaded = true)
            // Written straight back on an import so the migration happens once. Without it every
            // launch would re-read the Room table and re-derive a book the reader has since traded.
            if (raw == null) persist(book)
        }
    }

    /* ------------------------------------------------------------------------------- the market */

    /**
     * One observation of every symbol the book cares about.
     *
     * Idempotent: passing the same prices twice fills nothing twice, because every fill removes the
     * order or position that caused it. Passing a stale quote fills nothing at all.
     */
    fun onQuotes(quotes: Map<String, PaperQuote>) {
        val current = mutable.value
        if (!current.loaded) {
            mutable.value = current.copy(quotes = current.quotes + quotes)
            return
        }
        val merged = current.quotes + quotes
        val next = PaperEngine.observe(current.book, merged, now())
        mutable.value = current.copy(book = next, quotes = merged)
        persist(next)
    }

    /* ------------------------------------------------------------------------------ the commands */

    fun place(request: PaperOrderRequest) = mutate { book, quotes ->
        PaperEngine.place(book, request, quotes, now())
    }

    /**
     * A market order, which is what most of the app's entry points mean by "take this trade".
     *
     * Separate from [place] so a caller with a setup in hand — the chart's, a signal's — does not
     * have to build a request to say the simplest possible thing.
     */
    fun market(
        symbol: String,
        side: PaperSide,
        size: Double,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
    ) = place(
        PaperOrderRequest(
            symbol = symbol,
            side = side,
            type = PaperOrderType.MARKET,
            size = size,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
        ),
    )

    /**
     * Open at the price the reader is looking at, from a screen that has one and no book.
     *
     * This is the chart's entry point and it keeps the signature it has always had, so nothing
     * outside this module has to change to keep working. What changed underneath is that the price
     * is now an *observation* rather than a fill: the spread and the slippage rules apply to it
     * exactly as they do to a price from the feed, because a market order taken from a chart is
     * still a market order. A reader who takes the same setup twice and gets 2,648.30 rather than
     * 2,648.00 is learning something true.
     *
     * A live quote for the symbol wins where there is one, so this and the market list cannot
     * disagree; the passed price is the fallback for a symbol the feed is not carrying.
     */
    fun open(symbol: String, buy: Boolean, price: Double, size: Double) {
        val ticker = PaperEngine.normalise(symbol)
        if (ticker.isEmpty() || !price.isFinite() || price <= 0.0) return
        val live = mutable.value.quotes[ticker]?.takeIf { it.fillable }
        val quote = live ?: PaperQuote(ticker, price, stale = false, atEpochMillis = now())
        mutate { book, quotes ->
            PaperEngine.place(
                book,
                PaperOrderRequest(ticker, if (buy) PaperSide.BUY else PaperSide.SELL, PaperOrderType.MARKET, size),
                quotes + (ticker to quote),
                now(),
            )
        }
    }

    fun cancel(orderId: Long) = mutate { book, _ -> PaperEngine.cancel(book, orderId, now()) }

    fun amend(orderId: Long, limitPrice: Double? = null, stopPrice: Double? = null, size: Double? = null) =
        mutate { book, _ -> PaperEngine.amend(book, orderId, limitPrice, stopPrice, size) }

    fun setProtection(positionId: Long, stopLoss: Double?, takeProfit: Double?) =
        mutate { book, _ -> PaperEngine.setProtection(book, positionId, stopLoss, takeProfit) }

    fun closePosition(positionId: Long, fraction: Double = 1.0) = mutate { book, quotes ->
        PaperEngine.closePosition(book, positionId, fraction, quotes, now())
    }

    fun closeAll() = mutate { book, quotes -> PaperEngine.closeAll(book, quotes, now()) }

    fun reverse(positionId: Long) = mutate { book, quotes ->
        PaperEngine.reverse(book, positionId, quotes, now())
    }

    fun applyRules(rules: PaperRules) = mutate { book, _ -> PaperEngine.applyRules(book, rules) }

    /** Start again on a fresh balance, keeping every closed trade. */
    fun reset(startingBalance: Double? = null) = mutate { book, _ ->
        val rules = startingBalance?.let { book.rules.copy(startingBalance = it) } ?: book.rules
        PaperEngine.reset(book, rules, now())
    }

    /** Start again and delete the record with it. Only ever from a confirmation. */
    fun wipe() = mutate { book, _ -> PaperEngine.wipe(book.rules, now()) }

    /** The old screen's word for [wipe]. Kept so no existing caller loses its meaning. */
    fun clear() = wipe()

    private fun mutate(transform: (PaperBook, Map<String, PaperQuote>) -> PaperBook) {
        val current = mutable.value
        if (!current.loaded) return
        val next = transform(current.book, current.quotes)
        if (next === current.book) return
        mutable.value = current.copy(book = next)
        persist(next)
    }

    /**
     * Write, but only where the stored form actually differs.
     *
     * The comparison is on the encoded text rather than on the book, and that is what makes a tick
     * cheap: an observation that only moved [PaperOrder.lastSeenPrice] encodes to the same string —
     * that field is deliberately not written — so it costs one encode and no disk at all.
     */
    private fun persist(book: PaperBook) {
        val encoded = PaperBookCodec.encode(book)
        if (encoded == stored) return
        stored = encoded
        scope.launch { store.save(encoded) }
    }
}
