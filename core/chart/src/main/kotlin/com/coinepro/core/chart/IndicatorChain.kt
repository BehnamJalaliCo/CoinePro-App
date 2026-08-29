package com.coinepro.core.chart

/**
 * Which column of the bars an indicator reads when its source is the candles themselves.
 *
 * The four raw columns plus the three averages every terminal offers, because "EMA of hl2" is a
 * setting readers arrive with rather than something this app invented. Nothing here reads volume:
 * a volume column is a different quantity, not another price, and an average of it drawn on the
 * price axis is a line at a number that is not a price.
 */
enum class BarField {
    /** The bar's open. */
    OPEN,

    /** The bar's high. */
    HIGH,

    /** The bar's low. */
    LOW,

    /** The bar's close, and the default everywhere — every published indicator formula assumes it. */
    CLOSE,

    /** `(h + l) / 2`, the bar's mid. */
    HL2,

    /** `(h + l + c) / 3`, the "typical price" of the CCI family. */
    HLC3,

    /** `(o + h + l + c) / 4`, the bar's average. */
    OHLC4;

    /**
     * This column as an array aligned to [series].
     *
     * The three averages are computed here rather than cached on [CandleSeries], because they are
     * asked for only by a chain that names them and building three more columns for every series
     * on the chance one is wanted is the wrong trade.
     */
    fun of(series: CandleSeries): DoubleArray = when (this) {
        OPEN -> series.open
        HIGH -> series.high
        LOW -> series.low
        CLOSE -> series.close
        HL2 -> DoubleArray(series.size) { (series.high[it] + series.low[it]) / 2 }
        HLC3 -> DoubleArray(series.size) { (series.high[it] + series.low[it] + series.close[it]) / 3 }
        OHLC4 -> DoubleArray(series.size) {
            (series.open[it] + series.high[it] + series.low[it] + series.close[it]) / 4
        }
    }
}

/**
 * Where one indicator gets the numbers it works on.
 *
 * The whole of item 80 is this type existing: until it did, every indicator in this module read the
 * candle series and nothing else, and «RSI of a smoothed price» — the thing half of published
 * strategies are built on — could not be expressed at all.
 */
sealed interface IndicatorSource {

    /** The candle series, through one of its columns. */
    data class Bars(val field: BarField = BarField.CLOSE) : IndicatorSource

    /**
     * Another active indicator's output, named.
     *
     * [output] is null for "whatever that indicator's main line is", which is what a reader means
     * nine times in ten and what keeps a saved template short. It is named explicitly when the
     * producer publishes more than one series and the reader wants the other one — the signal line
     * of a MACD, the %D of a Stochastic RSI.
     */
    data class Output(val nodeId: String, val output: String? = null) : IndicatorSource

    companion object {
        /** The default source: the close. */
        val CANDLES: IndicatorSource = Bars(BarField.CLOSE)
    }
}

/**
 * One indicator as it is actually switched on: which one, at what lookback, reading what.
 *
 * [nodeId] rather than the indicator id alone, because the point of a chain is that the same
 * indicator appears twice — an EMA of the price and an EMA of *that* are two nodes with one
 * `indicatorId`, and a map keyed by the indicator would collapse them into one.
 *
 * [colour] is the reader's override; null takes the catalogue's colour for the indicator, which is
 * the right default until two nodes share it.
 */
data class ChainedIndicator(
    val nodeId: String,
    val indicatorId: String,
    val period: Int? = null,
    val source: IndicatorSource = IndicatorSource.CANDLES,
    val colour: Long? = null,
)

/**
 * An indicator that can appear in a chain, and what it publishes.
 *
 * [outputs] is ordered and the first entry is the main line — the one a source that names no output
 * gets. [takesPeriod] is false for the ones defined by a fixed set of periods rather than a single
 * lookback (MACD's 12/26/9, Connors RSI's 3/2/100): they are still chainable, they simply ignore a
 * period a reader tries to set, and the picker should show them no stepper.
 */
data class ChainableIndicator(
    val id: String,
    val outputs: List<String>,
    val takesPeriod: Boolean,
    val defaultPeriod: Int,
)

/** Why a chain was refused. Each one is a different mistake and gets its own sentence. */
enum class ChainRefusal {
    /** Two nodes share a [ChainedIndicator.nodeId]. */
    DUPLICATE_NODE,

    /** A node names an indicator this build does not have. */
    UNKNOWN_INDICATOR,

    /** A node reads an output of a node that is not in the list. */
    MISSING_SOURCE,

    /** A node reads an output name its producer does not publish. */
    UNKNOWN_OUTPUT,

    /** Either end of the link reads more than one column, so it cannot be chained. */
    NOT_CHAINABLE,

    /** The chain loops: A reads B reads A. */
    CYCLE,

    /** The chain is longer than [IndicatorChain.MAX_DEPTH]. */
    TOO_DEEP,
}

/** What came back from [IndicatorChain.evaluate]: values to draw, or a refusal with a reason. */
sealed interface ChainOutcome {

    /**
     * Every chained node evaluated, in the order it was evaluated.
     *
     * [order] is the record of the walk and not merely a sorted list: a node appears in it exactly
     * once no matter how many others read it, which is the diamond case — two indicators on one
     * base — computing the base once rather than twice.
     *
     * [unchained] names the nodes this engine deliberately did not touch: indicators that read the
     * bars directly and are not in [IndicatorChain.CHAINABLE]. They are not an error and not a
     * refusal; the caller draws them through [ChartCatalog] exactly as before.
     */
    data class Ready(
        val outputs: Map<String, Map<String, DoubleArray>>,
        val order: List<String>,
        val unchained: List<String>,
    ) : ChainOutcome

    /**
     * The chain was not evaluated, and [message] says why in a sentence a reader can act on.
     *
     * A refusal rather than a best effort. A chain with a loop in it has no answer at all, and the
     * two available wrong moves — recursing until the stack goes, or quietly dropping the link and
     * drawing an indicator that reads something the reader did not choose — are both worse than
     * saying so.
     */
    data class Refused(
        val reason: ChainRefusal,
        val nodeIds: List<String>,
        val message: String,
    ) : ChainOutcome
}

/**
 * What a chain draws: lines on the price axis, and panes of their own.
 *
 * Two lists rather than one, for the reason [ChartPane] exists at all — see the note there about an
 * RSI reading 0–100 against a gold price of 2,600.
 */
data class ChainPlot(
    val priceLines: List<ChartLine> = emptyList(),
    val panes: List<ChartPane> = emptyList(),
)

/**
 * Indicators that read other indicators.
 *
 * ### What this is for
 *
 * TradingView lets an indicator take another indicator's output as its source and gates the depth
 * by tier: one link on the free plan, forty-nine on the most expensive one. **This app caps the
 * chain at [MAX_DEPTH] and charges nothing for it.** Ten is the depth TradingView enforces on top
 * of the tier limit anyway, and it is also the honest ceiling: past ten links nobody — including
 * the person who built the chain — can say what the last line is measuring.
 *
 * ### What cannot be chained, and why
 *
 * [CHAINABLE] holds every indicator that is arithmetic on **one** series. Everything else is
 * absent, and that is a correctness rule rather than an unfinished list:
 *
 * * ATR, ADX, DMI, Stochastic, CCI, Williams %R, Aroon, Keltner, Donchian, Ichimoku, SuperTrend,
 *   Parabolic SAR, the Alligator, Chande-Kroll, the volatility stop, the Mass Index, the Awesome
 *   and Accelerator oscillators, the Ultimate Oscillator, the Fisher transform and Choppiness all
 *   read **high and low**, and several read the open as well. A "source" for them is not one array,
 *   it is three or four.
 * * VWAP, OBV, MFI, CMF, PVO, VWMA, the A/D line, the Chaikin family, Ease of Movement, the Force
 *   Index, Klinger, PVT, net volume and the volume profile read **volume**.
 * * The structure studies — pivots, swings, fractals, zigzag, auto-Fibonacci, support/resistance,
 *   supply and demand, the chop zone — do not produce a value per bar at all.
 * * `correlation` reads a second instrument, which is not an output of anything on this chart.
 *
 * Offering those a source picker would mean feeding them a single series and silently using it as
 * the close while the high, low and volume stayed the real ones — an indicator that looks computed
 * and is not the indicator it is labelled. So they take the bars, and only the bars.
 *
 * ### The warm-up trap
 *
 * An indicator's first values are absent, and in this pack absence travels as `Double.NaN`. Feeding
 * that straight into a running-sum average — which is what an SMA is — poisons the sum for the rest
 * of the series: one NaN goes in, and every value after it is NaN even once the NaN has left the
 * window. So [evaluate] computes each link over the source's finite tail and pads the head back
 * afterwards. A gap in the *middle* of a source is not repaired, and cannot honestly be: the values
 * after it are NaN, because there is no value there to average.
 */
object IndicatorChain {

    /**
     * The deepest chain this app evaluates.
     *
     * Ten, free, for everybody. Past this a chain is refused rather than truncated — a truncated
     * chain draws a line that is missing a link the reader can still see in the picker.
     */
    const val MAX_DEPTH = 10

    /**
     * The names an indicator publishes its series under.
     *
     * Strings rather than an enum, and they live here rather than at package scope: a saved
     * template stores the name a reader picked, and a template written by an older build must not
     * fail to parse because an enum constant was renamed. Names inside the object also keep `K` and
     * `D` from becoming package-wide identifiers that nothing else in `core:chart` could use.
     */
    const val VALUE = "value"

    /** The middle line of a band. */
    const val BASIS = "basis"

    /** The top edge of a band. */
    const val UPPER = "upper"

    /** The bottom edge of a band. */
    const val LOWER = "lower"

    /** The averaged companion of an oscillator — MACD's signal line, TRIX's. */
    const val SIGNAL = "signal"

    /** The columns an oscillator draws from zero. */
    const val HISTOGRAM = "histogram"

    /** A stochastic's fast line. */
    const val K = "k"

    /** A stochastic's slow line. */
    const val D = "d"

    /**
     * Every indicator that can take another indicator's output, and what each publishes.
     *
     * The entries are the ones whose maths is a function of one array. See the object KDoc for the
     * ones that are absent and why.
     */
    val CHAINABLE: Map<String, ChainableIndicator> = listOf(
        // Averages and bands on the price scale.
        ChainableIndicator("sma", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("ema", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("wma", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("hma", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("smma", listOf(VALUE), takesPeriod = true, defaultPeriod = 14),
        ChainableIndicator("zlema", listOf(VALUE), takesPeriod = true, defaultPeriod = 21),
        ChainableIndicator("kama", listOf(VALUE), takesPeriod = true, defaultPeriod = 10),
        ChainableIndicator("t3", listOf(VALUE), takesPeriod = true, defaultPeriod = 10),
        ChainableIndicator("mcginley", listOf(VALUE), takesPeriod = true, defaultPeriod = 14),
        ChainableIndicator("linreg", listOf(VALUE), takesPeriod = true, defaultPeriod = 100),
        ChainableIndicator("lsma", listOf(VALUE), takesPeriod = true, defaultPeriod = 25),
        ChainableIndicator("tema", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("dema", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("bollinger", listOf(BASIS, UPPER, LOWER), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("envelopes", listOf(BASIS, UPPER, LOWER), takesPeriod = true, defaultPeriod = 20),

        // Oscillators, each in a pane of its own.
        ChainableIndicator("rsi", listOf(VALUE), takesPeriod = true, defaultPeriod = 14),
        ChainableIndicator("stddev", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("hv", listOf(VALUE), takesPeriod = true, defaultPeriod = 10),
        ChainableIndicator("bbpercent", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("bbw", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("mom", listOf(VALUE), takesPeriod = true, defaultPeriod = 10),
        ChainableIndicator("roc", listOf(VALUE), takesPeriod = true, defaultPeriod = 12),
        ChainableIndicator("dpo", listOf(VALUE), takesPeriod = true, defaultPeriod = 20),
        ChainableIndicator("cmo", listOf(VALUE), takesPeriod = true, defaultPeriod = 9),
        ChainableIndicator("trix", listOf(VALUE, SIGNAL), takesPeriod = true, defaultPeriod = 18),
        ChainableIndicator("stochrsi", listOf(K, D), takesPeriod = true, defaultPeriod = 14),
        ChainableIndicator("tsi", listOf(VALUE, SIGNAL), takesPeriod = true, defaultPeriod = 25),
        // Fixed period sets. Chainable, but a stepper on them moves one third of the tool.
        ChainableIndicator("macd", listOf(VALUE, SIGNAL, HISTOGRAM), takesPeriod = false, defaultPeriod = 12),
        ChainableIndicator("ppo", listOf(VALUE, SIGNAL, HISTOGRAM), takesPeriod = false, defaultPeriod = 12),
        ChainableIndicator("kst", listOf(VALUE, SIGNAL), takesPeriod = false, defaultPeriod = 10),
        ChainableIndicator("smiErgodic", listOf(VALUE, SIGNAL), takesPeriod = false, defaultPeriod = 20),
        ChainableIndicator("crsi", listOf(VALUE), takesPeriod = false, defaultPeriod = 3),
        ChainableIndicator("coppock", listOf(VALUE), takesPeriod = false, defaultPeriod = 10),
    ).associateBy { it.id }

    /** Whether [id] may appear anywhere in a chain, at either end of a link. */
    fun canChain(id: String): Boolean = id in CHAINABLE

    /** What [id] publishes, main line first, or empty for an indicator that cannot be chained. */
    fun outputsOf(id: String): List<String> = CHAINABLE[id]?.outputs ?: emptyList()

    /**
     * The lookback a chained node is actually computed with.
     *
     * The catalogue's bounds where the indicator has an entry there, so a chained EMA and a plain
     * one clamp identically; otherwise the indicator's own default. An indicator whose periods are
     * fixed ignores the argument entirely.
     */
    fun periodFor(id: String, chosen: Int?): Int {
        val spec = CHAINABLE[id] ?: return chosen ?: 0
        if (!spec.takesPeriod) return spec.defaultPeriod
        val bounds = ChartCatalog.periodOf(id)
        val wanted = chosen ?: bounds?.default ?: spec.defaultPeriod
        return wanted.coerceIn(bounds?.min ?: 2, bounds?.max ?: 400)
    }

    /**
     * Resolve the chain and evaluate every node in it, once each.
     *
     * The order of the work is the whole function: the sources are checked first, then the graph is
     * walked for loops and depth, and only then is any arithmetic done. A cycle found halfway
     * through evaluation would already have cost the caller a hundred bars of an average nobody can
     * draw.
     */
    fun evaluate(series: CandleSeries, nodes: List<ChainedIndicator>): ChainOutcome {
        val byId = LinkedHashMap<String, ChainedIndicator>(nodes.size)
        for (node in nodes) {
            if (byId.put(node.nodeId, node) != null) {
                return refused(
                    ChainRefusal.DUPLICATE_NODE,
                    listOf(node.nodeId),
                    "شناسهٔ «${node.nodeId}» دو بار در زنجیره آمده است.",
                )
            }
        }
        for (node in nodes) {
            if (ChartCatalog.INDICATORS.none { it.id == node.indicatorId }) {
                return refused(
                    ChainRefusal.UNKNOWN_INDICATOR,
                    listOf(node.nodeId),
                    "اندیکاتور «${node.indicatorId}» در این نسخه وجود ندارد.",
                )
            }
            val source = node.source
            if (source !is IndicatorSource.Output) continue
            if (!canChain(node.indicatorId)) {
                return refused(
                    ChainRefusal.NOT_CHAINABLE,
                    listOf(node.nodeId),
                    "«${node.indicatorId}» بیش از یک ستون از کندل می‌خواند و نمی‌تواند از اندیکاتور دیگری تغذیه شود.",
                )
            }
            val producer = byId[source.nodeId] ?: return refused(
                ChainRefusal.MISSING_SOURCE,
                listOf(node.nodeId, source.nodeId),
                "منبع «${source.nodeId}» روشن نیست.",
            )
            if (!canChain(producer.indicatorId)) {
                return refused(
                    ChainRefusal.NOT_CHAINABLE,
                    listOf(node.nodeId, producer.nodeId),
                    "«${producer.indicatorId}» یک سری خروجی نمی‌دهد و نمی‌تواند منبع باشد.",
                )
            }
            val output = source.output
            if (output != null && output !in outputsOf(producer.indicatorId)) {
                return refused(
                    ChainRefusal.UNKNOWN_OUTPUT,
                    listOf(node.nodeId, producer.nodeId),
                    "خروجی «$output» در «${producer.indicatorId}» وجود ندارد.",
                )
            }
        }

        val order = ArrayList<String>(nodes.size)
        val depth = HashMap<String, Int>(nodes.size)
        val open = HashSet<String>()
        val done = HashSet<String>()
        val path = ArrayList<String>()

        fun walk(id: String): ChainOutcome.Refused? {
            if (id in done) return null
            if (id in open) {
                val from = path.indexOf(id)
                val loop = path.subList(from, path.size).toList() + id
                return refused(
                    ChainRefusal.CYCLE,
                    loop.distinct(),
                    "زنجیره حلقه دارد: ${loop.joinToString(" ← ")}",
                )
            }
            open += id
            path += id
            val node = byId.getValue(id)
            var links = 1
            val source = node.source
            if (source is IndicatorSource.Output) {
                walk(source.nodeId)?.let { return it }
                links = (depth[source.nodeId] ?: 0) + 1
            }
            path.removeAt(path.size - 1)
            open -= id
            done += id
            depth[id] = links
            if (links > MAX_DEPTH) {
                return refused(
                    ChainRefusal.TOO_DEEP,
                    listOf(id),
                    "زنجیره از ${persianCount(MAX_DEPTH)} اندیکاتور عمیق‌تر شده است.",
                )
            }
            order += id
            return null
        }

        for (node in nodes) walk(node.nodeId)?.let { return it }

        val outputs = LinkedHashMap<String, Map<String, DoubleArray>>()
        val evaluated = ArrayList<String>(order.size)
        val unchained = ArrayList<String>()
        for (id in order) {
            val node = byId.getValue(id)
            if (!canChain(node.indicatorId)) {
                unchained += id
                continue
            }
            val source = node.source
            val values = when (source) {
                is IndicatorSource.Bars -> source.field.of(series)
                is IndicatorSource.Output -> {
                    val produced = outputs[source.nodeId] ?: return refused(
                        ChainRefusal.MISSING_SOURCE,
                        listOf(id, source.nodeId),
                        "منبع «${source.nodeId}» محاسبه نشد.",
                    )
                    val name = source.output ?: outputsOf(byId.getValue(source.nodeId).indicatorId).first()
                    produced[name] ?: return refused(
                        ChainRefusal.UNKNOWN_OUTPUT,
                        listOf(id, source.nodeId),
                        "خروجی «$name» محاسبه نشد.",
                    )
                }
            }
            outputs[id] = compute(node.indicatorId, values, periodFor(node.indicatorId, node.period))
            evaluated += id
        }
        return ChainOutcome.Ready(outputs = outputs, order = evaluated, unchained = unchained)
    }

    /**
     * Turn an evaluated chain into things the renderer draws.
     *
     * ### The trap this function exists to avoid
     *
     * An EMA is a price-scale indicator, so the obvious rule — "draw it where its catalogue entry
     * says" — puts an EMA of an RSI over the candles, where a value of 46 lands somewhere under the
     * floor of a gold chart and drags the price axis down to meet it. A chained indicator belongs in
     * the pane of **the thing it is measuring**, not of the thing it is: an average of an RSI is
     * drawn inside the RSI's pane, and only a chain whose root reads the bars ever reaches the price
     * axis. That is what [paneOwnerOf] walks up to find.
     */
    fun plot(ready: ChainOutcome.Ready, nodes: List<ChainedIndicator>): ChainPlot {
        val byId = nodes.associateBy { it.nodeId }
        val priceLines = ArrayList<ChartLine>()
        val paneLines = LinkedHashMap<String, MutableList<ChartLine>>()
        val paneHistogram = LinkedHashMap<String, ChartLine>()
        for (id in ready.order) {
            val node = byId[id] ?: continue
            val produced = ready.outputs[id] ?: continue
            val owner = paneOwnerOf(node, byId)
            val colour = node.colour
                ?: ChartCatalog.INDICATORS.firstOrNull { it.id == node.indicatorId }?.colour
                ?: FALLBACK_COLOUR
            val primary = outputsOf(node.indicatorId).firstOrNull()
            for ((name, values) in produced) {
                val line = ChartLine(
                    values = values.asLine(),
                    colour = if (name == SIGNAL) shade(colour) else colour,
                    widthDp = if (name == BASIS) 0.9f else 1.2f,
                    label = when (name) {
                        primary -> labelOf(node, byId)
                        SIGNAL -> "سیگنال"
                        else -> null
                    },
                )
                when {
                    name == HISTOGRAM && owner != null -> paneHistogram[owner] = line
                    owner == null -> priceLines += line
                    else -> paneLines.getOrPut(owner) { ArrayList() } += line
                }
            }
        }
        val owners = LinkedHashSet<String>().apply {
            addAll(paneLines.keys)
            addAll(paneHistogram.keys)
        }
        val panes = owners.mapNotNull { ownerId ->
            val owner = byId[ownerId] ?: return@mapNotNull null
            ChartPane(
                title = labelOf(owner, byId),
                lines = paneLines[ownerId].orEmpty(),
                levels = referenceLevels(owner.indicatorId),
                histogram = paneHistogram[ownerId],
            )
        }
        return ChainPlot(priceLines = priceLines, panes = panes)
    }

    /**
     * What a node is called on screen: its own name, then what it is reading.
     *
     * «EMA 20 روی RSI 14» rather than an arrow or a bracket, because the label sits in a Persian
     * right-to-left legend and a chain of Latin tickers joined by punctuation reorders itself there
     * into something that reads backwards. A Persian word between them fixes the direction of the
     * sentence and says the relationship out loud.
     */
    fun labelOf(node: ChainedIndicator, byId: Map<String, ChainedIndicator>): String {
        val spec = CHAINABLE[node.indicatorId]
        val ticker = TICKERS[node.indicatorId] ?: node.indicatorId.uppercase()
        val own = if (spec != null && spec.takesPeriod) {
            "$ticker ${periodFor(node.indicatorId, node.period)}"
        } else {
            ticker
        }
        val source = node.source
        if (source !is IndicatorSource.Output) return own
        val parent = byId[source.nodeId] ?: return own
        val parentLabel = labelOf(parent, byId)
        val named = source.output?.takeIf { it != outputsOf(parent.indicatorId).firstOrNull() }
        return if (named == null) "$own روی $parentLabel" else "$own روی $parentLabel ($named)"
    }

    /**
     * The pane a node's lines belong in, or null for the price axis.
     *
     * Walks up the chain until it meets an indicator that has a scale of its own. Bounded by
     * [MAX_DEPTH] hops rather than trusting the graph, because this is also called on chains a
     * caller assembled by hand.
     */
    private fun paneOwnerOf(node: ChainedIndicator, byId: Map<String, ChainedIndicator>): String? {
        var current = node
        var hops = 0
        while (hops <= MAX_DEPTH) {
            val pane = ChartCatalog.INDICATORS.firstOrNull { it.id == current.indicatorId }?.pane
            if (pane == IndicatorPane.SEPARATE) return current.nodeId
            val source = current.source
            if (source !is IndicatorSource.Output) return null
            current = byId[source.nodeId] ?: return null
            hops++
        }
        return null
    }

    /**
     * The reference lines a chained oscillator's pane draws.
     *
     * The same numbers [ChartCatalog.paneFor] uses, and they are part of the indicator rather than
     * decoration — an RSI without 30 and 70 is a wiggle. Repeated here rather than reached for,
     * because the catalogue builds a whole pane and this needs the levels alone.
     */
    private fun referenceLevels(id: String): List<PriceLevel> = when (id) {
        "rsi" -> listOf(level(70.0), level(50.0, faint = true), level(30.0))
        "stochrsi" -> listOf(level(80.0), level(20.0))
        "bbpercent" -> listOf(level(1.0), level(0.0))
        "cmo" -> listOf(level(50.0), level(0.0, faint = true), level(-50.0))
        "crsi" -> listOf(level(90.0), level(10.0))
        "tsi" -> listOf(level(25.0), level(0.0, faint = true), level(-25.0))
        "macd", "ppo", "kst", "trix", "smiErgodic", "mom", "roc", "dpo", "coppock" ->
            listOf(level(0.0, faint = true))
        else -> emptyList()
    }

    private fun level(value: Double, faint: Boolean = false): PriceLevel =
        PriceLevel(value, if (faint) 0xFF5E6673 else 0xFF848E9C, label = null)

    /**
     * One indicator's outputs over one source array.
     *
     * The trimming is the important part and is described in the object KDoc: the source's warm-up
     * is cut off, the arithmetic runs on the finite tail, and the head is padded back so every
     * output still lines up with the bar it describes.
     */
    private fun compute(id: String, source: DoubleArray, period: Int): Map<String, DoubleArray> {
        val names = outputsOf(id)
        if (names.isEmpty()) return emptyMap()
        var head = 0
        while (head < source.size && !source[head].isFinite()) head++
        if (head >= source.size) return names.associateWith { nanSeries(source.size) }
        val tail = if (head == 0) source else source.copyOfRange(head, source.size)
        val computed = computeTail(id, tail, period)
        return computed.mapValues { (_, values) -> padded(values, head, source.size) }
    }

    private fun computeTail(id: String, source: DoubleArray, period: Int): Map<String, DoubleArray> =
        when (id) {
            "sma" -> single(Indicators.sma(source, period))
            "ema" -> single(Indicators.ema(source, period))
            "wma" -> single(Indicators.wma(source, period))
            "hma" -> single(Indicators.hma(source, period))
            "smma" -> single(IndicatorsExt.smma(source, period))
            "zlema" -> single(IndicatorsExt.zlema(source, period))
            "kama" -> single(IndicatorsExt.kama(source, period))
            "t3" -> single(IndicatorsExt.t3(source, period))
            "mcginley" -> single(IndicatorsExt.mcginley(source, period))
            "linreg", "lsma" -> single(IndicatorsExt.linearRegression(source, period))
            "tema" -> single(IndicatorsExtB.tema(source, period))
            "dema" -> single(IndicatorsExtB.dema(source, period))
            "bollinger" -> band(Indicators.bollinger(source, period))
            "envelopes" -> band(IndicatorsExt.envelopes(source, period))
            "rsi" -> single(Indicators.rsi(source, period))
            "stddev" -> single(IndicatorsExt.stdDev(source, period))
            "hv" -> single(IndicatorsExt.historicalVolatility(source, period))
            "bbpercent" -> single(IndicatorsExt.bollingerPercent(source, period))
            "bbw" -> single(IndicatorsExt.bollingerWidth(source, period))
            "mom" -> single(IndicatorsExt.momentum(source, period))
            "roc" -> single(IndicatorsExt.rateOfChange(source, period))
            "dpo" -> single(IndicatorsExtC.detrendedPriceOscillator(source, period))
            "cmo" -> single(IndicatorsExtC.chandeMomentumOscillator(source, period))
            "crsi" -> single(IndicatorsExt.connorsRsi(source))
            "coppock" -> single(IndicatorsExtC.coppockCurve(source))
            "trix" -> IndicatorsExt.trix(source, period).let {
                mapOf(VALUE to it.line.toArray(), SIGNAL to it.signal.toArray())
            }
            "smiErgodic" -> IndicatorsExt.smiErgodic(source).let {
                mapOf(VALUE to it.line.toArray(), SIGNAL to it.signal.toArray())
            }
            "stochrsi" -> IndicatorsExtB.stochasticRsi(source, period, period).let {
                mapOf(K to it.k, D to it.d)
            }
            "tsi" -> IndicatorsExtB.trueStrengthIndex(source, period).let {
                mapOf(VALUE to it.tsi, SIGNAL to it.signal)
            }
            "macd" -> Indicators.macd(source).let {
                mapOf(
                    VALUE to it.macd.toArray(),
                    SIGNAL to it.signal.toArray(),
                    HISTOGRAM to it.histogram.toArray(),
                )
            }
            "ppo" -> IndicatorsExtB.ppo(source).let {
                mapOf(
                    VALUE to it.oscillator,
                    SIGNAL to it.signal,
                    HISTOGRAM to it.histogram,
                )
            }
            "kst" -> IndicatorsExtC.knowSureThing(source).let {
                mapOf(VALUE to it.kst, SIGNAL to it.signal)
            }
            else -> emptyMap()
        }

    private fun single(line: Line): Map<String, DoubleArray> = mapOf(VALUE to line.toArray())

    private fun single(values: DoubleArray): Map<String, DoubleArray> = mapOf(VALUE to values)

    private fun band(values: Band): Map<String, DoubleArray> = mapOf(
        BASIS to values.basis.toArray(),
        UPPER to values.upper.toArray(),
        LOWER to values.lower.toArray(),
    )

    private fun padded(values: DoubleArray, head: Int, size: Int): DoubleArray {
        if (head == 0 && values.size == size) return values
        val out = nanSeries(size)
        for (index in values.indices) {
            val target = index + head
            if (target < size) out[target] = values[index]
        }
        return out
    }

    private fun nanSeries(size: Int): DoubleArray = DoubleArray(size) { Double.NaN }

    private fun Line.toArray(): DoubleArray =
        DoubleArray(size) { if (isPresent(it)) raw(it) else Double.NaN }

    private fun DoubleArray.asLine(): Line = Line.of(size) { this[it].takeIf(Double::isFinite) }

    /** Halved towards black, so a signal line reads as the same indicator in a second shade. */
    private fun shade(argb: Long): Long {
        val alpha = argb and 0xFF000000L
        val red = ((argb shr 16 and 0xFF) * 55 / 100) shl 16
        val green = ((argb shr 8 and 0xFF) * 55 / 100) shl 8
        val blue = (argb and 0xFF) * 55 / 100
        return alpha or red or green or blue
    }

    /**
     * A small count in Persian digits, for prose.
     *
     * Two lines rather than a dependency: `core:chart` does not depend on `core:common`, and the
     * one number this file ever prints in a sentence is [MAX_DEPTH]. Market figures elsewhere in
     * this module stay in Latin digits, which is the house rule and not an oversight here.
     */
    private fun persianCount(value: Int): String =
        value.toString().map { PERSIAN_DIGITS[it - '0'] }.joinToString("")

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    private fun refused(reason: ChainRefusal, nodeIds: List<String>, message: String) =
        ChainOutcome.Refused(reason, nodeIds, message)

    /** The Latin ticker each chainable indicator is labelled with, as the catalogue writes it. */
    private val TICKERS: Map<String, String> = mapOf(
        "sma" to "SMA", "ema" to "EMA", "wma" to "WMA", "hma" to "HMA", "smma" to "SMMA",
        "zlema" to "ZLEMA", "kama" to "KAMA", "t3" to "T3", "mcginley" to "McGinley",
        "linreg" to "LinReg", "lsma" to "LSMA", "tema" to "TEMA", "dema" to "DEMA",
        "bollinger" to "BB", "envelopes" to "Env", "rsi" to "RSI", "stddev" to "StdDev",
        "hv" to "HV", "bbpercent" to "%B", "bbw" to "BBW", "mom" to "Momentum", "roc" to "ROC",
        "dpo" to "DPO", "cmo" to "CMO", "trix" to "TRIX", "stochrsi" to "Stoch RSI", "tsi" to "TSI",
        "macd" to "MACD 12/26/9", "ppo" to "PPO 12/26/9", "kst" to "KST 10/15/20/30",
        "smiErgodic" to "SMI Ergodic 20/5/5", "crsi" to "Connors RSI 3/2/100",
        "coppock" to "Coppock 14/11/10",
    )

    private const val FALLBACK_COLOUR = 0xFF8E9BAEL
}
