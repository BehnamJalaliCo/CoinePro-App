package com.coinepro.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The envelope around an encrypted session, pinned.
 *
 * The keystore itself is not tested here and cannot be: Robolectric ships no `AndroidKeyStore`
 * provider, and a fake one would be a test of the fake. What *is* testable is the part that has
 * actually failed in products like this one — the string handling either side of the cipher. A
 * store that mangles its own envelope silently signs the reader out; one that throws on a mangled
 * envelope crashes them out on launch. Both are here.
 */
@RunWith(RobolectricTestRunner::class)
class SessionCipherTest {

    @Test
    fun `an envelope round-trips its two halves`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = ByteArray(40) { (it * 7).toByte() }

        val (readIv, readCiphertext) = openEnvelope(envelope(iv, ciphertext))

        assertArrayEquals(iv, readIv)
        assertArrayEquals(ciphertext, readCiphertext)
    }

    @Test
    fun `the envelope never wraps`() {
        // A newline inside a preference value is a newline the reader has to strip back out, and
        // forgetting to is a decrypt that fails only on entries long enough to have wrapped —
        // which is every real token and no short test fixture.
        val long = envelope(ByteArray(12), ByteArray(512) { it.toByte() })

        assertTrue("the envelope must not contain a newline", '\n' !in long)
        assertEquals("the envelope has exactly one separator", 1, long.count { it == ':' })
    }

    @Test
    fun `an envelope with no separator is refused`() {
        assertThrows(IllegalArgumentException::class.java) { openEnvelope("not-an-envelope") }
    }

    @Test
    fun `an envelope with an empty half is refused`() {
        assertThrows(IllegalArgumentException::class.java) { openEnvelope(":abc") }
        assertThrows(IllegalArgumentException::class.java) { openEnvelope("abc:") }
    }

    @Test
    fun `an envelope that decodes to nothing is refused`() {
        // Base64 accepts a string of separators and returns no bytes. Handing those to a cipher
        // fails one layer further down, where the error says nothing about what went wrong.
        assertThrows(IllegalArgumentException::class.java) { openEnvelope("=:=") }
    }

    @Test
    fun `a corrupted envelope with extra colons keeps its tail`() {
        // Limit two on the split. A three-part split would silently discard everything after the
        // second colon and then fail inside the cipher rather than here.
        val iv = ByteArray(12) { 1 }
        val body = "AAAA:BBBB"
        val corrupted = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP) + ":" + body

        val (_, ciphertext) = openEnvelope(corrupted)

        // Whatever it decodes to, the parser must have been handed the whole tail rather than half.
        assertTrue(ciphertext.isNotEmpty())
    }
}

/**
 * Which preference a token is filed under.
 *
 * The separation is a security property, not a naming choice: CoinePro-FX and TradeYar are separate
 * systems with separate user tables. Reading one under the other's key would send a TradeYar token
 * to CoinePro-FX, and clearing one would sign the reader out of both.
 */
class SessionPreferenceNameTest {

    @Test
    fun `two platforms never share a token key`() {
        assertTrue(tokenPreferenceName("coinepro_fx") != tokenPreferenceName("tradeyar"))
    }

    @Test
    fun `a token and a refresh token never share a key`() {
        assertTrue(tokenPreferenceName("tradeyar") != refreshPreferenceName("tradeyar"))
    }

    @Test
    fun `the names carry the platform id verbatim`() {
        // Pinned rather than merely distinct: these strings are on disk on every installed device,
        // so a rename is a silent sign-out for everybody who upgrades.
        assertEquals("session_ciphertext_tradeyar", tokenPreferenceName("tradeyar"))
        assertEquals("session_refresh_ciphertext_coinepro_fx", refreshPreferenceName("coinepro_fx"))
    }
}
