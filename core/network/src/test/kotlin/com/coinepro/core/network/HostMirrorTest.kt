package com.coinepro.core.network

import java.net.ServerSocket
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.27.0: forex rows were dashes without a VPN; the mirror is the road that is open. */
class HostMirrorTest {

    @Test
    fun `a path and its query are carried onto the mirror's base`() {
        val mirror = "https://tradeyar.trade-future.ir/fx/".toHttpUrl()
        assertEquals(
            "https://tradeyar.trade-future.ir/fx/api/user/me?a=b",
            HostMirror.rewrite("https://coineprofx.com/api/user/me?a=b".toHttpUrl(), mirror).toString(),
        )
        assertEquals(
            "https://tradeyar.trade-future.ir/fx/api/",
            HostMirror.rewrite("https://coineprofx.com/api/".toHttpUrl(), mirror).toString(),
        )
    }

    @Test
    fun `the build's line is read and a malformed one refused`() {
        val parsed = HostMirror.parse("coineprofx.com=https://tradeyar.trade-future.ir/fx/")
        assertEquals("tradeyar.trade-future.ir", parsed.getValue("coineprofx.com").host)
        assertTrue(HostMirror.parse("").isEmpty())
        assertTrue(runCatching { HostMirror.parse("coineprofx.com=http://plain/") }.isFailure)
    }

    /** Answers for the mirror's host without a network; everything else goes out for real. */
    private class FakeMirror(
        private val host: String,
        private val code: Int = 200,
        private val type: String = "application/json",
    ) : Interceptor {
        val paths = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.url.host != host) return chain.proceed(request)
            paths += request.url.encodedPath
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .header("Content-Type", type)
                .body("ok".toResponseBody())
                .build()
        }
    }

    @Test
    fun `an unreachable primary goes down the mirror and stays there`() {
        // A port nothing listens on: the connect is refused before anything is sent.
        val closed = ServerSocket(0).use { it.localPort }
        val deadUrl = "http://127.0.0.1:$closed/api/quotes".toHttpUrl()
        val fake = FakeMirror("mirror.test")
        val memory = MirrorMemory.InProcess()
        val client = OkHttpClient.Builder()
            .addInterceptor(HostMirror(mapOf("127.0.0.1" to "https://mirror.test/fx/".toHttpUrl()), memory, now = { 1_000L }, probeConnectMillis = 500))
            .addInterceptor(fake)
            .build()

        client.newCall(Request.Builder().url(deadUrl).build()).execute().use { assertEquals(200, it.code) }
        assertEquals(listOf("/fx/api/quotes"), fake.paths)
        assertTrue(memory.mirrorUntil("127.0.0.1") > 1_000L)

        client.newCall(Request.Builder().url(deadUrl).build()).execute().close()
        assertEquals(2, fake.paths.size)
    }

    @Test
    fun `a mirror that is not set up is given up on`() {
        val memory = MirrorMemory.InProcess().apply { useMirror("coineprofx.com", Long.MAX_VALUE) }
        val client = OkHttpClient.Builder()
            .addInterceptor(HostMirror(mapOf("coineprofx.com" to "https://mirror.test/fx/".toHttpUrl()), memory))
            .addInterceptor(FakeMirror("mirror.test", code = 404, type = "text/html"))
            .addInterceptor(FakeMirror("coineprofx.com"))
            .build()
        client.newCall(Request.Builder().url("https://coineprofx.com/api/x").build()).execute().use { assertEquals(200, it.code) }
        assertEquals(0L, memory.mirrorUntil("coineprofx.com"))
    }

    @Test
    fun `CoinePro-FX's own 404 through the mirror is an answer, not a reason to switch roads`() {
        // 5.27.0 dropped the mirror on any 404 and flapped back to the filtered primary.
        val memory = MirrorMemory.InProcess().apply { useMirror("coineprofx.com", Long.MAX_VALUE) }
        val fake = FakeMirror("mirror.test", code = 404)
        val client = OkHttpClient.Builder()
            .addInterceptor(HostMirror(mapOf("coineprofx.com" to "https://mirror.test/fx/".toHttpUrl()), memory))
            .addInterceptor(fake)
            .build()
        client.newCall(Request.Builder().url("https://coineprofx.com/api/academy/chart/NOPE").build()).execute().use {
            assertEquals(404, it.code)
        }
        assertEquals(1, fake.paths.size)
        assertTrue(memory.mirrorUntil("coineprofx.com") > 0L)
    }

    @Test
    fun `in Iran the mirror goes first, with no probe of the primary`() {
        val fake = FakeMirror("mirror.test")
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HostMirror(
                    mapOf("coineprofx.com" to "https://mirror.test/fx/".toHttpUrl()),
                    MirrorMemory.InProcess(),
                    preferMirror = { true },
                ),
            )
            .addInterceptor(fake)
            .build()
        client.newCall(Request.Builder().url("https://coineprofx.com/api/user/markets").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(listOf("/fx/api/user/markets"), fake.paths)
    }

    @Test
    fun `a Cloudflare block page is a wall, a JSON refusal is an answer`() {
        fun response(code: Int, type: String) = Response.Builder()
            .request(Request.Builder().url("https://coineprofx.com/api/x").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("x").header("Content-Type", type)
            .body("".toResponseBody()).build()
        assertTrue(HostMirror.refused(response(403, "text/html; charset=UTF-8"), "GET"))
        assertTrue(HostMirror.refused(response(522, "text/html"), "POST"))
        assertTrue(!HostMirror.refused(response(403, "application/json"), "GET"))
        assertTrue(!HostMirror.refused(response(502, "text/html"), "POST"))
    }
}
