package com.coinepro.feature.news

import com.coinepro.feature.news.PreferencesSavedNewsStore.Companion.decode
import com.coinepro.feature.news.PreferencesSavedNewsStore.Companion.encode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved list's encoding, which is the one part of this feature that can lose a reader's data.
 *
 * Everything else here is layout: wrong is visible and is fixed in the next build. A codec that
 * drops the wrong record is invisible, is discovered a week later, and cannot be undone — so the
 * two properties that matter are pinned directly rather than through a fake DataStore.
 *
 * No test here pins a count of anything the product decides.
 */
class SavedNewsCodecTest {

    /** The two separators, spelled out here so a test failure says which boundary moved. */
    private val unit = "\u001F"
    private val group = "\u001D"

    private fun article(
        id: String,
        title: String = "تصمیم نرخ بهره‌ی فدرال رزرو",
        summary: String? = "کمیته‌ی بازار باز نرخ را بدون تغییر نگه داشت.",
        source: String? = "ForexLive",
        url: String? = "https://example.com/a",
        imageUrl: String? = null,
    ) = SavedArticle(
        id = id,
        title = title,
        summary = summary,
        source = source,
        url = url,
        imageUrl = imageUrl,
        publishedAt = Instant.ofEpochSecond(1_756_000_000),
        savedAt = Instant.ofEpochSecond(1_756_000_500),
    )

    @Test
    fun `a saved story survives a round trip whole`() {
        val original = article(id = "fx-1", imageUrl = "https://example.com/a.jpg")
        val restored = decode(encode(listOf(original)))
        assertEquals(listOf(original), restored)
    }

    @Test
    fun `a story with no link and no picture keeps both as absent rather than as empty text`() {
        val original = article(id = "fx-2", url = null, imageUrl = null)
        val restored = decode(encode(listOf(original))).single()
        assertNull(restored.url)
        assertNull(restored.imageUrl)
    }

    @Test
    fun `one unreadable record costs one save and not the rest`() {
        val good = article(id = "fx-3")
        val alsoGood = article(id = "fx-4", title = "بایننس لیست شدن توکن X را اعلام کرد")
        // A record from a build that wrote a different number of fields, wedged between two this
        // build can read. Tolerant decode means the neighbours survive.
        val stored = encode(listOf(good)) + "" + "brokenrecord" + "" + encode(listOf(alsoGood))
        val restored = decode(stored)
        assertEquals(listOf("fx-3", "fx-4"), restored.map(SavedArticle::id))
    }

    @Test
    fun `a record with an unparseable timestamp is dropped rather than dated to the epoch`() {
        val stored = listOf("fx-5", "عنوان", "منبع", "", "", "not-a-time:also-not")
            .joinToString("")
        assertTrue(decode(stored).isEmpty())
    }

    @Test
    fun `nothing stored and blank stored both read as an empty list`() {
        assertTrue(decode(null).isEmpty())
        assertTrue(decode("").isEmpty())
    }

    @Test
    fun `a separator smuggled into a headline cannot invent a field boundary`() {
        val hostile = article(id = "fx-6", title = "عنوانجعلیدوم")
        val clean = with(PreferencesSavedNewsStore.Companion) { hostile.sanitised() }
        assertNotNull(clean)
        val restored = decode(encode(listOf(clean!!)))
        assertEquals(1, restored.size)
        assertEquals("fx-6", restored.single().id)
        assertEquals("عنوانجعلیدوم", restored.single().title)
    }

    @Test
    fun `a record with no id cannot be written`() {
        val nameless = article(id = "   ")
        assertNull(with(PreferencesSavedNewsStore.Companion) { nameless.sanitised() })
    }

    @Test
    fun `a save written before summaries were kept still reads back`() {
        // Six fields, in the order the previous build wrote them. Without the older shape being
        // recognised this record would fail the field-count check and be dropped, which on an
        // update would empty every reader's saved list at once — the one failure in this file that
        // cannot be undone.
        val stored = listOf(
            "fx-old",
            "تصمیم نرخ بهره‌ی فدرال رزرو",
            "ForexLive",
            "https://example.com/a",
            "",
            "1756000000:1756000500",
        ).joinToString(unit)
        val restored = decode(stored).single()
        assertEquals("fx-old", restored.id)
        assertEquals("ForexLive", restored.source)
        // Absent rather than empty text: the reading page already has a state for a story whose
        // server sent no summary, and this is honestly that state.
        assertNull(restored.summary)
    }

    @Test
    fun `a story saved with no publisher keeps the rest of itself`() {
        // The public feed's shape. The source field is written as empty and read back as absent,
        // which is what the byline draws around rather than printing a blank name.
        val original = article(id = "guest-1", source = null, url = null)
        val restored = decode(encode(listOf(original))).single()
        assertNull(restored.source)
        assertEquals(original.summary, restored.summary)
    }

    @Test
    fun `a summary carrying a separator cannot invent a field boundary`() {
        val hostile = article(id = "fx-7", summary = "خلاصه" + unit + "جعلی" + group + "دوم")
        val clean = with(PreferencesSavedNewsStore.Companion) { hostile.sanitised() }
        assertNotNull(clean)
        val restored = decode(encode(listOf(clean!!)))
        assertEquals(1, restored.size)
        assertEquals("خلاصهجعلیدوم", restored.single().summary)
    }
}
