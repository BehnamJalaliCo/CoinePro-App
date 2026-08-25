package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSessionsTest {

    private class FakeStorage(private var token: String? = null) : SessionTokenStorage {
        override suspend fun readToken(): String? = token
        override suspend fun writeToken(token: String) {
            this.token = token
        }

        override suspend fun clear() {
            token = null
        }

        fun peek(): String? = token
    }

    private class FakeGateway(private val profileName: String) : AuthGateway {
        override suspend fun authConfig() = AppResult.Success(AuthConfig("bot"))
        override suspend fun loginTelegram(payload: TelegramAuthPayload) =
            AppResult.Failure(ErrorKind.UNKNOWN)

        override suspend fun me(): AppResult<UserProfile> =
            AppResult.Success(UserProfile(telegramId = 1L, name = profileName))
    }

    // The unauthorized collector and the two stateIn shares never complete, so they belong to
    // backgroundScope; runTest would otherwise wait on them until it times out.
    private fun sessions(
        scope: CoroutineScope,
        forexStorage: FakeStorage,
        cryptoStorage: FakeStorage,
        includeCrypto: Boolean = true,
    ): PlatformSessions {
        val controllers = buildMap {
            put(
                MarketPlatform.COINEPRO_FX,
                SessionController(forexStorage, SessionMemory(), FakeGateway("forex"), scope),
            )
            if (includeCrypto) {
                put(
                    MarketPlatform.TRADEYAR,
                    SessionController(cryptoStorage, SessionMemory(), FakeGateway("crypto"), scope),
                )
            }
        }
        return PlatformSessions(controllers, scope)
    }

    @Test
    fun `signing out of one platform leaves the other signed in`() = runTest(UnconfinedTestDispatcher()) {
        val forex = FakeStorage("forex-token")
        val crypto = FakeStorage("crypto-token")
        val platforms = sessions(backgroundScope, forex, crypto)

        platforms.start()

        assertEquals(
            setOf(MarketPlatform.COINEPRO_FX, MarketPlatform.TRADEYAR),
            platforms.signedIn.value,
        )

        platforms.logout(MarketPlatform.COINEPRO_FX)

        assertEquals(setOf(MarketPlatform.TRADEYAR), platforms.signedIn.value)
        assertNull(forex.peek())
        assertEquals("crypto-token", crypto.peek())
    }

    @Test
    fun `each platform resolves its own profile`() = runTest(UnconfinedTestDispatcher()) {
        val platforms = sessions(backgroundScope, FakeStorage("a"), FakeStorage("b"))
        platforms.start()

        val states = platforms.states.value
        assertEquals(
            "forex",
            (states.getValue(MarketPlatform.COINEPRO_FX) as SessionState.SignedIn).profile.name,
        )
        assertEquals(
            "crypto",
            (states.getValue(MarketPlatform.TRADEYAR) as SessionState.SignedIn).profile.name,
        )
    }

    @Test
    fun `a platform with no stored token is signed out while the other is not`() =
        runTest(UnconfinedTestDispatcher()) {
            val platforms = sessions(backgroundScope, FakeStorage("forex-token"), FakeStorage(null))
            platforms.start()

            assertEquals(setOf(MarketPlatform.COINEPRO_FX), platforms.signedIn.value)
            assertTrue(platforms.states.value.getValue(MarketPlatform.TRADEYAR) is SessionState.SignedOut)
        }

    @Test
    fun `an unconfigured platform is neither offered nor reachable`() =
        runTest(UnconfinedTestDispatcher()) {
            val platforms = sessions(backgroundScope, FakeStorage("t"), FakeStorage("t"), includeCrypto = false)

            assertEquals(listOf(MarketPlatform.COINEPRO_FX), platforms.platforms)
            assertFalse(platforms.isConfigured(MarketPlatform.TRADEYAR))
            assertNull(platforms.controllerOrNull(MarketPlatform.TRADEYAR))
            assertThrows(IllegalStateException::class.java) {
                platforms.controller(MarketPlatform.TRADEYAR)
            }
        }

    @Test
    fun `platform order is stable regardless of map insertion order`() =
        runTest(UnconfinedTestDispatcher()) {
            val controllers = linkedMapOf(
                MarketPlatform.TRADEYAR to
                    SessionController(FakeStorage(), SessionMemory(), FakeGateway("crypto"), backgroundScope),
                MarketPlatform.COINEPRO_FX to
                    SessionController(FakeStorage(), SessionMemory(), FakeGateway("forex"), backgroundScope),
            )
            assertEquals(
                listOf(MarketPlatform.COINEPRO_FX, MarketPlatform.TRADEYAR),
                PlatformSessions(controllers, backgroundScope).platforms,
            )
        }
}
