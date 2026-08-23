package com.coinepro.core.network

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFactorySecurityTest {
    @Test
    fun `HTTP logging is absent by default`() {
        val client = NetworkFactory.okHttpClient()

        assertFalse(client.interceptors.any { it is HttpLoggingInterceptor })
    }

    @Test
    fun `debug HTTP logging is BASIC when explicitly enabled`() {
        val client = NetworkFactory.okHttpClient(enableHttpLogging = true)
        val loggers = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()

        assertEquals(1, loggers.size)
        assertEquals(HttpLoggingInterceptor.Level.BASIC, loggers.single().level)
    }

    @Test
    fun `retrofit rejects cleartext base URLs`() {
        val client = NetworkFactory.okHttpClient()

        val failure = runCatching {
            NetworkFactory.retrofit("http://example.invalid/", client)
        }

        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }
}
