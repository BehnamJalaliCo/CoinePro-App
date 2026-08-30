package com.coinepro.core.diagnostics

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The door, and the derivation behind it.
 *
 * No real credential appears anywhere in this file. The tests mint their own from a throwaway
 * password, which is also a demonstration of the arrangement the panel actually uses: a hash is a
 * value you can hand around, and the password it came from is never written down.
 */
class AdminPasswordHashTest {

    /**
     * Published PBKDF2-HMAC-SHA256 vectors, and the reason they are here.
     *
     * The derivation is hand-rolled on top of [javax.crypto.Mac] rather than taken from a
     * `SecretKeyFactory`, because providers disagree about how a password becomes bytes and the
     * disagreement only shows up on a device, in the field, after a password with a non-ASCII
     * character in it. The cost of writing the algorithm is that it has to be proved correct
     * against something other than itself, which is what these are.
     */
    @Test
    fun `the derivation matches the published vectors`() {
        val password = "password"
        val salt = "salt".toByteArray(Charsets.UTF_8)

        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            AdminPasswordHash.derive(password, salt, iterations = 1).hex(),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            AdminPasswordHash.derive(password, salt, iterations = 2).hex(),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            AdminPasswordHash.derive(password, salt, iterations = 4096).hex(),
        )
    }

    @Test
    fun `a non-ASCII password derives to a UTF-8 answer, here and on any device`() {
        // The exact case a `SecretKeyFactory` would answer differently depending on its provider:
        // UTF-8 on the JDK, the low byte of each character on the lineage Android inherited. Pinned
        // so a password with a Persian character in it cannot lock every handset out of the panel
        // while the developer's machine keeps passing.
        val derived = AdminPasswordHash.derive("رمز", "salt".toByteArray(Charsets.UTF_8), 1)
        val latin1 = AdminPasswordHash.derive(
            String("رمز".toByteArray(Charsets.UTF_8).map { (it.toInt() and 0xFF).toChar() }.toCharArray()),
            "salt".toByteArray(Charsets.UTF_8),
            1,
        )
        assertFalse("The two encodings must not be silently interchangeable", derived.hex() == latin1.hex())
    }

    @Test
    fun `the same salt and password always derive the same key, and a different salt never does`() {
        val one = credential("a-throwaway-password", salt = "0123456789abcdef")
        val two = credential("a-throwaway-password", salt = "fedcba9876543210")

        assertTrue(AdminPasswordHash.verify("a-throwaway-password", one))
        assertTrue(AdminPasswordHash.verify("a-throwaway-password", two))
        // Two installs never share a hash, so one leaking says nothing about the other.
        assertFalse(one.hash == two.hash)
    }

    @Test
    fun `nothing stored is the password`() {
        val password = "a-throwaway-password"
        val stored = credential(password)

        assertFalse(stored.hash.contains(password))
        assertFalse(stored.salt.contains(password))
        assertFalse(AdminPasswordHash.verify("a-throwaway-passwore", stored))
        assertFalse(AdminPasswordHash.verify("", stored))
    }

    @Test
    fun `an unprovisioned credential verifies nothing, including the empty password`() {
        val absent = BuildCredential(username = "", salt = "", hash = "")

        assertFalse(absent.present)
        assertFalse(AdminPasswordHash.verify("", absent))
        assertFalse(AdminPasswordHash.verify("anything", absent))
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}

class AdminGateTest {

    private val password = "a-throwaway-password"
    private val credential = credential(password)
    private var now = 1_756_000_000_000L

    @Test
    fun `the right username and password open it`() {
        val gate = AdminGate(credential, clock = { now })

        assertTrue(gate.submit("BehnamJalali", password))
        assertTrue(gate.state.value.unlocked)
        assertEquals(0, gate.state.value.failedAttempts)
    }

    @Test
    fun `the username is forgiving about case and space, and the password is not`() {
        // A phone keyboard capitalises the first letter of a field by default. Being refused
        // because the keyboard helped is a support call, not a security control — the password is
        // the secret here.
        assertTrue(AdminGate(credential, clock = { now }).submit("  behnamjalali ", password))
        assertFalse(AdminGate(credential, clock = { now }).submit("BehnamJalali", password.uppercase()))
    }

    @Test
    fun `a wrong password does not open it and is counted`() {
        val gate = AdminGate(credential, clock = { now })

        assertFalse(gate.submit("BehnamJalali", "wrong"))
        assertFalse(gate.state.value.unlocked)
        assertEquals(1, gate.state.value.failedAttempts)
        assertTrue(gate.state.value.refused)
    }

    @Test
    fun `a wrong username does not open it either`() {
        val gate = AdminGate(credential, clock = { now })

        assertFalse(gate.submit("someone-else", password))
        assertFalse(gate.state.value.unlocked)
    }

    @Test
    fun `a build with no credential fails closed and says which situation it is in`() {
        val gate = AdminGate(credential = null, clock = { now })

        assertFalse(gate.state.value.provisioned)
        assertFalse(gate.submit("BehnamJalali", password))
        assertFalse(gate.state.value.unlocked)
        // The distinction the lock screen draws: an operator hunting for a typo in a build that
        // simply has no key in it is an operator wasting an afternoon.
        assertFalse(gate.state.value.provisioned)
    }

    @Test
    fun `five refusals close it for a while, and the right password is refused too`() {
        val gate = AdminGate(credential, clock = { now })
        repeat(5) { gate.submit("BehnamJalali", "wrong") }

        assertTrue(gate.state.value.lockedAt(now))
        assertFalse(gate.submit("BehnamJalali", password))
        assertFalse(gate.state.value.unlocked)

        now += 5L * 60L * 1_000L + 1
        assertFalse(gate.state.value.lockedAt(now))
        assertTrue(gate.submit("BehnamJalali", password))
    }

    @Test
    fun `every attempt is in the log, and none of them names what was typed`() {
        val log = AppLog()
        val gate = AdminGate(credential, appLog = log, clock = { now })

        gate.submit("BehnamJalali", "wrong")
        gate.submit("BehnamJalali", password)

        val security = log.entries.value.filter { it.tag == LogTag.SECURITY }
        assertEquals(2, security.size)
        assertTrue(security.first().message.contains("refused"))
        assertTrue(security.last().message.contains("unlocked"))
        // An entry naming the input would put a near-miss of the real password into the file this
        // panel exports.
        assertTrue(security.none { it.render().contains("wrong") })
    }

    @Test
    fun `locking closes it again, because a door that only opens is a door in name`() {
        val gate = AdminGate(credential, clock = { now })
        gate.submit("BehnamJalali", password)

        gate.lock()

        assertFalse(gate.state.value.unlocked)
    }

    @Test
    fun `typing again clears the refusal, so the message is about this attempt`() {
        val gate = AdminGate(credential, clock = { now })
        gate.submit("BehnamJalali", "wrong")
        assertTrue(gate.state.value.refused)

        gate.editing()

        assertFalse(gate.state.value.refused)
    }
}

/** A credential minted for a test, exactly the way the build tool mints the real one. */
private fun credential(
    password: String,
    username: String = "BehnamJalali",
    salt: String = "0123456789abcdef",
    iterations: Int = 1_000,
): BuildCredential {
    val saltBytes = salt.toByteArray(Charsets.UTF_8)
    return BuildCredential(
        username = username,
        salt = Base64.getEncoder().encodeToString(saltBytes),
        hash = Base64.getEncoder()
            .encodeToString(AdminPasswordHash.derive(password, saltBytes, iterations)),
        iterations = iterations,
    )
}
