package com.coinepro.core.announcements

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsControllerTest {

    /**
     * The whole point of `loaded`, stated as a test.
     *
     * Before the first read the list is empty because nothing has been asked; after it the same
     * empty list means the channel is genuinely empty. The screen writes a different sentence for
     * each, so the two states have to be distinguishable in the controller or the screen has to
     * guess — and the guess it would make is to call the first state "nothing has been announced",
     * which is a claim the app has no grounds for until the server has answered.
     */
    @Test
    fun `an empty channel is loaded and empty, not merely empty`() = runTest {
        val controller = AnnouncementsController(FakeGateway(AppResult.Success(emptyList())), this)

        assertFalse("nothing has been asked yet", controller.state.value.loaded)

        controller.refresh()
        runCurrent()

        val state = controller.state.value
        assertTrue(state.loaded)
        assertEquals(emptyList<Announcement>(), state.announcements)
        assertNull("an empty channel is not a failure", state.failure)
    }

    @Test
    fun `the first read shows loading and later reads show refreshing`() = runTest {
        val gateway = FakeGateway(AppResult.Success(listOf(announcement("a"))))
        val controller = AnnouncementsController(gateway, this)

        controller.refresh()
        assertTrue("the first read has nothing to keep on screen", controller.state.value.loading)
        assertFalse(controller.state.value.refreshing)
        runCurrent()

        controller.refresh()
        assertFalse(controller.state.value.loading)
        assertTrue("a reread keeps the list and reports itself quietly", controller.state.value.refreshing)
    }

    /**
     * A refresh of an empty-but-loaded channel is still a refresh.
     *
     * Keying the spinner off `announcements.isEmpty()` rather than off `loaded` would make every
     * pull on this screen look like a cold start for as long as nothing has been announced — which
     * is to say, for the whole of the feature's first months.
     */
    @Test
    fun `rereading a correctly empty channel does not fall back to the cold-start spinner`() = runTest {
        val controller = AnnouncementsController(FakeGateway(AppResult.Success(emptyList())), this)
        controller.refresh()
        runCurrent()

        controller.refresh()

        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.refreshing)
    }

    @Test
    fun `a failure keeps the announcements that were already on screen`() = runTest {
        val gateway = FakeGateway(AppResult.Success(listOf(announcement("outage"))))
        val controller = AnnouncementsController(gateway, this)
        controller.refresh()
        runCurrent()

        gateway.answer = AppResult.Failure(ErrorKind.NETWORK)
        controller.refresh()
        runCurrent()

        val state = controller.state.value
        assertEquals(listOf("outage"), state.announcements.map(Announcement::id))
        assertEquals(ErrorKind.NETWORK, state.failure)
        assertNull("a connection that never answered has no wording of its own", state.failureText)
    }

    @Test
    fun `a refusal carries the server's own wording`() = runTest {
        val gateway = FakeGateway(
            AppResult.Failure(ErrorKind.SERVER, message = "سرویس اطلاعیه موقتاً در دسترس نیست."),
        )
        val controller = AnnouncementsController(gateway, this)

        controller.refresh()
        runCurrent()

        assertEquals("سرویس اطلاعیه موقتاً در دسترس نیست.", controller.state.value.failureText)
        assertFalse("a failed first read has still not loaded", controller.state.value.loaded)
    }

    @Test
    fun `a second refresh while one is in flight is ignored`() = runTest {
        val gateway = FakeGateway(AppResult.Success(listOf(announcement("a"))))
        val controller = AnnouncementsController(gateway, this)

        controller.refresh()
        controller.refresh()
        runCurrent()

        assertEquals(1, gateway.calls)
    }

    @Test
    fun `a successful read clears the failure it recovered from`() = runTest {
        val gateway = FakeGateway(AppResult.Failure(ErrorKind.NETWORK))
        val controller = AnnouncementsController(gateway, this)
        controller.refresh()
        runCurrent()

        gateway.answer = AppResult.Success(listOf(announcement("a")))
        controller.refresh()
        runCurrent()

        assertNull(controller.state.value.failure)
        assertEquals(listOf("a"), controller.state.value.announcements.map(Announcement::id))
    }

    @Test
    fun `clearing returns the controller to having asked nothing`() = runTest {
        val controller = AnnouncementsController(
            FakeGateway(AppResult.Success(listOf(announcement("a")))),
            this,
        )
        controller.refresh()
        runCurrent()

        controller.clear()

        assertEquals(AnnouncementsState(), controller.state.value)
    }

    private fun announcement(id: String) = Announcement(
        id = id,
        title = "عنوان",
        body = null,
        source = null,
        url = null,
        publishedAt = Instant.parse("2026-08-29T08:30:00Z"),
        importance = AnnouncementImportance.UNKNOWN,
    )

    private class FakeGateway(var answer: AppResult<List<Announcement>>) : AnnouncementsGateway {
        var calls = 0
            private set

        override suspend fun announcements(limit: Int): AppResult<List<Announcement>> {
            calls++
            return answer
        }
    }
}
