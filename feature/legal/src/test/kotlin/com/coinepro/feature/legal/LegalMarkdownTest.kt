package com.coinepro.feature.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every construct the two legal documents actually use, and the ones they do not.
 *
 * The failure this file exists to prevent is a single one: a delimiter reaching a reader. A screen
 * that prints `##` or `**` in the middle of a privacy policy is worse than no screen, because it
 * looks like a bug in the document rather than a bug in the app, and the document is a legal
 * instrument.
 *
 * So the assertions come in two shapes. The construct tests say what a thing parses *into*. The
 * degradation tests say only one thing about constructs the renderer has no style for — the text
 * survived and the syntax did not.
 */
class LegalMarkdownTest {

    // ── headings ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `headings carry their level and lose their hashes`() {
        val blocks = LegalMarkdown.parse("# عنوان\n\n## بند ۱\n\n### زیربند")
        assertEquals(
            listOf(1 to "عنوان", 2 to "بند ۱", 3 to "زیربند"),
            blocks.map { it as LegalBlock.Heading }.map { it.level to it.spans.plainText() },
        )
    }

    /** `#hashtag` is not a heading. It is a word, and it has to reach the reader as one. */
    @Test
    fun `a hash with no space is text`() {
        val blocks = LegalMarkdown.parse("#hashtag")
        assertEquals("#hashtag", (blocks.single() as LegalBlock.Paragraph).spans.plainText())
    }

    // ── paragraphs ──────────────────────────────────────────────────────────────────────────────

    /**
     * Both documents wrap at a hundred columns. Every paragraph in them is several source lines and
     * one sentence, so a renderer that kept the line breaks would print a legal document as ragged
     * poetry.
     */
    @Test
    fun `a wrapped paragraph is one paragraph`() {
        val blocks = LegalMarkdown.parse("خط اول\nخط دوم\nخط سوم\n\nپاراگراف بعدی")
        assertEquals(
            listOf("خط اول خط دوم خط سوم", "پاراگراف بعدی"),
            blocks.map { (it as LegalBlock.Paragraph).spans.plainText() },
        )
    }

    // ── emphasis, code ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `bold is bold and keeps no asterisks`() {
        val spans = paragraph("پرو چارت **کارگزار نیست** و پول شما را نگه نمی‌دارد.")
        assertEquals("پرو چارت کارگزار نیست و پول شما را نگه نمی‌دارد.", spans.plainText())
        assertEquals(listOf("کارگزار نیست"), spans.filter { it.bold }.map { it.text })
    }

    @Test
    fun `italic is italic and keeps no asterisk`() {
        val spans = paragraph("an exchange API key *only* if you connect an exchange account")
        assertEquals("an exchange API key only if you connect an exchange account", spans.plainText())
        assertEquals(listOf("only"), spans.filter { it.italic }.map { it.text })
    }

    /** A lone asterisk in a sentence must not swallow the rest of it. */
    @Test
    fun `an unpaired asterisk is text`() {
        val spans = paragraph("سود * زیان")
        assertEquals("سود * زیان", spans.plainText())
        assertTrue(spans.none { it.italic })
    }

    @Test
    fun `inline code is code and keeps no backticks`() {
        val spans = paragraph("شناسه‌ی نصب در هدر `X-Install-Id` فرستاده می‌شود")
        assertEquals("شناسه‌ی نصب در هدر X-Install-Id فرستاده می‌شود", spans.plainText())
        assertEquals(listOf("X-Install-Id"), spans.filter { it.code }.map { it.text })
    }

    // ── links ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The whole point of the screen, in one assertion.
     *
     * A named link renders its **name**. The address is kept in the model, because the model is
     * what a caller would need to act on one, and it is never the text a reader sees — which is
     * exactly what stops somebody's hosting address appearing in the middle of the terms.
     */
    @Test
    fun `a named link shows its name and not its address`() {
        val spans = paragraph("در سند جدا: [سیاست حریم خصوصی](PRIVACY_POLICY.md).")
        assertEquals("در سند جدا: سیاست حریم خصوصی.", spans.plainText())
        val link = spans.single { it.link != null }
        assertEquals("سیاست حریم خصوصی", link.text)
        assertEquals("PRIVACY_POLICY.md", link.link)
        assertFalse(link.autolink)
    }

    @Test
    fun `an autolink is its own text`() {
        val spans = paragraph("پشتیبانی در تلگرام: <https://t.me/CoinePro_Admin>")
        val link = spans.single { it.link != null }
        assertEquals("https://t.me/CoinePro_Admin", link.text)
        assertEquals("https://t.me/CoinePro_Admin", link.link)
        assertTrue(link.autolink)
    }

    @Test
    fun `an e-mail autolink gains a mailto and keeps its angle brackets off the screen`() {
        val spans = paragraph("ایمیل پشتیبانی: <behnamjalali88@gmail.com>")
        assertEquals("ایمیل پشتیبانی: behnamjalali88@gmail.com", spans.plainText())
        assertEquals("mailto:behnamjalali88@gmail.com", spans.single { it.link != null }.link)
    }

    /** Angle brackets around anything else are not a link, and are not markup either. */
    @Test
    fun `angle brackets that are not a link are text`() {
        assertEquals("۵ < ۱۰", paragraph("۵ < ۱۰").plainText())
    }

    // ── lists ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bullets become items and the asterisk becomes a bullet`() {
        val items = listing(
            """
            * ما کارگزار نیستیم.
            * خروجی هوش مصنوعی می‌تواند خطا داشته باشد و باید
              مستقل بازبینی شود.
            """.trimIndent(),
        )
        assertEquals(listOf("•", "•"), items.map { it.marker })
        assertEquals(
            listOf("ما کارگزار نیستیم.", "خروجی هوش مصنوعی می‌تواند خطا داشته باشد و باید مستقل بازبینی شود."),
            items.map { it.spans.plainText() },
        )
    }

    /**
     * The terms number their own clauses in Persian digits. The marker is the document's, not a
     * counter's: renumbering clause «۲» as `2` on screen would be the app rewriting a contract.
     */
    @Test
    fun `numbered items keep the document's own Persian digits`() {
        val items = listing("۱. ثبت‌نام کنید.\n۲. موجودی را برسانید.\n۳. UID را وارد کنید.")
        assertEquals(listOf("۱.", "۲.", "۳."), items.map { it.marker })
        assertEquals("موجودی را برسانید.", items[1].spans.plainText())
    }

    @Test
    fun `a bullet may carry bold`() {
        val items = listing("* **بروکر MT5** — اگر کپی‌تریدینگ را فعال کنید.")
        assertEquals("بروکر MT5 — اگر کپی‌تریدینگ را فعال کنید.", items.single().spans.plainText())
        assertEquals(listOf("بروکر MT5"), items.single().spans.filter { it.bold }.map { it.text })
    }

    // ── quotes, rules ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a blockquote splits at its own blank line`() {
        val quote = LegalMarkdown.parse("> یادداشت اول\n> ادامه‌ی آن\n>\n> یادداشت دوم")
            .single() as LegalBlock.Quote
        assertEquals(
            listOf("یادداشت اول ادامه‌ی آن", "یادداشت دوم"),
            quote.paragraphs.map { it.plainText() },
        )
    }

    @Test
    fun `three dashes are a rule and not a heading underline`() {
        assertEquals(listOf(LegalBlock.Rule), LegalMarkdown.parse("---"))
    }

    // ── tables ──────────────────────────────────────────────────────────────────────────────────

    /**
     * The privacy policy is thirty rows of table. The alignment row is dropped by its *shape* — a
     * table that lost it still renders every row of data rather than eating one.
     */
    @Test
    fun `a table keeps its header and drops the alignment row`() {
        val table = LegalMarkdown.parse(
            """
            | داده | کجا | چرا |
            | --- | --- | --- |
            | توکن نشست | DataStore رمزنگاری‌شده | شما را وارد نگه می‌دارد |
            | کش قیمت | Room | نمایش آخرین وضعیت |
            """.trimIndent(),
        ).single() as LegalBlock.Table

        assertEquals(listOf("داده", "کجا", "چرا"), table.header.map { it.plainText() })
        assertEquals(2, table.rows.size)
        assertEquals(
            listOf("توکن نشست", "DataStore رمزنگاری‌شده", "شما را وارد نگه می‌دارد"),
            table.rows.first().map { it.plainText() },
        )
    }

    @Test
    fun `a table cell carries inline markup`() {
        val table = LegalMarkdown.parse(
            "| مجوز | برای چه |\n| --- | --- |\n| `INTERNET` | بدون آن اپ کاری ندارد |",
        ).single() as LegalBlock.Table
        assertTrue(table.rows.single().first().single().code)
        assertEquals("INTERNET", table.rows.single().first().plainText())
    }

    // ── the document's own shape ────────────────────────────────────────────────────────────────

    @Test
    fun `the title and the revision line are lifted out of the body`() {
        val reading = LegalMarkdown.read(
            """
            # شرایط استفاده — پرو چارت

            **آخرین بازنگری:** ۱۴۰۵/۰۶/۰۴

            ## ۱) پذیرش شرایط

            متن بند.
            """.trimIndent(),
        )
        assertEquals("شرایط استفاده — پرو چارت", reading.title)
        assertEquals("آخرین بازنگری: ۱۴۰۵/۰۶/۰۴", reading.revision?.plainText())
        assertTrue(reading.rightToLeft)
        assertEquals(
            listOf<Any>(2 to "۱) پذیرش شرایط", "متن بند."),
            reading.blocks.map {
                when (it) {
                    is LegalBlock.Heading -> it.level to it.spans.plainText()
                    is LegalBlock.Paragraph -> it.spans.plainText()
                    else -> it
                }
            },
        )
    }

    /**
     * The English half of the privacy policy opens with a sentence, not a date. Lifting it would
     * put a paragraph of content in the place a revision stamp goes.
     */
    @Test
    fun `an opening sentence is not mistaken for a revision line`() {
        val reading = LegalMarkdown.read("# Privacy Policy\n\nEvery claim below was written from code.")
        assertEquals("Privacy Policy", reading.title)
        assertNull(reading.revision)
        assertFalse(reading.rightToLeft)
        assertEquals(1, reading.blocks.size)
    }

    /** A generated file says it is generated. A reader of the terms never sees it say so. */
    @Test
    fun `an html comment never reaches the reader`() {
        val blocks = LegalMarkdown.parse("<!-- Generated. Edit docs/legal/. -->\n# عنوان\n\nمتن.")
        assertTrue(blocks.none { it is LegalBlock.Paragraph && "Generated" in it.spans.plainText() })
        assertEquals(1, blocks.count { it is LegalBlock.Heading })
    }

    // ── outside the set ─────────────────────────────────────────────────────────────────────────

    /**
     * The construct the documents do not use, and the one this whole file is really about.
     *
     * Strikethrough has no style here — a struck-out clause in a contract is a clause somebody
     * meant to remove, and inventing a treatment for it would be the app taking a position. What it
     * must not do is print `~~`. The words survive; the syntax does not.
     */
    @Test
    fun `strikethrough degrades to its words and never to tildes`() {
        val spans = paragraph("این بند ~~حذف شده~~ است.")
        assertEquals("این بند حذف شده است.", spans.plainText())
        assertFalse(spans.any { it.text.contains("~") })
    }

    /** Also unused: an image. The alt text is the sentence written for exactly this case. */
    @Test
    fun `an image degrades to its alt text`() {
        val spans = paragraph("![نمودار نمونه](chart.webp)")
        assertEquals("نمودار نمونه", spans.plainText())
        assertTrue(spans.none { it.link != null })
    }

    /** Also unused: a heading deeper than the documents go. It is still a heading, not a row of hashes. */
    @Test
    fun `a level five heading is a heading`() {
        val heading = LegalMarkdown.parse("##### ریزبند").single() as LegalBlock.Heading
        assertEquals(5, heading.level)
        assertEquals("ریزبند", heading.spans.plainText())
    }

    /** Also unused: a fence. Its contents are lines; the fence itself is not one of them. */
    @Test
    fun `a fenced block keeps its lines and drops its fence`() {
        val code = LegalMarkdown.parse("```\nfirst\nsecond\n```").single() as LegalBlock.Code
        assertEquals(listOf("first", "second"), code.lines)
    }

    // ── the shipped documents ───────────────────────────────────────────────────────────────────

    /**
     * The three files the app actually ships, parsed, with one question asked of every span: is
     * there a delimiter in it?
     *
     * This is the test that would have caught the tables — a construct the brief did not mention
     * because nobody had counted them, and which as unparsed text would have printed thirty rows of
     * `| … | … |` down the middle of the privacy policy.
     *
     * It reads the assets from disk rather than through an `AssetManager`, which keeps the whole
     * suite off Robolectric. If `scripts/release/sync-legal-documents.py` has not been run the
     * files are absent and the test says so rather than passing quietly.
     */
    @Test
    fun `no shipped document renders a delimiter`() {
        for (name in listOf("terms.md", "privacy.md", "privacy-en.md")) {
            val reading = LegalMarkdown.read(asset(name))

            assertTrue("$name has no title", reading.title.isNotBlank())
            assertTrue("$name parsed to nothing", reading.blocks.size > 20)

            for (text in reading.texts()) {
                for (delimiter in listOf("**", "](", "~~", "```", "<!--")) {
                    assertFalse("$name renders \"$delimiter\" in: $text", text.contains(delimiter))
                }
                assertFalse("$name renders a table pipe in: $text", text.contains("|"))
                assertFalse("$name renders a hash heading in: $text", text.trimStart().startsWith("#"))
            }
        }
    }

    /**
     * And the reason the whole feature exists: the hosting address is not printed to a reader.
     *
     * Both documents still *link* to the published deletion page — Google Play requires a
     * web-reachable route and the link carries it — but they name it rather than spelling it out,
     * so no address bar and no address reaches somebody who only wanted to read the terms.
     */
    @Test
    fun `no shipped document prints the hosting address`() {
        for (name in listOf("terms.md", "privacy.md", "privacy-en.md")) {
            val reading = LegalMarkdown.read(asset(name))
            for (text in reading.texts()) {
                assertFalse("$name prints the hosting address in: $text", text.contains("github.io"))
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private fun paragraph(source: String): List<LegalSpan> =
        (LegalMarkdown.parse(source).single() as LegalBlock.Paragraph).spans

    private fun listing(source: String): List<LegalBlock.Listing.Item> =
        (LegalMarkdown.parse(source).single() as LegalBlock.Listing).items

    /** Every string this document would put on a screen, header included. */
    private fun LegalReading.texts(): List<String> = buildList {
        add(title)
        revision?.let { add(it.plainText()) }
        for (block in blocks) {
            when (block) {
                is LegalBlock.Heading -> add(block.spans.plainText())
                is LegalBlock.Paragraph -> add(block.spans.plainText())
                is LegalBlock.Quote -> block.paragraphs.forEach { add(it.plainText()) }
                is LegalBlock.Listing -> block.items.forEach { add(it.marker); add(it.spans.plainText()) }
                is LegalBlock.Table -> {
                    block.header.forEach { add(it.plainText()) }
                    block.rows.forEach { row -> row.forEach { add(it.plainText()) } }
                }
                is LegalBlock.Code -> addAll(block.lines)
                LegalBlock.Rule -> Unit
            }
        }
    }

    private fun asset(name: String): String {
        // Gradle runs a unit test with the module directory as its working directory; the second
        // candidate is for a runner started from the repository root instead.
        val candidates = listOf(
            java.io.File("src/main/assets/legal/$name"),
            java.io.File("feature/legal/src/main/assets/legal/$name"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue(
            "$name is missing. Run scripts/release/sync-legal-documents.py.",
            file != null,
        )
        return file!!.readText(Charsets.UTF_8)
    }
}
