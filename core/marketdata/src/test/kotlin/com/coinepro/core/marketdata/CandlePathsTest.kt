package com.coinepro.core.marketdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import retrofit2.http.GET
import org.junit.Test

/**
 * The wire paths of the two candle feeds, pinned.
 *
 * Every other crypto gateway in this app has a test like this one and `core:marketdata` did not,
 * which is exactly why its path was wrong: `market/candles` against TradeYar's bare-host base
 * resolves to the web portal and answers 307 to `/login`, while `api/mobile/v1/market/candles`
 * answers 401. Nothing failed, because a Retrofit path is a string in an annotation and the
 * compiler has no opinion about it, and the app had never been run against a live server.
 *
 * The two backends do not share a convention and that is the trap: CoinePro-FX serves its mobile
 * surface at the root (`academy/…`, `user/…`) and TradeYar serves its own under `api/mobile/v1/`.
 * The same interface elsewhere in this codebase carries one of each for that reason. So a path
 * that reads plausibly can still be addressed at the wrong host's shape, and only a pin catches it.
 */
class CandlePathsTest {

    private fun pathOf(api: Class<*>, method: String): String =
        api.declaredMethods.first { it.name == method }.getAnnotation(GET::class.java)!!.value

    @Test
    fun `the crypto candle route carries TradeYar's mobile prefix`() {
        assertEquals("api/mobile/v1/market/candles", pathOf(CryptoCandleApi::class.java, "candles"))
    }

    @Test
    fun `the forex chart routes are addressed at the root, as CoinePro-FX serves them`() {
        val api = AcademyChartApi::class.java
        for (method in api.declaredMethods) {
            val path = method.getAnnotation(GET::class.java)?.value ?: continue
            assertTrue(
                "$path is a CoinePro-FX route and must not carry TradeYar's prefix",
                !path.startsWith("api/mobile/v1/"),
            )
            assertTrue("$path should sit under academy/", path.startsWith("academy/"))
        }
    }
}
