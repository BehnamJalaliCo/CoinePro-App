package com.coinepro.core.watchlistsync

import com.coinepro.core.model.MarketPlatform
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The route, and the two refusals it makes on purpose.
 *
 * `TYR-052` returns the whole current document with the `409` so that a merge costs no second
 * round trip on a connection that has just proved unreliable. If this file ever stops reading that
 * document out of the body, nothing breaks loudly — the gateway simply re-fetches, on the exact
 * connections that cannot afford it, and every conflict silently gets slower.
 */
class WatchlistSyncGatewayTest {

    private fun gateway(api: WatchlistSyncApi, platform: MarketPlatform = MarketPlatform.TRADEYAR) =
        NetworkWatchlistSyncGateway.create(api, platform)

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<JsonObject>(code, body.toResponseBody("application/json".toMediaType())),
    )

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun `the document is served under TradeYar's mobile prefix and nowhere else`() {
        assertEquals("api/mobile/v1/watchlists", WatchlistSyncPaths.of(MarketPlatform.TRADEYAR)?.document)
        // CoinePro-FX has no such route. Posting a reader's watchlist to an address that does not
        // exist answers 404 in wording that reads like an outage.
        assertNull(WatchlistSyncPaths.of(MarketPlatform.COINEPRO_FX))
    }

    @Test
    fun `on the platform with no route the feature is absent, not broken`() = runTest {
        val gateway = gateway(RecordingApi(), MarketPlatform.COINEPRO_FX)

        assertFalse(gateway.supported)
        assertThrows(WatchlistSyncUnsupportedException::class.java) { runBlockingRead(gateway) }
    }

    @Test
    fun `a reader who has never synced reads version zero and an empty payload, not a 404`() = runTest {
        val api = RecordingApi(get = json("""{"version": 0, "payload": {}, "max_bytes": 65536}"""))

        val document = gateway(api).read()

        assertEquals(0L, document.version)
        assertEquals(0, document.payload.size())
        assertEquals(65_536, document.maxBytes)
    }

    @Test
    fun `the cap is read off every response and never hard-coded`() = runTest {
        val api = RecordingApi(get = json("""{"version": 3, "payload": {}, "max_bytes": 131072}"""))

        // A client constant would go wrong the day the server raises its cap, and would go wrong in
        // the direction of refusing to sync a document the server would have accepted.
        assertEquals(131_072, gateway(api).read().maxBytes)
    }

    @Test
    fun `a 409 hands back the whole current document from its own body`() = runTest {
        val api = RecordingApi(
            put = httpError(
                409,
                """{"code": "TYR-052", "version": 7, "payload": {"schema": 1, "lists": []},
                    "max_bytes": 65536}""",
            ),
        )

        val conflict = assertThrows(WatchlistSyncConflict::class.java) {
            runBlockingWrite(gateway(api))
        }

        assertNotNull("The merge has to happen off this body, not off a second GET", conflict.current)
        assertEquals(7L, conflict.current?.version)
        assertEquals(1, conflict.current?.payload?.get("schema")?.asInt)
    }

    @Test
    fun `a 409 whose document is nested under detail is read just the same`() = runTest {
        // TradeYar's mobile routes answer in RFC 7807, so a document delivered with a refusal could
        // reasonably arrive either way round. Reading both costs four lines.
        val api = RecordingApi(
            put = httpError(
                409,
                """{"code": "TYR-052", "detail": {"version": 4, "payload": {"schema": 1}}}""",
            ),
        )

        val conflict = assertThrows(WatchlistSyncConflict::class.java) { runBlockingWrite(gateway(api)) }

        assertEquals(4L, conflict.current?.version)
    }

    @Test
    fun `a 409 with no document at all is still a conflict, just a slower one`() = runTest {
        val api = RecordingApi(put = httpError(409, """{"code": "TYR-052"}"""))

        val conflict = assertThrows(WatchlistSyncConflict::class.java) { runBlockingWrite(gateway(api)) }

        assertNull(conflict.current)
    }

    @Test
    fun `a 413 names the cap the server refused against`() = runTest {
        val api = RecordingApi(put = httpError(413, """{"code": "TYR-053", "max_bytes": 65536}"""))

        val error = assertThrows(WatchlistSyncTooLargeException::class.java) { runBlockingWrite(gateway(api)) }

        assertEquals(65_536, error.maxBytes)
    }

    @Test
    fun `a 413 that named no cap reports none rather than one this app invented`() = runTest {
        val api = RecordingApi(put = httpError(413, """{"detail": "خیلی بزرگ است"}"""))

        assertNull(
            assertThrows(WatchlistSyncTooLargeException::class.java) {
                runBlockingWrite(gateway(api))
            }.maxBytes,
        )
    }

    @Test
    fun `every other failure is left alone for the layer that owns it`() = runTest {
        // A 401 is a session problem and a 500 is an outage. Naming them here would put this file
        // in charge of deciding what an outage means to a reader.
        listOf(401, 500).forEach { code ->
            val api = RecordingApi(put = httpError(code, "{}"))
            assertEquals(
                code,
                assertThrows(HttpException::class.java) { runBlockingWrite(gateway(api)) }.code(),
            )
        }
    }

    @Test
    fun `the write sends the version it was built on`() = runTest {
        val api = RecordingApi(put = json("""{"version": 9, "payload": {}}"""))

        gateway(api).write(8L, json("""{"schema": 1}"""))

        assertEquals(8L, api.lastBody?.get("version")?.asLong)
        assertEquals(1, api.lastBody?.getAsJsonObject("payload")?.get("schema")?.asInt)
        assertTrue(api.lastPath == "api/mobile/v1/watchlists")
    }

    /** `assertThrows` cannot take a suspending block, so the two calls are wrapped. */
    private fun runBlockingRead(gateway: WatchlistSyncGateway) =
        kotlinx.coroutines.runBlocking { gateway.read() }

    private fun runBlockingWrite(gateway: WatchlistSyncGateway) =
        kotlinx.coroutines.runBlocking { gateway.write(1L, JsonObject()) }
}

/** Answers with a prepared body, or throws a prepared failure, and remembers what it was sent. */
private class RecordingApi(
    private val get: JsonObject = JsonObject(),
    private val put: Any = JsonObject(),
) : WatchlistSyncApi {
    var lastPath: String? = null
    var lastBody: JsonObject? = null

    override suspend fun document(path: String): JsonObject {
        lastPath = path
        return get
    }

    override suspend fun putDocument(path: String, body: JsonObject): JsonObject {
        lastPath = path
        lastBody = body
        return when (put) {
            is Throwable -> throw put
            else -> put as JsonObject
        }
    }
}
