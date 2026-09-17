package com.coinepro.core.symbols

/**
 * Whether a venue will let a reader trade this market right now.
 *
 * [UNKNOWN] is the honest answer for a market nobody said anything about, and it is treated as
 * tradable: refusing a chart because a field was absent would hide most of the universe on a venue
 * that does not send the field at all.
 */
enum class SymbolStatus {
    TRADING,
    PAUSED,
    DELISTED,
    UNKNOWN,
}

/**
 * One market in the universe, with everything a list, a filter and a search need.
 *
 * [SymbolMeta] answers «what is this instrument called and what kind of thing is it», which is what
 * a chart needs. A markets screen needs more: which venue quotes it, what it turned over yesterday,
 * whether it is still trading, and which ticker to draw the logo from. Those are here rather than on
 * `SymbolMeta` because they are **facts about a listing**, they change through the day, and a chart
 * that re-rendered every time a turnover figure moved would be a chart nobody could read.
 *
 * ### Identity
 *
 * [id] is the venue's own spelling and the only thing that goes back on the wire. Everything else is
 * presentation, derived or sent alongside. Two venues quoting the same instrument produce two rows
 * with two ids, which is correct — the prices differ.
 *
 * ### The logo legs
 *
 * [logoBase] and [logoQuote] name the tickers whose artwork draws the row, which is not always the
 * pair's own legs: `WBTCUSDT` draws Bitcoin's mark, and a pair draws two. They are nullable and a
 * null is not a problem — a market with no artwork is drawn as a monogram and **is still listed**.
 * See `SymbolArtwork` for how that rule changed and why.
 */
data class UniverseSymbol(
    val id: String,
    val base: String?,
    val quote: String?,
    val venue: String,
    val type: SymbolCategory,
    val displayNameEn: String,
    val displayNameFa: String,
    val logoBase: String?,
    val logoQuote: String?,
    val tickSize: Double?,
    val minQty: Double?,
    val status: SymbolStatus,
    val turnover24h: Double?,
) {

    /** The instrument as the rest of the app knows it. */
    val meta: SymbolMeta by lazy {
        SymbolClassifier.classify(id).copy(
            description = displayNameFa,
            descriptionEn = displayNameEn,
        )
    }

    /** The name in the language the screen is in. */
    fun displayName(english: Boolean): String = if (english) displayNameEn else displayNameFa

    /** The pair as a terminal writes it — `BTC/USDT`, `EUR/USD` — or the whole id where it has no legs. */
    val pretty: String get() = if (base != null && quote != null) "$base/$quote" else id

    /**
     * Whether a reader can act on this row.
     *
     * [SymbolStatus.UNKNOWN] counts, because most venues in this app's scope send no status at all
     * and the alternative is an empty screen.
     */
    val tradable: Boolean get() = status == SymbolStatus.TRADING || status == SymbolStatus.UNKNOWN
}

/**
 * The whole universe, and the four things a markets screen does to it.
 *
 * This object holds no state. It is arithmetic over lists — merge, rank, search, filter — so that
 * the list on screen, the list a search covers and the list a filter sheet counts are provably the
 * same list, ordered four ways.
 *
 * ### Why it does not filter on artwork
 *
 * It used to, everywhere. `SymbolArtwork.covers` was the rule at the catalogue and at the live feed
 * alike, and the reasoning was sound as far as it went: a grey disc with a «D» in it beside forty
 * real logos reads as a broken image rather than as Dogecoin.
 *
 * The owner has overruled it, and the measurement is why. LBank lists 1 333 tether pairs; this
 * repository holds a mark for 178 of them. The filter was not trimming a long tail nobody asked
 * for — it was **hiding seven markets in eight**, in a product whose subject is the list of markets.
 * A monogram whose colour is a stable function of the ticker is a weaker row than a real logo and a
 * far better answer than no row: the reader can find the market, chart it, alert on it and search
 * for it, which is everything except recognising it at a glance.
 *
 * So artwork decides how a row is *drawn* and never whether it exists. `SymbolArtwork.covers` is
 * still the question «do we have a mark», asked by the thing that draws.
 */
object SymbolUniverse {

    /** What the bundled table calls itself when it is the only thing that answered. */
    const val BUNDLED_VENUE: String = "bundled"

    /**
     * Build a universe row from an instrument this app has already classified.
     *
     * The path every live response takes: a venue sends tickers and prices, the classifier says what
     * they are, and this adds the listing's own facts on top.
     */
    fun of(
        meta: SymbolMeta,
        venue: String,
        turnover24h: Double? = null,
        tickSize: Double? = null,
        minQty: Double? = null,
        status: SymbolStatus = SymbolStatus.UNKNOWN,
    ): UniverseSymbol = UniverseSymbol(
        id = meta.symbol,
        base = meta.base,
        quote = meta.quote,
        venue = venue,
        type = meta.category,
        displayNameEn = meta.descriptionEn,
        displayNameFa = meta.description,
        logoBase = meta.base,
        logoQuote = meta.quote,
        tickSize = tickSize,
        minQty = minQty,
        status = status,
        turnover24h = turnover24h,
    )

    /**
     * The floor: every market in [BundledUniverse], as universe rows.
     *
     * Their venue is [BUNDLED_VENUE] rather than a real one, and that is deliberate — a row the app
     * is carrying rather than one a venue quoted is a different fact, the filter sheet can say so,
     * and `merge` can drop it the moment the real thing arrives.
     */
    fun bundled(): List<UniverseSymbol> {
        val crypto = BundledUniverse.CRYPTO.map { row ->
            val id = row.base + BundledUniverse.QUOTE
            UniverseSymbol(
                id = id,
                base = row.base,
                quote = BundledUniverse.QUOTE,
                venue = BUNDLED_VENUE,
                type = SymbolCategory.CRYPTO,
                displayNameEn = row.nameEn,
                displayNameFa = row.nameFa.ifEmpty { row.nameEn },
                logoBase = row.base,
                logoQuote = BundledUniverse.QUOTE,
                tickSize = null,
                minQty = null,
                status = SymbolStatus.UNKNOWN,
                turnover24h = null,
            )
        }
        val other = BundledUniverse.FOREX.map { symbol ->
            of(SymbolClassifier.classify(symbol), BUNDLED_VENUE)
        }
        return crypto + other
    }

    /**
     * Live rows over bundled ones, keyed on the id.
     *
     * A venue's own row always wins: it has the price, the turnover and the status, and the bundled
     * row has a name and a rank. The bundled rows that survive are the markets the venue did not
     * mention, which is the whole point of carrying them — a reader can still search for Solana on a
     * morning when the venue is answering with nineteen symbols.
     */
    fun merge(live: List<UniverseSymbol>, floor: List<UniverseSymbol> = bundled()): List<UniverseSymbol> {
        if (live.isEmpty()) return floor
        val known = live.mapTo(HashSet(live.size)) { it.id.uppercase() }
        return live + floor.filterNot { it.id.uppercase() in known }
    }

    /**
     * Most traded first, and where nothing is traded, the majors in order.
     *
     * Turnover beats everything that has it; a market with no figure sorts behind every market that
     * has one rather than being read as zero, for the reason `SymbolRanking.byLiquidity` gives —
     * zero is a fabricated number and it would sink the whole non-crypto side, which reports none.
     */
    fun ranked(symbols: List<UniverseSymbol>): List<UniverseSymbol> = symbols.sortedWith { a, b ->
        SymbolRanking.byLiquidity(a.turnover24h, b.turnover24h, a.id, b.id)
    }

    /**
     * One listing per asset, preferring the quote the reader asked for (run ΤΦΥ, U2).
     *
     * An asset quoted against several units — `BTC/USDT` and `BTC/USDC` on the same book — is one
     * market to a reader and two rows to a venue. This keeps the one they said they think in, and
     * where the asset is not quoted in it at all, keeps whatever the venue does quote. A reader who
     * chose rial does not lose a list; they get the venue's own units, which is the only honest
     * answer when no rial market exists.
     *
     * It is a **listing preference and never a conversion**: no figure is re-denominated, because a
     * price that quietly changed units is the worst thing a market screen can do. See
     * `QuoteCurrency`.
     *
     * An empty or unknown quote is «no opinion» and returns the list untouched, which is also what
     * every asset with one listing gets — and today that is nearly all of them, because both venues
     * quote essentially their whole book against one unit.
     */
    fun preferring(symbols: List<UniverseSymbol>, quote: String?): List<UniverseSymbol> {
        val wanted = quote?.trim()?.uppercase().orEmpty()
        if (wanted.isEmpty()) return symbols
        // Grouped by base *and venue*: the same ticker on two books is two markets, and collapsing
        // them would hide one venue's price behind another's.
        val duplicated = symbols
            .groupBy { (it.base?.uppercase() ?: it.id.uppercase()) to it.venue }
            .filterValues { it.size > 1 }
        if (duplicated.isEmpty()) return symbols
        val dropped = duplicated.values.flatMapTo(HashSet()) { group ->
            val keep = group.firstOrNull { it.quote?.uppercase() == wanted } ?: return@flatMapTo emptyList()
            group.filterNot { it === keep }.map { it.id }
        }
        return if (dropped.isEmpty()) symbols else symbols.filterNot { it.id in dropped }
    }

    /**
     * Search the **whole** universe, ranked by how well it matched and then by turnover.
     *
     * Not by `SymbolRanking` alone, which is what `SymbolSearch` falls back to: a universe with live
     * turnover in it knows better than a hand-written list of majors which of two equally good
     * matches a reader meant.
     */
    fun search(symbols: List<UniverseSymbol>, query: String): List<UniverseSymbol> {
        val byId = symbols.associateBy { it.id }
        val needle = query.trim()
        if (needle.isEmpty()) return ranked(symbols)
        return SymbolSearch.search(symbols.map { it.meta }, needle)
            .mapNotNull { match -> byId[match.meta.symbol]?.let { match.score to it } }
            .sortedWith(
                compareByDescending<Pair<Int, UniverseSymbol>> { it.first }
                    .thenComparator { a, b ->
                        SymbolRanking.byLiquidity(a.second.turnover24h, b.second.turnover24h, a.second.id, b.second.id)
                    },
            )
            .map { it.second }
    }

    /**
     * What a filter sheet can narrow by.
     *
     * Every field is «no opinion» by default, and an empty set means the same thing as null: a
     * filter nobody has touched must not remove a row. A change band is in **per cent**, which is
     * the unit on screen, and it is applied against a figure the caller supplies rather than one
     * carried here — the change comes from the price feed and a universe row is a listing.
     */
    data class Filter(
        val types: Set<SymbolCategory> = emptySet(),
        val venues: Set<String> = emptySet(),
        val changeFromPercent: Double? = null,
        val changeToPercent: Double? = null,
        val minTurnover: Double? = null,
    ) {
        val isEmpty: Boolean
            get() = types.isEmpty() && venues.isEmpty() &&
                changeFromPercent == null && changeToPercent == null && minTurnover == null
    }

    /**
     * Apply [filter], reading each row's 24-hour change through [changeOf].
     *
     * A row whose change is unknown passes a change band rather than failing it. The band is a
     * reader narrowing the list, not asking for rows the app happens to have a figure for, and
     * dropping the unknowns would silently empty the non-crypto side.
     */
    fun filter(
        symbols: List<UniverseSymbol>,
        filter: Filter,
        changeOf: (UniverseSymbol) -> Double? = { null },
    ): List<UniverseSymbol> {
        if (filter.isEmpty) return symbols
        return symbols.filter { symbol ->
            if (filter.types.isNotEmpty() && symbol.type !in filter.types) return@filter false
            if (filter.venues.isNotEmpty() && symbol.venue !in filter.venues) return@filter false
            filter.minTurnover?.let { floor ->
                val turnover = symbol.turnover24h
                if (turnover == null || turnover < floor) return@filter false
            }
            val change = changeOf(symbol)
            if (change != null) {
                filter.changeFromPercent?.let { if (change < it) return@filter false }
                filter.changeToPercent?.let { if (change > it) return@filter false }
            }
            true
        }
    }

    /**
     * One page of [size] rows starting at [offset], for a list that loads as it is scrolled.
     *
     * A method rather than a `subList` at the call site, because the two edges — an offset past the
     * end and a page that runs off it — are exactly where an infinite list throws, and they are
     * worth writing down once.
     */
    fun page(symbols: List<UniverseSymbol>, offset: Int, size: Int): List<UniverseSymbol> {
        if (offset <= 0 && size >= symbols.size) return symbols
        val from = offset.coerceIn(0, symbols.size)
        val to = (from + size.coerceAtLeast(0)).coerceAtMost(symbols.size)
        return symbols.subList(from, to)
    }

    /** How many pages of [size] the universe holds. */
    fun pages(symbols: List<UniverseSymbol>, size: Int): Int =
        if (size <= 0) 0 else (symbols.size + size - 1) / size

    /** The page size the markets list loads in. F2's number, and the one `page` is called with. */
    const val PAGE: Int = 200
}
