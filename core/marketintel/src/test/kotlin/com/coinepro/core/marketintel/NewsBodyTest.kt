package com.coinepro.core.marketintel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole of a story's text — where it comes from, and everything it refuses on the way.
 *
 * The subject is one sentence the owner has now written twice: «متن کامل خبر قرار بود ترجمه بشه».
 * Every earlier round answered it by asserting that no body existed, and the field's own KDoc said
 * so. It did exist: `news_posts.body_fa`, filled by the newsroom, served in full at
 * `api/v1/news/{slug}`. So the tests here are about the two halves of actually using it — reading
 * the envelope the route really sends, and refusing the three shapes that are a `body` field
 * without being a body.
 */
class NewsBodyTest {

    // ------------------------------------------------------------------ reading the envelope

    @Test
    fun `the body is read from the data envelope this route actually sends`() {
        // Captured shape, trimmed: TradeYar wraps a single article in `data` and names the field
        // `bodyFa`. The whole reason this module reads names rather than a typed DTO is that the
        // two backends disagree about both.
        val body = readArticleBody(
            """{"data":{"slug":"x","summaryFa":"خلاصه","bodyFa":"پاراگراف یک\n\nپاراگراف دو"},"meta":null}""",
        )
        assertEquals("پاراگراف یک\n\nپاراگراف دو", body)
    }

    @Test
    fun `a bare object is read too, because the other backend answers one`() {
        // CoinePro-FX's `user/mobile/news/{id}` has no envelope and calls the field `body`. A
        // reader that insisted on `data.bodyFa` would find nothing there and show a summary on a
        // page whose server had sent the article.
        assertEquals("متن", readArticleBody("""{"id":"12","title":"t","body":"متن"}"""))
    }

    @Test
    fun `a body that is not there, or not text, is null rather than an exception`() {
        assertNull(readArticleBody(null))
        assertNull(readArticleBody(""))
        assertNull(readArticleBody("not json at all"))
        assertNull(readArticleBody("""{"data":{"bodyFa":""}}"""))
        assertNull(readArticleBody("""{"data":{"bodyFa":null}}"""))
        // An array where an object was expected. This runs behind a reading page, and the page has
        // to survive a route answering something nobody designed for.
        assertNull(readArticleBody("""[{"bodyFa":"x"}]"""))
    }

    // ------------------------------------------------------------------ what it refuses

    @Test
    fun `a body that is a copy of the summary is refused`() {
        // The likeliest first version of any such route: an adapter mapping the summary into both
        // fields is one line and looks right from the server side. Taken at face value it prints
        // the same paragraph twice on one page, the second time under a heading claiming it is more.
        assertNull(articleBody("خلاصهٔ خبر", summary = "خلاصهٔ خبر"))
        assertNull(articleBody("  خلاصهٔ خبر  ", summary = "خلاصهٔ خبر"))
    }

    @Test
    fun `a body with markup in it is refused`() {
        // A Compose Text renders `<p>` as the four characters it is. The honest fallback — the
        // summary, well set, with the source named — reads better than a story with its own tags
        // printed through it.
        assertNull(articleBody("<p>یک</p><p>دو</p>", summary = null))
    }

    @Test
    fun `a real body keeps its paragraphs and loses its stray blank lines`() {
        val text = articleBody("یک\r\n\r\n\r\n\r\nدو\r\n\r\nسه", summary = "خلاصه")
        assertEquals("یک\n\nدو\n\nسه", text)
    }

    // ------------------------------------------------------------------ the crypto reader

    @Test
    fun `the crypto reader addresses the story by slug and strips this module's own prefix`() {
        var asked: String? = null
        val source = TradeYarNewsBodySource(
            client = { url -> asked = url; """{"data":{"bodyFa":"متن کامل"}}""" },
            baseUrl = "https://tradeyar.example",
        )
        // The members' feed sends the slug as the id; the public reader in this module prefixes
        // its own with `tyr:` so the two backends' ids cannot collide. The prefix is ours, so it
        // comes off before the address is built.
        val body = runBlocking { source.body("tyr:some-story-abc123", summary = "خلاصه") }
        assertEquals("متن کامل", body)
        assertEquals("https://tradeyar.example/api/v1/news/some-story-abc123", asked)
    }

    @Test
    fun `an id that cannot be a slug is not asked for at all`() {
        var asked = false
        val source = TradeYarNewsBodySource(
            client = { asked = true; null },
            baseUrl = "https://tradeyar.example",
        )
        runBlocking {
            // A numeric id is the other backend's, or a row with no slug. The route is `/{slug}`
            // and would answer 404 — once per opened article, for ever.
            assertNull(source.body("41277", summary = null))
            // A path separator in an identifier is not an identifier. Refused rather than escaped.
            assertNull(source.body("../../admin", summary = null))
            assertNull(source.body("", summary = null))
        }
        assertTrue("no request should have been made", !asked)
    }

    @Test
    fun `a build with no configured host asks nothing`() {
        var asked = false
        val source = TradeYarNewsBodySource(
            client = { asked = true; null },
            baseUrl = "",
        )
        runBlocking { assertNull(source.body("a-story", summary = null)) }
        assertTrue("no request should have been made", !asked)
    }
}
