package com.coinepro.core.security

import androidx.test.core.app.ApplicationProvider
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store, with the keystore swapped for something a JVM can run.
 *
 * Every behaviour here is one a reader would feel: signing out of one platform must not sign them
 * out of the other, a refresh token must not survive a sign-out, and an entry that cannot be read
 * must sign them out rather than crash them out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreSessionTokenStorageTest {

    /**
     * A reversible stand-in, not encryption.
     *
     * It uses the real envelope, so a change to the envelope format breaks these tests too — which
     * is what makes them a test of the store rather than of a mock.
     */
    private class FakeCipher(var failToRead: Boolean = false) : SessionCipher {
        override fun encrypt(value: String): String =
            envelope(ByteArray(12) { 9 }, value.toByteArray(Charsets.UTF_8))

        override fun decrypt(value: String): String {
            if (failToRead) throw IllegalStateException("key invalidated")
            return String(openEnvelope(value).second, Charsets.UTF_8)
        }
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun storage(platform: MarketPlatform, cipher: SessionCipher = FakeCipher()) =
        KeystoreSessionTokenStorage(context, platform, cipher)

    @After
    fun tearDown() = runTest {
        // The DataStore file outlives a single test method, so each one has to leave it empty or
        // the next reads what this one wrote.
        for (platform in MarketPlatform.entries) storage(platform).clear()
    }

    @Test
    fun `a written token reads back`() = runTest {
        val store = storage(MarketPlatform.COINEPRO_FX)
        store.writeToken("access-token")
        store.writeRefreshToken("refresh-token")

        assertEquals("access-token", store.readToken())
        assertEquals("refresh-token", store.readRefreshToken())
    }

    @Test
    fun `nothing written reads as null rather than empty`() = runTest {
        val store = storage(MarketPlatform.TRADEYAR)
        assertNull(store.readToken())
        assertNull(store.readRefreshToken())
    }

    @Test
    fun `the access and refresh tokens do not overwrite each other`() = runTest {
        val store = storage(MarketPlatform.COINEPRO_FX)
        store.writeToken("first")
        store.writeRefreshToken("second")
        store.writeToken("third")

        assertEquals("third", store.readToken())
        assertEquals("second", store.readRefreshToken())
    }

    @Test
    fun `signing out of one platform leaves the other signed in`() = runTest {
        val forex = storage(MarketPlatform.COINEPRO_FX)
        val crypto = storage(MarketPlatform.TRADEYAR)
        forex.writeToken("forex-token")
        crypto.writeToken("crypto-token")

        forex.clear()

        assertNull(forex.readToken())
        assertEquals("the other platform must survive", "crypto-token", crypto.readToken())
    }

    @Test
    fun `clearing takes the refresh token with it`() = runTest {
        // A refresh token left behind is a live credential for an account nobody is signed in to,
        // and the next refresh would silently restore a session the reader deliberately ended.
        val store = storage(MarketPlatform.TRADEYAR)
        store.writeToken("access")
        store.writeRefreshToken("refresh")

        store.clear()

        assertNull(store.readToken())
        assertNull(store.readRefreshToken())
    }

    @Test
    fun `an unreadable entry signs the reader out rather than crashing`() = runTest {
        // The real causes are a keystore key invalidated by a lock-screen change, a partial write,
        // or a backup restored onto another device. All of them mean "sign in again"; none of them
        // should be an exception on launch.
        val cipher = FakeCipher()
        storage(MarketPlatform.COINEPRO_FX, cipher).writeToken("access")

        cipher.failToRead = true
        assertNull(storage(MarketPlatform.COINEPRO_FX, cipher).readToken())
    }

    @Test
    fun `an unreadable entry is dropped, so the next launch does not retry it`() = runTest {
        val cipher = FakeCipher()
        storage(MarketPlatform.COINEPRO_FX, cipher).writeToken("access")

        cipher.failToRead = true
        storage(MarketPlatform.COINEPRO_FX, cipher).readToken()

        // Same entry, a working cipher: it is gone, because the failed read removed it rather than
        // leaving a value the app would fail on at every start.
        cipher.failToRead = false
        assertNull(storage(MarketPlatform.COINEPRO_FX, cipher).readToken())
    }

    @Test
    fun `a blank token is refused rather than stored`() = runTest {
        // An empty Authorization header is not a session, and storing one turns "signed out" into
        // "signed in and rejected by every request".
        val store = storage(MarketPlatform.COINEPRO_FX)
        val failure = runCatching { store.writeToken("   ") }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class, failure!!::class)
        assertNull(store.readToken())
    }
}
