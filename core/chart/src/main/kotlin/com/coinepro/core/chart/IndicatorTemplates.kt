package com.coinepro.core.chart

/**
 * A template's three columns, in the shape a preferences store can hold.
 *
 * `core:datastore` keeps an indicator template as an id list, a period map and a source map of
 * plain strings, and deliberately does not know what any of them mean — see the note on its
 * `IndicatorTemplate`. This is the other side of that boundary: the one place in `core:chart` that
 * knows how a [ChainedIndicator] is spelled as text and how to read it back.
 *
 * Keyed by [ChainedIndicator.nodeId] rather than by indicator id, which for every template a reader
 * can build today is the same string — the chart holds one instance of each indicator, so the node
 * id defaults to the indicator id and the store's own KDoc stays true.
 */
data class TemplateStorage(
    val indicators: List<String>,
    val periods: Map<String, Int>,
    val sources: Map<String, String>,
)

/**
 * A template resolved against this build, ready to be switched on.
 *
 * [activeIds] and [periods] are shaped for the path the chart already has — the plain
 * indicator-id-and-lookback one — and carry only the nodes that read the candles. Anything reading
 * another indicator cannot be expressed there at all and is in [indicators], which
 * [IndicatorChain.evaluate] computes. A caller that reads only [activeIds] draws the template's
 * roots correctly and silently loses its chains, so it must not.
 *
 * [dropped] names entries this build could not honour: an indicator id from a newer version, a
 * source spelling it does not recognise, or a node whose own source was dropped. Reported rather
 * than hidden, because a template that comes back with three of its five indicators has to say so
 * instead of looking like the reader misremembered.
 */
data class TemplateApplication(
    val indicators: List<ChainedIndicator>,
    val activeIds: Set<String>,
    val periods: Map<String, Int>,
    val dropped: List<String>,
    val refusal: ChainOutcome.Refused? = null,
)

/**
 * Indicator templates: a named set of indicators, applied without touching anything else.
 *
 * ### What a template is, and what it is not
 *
 * A saved layout carries the indicator set *and* the timeframe, the chart type, the scale mode and
 * the colours. That is right for "put my chart back the way it was" and wrong for the thing readers
 * do ten times a session — «add my momentum set to this chart» — because it drags a fifteen-minute
 * line chart along with the four indicators somebody wanted. **Applying a template sets indicators,
 * their periods and their chain sources, and nothing else.** Not the timeframe, not the chart type,
 * not the scale, not the colours, not the drawings. That single sentence is the whole feature.
 *
 * ### The chain travels with it
 *
 * Each entry is a [ChainedIndicator], so a template records not only which indicators at what
 * lookback but what each one is *reading*: «RSI 14 on an EMA 20 of the close» survives being saved
 * and re-applied. That is why item 80 and item 81 are one type here rather than two.
 *
 * ### What it costs
 *
 * Nothing, and there is no limit. TradingView allows one indicator template on the free plan and
 * unlimited on the paid ones. There is no cap in this file and no counter to raise later; the
 * store's own two hundred is a runaway bound, not a product rule.
 *
 * ### Storage
 *
 * `core:datastore` owns the file and must not depend on this module, so a template crosses that
 * boundary as [TemplateStorage] — three collections of plain strings. A spelling this build does
 * not understand is left on disk untouched and simply not resolved, which is what should happen
 * when a reader downgrades.
 */
object IndicatorTemplates {

    /**
     * The source column's grammar, as one string per node.
     *
     * A bar column is its own name in lower case — `close`, `hl2` — and a chained source is the
     * producing node with an `@` in front, optionally naming which of its outputs to read:
     * `@ema20`, `@macd:signal`. Two characters of grammar, chosen because the store's own encoding
     * already reserves the ASCII separators and neither `@` nor `:` can appear in a catalogue id.
     *
     * A node reading the close needs no entry at all, which keeps the stored map empty for every
     * template that has no chain in it.
     */
    fun sourceToken(source: IndicatorSource): String = when (source) {
        is IndicatorSource.Bars -> source.field.name.lowercase()
        is IndicatorSource.Output ->
            if (source.output == null) "@${source.nodeId}" else "@${source.nodeId}:${source.output}"
    }

    /**
     * A stored source string back, or null when this build cannot read it.
     *
     * Null rather than a fallback to the close, and this is the one decision in the file worth
     * arguing about. A token this version does not recognise means the node was reading *something
     * else*; resolving it to the close would draw an indicator that looks computed, is labelled as
     * the reader's, and is not the one they saved. So the node is dropped and named in
     * [TemplateApplication.dropped], where the screen can say which ones did not come back.
     */
    fun parseSource(token: String): IndicatorSource? {
        if (token.isEmpty()) return IndicatorSource.CANDLES
        if (!token.startsWith("@")) {
            val field = BarField.entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
            return field?.let { IndicatorSource.Bars(it) }
        }
        val body = token.substring(1)
        if (body.isEmpty()) return null
        val split = body.indexOf(':')
        return if (split < 0) {
            IndicatorSource.Output(body)
        } else {
            IndicatorSource.Output(body.take(split), body.substring(split + 1).takeIf { it.isNotEmpty() })
        }
    }

    /** The set as the store keeps it. The close is left out of [TemplateStorage.sources]. */
    fun toStorage(nodes: List<ChainedIndicator>): TemplateStorage = TemplateStorage(
        indicators = nodes.map { it.nodeId },
        periods = nodes.mapNotNull { node -> node.period?.let { node.nodeId to it } }.toMap(),
        sources = nodes
            .filterNot { it.source == IndicatorSource.CANDLES }
            .associate { it.nodeId to sourceToken(it.source) },
    )

    /**
     * The set back from the store's three columns.
     *
     * The id list is authoritative: a period or a source keyed to something not in it is ignored,
     * because a stored map can outlive the row it described. The node id doubles as the indicator
     * id, which is what the chart's own state has always assumed — one instance of each indicator —
     * and is why a template written before chaining existed reads back unchanged.
     */
    fun nodesFrom(
        indicators: List<String>,
        periods: Map<String, Int> = emptyMap(),
        sources: Map<String, String> = emptyMap(),
    ): List<ChainedIndicator> = indicators.map { id ->
        ChainedIndicator(
            nodeId = id,
            indicatorId = id,
            period = periods[id],
            source = sources[id]?.let { parseSource(it) } ?: IndicatorSource.CANDLES,
        )
    }

    /**
     * Whether a set's chain is sound, without computing anything.
     *
     * Evaluated against an empty series on purpose: [IndicatorChain.evaluate] checks the graph —
     * duplicate ids, missing sources, unknown outputs, loops, depth — before it touches any
     * arithmetic, and on a series of no bars there is no arithmetic to touch. So this is the full
     * structural check for the cost of walking the list, and it can run when a reader *names* a
     * template rather than when they apply it, which is where a refusal is still actionable.
     */
    fun check(nodes: List<ChainedIndicator>): ChainOutcome =
        IndicatorChain.evaluate(CandleSeries.EMPTY, nodes)

    /**
     * Resolve a stored template against this build.
     *
     * Rows naming an indicator this version does not have are dropped, and so is anything that was
     * reading one of them — a chain with its root removed is not a chain, and leaving the dependent
     * behind would quietly re-point it at the close, which is the failure this whole file is
     * careful about.
     */
    fun apply(
        indicators: List<String>,
        periods: Map<String, Int> = emptyMap(),
        sources: Map<String, String> = emptyMap(),
    ): TemplateApplication {
        val known = ChartCatalog.INDICATORS.mapTo(HashSet()) { it.id }
        val kept = LinkedHashMap<String, ChainedIndicator>()
        val dropped = ArrayList<String>()
        for (id in indicators) {
            val token = sources[id]
            val source = if (token == null) IndicatorSource.CANDLES else parseSource(token)
            when {
                id !in known || id in kept || source == null -> dropped += id
                else -> kept[id] = ChainedIndicator(id, id, periods[id], source)
            }
        }
        // Orphans are swept repeatedly rather than in one pass, and the stored order is not
        // trusted: a template may list a chained RSI before the average it reads, and dropping it
        // for being ahead of its source would silently thin the reader's set. Each sweep can orphan
        // whatever was reading the node it just removed, so it runs until a sweep removes nothing.
        var swept = true
        while (swept) {
            swept = false
            for (node in kept.values.toList()) {
                val source = node.source
                if (source !is IndicatorSource.Output || source.nodeId in kept) continue
                kept.remove(node.nodeId)
                dropped += node.nodeId
                swept = true
            }
        }
        val nodes = kept.values.toList()
        val refusal = check(nodes) as? ChainOutcome.Refused
        val roots = nodes.filter { it.source !is IndicatorSource.Output }
        return TemplateApplication(
            indicators = nodes,
            activeIds = roots.mapTo(LinkedHashSet()) { it.indicatorId },
            periods = roots.mapNotNull { node -> node.period?.let { node.indicatorId to it } }.toMap(),
            dropped = dropped,
            refusal = refusal,
        )
    }
}
