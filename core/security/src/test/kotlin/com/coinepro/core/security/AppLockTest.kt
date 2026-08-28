package com.coinepro.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app lock's policy, which is the half that can be wrong without anybody noticing.
 *
 * A lock that challenges too often gets switched off, and a lock that is switched off protects
 * nothing — so "does not challenge" is as much a requirement here as "does". Both directions are
 * tested.
 */
class AppLockTest {

    private fun challenge(
        enabled: Boolean = true,
        capable: Boolean = true,
        unlockedAt: Long? = 1_000L,
        now: Long = 1_000L,
        backgroundedAt: Long? = null,
    ) = AppLock.shouldChallenge(enabled, capable, unlockedAt, now, backgroundedAt)

    @Test
    fun `off is off`() {
        assertFalse(challenge(enabled = false, unlockedAt = null))
    }

    @Test
    fun `a cold start with the lock on challenges`() {
        assertTrue(challenge(unlockedAt = null))
    }

    @Test
    fun `a short trip out of the app is free`() {
        // Copying a code out of an authenticator, answering a message, taking a screenshot. Being
        // challenged for these is what makes people turn the feature off.
        val backgrounded = 2_000L
        assertFalse(challenge(unlockedAt = 1_000L, backgroundedAt = backgrounded, now = backgrounded + 5_000))
        assertFalse(
            challenge(
                unlockedAt = 1_000L,
                backgroundedAt = backgrounded,
                now = backgrounded + AppLock.GRACE_MILLIS - 1,
            ),
        )
    }

    @Test
    fun `a phone put down is locked when it is picked up`() {
        val backgrounded = 2_000L
        assertTrue(
            challenge(
                unlockedAt = 1_000L,
                backgroundedAt = backgrounded,
                now = backgrounded + AppLock.GRACE_MILLIS,
            ),
        )
        assertTrue(challenge(unlockedAt = 1_000L, backgroundedAt = backgrounded, now = backgrounded + 600_000))
    }

    @Test
    fun `staying in the app never challenges, however long`() {
        // Somebody reading a chart for an hour is not somebody to interrupt.
        assertFalse(challenge(unlockedAt = 1_000L, backgroundedAt = null, now = 1_000L + 3_600_000))
    }

    @Test
    fun `a background before the unlock does not count`() {
        // The order this rules out: app backgrounded, then unlocked (the reader answered the
        // prompt), then the state is read. Comparing against a stale background timestamp would
        // challenge somebody who had just that second unlocked.
        assertFalse(challenge(unlockedAt = 5_000L, backgroundedAt = 2_000L, now = 5_000L + 600_000))
    }

    @Test
    fun `a phone that can no longer authenticate is not locked out`() {
        // The reader removed their fingerprints, or a policy did. The setting stays on and the
        // challenge is skipped — the alternative is an app nobody can open again.
        assertFalse(challenge(capable = false, unlockedAt = null))
    }

    @Test
    fun `a clock that appears to run backwards challenges`() {
        // `elapsedRealtime` is monotonic, so this means two different clocks got mixed. The safe
        // answer to "I do not know how long it has been" is to ask.
        assertTrue(challenge(unlockedAt = 10_000L, now = 5_000L))
    }

    @Test
    fun `capability decides what the settings screen offers`() {
        assertTrue(LockCapability.READY.usable)
        assertTrue(LockCapability.CREDENTIAL_ONLY.usable)
        assertFalse(LockCapability.NOT_ENROLLED.usable)
        assertFalse(LockCapability.NONE.usable)

        // Not enrolled is still offered — with a shortcut into enrolment. Only a phone with no
        // screen lock at all hides the switch, because there the app cannot add one.
        assertTrue(LockCapability.NOT_ENROLLED.offerable)
        assertFalse(LockCapability.NONE.offerable)
    }
}
