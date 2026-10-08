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
    private class FakeMirror(private val host: String, private val code: Int = 200) : Interceptor {
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
            .addInterceptor(FakeMirror("mirror.test", code = 404))
            .build()
        client.newCall(Request.Builder().url("https://coineprofx.com/api/x").build()).execute().close()
        assertEquals(0L, memory.mirrorUntil("coineprofx.com"))
    }
}
