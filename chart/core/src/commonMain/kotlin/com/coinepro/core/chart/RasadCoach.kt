package com.coinepro.core.chart

import kotlin.math.abs

/**
 * **رصد** — the coach that reads a chart out loud (run Ω4).
 *
 * ### Why a coach at all, and why this one cannot lie
 *
 * The thesis of this product is «چارتی که حرف می‌زند» — a chart that translates rather than draws.
 * The Signal Layer got most of the way there: every study says what it is saying and prints the base
 * rate behind it. What it does not do is *compose*. A reader with four studies on the chart gets four
 * sentences and has to be the one who decides which of them is the story, and deciding that is
 * exactly the skill they do not have yet.
 *
 * So Rasad says three sentences, in this order, always: **what the market is doing, where the prices
 * that matter are, and what the chart's own studies make of it.** Three, because two is a headline
 * and four is an essay, and in the same order every time so a reader learns where to look rather than
 * reading it afresh.
 *
 * ### Deterministic, and why that is the feature rather than a limitation
 *
 * Nothing here calls a model. Every sentence is a template filled from the same arithmetic that drew
 * the lines — [ChartReading] for the trend, [Structure.supportResistance] for the levels, the
 * [SignalRead]s and the [SetupScore] the chart already published — so the coach and the chart can
 * never disagree, the same chart always produces the same words, and there is no sentence this can
 * emit that the reader cannot check against the picture in front of them.
 *
 * An assistant that paraphrases a chart in a slightly different way each time is an assistant a
 * reader learns not to trust with money. And the failure mode of the other approach is not a worse
 * sentence, it is a *confident* wrong one: a model asked about a chart will happily name a level that
 * is not there.
 *
 * ### Why it lives here
 *
 * `:chart-core` has no Android on its classpath and compiles for the JVM as well — see the module's
 * own note — so the coach ships to the web terminal with the engine rather than being rewritten
 * against it. Both languages are in this file for the reason [ChartReading]'s labels are: the
 * *threshold* and the wording belong together, and a second table keyed by the same thresholds in a
 * `strings.xml` is a second place to get the boundary wrong.
 */
object RasadCoach {

    /**
     * **«این چارت را بخوان»** — three sentences about the chart in front of the reader.
     *
     * Always three, and always in this order. A caller that gets fewer is looking at a series too
     * short to say anything about, and the list is empty rather than padded: three sentences about a
     * twelve-bar chart would be three guesses.
     *
     * @param reads the studies the chart is carrying, as the Signal Layer published them.
     * @param setup the chart's own score, so the third sentence agrees with the pill above it.
     */
    fun readChart(
        series: CandleSeries,
        reads: List<SignalRead> = emptyList(),
        setup: SetupScore = SetupScore(0, MarketState.NEUTRAL, 0, 0),
        english: Boolean = false,
    ): List<String> {
        if (series.size < MINIMUM_BARS) return emptyList()
        return listOfNotNull(
            trendSentence(series, english),
            levelSentence(series, english),
            studySentence(reads, setup, english),
        )
    }

    /**
     * **«یک هشدار پیشنهاد بده»** — one price worth being told about, and why.
     *
     * The price is a *level the market has already respected*, not a round number and not a
     * percentage: the whole point of an alert is to be told when something happens, and «bitcoin
     * reached 90,000» is an event about the decimal system rather than about the market.
     *
     * Which side is read off where the price already is. A reader watching a market under a level it
     * has been rejected at three times wants to know when it gets through; one sitting above support
     * wants to know when it breaks. Null where there is no level clear of the current price — and
     * null is the honest answer there, because an alert at the price the market is at fires
     * immediately and teaches the reader that alerts are noise.
     */
    fun suggestAlert(series: CandleSeries, english: Boolean = false): AlertSuggestion? {
        if (series.size < MINIMUM_BARS) return null
        val last = series.close.lastOrNull() ?: return null
        val decimals = decimalsFor(last)
        // Far enough away that the market has to actually go somewhere, and near enough that it is
        // this week's question rather than next year's. A level inside the noise band fires on the
        // next tick; one ten per cent away is not an alert, it is a wish.
        fun inRange(price: Double): Boolean {
            val distance = abs(price - last) / last
            return distance in NEAR_FLOOR..NEAR_CEILING
        }
        // A repeated level first, because «the market turned here twice» is a stronger claim than
        // «the market turned here». The swing is the fallback and not the equal: in a market that
        // has trended for two hundred bars every clustered level is far below the price, and a
        // reader asking for an alert there should get the last place it turned rather than nothing.
        val clustered = Structure.supportResistance(series)
            .map { it.price }
            .filter(::inRange)
            .minByOrNull { abs(it - last) }
        val price = clustered ?: Structure.swings(series)
            .map { it.price }
            .filter(::inRange)
            .minByOrNull { abs(it - last) }
            ?: return null
        val above = price > last
        return AlertSuggestion(
            price = price,
            above = above,
            why = if (above) {
                if (english) {
                    "The market has turned back from ${formatPrice(price, decimals)} before. " +
                        "Getting through it is the news."
                } else {
                    "بازار پیش از این از ${formatPrice(price, decimals)} برگشته. " +
                        "رد شدن از آن، خبر است."
                }
            } else {
                if (english) {
                    "${formatPrice(price, decimals)} has held the market up before. " +
                        "Losing it is the news."
                } else {
                    "${formatPrice(price, decimals)} پیش از این بازار را نگه داشته. " +
                        "از دست دادنش، خبر است."
                }
            },
        )
    }

    /**
     * **«معامله‌ی آخرم را مرور کن»** — what the reader did, said back to them without a verdict.
     *
     * ### Why the review is about the discipline and not about the money
     *
     * A winning trade taken without a stop is a worse trade than a losing one taken with a stop, and
     * every review that leads with the profit teaches the opposite. So the first sentence is what was
     * risked, the second is what happened to the plan, and the result comes last — which is also the
     * order in which the three facts are *useful*.
     *
     * Nothing here scolds. «شما طمع کردید» is a sentence about a person; «هدف جلوتر از جایی بود که
     * بازار رفت» is a sentence about a trade, and only one of the two is something anybody acts on.
     */
    fun reviewTrade(trade: TradeFacts, english: Boolean = false): List<String> = listOfNotNull(
        riskSentence(trade, english),
        planSentence(trade, english),
        resultSentence(trade, english),
    )

    /** The trend, its strength, and how much the instrument is moving while it does it. */
    private fun trendSentence(series: CandleSeries, english: Boolean): String? {
        val reading = ChartReading.of(series) ?: return null
        val trending = reading.strength >= ChartReading.TRENDING_FLOOR
        val direction = reading.biasLabel(english)
        val strength = reading.strengthLabel(english)
        val swing = reading.volatilityLabel(english)
        return if (trending) {
            if (english) {
                "The market is $direction and the trend reads $strength, with $swing swing."
            } else {
                "بازار $direction است و روند $strength خوانده می‌شود، با نوسان $swing."
            }
        } else {
            // No trend is a fact about the market and the most useful one there is: every signal a
            // trend study gives inside a range is a signal measured against something that is not
            // happening.
            if (english) {
                "There is no trend here — the market is turning inside a range, with $swing swing."
            } else {
                "اینجا روندی نیست — بازار داخل یک محدوده می‌چرخد، با نوسان $swing."
            }
        }
    }

    /** Where the prices the market has respected are, relative to where it is now. */
    private fun levelSentence(series: CandleSeries, english: Boolean): String? {
        val last = series.close.lastOrNull() ?: return null
        val decimals = decimalsFor(last)
        val levels = Structure.supportResistance(series)
        val above = levels.filter { it.price > last }.minByOrNull { it.price }?.price
        val below = levels.filter { it.price < last }.maxByOrNull { it.price }?.price
        val price = formatPrice(last, decimals)
        return when {
            above != null && below != null -> if (english) {
                "It is at $price, between support at ${formatPrice(below, decimals)} and " +
                    "resistance at ${formatPrice(above, decimals)}."
            } else {
                "روی $price است، بین حمایت ${formatPrice(below, decimals)} و " +
                    "مقاومت ${formatPrice(above, decimals)}."
            }
            above != null -> if (english) {
                "It is at $price, with the nearest level above it at ${formatPrice(above, decimals)}."
            } else {
                "روی $price است و نزدیک‌ترین سطح بالای آن ${formatPrice(above, decimals)} است."
            }
            below != null -> if (english) {
                "It is at $price, with the nearest level below it at ${formatPrice(below, decimals)}."
            } else {
                "روی $price است و نزدیک‌ترین سطح زیر آن ${formatPrice(below, decimals)} است."
            }
            // A market with no repeated level is a market in open space, and saying so is more use
            // than naming the highest bar and calling it resistance.
            else -> if (english) {
                "It is at $price, with no level it has turned at more than once nearby."
            } else {
                "روی $price است و نزدیکش سطحی نیست که بیش از یک بار از آن برگشته باشد."
            }
        }
    }

    /** What the studies on the chart make of it, with the count they are counted out of. */
    private fun studySentence(reads: List<SignalRead>, setup: SetupScore, english: Boolean): String {
        val speaking = reads.filter { it.state != MarketState.NEUTRAL }
        if (speaking.isEmpty()) {
            return if (english) {
                "Nothing on this chart has an opinion about direction right now."
            } else {
                "الان هیچ‌کدام از ابزارهای روی این چارت نظری درباره‌ی جهت ندارند."
            }
        }
        // Prose counts, so Persian digits in Persian — the app's rule, and the one place in this
        // file it applies: the *prices* in the level sentence are market figures and stay Latin.
        val bulls = speaking.count { it.state == MarketState.BULL }.prose(english)
        val bears = speaking.count { it.state == MarketState.BEAR }.prose(english)
        val agreeing = speaking.size.prose(english)
        val total = reads.size.prose(english)
        val side = setup.side.label(english)
        return when {
            speaking.any { it.state == MarketState.BULL } && speaking.any { it.state == MarketState.BEAR } -> if (english) {
                "Of $total studies on the chart, $bulls read up and $bears read down — they disagree."
            } else {
                "از $total ابزار روی چارت، $bulls صعودی و $bears نزولی می‌خوانند — با هم موافق نیستند."
            }
            else -> if (english) {
                "Of $total studies on the chart, $agreeing agree and all of them read $side."
            } else {
                "از $total ابزار روی چارت، $agreeing هم‌نظرند و همه $side می‌خوانند."
            }
        }
    }

    private fun riskSentence(trade: TradeFacts, english: Boolean): String {
        val decimals = decimalsFor(trade.entry)
        // Three cases, not two, and the third is the one that keeps this honest: a record that does
        // not say whether there was a stop must not be read as a record saying there was none. The
        // app's own book keeps the stop only where the stop is what closed the trade, so a
        // take-profit arrives here with `hadStop == null` — and «this one went on without a stop»
        // would be the coach inventing a fault out of a gap in a database.
        if (trade.stop == null && trade.hadStop == null) {
            return if (english) {
                "You entered at ${formatPrice(trade.entry, decimals)} and it closed at " +
                    "${formatPrice(trade.exit, decimals)}."
            } else {
                "روی ${formatPrice(trade.entry, decimals)} وارد شدید و روی " +
                    "${formatPrice(trade.exit, decimals)} بسته شد."
            }
        }
        return if (trade.stop == null) {
            // The one thing in this whole file that is close to a judgement, and it earns it: a
            // trade with no stop has no defined loss, which is not a style, it is an open position
            // with no floor under it.
            if (english) {
                "This one went on without a stop, so there was no price at which the loss was decided."
            } else {
                "این یکی بدون حد ضرر باز شد، پس قیمتی نبود که ضرر را در آن تمام کند."
            }
        } else {
            val risk = abs(trade.entry - trade.stop)
            if (english) {
                "You entered at ${formatPrice(trade.entry, decimals)} risking " +
                    "${formatPrice(risk, decimals)} to the stop."
            } else {
                "روی ${formatPrice(trade.entry, decimals)} وارد شدید و تا حد ضرر " +
                    "${formatPrice(risk, decimals)} ریسک کردید."
            }
        }
    }

    private fun planSentence(trade: TradeFacts, english: Boolean): String = when {
        trade.hadStop == false -> if (english) {
            "Deciding where to be wrong before entering is the one habit that makes the rest measurable."
        } else {
            "تصمیم‌گرفتن درباره‌ی جای اشتباه‌بودن، پیش از ورود، تنها عادتی است که بقیه را قابل اندازه‌گیری می‌کند."
        }
        trade.closedByStop -> if (english) {
            "The stop did its job and closed it — that is the plan working, not the plan failing."
        } else {
            "حد ضرر کارش را کرد و معامله را بست — این یعنی نقشه کار کرد، نه اینکه شکست خورد."
        }
        trade.closedEarly -> if (english) {
            "You closed it by hand before either line was reached."
        } else {
            "پیش از رسیدن به هر کدام از دو خط، خودتان معامله را بستید."
        }
        else -> if (english) {
            "It reached the target you set."
        } else {
            "به هدفی که گذاشته بودید رسید."
        }
    }

    private fun resultSentence(trade: TradeFacts, english: Boolean): String? {
        val r = trade.rMultiple ?: return null
        val rounded = ChartFormatting.oneDecimal(r)
        return if (english) {
            "The result was $rounded times what you risked."
        } else {
            "نتیجه $rounded برابر چیزی بود که ریسک کردید."
        }
    }

    /**
     * A count as prose: Persian digits in Persian, Latin in English.
     *
     * Here rather than borrowed from `core:common`, which this module cannot see — `:chart-core` has
     * no Android and no dependency on the app's own utility modules by construction. Ten characters
     * of table is a smaller price than a module boundary.
     */
    private fun Int.prose(english: Boolean): String =
        if (english) toString() else toString().map { PERSIAN_DIGITS[it - '0'] }.joinToString("")

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Below this the arithmetic is noise and so is anything said about it. */
    const val MINIMUM_BARS = 30

    /** How far from the last price a suggested alert has to sit, as a share of it. */
    private const val NEAR_FLOOR = 0.002
    private const val NEAR_CEILING = 0.06
}

/**
 * A level worth being told about, and the one sentence saying why it is that one.
 *
 * [above] rather than a condition enum, because the app's own `LocalAlertCondition` lives in another
 * module and this one must not learn about it: the caller maps a boolean to whatever its store calls
 * «crosses upward».
 */
data class AlertSuggestion(
    val price: Double,
    val above: Boolean,
    val why: String,
)

/**
 * What a finished trade was, reduced to the facts a review is about.
 *
 * A flat value rather than the app's own paper-trade row, for the reason every other input to this
 * file is one: `:chart-core` does not know what a `PaperTradeEntity` is and must not, and a review is
 * about a price, a stop and an outcome — not about a database.
 */
data class TradeFacts(
    val entry: Double,
    val exit: Double,
    /** The stop's price, where the record kept it. */
    val stop: Double? = null,
    /**
     * Whether there was a stop at all — `false` for a trade that went on without one, and **null
     * where the record does not say**.
     *
     * The three-way answer exists because the two-way one lies. A book that keeps the stop's price
     * only when the stop is what closed the trade hands every take-profit to the coach with no stop
     * attached, and a coach that reads that as «you traded without a stop» is scolding a reader for
     * a gap in a database.
     */
    val hadStop: Boolean? = if (stop != null) true else null,
    val target: Double? = null,
    /** Whether the stop is what closed it. */
    val closedByStop: Boolean = false,
    /** Whether the reader closed it by hand before either line was reached. */
    val closedEarly: Boolean = false,
    /** The result in units of risk, where there was a stop to measure it against. */
    val rMultiple: Double? = null,
)

/** One decimal place, without pulling in a platform formatter. */
private object ChartFormatting {
    fun oneDecimal(value: Double): String = formatPrice(value, 1)
}
