package com.coinepro.core.diagnostics

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one credential that opens the admin panel.
 *
 * ### Where the values come from, and why not from here
 *
 * Neither the password nor its hash is in this repository, and that is a deliberate arrangement
 * rather than an accident of where the file sits.
 *
 * The password itself is never stored anywhere at all — not here, not in a build file, not on the
 * device. What is stored is a PBKDF2 derivation of it under a random salt, which is a value that
 * cannot be turned back into the password and is useless against any other system even if it
 * leaks.
 *
 * That derivation is supplied at build time from `local.properties`, which `.gitignore` refuses,
 * `scripts/security/scan-secrets.sh` fails the build over if it is ever tracked, and Android Studio
 * already creates on every developer machine. The application module reads it exactly the way it
 * already reads the release signing credentials — a Gradle property first, the untracked file
 * second — and passes it here as a [BuildCredential]. Three consequences are worth stating:
 *
 *  * **Nothing in a tracked file can open the panel.** A clone of this repository builds an app
 *    whose admin door has no key in it at all.
 *  * **Rotating it is editing one untracked line.** No migration, no stored state to clear, no
 *    release needed on any server.
 *  * **A build that was never given one fails closed.** [AdminGate] with a null credential refuses
 *    every attempt and says so, which is the correct behaviour for a door — the alternative, and
 *    the behaviour this replaces, was five taps opening the panel outright.
 */
data class BuildCredential(
    val username: String,
    /** Base64. Random per credential, and the reason two installs never share a hash. */
    val salt: String,
    /** Base64 of the PBKDF2 output. */
    val hash: String,
    val iterations: Int = AdminPasswordHash.ITERATIONS,
) {
    /**
     * Whether this is a credential at all.
     *
     * A build configured with empty strings — which is what a `buildConfigField` reads when the
     * property is absent — must not produce a gate that accepts an empty password.
     */
    val present: Boolean
        get() = username.isNotBlank() && salt.isNotBlank() && hash.isNotBlank() && iterations > 0
}

/**
 * PBKDF2-HMAC-SHA256, implemented on top of [Mac] rather than taken from a `SecretKeyFactory`.
 *
 * The reason is portability of one specific decision. `PBKDF2WithHmacSHA256` does not agree with
 * itself across providers about how a password becomes bytes: the JDK's implementation encodes it
 * as UTF-8, while the Bouncy Castle lineage Android inherited historically used the PKCS#5
 * convention of the low byte of each character. For an ASCII password the two are identical, which
 * is exactly the kind of agreement that holds right up until somebody changes the password to one
 * with a Persian character in it and every device in the field stops opening the panel — while the
 * unit test on the developer's JDK keeps passing.
 *
 * Fifty lines of the algorithm from RFC 8018 removes the question. The password is UTF-8, here and
 * everywhere, and the derivation this file computes is the derivation the build tool computed.
 */
object AdminPasswordHash {

    /**
     * Iterations.
     *
     * This runs once, on a deliberate button press, on the main thread of a screen that is doing
     * nothing else. A hundred thousand rounds is roughly a fifth of a second on a mid-range phone —
     * unnoticeable to the operator, and the difference between a leaked hash being worth attacking
     * and not. It is stored alongside the hash so raising it later does not invalidate a credential
     * issued under the old count.
     */
    const val ITERATIONS: Int = 100_000

    /** 256 bits, matching the hash function. A longer output would be extra rounds for nothing. */
    private const val KEY_BYTES = 32
    private const val ALGORITHM = "HmacSHA256"

    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), ALGORITHM))
        val blockCount = (KEY_BYTES + mac.macLength - 1) / mac.macLength
        val out = ByteArray(blockCount * mac.macLength)

        for (block in 1..blockCount) {
            // U1 = PRF(password, salt || INT(block)), big-endian, per RFC 8018 section 5.2.
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte(),
                ),
            )
            var u = mac.doFinal()
            val accumulated = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (index in accumulated.indices) {
                    accumulated[index] = (accumulated[index].toInt() xor u[index].toInt()).toByte()
                }
            }
            accumulated.copyInto(out, (block - 1) * mac.macLength)
        }
        return out.copyOf(KEY_BYTES)
    }

    /**
     * Whether a typed password derives to the stored hash.
     *
     * [MessageDigest.isEqual] rather than `contentEquals`: it compares every byte regardless of
     * where the first difference is, so the time this takes says nothing about how much of the
     * password was right. That matters less on a local gate than it does on a server, and it costs
     * nothing to be correct about.
     */
    fun verify(password: String, credential: BuildCredential): Boolean {
        if (!credential.present) return false
        return runCatching {
            val salt = Base64.getDecoder().decode(credential.salt)
            val expected = Base64.getDecoder().decode(credential.hash)
            MessageDigest.isEqual(expected, derive(password, salt, credential.iterations))
        }.getOrDefault(false)
    }
}

/**
 * What the lock screen draws.
 *
 * [provisioned] is separate from every other field because a build with no credential and a build
 * with the wrong password typed into it are different situations with different fixes, and a screen
 * that showed both as "wrong password" would send an operator hunting for a typo in a build that
 * simply has no key in it.
 */
data class AdminGateState(
    val unlocked: Boolean = false,
    val provisioned: Boolean = false,
    val failedAttempts: Int = 0,
    /** Wall clock. Non-null means every attempt is refused until it passes. */
    val lockedUntilEpochMillis: Long? = null,
    /** Set after a refused attempt, cleared as soon as the operator types again. */
    val refused: Boolean = false,
) {
    fun lockedAt(now: Long): Boolean = lockedUntilEpochMillis?.let { it > now } == true

    fun remainingLockMillis(now: Long): Long =
        lockedUntilEpochMillis?.minus(now)?.coerceAtLeast(0) ?: 0
}

/**
 * The door.
 *
 * ### Why the panel needs one at all
 *
 * It reaches further than any other screen in the app: it signs sessions out on both platforms,
 * restarts the market feed, drops caches, re-registers the push token, fires requests at both
 * gateways, and exports a file describing the install. Five taps on a version label opened all of
 * that to whoever was holding the phone — a repair shop, a family member, anybody the handset was
 * passed to for a minute. The taps are still how it is *found*, which keeps it invisible to an
 * ordinary reader; they are no longer how it is *entered*.
 *
 * ### Failed attempts cost time
 *
 * After [maxAttempts] refusals the gate closes for [lockoutMillis]. A four-digit-PIN style attack
 * is not the threat here — a twelve-character password is not going to be guessed — but a lockout
 * is what turns a stolen handset from "somebody can sit and try" into "somebody cannot", and it
 * puts every attempt in the log under [LogTag.SECURITY] where an operator will actually see it.
 *
 * The counter is in memory, so it resets when the process does. That is a deliberate limit rather
 * than an oversight: persisting it would mean an app that can lock its owner out of their own
 * diagnostics across a restart, and the recovery from that is a reinstall.
 */
class AdminGate(
    private val credential: BuildCredential?,
    private val appLog: AppLog? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val lockoutMillis: Long = LOCKOUT_MILLIS,
) {
    private val stateMutable = MutableStateFlow(
        AdminGateState(provisioned = credential?.present == true),
    )

    val state: StateFlow<AdminGateState> = stateMutable.asStateFlow()

    /**
     * One attempt. Returns whether it opened, so a caller can act without re-reading the flow.
     *
     * The username is compared case-insensitively and trimmed. A phone keyboard capitalises the
     * first letter of a field by default, and an operator being refused because their keyboard
     * helped them is a support call, not a security control — the password is what is secret here.
     */
    fun submit(username: String, password: String): Boolean {
        val now = clock()
        val current = stateMutable.value

        if (current.lockedAt(now)) {
            appLog?.warn(LogTag.SECURITY, "admin unlock refused while locked out")
            stateMutable.value = current.copy(refused = true)
            return false
        }
        val expected = credential
        if (expected == null || !expected.present) {
            appLog?.warn(LogTag.SECURITY, "admin unlock attempted on a build with no credential")
            stateMutable.value = current.copy(refused = true, provisioned = false)
            return false
        }

        val matches = username.trim().equals(expected.username, ignoreCase = true) &&
            AdminPasswordHash.verify(password, expected)

        if (matches) {
            appLog?.info(LogTag.SECURITY, "admin panel unlocked")
            stateMutable.value = AdminGateState(unlocked = true, provisioned = true)
            return true
        }

        val attempts = current.failedAttempts + 1
        val lockedUntil = if (attempts >= maxAttempts) now + lockoutMillis else null
        // The count, never the input. An entry naming what was typed would put a near-miss of the
        // real password into the file this panel exports.
        appLog?.warn(
            tag = LogTag.SECURITY,
            message = "admin unlock refused",
            fields = mapOf("attempt" to attempts.toString()),
        )
        stateMutable.value = current.copy(
            failedAttempts = if (lockedUntil != null) 0 else attempts,
            lockedUntilEpochMillis = lockedUntil,
            refused = true,
            provisioned = true,
        )
        return false
    }

    /** Clears the refusal as soon as the operator edits a field, so the message is about this try. */
    fun editing() {
        if (stateMutable.value.refused) {
            stateMutable.value = stateMutable.value.copy(refused = false)
        }
    }

    /**
     * Closes the panel again.
     *
     * Offered as a control on the panel itself and called when the screen is left. A panel that
     * stayed open for the life of the process would mean one unlock opens it for the rest of the
     * day, which is most of the way back to no gate at all.
     */
    fun lock() {
        if (stateMutable.value.unlocked) {
            appLog?.info(LogTag.SECURITY, "admin panel locked")
        }
        stateMutable.value = stateMutable.value.copy(unlocked = false, refused = false)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 5L * 60L * 1_000L
    }
}
