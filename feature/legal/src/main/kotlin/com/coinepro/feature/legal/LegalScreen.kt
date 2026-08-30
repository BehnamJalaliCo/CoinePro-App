package com.coinepro.feature.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The two documents the app can show a reader without sending them anywhere.
 *
 * The asset path is a string resource, so Android's own resource qualification picks the Persian or
 * the English file and this module contains no locale check of its own. See
 * `scripts/release/sync-legal-documents.py`, which writes those assets from `docs/legal/`.
 */
enum class LegalDocument(
    @StringRes val assetPath: Int,
    /** For a top bar, a menu row or the button at the foot of the other document. */
    @StringRes val titleRes: Int,
) {
    TERMS(R.string.legal_asset_terms, R.string.legal_title_terms),
    PRIVACY(R.string.legal_asset_privacy, R.string.legal_title_privacy),
    ;

    /** The one a reader of this document is most likely to want next. */
    val other: LegalDocument get() = if (this == TERMS) PRIVACY else TERMS
}

/**
 * Reading the terms, or the privacy policy, without leaving the app.
 *
 * ### Why this screen exists
 *
 * Both documents used to open in a browser. Nothing in the app printed the address, but the browser
 * did — in its own address bar, at the top of the page, where the owner read it. The address is not
 * this module's to move. What is ours is the observation that these are *text*, that this app draws
 * text for a living, and that a reader who never leaves the app never sees anybody's address bar.
 *
 * ### Nothing on this screen opens anything
 *
 * Links render as their text and are not tappable, which is the point rather than a limitation: the
 * screen's whole reason for existing is that reading the terms should not launch a browser, and a
 * tappable link in the middle of it would put the address bar back. The two cross-references
 * between the documents are answered by the button at the foot instead, which is a real target for
 * a thumb rather than eleven points of Persian text.
 *
 * The support address and the Telegram handle are printed as they are written, and the whole
 * document sits in a [SelectionContainer], so a reader who wants one can take it. That is the
 * honest version of a link on a screen that refuses to open things.
 */
@Composable
fun LegalScreen(
    document: LegalDocument,
    modifier: Modifier = Modifier,
    /** Switches to the other document. Null hides the button at the foot. */
    onOpenDocument: ((LegalDocument) -> Unit)? = null,
) {
    val reading = rememberLegalReading(document)
    if (reading == null) {
        // A packaged asset that will not open is a broken build, not a state a reader can act on.
        // It still gets a sentence rather than a blank screen.
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(CoineProSpacing.Two),
        ) {
            Text(
                text = stringResource(R.string.legal_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    LegalDocumentBody(document, reading, modifier, onOpenDocument)
}

/**
 * Reads and parses the packaged document.
 *
 * Synchronously, on the composing thread, which is deliberate and is the opposite of what
 * `core:help`'s `HelpCatalog` does. That catalogue is 800 KB of JSON and costs a tenth of a second;
 * these are twelve kilobytes of Markdown parsed in a single pass. Making it suspend would buy
 * nothing measurable and would cost this screen its determinism — a document that arrives one
 * frame later is a document no screenshot test can capture.
 */
@Composable
fun rememberLegalReading(document: LegalDocument): LegalReading? {
    val assets = LocalContext.current.assets
    val path = stringResource(document.assetPath)
    return remember(path) {
        runCatching { assets.open(path).use { it.bufferedReader(Charsets.UTF_8).readText() } }
            .getOrNull()
            ?.let(LegalMarkdown::read)
    }
}

/**
 * The document itself, laid out.
 *
 * Split from [LegalScreen] so a caller — the screenshot render test above all — can hand it a
 * parsed document instead of depending on a packaged asset being where it thinks it is.
 */
@Composable
fun LegalDocumentBody(
    document: LegalDocument,
    reading: LegalReading,
    modifier: Modifier = Modifier,
    onOpenDocument: ((LegalDocument) -> Unit)? = null,
) {
    // The document's own direction, not the app's. The privacy policy ships in two languages and
    // the terms in one, so an English reader can be looking at a Persian document and the column
    // has to be laid out for the text in it rather than for the phone's language.
    val direction = if (reading.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Right rather than End, per the house rule, and Left rather than Start for the same reason:
    // both are resolved here from what the document is, so neither can be flipped by a container.
    val align = if (reading.rightToLeft) TextAlign.Right else TextAlign.Left
    val untranslated = stringResource(R.string.legal_untranslated)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .verticalScroll(rememberScrollState())
                .padding(bottom = CoineProSpacing.Six),
        ) {
            CoineProListHeader(
                title = reading.title,
                // The revision date the document carries, in the document's own words and digits.
                subtitle = reading.revision?.plainText(),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = ReadingMeasure)
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                // Only where the reader's language and the document's disagree, and only where a
                // translation of this locale's strings bothered to say something about it.
                if (reading.rightToLeft && untranslated.isNotBlank()) {
                    Text(
                        text = untranslated,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        reading.blocks.forEach { LegalBlockView(it, align) }
                    }
                }

                onOpenDocument?.let { open ->
                    CoineProSecondaryButton(
                        text = stringResource(document.other.titleRes),
                        onClick = { open(document.other) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CoineProSpacing.Two),
                    )
                }
            }
        }
    }
}

/**
 * How wide a line of this is allowed to get.
 *
 * A phone is narrower than this and nothing happens. A tablet is not, and a legal paragraph run to
 * the full width of a ten-inch screen is a paragraph whose next line nobody can find.
 */
private val ReadingMeasure = 560.dp

@Composable
private fun LegalBlockView(block: LegalBlock, align: TextAlign) {
    when (block) {
        is LegalBlock.Heading -> Text(
            text = block.spans.annotated(),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                // Everything deeper than three shares the smallest heading. The documents stop at
                // three; a level five that arrived later would still read as a heading rather than
                // as a paragraph in a strange weight.
                else -> MaterialTheme.typography.titleSmall
            },
            color = if (block.level >= 3) CoineProColors.TextSecondary else CoineProColors.TextPrimary,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                // Space above a heading and not below it, so a heading belongs to the section it
                // opens rather than floating between two of them.
                .padding(top = if (block.level <= 2) CoineProSpacing.Two else CoineProSpacing.One),
        )

        is LegalBlock.Paragraph -> Reading(block.spans, align)

        is LegalBlock.Quote -> CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                block.paragraphs.forEach { spans ->
                    Text(
                        text = spans.annotated(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        is LegalBlock.Listing -> Column(
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            // The marker goes inside the string rather than beside it. A separate composable for
            // it paints at a fixed offset, which is the wrong side of a right-to-left line — the
            // same reason the deletion screen writes its own bullets.
            block.items.forEach { item -> Reading(item.spans, align, prefix = item.marker + " ") }
        }

        is LegalBlock.Table -> TableCard(block, align)

        is LegalBlock.Code -> CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                block.lines.forEach { line ->
                    Text(
                        text = BidiText.isolateLtr(line),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }

        LegalBlock.Rule -> HorizontalDivider(
            color = CoineProColors.Border,
            modifier = Modifier.padding(vertical = CoineProSpacing.One),
        )
    }
}

/**
 * A paragraph of the document.
 *
 * `bodyLarge` with the leading opened up, which is the one place in this app that earns it. The
 * type scale is tuned for dense financial rows and says so at length; this is the opposite surface
 * — somebody sitting down with several thousand words of Persian legal prose — and a reading
 * measure without reading leading is a wall.
 */
@Composable
private fun Reading(spans: List<LegalSpan>, align: TextAlign, prefix: String = "") {
    Text(
        text = spans.annotated(prefix),
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
        color = CoineProColors.TextSecondary,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A table, drawn as records rather than as a grid.
 *
 * The privacy policy has thirty rows across five tables and three of them are three columns wide,
 * every cell a Persian sentence. On a 411dp phone that is 110dp a column — four words a line, and a
 * row eight lines tall with its columns out of step. So each row becomes a small block: the first
 * cell is the row's subject and reads as its title, and every other cell is printed under it
 * labelled with its own column heading. Nothing is dropped, nothing scrolls sideways, and the row
 * reads in the order somebody would say it out loud.
 */
@Composable
private fun TableCard(table: LegalBlock.Table, align: TextAlign) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            table.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = CoineProColors.BorderSubtle)
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    Text(
                        text = row.firstOrNull().orEmpty().annotated(),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    row.drop(1).forEachIndexed { column, cell ->
                        Text(
                            text = labelled(table.header.getOrNull(column + 1), cell),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextSecondary,
                            textAlign = align,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** «کجا — تنظیمات ← حذف حساب کاربری»: the column's heading, then the cell. */
@Composable
private fun labelled(label: List<LegalSpan>?, cell: List<LegalSpan>): AnnotatedString {
    val muted = CoineProColors.TextMuted
    val value = cell.annotated()
    return buildAnnotatedString {
        if (!label.isNullOrEmpty()) {
            withStyle(SpanStyle(color = muted)) { append(label.plainText() + " — ") }
        }
        append(value)
    }
}

/**
 * Spans to text, and the one place a decision about links is made.
 *
 * A named link is its name; a link is never its address unless the document wrote the address
 * itself, which is what `autolink` means and is true only of the support e-mail and the Telegram
 * handle. Those two, and every inline code identifier, are isolated so a Latin run inside a Persian
 * paragraph keeps its own direction — without it the full stop after an address ends up in front of
 * it rather than behind.
 */
@Composable
private fun List<LegalSpan>.annotated(prefix: String = ""): AnnotatedString {
    val strong = CoineProColors.TextPrimary
    val codeBackground = CoineProColors.SurfaceElevated
    return buildAnnotatedString {
        if (prefix.isNotEmpty()) append(prefix)
        for (span in this@annotated) {
            val text = if (span.code || span.autolink) BidiText.isolateLtr(span.text) else span.text
            val style = when {
                span.code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                    color = strong,
                )
                span.bold -> SpanStyle(fontWeight = FontWeight.Bold, color = strong)
                span.italic -> SpanStyle(fontStyle = FontStyle.Italic)
                // Not the accent colour. Nothing on this screen opens, and painting text gold to
                // say "link" would promise a tap that never comes; a shade stronger than the body
                // is enough to say the words are a name or an address.
                span.link != null -> SpanStyle(color = strong)
                else -> null
            }
            if (style == null) append(text) else withStyle(style) { append(text) }
        }
    }
}
