package com.coinepro.core.account

import com.coinepro.core.network.NetworkFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pro page's three reads, against the bodies TradeYar actually sends (2026-10-06). */
class PaymentsGatewayTest {

    private fun gateway(code: Int, body: String, seen: MutableList<String> = mutableListOf()): PaymentsGateway {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                seen += request.method + " " + request.url.encodedPath
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("x")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return NetworkPaymentsGateway.create(NetworkFactory.retrofit("https://tradeyar.example/", client))
    }

    @Test
    fun `the plans document is read with its USDT wallet`() = runTest {
        val plans = gateway(200, PLANS).plans()!!
        assertEquals(listOf("monthly", "quarterly", "yearly"), plans.plans.map { it.id })
        assertEquals(listOf("15", "35", "120"), plans.plans.map { it.priceUsdt })
        assertEquals(399_000L, plans.plans.first().priceToman)
        assertEquals("0xaB828f7864A9d3a90acB12260CFeD5b6Ff0BE1E3", plans.usdtWallet)
        assertEquals("BEP20", plans.usdtNetwork)
        assertTrue(plans.bazaar)
    }

    @Test
    fun `a refused hash carries the server's sentence`() = runTest {
        val seen = mutableListOf<String>()
        val claim = gateway(422, """{"detail":{"code":"VAL-001","message":"مبلغ این تراکنش از قیمت این طرح کمتر است.","field":"underpaid"}}""", seen)
            .claimUsdt("monthly", " 0xabc ")
        assertEquals(UsdtClaim.Refused("مبلغ این تراکنش از قیمت این طرح کمتر است."), claim)
        assertEquals(listOf("POST /api/mobile/v1/payments/usdt"), seen)
    }

    @Test
    fun `a verified hash is the new period, and a 401 asks for the account`() = runTest {
        assertEquals(
            UsdtClaim.Activated("2026-11-05T10:00:00+00:00"),
            gateway(200, """{"plan":"monthly","starts_at":"2026-10-06T10:00:00+00:00","ends_at":"2026-11-05T10:00:00+00:00"}""").claimUsdt("monthly", "0xabc"),
        )
        assertEquals(UsdtClaim.Refused(null, needsSignIn = true), gateway(401, "{}").claimUsdt("monthly", "0xabc"))
    }

    @Test
    fun `only a premium answer is a running period`() = runTest {
        assertEquals(null, gateway(200, """{"all":true,"premium":false,"paywall":false,"plan":null,"ends_at":null}""").current())
        assertEquals(
            ProPeriod("yearly", "2027-10-06T00:00:00+00:00"),
            gateway(200, """{"all":true,"premium":true,"paywall":false,"plan":"yearly","ends_at":"2027-10-06T00:00:00+00:00"}""").current(),
        )
    }

    private companion object {
        const val PLANS = """{"paywall":false,"plans":[{"id":"monthly","days":30,"price_toman":399000,"price_usdt":"15","bazaar_sku":"pro_monthly"},{"id":"quarterly","days":90,"price_toman":999000,"price_usdt":"35","bazaar_sku":"pro_quarterly"},{"id":"yearly","days":365,"price_toman":1999000,"price_usdt":"120","bazaar_sku":"pro_yearly"}],"usdt":{"network":"BEP20","wallet":"0xaB828f7864A9d3a90acB12260CFeD5b6Ff0BE1E3"},"bazaar":true}"""
    }
}
