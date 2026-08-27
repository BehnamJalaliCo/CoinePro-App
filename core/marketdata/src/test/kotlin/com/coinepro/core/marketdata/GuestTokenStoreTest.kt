package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guest token's lifetime arithmetic.
 *
 * All of it is about not minting more often than necessary: the route allows thirty tokens per ten
 * minutes per address, and under carrier-grade NAT that address is shared by a great many phones.
 * A store that re-minted on every chart open would spend a whole city's allowance.
 */
class GuestTokenStoreTest {

    @Test
    fun `one token is reused until it is close to expiry`() = runTest {
        var now = 0L
        val api = CountingApi(GuestTokenDto(token = "t1", expiresIn = 7_200, scope = "guest"))
        val store = NetworkGuestTokenStore.forTest(api) { now }

        assertEquals("t1", store.token())
        now += 60_000
        assertEquals("t1", store.token())
        now += 60 * 60_000
        assertEquals("t1", store.token())

        assertEquals(1, api.calls)
    }

    @Test
    fun `a token inside the renewal margin is replaced rather than used`() = runTest {
        var now = 0L
        val api = CountingApi(GuestTokenDto(token = "t1", expiresIn = 7_200))
        val store = NetworkGuestTokenStore.forTest(api) { now }
        store.token()

        // Two hours less one minute: inside the two-minute margin, so a request that took a while
        // would otherwise be issued against a token that dies mid-flight.
        now = (7_200 - 60) * 1_000L
        api.next = GuestTokenDto(token = "t2", expiresIn = 7_200)

        assertEquals("t2", store.token())
        assertEquals(2, api.calls)
    }

    @Test
    fun `a response with no lifetime is held conservatively rather than forever`() = runTest {
        var now = 0L
        val api = CountingApi(GuestTokenDto(token = "t1"))
        val store = NetworkGuestTokenStore.forTest(api) { now }
        store.token()

        // A missing lifetime that defaulted to "never" would produce a token that is silently dead
        // while every chart request answers 401 and nothing on screen explains it.
        now = 31 * 60_000L
        api.next = GuestTokenDto(token = "t2")
        assertEquals("t2", store.token())
    }

    @Test
    fun `clearing forgets the token`() = runTest {
        val api = CountingApi(GuestTokenDto(token = "t1", expiresIn = 7_200))
        val store = NetworkGuestTokenStore.forTest(api) { 0L }
        store.token()
        store.clear()
        api.next = GuestTokenDto(token = "t2", expiresIn = 7_200)

        assertEquals("t2", store.token())
        assertEquals(2, api.calls)
    }

    @Test
    fun `a mint that returns no token fails loudly`() = runTest {
        val store = NetworkGuestTokenStore.forTest(CountingApi(GuestTokenDto(token = null))) { 0L }

        val failure = runCatching { store.token() }
        assertTrue(failure.isFailure)
    }
}

private class CountingApi(var next: GuestTokenDto) : GuestTokenApi {
    var calls = 0
    override suspend fun mint(): GuestTokenDto {
        calls++
        return next
    }
}
