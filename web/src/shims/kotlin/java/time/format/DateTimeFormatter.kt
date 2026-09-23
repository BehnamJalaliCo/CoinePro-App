@file:Suppress("unused")

package java.time.format

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

enum class TextStyle { FULL, FULL_STANDALONE, SHORT, SHORT_STANDALONE, NARROW, NARROW_STANDALONE }

enum class FormatStyle { FULL, LONG, MEDIUM, SHORT }

enum class ResolverStyle { STRICT, SMART, LENIENT }

open class DateTimeParseException(message: String, val parsedString: CharSequence = "", val errorIndex: Int = 0) :
    DateTimeException(message)

private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MONTHS_LONG = listOf("January", "February", "March", "April", "May", "June", "July", "August",
    "September", "October", "November", "December")
private val DAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAYS_LONG = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The fields a pattern can print, resolved once from whatever temporal was handed in. */
private class Fields(
    val date: LocalDate?,
    val time: LocalTime?,
    val offset: ZoneOffset?,
    val zone: ZoneId?,
    val nano: Int,
)

/**
 * `DateTimeFormatter`, for the patterns the shared code uses: the pattern letters `y u M L d D E e
 * a H h K k m s S n X x Z z V`, quoted literals, and the ISO/RFC constants. Names are English in
 * every locale here, which is what `Locale.US` — the only locale the phone formats markets in —
 * gives; Persian dates never went through this class on the phone either.
 */
class DateTimeFormatter private constructor(
    private val pattern: String?,
    private val kind: Iso?,
    private val zoneOverride: ZoneId? = null,
) {
    private enum class Iso { INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME, DATE_TIME, BASIC_DATE, RFC_1123 }

    val zone: ZoneId? get() = zoneOverride
    fun getZone(): ZoneId? = zoneOverride
    fun withZone(zone: ZoneId?): DateTimeFormatter = DateTimeFormatter(pattern, kind, zone)
    fun withLocale(locale: Locale?): DateTimeFormatter = this
    fun withResolverStyle(style: ResolverStyle): DateTimeFormatter = this
    val locale: Locale get() = Locale.US

    private fun fieldsOf(temporal: Any): Fields = when (temporal) {
        is ZonedDateTime -> Fields(temporal.toLocalDate(), temporal.toLocalTime(), temporal.offset, temporal.zone, temporal.nano)
        is OffsetDateTime -> Fields(temporal.toLocalDate(), temporal.toLocalTime(), temporal.offset, temporal.offset, temporal.toLocalTime().nano)
        is LocalDateTime -> zoneOverride?.let { fieldsOf(temporal.atZone(it)) }
            ?: Fields(temporal.toLocalDate(), temporal.toLocalTime(), null, null, temporal.nano)
        is LocalDate -> Fields(temporal, null, null, zoneOverride, 0)
        is LocalTime -> Fields(null, temporal, null, zoneOverride, temporal.nano)
        is Instant -> {
            val z = zoneOverride ?: if (kind == Iso.INSTANT) ZoneOffset.UTC else throw DateTimeException(
                "Unsupported field: formatting an Instant needs a zone",
            )
            fieldsOf(temporal.atZone(z))
        }
        else -> throw DateTimeException("Unsupported temporal $temporal")
    }

    fun format(temporal: Any): String {
        if (kind != null) return formatIso(temporal)
        val f = fieldsOf(temporal)
        val out = StringBuilder()
        val p = pattern!!
        var i = 0
        while (i < p.length) {
            val c = p[i]
            if (c == '\'') {
                val end = p.indexOf('\'', i + 1).let { if (it < 0) p.length else it }
                if (end == i + 1) out.append('\'') else out.append(p, i + 1, end)
                i = end + 1
                continue
            }
            if (!c.isLetter()) { out.append(c); i++; continue }
            var n = 1
            while (i + n < p.length && p[i + n] == c) n++
            out.append(field(c, n, f))
            i += n
        }
        return out.toString()
    }

    private fun need(date: LocalDate?): LocalDate = date ?: throw DateTimeException("Unsupported field: date")
    private fun needTime(time: LocalTime?): LocalTime = time ?: throw DateTimeException("Unsupported field: time")

    private fun field(c: Char, n: Int, f: Fields): String = when (c) {
        'y', 'u' -> need(f.date).year.let { if (n == 2) (it % 100).toString().padStart(2, '0') else it.toString().padStart(n, '0') }
        'M', 'L' -> need(f.date).monthValue.let {
            when {
                n >= 4 -> MONTHS_LONG[it - 1]
                n == 3 -> MONTHS_SHORT[it - 1]
                else -> it.toString().padStart(n, '0')
            }
        }
        'd' -> need(f.date).dayOfMonth.toString().padStart(n, '0')
        'D' -> need(f.date).dayOfYear.toString().padStart(n, '0')
        'E', 'e', 'c' -> need(f.date).dayOfWeek.ordinal.let { if (n >= 4) DAYS_LONG[it] else if (c != 'E' && n <= 2) (it + 1).toString() else DAYS_SHORT[it] }
        'a' -> if (needTime(f.time).hour < 12) "AM" else "PM"
        'H' -> needTime(f.time).hour.toString().padStart(n, '0')
        'k' -> needTime(f.time).hour.let { if (it == 0) 24 else it }.toString().padStart(n, '0')
        'h' -> needTime(f.time).hour.let { (it + 11) % 12 + 1 }.toString().padStart(n, '0')
        'K' -> (needTime(f.time).hour % 12).toString().padStart(n, '0')
        'm' -> needTime(f.time).minute.toString().padStart(n, '0')
        's' -> needTime(f.time).second.toString().padStart(n, '0')
        'S' -> f.nano.toString().padStart(9, '0').take(n)
        'n' -> f.nano.toString()
        'X' -> f.offset?.let { if (it.totalSeconds == 0) "Z" else offsetText(it.totalSeconds, n) } ?: throw DateTimeException("Unsupported field: offset")
        'x' -> f.offset?.let { offsetText(it.totalSeconds, n) } ?: throw DateTimeException("Unsupported field: offset")
        'Z' -> f.offset?.let { if (n >= 4) "GMT" + (if (it.totalSeconds == 0) "" else offsetText(it.totalSeconds, 3)) else offsetText(it.totalSeconds, 2) }
            ?: throw DateTimeException("Unsupported field: offset")
        'z', 'V', 'O' -> (f.zone ?: f.offset)?.id?.let { if (it == "Z") "UTC" else it } ?: throw DateTimeException("Unsupported field: zone")
        'G' -> "AD"
        else -> c.toString().repeat(n)
    }

    private fun offsetText(total: Int, n: Int): String {
        val sign = if (total < 0) '-' else '+'
        val a = abs(total)
        val hh = (a / 3600).toString().padStart(2, '0')
        val mm = ((a / 60) % 60).toString().padStart(2, '0')
        return when (n) {
            1 -> "$sign$hh" + if (mm != "00") mm else ""
            2 -> "$sign$hh$mm"
            else -> "$sign$hh:$mm"
        }
    }

    private fun two(v: Int) = v.toString().padStart(2, '0')

    private fun isoTime(t: LocalTime, nano: Int): String = buildString {
        append(two(t.hour)); append(':'); append(two(t.minute)); append(':'); append(two(t.second))
        if (nano != 0) {
            append('.')
            val digits = nano.toString().padStart(9, '0')
            append(if (nano % 1_000_000 == 0) digits.take(3) else if (nano % 1000 == 0) digits.take(6) else digits)
        }
    }

    private fun formatIso(temporal: Any): String = when (kind!!) {
        Iso.INSTANT -> {
            val z = (temporal as? Instant ?: Instant.from(temporal)).atZone(ZoneOffset.UTC)
            "${z.toLocalDate()}T${isoTime(z.toLocalTime(), z.nano)}Z"
        }
        Iso.LOCAL_DATE, Iso.BASIC_DATE -> fieldsOf(temporal).let { f ->
            need(f.date).toString().let { if (kind == Iso.BASIC_DATE) it.replace("-", "") else it }
        }
        Iso.LOCAL_TIME -> fieldsOf(temporal).let { f -> isoTime(needTime(f.time), f.nano) }
        Iso.LOCAL_DATE_TIME -> fieldsOf(temporal).let { f -> "${need(f.date)}T${isoTime(needTime(f.time), f.nano)}" }
        Iso.OFFSET_DATE_TIME, Iso.ZONED_DATE_TIME, Iso.DATE_TIME -> fieldsOf(temporal).let { f ->
            val offset = f.offset ?: throw DateTimeException("Unsupported field: offset")
            val base = "${need(f.date)}T${isoTime(needTime(f.time), f.nano)}${offset.id}"
            if (kind != Iso.OFFSET_DATE_TIME && f.zone != null && f.zone !is ZoneOffset) "$base[${f.zone.id}]" else base
        }
        Iso.RFC_1123 -> fieldsOf(temporal).let { f ->
            val d = need(f.date)
            val t = needTime(f.time)
            val o = f.offset ?: ZoneOffset.UTC
            "${DAYS_SHORT[d.dayOfWeek.ordinal]}, ${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${d.year} " +
                "${two(t.hour)}:${two(t.minute)}:${two(t.second)} " + (if (o.totalSeconds == 0) "GMT" else offsetText(o.totalSeconds, 2))
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────────────────────

    private class Parsed {
        var year: Int? = null; var month: Int? = null; var day: Int? = null
        var hour: Int = 0; var minute: Int = 0; var second: Int = 0; var nano: Int = 0
        var pm: Boolean? = null; var offset: ZoneOffset? = null; var zone: ZoneId? = null
    }

    private fun fail(text: String, at: Int): Nothing =
        throw DateTimeParseException("Text '$text' could not be parsed at index $at", text, at)

    private fun parseFields(text: String): Parsed {
        if (kind != null) {
            val z = parseIsoZoned(text, allowLocal = true)
            return Parsed().also {
                it.year = z.year; it.month = z.monthValue; it.day = z.dayOfMonth
                it.hour = z.hour; it.minute = z.minute; it.second = z.second; it.nano = z.nano
                it.offset = z.offset; it.zone = z.zone
            }
        }
        val p = pattern!!
        val r = Parsed()
        var i = 0
        var at = 0
        fun digits(min: Int, max: Int): Int {
            val start = at
            if (at < text.length && (text[at] == '-' || text[at] == '+') && max > 4) at++
            while (at < text.length && text[at].isDigit() && at - start < max) at++
            if (at - start < min) fail(text, start)
            return text.substring(start, at).toInt()
        }
        fun word(options: List<String>): Int {
            val idx = options.indexOfFirst { text.regionMatches(at, it, 0, it.length, ignoreCase = true) }
            if (idx < 0) fail(text, at)
            at += options[idx].length
            return idx
        }
        while (i < p.length) {
            val c = p[i]
            if (c == '\'') {
                val end = p.indexOf('\'', i + 1).let { if (it < 0) p.length else it }
                val lit = if (end == i + 1) "'" else p.substring(i + 1, end)
                if (!text.regionMatches(at, lit, 0, lit.length)) fail(text, at)
                at += lit.length; i = end + 1; continue
            }
            if (!c.isLetter()) {
                if (at < text.length && text[at] == c) at++ else if (c != ' ') fail(text, at)
                i++; continue
            }
            var n = 1
            while (i + n < p.length && p[i + n] == c) n++
            when (c) {
                'y', 'u' -> r.year = if (n == 2) 2000 + digits(2, 2) else digits(1, 9)
                'M', 'L' -> r.month = when {
                    n >= 4 -> word(MONTHS_LONG) + 1
                    n == 3 -> word(MONTHS_SHORT) + 1
                    else -> digits(1, 2)
                }
                'd' -> r.day = digits(1, 2)
                'E', 'e' -> if (n >= 4) word(DAYS_LONG) else word(DAYS_SHORT)
                'H', 'k' -> r.hour = digits(1, 2) % 24
                'h', 'K' -> r.hour = digits(1, 2) % 12
                'm' -> r.minute = digits(1, 2)
                's' -> r.second = digits(1, 2)
                'S' -> r.nano = digits(1, n).toString().padEnd(9, '0').take(9).toInt()
                'a' -> r.pm = word(listOf("AM", "PM")) == 1
                'X', 'x', 'Z' -> {
                    if (at < text.length && (text[at] == 'Z' || text[at] == 'z')) { at++; r.offset = ZoneOffset.UTC } else {
                        val start = at
                        at++
                        while (at < text.length && (text[at].isDigit() || text[at] == ':')) at++
                        r.offset = runCatching { ZoneOffset.of(text.substring(start, at)) }.getOrElse { fail(text, start) }
                    }
                }
                'z', 'V' -> {
                    val start = at
                    while (at < text.length && !text[at].isWhitespace()) at++
                    val name = text.substring(start, at)
                    r.zone = when (name) {
                        "GMT", "UTC", "UT", "Z" -> ZoneOffset.UTC
                        else -> runCatching { ZoneId.of(name) }.getOrElse { fail(text, start) }
                    }
                }
                else -> at += n
            }
            i += n
        }
        if (r.pm == true && r.hour < 12) r.hour += 12
        return r
    }

    internal fun parseLocalDateTime(text: String): LocalDateTime {
        val r = parseFields(text)
        val date = LocalDate.of(r.year ?: 1970, r.month ?: 1, r.day ?: 1)
        return LocalDateTime.of(date, LocalTime.of(r.hour, r.minute, r.second, r.nano))
    }

    internal fun parseZoned(text: String): ZonedDateTime {
        val r = parseFields(text)
        val local = LocalDateTime.of(LocalDate.of(r.year ?: 1970, r.month ?: 1, r.day ?: 1), LocalTime.of(r.hour, r.minute, r.second, r.nano))
        val zone = r.zone ?: r.offset ?: zoneOverride ?: throw DateTimeParseException("Text '$text' has no zone", text, 0)
        return if (r.offset != null && r.zone == null) ZonedDateTime.ofInstant(local.toInstant(r.offset!!), zone) else ZonedDateTime.of(local, zone)
    }

    fun parse(text: CharSequence): Any = parseZonedOrLocal(text.toString())

    private fun parseZonedOrLocal(text: String): Any =
        runCatching { parseZoned(text) }.getOrElse { parseLocalDateTime(text) }

    fun <T> parse(text: CharSequence, query: (Any) -> T): T = query(parseZonedOrLocal(text.toString()))

    override fun toString(): String = pattern ?: kind.toString()

    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern, null)
        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, null)
        fun ofLocalizedDate(style: FormatStyle): DateTimeFormatter = ofPattern("d MMM yyyy")
        fun ofLocalizedDateTime(style: FormatStyle): DateTimeFormatter = ofPattern("d MMM yyyy HH:mm")
        fun ofLocalizedTime(style: FormatStyle): DateTimeFormatter = ofPattern("HH:mm")

        val ISO_INSTANT = DateTimeFormatter(null, Iso.INSTANT)
        val ISO_LOCAL_DATE = DateTimeFormatter(null, Iso.LOCAL_DATE)
        val ISO_DATE = ISO_LOCAL_DATE
        val ISO_LOCAL_TIME = DateTimeFormatter(null, Iso.LOCAL_TIME)
        val ISO_TIME = ISO_LOCAL_TIME
        val ISO_LOCAL_DATE_TIME = DateTimeFormatter(null, Iso.LOCAL_DATE_TIME)
        val ISO_OFFSET_DATE_TIME = DateTimeFormatter(null, Iso.OFFSET_DATE_TIME)
        val ISO_ZONED_DATE_TIME = DateTimeFormatter(null, Iso.ZONED_DATE_TIME)
        val ISO_DATE_TIME = DateTimeFormatter(null, Iso.DATE_TIME)
        val BASIC_ISO_DATE = DateTimeFormatter(null, Iso.BASIC_DATE)
        val RFC_1123_DATE_TIME = DateTimeFormatter(null, Iso.RFC_1123)

        /** `2026-09-22T13:00:00Z`, `…+03:30`, `…T13:00:00.123456+00:00[Asia/Tehran]`, or with a space. */
        internal fun parseIsoZoned(text: String, allowLocal: Boolean = false): ZonedDateTime {
            val s = text.trim()
            fun bad(): Nothing = throw DateTimeParseException("Text '$s' could not be parsed", s, 0)
            val bracket = s.indexOf('[')
            val core = if (bracket >= 0) s.substring(0, bracket) else s
            val region = if (bracket >= 0) s.substring(bracket + 1, s.indexOf(']', bracket).let { if (it < 0) s.length else it }) else null
            val tIndex = core.indexOfFirst { it == 'T' || it == 't' || it == ' ' }
            val datePart = if (tIndex >= 0) core.substring(0, tIndex) else core
            val rest = if (tIndex >= 0) core.substring(tIndex + 1) else ""
            val date = runCatching { LocalDate.parse(datePart) }.getOrElse { bad() }
            var offset: ZoneOffset? = null
            var timePart = rest
            val z = rest.indexOfFirst { it == 'Z' || it == 'z' }
            if (z >= 0) { offset = ZoneOffset.UTC; timePart = rest.substring(0, z) } else {
                val sign = rest.indexOfLast { it == '+' || it == '-' }
                if (sign > 0) { offset = runCatching { ZoneOffset.of(rest.substring(sign)) }.getOrElse { bad() }; timePart = rest.substring(0, sign) }
            }
            val time = if (timePart.isEmpty()) LocalTime.MIDNIGHT else runCatching {
                val main = timePart.substringBefore('.')
                val frac = timePart.substringAfter('.', "")
                val p = main.split(':')
                LocalTime.of(p[0].toInt(), p.getOrNull(1)?.toInt() ?: 0, p.getOrNull(2)?.toInt() ?: 0,
                    if (frac.isEmpty()) 0 else frac.padEnd(9, '0').take(9).toInt())
            }.getOrElse { bad() }
            val local = LocalDateTime.of(date, time)
            val zone: ZoneId = region?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: offset
                ?: if (allowLocal) ZoneOffset.UTC else bad()
            return if (offset != null) ZonedDateTime.ofInstant(local.toInstant(offset), zone) else ZonedDateTime.of(local, zone)
        }

        internal fun parseIsoInstant(text: String): Instant = parseIsoZoned(text).toInstant()
    }
}

/** `DateTimeFormatterBuilder` is not used by the shared code; this exists so an import resolves. */
class DateTimeFormatterBuilder {
    private var pattern = StringBuilder()
    fun appendPattern(p: String): DateTimeFormatterBuilder = apply { pattern.append(p) }
    fun appendLiteral(s: String): DateTimeFormatterBuilder = apply { pattern.append('\'').append(s).append('\'') }
    fun toFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern.toString())
    fun toFormatter(locale: Locale): DateTimeFormatter = toFormatter()
}

private fun DayOfWeek.shortName(): String = DAYS_SHORT[ordinal]
