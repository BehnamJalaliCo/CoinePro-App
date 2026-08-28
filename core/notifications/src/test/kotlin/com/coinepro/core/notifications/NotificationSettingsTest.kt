package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions that stand between a server and somebody's evening.
 *
 * Both are pure functions on purpose, and this is why: the failure worth catching is not "a
 * notification was shown" but "a notification was **not** shown" — and that failure is silent on
 * every device it happens on. Nobody reports the alert they never got.
 */
class NotificationSettingsTest {

    private val settings = NotificationSettings()
    private val noon = 12 * 60

    @Test
    fun `an unknown category is shown rather than swallowed`() {
        // A server that adds a kind this build has not heard of must not have its message dropped:
        // the failure would be invisible at both ends, and the first thing lost would be whatever
        // was new enough to be worth announcing.
        assertTrue(settings.shouldShow(null, nowEpochMillis = 0L, minuteOfDay = noon))
        assertEquals(null, NotificationCategory.forKind("something_from_2027"))
    }

    @Test
    fun `security cannot be silenced by anything`() {
        val silenced = settings.copy(
            enabled = false,
            mutedUntilEpochMillis = Long.MAX_VALUE,
            categories = NotificationCategory.entries.associateWith { false },
            quietHours = QuietHours(enabled = true, fromMinuteOfDay = 0, toMinuteOfDay = 23 * 60 + 59),
        )
        assertTrue(silenced.shouldShow(NotificationCategory.SECURITY, 0L, noon))
        assertTrue("The switch itself must refuse", silenced.isOn(NotificationCategory.SECURITY))
    }

    @Test
    fun `the master switch stops everything else`() {
        val off = settings.copy(enabled = false)
        assertFalse(off.shouldShow(NotificationCategory.NEW_SIGNAL, 0L, noon))
        assertFalse(off.shouldShow(NotificationCategory.PRICE_ALERT, 0L, noon))
    }

    @Test
    fun `a mute expires by itself`() {
        val muted = settings.copy(mutedUntilEpochMillis = 1_000L)
        assertFalse(muted.shouldShow(NotificationCategory.NEW_SIGNAL, 999L, noon))
        assertTrue(muted.shouldShow(NotificationCategory.NEW_SIGNAL, 1_000L, noon))
    }

    /**
     * The window people actually pick wraps midnight.
     *
     * Eleven at night until seven in the morning is two ranges in clock arithmetic and one idea to
     * a reader. Getting this backwards silences the whole day and lets the night through, which is
     * exactly wrong and would look like the feature working.
     */
    @Test
    fun `quiet hours across midnight cover the night and not the day`() {
        val night = settings.copy(
            quietHours = QuietHours(enabled = true, fromMinuteOfDay = 23 * 60, toMinuteOfDay = 7 * 60),
        )
        val inside = listOf(23 * 60, 23 * 60 + 30, 0, 3 * 60, 6 * 60 + 59)
        val outside = listOf(7 * 60, 12 * 60, 22 * 60 + 59)
        // A category that is on by default and is not one of the three exceptions, so the only
        // thing under test is the window arithmetic.
        val category = NotificationCategory.PRICE_ALERT
        inside.forEach { minute ->
            assertFalse("$minute should be quiet", night.shouldShow(category, 0L, minute))
        }
        outside.forEach { minute ->
            assertTrue("$minute should not be quiet", night.shouldShow(category, 0L, minute))
        }
    }

    @Test
    fun `a daytime window does not wrap`() {
        val nap = settings.copy(
            quietHours = QuietHours(enabled = true, fromMinuteOfDay = 13 * 60, toMinuteOfDay = 15 * 60),
        )
        assertFalse(nap.shouldShow(NotificationCategory.PRICE_ALERT, 0L, 14 * 60))
        assertTrue(nap.shouldShow(NotificationCategory.PRICE_ALERT, 0L, 12 * 60))
        assertTrue(nap.shouldShow(NotificationCategory.PRICE_ALERT, 0L, 16 * 60))
    }

    /** Money that moved while somebody slept is worth waking them for. Nothing else is. */
    @Test
    fun `the three exceptions come through quiet hours`() {
        val night = settings.copy(
            quietHours = QuietHours(enabled = true, fromMinuteOfDay = 23 * 60, toMinuteOfDay = 7 * 60),
        )
        listOf(
            NotificationCategory.COPY_OPENED,
            NotificationCategory.COPY_FAILED,
            NotificationCategory.SECURITY,
        ).forEach { category ->
            assertTrue(category.name, night.shouldShow(category, 0L, 3 * 60))
        }
        assertFalse(night.shouldShow(NotificationCategory.NEW_SIGNAL, 0L, 3 * 60))
    }

    /**
     * The server has one flag for three of our categories, and turning it off to satisfy one of
     * them would silence the other two at the source — where the app cannot get them back.
     */
    @Test
    fun `one wanted update keeps the server flag on`() {
        val onlyTargets = settings.copy(
            categories = settings.categories +
                mapOf(
                    NotificationCategory.STOP_HIT to false,
                    NotificationCategory.SIGNAL_CLOSED to false,
                ),
        )
        assertTrue(onlyTargets.serverPreferences().signalUpdates)

        val noUpdates = settings.copy(
            categories = settings.categories +
                mapOf(
                    NotificationCategory.TARGET_HIT to false,
                    NotificationCategory.STOP_HIT to false,
                    NotificationCategory.SIGNAL_CLOSED to false,
                ),
        )
        assertFalse(noUpdates.serverPreferences().signalUpdates)
    }

    @Test
    fun `marketing starts off and security starts on`() {
        assertFalse(settings.isOn(NotificationCategory.MARKETING))
        assertTrue(settings.isOn(NotificationCategory.SECURITY))
    }

    @Test
    fun `every silenceable category, turned off, is off — one by one`() {
        // The exhaustive version, and the reason it is exhaustive rather than a sample: this is
        // the promise the settings screen makes fifteen times, and a category added later that
        // forgets to consult `isOn` would pass every other test in this file.
        //
        // "Not receiving what I asked for" and "receiving what I turned off" are the two loudest
        // complaints readers of this category of app make. The first is a permission problem the
        // screen surfaces; the second is this function, and it has to be true for all of them.
        val silenceable = NotificationCategory.entries.filter { it.silenceable }
        assertTrue("Nothing to prove means the enum lost its categories", silenceable.size >= 10)
        silenceable.forEach { category ->
            val off = settings.copy(categories = settings.categories + mapOf(category to false))
            assertFalse(
                "${category.id} was turned off and would still have been shown",
                off.shouldShow(category, nowEpochMillis = 0L, minuteOfDay = noon),
            )
            // And turning one off silences *only* that one. A group switch that silenced its
            // neighbours would be the "too many"/"none at all" failure in the other direction.
            //
            // Compared against the categories that were on *before* the change rather than against
            // every category, because three of them — news, the calendar, marketing — start off by
            // their own choice and staying off is them working.
            silenceable.filter { it != category && settings.isOn(it) }.forEach { other ->
                assertTrue(
                    "Turning off ${category.id} also silenced ${other.id}",
                    off.shouldShow(other, nowEpochMillis = 0L, minuteOfDay = noon),
                )
            }
        }
    }

    @Test
    fun `a category the reader never touched keeps its own default`() {
        // The stored map holds only what was changed. A category absent from it must fall back to
        // its own `defaultOn`, not to a blanket true — which is what would silently switch
        // marketing on for every reader who ever opened this screen.
        NotificationCategory.entries.forEach { category ->
            assertEquals(
                "${category.id} did not fall back to its own default",
                category.defaultOn || !category.silenceable,
                settings.isOn(category),
            )
        }
    }
}
