package com.coinepro.core.auth

import com.coinepro.core.model.MarketPlatform
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two ways the crypto side of authentication was silently broken, pinned so neither returns.
 *
 * Both failed the same way: not with an exception, but with a plausible-looking wrong answer. A
 * path built for the wrong backend 404s in wording that reads like an outage, and a profile parsed
 * under the wrong naming convention arrives blank rather than absent — indistinguishable from a
 * brand-new free account.
 */
class AuthWireTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `each platform's auth routes carry that platform's own prefix`() {
        val forex = AuthPaths.of(MarketPlatform.COINEPRO_FX)
        val crypto = AuthPaths.of(MarketPlatform.TRADEYAR)

        assertEquals("user/auth/login", forex.login)
        assertEquals("api/mobile/v1/auth/login", crypto.login)
        assertEquals("user/auth/register/verify", forex.registerVerify)
        assertEquals("api/mobile/v1/auth/register/verify", crypto.registerVerify)
    }

    @Test
    fun `no auth route is shared between the two backends`() {
        val forex = AuthPaths.of(MarketPlatform.COINEPRO_FX).all()
        val crypto = AuthPaths.of(MarketPlatform.TRADEYAR).all()

        assertEquals("Both backends must expose the same eight routes", forex.size, crypto.size)
        assertTrue(
            "A path serving both platforms means one of them is being asked the wrong question",
            forex.intersect(crypto).isEmpty(),
        )
    }

    @Test
    fun `the profile read follows the platform, and telegram exists only where it is served`() {
        val forex = SessionPaths.of(MarketPlatform.COINEPRO_FX)
        val crypto = SessionPaths.of(MarketPlatform.TRADEYAR)

        assertEquals("user/me", forex.me)
        assertEquals("api/mobile/v1/me", crypto.me)
        assertNotNull(forex.telegram)
        assertNull("TradeYar has no Telegram sign-in, so there is no route to call", crypto.telegram)
    }

    @Test
    fun `a snake_case profile parses, which is how CoinePro-FX sends it`() {
        val profile = gson.fromJson(
            """
            {
              "telegram_id": 77,
              "name": "بهنام",
              "email": "a@b.co",
              "email_verified": true,
              "is_paid": true,
              "is_vip": true,
              "panel_allowed": true,
              "plan": "monthly",
              "plan_expires_at": "2026-09-24T12:00:00Z"
            }
            """.trimIndent(),
            AuthUserDto::class.java,
        ).toDomain()

        assertEquals(77L, profile.telegramId)
        assertEquals("بهنام", profile.name)
        assertTrue(profile.isPaid)
        assertEquals("monthly", profile.plan)
        assertEquals("2026-09-24T12:00:00Z", profile.planExpiresAt)
    }

    @Test
    fun `a camelCase profile parses too, which is how TradeYar sends it`() {
        val profile = gson.fromJson(
            """
            {
              "telegramId": 77,
              "fullName": "بهنام",
              "email": "a@b.co",
              "emailVerified": true,
              "isPaid": true,
              "isVip": true,
              "panelAllowed": true,
              "plan": "ماهانه",
              "planExpiresAt": "2026-09-24T12:00:00Z"
            }
            """.trimIndent(),
            AuthUserDto::class.java,
        ).toDomain()

        assertEquals(
            "A camelCase profile read under one naming policy arrives blank, which looks exactly " +
                "like a free account rather than like a parsing failure",
            "بهنام",
            profile.name,
        )
        assertEquals(77L, profile.telegramId)
        assertTrue(profile.isPaid)
        assertTrue(profile.isVip)
        assertTrue(profile.emailVerified)
        assertEquals("ماهانه", profile.plan)
    }

    @Test
    fun `an empty profile keeps the defaults rather than inventing a name`() {
        val profile = gson.fromJson("{}", AuthUserDto::class.java).toDomain()

        assertEquals("", profile.name)
        assertEquals("none", profile.kycStatus)
        assertEquals("free", profile.plan)
        assertNull(profile.planExpiresAt)
        assertTrue(!profile.isPaid && !profile.isVip)
    }

    private fun AuthPaths.all() = setOf(
        methods,
        registerStart,
        registerVerify,
        login,
        google,
        forgotPassword,
        resetPassword,
        refresh,
        logout,
    )
}
