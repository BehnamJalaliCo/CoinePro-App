package com.coinepro.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.coinepro.core.script.NamaScript
import com.coinepro.feature.script.SNIPPETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The studio's snippet chips compile — **in both languages** (run I item 4).
 *
 * A snippet is not a label: tapping one types a working script into the reader's editor, and since
 * 4.73.0 both halves of it are resources so that an English reader is not handed a plot named
 * «تند». That makes the text translatable and it also makes it breakable in a way a Kotlin literal
 * was not — a `\"` lost in the XML escaping is a script that does not parse, and nothing else in
 * the build would notice. This is what notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScriptSnippetsTest {

    @Test
    fun `every snippet compiles in Persian`() = assertSnippetsCompile("fa")

    @Test
    fun `every snippet compiles in English`() = assertSnippetsCompile("en")

    private fun assertSnippetsCompile(language: String) {
        val context = localised(language)
        assertTrue("there are snippets to check", SNIPPETS.size >= 4)
        for (snippet in SNIPPETS) {
            val title = context.getString(snippet.titleRes)
            val source = context.getString(snippet.sourceRes)
            val failure = NamaScript.check(source)
            assertEquals("[$language] $title: ${failure?.messageEn}", null, failure)
        }
    }

    private fun localised(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(java.util.Locale(language))
        return base.createConfigurationContext(configuration)
    }
}
