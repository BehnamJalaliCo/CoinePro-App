@file:Suppress("unused")

package java.time.temporal

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** The units the shared code counts in. `millis` is the fixed length; months and years are calendar units. */
enum class ChronoUnit(internal val millis: Long) {
    NANOS(0), MICROS(0), MILLIS(1), SECONDS(1_000), MINUTES(60_000), HOURS(3_600_000), HALF_DAYS(43_200_000),
    DAYS(86_400_000), WEEKS(604_800_000), MONTHS(2_629_746_000), YEARS(31_556_952_000), DECADES(315_569_520_000),
    CENTURIES(3_155_695_200_000), MILLENNIA(31_556_952_000_000), ERAS(0), FOREVER(Long.MAX_VALUE);

    val duration: Duration get() = Duration.ofMillis(millis)
    fun getDuration(): Duration = duration

    fun between(start: Any, end: Any): Long = when {
        start is Instant && end is Instant -> start.until(end, this)
        start is LocalDate && end is LocalDate -> start.until(end, this)
        start is ZonedDateTime && end is ZonedDateTime -> when (this) {
            DAYS, WEEKS, MONTHS, YEARS -> start.toLocalDate().until(end.toLocalDate(), this)
            else -> start.toInstant().until(end.toInstant(), this)
        }
        start is LocalDateTime && end is LocalDateTime ->
            (end.toEpochSecond(java.time.ZoneOffset.UTC) - start.toEpochSecond(java.time.ZoneOffset.UTC)) * 1000 / millis
        else -> throw java.time.DateTimeException("Unsupported temporal pair")
    }
}

enum class ChronoField { NANO_OF_SECOND, SECOND_OF_MINUTE, MINUTE_OF_HOUR, HOUR_OF_DAY, DAY_OF_WEEK, DAY_OF_MONTH, DAY_OF_YEAR, MONTH_OF_YEAR, YEAR, EPOCH_DAY, INSTANT_SECONDS }

/** Adjusts a date; the shared code applies them to `LocalDate`s. */
fun interface TemporalAdjuster {
    fun adjustInto(temporal: Any): Any
}

object TemporalAdjusters {
    private fun onDate(block: (LocalDate) -> LocalDate) = TemporalAdjuster { t -> block(t as LocalDate) }

    fun previousOrSame(day: DayOfWeek): TemporalAdjuster = onDate { d ->
        d.minusDays(((d.dayOfWeek.value - day.value + 7) % 7).toLong())
    }
    fun previous(day: DayOfWeek): TemporalAdjuster = onDate { d ->
        val back = (d.dayOfWeek.value - day.value + 7) % 7
        d.minusDays((if (back == 0) 7 else back).toLong())
    }
    fun nextOrSame(day: DayOfWeek): TemporalAdjuster = onDate { d ->
        d.plusDays(((day.value - d.dayOfWeek.value + 7) % 7).toLong())
    }
    fun next(day: DayOfWeek): TemporalAdjuster = onDate { d ->
        val ahead = (day.value - d.dayOfWeek.value + 7) % 7
        d.plusDays((if (ahead == 0) 7 else ahead).toLong())
    }
    fun firstDayOfMonth(): TemporalAdjuster = onDate { d -> d.withDayOfMonth(1) }
    fun lastDayOfMonth(): TemporalAdjuster = onDate { d -> d.withDayOfMonth(d.lengthOfMonth()) }
    fun firstDayOfNextMonth(): TemporalAdjuster = onDate { d -> d.withDayOfMonth(1).plusMonths(1) }
    fun firstDayOfYear(): TemporalAdjuster = onDate { d -> LocalDate.of(d.year, 1, 1) }
    fun lastDayOfYear(): TemporalAdjuster = onDate { d -> LocalDate.of(d.year, 12, 31) }
}

class UnsupportedTemporalTypeException(message: String) : java.time.DateTimeException(message)
