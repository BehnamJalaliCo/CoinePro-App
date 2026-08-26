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
        IndicatorOption("envelopes", "پاکت درصدی", null, IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_flatchannel),

        // Volatility, on its own scale.
        IndicatorOption("stddev", "انحراف معیار", null, IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_ruler),
        IndicatorOption("hv", "نوسان تاریخی", null, IndicatorPane.SEPARATE, 0xFFF59E0B, DesignR.drawable.tv_ruler),
        IndicatorOption("chaikinVol", "نوسان چایکین", "chaikinVol", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_ruler),
        IndicatorOption("bbpercent", "باند بولینگر ٪B", "bbpercent", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_tool_sine),
        IndicatorOption("bbw", "پهنای باند بولینگر", "bbw", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_ruler),

        // Momentum, on its own scale.
        IndicatorOption("mom", "مومنتوم", null, IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("roc", "نرخ تغییر", null, IndicatorPane.SEPARATE, 0xFFF472B6, DesignR.drawable.tv_tool_sine),
        IndicatorOption("trix", "تریکس (TRIX)", null, IndicatorPane.SEPARATE, 0xFF34D399, DesignR.drawable.tv_tool_sine),
        IndicatorOption("ac", "شتاب‌دهنده", "ac", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_chart_columns),
        IndicatorOption("uo", "اسیلاتور غایی", "uo", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("fisher", "تبدیل فیشر", null, IndicatorPane.SEPARATE, 0xFFFB923C, DesignR.drawable.tv_tool_sine),
        IndicatorOption("crsi", "کانرز RSI", "crsi", IndicatorPane.SEPARATE, 0xFFE879F9, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smiErgodic", "SMI ارگودیک", null, IndicatorPane.SEPARATE, 0xFF38BDF8, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smi", "مومنتوم استوکاستیک", null, IndicatorPane.SEPARATE, 0xFFFACC15, DesignR.drawable.tv_tool_sine),
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
     * Defaults for the two regression curves.
     *
     * A hundred bars for the curve and twenty-five for the projected average, which are the web
     * terminal's — the same series under two periods is two different indicators to a reader, and
     * two products that disagree about which is which are worse than one that offers only one.
     */
    private const val LINREG_PERIOD = 100
    private const val LSMA_PERIOD = 25

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
    fun overlayFor(option: IndicatorOption, series: CandleSeries): List<ChartLine> {
        if (option.pane != IndicatorPane.PRICE || series.isEmpty) return emptyList()
        val close = series.close
        val high = series.high
        val low = series.low
        return when (option.id) {
            "sma" -> listOf(ChartLine(Indicators.sma(close, 20), option.colour, label = "SMA 20"))
            "ema" -> listOf(ChartLine(Indicators.ema(close, 20), option.colour, label = "EMA 20"))
            "wma" -> listOf(ChartLine(Indicators.wma(close, 20), option.colour, label = "WMA 20"))
            "hma" -> listOf(ChartLine(Indicators.hma(close, 20), option.colour, label = "HMA 20"))
            "bollinger" -> Indicators.bollinger(close).let { band ->
                // The basis is drawn thinner than its edges: it is a reference, and at equal weight
                // it competes with the two lines a reader is actually watching for a touch.
                listOf(
                    ChartLine(band.upper, option.colour, label = "BB"),
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
            "donchian" -> Indicators.donchian(high, low).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "DC"),
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
            "supertrend" -> listOf(
                ChartLine(
                    Indicators.supertrend(high, low, close).line,
                    option.colour,
                    widthDp = 1.6f,
                    label = "SuperTrend",
                ),
            )
            "vwap" -> listOf(
                ChartLine(
                    Indicators.vwap(high, low, close, series.volume),
                    option.colour,
                    label = "VWAP",
                ),
            )

            // ── The second thirty's price-scale entries ────────────────────────────────────
            "smma" -> listOf(ChartLine(IndicatorsExt.smma(close, 14), option.colour, label = "SMMA 14"))
            "zlema" -> listOf(ChartLine(IndicatorsExt.zlema(close, 21), option.colour, label = "ZLEMA 21"))
            "kama" -> listOf(ChartLine(IndicatorsExt.kama(close), option.colour, label = "KAMA 10"))
            "t3" -> listOf(ChartLine(IndicatorsExt.t3(close), option.colour, label = "T3 10"))
            "mcginley" -> listOf(ChartLine(IndicatorsExt.mcginley(close), option.colour, label = "McGinley 14"))
            "linreg" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, LINREG_PERIOD), option.colour, label = "LinReg 100"),
            )
            "lsma" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, LSMA_PERIOD), option.colour, label = "LSMA 25"),
            )
            "envelopes" -> IndicatorsExt.envelopes(close).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "Env 1%"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            else -> emptyList()
        }
    }
}
