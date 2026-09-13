package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A guest signs in and keeps everything** (run Σ, S6; run Σ-FIX 5).
 *
 * ### The failure this is written against
 *
 * A reader uses this app for three weeks without an account — the watchlist, the layouts, the
 * scripts, the journal, all of it on the phone — and then makes one. The obvious implementation of
 * «now you have an account» is to fetch the account's data and show it, and the obvious
 * implementation is the one that empties their screen. They did not import anything; they *signed
 * in*, and the app replaced three weeks of their work with an empty document belonging to an
 * account created ten seconds ago.
 *
 * Nothing in this app does that, and this file is why we can say so rather than hope. The reason it
 * holds is structural: **there is no account-scoped store.** The watchlist, the saved scripts, the
 * layouts and the journal are all keyed by the device, so signing in changes who the *server* thinks
 * you are and changes nothing about what is on the phone. The only code that then reconciles the two
 * is the watchlist sync, and it merges.
 *
 * So the migration is not a step that runs; it is an absence of a step that would destroy something.
 * That is the kind of claim that quietly stops being true, which is what a test is for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuestMigrationTest {

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun stopControllers() = scopes.forEach { it.cancel() }

    private fun TestScope.controller(gateway: WatchlistSyncGateway, store: WatchlistStore): WatchlistSyncController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scopes += scope
        return WatchlistSyncController(gateway, store, scope) { 0L }
    }

    @Test
    fun `the list a guest built survives their first sync`() = runTest {
        // Three weeks of starring, and a brand new account whose document is empty. The merge keeps
        // the local list and sends it up; it does not adopt the emptiness.
        val store = WatchlistStore(FakeDataStore())
        listOf("BTCUSDT", "XAUUSD", "EURUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }
        val gateway = FreshAccount()

        controller(gateway, store).sync()
        advanceUntilIdle()

        assertEquals(
            listOf("BTCUSDT", "XAUUSD", "EURUSD"),
            store.symbols(Watchlist.DEFAULT_LIST_ID).first(),
        )
        assertTrue("the guest's list was never sent to the account", gateway.writes.isNotEmpty())
    }

    @Test
    fun `signing in adds the account's markets rather than replacing the guest's`() = runTest {
        // The other direction, and the one a reader with two phones actually hits: the account has
        // something this device does not. Both are kept — a sync is a merge, and a merge that
        // dropped either side would be a sync that loses a list somebody made.
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT")
        val gateway = FreshAccount(holding = listOf("SOLUSDT"))

        controller(gateway, store).sync()
        advanceUntilIdle()

        val after = store.symbols(Watchlist.DEFAULT_LIST_ID).first()
        assertTrue("the guest's own symbol went", "BTCUSDT" in after)
        assertTrue("the account's symbol never arrived", "SOLUSDT" in after)
    }

    @Test
    fun `an account with nothing in it takes nothing away`() = runTest {
        // The narrowest statement of the whole file, kept separate because it is the one somebody
        // would break while «fixing» the sync to be authoritative.
        val store = WatchlistStore(FakeDataStore())
        store.add(Watchlist.DEFAULT_LIST_ID, "XAUUSD")
        val before = store.symbols(Watchlist.DEFAULT_LIST_ID).first()

        controller(FreshAccount(), store).sync()
        advanceUntilIdle()

        assertEquals(before, store.symbols(Watchlist.DEFAULT_LIST_ID).first())
    }
}

/** A brand new account: version zero, and whatever another device of theirs had already put in it. */
private class FreshAccount(holding: List<String> = emptyList()) : WatchlistSyncGateway {

    override val supported: Boolean = true

    val writes = mutableListOf<JsonObject>()

    private var document: WatchlistDocument = WatchlistDocument(
        version = 0L,
        payload = if (holding.isEmpty()) JsonObject() else payloadOf(holding),
    )

    override suspend fun read(): WatchlistDocument = document

    override suspend fun write(version: Long, payload: JsonObject): WatchlistDocument {
        writes += payload
        document = WatchlistDocument(version + 1L, payload)
        return document
    }

    private companion object {
        /** The document the route answers with: one list, in the shape `WatchlistPayload` reads. */
        fun payloadOf(symbols: List<String>): JsonObject = JsonObject().apply {
            addProperty("schema", WatchlistPayload.SCHEMA)
            add(
                "lists",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", Watchlist.DEFAULT_LIST_ID)
                            addProperty("name", "")
                            addProperty("updated_at_ms", 9_000L)
                            add("symbols", JsonArray().apply { symbols.forEach(::add) })
                        },
                    )
                },
            )
        }
    }
}
