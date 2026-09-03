package com.coinepro.core.marketdata

import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The zone every calendar boundary in a chart is measured in.
 *
 * Tehran is **UTC+03:30**, and the half hour is the whole point. Arithmetic on epoch seconds —
 * `epoch / 86_400 * 86_400` — puts the daily bar's open at 00:00 UTC, which is 03:30 in Tehran:
 * a reader in Tehran would see the day roll over in the middle of the morning, and the bar labelled
 * with today's date would hold three and a half hours of yesterday. No multiple of an hour fixes
 * that, so no amount of care with a UTC offset constant fixes it either; the boundary has to come
 * from a calendar. Our audience is Iranian, so that calendar is Tehran's unless a caller says
 * otherwise.
 */
val CHART_TIME_ZONE: ZoneId = ZoneId.of("Asia/Tehran")

/**
 * The timeframes the chart offers, from one minute to one month.
 *
 * One enum for both backends, because they agree on the spellings — `M1`, `H4`, `D1` — and a reader
 * switching between a gold chart and a Bitcoin chart must not find the control relabelled. Where
 * they differ is in what they *accept*: TradeYar also takes `1h`/`15m` and echoes back the
 * canonical spelling, CoinePro-FX takes only these. Sending the canonical spelling to both is the
 * intersection, and costs nothing.
 *
 * ## Not every entry here is a request a server will answer
 *
 * The original eight — [M1], [M5], [M15], [M30], [H1], [H4], [D1], [W1] — are what both backends
 * documented and serve natively. The rest ([M2], [M3], [M10], [M45], [H2], [H3], [MN1]) exist
 * because readers coming from TradingView expect them, and they are built by aggregating a finer
 * bar the server does serve. A gateway that forwards one of them verbatim will get an error or,
 * worse, a silently wrong series back, so a caller that can reach the network must map an
 * unsupported entry onto a divisor of it and fold the bars itself.
 *
 * Labels are in Persian digits: a label is prose read aloud as "پانزده دقیقه", not a market figure
 * a reader compares against another terminal.
 */
enum class Timeframe(val wire: String, val seconds: Long, val label: String) {
    M1("M1", 60, "۱ دقیقه"),
    M2("M2", 120, "۲ دقیقه"),
    M3("M3", 180, "۳ دقیقه"),
    M5("M5", 300, "۵ دقیقه"),
    M10("M10", 600, "۱۰ دقیقه"),
    M15("M15", 900, "۱۵ دقیقه"),
    M30("M30", 1_800, "۳۰ دقیقه"),
    M45("M45", 2_700, "۴۵ دقیقه"),
    H1("H1", 3_600, "۱ ساعت"),
    H2("H2", 7_200, "۲ ساعت"),
    H3("H3", 10_800, "۳ ساعت"),
    H4("H4", 14_400, "۴ ساعت"),
    D1("D1", 86_400, "۱ روز"),
    W1("W1", 604_800, "۱ هفته"),

    /**
     * One month, whose [seconds] is thirty days and is therefore **nominal**.
     *
     * A month is not a fixed number of seconds — the ones this app will draw run from 28 to 31 days,
     * and a Persian calendar month is a different set of lengths again. The constant is here so that
     * a monthly bar can be spaced on an axis, sized in a cache key and compared against the other
     * entries for "which is coarser"; it is not an answer to "when does this bar open". For that,
     * and for any arithmetic a reader will see the result of, use [bucketStart], which asks a
     * calendar. Multiplying this constant by a bar count drifts by up to three days a year.
     */
    MN1("MN1", 2_592_000, "۱ ماه"),
    ;

    /**
     * The open time of the bar that contains [epochSeconds], in unix seconds.
     *
     * Intraday bars are laid out on the epoch itself, because that is what both servers do: an
     * hourly bar opens on the hour UTC, and folding finer bars any other way would produce a series
     * that disagrees with the one the server sends for the same period. From [D1] upward the
     * boundary stops being arithmetic and becomes a calendar fact — a day is when the reader's
     * midnight is, a week starts on the day their region starts it, and a month is however long
     * that month happens to be — so those three ask [java.time] in [zone]. See [CHART_TIME_ZONE]
     * for why the default is Tehran and why a half-hour offset makes this more than pedantry.
     */
    fun bucketStart(epochSeconds: Long, zone: ZoneId = CHART_TIME_ZONE): Long = when (this) {
        D1 -> dateIn(epochSeconds, zone).atStartOfDay(zone).toEpochSecond()
        W1 -> dateIn(epochSeconds, zone)
            .with(TemporalAdjusters.previousOrSame(weekStartIn(zone)))
            .atStartOfDay(zone)
            .toEpochSecond()
        MN1 -> dateIn(epochSeconds, zone).withDayOfMonth(1).atStartOfDay(zone).toEpochSecond()
        else -> Math.floorDiv(epochSeconds, seconds) * seconds
    }

    companion object {
        /** Tolerant of either spelling, because a saved layout may carry the other one. */
        fun of(wire: String?): Timeframe? {
            val clean = wire?.trim()?.uppercase() ?: return null
            entries.firstOrNull { it.wire == clean }?.let { return it }
            // `15M` / `1H` / `1MN` — TradeYar's alternate spelling, reversed. `MN` is matched before
            // the single letters so that `1MN` is a month; `1M` stays one minute, which is what
            // every caller that has ever sent it meant, whatever TradingView does with the same
            // string.
            val alternate = Regex("^(\\d+)(MN|[MHDW])$").matchEntire(clean) ?: return null
            return entries.firstOrNull { it.wire == alternate.groupValues[2] + alternate.groupValues[1] }
        }
    }
}

/**
 * An interval a reader typed rather than picked, measured in whole minutes.
 *
 * The cap is TradingView's own: it accepts custom minute intervals up to 1440, one day, and refuses
 * anything longer on the grounds that beyond a day the reader wants a daily, weekly or monthly bar
 * with calendar boundaries rather than a very long minute bar with arithmetic ones. Borrowing the
 * number keeps a layout imported from there importable, and the reasoning holds for us too.
 *
 * The constructor rejects anything outside 1..1440 rather than clamping, because a clamp turns a
 * typo into a chart that quietly is not what was asked for. Callers parsing text should go through
 * [customOf], which answers `null` instead of throwing.
 */
data class CustomInterval(val minutes: Int) {

    init {
        require(minutes in 1..1440) { "A custom interval is 1..1440 minutes, not $minutes." }
    }

    /** What goes on the wire and into a saved layout: the bare minute count, as TradingView spells it. */
    val wire: String get() = minutes.toString()

    /** The bar's length, in the same unit [Timeframe.seconds] uses, so the two are interchangeable. */
    val seconds: Long get() = minutes * 60L

    /**
     * The control's caption, in Persian digits, because a caption is prose and not a market figure.
     *
     * Hours win when the count divides evenly — «۲ ساعت» reads better than «۱۲۰ دقیقه» and is what
     * the preset next to it says — and everything else stays in minutes, so 205 is «۲۰۵ دقیقه». The
     * cap makes 1440 the largest, which reads as «۲۴ ساعت»; it deliberately does not become "۱ روز",
     * because a daily bar opens at the reader's midnight and this one does not necessarily.
     */
    val label: String
        get() = if (minutes % 60 == 0) {
            "${(minutes / 60).toPersianDigits()} ساعت"
        } else {
            "${minutes.toPersianDigits()} دقیقه"
        }

    /**
     * The open time of the bar containing [epochSeconds], anchored to midnight in [zone].
     *
     * A custom interval rarely divides the day or the hour, so there is no natural boundary on the
     * epoch to hang it on: 205 minutes counted from 1970 opens bars at an arbitrary and drifting
     * time of day. Counting from the reader's own midnight is what TradingView does and what makes
     * the first bar of a session start with the session, and it also gets the one case that would
     * otherwise be plainly wrong right — 1440 minutes becomes the reader's day rather than a UTC day
     * three and a half hours out of step. The price is that a 60-minute custom interval is not the
     * same series as [Timeframe.H1] in Tehran; a reader who wants the server's hourly bars should
     * pick the preset, which is why the preset exists.
     */
    fun bucketStart(epochSeconds: Long, zone: ZoneId = CHART_TIME_ZONE): Long {
        // `atStartOfDay` rather than a midnight literal, because on a date whose midnight does not
        // exist — a DST spring forward, which Iran had until 2022 and other zones still have — it
        // returns the first instant that does. `floorDiv` then keeps the arithmetic correct on the
        // wrong side of the anchor too, where plain integer division rounds towards zero and would
        // hand back the *next* bar's open for any instant before midnight.
        val dayStart = dateIn(epochSeconds, zone).atStartOfDay(zone).toEpochSecond()
        return dayStart + Math.floorDiv(epochSeconds - dayStart, seconds) * seconds
    }
}

/**
 * Reads a bare minute count — «۲۰۵» or `205` — as a [CustomInterval], or `null` if it is not one.
 *
 * Persian digits are folded first because that is what an Iranian keyboard produces by default, and
 * a field that refuses ۲۰۵ while accepting 205 is a field that looks broken. Out-of-range and
 * non-numeric input answers `null` rather than throwing: this parses text a person is still typing,
 * where "not yet a valid interval" is the normal state and not an error worth an exception.
 */
fun customOf(wire: String?): CustomInterval? {
    val clean = wire?.trim()?.foldDigitsToLatin() ?: return null
    if (clean.isEmpty() || clean.any { it !in '0'..'9' }) return null
    val minutes = clean.toIntOrNull() ?: return null
    return if (minutes in 1..1440) CustomInterval(minutes) else null
}

/**
 * What the chart is actually drawing: one of the presets, or an interval the reader made up.
 *
 * The two are kept as one type because everything downstream of the picker — the request, the cache
 * key, the axis, the saved layout — needs a length, a caption and a wire spelling, and has no reason
 * to care which of the two produced them. Keeping them apart instead means every one of those call
 * sites grows a branch, and the branch that gets forgotten is the one that silently draws hourly
 * bars for a reader who asked for 205 minutes.
 */
sealed interface ChartInterval {

    /** The spelling to send and to save, canonical for a preset and the bare minute count for a custom one. */
    val wire: String

    /** The bar's nominal length in seconds — nominal in the [Timeframe.MN1] sense, so not a boundary. */
    val seconds: Long

    /** The caption, Persian digits either way, because a caption is prose. */
    val label: String

    /** The open time of the bar containing [epochSeconds]; see [Timeframe.bucketStart] for the rules. */
    fun bucketStart(epochSeconds: Long, zone: ZoneId = CHART_TIME_ZONE): Long

    /** One of the fifteen the picker lists, and the only kind either backend serves directly. */
    data class Preset(val timeframe: Timeframe) : ChartInterval {
        override val wire: String get() = timeframe.wire
        override val seconds: Long get() = timeframe.seconds
        override val label: String get() = timeframe.label
        override fun bucketStart(epochSeconds: Long, zone: ZoneId): Long =
            timeframe.bucketStart(epochSeconds, zone)
    }

    /**
     * A bar shorter than a minute, built on the phone out of the price feed.
     *
     * ### Why this is a third kind and not a fourteenth preset
     *
     * «ما تایم‌فریم ۱۰ ثانیه تا ۵۰ ثانیه هم باید داشته باشیم.» Neither backend serves one. TradeYar's
     * candle route lists `1m 3m 5m …` and CoinePro-FX's starts at M5, so there is no request that
     * fetches a ten-second bar and no series to fold one out of — a minute candle cannot be cut into
     * six. [Preset] and [Custom] are both "ask the server, possibly fold"; this one has no server
     * behind it at all, which is exactly why it is its own type rather than a [Timeframe] entry that
     * every gateway would have to remember to refuse.
     *
     * What it *does* have is the price socket, which reports far more often than once a minute. So a
     * seconds chart is drawn from ticks: each one lands in its bucket, the bucket is a bar, and the
     * bars accumulate in the archive the same way every other series does — so the second visit to a
     * ten-second chart opens on the history the first visit built rather than on nothing.
     *
     * ### The five lengths, and why the set is closed
     *
     * Ten to fifty seconds is what was asked for, and [SECONDS_KEYS] is that range at the steps a
     * trader actually names. It is a fixed list rather than a free number because unlike a custom
     * minute count there is nothing to type into: these are keys on a strip, and a bar length nobody
     * can reach is a bar length nobody has to maintain a bucket rule for.
     */
    data class Seconds(val count: Int) : ChartInterval {

        init {
            require(count in SECONDS_KEYS) { "A seconds bar is one of $SECONDS_KEYS, not $count." }
        }

        /** `10S`, `30S` — TradingView's own spelling, and distinct from every [Timeframe.wire]. */
        override val wire: String get() = "${count}S"

        override val seconds: Long get() = count.toLong()

        override val label: String get() = "${count.toPersianDigits()} ثانیه"

        /**
         * On the epoch, like every intraday bar in this file.
         *
         * The zone is ignored and that is correct rather than lazy: a sub-minute boundary is not a
         * calendar fact in any zone this app supports, all of which are offset from UTC by a whole
         * number of minutes — so midnight in Tehran and midnight in UTC put a ten-second bar on the
         * same ten-second grid, and anchoring to one of them would only make the arithmetic harder
         * to check.
         */
        override fun bucketStart(epochSeconds: Long, zone: ZoneId): Long =
            Math.floorDiv(epochSeconds, count.toLong()) * count
    }

    /** A minute count the reader typed, aggregated on the client from a finer bar. */
    data class Custom(val interval: CustomInterval) : ChartInterval {
        override val wire: String get() = interval.wire
        override val seconds: Long get() = interval.seconds
        override val label: String get() = interval.label
        override fun bucketStart(epochSeconds: Long, zone: ZoneId): Long =
            interval.bucketStart(epochSeconds, zone)
    }

    companion object
}

/**
 * Resolves a saved or received spelling to whichever kind it is, presets first.
 *
 * Order matters and is not arbitrary: the preset table is tried before the number parse so that a
 * layout carrying `15M` keeps resolving to [Timeframe.M15] rather than becoming a fifteen-minute
 * custom interval that looks identical and caches under a different key. Only a string no preset
 * claims is read as a minute count.
 */
fun ChartInterval.Companion.of(wire: String?): ChartInterval? {
    Timeframe.of(wire)?.let { return ChartInterval.Preset(it) }
    secondsOf(wire)?.let { return it }
    return customOf(wire)?.let { ChartInterval.Custom(it) }
}

/**
 * The five sub-minute bar lengths this app builds from ticks, shortest first.
 *
 * Ten to fifty seconds, which is the range the owner asked for, at the steps a trader names one.
 * Fifty is included because the range was named inclusively and not because fifty divides anything:
 * a fifty-second bar sits on the epoch's fifty-second grid and drifts against the minute, which is
 * true of every seconds chart that is not a divisor of sixty and is not a defect.
 */
val SECONDS_KEYS: List<Int> = listOf(10, 15, 20, 30, 45, 50)

/**
 * Reads `10S`, `30s` or a bare `45S` as a [ChartInterval.Seconds], or null.
 *
 * Tried after the preset table and before the minute count, which is the only order that works:
 * `Timeframe.of` claims `1M`, and `customOf` would read the digits of `10S` as ten minutes if the
 * suffix were ignored. Anything outside [SECONDS_KEYS] is null rather than clamped — a saved layout
 * naming a length this build does not have should fall back to a default, not to a neighbour.
 */
fun secondsOf(wire: String?): ChartInterval.Seconds? {
    val clean = wire?.trim()?.uppercase()?.takeIf { it.length >= 2 && it.endsWith("S") } ?: return null
    val count = clean.dropLast(1).toIntOrNull() ?: return null
    return if (count in SECONDS_KEYS) ChartInterval.Seconds(count) else null
}

/** The reader's calendar date at [epochSeconds], which is the only thing a day boundary can be read from. */
private fun dateIn(epochSeconds: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

/**
 * The day a week starts on, which is a regional fact and not a property of the offset.
 *
 * Iran's week starts on Saturday, as it does across the Gulf, and a weekly bar that opened on Monday
 * would put the two quietest days of an Iranian week in the middle of it rather than at its end.
 * Everywhere else this falls back to the ISO Monday. The zone is the closest thing to a region this
 * layer has: it is what the caller already passes, and it is right for every reader who has not gone
 * out of their way to run an Iranian chart on a foreign clock.
 */
private fun weekStartIn(zone: ZoneId): DayOfWeek =
    if (zone.id in SATURDAY_WEEK_ZONES) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

private val SATURDAY_WEEK_ZONES = setOf(
    "Asia/Tehran",
    "Asia/Kabul",
    "Asia/Baghdad",
    "Asia/Bahrain",
    "Asia/Dubai",
    "Asia/Kuwait",
    "Asia/Muscat",
    "Asia/Qatar",
    "Asia/Riyadh",
)

/**
 * One bar, on the wire.
 *
 * Deliberately not `core:chart`'s `Candle`, and the boundary is worth keeping: `core:chart` is a
 * Compose module, and a network layer that depends on it drags a UI toolkit into every gateway.
 * The mapping is four fields wide and lives at the one call site that needs it.
 *
 * [t] is **unix seconds** and is the bar's *open* time. Both backends confirmed that in writing,
 * and both contrasted it with their own price sockets, which use milliseconds — so the one thing
 * that must not happen here is a stray ×1000.
 */
data class OhlcBar(
    val t: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double,
    /**
     * Whether the bar has finished.
     *
     * TradeYar sends this per bar; CoinePro-FX does not send it at all, and there it is derived
     * from the bar's own open time against the server clock. Either way the last bar of a live
     * chart is usually `false`, and a reader who does not know that will read a half-formed bar as
     * a real one.
     */
    val closed: Boolean = true,
)

/**
 * One page of history, and enough to ask for the next one without guessing.
 *
 * [oldest] and [hasMore] are TradeYar's, and they are the difference between paging that works and
 * paging that stops one page early: without them a caller has to infer "there is more" from the
 * page being full, which is wrong exactly when the last page happens to be full.
 */
data class CandlePage(
    val symbol: String,
    val timeframe: Timeframe,
    /** Oldest first. Both backends promise it and one of them enforces it server-side. */
    val candles: List<OhlcBar>,
    val oldest: Long? = null,
    val hasMore: Boolean = false,
    /** The server's own cap, so the app does not have to carry a number that can change. */
    val limitMax: Int? = null,
    /**
     * What the reader actually asked for. Prefer this over [timeframe].
     *
     * The two differ exactly when the interval is not one the backend serves. Neither backend has
     * M2, M45, MN1 or any custom minute count, so those are fetched at a divisor the server does
     * have and folded on the client — and in that case [timeframe] names the *feed* the bars came
     * from, not the bars in [candles]. Reading [timeframe] there gives a caller the wrong bar
     * length, which is the sort of mistake that produces a chart nobody can see is wrong.
     *
     * Null on a page built before this field existed; a caller may fall back to
     * `ChartInterval.Preset(timeframe)` when it knows it only ever asked for a preset.
     */
    val interval: ChartInterval? = null,
) {
    val isEmpty: Boolean get() = candles.isEmpty()

    /** [interval] when it is known, and the preset the feed named otherwise. */
    val effectiveInterval: ChartInterval get() = interval ?: ChartInterval.Preset(timeframe)
}
