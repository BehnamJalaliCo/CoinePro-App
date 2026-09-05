package com.coinepro.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nothing that holds a credential may reach a log.
 *
 * The rule the design brief asks for as a unit test: no `Log.*` and no `println` may be handed a
 * value whose name says it is a key, a secret, a password or a token. The app has no wrapper
 * types for those — they travel as strings — so the check is on the identifier at the call site,
 * with string literals blanked first so a log *message* that mentions a token («token refresh
 * failed») is not a violation while `Log.d(TAG, token)` is.
 */
class SecretLoggingRulesTest {

    @Test
    fun `no log call receives a credential-named value`() {
        val offenders = mutableListOf<String>()
        for (root in ROOTS) {
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && "/build/" !in it.path && "/test/" !in it.path }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (!LOG_CALL.containsMatchIn(line)) return@forEachIndexed
                        val code = STRING_LITERAL.replace(line, "\"\"")
                        if (CREDENTIAL.containsMatchIn(code)) {
                            offenders += file.relativeTo(REPO).path + ":" + (index + 1) + "  " + line.trim()
                        }
                    }
                }
        }
        assertEquals("credentials reaching a log:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    private companion object {
        val REPO = File("..").canonicalFile
        val ROOTS = listOf("app", "core", "feature").map { REPO.resolve(it) }
        val LOG_CALL = Regex("""\b(Log\.[dievw]|Timber\.[dievw]|println)\(""")
        val STRING_LITERAL = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
        val CREDENTIAL = Regex(
            """\b(apiKey|apiSecret|secret|password|passphrase|privateKey|sessionToken|accessToken|refreshToken|bearerToken)\b""",
        )
    }
}
