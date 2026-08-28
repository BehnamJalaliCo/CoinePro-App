package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import java.util.Locale
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart types a reader can choose, and the indicators they can add.
 *
 * Almost every entry carries a [helpId], and that is the point of this file existing at all rather
 * than the pickers hard-coding their own lists. The «؟» content is the web terminal's, keyed by its
 * own ids, so the mapping from "the thing this app offers" to "the thing the help explains" has to
 * be written down once and be checkable — a picker that silently offers a tool with no help, or
 * points at the wrong entry, is exactly the kind of gap nobody notices until a user does.
 *
 * `ChartCatalogTest` asserts that every id here exists in the shipped catalogue, and pins the nine
 * indicators that have no entry so a tenth cannot join them unnoticed.
 */
data class ChartTypeOption(
    val type: ChartType,
    /** Persian label. The web terminal's own name for it. */
    val label: String,
    /** The «؟» entry id in the help catalogue. */
    val helpId: String,
    /**
     * TradingView's own glyph for this chart type.
     *
     * There is one for every type, drawn as a tiny picture of the thing — two candles, a Renko
     * staircase, a Kagi zigzag. A list of eleven chart types with no pictures asks the reader to
     * know what "لاین‌بریک" looks like before they can choose it, which defeats the point of
     * offering it.
     */
    @DrawableRes val icon: Int,
)

/**
 * One indicator a reader can switch on.
 *
 * [pane] decides where it draws. An indicator on the price scale — a moving average, a band, a
 * trailing stop — goes over the candles; one on its own scale must not, because plotting RSI's 0-100
 * against a gold price of 2,600 collapses the price axis to a line.
 */
data class IndicatorOption(
    val id: String,
    val label: String,
    /**
     * The «؟» entry id, or null where the shipped catalogue has no entry.
     *
     * Null rather than a guess. Nine of the fifty point at nothing — the web terminal's help was
     * written before those indicators were added to it — and a «؟» that opens an empty sheet is a
     * worse answer than no «؟». `ChartCatalogTest` pins exactly which nine, so the gap can only
     * close, never quietly widen.
     */
    val helpId: String?,
    val pane: IndicatorPane,
    /** ARGB. Several indicators draw more than one line and pick shades around this. */
    val colour: Long,
    /**
     * A glyph for the *kind* of indicator, not for the indicator itself.
     *
     * Nobody publishes a distinct icon per indicator — TradingView included, which lists them as
     * plain text. Twenty copies of one generic symbol would say nothing, so these say what shape
     * the thing draws: a line, a channel, an oscillating wave, a histogram, a ruler, a direction.
     * That is the distinction a reader scanning the list is actually making.
     */
    @DrawableRes val icon: Int,
)

/**
 * The one number a reader is allowed to change on an indicator, and the range it may take.
 *
 * ### Why only one
 *
 * Most indicators here have several parameters — MACD has three, Bollinger has a period and a
 * multiplier, Ichimoku has three spans. Exposing all of them would mean a form per indicator and
 * fifty forms to design, most of which nobody would ever open. Exposing *the lookback* covers what
 * readers actually change: «EMA 20» is not the same tool as «EMA 200», and until this existed the
 * app offered only the first, forever, with the number baked into the label as a literal.
 *
 * Indicators whose shape is not a single lookback — MACD, Ichimoku, VWAP, the structure studies —
 * are absent from this table on purpose, and the picker shows them no stepper rather than a
 * stepper that changes nothing.
 *
 * ### The bounds
 *
 * [min] is 2 almost everywhere, because a one-bar average is the price. [max] is 400, which is
 * past the longest period anybody uses (the 200-day average) with room above it, and short enough
 * that the result is not a line of nulls on a series of 500 bars.
 */
data class IndicatorPeriod(val default: Int, val min: Int = 2, val max: Int = 400)

enum class IndicatorPane {
    /** Drawn over the candles, on the price axis. */
    PRICE,

    /** Drawn in its own pane below, on its own scale. */
    SEPARATE,

    /**
     * Drawn over the candles, but not as a value per bar.
     *
     * Its own pane rather than sharing [PRICE] because it is a different kind of answer. A moving
     * average says "the average here is 2,614"; a support study says "2,614 is a level" and a swing
     * study says "this bar mattered". They needed two drawing shapes the chart did not have —
     * [PriceLevel] and [ChartMarker] — and they are reached through [ChartCatalog.structureFor]
     * rather than [ChartCatalog.overlayFor], so keeping them apart is the type system agreeing.
     */
    STRUCTURE,
}

/** What a structure study draws: any mix of lines, horizontal levels and per-bar marks. */
data class StructureOverlay(
    val lines: List<ChartLine> = emptyList(),
    val levels: List<PriceLevel> = emptyList(),
    val markers: List<ChartMarker> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty() && levels.isEmpty() && markers.isEmpty()
}

object ChartCatalog {

    /**
     * The eleven chart types, in the order the picker shows them.
     *
     * Ordinary first, price-driven last. Somebody opening this list is nine times in ten switching
     * between candles and Heikin-Ashi; Renko and Point & Figure are deliberate choices that people
     * go looking for.
     */
    val CHART_TYPES: List<ChartTypeOption> = listOf(
        ChartTypeOption(ChartType.CANDLES, "کندل", "candles", DesignR.drawable.tv_chart_candles),
        ChartTypeOption(ChartType.HOLLOW, "کندل توخالی", "hollow", DesignR.drawable.tv_chart_hollow),
        ChartTypeOption(ChartType.HEIKIN_ASHI, "هایکین‌آشی", "heikin", DesignR.drawable.tv_chart_heikin),
        ChartTypeOption(ChartType.BARS, "میله‌ای (OHLC)", "bars", DesignR.drawable.tv_chart_bars),
        ChartTypeOption(ChartType.LINE, "خطی", "line", DesignR.drawable.tv_chart_line),
        ChartTypeOption(ChartType.AREA, "ناحیه‌ای", "area", DesignR.drawable.tv_chart_area),
        ChartTypeOption(ChartType.RENKO, "رنکو", "renko", DesignR.drawable.tv_chart_renko),
        ChartTypeOption(ChartType.RANGE, "رنج", "range", DesignR.drawable.tv_chart_range),
        ChartTypeOption(ChartType.LINE_BREAK, "لاین‌بریک", "linebreak", DesignR.drawable.tv_chart_linebreak),
        ChartTypeOption(ChartType.KAGI, "کاگی", "kagi", DesignR.drawable.tv_chart_kagi),
        ChartTypeOption(ChartType.POINT_AND_FIGURE, "نقطه و رقم", "pnf", DesignR.drawable.tv_chart_pnf),
    )

    /**
     * The fifty indicators the engine computes, grouped the way a trader thinks about them.
     *
     * Fifty is past the point where a list can be scanned, which is why the picker grew a search
     * field and a pane filter before this list grew past twenty. The order is the useful one and
     * not an alphabet: within each pane, the ones most readers reach for first.
     */
    val INDICATORS: List<IndicatorOption> = listOf(
        // Trend — on the price.
        IndicatorOption("sma", "میانگین متحرک ساده", "ma", IndicatorPane.PRICE, 0xFFD8A848, DesignR.drawable.tv_chart_line),
        IndicatorOption("ema", "میانگین متحرک نمایی", "ema", IndicatorPane.PRICE, 0xFF6E8BE0, DesignR.drawable.tv_chart_line),
        IndicatorOption("wma", "میانگین متحرک وزنی", "wma", IndicatorPane.PRICE, 0xFF9B7BE0, DesignR.drawable.tv_chart_line),
        IndicatorOption("hma", "میانگین متحرک هال", "hma", IndicatorPane.PRICE, 0xFF4FB3A5, DesignR.drawable.tv_chart_line),
        IndicatorOption("bollinger", "باند بولینگر", "bb", IndicatorPane.PRICE, 0xFF8E9BAE, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("keltner", "کانال کلتنر", "keltner", IndicatorPane.PRICE, 0xFF7FA3C7, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("donchian", "کانال دونچیان", "donchian", IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("ichimoku", "ایچیموکو", "ichimoku", IndicatorPane.PRICE, 0xFFC77F9B, DesignR.drawable.tv_chart_hlcarea),
        IndicatorOption("supertrend", "سوپرترند", "supertrend", IndicatorPane.PRICE, 0xFF00B15C, DesignR.drawable.tv_tool_trend),
        IndicatorOption("vwap", "میانگین وزنی حجم", "vwap", IndicatorPane.PRICE, 0xFFE0A85C, DesignR.drawable.tv_chart_volcandles),

        // Momentum and volatility — their own scale.
        IndicatorOption("rsi", "شاخص قدرت نسبی", "rsi", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("macd", "مکدی", "macd", IndicatorPane.SEPARATE, 0xFF6E8BE0, DesignR.drawable.tv_chart_columns),
        IndicatorOption("stochastic", "استوکاستیک", "stoch", IndicatorPane.SEPARATE, 0xFF4FB3A5, DesignR.drawable.tv_tool_sine),
        IndicatorOption("cci", "شاخص کانال کالا", "cci", IndicatorPane.SEPARATE, 0xFF9B7BE0, DesignR.drawable.tv_tool_sine),
        IndicatorOption("williams", "ویلیامز R%", "willr", IndicatorPane.SEPARATE, 0xFFC77F9B, DesignR.drawable.tv_tool_sine),
        IndicatorOption("atr", "میانگین دامنهٔ واقعی", "atr", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_ruler),
        IndicatorOption("adx", "شاخص میانگین جهت‌دار", "adx", IndicatorPane.SEPARATE, 0xFFE0A85C, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("choppiness", "شاخص چاپینس", "choppiness", IndicatorPane.SEPARATE, 0xFF7FA3C7, DesignR.drawable.tv_ruler),
        IndicatorOption("vortex", "ورتکس", "vortex", IndicatorPane.SEPARATE, 0xFFB08BC7, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("obv", "حجم متعادل", "obv", IndicatorPane.SEPARATE, 0xFF00B15C, DesignR.drawable.tv_chart_columns),

        // ── The second thirty, from indicators_ext_a.js and indicators_ext_b.js ─────────────
        // Trend, on the price.
        IndicatorOption("smma", "میانگین وایلدر (SMMA)", "smma", IndicatorPane.PRICE, 0xFF84CC16, DesignR.drawable.tv_chart_line),
        IndicatorOption("zlema", "میانگین با تأخیر صفر", "zlema", IndicatorPane.PRICE, 0xFF2DD4BF, DesignR.drawable.tv_chart_line),
        IndicatorOption("kama", "میانگین تطبیقی کافمن", "kama", IndicatorPane.PRICE, 0xFFFB923C, DesignR.drawable.tv_chart_line),
        IndicatorOption("t3", "میانگین T3 تیلسون", "t3", IndicatorPane.PRICE, 0xFFE879F9, DesignR.drawable.tv_chart_line),
        IndicatorOption("mcginley", "مک‌گینلی داینامیک", "mcginley", IndicatorPane.PRICE, 0xFFFACC15, DesignR.drawable.tv_chart_line),
        IndicatorOption("linreg", "منحنی رگرسیون خطی", "linreg", IndicatorPane.PRICE, 0xFF38BDF8, DesignR.drawable.tv_tool_trend),
        IndicatorOption("lsma", "میانگین کمترین‌مربعات", "lsma", IndicatorPane.PRICE, 0xFFD8A848, DesignR.drawable.tv_tool_trend),
        IndicatorOption("envelopes", "پاکت درصدی", "envelopes", IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_flatchannel),

        // Volatility, on its own scale.
        IndicatorOption("stddev", "انحراف معیار", "stddev", IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_ruler),
        IndicatorOption("hv", "نوسان تاریخی", "hv", IndicatorPane.SEPARATE, 0xFFF59E0B, DesignR.drawable.tv_ruler),
        IndicatorOption("chaikinVol", "نوسان چایکین", "chaikinVol", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_ruler),
        IndicatorOption("bbpercent", "باند بولینگر ٪B", "bbpercent", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_tool_sine),
        IndicatorOption("bbw", "پهنای باند بولینگر", "bbw", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_ruler),

        // Momentum, on its own scale.
        IndicatorOption("mom", "مومنتوم", "mom", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("roc", "نرخ تغییر", "roc", IndicatorPane.SEPARATE, 0xFFF472B6, DesignR.drawable.tv_tool_sine),
        IndicatorOption("trix", "تریکس (TRIX)", "trix", IndicatorPane.SEPARATE, 0xFF34D399, DesignR.drawable.tv_tool_sine),
        IndicatorOption("ac", "شتاب‌دهنده", "ac", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_chart_columns),
        IndicatorOption("uo", "اسیلاتور غایی", "uo", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("fisher", "تبدیل فیشر", "fisher", IndicatorPane.SEPARATE, 0xFFFB923C, DesignR.drawable.tv_tool_sine),
        IndicatorOption("crsi", "کانرز RSI", "crsi", IndicatorPane.SEPARATE, 0xFFE879F9, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smiErgodic", "SMI ارگودیک", "smiErgodic", IndicatorPane.SEPARATE, 0xFF38BDF8, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smi", "مومنتوم استوکاستیک", "smi", IndicatorPane.SEPARATE, 0xFFFACC15, DesignR.drawable.tv_tool_sine),
        IndicatorOption("bop", "توازن قدرت", "bop", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_chart_columns),

        // Volume, on its own scale.
        IndicatorOption("adline", "تجمع و توزیع", "adline", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("chaikinOsc", "اسیلاتور چایکین", "chaikinOsc", IndicatorPane.SEPARATE, 0xFFF97316, DesignR.drawable.tv_chart_columns),
        IndicatorOption("eom", "سهولت حرکت", "eom", IndicatorPane.SEPARATE, 0xFF84CC16, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("forceIndex", "شاخص نیرو", "forceIndex", IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_chart_columns),
        IndicatorOption("klinger", "اسیلاتور کلینگر", "klinger", IndicatorPane.SEPARATE, 0xFFA855F7, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("pvt", "روند قیمت-حجم", "pvt", IndicatorPane.SEPARATE, 0xFF10B981, DesignR.drawable.tv_chart_volcandles),

        // ── Structure: levels and marks rather than a value per bar ─────────────────────────
        IndicatorOption("pivots", "پیووت (۵ روش)", null, IndicatorPane.STRUCTURE, 0xFF94A3B8, DesignR.drawable.tv_tool_hline),
        IndicatorOption("swings", "نقاط چرخش", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("fractals", "فرکتال ویلیامز", null, IndicatorPane.STRUCTURE, 0xFFF0B90B, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("zigzag", "زیگزاگ", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_trend),
        IndicatorOption("autofib", "فیبوناچی خودکار", null, IndicatorPane.STRUCTURE, 0xFF22D3EE, DesignR.drawable.tv_tool_fib),
        IndicatorOption("sr", "حمایت و مقاومت", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_hline),
        IndicatorOption("supplydemand", "نواحی عرضه و تقاضا", null, IndicatorPane.STRUCTURE, 0xFF00B15C, DesignR.drawable.tv_tool_rect),
    )

    /**
     * What a structure study draws for a series.
     *
     * Separate from [overlayFor] because the return type is genuinely different, and a single
     * function returning an object where four of five fields are always empty would hide that.
     */
    fun structureFor(option: IndicatorOption, series: CandleSeries): StructureOverlay {
        if (option.pane != IndicatorPane.STRUCTURE || series.isEmpty) return StructureOverlay()
        return when (option.id) {
            "pivots" -> StructureOverlay(lines = Structure.pivots(series))
            "swings" -> StructureOverlay(markers = Structure.swings(series))
            "fractals" -> StructureOverlay(markers = Structure.fractals(series))
            "zigzag" -> Structure.zigzag(series).let { (line, markers) ->
                StructureOverlay(lines = listOf(line), markers = markers)
            }
            "autofib" -> StructureOverlay(levels = Structure.autoFibonacci(series))
            "sr" -> StructureOverlay(levels = Structure.supportResistance(series))
            "supplydemand" -> StructureOverlay(levels = Structure.supplyDemand(series))
            else -> StructureOverlay()
        }
    }

    /**
     * What a separate-pane indicator draws, on its own scale.
     *
     * The third of the three shapes an indicator can take, beside [overlayFor] and [structureFor],
     * and the last one the chart learned. Until it existed, switching on an RSI in the picker did
     * nothing at all: the option was in the catalogue, the arithmetic was in [Indicators], and
     * nothing joined them because there was nowhere on the canvas to put a second scale.
     *
     * The reference levels each pane declares — RSI's 30 and 70, the zero line under a momentum
     * oscillator — are part of the indicator, not decoration. An RSI without them is a wiggle.
     */
    fun paneFor(
        option: IndicatorOption,
        series: CandleSeries,
        /** The reader's lookback, or null for this indicator's own default. See [PERIODS]. */
        period: Int? = null,
    ): ChartPane? {
        if (option.pane != IndicatorPane.SEPARATE || series.isEmpty) return null
        val n = periodFor(option.id, period)
        val open = series.open
        val high = series.high
        val low = series.low
        val close = series.close
        val volume = series.volume
        val colour = option.colour
        val second = shade(colour)
        fun pane(
            title: String,
            vararg lines: ChartLine,
            levels: List<PriceLevel> = emptyList(),
            histogram: ChartLine? = null,
        ) = ChartPane(title = title, lines = lines.toList(), levels = levels, histogram = histogram)

        return when (option.id) {
            "rsi" -> pane(
                "RSI $n",
                ChartLine(Indicators.rsi(close, n), colour, label = "RSI"),
                levels = listOf(band(70.0), band(50.0, faint = true), band(30.0)),
            )
            "macd" -> Indicators.macd(close).let { macd ->
                pane(
                    "MACD 12/26/9",
                    ChartLine(macd.macd, colour, label = "MACD"),
                    ChartLine(macd.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                    histogram = ChartLine(macd.histogram, colour),
                )
            }
            "stochastic" -> Indicators.stochastic(high, low, close).let { stoch ->
                pane(
                    "Stochastic 14/3",
                    ChartLine(stoch.k, colour, label = "%K"),
                    ChartLine(stoch.d, second, label = "%D"),
                    levels = listOf(band(80.0), band(20.0)),
                )
            }
            "cci" -> pane(
                "CCI $n",
                ChartLine(Indicators.cci(high, low, close, n), colour),
                levels = listOf(band(100.0), band(0.0, faint = true), band(-100.0)),
            )
            "williams" -> pane(
                "Williams %R $n",
                ChartLine(Indicators.williamsR(high, low, close, n), colour),
                levels = listOf(band(-20.0), band(-80.0)),
            )
            "atr" -> pane("ATR $n", ChartLine(Indicators.atr(high, low, close, n), colour))
            "adx" -> Indicators.adx(high, low, close, n).let { adx ->
                pane(
                    "ADX $n",
                    ChartLine(adx.adx, colour, label = "ADX"),
                    ChartLine(adx.plusDi, 0xFF00B15C, label = "+DI"),
                    ChartLine(adx.minusDi, 0xFFF6465D, label = "−DI"),
                    // Twenty-five is Wilder's own threshold for "there is a trend here at all",
                    // and reading ADX without it is reading a number with no scale.
                    levels = listOf(band(25.0)),
                )
            }
            "choppiness" -> pane(
                "Choppiness $n",
                ChartLine(Indicators.choppiness(high, low, close, n), colour),
                levels = listOf(band(61.8), band(38.2)),
            )
            "vortex" -> Indicators.vortex(high, low, close).let { vortex ->
                pane(
                    "Vortex 14",
                    ChartLine(vortex.plus, 0xFF00B15C, label = "VI+"),
                    ChartLine(vortex.minus, 0xFFF6465D, label = "VI−"),
                    levels = listOf(band(1.0, faint = true)),
                )
            }
            "obv" -> pane("OBV", ChartLine(Indicators.obv(close, volume), colour))
            "stddev" -> pane("StdDev 20", ChartLine(IndicatorsExt.stdDev(close, 20), colour))
            "hv" -> pane("HV 10", ChartLine(IndicatorsExt.historicalVolatility(close), colour))
            "chaikinVol" -> pane(
                "Chaikin Vol 10",
                ChartLine(IndicatorsExt.chaikinVolatility(high, low), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "bbpercent" -> pane(
                "%B 20",
                ChartLine(IndicatorsExt.bollingerPercent(close), colour),
                levels = listOf(band(1.0), band(0.0)),
            )
            "bbw" -> pane("BBW 20", ChartLine(IndicatorsExt.bollingerWidth(close), colour))
            "mom" -> pane(
                "Momentum 10",
                ChartLine(IndicatorsExt.momentum(close, 10), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "roc" -> pane(
                "ROC 12",
                ChartLine(IndicatorsExt.rateOfChange(close, 12), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "trix" -> IndicatorsExt.trix(close).let { trix ->
                pane(
                    "TRIX 18/9",
                    ChartLine(trix.line, colour, label = "TRIX"),
                    ChartLine(trix.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "ac" -> pane(
                "Accelerator",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.accelerator(high, low), colour),
            )
            "uo" -> pane(
                "Ultimate 7/14/28",
                ChartLine(IndicatorsExt.ultimateOscillator(high, low, close), colour),
                levels = listOf(band(70.0), band(30.0)),
            )
            "fisher" -> IndicatorsExt.fisherTransform(high, low).let { fisher ->
                pane(
                    "Fisher 9",
                    ChartLine(fisher.line, colour, label = "Fisher"),
                    ChartLine(fisher.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "crsi" -> pane(
                "Connors RSI 3/2/100",
                ChartLine(IndicatorsExt.connorsRsi(close), colour),
                levels = listOf(band(90.0), band(10.0)),
            )
            "smiErgodic" -> IndicatorsExt.smiErgodic(close).let { smi ->
                pane(
                    "SMI Ergodic 20/5/5",
                    ChartLine(smi.line, colour, label = "SMI"),
                    ChartLine(smi.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "smi" -> pane(
                "Stochastic Momentum 10",
                ChartLine(IndicatorsExt.stochasticMomentum(high, low, close), colour),
                levels = listOf(band(40.0), band(0.0, faint = true), band(-40.0)),
            )
            "bop" -> pane(
                "Balance of Power",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.balanceOfPower(open, high, low, close), colour),
            )
            "adline" -> pane(
                "A/D Line",
                ChartLine(IndicatorsExt.accumulationDistribution(high, low, close, volume), colour),
            )
            "chaikinOsc" -> pane(
                "Chaikin Osc 3/10",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.chaikinOscillator(high, low, close, volume), colour),
            )
            "eom" -> pane(
                "Ease of Movement 14",
                ChartLine(IndicatorsExt.easeOfMovement(high, low, volume), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "forceIndex" -> pane(
                "Force Index 13",
                ChartLine(IndicatorsExt.forceIndex(close, volume), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "klinger" -> IndicatorsExt.klinger(high, low, close, volume).let { klinger ->
                pane(
                    "Klinger 34/55/13",
                    ChartLine(klinger.line, colour, label = "KVO"),
                    ChartLine(klinger.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "pvt" -> pane("PVT", ChartLine(IndicatorsExt.priceVolumeTrend(close, volume), colour))
            else -> null
        }
    }

    /**
     * A reference line inside a pane.
     *
     * [faint] is for the ones that mark a *centre* rather than a threshold — zero on a momentum
     * oscillator, fifty on an RSI. Drawn at the same weight as the thresholds they compete with
     * them for attention, and the thresholds are the ones a reader is watching for.
     */
    private fun band(value: Double, faint: Boolean = false): PriceLevel =
        PriceLevel(value, if (faint) 0xFF5E6673 else 0xFF848E9C, label = null)

    /**
     * A companion colour for an indicator's second line.
     *
     * Derived from the first rather than picked, so a pane with a signal line always reads as one
     * indicator in two shades instead of two indicators sharing a box. Halving each channel towards
     * black is enough separation to tell them apart and not enough to look unrelated.
     */
    private fun shade(argb: Long): Long {
        val alpha = argb and 0xFF000000L
        val red = ((argb shr 16 and 0xFF) * 55 / 100) shl 16
        val green = ((argb shr 8 and 0xFF) * 55 / 100) shl 8
        val blue = (argb and 0xFF) * 55 / 100
        return alpha or red or green or blue
    }

    /**
     * Defaults for the two regression curves.
     *
     * A hundred bars for the curve and twenty-five for the projected average, which are the web
     * terminal's — the same series under two periods is two different indicators to a reader, and
     * two products that disagree about which is which are worse than one that offers only one.
     */
    private const val LINREG_PERIOD = 100
    private const val LSMA_PERIOD = 25

    /**
     * The default lookback for every indicator that has exactly one, keyed by id.
     *
     * These are the numbers that used to be literals inside [overlayFor] and [paneFor] — the same
     * values, moved to one place so the picker can show them and a reader can change them. Anything
     * absent has no single lookback and is not offered a stepper.
     *
     * `ChartCatalogTest` holds this table against the two builders, so an indicator that gains a
     * period here and does not read it there is a failing test rather than a stepper that moves a
     * number nothing looks at.
     */
    val PERIODS: Map<String, IndicatorPeriod> = mapOf(
        // Price-scale averages and bands.
        "sma" to IndicatorPeriod(20),
        "ema" to IndicatorPeriod(20),
        "wma" to IndicatorPeriod(20),
        "hma" to IndicatorPeriod(20),
        "smma" to IndicatorPeriod(14),
        "zlema" to IndicatorPeriod(21),
        "kama" to IndicatorPeriod(10),
        "t3" to IndicatorPeriod(10),
        "mcginley" to IndicatorPeriod(14),
        // A hundred and twenty-five, the web terminal's, and the two are different tools to a
        // reader rather than one tool at two settings — so each keeps its own default.
        "linreg" to IndicatorPeriod(LINREG_PERIOD),
        "lsma" to IndicatorPeriod(LSMA_PERIOD),
        "bollinger" to IndicatorPeriod(20),
        "donchian" to IndicatorPeriod(20),
        "envelopes" to IndicatorPeriod(20),
        // Own-pane oscillators.
        "rsi" to IndicatorPeriod(14),
        "cci" to IndicatorPeriod(20),
        "williams" to IndicatorPeriod(14),
        "atr" to IndicatorPeriod(14),
        "adx" to IndicatorPeriod(14),
        "choppiness" to IndicatorPeriod(14),
    )

    /** The lookback [id] can be given, or null where it has no single one. */
    fun periodOf(id: String): IndicatorPeriod? = PERIODS[id]

    /**
     * The period an indicator should actually be computed with.
     *
     * [chosen] is the reader's, when they have moved the stepper. Null falls back to the default,
     * and anything out of range is clamped rather than refused — a stored value from an older
     * build with wider bounds must not produce an empty chart.
     */
    private fun periodFor(id: String, chosen: Int?): Int {
        val bounds = PERIODS[id] ?: return chosen ?: 0
        return (chosen ?: bounds.default).coerceIn(bounds.min, bounds.max)
    }

    /**
     * The indicators whose Persian name or ticker contains [query].
     *
     * Deliberately a plain substring rather than the ranked matcher `core:symbols` uses. That one
     * scores subsequences, so «ما» would drag in half the list through letters it merely passes
     * through; here the reader is filtering a list they can already see, and a filter that returns
     * things they did not ask for reads as broken. Both the Persian label and the Latin id match, so
     * «rsi» and «قدرت» find the same row.
     */
    fun matchingIndicators(query: String): List<IndicatorOption> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return INDICATORS
        return INDICATORS.filter {
            needle in it.label.lowercase(Locale.ROOT) || needle in it.id.lowercase(Locale.ROOT)
        }
    }

    /**
     * Compute one indicator's price-scale lines for a series.
     *
     * Only the [IndicatorPane.PRICE] ones, because those are the ones the chart draws over the
     * candles. A separate-pane indicator needs its own axis and is a different drawing problem.
     */
    fun overlayFor(
        option: IndicatorOption,
        series: CandleSeries,
        /** The reader's lookback, or null for this indicator's own default. See [PERIODS]. */
        period: Int? = null,
    ): List<ChartLine> {
        if (option.pane != IndicatorPane.PRICE || series.isEmpty) return emptyList()
        val close = series.close
        val high = series.high
        val low = series.low
        // Resolved once, and every label below is built from it rather than written as a literal.
        // The label and the maths reading two different numbers is the one failure a period
        // control can have that a reader cannot see.
        val n = periodFor(option.id, period)
        return when (option.id) {
            "sma" -> listOf(ChartLine(Indicators.sma(close, n), option.colour, label = "SMA $n"))
            "ema" -> listOf(ChartLine(Indicators.ema(close, n), option.colour, label = "EMA $n"))
            "wma" -> listOf(ChartLine(Indicators.wma(close, n), option.colour, label = "WMA $n"))
            "hma" -> listOf(ChartLine(Indicators.hma(close, n), option.colour, label = "HMA $n"))
            "bollinger" -> Indicators.bollinger(close, n).let { band ->
                // The basis is drawn thinner than its edges: it is a reference, and at equal weight
                // it competes with the two lines a reader is actually watching for a touch.
                listOf(
                    ChartLine(band.upper, option.colour, label = "BB $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "keltner" -> Indicators.keltner(high, low, close).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "KC"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "donchian" -> Indicators.donchian(high, low, n).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "DC $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "ichimoku" -> Indicators.ichimoku(high, low).let { cloud ->
                listOf(
                    ChartLine(cloud.tenkan, option.colour, label = "Ichimoku"),
                    ChartLine(cloud.kijun, 0xFF6E8BE0),
                    ChartLine(cloud.spanA, 0xFF00B15C, widthDp = 0.9f),
                    ChartLine(cloud.spanB, 0xFFF6465D, widthDp = 0.9f),
                )
            }
            "supertrend" -> Indicators.supertrend(high, low, close).let { result ->
                // Broken at each flip rather than drawn as one continuous line.
                //
                // A SuperTrend jumps from below the price to above it when the trend turns, and a
                // line that carries the pen across that jump draws a vertical stroke through the
                // candles that no terminal draws and that reads as a real move. The values are
                // untouched — they are parity-checked against the web app — and only the gap is
                // added, which is exactly what a null in a `Line` means.
                val trend = result.trend
                val split = Line.of(series.size) { index ->
                    val flipped = index > 0 &&
                        trend.isPresent(index) &&
                        trend.isPresent(index - 1) &&
                        trend.raw(index) != trend.raw(index - 1)
                    if (flipped) null else result.line[index]
                }
                listOf(ChartLine(split, option.colour, widthDp = 1.6f, label = "SuperTrend"))
            }
            "vwap" -> listOf(
                ChartLine(
                    Indicators.vwap(high, low, close, series.volume),
                    option.colour,
                    label = "VWAP",
                ),
            )

            // ── The second thirty's price-scale entries ────────────────────────────────────
            "smma" -> listOf(ChartLine(IndicatorsExt.smma(close, n), option.colour, label = "SMMA $n"))
            "zlema" -> listOf(ChartLine(IndicatorsExt.zlema(close, n), option.colour, label = "ZLEMA $n"))
            "kama" -> listOf(ChartLine(IndicatorsExt.kama(close, n), option.colour, label = "KAMA $n"))
            "t3" -> listOf(ChartLine(IndicatorsExt.t3(close, n), option.colour, label = "T3 $n"))
            "mcginley" -> listOf(ChartLine(IndicatorsExt.mcginley(close, n), option.colour, label = "McGinley $n"))
            "linreg" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, n), option.colour, label = "LinReg $n"),
            )
            "lsma" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, n), option.colour, label = "LSMA $n"),
            )
            "envelopes" -> IndicatorsExt.envelopes(close, n).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "Env $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            else -> emptyList()
        }
    }
}
