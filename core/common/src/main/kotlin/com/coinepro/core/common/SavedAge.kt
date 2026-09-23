package com.coinepro.core.common

/**
 * How old a saved picture is, in the units a sentence can use — run Τ2, B6.
 *
 * ### Why this is not a formatted string
 *
 * «ذخیره‌شده · %s پیش» needs a *number* and a *unit*, and the two locales disagree about how the
 * unit is written. Formatting here would put Persian in a module that has no resources; returning
 * a unit and a count lets each surface pick its own plural and its own digits — Persian for the
 * count, because it is prose, which is the rule in the working agreement.
 *
 * ### Why a number at all
 *
 * The bar it feeds exists because «no network» and «this is from an hour ago» are different
 * sentences. The first is `CoineProOfflineBar`'s and clears when the reader walks to a window. The
 * second is about the numbers in front of them, and it is the one they need even when the first is
 * not true — a cached picture is just as old on a good connection that has not refreshed yet.
 *
 * A reader who is told only «saved» has to guess whether that means a minute or a week, and the
 * two lead to opposite decisions.
 */
enum class SavedAgeUnit {
    /** Under a minute. There is no number to print: the picture is effectively current. */
    MOMENTS,
    MINUTES,
    HOURS,
    DAYS,
}

/** A count and its unit. [count] is meaningless for [SavedAgeUnit.MOMENTS] and is zero there. */
data class SavedAge(val unit: SavedAgeUnit, val count: Int) {

    companion object {

        /** Under this, the picture is «just now» rather than a number of minutes. */
        const val MOMENTS_SECONDS = 60L

        /**
         * How old the picture is, or **null where there is nothing to say**.
         *
         * Null for a missing timestamp and for one in the future. Neither is an age:
         *
         * * A cache with no stored time is a cache whose age is **unknown**, and «ذخیره‌شده ·
         *   همین حالا» would be the app answering a question it cannot answer. The caller draws
         *   nothing, or says it does not know — see the surfaces.
         * * A timestamp ahead of the clock means the phone's clock moved, not that the data is from
         *   the future. Clamping it to zero would print «همین حالا» over a picture that might be
         *   days old, which is the more dangerous of the two wrong answers.
         *
         * Everything is floored rather than rounded, deliberately: fifty-nine minutes is «۵۹
         * دقیقه», not «یک ساعت». Rounding up makes a picture sound older than it is, rounding to
         * nearest makes it sound younger half the time, and a reader deciding whether to trust a
         * number is better served by the figure that cannot overstate freshness.
         */
        fun of(savedAtMillis: Long?, nowMillis: Long): SavedAge? {
            if (savedAtMillis == null || savedAtMillis <= 0L) return null
            val elapsedSeconds = (nowMillis - savedAtMillis) / 1_000L
            if (elapsedSeconds < 0L) return null
            return when {
                elapsedSeconds < MOMENTS_SECONDS -> SavedAge(SavedAgeUnit.MOMENTS, 0)
                elapsedSeconds < 3_600L -> SavedAge(SavedAgeUnit.MINUTES, (elapsedSeconds / 60L).toInt())
                elapsedSeconds < 86_400L -> SavedAge(SavedAgeUnit.HOURS, (elapsedSeconds / 3_600L).toInt())
                else -> SavedAge(SavedAgeUnit.DAYS, (elapsedSeconds / 86_400L).toInt())
            }
        }
    }
}
