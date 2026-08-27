package com.coinepro.app

import com.coinepro.app.security.AppIntegrity
import com.coinepro.app.security.IntegrityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison that decides whether this app starts.
 *
 * The failure that matters here is not "it let a bad build through" — it is "it refused a good
 * one", because this check runs before anything is drawn and there is no way past it. Four of these
 * five tests are about that.
 */
class AppIntegrityTest {

    private val ours = "96:12:AB:6C:BF:BB:4F:4F:FB:F1:51:D8:60:2C:12:9D:" +
        "CC:E8:A9:77:1E:85:46:69:E6:63:87:0F:04:04:FB:D0"
    private val theirs = List(32) { "AA" }.joinToString(":")

    /**
     * No expected fingerprint means no check, not a failed one.
     *
     * Every debug build is in this state. Reporting it as a failure would refuse to start the app
     * on the machine it is being written on.
     */
    @Test
    fun `nothing compiled in means nothing is checked`() {
        assertEquals(IntegrityState.NotChecked, AppIntegrity.verdict("", listOf(ours)))
        assertEquals(IntegrityState.NotChecked, AppIntegrity.verdict("  ,  , ", listOf(ours)))
    }

    /**
     * A certificate that could not be read is also not a failure.
     *
     * Fail open. This check stops a repackaged copy; it is not what guards an account, and a phone
     * that answered strangely must not be locked out of a trading app it paid for.
     */
    @Test
    fun `an unreadable certificate is not treated as tampering`() {
        assertEquals(IntegrityState.NotChecked, AppIntegrity.verdict(ours, emptyList()))
    }

    @Test
    fun `the expected certificate runs`() {
        assertEquals(IntegrityState.Genuine, AppIntegrity.verdict(ours, listOf(ours)))
    }

    /**
     * Punctuation and case are not part of the comparison, and a list is allowed.
     *
     * The console shows colons, the keystore reader does not, and Play App Signing makes a *second*
     * key legitimate. Refusing to start over any of those would be the worst bug in this file.
     */
    @Test
    fun `colons, case and a second legitimate key all work`() {
        assertEquals(IntegrityState.Genuine, AppIntegrity.verdict(ours.lowercase(), listOf(ours)))
        assertEquals(
            IntegrityState.Genuine,
            AppIntegrity.verdict(ours.replace(":", ""), listOf(ours)),
        )
        assertEquals(
            IntegrityState.Genuine,
            AppIntegrity.verdict("$theirs,$ours", listOf(ours)),
        )
    }

    /** A different key is a different app, whatever it calls itself. */
    @Test
    fun `a certificate nobody expected is reported as repackaged`() {
        val result = AppIntegrity.verdict(ours, listOf(theirs))
        assertTrue(result is IntegrityState.Repackaged)
        assertEquals(
            "The refusal screen names what it actually found, colons and all",
            theirs,
            (result as IntegrityState.Repackaged).actual,
        )
    }
}
