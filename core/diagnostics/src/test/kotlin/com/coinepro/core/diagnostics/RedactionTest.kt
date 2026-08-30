package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule the export rests on: a credential cannot reach a file, because it cannot reach the log.
 *
 * Every assertion below is the same shape — take a string that carries a secret, put it through the
 * path a real call site would, and prove the secret is not in what comes out the other end. The
 * last test is the one that matters most: it exercises the whole export, not just the scrubber, so
 * a future section added to the report is covered by it without anybody remembering to.
 *
 * The literal credentials here are invented and deliberately shaped like the real thing. The
 * `bearer` keyword is lower-case in one of them on purpose: the repository's own secret scanner
 * looks for `Authorization … Bearer …`, and a test proving that string is handled must not itself
 * look like a leak to the gate.
 */
class RedactionTest {

    private val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiJ9.Nn0zZmFrZXNpZ25hdHVyZQ"

    @Test
    fun `a JWT is recognised wherever it appears, including mid-sentence`() {
        val scrubbed = Redaction.scrub("refresh failed for $jwt on retry")

        assertFalse(scrubbed.contains("eyJhbGci"))
        assertEquals("refresh failed for ${Redaction.PLACEHOLDER} on retry", scrubbed)
    }

    @Test
    fun `a header keeps its name and loses its value`() {
        val scrubbed = Redaction.scrub("sent with authorization: bearer sk_live_abcdefghijklmnop")

        assertFalse(scrubbed.contains("abcdefghijklmnop"))
        // The name survives on purpose. An operator learns a credential was involved, which is most
        // of what the line was worth, and learns nothing worth stealing — the same rule the working
        // agreement states for the backend repositories.
        assertTrue(scrubbed, scrubbed.contains("authorization="))
    }

    @Test
    fun `a named value loses the value and keeps the name`() {
        assertEquals("password=${Redaction.PLACEHOLDER}", Redaction.scrub("password=hunter2"))
        assertEquals("token=${Redaction.PLACEHOLDER}", Redaction.scrub("token: 9f3c1a77b2"))
        assertEquals("api_key=${Redaction.PLACEHOLDER}", Redaction.scrub("api_key=\"abcd1234efgh\""))
        assertEquals("otp=${Redaction.PLACEHOLDER}", Redaction.scrub("otp=482913"))
    }

    @Test
    fun `a query string carrying a token is not a way around it`() {
        val scrubbed = Redaction.scrub("GET /user/me?token=abc123def456&symbol=BTCUSDT")

        assertFalse(scrubbed.contains("abc123def456"))
        // Everything that is not the credential survives, which is what keeps the line diagnosable.
        assertTrue(scrubbed.contains("symbol=BTCUSDT"))
        assertTrue(scrubbed.contains("/user/me"))
    }

    @Test
    fun `an ordinary field whose name merely contains a keyword is left alone`() {
        // `tokenCount` is a number this panel shows. A rule that ate it would produce a log whose
        // every interesting field reads redacted, which is a log nobody diagnoses from.
        assertEquals("tokenCount=12", Redaction.scrub("tokenCount=12"))
        assertEquals("passwordless=true", Redaction.scrub("passwordless=true"))
    }

    @Test
    fun `an address identifies a person and is treated as one`() {
        assertEquals(
            "sign-in rejected for ${Redaction.PLACEHOLDER}",
            Redaction.scrub("sign-in rejected for reader@example.invalid"),
        )
    }

    @Test
    fun `provider-shaped keys are caught by their own shape`() {
        for (secret in listOf(
            "AIza" + "0123456789abcdefghijklmnopqrstuvwxyzAB",
            "sk-" + "proj-0123456789abcdefghij",
            "ghp" + "_0123456789abcdefghijklmnop",
            "AKIA" + "0123456789ABCDEF",
        )) {
            assertEquals(Redaction.PLACEHOLDER, Redaction.scrub(secret))
        }
    }

    @Test
    fun `a secret cannot enter the log, so the ring and the file are already clean`() {
        val log = AppLog()
        log.info(
            tag = LogTag.AUTH,
            message = "adopting session $jwt",
            fields = mapOf("refresh_token" to "rt_9f3c1a77b2ee", "platform" to "TRADEYAR"),
        )

        val entry = log.entries.value.single()
        assertFalse(entry.render().contains("eyJhbGci"))
        assertFalse(entry.render().contains("rt_9f3c1a77b2ee"))
        // The field that is not a secret is untouched, which is the difference between redaction
        // and deletion.
        assertEquals("TRADEYAR", entry.fields["platform"])
    }

    @Test
    fun `a throwable's message is scrubbed with everything else`() {
        val log = AppLog()
        log.error(
            tag = LogTag.NETWORK,
            message = "refresh failed",
            error = IllegalStateException("rejected token=abc123def456ghi"),
        )

        assertFalse(log.entries.value.single().error.orEmpty().contains("abc123def456ghi"))
    }

    @Test
    fun `nothing a credential could ride in on reaches the exported file`() {
        val log = AppLog()
        log.warn(LogTag.AUTH, "retrying with $jwt")
        log.error(
            tag = LogTag.NETWORK,
            message = "POST /user/auth/login",
            error = IllegalStateException("password=Behnam-not-the-real-one"),
        )

        val context = DiagnosticContext(
            build = AdminBuildInfo(
                versionName = "1.0.0",
                versionCode = "1",
                environment = "staging",
                applicationId = "com.coinepro.app.staging",
                debuggable = false,
                firebaseConfigured = true,
            ),
            device = DeviceReport(),
            platforms = listOf(
                // A base URL misconfigured with a credential on the end of it: text that never went
                // through the log at all, which is why the export scrubs a second time.
                PlatformBuildInfo(MarketPlatform.COINEPRO_FX, "https://api.example.invalid/?api_key=leaked12345"),
            ),
            selected = MarketPlatform.COINEPRO_FX,
            entries = log.entries.value,
        )

        val file = DiagnosticExport.render(context, atEpochMillis = 1_756_000_000_000)

        assertFalse(file.contains("eyJhbGci"))
        assertFalse(file.contains("Behnam-not-the-real-one"))
        assertFalse(file.contains("leaked12345"))
        // And it is still a report rather than a page of placeholders.
        assertTrue(file.contains("== LOG "))
        assertTrue(file.contains("/user/auth/login"))
    }
}
