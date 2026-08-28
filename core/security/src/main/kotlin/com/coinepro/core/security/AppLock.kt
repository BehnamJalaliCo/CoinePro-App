package com.coinepro.core.security

/**
 * Whether the app should be behind a fingerprint or a face right now.
 *
 * ### What this locks, and what it deliberately does not
 *
 * It locks the **app**, not the account. The session tokens stay where they were — in the
 * hardware keystore, see [KeystoreSessionTokenStorage] — and unlocking does not sign anybody in or
 * out. This is a curtain over a phone somebody else is holding, and it answers exactly one threat:
 * an unlocked phone in the wrong hands, briefly. It is not a defence against somebody who has the
 * device and time, and this file is not going to pretend otherwise.
 *
 * That is worth stating plainly because the failure mode of an over-promised app lock is a reader
 * who believes their balance is safe on a phone they lent to somebody.
 *
 * ### Why not simply gate the numbers
 *
 * The balance already has its own control — the eye that swaps a figure for six dots, see
 * `CoineProPrivacy`. This is the other half: that one hides a number from a shoulder, this one
 * hides the whole app from a hand. They are different threats and both are cheap.
 *
 * ### The grace period is the whole design
 *
 * A lock that re-arms the instant the app leaves the foreground is a lock nobody keeps on. Copying
 * a code out of an authenticator, answering a message, taking a photograph of a chart to send to
 * somebody — every one of those is a trip out of the app and back, and being challenged each time
 * is what makes people turn the feature off. [GRACE_MILLIS] is the window in which coming back is
 * free.
 *
 * Thirty seconds. Long enough for the errands above, short enough that a phone put down on a table
 * is locked before anybody picks it up.
 */
object AppLock {

    /**
     * How long the app may be away before it re-arms.
     *
     * See the class note. Not configurable: a reader asked to choose a number here has to model a
     * threat to answer, and the ones who would answer it well are the ones who would also be
     * fine with thirty seconds.
     */
    const val GRACE_MILLIS: Long = 30_000

    /**
     * Whether the app should be showing the lock screen.
     *
     * Pure, so the whole policy is a unit test rather than a device. Every argument is a fact the
     * caller can observe.
     *
     * @param enabled whether the reader turned the lock on.
     * @param capable whether this phone can actually authenticate — see [LockCapability].
     * @param unlockedAtElapsedMillis when the last successful unlock happened on the elapsed-real-time
     *   clock, or null if there has not been one this process.
     * @param nowElapsedMillis the same clock, now.
     * @param backgroundedAtElapsedMillis when the app last went to the background, or null if it
     *   has not since the unlock.
     */
    fun shouldChallenge(
        enabled: Boolean,
        capable: Boolean,
        unlockedAtElapsedMillis: Long?,
        nowElapsedMillis: Long,
        backgroundedAtElapsedMillis: Long?,
    ): Boolean {
        if (!enabled) return false
        // A phone that has lost its fingerprints — the reader removed them, or a work profile
        // policy did — must not be locked out of an app it can no longer unlock. The setting stays
        // on and the challenge is skipped; `LockCapability` is what the settings screen shows them
        // so they can see why.
        if (!capable) return false
        val unlockedAt = unlockedAtElapsedMillis ?: return true
        // The clock cannot run backwards — `elapsedRealtime` is monotonic and survives sleep — so
        // a negative interval means the caller passed times from two different clocks. Challenge:
        // the safe answer to "I do not know how long it has been".
        if (nowElapsedMillis < unlockedAt) return true
        val backgroundedAt = backgroundedAtElapsedMillis ?: return false
        if (backgroundedAt < unlockedAt) return false
        return nowElapsedMillis - backgroundedAt >= GRACE_MILLIS
    }
}

/**
 * What this phone can do about proving who is holding it.
 *
 * Four states rather than a boolean, because the settings screen has something different and true
 * to say in each one, and "biometric unavailable" over all three of them is the answer that makes
 * a reader think the app is broken.
 */
enum class LockCapability {
    /** A fingerprint or a face is enrolled and usable. */
    READY,

    /**
     * The hardware is there and nothing is enrolled.
     *
     * The one case with an action attached: the screen offers a shortcut into the system's own
     * enrolment, rather than telling the reader to go and find it.
     */
    NOT_ENROLLED,

    /**
     * No biometric hardware, but the phone has a PIN, pattern or password.
     *
     * Still offered, and still worth having. The prompt falls back to the device credential, which
     * is the same curtain drawn with a different hand.
     */
    CREDENTIAL_ONLY,

    /**
     * Nothing at all — no biometric, no screen lock.
     *
     * The switch is hidden rather than shown disabled. A control that cannot be turned on is a
     * question the reader cannot answer, and on a phone with no lock screen at all the app cannot
     * add one.
     */
    NONE,
    ;

    /** Whether the lock can actually challenge somebody. */
    val usable: Boolean get() = this == READY || this == CREDENTIAL_ONLY

    /** Whether the settings screen should offer the switch at all. */
    val offerable: Boolean get() = this != NONE
}
