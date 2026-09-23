@file:Suppress("unused", "MemberVisibilityCanBePrivate")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjuster
import kotlin.math.abs

/*
 * The part of `java.time` the phone's code calls, for the browser.
 *
 * Kotlin/Wasm has no JDK, and the shared sources were written against one. Rather than fork every
 * file that formats a date, this gives them the same names with the same meaning: epoch arithmetic
 * is Hinnant's civil-date algorithms, and a named zone's offset at an instant comes from the
 * browser's own time-zone database (`Intl.DateTimeFormat`), so Asia/Tehran, Europe/London and
 * daylight saving behave as they do on the phone. What is not here was not called.
 */

// ── The browser's clock and zones ──────────────────────────────────────────────────────────────

private fun nowMillisJs(): Double = js("Date.now()")

/** Seconds east of UTC for [zone] at [epochMillis], from the browser's tz database; NaN if unknown. */
private fun zoneOffsetSecondsJs(zone: String, epochMillis: Double): Double = js(
    """(function () {
        try {
            var cache = globalThis.__pcZoneFormatters || (globalThis.__pcZoneFormatters = {});
            var f = cache[zone] || (cache[zone] = new Intl.DateTimeFormat('en-US', {
                timeZone: zone, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric',
                hour: 'numeric', minute: 'numeric', second: 'numeric' }));
            var parts = f.formatToParts(new Date(epochMillis));
            var v = {};
            for (var i = 0; i < parts.length; i++) v[parts[i].type] = parseInt(parts[i].value, 10);
            var local = Date.UTC(v.year, v.month - 1, v.day, v.hour % 24, v.minute, v.second);
            var whole = epochMillis - (((epochMillis % 1000) + 1000) % 1000);
            return (local - whole) / 1000;
        } catch (e) { return NaN; }
    })()""",
)

private fun systemZoneIdJs(): String =
    js("(function () { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch (e) { return 'UTC'; } })()")

private fun availableZonesJs(): String =
    js("(function () { try { return Intl.supportedValuesOf('timeZone').join(','); } catch (e) { return 'UTC'; } })()")

// ── Civil calendar arithmetic ──────────────────────────────────────────────────────────────────

internal fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if ((a % b != 0L) && ((a xor b) < 0L)) q - 1 else q
}

internal fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

internal fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
}

internal fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

internal fun monthLength(year: Int, month: Int): Int = when (month) {
    2 -> if (isLeap(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

open class DateTimeException(message: String? = null) : RuntimeException(message)

// ── Duration ───────────────────────────────────────────────────────────────────────────────────

class Duration private constructor(val seconds: Long, val nano: Int) : Comparable<Duration> {
    fun toMillis(): Long = seconds * 1000 + nano / 1_000_000
    fun toNanos(): Long = seconds * 1_000_000_000 + nano
    fun toSeconds(): Long = seconds
    fun toMinutes(): Long = seconds / 60
    fun toHours(): Long = seconds / 3600
    fun toDays(): Long = seconds / 86_400
    fun toMinutesPart(): Int = ((seconds / 60) % 60).toInt()
    fun toSecondsPart(): Int = (seconds % 60).toInt()
    fun toHoursPart(): Int = ((seconds / 3600) % 24).toInt()
    fun getSeconds(): Long = seconds
    val isNegative: Boolean get() = seconds < 0
    val isZero: Boolean get() = seconds == 0L && nano == 0
    fun plus(other: Duration): Duration = ofMillis(toMillis() + other.toMillis())
    fun minus(other: Duration): Duration = ofMillis(toMillis() - other.toMillis())
    fun plusSeconds(s: Long): Duration = ofSeconds(seconds + s, nano.toLong())
    fun plusMinutes(m: Long): Duration = plusSeconds(m * 60)
    fun plusHours(h: Long): Duration = plusSeconds(h * 3600)
    fun plusDays(d: Long): Duration = plusSeconds(d * 86_400)
    fun multipliedBy(n: Long): Duration = ofMillis(toMillis() * n)
    fun dividedBy(n: Long): Duration = ofMillis(toMillis() / n)
    fun negated(): Duration = ofMillis(-toMillis())
    fun abs(): Duration = if (isNegative) negated() else this
    override fun compareTo(other: Duration): Int = compareValuesBy(this, other, { it.seconds }, { it.nano })
    override fun equals(other: Any?): Boolean = other is Duration && other.seconds == seconds && other.nano == nano
    override fun hashCode(): Int = seconds.hashCode() * 31 + nano
    override fun toString(): String = "PT${seconds}S"

    companion object {
        val ZERO = Duration(0, 0)
        fun ofSeconds(s: Long): Duration = Duration(s, 0)
        fun ofSeconds(s: Long, nanoAdjustment: Long): Duration {
            val total = s + floorDiv(nanoAdjustment, 1_000_000_000)
            return Duration(total, floorMod(nanoAdjustment, 1_000_000_000).toInt())
        }
        fun ofMillis(ms: Long): Duration = Duration(floorDiv(ms, 1000), (floorMod(ms, 1000) * 1_000_000).toInt())
        fun ofNanos(n: Long): Duration = ofSeconds(0, n)
        fun ofMinutes(m: Long): Duration = Duration(m * 60, 0)
        fun ofHours(h: Long): Duration = Duration(h * 3600, 0)
        fun ofDays(d: Long): Duration = Duration(d * 86_400, 0)
        fun of(amount: Long, unit: ChronoUnit): Duration = ofMillis(amount * unit.millis)
        fun between(start: Instant, end: Instant): Duration =
            ofSeconds(end.epochSecond - start.epochSecond, (end.nano - start.nano).toLong())
        fun between(start: ZonedDateTime, end: ZonedDateTime): Duration = between(start.toInstant(), end.toInstant())
        fun between(start: LocalDateTime, end: LocalDateTime): Duration =
            ofSeconds(end.toEpochSecond(ZoneOffset.UTC) - start.toEpochSecond(ZoneOffset.UTC))
    }
}

// ── Instant ────────────────────────────────────────────────────────────────────────────────────

class Instant private constructor(val epochSecond: Long, val nano: Int) : Comparable<Instant> {
    fun toEpochMilli(): Long = epochSecond * 1000 + nano / 1_000_000
    fun getEpochSecond(): Long = epochSecond
    fun plusSeconds(s: Long): Instant = ofEpochSecond(epochSecond + s, nano.toLong())
    fun minusSeconds(s: Long): Instant = plusSeconds(-s)
    fun plusMillis(ms: Long): Instant = ofEpochMilli(toEpochMilli() + ms)
    fun minusMillis(ms: Long): Instant = plusMillis(-ms)
    fun plusNanos(n: Long): Instant = ofEpochSecond(epochSecond, nano + n)
    fun plus(d: Duration): Instant = ofEpochSecond(epochSecond + d.seconds, (nano + d.nano).toLong())
    fun minus(d: Duration): Instant = ofEpochSecond(epochSecond - d.seconds, (nano - d.nano).toLong())
    fun plus(amount: Long, unit: ChronoUnit): Instant = plusMillis(amount * unit.millis)
    fun minus(amount: Long, unit: ChronoUnit): Instant = plusMillis(-amount * unit.millis)
    fun isBefore(other: Instant): Boolean = this < other
    fun isAfter(other: Instant): Boolean = this > other
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.ofInstant(this, offset)
    fun truncatedTo(unit: ChronoUnit): Instant =
        if (unit == ChronoUnit.NANOS) this else ofEpochMilli(floorDiv(toEpochMilli(), unit.millis) * unit.millis)
    fun until(end: Instant, unit: ChronoUnit): Long = (end.toEpochMilli() - toEpochMilli()) / unit.millis
    override fun compareTo(other: Instant): Int = compareValuesBy(this, other, { it.epochSecond }, { it.nano })
    override fun equals(other: Any?): Boolean = other is Instant && other.epochSecond == epochSecond && other.nano == nano
    override fun hashCode(): Int = epochSecond.hashCode() * 31 + nano
    override fun toString(): String = DateTimeFormatter.ISO_INSTANT.format(this)

    companion object {
        val EPOCH = Instant(0, 0)
        val MIN = Instant(-31_557_014_167_219_200L, 0)
        val MAX = Instant(31_556_889_864_403_199L, 999_999_999)
        fun ofEpochSecond(s: Long): Instant = Instant(s, 0)
        fun ofEpochSecond(s: Long, nanoAdjustment: Long): Instant =
            Instant(s + floorDiv(nanoAdjustment, 1_000_000_000), floorMod(nanoAdjustment, 1_000_000_000).toInt())
        fun ofEpochMilli(ms: Long): Instant = Instant(floorDiv(ms, 1000), (floorMod(ms, 1000) * 1_000_000).toInt())
        fun now(): Instant = ofEpochMilli(nowMillisJs().toLong())
        fun now(clock: Clock): Instant = clock.instant()
        fun parse(text: CharSequence): Instant = DateTimeFormatter.parseIsoInstant(text.toString())
        fun from(temporal: Any): Instant = when (temporal) {
            is Instant -> temporal
            is ZonedDateTime -> temporal.toInstant()
            is OffsetDateTime -> temporal.toInstant()
            else -> throw DateTimeException("Unable to obtain Instant from $temporal")
        }
    }
}

// ── Zones ──────────────────────────────────────────────────────────────────────────────────────

class ZoneRules internal constructor(private val zone: ZoneId) {
    fun getOffset(instant: Instant): ZoneOffset = ZoneOffset.ofTotalSeconds(zone.offsetSecondsAt(instant.epochSecond))
    fun getOffset(dateTime: LocalDateTime): ZoneOffset = ZoneOffset.ofTotalSeconds(zone.offsetForLocal(dateTime))
    fun isDaylightSavings(instant: Instant): Boolean = false
    val isFixedOffset: Boolean get() = zone is ZoneOffset
}

/**
 * A zone is also the chart's zone: the phone passes a `ZoneId` where the chart takes its
 * `ChartTimeZone` (a typealias on Android), and in the browser the one type is the other.
 */
abstract class ZoneId internal constructor() : com.coinepro.core.chart.ChartTimeZone() {
    abstract val id: String
    override val zone: com.coinepro.core.chart.ChartZone
        get() = com.coinepro.core.chart.ChartZone { offsetSecondsAt(it).toLong() }
    fun getId(): String = id
    val rules: ZoneRules get() = ZoneRules(this)
    fun getRules(): ZoneRules = rules
    internal abstract fun offsetSecondsAt(epochSecond: Long): Int

    /** The offset in force at a local wall time — the earlier one in an overlap, as java.time does. */
    internal fun offsetForLocal(local: LocalDateTime): Int {
        val guess = local.toEpochSecond(ZoneOffset.UTC)
        val first = offsetSecondsAt(guess)
        val second = offsetSecondsAt(guess - first)
        return if (offsetSecondsAt(guess - second) == second) second else maxOf(first, second)
    }

    fun normalized(): ZoneId = this
    override fun equals(other: Any?): Boolean = other is ZoneId && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    companion object {
        fun of(id: String): ZoneId {
            if (id == "Z" || id == "UTC" || id == "GMT" || id == "UT") return if (id == "Z") ZoneOffset.UTC else RegionZone(id)
            if (id.startsWith("+") || id.startsWith("-")) return ZoneOffset.of(id)
            if (id.startsWith("UTC+") || id.startsWith("UTC-") || id.startsWith("GMT+") || id.startsWith("GMT-")) {
                return ZoneOffset.of(id.substring(3))
            }
            if (zoneOffsetSecondsJs(id, 0.0).isNaN()) throw DateTimeException("Unknown time-zone ID: $id")
            return RegionZone(id)
        }
        fun systemDefault(): ZoneId = RegionZone(systemZoneIdJs())
        fun getAvailableZoneIds(): Set<String> = availableZonesJs().split(',').filter { it.isNotEmpty() }.toSet()
        val SHORT_IDS: Map<String, String> = emptyMap()
    }
}

internal class RegionZone(override val id: String) : ZoneId() {
    override fun offsetSecondsAt(epochSecond: Long): Int {
        val seconds = zoneOffsetSecondsJs(id, epochSecond * 1000.0)
        return if (seconds.isNaN()) 0 else seconds.toInt()
    }
}

class ZoneOffset private constructor(val totalSeconds: Int) : ZoneId() {
    override val id: String = if (totalSeconds == 0) "Z" else buildString {
        append(if (totalSeconds < 0) '-' else '+')
        val a = abs(totalSeconds)
        append((a / 3600).toString().padStart(2, '0')); append(':')
        append(((a / 60) % 60).toString().padStart(2, '0'))
        if (a % 60 != 0) { append(':'); append((a % 60).toString().padStart(2, '0')) }
    }
    fun getTotalSeconds(): Int = totalSeconds
    override fun offsetSecondsAt(epochSecond: Long): Int = totalSeconds

    companion object {
        val UTC = ZoneOffset(0)
        val MIN = ZoneOffset(-18 * 3600)
        val MAX = ZoneOffset(18 * 3600)
        fun ofTotalSeconds(seconds: Int): ZoneOffset = if (seconds == 0) UTC else ZoneOffset(seconds)
        fun ofHours(h: Int): ZoneOffset = ofTotalSeconds(h * 3600)
        fun ofHoursMinutes(h: Int, m: Int): ZoneOffset = ofTotalSeconds(h * 3600 + m * 60 * (if (h < 0) -1 else 1))
        fun of(text: String): ZoneOffset {
            if (text == "Z" || text == "z") return UTC
            val sign = when (text.firstOrNull()) { '+' -> 1; '-' -> -1; else -> throw DateTimeException("Invalid offset $text") }
            val body = text.substring(1).replace(":", "")
            val h = body.take(2).toInt()
            val m = body.drop(2).take(2).ifEmpty { "0" }.toInt()
            val s = body.drop(4).take(2).ifEmpty { "0" }.toInt()
            return ofTotalSeconds(sign * (h * 3600 + m * 60 + s))
        }
    }
}

// ── Clock ──────────────────────────────────────────────────────────────────────────────────────

abstract class Clock {
    abstract val zone: ZoneId
    fun getZone(): ZoneId = zone
    abstract fun instant(): Instant
    open fun millis(): Long = instant().toEpochMilli()
    abstract fun withZone(zone: ZoneId): Clock

    private class System(override val zone: ZoneId) : Clock() {
        override fun instant(): Instant = Instant.now()
        override fun withZone(zone: ZoneId): Clock = System(zone)
    }

    private class Fixed(private val at: Instant, override val zone: ZoneId) : Clock() {
        override fun instant(): Instant = at
        override fun withZone(zone: ZoneId): Clock = Fixed(at, zone)
    }

    private class Offset(private val base: Clock, private val by: Duration) : Clock() {
        override val zone: ZoneId get() = base.zone
        override fun instant(): Instant = base.instant().plus(by)
        override fun withZone(zone: ZoneId): Clock = Offset(base.withZone(zone), by)
    }

    companion object {
        fun systemUTC(): Clock = System(ZoneOffset.UTC)
        fun systemDefaultZone(): Clock = System(ZoneId.systemDefault())
        fun system(zone: ZoneId): Clock = System(zone)
        fun fixed(instant: Instant, zone: ZoneId): Clock = Fixed(instant, zone)
        fun offset(base: Clock, duration: Duration): Clock = Offset(base, duration)
    }
}

// ── Calendar enums ─────────────────────────────────────────────────────────────────────────────

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val value: Int get() = ordinal + 1
    fun getValue(): Int = value
    fun plus(days: Long): DayOfWeek = entries[floorMod(ordinal + days, 7).toInt()]
    fun minus(days: Long): DayOfWeek = plus(-days)
    fun getDisplayName(style: java.time.format.TextStyle, locale: java.util.Locale): String =
        name.lowercase().replaceFirstChar { it.uppercase() }.let { if (style == java.time.format.TextStyle.SHORT) it.take(3) else it }

    companion object {
        fun of(value: Int): DayOfWeek = entries[value - 1]
        fun from(temporal: Any): DayOfWeek = when (temporal) {
            is LocalDate -> temporal.dayOfWeek
            is LocalDateTime -> temporal.dayOfWeek
            is ZonedDateTime -> temporal.dayOfWeek
            else -> throw DateTimeException("Unable to obtain DayOfWeek")
        }
    }
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1
    fun getValue(): Int = value
    fun plus(months: Long): Month = entries[floorMod(ordinal + months, 12).toInt()]
    fun minus(months: Long): Month = plus(-months)
    fun length(leapYear: Boolean): Int = if (this == FEBRUARY) (if (leapYear) 29 else 28) else monthLength(2001, value)
    fun getDisplayName(style: java.time.format.TextStyle, locale: java.util.Locale): String =
        name.lowercase().replaceFirstChar { it.uppercase() }.let { if (style == java.time.format.TextStyle.SHORT) it.take(3) else it }

    companion object {
        fun of(value: Int): Month = entries[value - 1]
    }
}

// ── Local date and time ────────────────────────────────────────────────────────────────────────

class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<LocalDate> {
    val month: Month get() = Month.of(monthValue)
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[floorMod(toEpochDay() + 3, 7).toInt()]
    val dayOfYear: Int get() = (toEpochDay() - daysFromCivil(year, 1, 1)).toInt() + 1
    val isLeapYear: Boolean get() = isLeap(year)
    fun getYear(): Int = year
    fun getMonthValue(): Int = monthValue
    fun getDayOfMonth(): Int = dayOfMonth
    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun lengthOfYear(): Int = if (isLeapYear) 366 else 365
    fun toEpochDay(): Long = daysFromCivil(year, monthValue, dayOfMonth)
    fun plusDays(n: Long): LocalDate = ofEpochDay(toEpochDay() + n)
    fun minusDays(n: Long): LocalDate = plusDays(-n)
    fun plusWeeks(n: Long): LocalDate = plusDays(n * 7)
    fun minusWeeks(n: Long): LocalDate = plusDays(-n * 7)
    fun plusMonths(n: Long): LocalDate {
        val total = year * 12L + (monthValue - 1) + n
        val y = floorDiv(total, 12).toInt()
        val m = floorMod(total, 12).toInt() + 1
        return of(y, m, minOf(dayOfMonth, monthLength(y, m)))
    }
    fun minusMonths(n: Long): LocalDate = plusMonths(-n)
    fun plusYears(n: Long): LocalDate = plusMonths(n * 12)
    fun minusYears(n: Long): LocalDate = plusMonths(-n * 12)
    fun plus(amount: Long, unit: ChronoUnit): LocalDate = when (unit) {
        ChronoUnit.DAYS -> plusDays(amount)
        ChronoUnit.WEEKS -> plusWeeks(amount)
        ChronoUnit.MONTHS -> plusMonths(amount)
        ChronoUnit.YEARS -> plusYears(amount)
        else -> throw DateTimeException("Unsupported unit $unit")
    }
    fun minus(amount: Long, unit: ChronoUnit): LocalDate = plus(-amount, unit)
    fun withDayOfMonth(day: Int): LocalDate = of(year, monthValue, day)
    fun withMonth(month: Int): LocalDate = of(year, month, minOf(dayOfMonth, monthLength(year, month)))
    fun withYear(y: Int): LocalDate = of(y, monthValue, minOf(dayOfMonth, monthLength(y, monthValue)))
    fun withDayOfYear(day: Int): LocalDate = ofEpochDay(daysFromCivil(year, 1, 1) + day - 1)
    fun with(adjuster: TemporalAdjuster): LocalDate = adjuster.adjustInto(this) as LocalDate
    fun atStartOfDay(): LocalDateTime = LocalDateTime.of(this, LocalTime.MIDNIGHT)
    fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(atStartOfDay(), zone)
    fun atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)
    fun atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime.of(this, LocalTime.of(hour, minute))
    fun atTime(hour: Int, minute: Int, second: Int): LocalDateTime = LocalDateTime.of(this, LocalTime.of(hour, minute, second))
    fun isBefore(other: LocalDate): Boolean = this < other
    fun isAfter(other: LocalDate): Boolean = this > other
    fun isEqual(other: LocalDate): Boolean = this == other
    fun until(end: LocalDate, unit: ChronoUnit): Long = when (unit) {
        ChronoUnit.DAYS -> end.toEpochDay() - toEpochDay()
        ChronoUnit.WEEKS -> (end.toEpochDay() - toEpochDay()) / 7
        ChronoUnit.MONTHS -> monthsUntil(end)
        ChronoUnit.YEARS -> monthsUntil(end) / 12
        else -> throw DateTimeException("Unsupported unit $unit")
    }
    private fun monthsUntil(end: LocalDate): Long {
        var months = (end.year * 12L + end.monthValue) - (year * 12L + monthValue)
        if (months > 0 && end.dayOfMonth < dayOfMonth) months-- else if (months < 0 && end.dayOfMonth > dayOfMonth) months++
        return months
    }
    fun datesUntil(end: LocalDate): Sequence<LocalDate> =
        generateSequence(this) { it.plusDays(1) }.takeWhile { it < end }
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: LocalDate): Int = toEpochDay().compareTo(other.toEpochDay())
    override fun equals(other: Any?): Boolean = other is LocalDate && other.toEpochDay() == toEpochDay()
    override fun hashCode(): Int = toEpochDay().hashCode()
    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

    companion object {
        val MIN = LocalDate(-999_999_999, 1, 1)
        val MAX = LocalDate(999_999_999, 12, 31)
        val EPOCH = LocalDate(1970, 1, 1)
        fun of(year: Int, month: Int, day: Int): LocalDate {
            if (month !in 1..12 || day !in 1..monthLength(year, month)) {
                throw DateTimeException("Invalid date $year-$month-$day")
            }
            return LocalDate(year, month, day)
        }
        fun of(year: Int, month: Month, day: Int): LocalDate = of(year, month.value, day)
        fun ofEpochDay(epochDay: Long): LocalDate = civilFromDays(epochDay).let { (y, m, d) -> LocalDate(y, m, d) }
        fun ofYearDay(year: Int, dayOfYear: Int): LocalDate = ofEpochDay(daysFromCivil(year, 1, 1) + dayOfYear - 1)
        fun ofInstant(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
        fun now(): LocalDate = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDate = Instant.now().atZone(zone).toLocalDate()
        fun now(clock: Clock): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()
        fun parse(text: CharSequence): LocalDate {
            val s = text.toString().trim()
            val parts = s.split('-')
            if (parts.size != 3) throw java.time.format.DateTimeParseException("Text '$s' could not be parsed", s, 0)
            return runCatching { of(parts[0].toInt(), parts[1].toInt(), parts[2].take(2).toInt()) }
                .getOrElse { throw java.time.format.DateTimeParseException("Text '$s' could not be parsed", s, 0) }
        }
        fun parse(text: CharSequence, formatter: DateTimeFormatter): LocalDate = formatter.parseLocalDateTime(text.toString()).toLocalDate()
        fun from(temporal: Any): LocalDate = when (temporal) {
            is LocalDate -> temporal
            is LocalDateTime -> temporal.toLocalDate()
            is ZonedDateTime -> temporal.toLocalDate()
            is OffsetDateTime -> temporal.toLocalDate()
            else -> throw DateTimeException("Unable to obtain LocalDate from $temporal")
        }
    }
}

class LocalTime private constructor(val hour: Int, val minute: Int, val second: Int, val nano: Int) : Comparable<LocalTime> {
    fun getHour(): Int = hour
    fun getMinute(): Int = minute
    fun getSecond(): Int = second
    fun toSecondOfDay(): Int = hour * 3600 + minute * 60 + second
    fun toNanoOfDay(): Long = toSecondOfDay() * 1_000_000_000L + nano
    fun plusHours(h: Long): LocalTime = ofSecondOfDay(floorMod(toSecondOfDay() + h * 3600, 86_400))
    fun plusMinutes(m: Long): LocalTime = ofSecondOfDay(floorMod(toSecondOfDay() + m * 60, 86_400))
    fun plusSeconds(s: Long): LocalTime = ofSecondOfDay(floorMod(toSecondOfDay() + s, 86_400))
    fun minusHours(h: Long): LocalTime = plusHours(-h)
    fun minusMinutes(m: Long): LocalTime = plusMinutes(-m)
    fun withHour(h: Int): LocalTime = of(h, minute, second)
    fun withMinute(m: Int): LocalTime = of(hour, m, second)
    fun withSecond(s: Int): LocalTime = of(hour, minute, s)
    fun withNano(n: Int): LocalTime = LocalTime(hour, minute, second, n)
    fun truncatedTo(unit: ChronoUnit): LocalTime = when (unit) {
        ChronoUnit.HOURS -> of(hour, 0)
        ChronoUnit.MINUTES -> of(hour, minute)
        ChronoUnit.SECONDS -> of(hour, minute, second)
        ChronoUnit.DAYS -> MIDNIGHT
        else -> this
    }
    fun isBefore(other: LocalTime): Boolean = this < other
    fun isAfter(other: LocalTime): Boolean = this > other
    fun atDate(date: LocalDate): LocalDateTime = LocalDateTime.of(date, this)
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: LocalTime): Int = toNanoOfDay().compareTo(other.toNanoOfDay())
    override fun equals(other: Any?): Boolean = other is LocalTime && other.toNanoOfDay() == toNanoOfDay()
    override fun hashCode(): Int = toNanoOfDay().hashCode()
    override fun toString(): String = buildString {
        append(hour.toString().padStart(2, '0')); append(':'); append(minute.toString().padStart(2, '0'))
        if (second != 0 || nano != 0) { append(':'); append(second.toString().padStart(2, '0')) }
    }

    companion object {
        val MIDNIGHT = LocalTime(0, 0, 0, 0)
        val MIN = MIDNIGHT
        val NOON = LocalTime(12, 0, 0, 0)
        val MAX = LocalTime(23, 59, 59, 999_999_999)
        fun of(hour: Int, minute: Int): LocalTime = of(hour, minute, 0)
        fun of(hour: Int, minute: Int, second: Int): LocalTime = of(hour, minute, second, 0)
        fun of(hour: Int, minute: Int, second: Int, nano: Int): LocalTime {
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) throw DateTimeException("Invalid time $hour:$minute:$second")
            return LocalTime(hour, minute, second, nano)
        }
        fun ofSecondOfDay(s: Long): LocalTime = LocalTime((s / 3600).toInt(), ((s / 60) % 60).toInt(), (s % 60).toInt(), 0)
        fun now(): LocalTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalTime = Instant.now().atZone(zone).toLocalTime()
        fun now(clock: Clock): LocalTime = clock.instant().atZone(clock.zone).toLocalTime()
        fun parse(text: CharSequence): LocalTime {
            val p = text.toString().trim().split(':')
            return of(p[0].toInt(), p.getOrNull(1)?.toInt() ?: 0, p.getOrNull(2)?.take(2)?.toInt() ?: 0)
        }
    }
}

class LocalDateTime private constructor(private val date: LocalDate, private val time: LocalTime) : Comparable<LocalDateTime> {
    val year: Int get() = date.year
    val monthValue: Int get() = date.monthValue
    val month: Month get() = date.month
    val dayOfMonth: Int get() = date.dayOfMonth
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
    val dayOfYear: Int get() = date.dayOfYear
    val hour: Int get() = time.hour
    val minute: Int get() = time.minute
    val second: Int get() = time.second
    val nano: Int get() = time.nano
    fun toLocalDate(): LocalDate = date
    fun toLocalTime(): LocalTime = time
    fun toEpochSecond(offset: ZoneOffset): Long = date.toEpochDay() * 86_400 + time.toSecondOfDay() - offset.totalSeconds
    fun toInstant(offset: ZoneOffset): Instant = Instant.ofEpochSecond(toEpochSecond(offset), time.nano.toLong())
    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.of(this, offset)
    private fun plusSecondsInternal(s: Long): LocalDateTime = ofEpochSecond(toEpochSecond(ZoneOffset.UTC) + s, time.nano, ZoneOffset.UTC)
    fun plusSeconds(s: Long): LocalDateTime = plusSecondsInternal(s)
    fun plusMinutes(m: Long): LocalDateTime = plusSecondsInternal(m * 60)
    fun plusHours(h: Long): LocalDateTime = plusSecondsInternal(h * 3600)
    fun plusDays(d: Long): LocalDateTime = of(date.plusDays(d), time)
    fun plusWeeks(w: Long): LocalDateTime = of(date.plusWeeks(w), time)
    fun plusMonths(m: Long): LocalDateTime = of(date.plusMonths(m), time)
    fun plusYears(y: Long): LocalDateTime = of(date.plusYears(y), time)
    fun minusSeconds(s: Long): LocalDateTime = plusSeconds(-s)
    fun minusMinutes(m: Long): LocalDateTime = plusMinutes(-m)
    fun minusHours(h: Long): LocalDateTime = plusHours(-h)
    fun minusDays(d: Long): LocalDateTime = plusDays(-d)
    fun minusWeeks(w: Long): LocalDateTime = plusWeeks(-w)
    fun minusMonths(m: Long): LocalDateTime = plusMonths(-m)
    fun plus(amount: Long, unit: ChronoUnit): LocalDateTime = when (unit) {
        ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS -> of(date.plus(amount, unit), time)
        else -> plusSecondsInternal(amount * unit.millis / 1000)
    }
    fun plus(duration: Duration): LocalDateTime = plusSecondsInternal(duration.seconds)
    fun minus(duration: Duration): LocalDateTime = plusSecondsInternal(-duration.seconds)
    fun withHour(h: Int): LocalDateTime = of(date, time.withHour(h))
    fun withMinute(m: Int): LocalDateTime = of(date, time.withMinute(m))
    fun withSecond(s: Int): LocalDateTime = of(date, time.withSecond(s))
    fun withNano(n: Int): LocalDateTime = of(date, time.withNano(n))
    fun withDayOfMonth(d: Int): LocalDateTime = of(date.withDayOfMonth(d), time)
    fun with(adjuster: TemporalAdjuster): LocalDateTime = of(adjuster.adjustInto(date) as LocalDate, time)
    fun truncatedTo(unit: ChronoUnit): LocalDateTime = of(date, time.truncatedTo(unit))
    fun isBefore(other: LocalDateTime): Boolean = this < other
    fun isAfter(other: LocalDateTime): Boolean = this > other
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: LocalDateTime): Int = compareValuesBy(this, other, { it.date }, { it.time })
    override fun equals(other: Any?): Boolean = other is LocalDateTime && other.date == date && other.time == time
    override fun hashCode(): Int = date.hashCode() * 31 + time.hashCode()
    override fun toString(): String = "${date}T$time"

    companion object {
        fun of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute))
        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, day), LocalTime.of(hour, minute, second))
        fun ofEpochSecond(epochSecond: Long, nano: Int, offset: ZoneOffset): LocalDateTime {
            val local = epochSecond + offset.totalSeconds
            val day = floorDiv(local, 86_400)
            val secs = floorMod(local, 86_400)
            return LocalDateTime(LocalDate.ofEpochDay(day), LocalTime.ofSecondOfDay(secs).withNano(nano))
        }
        fun ofInstant(instant: Instant, zone: ZoneId): LocalDateTime = instant.atZone(zone).toLocalDateTime()
        fun now(): LocalDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDateTime = Instant.now().atZone(zone).toLocalDateTime()
        fun now(clock: Clock): LocalDateTime = clock.instant().atZone(clock.zone).toLocalDateTime()
        fun parse(text: CharSequence): LocalDateTime {
            val s = text.toString().trim()
            val t = s.indexOfFirst { it == 'T' || it == ' ' }
            if (t < 0) return LocalDate.parse(s).atStartOfDay()
            return of(LocalDate.parse(s.substring(0, t)), LocalTime.parse(s.substring(t + 1)))
        }
        fun parse(text: CharSequence, formatter: DateTimeFormatter): LocalDateTime = formatter.parseLocalDateTime(text.toString())
    }
}

// ── Zoned and offset date-times ────────────────────────────────────────────────────────────────

class ZonedDateTime private constructor(
    private val local: LocalDateTime,
    val offset: ZoneOffset,
    val zone: ZoneId,
) : Comparable<ZonedDateTime> {
    val year: Int get() = local.year
    val monthValue: Int get() = local.monthValue
    val month: Month get() = local.month
    val dayOfMonth: Int get() = local.dayOfMonth
    val dayOfWeek: DayOfWeek get() = local.dayOfWeek
    val dayOfYear: Int get() = local.dayOfYear
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
    val second: Int get() = local.second
    val nano: Int get() = local.nano
    fun getZone(): ZoneId = zone
    fun getOffset(): ZoneOffset = offset
    fun toLocalDate(): LocalDate = local.toLocalDate()
    fun toLocalTime(): LocalTime = local.toLocalTime()
    fun toLocalDateTime(): LocalDateTime = local
    fun toEpochSecond(): Long = local.toEpochSecond(offset)
    fun toInstant(): Instant = local.toInstant(offset)
    fun toOffsetDateTime(): OffsetDateTime = OffsetDateTime.of(local, offset)
    fun withZoneSameInstant(z: ZoneId): ZonedDateTime = ofInstant(toInstant(), z)
    fun withZoneSameLocal(z: ZoneId): ZonedDateTime = of(local, z)
    private fun relocal(l: LocalDateTime): ZonedDateTime = of(l, zone)
    fun plusDays(d: Long): ZonedDateTime = relocal(local.plusDays(d))
    fun plusWeeks(w: Long): ZonedDateTime = relocal(local.plusWeeks(w))
    fun plusMonths(m: Long): ZonedDateTime = relocal(local.plusMonths(m))
    fun plusYears(y: Long): ZonedDateTime = relocal(local.plusYears(y))
    fun minusDays(d: Long): ZonedDateTime = plusDays(-d)
    fun minusWeeks(w: Long): ZonedDateTime = plusWeeks(-w)
    fun minusMonths(m: Long): ZonedDateTime = plusMonths(-m)
    fun minusYears(y: Long): ZonedDateTime = plusYears(-y)
    fun plusHours(h: Long): ZonedDateTime = ofInstant(toInstant().plusSeconds(h * 3600), zone)
    fun plusMinutes(m: Long): ZonedDateTime = ofInstant(toInstant().plusSeconds(m * 60), zone)
    fun plusSeconds(s: Long): ZonedDateTime = ofInstant(toInstant().plusSeconds(s), zone)
    fun minusHours(h: Long): ZonedDateTime = plusHours(-h)
    fun minusMinutes(m: Long): ZonedDateTime = plusMinutes(-m)
    fun minusSeconds(s: Long): ZonedDateTime = plusSeconds(-s)
    fun plus(amount: Long, unit: ChronoUnit): ZonedDateTime = when (unit) {
        ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS -> relocal(local.plus(amount, unit))
        else -> ofInstant(toInstant().plusMillis(amount * unit.millis), zone)
    }
    fun minus(amount: Long, unit: ChronoUnit): ZonedDateTime = plus(-amount, unit)
    fun plus(duration: Duration): ZonedDateTime = ofInstant(toInstant().plus(duration), zone)
    fun minus(duration: Duration): ZonedDateTime = ofInstant(toInstant().minus(duration), zone)
    fun withHour(h: Int): ZonedDateTime = relocal(local.withHour(h))
    fun withMinute(m: Int): ZonedDateTime = relocal(local.withMinute(m))
    fun withSecond(s: Int): ZonedDateTime = relocal(local.withSecond(s))
    fun withNano(n: Int): ZonedDateTime = relocal(local.withNano(n))
    fun withDayOfMonth(d: Int): ZonedDateTime = relocal(local.withDayOfMonth(d))
    fun with(adjuster: TemporalAdjuster): ZonedDateTime = relocal(local.with(adjuster))
    fun truncatedTo(unit: ChronoUnit): ZonedDateTime = relocal(local.truncatedTo(unit))
    fun isBefore(other: ZonedDateTime): Boolean = toInstant().isBefore(other.toInstant())
    fun isAfter(other: ZonedDateTime): Boolean = toInstant().isAfter(other.toInstant())
    fun isEqual(other: ZonedDateTime): Boolean = toInstant() == other.toInstant()
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: ZonedDateTime): Int = toInstant().compareTo(other.toInstant())
    override fun equals(other: Any?): Boolean = other is ZonedDateTime && other.local == local && other.zone == zone
    override fun hashCode(): Int = local.hashCode() * 31 + zone.hashCode()
    override fun toString(): String = "$local$offset" + if (zone is ZoneOffset) "" else "[$zone]"

    companion object {
        fun ofInstant(instant: Instant, zone: ZoneId): ZonedDateTime {
            val offset = ZoneOffset.ofTotalSeconds(zone.offsetSecondsAt(instant.epochSecond))
            return ZonedDateTime(LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset), offset, zone)
        }
        fun of(local: LocalDateTime, zone: ZoneId): ZonedDateTime {
            val offset = ZoneOffset.ofTotalSeconds(zone.offsetForLocal(local))
            return ZonedDateTime(local, offset, zone)
        }
        fun of(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = of(LocalDateTime.of(date, time), zone)
        fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, nano: Int, zone: ZoneId): ZonedDateTime =
            of(LocalDateTime.of(year, month, day, hour, minute, second).withNano(nano), zone)
        fun now(): ZonedDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): ZonedDateTime = ofInstant(Instant.now(), zone)
        fun now(clock: Clock): ZonedDateTime = ofInstant(clock.instant(), clock.zone)
        fun parse(text: CharSequence): ZonedDateTime = DateTimeFormatter.parseIsoZoned(text.toString())
        fun parse(text: CharSequence, formatter: DateTimeFormatter): ZonedDateTime = formatter.parseZoned(text.toString())
    }
}

class OffsetDateTime private constructor(private val local: LocalDateTime, val offset: ZoneOffset) : Comparable<OffsetDateTime> {
    val year: Int get() = local.year
    val monthValue: Int get() = local.monthValue
    val dayOfMonth: Int get() = local.dayOfMonth
    val dayOfWeek: DayOfWeek get() = local.dayOfWeek
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
    val second: Int get() = local.second
    fun getOffset(): ZoneOffset = offset
    fun toInstant(): Instant = local.toInstant(offset)
    fun toEpochSecond(): Long = local.toEpochSecond(offset)
    fun toLocalDate(): LocalDate = local.toLocalDate()
    fun toLocalDateTime(): LocalDateTime = local
    fun toLocalTime(): LocalTime = local.toLocalTime()
    fun toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), offset)
    fun atZoneSameInstant(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), zone)
    fun isBefore(other: OffsetDateTime): Boolean = toInstant().isBefore(other.toInstant())
    fun isAfter(other: OffsetDateTime): Boolean = toInstant().isAfter(other.toInstant())
    fun format(formatter: DateTimeFormatter): String = formatter.format(this)
    override fun compareTo(other: OffsetDateTime): Int = toInstant().compareTo(other.toInstant())
    override fun equals(other: Any?): Boolean = other is OffsetDateTime && other.local == local && other.offset == offset
    override fun hashCode(): Int = local.hashCode() * 31 + offset.hashCode()
    override fun toString(): String = "$local$offset"

    companion object {
        fun of(local: LocalDateTime, offset: ZoneOffset): OffsetDateTime = OffsetDateTime(local, offset)
        fun ofInstant(instant: Instant, zone: ZoneId): OffsetDateTime {
            val offset = ZoneOffset.ofTotalSeconds(zone.offsetSecondsAt(instant.epochSecond))
            return OffsetDateTime(LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset), offset)
        }
        fun now(): OffsetDateTime = ofInstant(Instant.now(), ZoneId.systemDefault())
        fun parse(text: CharSequence): OffsetDateTime = DateTimeFormatter.parseIsoZoned(text.toString()).toOffsetDateTime()
        fun parse(text: CharSequence, formatter: DateTimeFormatter): OffsetDateTime = formatter.parseZoned(text.toString()).toOffsetDateTime()
    }
}

class YearMonth private constructor(val year: Int, val monthValue: Int) : Comparable<YearMonth> {
    val month: Month get() = Month.of(monthValue)
    fun atDay(day: Int): LocalDate = LocalDate.of(year, monthValue, day)
    fun atEndOfMonth(): LocalDate = LocalDate.of(year, monthValue, lengthOfMonth())
    fun lengthOfMonth(): Int = monthLength(year, monthValue)
    fun plusMonths(n: Long): YearMonth = atDay(1).plusMonths(n).let { of(it.year, it.monthValue) }
    fun minusMonths(n: Long): YearMonth = plusMonths(-n)
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.monthValue })
    override fun equals(other: Any?): Boolean = other is YearMonth && other.year == year && other.monthValue == monthValue
    override fun hashCode(): Int = year * 12 + monthValue
    override fun toString(): String = "${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}"

    companion object {
        fun of(year: Int, month: Int): YearMonth = YearMonth(year, month)
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.monthValue)
        fun now(): YearMonth = from(LocalDate.now())
    }
}

class Period private constructor(val years: Int, val months: Int, val days: Int) {
    companion object {
        fun ofDays(d: Int): Period = Period(0, 0, d)
        fun ofMonths(m: Int): Period = Period(0, m, 0)
        fun ofYears(y: Int): Period = Period(y, 0, 0)
        fun between(start: LocalDate, end: LocalDate): Period {
            val months = start.until(end, ChronoUnit.MONTHS)
            val days = end.toEpochDay() - start.plusMonths(months).toEpochDay()
            return Period((months / 12).toInt(), (months % 12).toInt(), days.toInt())
        }
    }
}

/** `Math.floorDiv` for the per-package `Math` stand-in. */
fun floorDivCompat(a: Long, b: Long): Long = floorDiv(a, b)
