package com.coinepro.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate on the Google button.
 *
 * Worth a test of its own because both halves of it have shipped wrong: a server that reports the
 * method on without sending an audience, and a client that drew the button anyway and reported a
 * failure the reader could do nothing about. See [AuthMethods.googleUsable].
 */
class AuthMethodsGoogleTest {

    private val real = "1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com"

    @Test
    fun `a live web client id is usable`() {
        assertTrue(AuthMethods(google = true, googleClientId = real).googleUsable)
    }

    @Test
    fun `the method reported on with no audience is not usable`() {
        assertFalse(AuthMethods(google = true).googleUsable)
        assertFalse(AuthMethods(google = true, googleClientId = "   ").googleUsable)
    }

    @Test
    fun `something that is not an OAuth client id is not usable`() {
        assertFalse(AuthMethods(google = true, googleClientId = "changeme").googleUsable)
        assertFalse(AuthMethods(google = true, googleClientId = "1033486124390").googleUsable)
    }

    @Test
    fun `an audience without the method is still not usable`() {
        assertFalse(AuthMethods(google = false, googleClientId = real).googleUsable)
    }

    @Test
    fun `a server offering only an unusable Google is a server offering nothing`() {
        // `any` is what draws «هیچ روش ورودی در دسترس نیست» instead of an empty form with a button
        // on it that cannot work.
        assertFalse(AuthMethods(google = true, googleClientId = null).any)
        assertTrue(AuthMethods(google = true, googleClientId = real).any)
    }
}
