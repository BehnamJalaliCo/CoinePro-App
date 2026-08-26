package com.coinepro.core.help

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped «؟» catalogue, checked as data rather than trusted.
 *
 * It is 177 entries exported from another product. Nothing in the Kotlin compiler can tell whether
 * a field went missing in the export, an image file was left behind, or a language is blank — and
 * all three would show up as an empty panel next to a tool somebody was trying to learn.
 */
class HelpCatalogTest {

    private val catalog = HelpCatalog.parse(assetFile("content.json").readText())

    @Test
    fun `every entry the export promised is here`() {
        assertEquals(177, catalog.size)
    }

    @Test
    fun `no entry is missing its title, and no title is blank in either language`() {
        for (id in catalog.ids) {
            val entry = catalog[id]!!
            assertTrue("$id has an empty Persian title", entry.title.fa.isNotBlank())
            assertTrue("$id has an empty English title", entry.title.en.isNotBlank())
        }
    }

    @Test
    fun `every entry says something beyond its own name`() {
        // An entry with a title and nothing else opens as a sheet with a heading and white space,
        // which is worse than no «؟» at all — it implies there is help and then withholds it.
        for (id in catalog.ids) {
            val entry = catalog[id]!!
            val hasBody = entry.what != null || !entry.how.isEmpty || entry.useCase != null
            assertTrue("$id has a title and no content", hasBody)
        }
    }

    @Test
    fun `every referenced image file is actually packaged`() {
        // The export and the images are two directories that can drift apart. A missing file draws
        // nothing at runtime — silently — so it is caught here instead.
        val images = assetFile("images")
        var referenced = 0
        for (id in catalog.ids) {
            for (image in catalog[id]!!.images) {
                referenced++
                assertTrue(
                    "$id references ${image.file}, which is not in the assets",
                    File(images, image.file).exists(),
                )
            }
        }
        assertTrue("no images were referenced at all", referenced > 200)
    }

    @Test
    fun `the illustrated entries are the ones the export said`() {
        val illustrated = catalog.ids.count { catalog[it]!!.hasImages }
        assertEquals(83, illustrated)
    }

    @Test
    fun `an unknown id is null rather than an empty entry`() {
        // The «؟» is hidden when this is null. An empty entry would open a blank sheet instead.
        assertNull(catalog["no-such-tool"])
        assertNotNull(catalog["rsi"])
    }

    @Test
    fun `a well known entry reads correctly end to end`() {
        val rsi = catalog["rsi"]!!
        assertTrue(rsi.title.fa.contains("قدرت"))
        assertTrue(rsi.title.en.contains("Relative Strength"))
        assertTrue(rsi.how.fa.isNotEmpty())
        assertEquals(rsi.how.fa.size, rsi.how.en.size)
        assertTrue(rsi.hasImages)
    }

    @Test
    fun `steps and tips have the same count in both languages`() {
        // A translation that dropped a step would leave the Persian and English readers following
        // different instructions for the same tool.
        for (id in catalog.ids) {
            val entry = catalog[id]!!
            if (!entry.how.isEmpty) {
                assertEquals("$id: how steps differ between languages", entry.how.fa.size, entry.how.en.size)
            }
            if (!entry.tips.isEmpty) {
                assertEquals("$id: tips differ between languages", entry.tips.fa.size, entry.tips.en.size)
            }
        }
    }

    @Test
    fun `malformed json yields an empty catalogue rather than a crash`() {
        assertEquals(0, HelpCatalog.parse("{}").size)
    }

    private fun assetFile(name: String): File {
        val file = File("src/main/assets/help/$name")
        assertTrue(
            "The help assets are not where this test expects them: ${file.absolutePath}. " +
                "They live in core/help/src/main/assets/help.",
            file.exists(),
        )
        return file
    }
}
