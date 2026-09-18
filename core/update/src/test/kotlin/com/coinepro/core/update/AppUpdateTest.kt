package com.coinepro.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison, and the four ways a published release can be one this app declines to offer.
 *
 * Worth a test rather than three lines in a composable because the failure is invisible from a
 * screenshot in both directions: an offer that never appears looks exactly like «you are up to
 * date», and an offer that appears when it should not looks exactly like a legitimate one. Neither
 * shows up in a render, and one of them ends with a package on somebody's phone.
 */
class AppUpdateTest {

    private fun release(
        versionCode: Long = 50_000_000L,
        versionName: String = "5.0.0",
        url: String = "https://pro-chart.com/download/pro-chart-5.0.0.apk",
        sha256: String = "a".repeat(64),
        mandatory: Boolean = false,
    ) = AppRelease(
        versionCode = versionCode,
        versionName = versionName,
        url = url,
        sha256 = sha256,
        notesFa = "چند اصلاح کوچک.",
        notesEn = "A few small fixes.",
        mandatory = mandatory,
    )

    @Test
    fun `a higher code is an update`() {
        val newer = release(versionCode = 50_000_001L)
        assertEquals(AppUpdateStatus.Available(newer), AppUpdate.decide(50_000_000L, newer))
    }

    @Test
    fun `the same code is not`() {
        assertEquals(AppUpdateStatus.Current, AppUpdate.decide(50_000_000L, release()))
    }

    @Test
    fun `a lower code is not, and is not an error either`() {
        // Somebody running a build newer than the published one. Telling them to «update» to an
        // older APK would be advice the package manager itself refuses to take.
        assertEquals(AppUpdateStatus.Current, AppUpdate.decide(50_000_010L, release()))
    }

    @Test
    fun `nothing fetched is nothing shown`() {
        assertEquals(AppUpdateStatus.Unknown, AppUpdate.decide(50_000_000L, null))
    }

    @Test
    fun `only the code decides, never the name`() {
        // A name that reads newer over a code that is not. The package manager compares the code
        // and so does this; a name is for the reader.
        val misnamed = release(versionCode = 49_900_000L, versionName = "9.9.9")
        assertEquals(AppUpdateStatus.Current, AppUpdate.decide(50_000_000L, misnamed))
    }

    @Test
    fun `an update over plain http is not offered`() {
        val insecure = release(
            versionCode = 50_000_001L,
            url = "http://pro-chart.com/download/pro-chart-5.0.1.apk",
        )
        assertFalse(AppUpdate.publishable(insecure))
        assertEquals(AppUpdateStatus.Unknown, AppUpdate.decide(50_000_000L, insecure))
    }

    @Test
    fun `an update from a host this product does not publish from is not offered`() {
        val elsewhere = release(versionCode = 50_000_001L, url = "https://evil.example/pro-chart.apk")
        assertEquals(AppUpdateStatus.Unknown, AppUpdate.decide(50_000_000L, elsewhere))
    }

    @Test
    fun `the brand name in the userinfo does not make it the brand host`() {
        // `https://pro-chart.com@evil.example/x` is served by evil.example, and a reader glancing
        // at the address sees the product's own name at the front of it. This is the case the
        // hand-written parser exists for.
        val spoofed = release(
            versionCode = 50_000_001L,
            url = "https://pro-chart.com@evil.example/pro-chart.apk",
        )
        assertEquals(AppUpdateStatus.Unknown, AppUpdate.decide(50_000_000L, spoofed))
    }

    @Test
    fun `case and port do not change which machine is named`() {
        val shouting = release(
            versionCode = 50_000_001L,
            url = "https://PRO-CHART.COM:443/download/pro-chart-5.0.1.apk",
        )
        assertTrue(AppUpdate.publishable(shouting))
    }

    @Test
    fun `an APK offered without a checkable digest is not offered`() {
        assertFalse(AppUpdate.publishable(release(versionCode = 50_000_001L, sha256 = "")))
        assertFalse(AppUpdate.publishable(release(versionCode = 50_000_001L, sha256 = "abc")))
        assertFalse(AppUpdate.publishable(release(versionCode = 50_000_001L, sha256 = "z".repeat(64))))
        assertTrue(AppUpdate.publishable(release(versionCode = 50_000_001L, sha256 = "A".repeat(64))))
    }

    @Test
    fun `a release the host has not finished writing is not offered`() {
        assertFalse(AppUpdate.publishable(release(versionCode = 0L)))
        assertFalse(AppUpdate.publishable(release(versionName = "  ")))
    }

    @Test
    fun `mandatory changes the sentence and not the decision`() {
        // The whole of what `mandatory` may do. It is a word on a card; there is no state in which
        // this app stops working because a host said so — see `AppUpdate`'s own note on why not.
        val urgent = release(versionCode = 50_000_001L, mandatory = true)
        assertEquals(AppUpdateStatus.Available(urgent), AppUpdate.decide(50_000_000L, urgent))
        assertEquals(AppUpdateStatus.Current, AppUpdate.decide(50_000_001L, urgent))
    }

    @Test
    fun `GitHub releases are a publishing host, because that is where builds are published today`() {
        val github = release(
            versionCode = 50_000_001L,
            url = "https://github.com/BehnamJalaliCo/CoinePro-App/releases/download/v5.0.1/pro-chart-5.0.1.apk",
        )
        assertTrue(AppUpdate.publishable(github))
    }
}
