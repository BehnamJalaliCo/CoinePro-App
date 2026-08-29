package com.coinepro.feature.news

import com.coinepro.core.announcements.Announcement
import com.coinepro.core.announcements.AnnouncementImportance
import com.coinepro.core.announcements.AnnouncementsState
import com.coinepro.core.common.ErrorKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The four states of a screen three of whose states are the same empty list.
 *
 * This is the decision the whole feature turns on. An announcements channel is empty on the day it
 * ships and that is correct, so the screen has to tell "nothing has been announced" apart from "we
 * have not asked yet" and from "we asked and could not reach the server" — and getting it wrong in
 * either direction produces the two failures this feature was built to avoid: an empty state that
 * reads like a fault, or a fault that reads like an empty state.
 */
class AnnouncementsModeTest {

    @Test
    fun `before the first read finishes the screen says nothing at all`() {
        assertEquals(
            AnnouncementsMode.WAITING,
            announcementsMode(AnnouncementsState(loading = true)),
        )
    }

    @Test
    fun `a channel that answered with nothing is empty, not waiting and not failed`() {
        assertEquals(
            AnnouncementsMode.EMPTY,
            announcementsMode(AnnouncementsState(loaded = true)),
        )
    }

    /**
     * The mistake this ordering exists to prevent: a channel known to be empty whose reread failed
     * must not repeat «چیزی اعلام نشده». That sentence is a claim about the server, and a request
     * that never reached the server is in no position to make it.
     */
    @Test
    fun `an empty channel whose reread failed reports the failure and not the emptiness`() {
        assertEquals(
            AnnouncementsMode.FAILED,
            announcementsMode(AnnouncementsState(loaded = true, failure = ErrorKind.NETWORK)),
        )
    }

    @Test
    fun `a failed first read is a failure rather than an empty channel`() {
        assertEquals(
            AnnouncementsMode.FAILED,
            announcementsMode(AnnouncementsState(failure = ErrorKind.SERVER)),
        )
    }

    /**
     * A failed refresh over announcements that did load keeps showing them. They are durable
     * statements — an outage notice is still the last thing the service said whether or not this
     * request got through — and the failure is reported by a strip above the list instead.
     */
    @Test
    fun `announcements already on screen survive a failed refresh`() {
        assertEquals(
            AnnouncementsMode.CONTENT,
            announcementsMode(
                AnnouncementsState(
                    loaded = true,
                    announcements = listOf(announcement()),
                    failure = ErrorKind.NETWORK,
                ),
            ),
        )
    }

    @Test
    fun `a channel with announcements in it is content`() {
        assertEquals(
            AnnouncementsMode.CONTENT,
            announcementsMode(
                AnnouncementsState(loaded = true, announcements = listOf(announcement())),
            ),
        )
    }

    private fun announcement() = Announcement(
        id = "outage",
        title = "اتصال به صرافی موقتاً قطع است",
        body = null,
        source = null,
        url = null,
        publishedAt = Instant.parse("2026-08-29T08:30:00Z"),
        importance = AnnouncementImportance.HIGH,
    )
}
