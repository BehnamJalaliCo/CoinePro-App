package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.WatchlistStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One sync, from the reader's side of it.
 *
 * The property every one of these is really asserting is the same one: after any sync, whatever
 * happened, nothing this device held has gone. Offline, refused, over the cap, or in conflict with
 * another phone — the local watchlist is still a working watchlist, because for this audience it
 * is the only one they have most of the time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistSyncControllerTest {

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun stopControllers() = scopes.forEach { it.cancel() }

    private fun store(clock: Long = 1_000L) = WatchlistStore(FakeDataStore()) { clock }

    /**
     * A controller on a scope this test drives.
     *
     * Deliberately not the test's own background scope: `advanceUntilIdle` ignores work launched
     * there when it decides whether the scheduler is idle, so a controller living in one never
     * executes a line, and every assertion below about what it did would be an assertion about a
     * controller that never ran — passing or failing for reasons that have nothing to do with the
     * code under test. This scope shares the test's scheduler, so `advanceUntilIdle` drives it as
     * written, and it is torn down in [stopControllers] because the cursor collector in the
     * controller's `init` never finishes on its own.
     */
    private fun TestScope.controller(
        gateway: WatchlistSyncGateway,
        store: WatchlistStore,
        clock: () -> Long = { 0L },
    ): WatchlistSyncController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scopes += scope
        return WatchlistSyncController(gateway, store, scope, clock)
    }

    private fun payloadOf(vararg listIds: String): JsonObject = JsonObject().apply {
        addProperty("schema", WatchlistPayload.SCHEMA)
        add(
            "lists",
            JsonArray().apply {
                listIds.forEach { id ->
                    add(
                        JsonObject().apply {
                            addProperty("id", id)
                            addProperty("name", id)
                            addProperty("updated_at_ms", 9_000L)
                            add("symbols", JsonArray().apply { add("SOLUSDT") })
                        },
                    )
                }
            },
        )
    }

    @Test
    fun `a first-ever sync sends what this device has and loses none of it`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val listId = store.create("طلا")
        store.add(listId, "XAUUSD")
        // What the route answers a reader who has never synced with: version zero, empty payload.
        val gateway = FakeGateway(document = WatchlistDocument(0L, JsonObject(), maxBytes = 65_536))

        val controller = controller(gateway, store) { 4_242L }
        controller.sync()
        advanceUntilIdle()

        assertEquals(1, gateway.writes.size)
        assertEquals(0L, gateway.writes.single().first)
        val sent = WatchlistPayload.decode(gateway.writes.single().second)
        assertEquals(listOf("default", listId), sent.lists.map { it.id })
        assertEquals(listOf("BTCUSDT"), sent.lists.first().symbols)
        assertEquals(WatchlistSyncNotice.UPLOADED, controller.state.value.notice)
        assertEquals(4_242L, controller.state.value.lastSyncedAtMs)
    }

    @Test
    fun `an empty remote takes nothing away from this device`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(document = WatchlistDocument(2L, JsonObject(), maxBytes = 65_536))

        controller(gateway, store).also { it.sync() }
        advanceUntilIdle()

        assertEquals(listOf("BTCUSDT"), store.symbols.first())
    }

    @Test
    fun `a list from the other phone arrives and the reader is told how much`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(
            document = WatchlistDocument(5L, payloadOf("list_other"), maxBytes = 65_536),
        )

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals(listOf("default", "list_other"), store.lists().first().map { it.id })
        assertEquals(WatchlistSyncNotice.MERGED, controller.state.value.notice)
        assertEquals(1, controller.state.value.listsAdopted)
        assertEquals(1, controller.state.value.symbolsAdopted)
    }

    @Test
    fun `a stale version is settled from the body of the 409, never with a second read`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = 65_536))
        // The server moved on between this device's read and its write, and says so by handing back
        // the document it now holds.
        gateway.writeFailures.add(
            WatchlistSyncConflict(WatchlistDocument(6L, payloadOf("list_other"), maxBytes = 65_536)),
        )

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals("A conflict must not cost a second round trip", 1, gateway.reads)
        assertEquals(2, gateway.writes.size)
        assertEquals("The retry is built on the version the refusal carried", 6L, gateway.writes[1].first)
        val sent = WatchlistPayload.decode(gateway.writes[1].second)
        assertEquals(listOf("default", "list_other"), sent.lists.map { it.id })
        assertEquals(listOf("BTCUSDT"), sent.lists.first().symbols)
        assertEquals(WatchlistSyncNotice.MERGED, controller.state.value.notice)
    }

    @Test
    fun `a 409 that carried no document falls back to reading again`() = runTest {
        val store = store()
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = 65_536))
        gateway.writeFailures.add(WatchlistSyncConflict(null))

        controller(gateway, store).also { it.sync() }
        advanceUntilIdle()

        assertEquals(2, gateway.reads)
    }

    @Test
    fun `a conflict that never settles leaves everything on this device`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = 65_536))
        repeat(5) { gateway.writeFailures.add(WatchlistSyncConflict(WatchlistDocument(it + 2L, JsonObject()))) }

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals("The loop is bounded", 3, gateway.writes.size)
        assertEquals(WatchlistSyncNotice.REFUSED, controller.state.value.notice)
        assertEquals(listOf("BTCUSDT"), store.symbols.first())
    }

    @Test
    fun `a document over the cap is not even sent, and the cap comes from the server`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        // Ten bytes. Spending a metered connection on a round trip whose answer is already known is
        // the difference a reader on a phone plan notices.
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = 10))

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertTrue(gateway.writes.isEmpty())
        assertEquals(WatchlistSyncNotice.TOO_LARGE, controller.state.value.notice)
        assertEquals(10, controller.state.value.maxBytes)
        assertEquals(listOf("BTCUSDT"), store.symbols.first())
    }

    @Test
    fun `a 413 from the server writes nothing and keeps the local watchlist working`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = null))
        gateway.writeFailures.add(WatchlistSyncTooLargeException(65_536))

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals(WatchlistSyncNotice.TOO_LARGE, controller.state.value.notice)
        assertEquals(65_536, controller.state.value.maxBytes)
        assertEquals(listOf("BTCUSDT"), store.symbols.first())
        assertEquals("Nothing was written, so nothing is claimed", 0L, controller.state.value.lastSyncedAtMs)
    }

    @Test
    fun `offline is quiet and changes nothing`() = runTest {
        val store = store()
        store.toggle("BTCUSDT")
        val gateway = FakeGateway(readFailure = IOException("no route to host"))

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals(WatchlistSyncNotice.OFFLINE, controller.state.value.notice)
        assertEquals(listOf("BTCUSDT"), store.symbols.first())
        assertFalse(controller.state.value.syncing)
    }

    @Test
    fun `on the platform with no route nothing is called at all`() = runTest {
        val gateway = FakeGateway(supported = false)

        val controller = controller(gateway, store())
        assertFalse(controller.state.value.available)
        controller.sync()
        advanceUntilIdle()

        assertEquals(0, gateway.reads)
        assertEquals(WatchlistSyncNotice.UNSUPPORTED, controller.state.value.notice)
    }

    @Test
    fun `a document the server already holds is not sent again`() = runTest {
        val store = store()
        val gateway = FakeGateway(document = WatchlistDocument(4L, JsonObject(), maxBytes = 65_536))
        val controller = controller(gateway, store)

        controller.sync()
        advanceUntilIdle()
        gateway.document = WatchlistDocument(5L, gateway.writes.single().second, maxBytes = 65_536)
        controller.sync()
        advanceUntilIdle()

        assertEquals("The second sync had nothing to say", 1, gateway.writes.size)
        assertEquals(WatchlistSyncNotice.UP_TO_DATE, controller.state.value.notice)
    }

    @Test
    fun `a list deleted on the other phone is removed here and reported as a removal`() = runTest {
        val store = store()
        val listId = store.create("موقت")
        store.add(listId, "SOLUSDT")
        val remote = JsonObject().apply {
            addProperty("schema", WatchlistPayload.SCHEMA)
            add("lists", JsonArray())
            add("deleted", JsonObject().apply { addProperty(listId, 9_000L) })
        }
        val gateway = FakeGateway(document = WatchlistDocument(2L, remote, maxBytes = 65_536))

        val controller = controller(gateway, store)
        controller.sync()
        advanceUntilIdle()

        assertEquals(listOf("default"), store.lists().first().map { it.id })
        assertEquals(WatchlistSyncNotice.REMOVED, controller.state.value.notice)
        assertEquals(1, controller.state.value.listsDropped)
    }

    @Test
    fun `a second sync while one is running is dropped rather than queued`() = runTest {
        val store = store()
        val gateway = FakeGateway(document = WatchlistDocument(1L, JsonObject(), maxBytes = 65_536))

        val controller = controller(gateway, store)
        controller.sync()
        controller.sync()
        advanceUntilIdle()

        assertEquals(1, gateway.reads)
    }
}

/** Answers with a prepared document, and fails the writes it was told to fail, in order. */
private class FakeGateway(
    override val supported: Boolean = true,
    var document: WatchlistDocument = WatchlistDocument(0L, JsonObject()),
    private val readFailure: Throwable? = null,
) : WatchlistSyncGateway {
    var reads = 0
    val writes = mutableListOf<Pair<Long, JsonObject>>()
    val writeFailures = ArrayDeque<Throwable>()

    override suspend fun read(): WatchlistDocument {
        reads++
        readFailure?.let { throw it }
        return document
    }

    override suspend fun write(version: Long, payload: JsonObject): WatchlistDocument {
        writes += version to payload
        writeFailures.removeFirstOrNull()?.let { throw it }
        document = WatchlistDocument(version + 1L, payload, maxBytes = document.maxBytes)
        return document
    }
}
