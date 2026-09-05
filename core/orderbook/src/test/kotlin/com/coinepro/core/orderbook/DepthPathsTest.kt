package com.coinepro.core.orderbook

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET

/**
 * The wire paths of the two depth feeds, pinned.
 *
 * `core:marketdata` learned this the expensive way and wrote it down: a Retrofit path is a string in
 * an annotation, the compiler has no opinion about it, and `market/candles` against TradeYar's
 * bare-host base resolved to the web portal for as long as that file existed. This module shipped
 * with no path test at all, which is the same gap.
 *
 * The two paths here are addressed at **different hosts** and neither convention is transferable, so
 * they are pinned separately rather than in one loop. The relay's carries TradeYar's mobile prefix
 * because that backend's base address is the bare host; the exchange's carries LBank's futures
 * prefix, `cfd/openApi/v1/pub/`, which is not `v2/` — that is the same exchange's **spot** API, on a
 * different host, holding a book that stood 22.6 USDT away at one measured instant.
 */
class DepthPathsTest {

    private fun pathOf(api: Class<*>, method: String): String =
        api.declaredMethods.first { it.name == method }.getAnnotation(GET::class.java)!!.value

    @Test
    fun `the relayed depth route carries TradeYar's mobile prefix`() {
        // Measured against the live host 2026-09-05: with the prefix the route answers 401 (it
        // exists and wants a session); a path that is not on that server answers 404. Without the
        // prefix this would be the second, and the gateway would read its own misspelling as "the
        // relay has not shipped yet" and tell every reader to wait for a route that went live in
        // August.
        //
        // The path is a constant rather than an annotation now, because one gateway reads two
        // routes — see `TradeYarOrderBookGateway.path` — so the `@GET` is bare and `@Url` carries
        // it. That moves the thing this test exists to protect from the annotation to the
        // companion, and it is pinned here in the same words.
        assertEquals("api/mobile/v1/market/depth", TradeYarOrderBookGateway.MEMBER_PATH)
    }

    @Test
    fun `the public depth twin is on the public prefix, not the mobile one`() {
        // Shipped 2026-09-05 at this app's request and measured the same day: `200` with a book on
        // it, for a caller carrying no token at all, and `422 TYR-021` for a symbol the platform
        // does not list. It is `api/v1/public/…` and not `api/mobile/v1/…`: the mobile prefix is
        // the gated family, and asking for this route there is a 401 with extra steps.
        assertEquals("api/v1/public/market/depth", TradeYarOrderBookGateway.PUBLIC_PATH)
    }

    @Test
    fun `the public depth route is LBank's futures book, not its spot book`() {
        val path = pathOf(LBankFuturesDepthApi::class.java, "marketOrder")
        assertEquals("cfd/openApi/v1/pub/marketOrder", path)
        // The relay reads exactly this path — `_DEPTH_PATH` in their `app/api/mobile/depth.py` — so
        // a reader served by the fallback is looking at the same book they would have been relayed,
        // and the ladder's provenance line stays true either way.
    }
}
