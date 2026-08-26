package com.coinepro.core.security

import android.util.Base64
import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The envelope around an encrypted session token, and where each platform's token is filed.
 *
 * The cipher itself is not tested here and cannot be: `AndroidKeyStore` is a platform service with
 * no local provider. What *is* tested is everything around it, and that is where the failures a
 * reader would feel actually live — a mangled envelope that crashes the app on launch instead of
 * signing them out, or two platforms sharing one preference key so signing out of one signs them
 * out of both.
 *
 * Robolectric is here only for `android.util.Base64`, which is a platform class with no JVM stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionCipherTest {

    @Test
    fun `an envelope round-trips both halves exactly`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(64) { (255 - it).toByte() }

        val (readIv, readCiphertext) = openEnvelope(envelope(iv, ciphertext))

        assertArrayEquals(iv, readIv)
        assertArrayEquals(ciphertext, readCiphertext)
    }

    @Test
    fun `a long value produces no line breaks`() {
        // NO_WRAP, and it matters: base64 with wrapping puts a newline into a preference value,
        // and the read side would have to strip it back out. Forgetting to is a decrypt that
        // works on short tokens and fails on the long ones — which is to say, on real JWTs.
        val ciphertext = ByteArray(4_096) { it.toByte() }
        val text = envelope(ByteArray(12), ciphertext)

        assertTrue("wrapped: $text", '\n' !in text)
        assertTrue('\r' !in text)
        assertEquals(1, text.count { it == ':' })
    }

    @Test
    fun `an envelope with no separator is rejected rather than half-read`() {
        assertThrows { openEnvelope("nocolonhere") }
    }

    @Test
    fun `an envelope with an empty half is rejected`() {
        assertThrows { openEnvelope(":") }
        assertThrows { openEnvelope("abc:") }
        assertThrows { openEnvelope(":abc") }
        assertThrows { openEnvelope("") }
    }

    @Test
    fun `a corrupted value with extra colons is rejected here, not one layer down`() {
        // The split is limited to two, so a third colon stays inside the second half rather than
        // being silently dropped — and `Base64.decode` then refuses the whole thing.
        //
        // That refusal is the point. The alternative some base64 decoders take is to stop at the
        // first bad character and return a shorter payload, which would hand the cipher a
        // truncated ciphertext and fail on GCM's tag check instead — one layer further down, with
        // an error that says nothing about what actually went wrong.
        val iv = Base64.encodeToString(ByteArray(12) { 1 }, Base64.NO_WRAP)
        val body = Base64.encodeToString(ByteArray(32) { 2 }, Base64.NO_WRAP)

        assertThrows { openEnvelope("$iv:$body:trailing") }
    }

    @Test
    fun `the two platforms are filed under different keys`() {
        // Separate systems with separate user tables. One shared key would mean signing out of one
        // signs the reader out of both — and worse, reading one under the other's key would send a
        // TradeYar token to CoinePro-FX.
        val forex = tokenPreferenceName(MarketPlatform.COINEPRO_FX.id)
        val crypto = tokenPreferenceName(MarketPlatform.TRADEYAR.id)

        assertNotEquals(forex, crypto)
        assertTrue(forex.contains(MarketPlatform.COINEPRO_FX.id))
        assertTrue(crypto.contains(MarketPlatform.TRADEYAR.id))
    }

    @Test
    fun `the access and refresh tokens are filed apart, on every platform`() {
        // They have different lifetimes: the access token is replaced every refresh and the refresh
        // token outlives it. One key for both would have each write destroy the other.
        for (platform in MarketPlatform.entries) {
            assertNotEquals(
                "access and refresh collide on ${platform.id}",
                tokenPreferenceName(platform.id),
                refreshPreferenceName(platform.id),
            )
        }
        // And across platforms too, which a naive "refresh_" prefix on a shared name would break.
        val all = MarketPlatform.entries.flatMap {
            listOf(tokenPreferenceName(it.id), refreshPreferenceName(it.id))
        }
        assertEquals("keys collide: $all", all.size, all.toSet().size)
    }

    @Test
    fun `a fake cipher round-trips through the same envelope the real one uses`() {
        // Not a test of the keystore — a test that the seam is a seam. Anything implementing
        // SessionCipher can be dropped in, which is what makes the storage above testable at all.
        val cipher = object : SessionCipher {
            override fun encrypt(value: String): String =
                envelope(ByteArray(12) { 7 }, value.toByteArray(Charsets.UTF_8))

            override fun decrypt(value: String): String =
                String(openEnvelope(value).second, Charsets.UTF_8)
        }
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    private fun assertThrows(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("expected a failure, got none", failure != null)
    }
}
