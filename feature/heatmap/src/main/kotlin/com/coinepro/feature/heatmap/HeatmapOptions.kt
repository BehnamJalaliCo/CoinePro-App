package com.coinepro.feature.heatmap

/**
 * What decides how much room a market gets on the map.
 *
 * Area is the map's loudest signal — it is read before any colour — so the choice here is a choice
 * about what the reader is being told is important.
 *
 * ### Why capitalisation is not one of the choices any more
 *
 * There used to be a fifth member, `MARKET_CAP`, and it was the default. Nothing ever wrote it.
 * Neither backend has a capitalisation field anywhere in its surface — not on the snapshot, not on
 * the candle route, not on the catalogue — so every map drawn under it silently fell through to
 * [LIQUIDITY]'s ranking while telling the reader it was showing capitalisation. A control that
 * names a quantity the app cannot obtain is worse than no control: the reader trusts the label and
 * reads a different map from the one they asked for. It is gone rather than disabled, and
 * [LIQUIDITY] is what that fallback was all along, now under its own name.
 */
enum class HeatmapSize {
    /**
     * The app's own offline liquidity ranking, named honestly.
     *
     * Coarse — it knows that Bitcoin outweighs a mid-cap alt, not by how much — and it needs no
     * network at all, which is why it is the default: it is the one sizing that is complete the
     * instant the catalogue lands, before a single daily bar has been fetched.
     */
    LIQUIDITY,

    /** Traded quantity over the last daily bar, in units of the base asset. */
    VOLUME,

    /** Quantity times price — money moved rather than units moved. */
    TURNOVER,

    /**
     * Every tile the same size.
     *
     * Not a filler option. With equal areas the map becomes a pure colour field, which is the right
     * shape for the question "what is happening across the board" — on a weighted map that question
     * is drowned out by whichever three markets are largest, and a reader watching for a sector
     * turning cannot see it happen in the small tiles.
     */
    MONO,
}

/**
 * What the tile's colour means.
 *
 * All five map onto the same diverging ramp, so the reader learns one scale and not five. What
 * changes is the quantity being placed on it, and each one is a different question about the same
 * market — where it closed, how it did over the window, whether it is unusually agitated, where in
 * the day's range it is sitting, and whether it opened away from where it closed.
 *
 * Every one of them is derived from the market's own daily bars by [HeatmapFacts], because the
 * quote either backend sends carries a price and nothing else. Without bars all five answer null
 * and the map draws as unknown rather than as flat — see [HeatmapColours.unknown].
 */
enum class HeatmapColour {
    /** Percentage change against the previous daily close. The default, and what a heatmap means. */
    CHANGE,

    /**
     * Percentage change over the window named by [HeatmapPeriod].
     *
     * This used to fall back to [CHANGE] when no period figure was available, which meant a reader
     * who selected "three months" could be shown today's move under a three-month label with
     * nothing on screen saying so. It answers null now: a mode that cannot answer its own question
     * says so.
     */
    PERFORMANCE,

    /**
     * How agitated the market is against its *own* normal, not against its neighbours'.
     *
     * A raw volatility figure cannot go on a diverging ramp: it has no sign, so half the scale
     * would be unreachable and every tile would read as a gain. What is plotted instead is the
     * excess over the instrument's typical daily range, which does have a sign and does answer the
     * question a reader is asking — is this market calmer or wilder than it usually is.
     */
    VOLATILITY,

    /**
     * Where the last price sits inside the session's own high-low range, from the low at one
     * extreme to the high at the other.
     *
     * Worth its own mode because it disagrees with [CHANGE] often enough to be informative: a
     * market up two percent but trading at the bottom of its range has given most of the day back.
     */
    RANGE,

    /**
     * The opening gap against the previous close.
     *
     * On a coin this is almost always near zero, because crypto does not close; on a currency pair,
     * a metal or an index it is the weekend, and it is one of the few figures a reader cannot get
     * from the price alone. Both are true answers, and a map of near-zero gaps across the crypto
     * block beside a real gap on gold is itself the information.
     */
    GAP,
}

/**
 * The window [HeatmapColour.PERFORMANCE] measures over.
 *
 * Counted in daily bars rather than in calendar days, which is the same thing for a coin and
 * deliberately not the same thing for a currency pair: a forex market has no Saturday bar, so
 * "thirty bars back" is six weeks of calendar and is nonetheless the right reference, because the
 * days in between had no trading to measure.
 */
enum class HeatmapPeriod(val bars: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
}

/**
 * The three colour schemes.
 *
 * [COLOUR_BLIND] is the reason this enum exists rather than a boolean. Red-green colour vision
 * deficiency affects roughly one man in twelve, and a market heatmap is the single worst object in
 * a trading app for it: the entire content is encoded in the one axis those readers cannot resolve,
 * with no numbers large enough to fall back on. Every serious terminal ships an alternative and
 * almost no clone does, which is exactly why it is here.
 */
enum class HeatmapPalette {
    /**
     * The product's own market up and down colours.
     *
     * These follow the reader's buy/sell direction preference: the theme implements the red-up
     * convention by exchanging `buy` and `sell` on the palette, so the map has to ask which way
     * round the reader is before it decides which end of its ramp is green. See
     * [HeatmapColours.colourFor].
     */
    CLASSIC,

    /**
     * A blue-to-orange divergent ramp that does not use the red-green axis at all.
     *
     * Blue for a rise and orange for a fall, with the blue channel moving monotonically from one
     * end of the ramp to the other. That last property is the one that matters and the one the
     * tests assert: a scheme whose two halves differ only in their red and green channels is
     * indistinguishable to a deuteranope no matter which hues it names, so the ramp is built so
     * that even a reader who sees no red-green difference still reads a rise as lighter and bluer
     * and a fall as darker and warmer.
     */
    COLOUR_BLIND,

    /**
     * Value as lightness alone, from near-black through mid-grey to near-white.
     *
     * For a reader who wants the shape of the market without any colour argument at all, and for a
     * screenshot that has to survive being printed.
     */
    MONOCHROME,
}

/**
 * Whether markets of different kinds are kept apart, and by what.
 *
 * Grouping costs area — each block needs a strip for its name — and it used to be off by default
 * for that reason. It is on now, because [HeatmapDensity] caps how many tiles a map draws: at a
 * hundred and forty tiles the blocks are tall enough to give a strip up without the tiles under
 * them becoming unreadable, and an ungrouped map of that size interleaves coins with currency
 * pairs so thoroughly that a reader cannot see one whole class turn.
 *
 * A group is also the drill-down: tapping a block's name strip focuses that block, which is how a
 * reader reaches the markets the density cap left off the map.
 */
enum class HeatmapGrouping {
    NONE,

    /** By the class [com.coinepro.core.symbols.SymbolClassifier] derives from the ticker. */
    BY_CLASS,

    /**
     * By what the market is priced in — USDT, USD, JPY.
     *
     * A different question from the class and a better one on a day when a quote currency is
     * itself moving: every USD-quoted pair falling together is a dollar story, not forty separate
     * ones, and only this grouping shows it as one block.
     */
    BY_QUOTE,
}

/**
 * How many markets the map draws at once.
 *
 * ### The problem this exists to answer
 *
 * The catalogue runs to several hundred markets. A treemap of four hundred tiles on a phone is four
 * hundred rectangles averaging under a fifth of a square centimetre: no ticker fits in one, no
 * figure fits in one, and no thumb can hit the one it aimed at. What shipped looked like a wall of
 * names precisely because it drew every market it was handed.
 *
 * So the map draws the largest [tiles] by whatever the current sizing is, and the reader reaches
 * the rest by grouping and focusing rather than by squinting. [EVERYTHING] is kept because a
 * reader on a tablet, or one who has filtered to a single class, genuinely can use it — but it is
 * not the default, and choosing it is a choice rather than an accident.
 */
enum class HeatmapDensity(val tiles: Int?) {
    /** Big tiles, every one of them labelled with its ticker and its figure. */
    FOCUSED(48),

    /** The default. Most tiles carry a ticker; the smallest carry colour alone. */
    STANDARD(144),

    /** No cap. Honest about what it costs: below a certain size a tile is a coloured pixel. */
    EVERYTHING(null),
}

/**
 * The six choices the settings sheet writes and the map reads.
 *
 * One immutable object rather than six pieces of state because the plan is rebuilt whenever any of
 * them changes, and a single key makes that `remember` correct by construction instead of correct
 * if somebody remembered to list all six.
 */
data class HeatmapOptions(
    val size: HeatmapSize = HeatmapSize.LIQUIDITY,
    val colour: HeatmapColour = HeatmapColour.CHANGE,
    val period: HeatmapPeriod = HeatmapPeriod.MONTH,
    val palette: HeatmapPalette = HeatmapPalette.CLASSIC,
    val grouping: HeatmapGrouping = HeatmapGrouping.BY_CLASS,
    val density: HeatmapDensity = HeatmapDensity.STANDARD,
)
