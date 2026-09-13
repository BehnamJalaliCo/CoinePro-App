package com.coinepro.feature.script

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.script.ScriptPaste
import com.coinepro.core.script.ScriptTemplate

/**
 * **«چسباندن اسکریپت»** — where an assistant's answer lands (run Σ, S3 A).
 *
 * ### Why there is a sheet at all, rather than just pasting into the editor
 *
 * Because the text in the editor is not going to be the text they pasted, and a reader who is not
 * told that will believe the app corrupted their script. The sheet's whole job is one sentence —
 * «this is what I changed, and here is how to put it back» — said before they go looking.
 *
 * The three outcomes are three different sheets sharing a frame, because they are three different
 * conversations: for code that needed work, a list of repairs and an undo; for code that was
 * already right, one line saying so; for a sentence, a picker.
 *
 * ### Why the fixes are applied before the sheet opens
 *
 * A sheet that asked permission for each of nineteen repairs would be a worse experience than the
 * error messages it replaces. They are applied, listed, and reversible in one tap — which is what
 * `ScriptController.undoPaste` is for and why the raw text is kept.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScriptPasteSheet(
    paste: ScriptPaste.Paste,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    onUseTemplate: (ScriptTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    CoineProSheet(
        title = stringResource(R.string.script_paste_title),
        subtitle = stringResource(paste.dialect.label),
        onDismiss = onDismiss,
        modifier = modifier.testTag(PASTE_SHEET),
    ) {
        ScriptPasteBody(paste = paste, onKeep = onDismiss, onUndo = onUndo, onUseTemplate = onUseTemplate)
    }
}

/**
 * The paste sheet's content without the sheet.
 *
 * Split out for the same reason every other sheet in this app is: a `ModalBottomSheet` draws into
 * its own window, so an off-device capture of the activity comes back empty and the panel can never
 * be looked at before it ships. `ScriptPasteProofTest` renders this.
 */
@Composable
fun ScriptPasteBody(
    paste: ScriptPaste.Paste,
    onKeep: () -> Unit,
    onUndo: () -> Unit,
    onUseTemplate: (ScriptTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            when {
                paste.dialect == ScriptPaste.Dialect.PROSE -> TemplateChoices(paste.templates, english, onUseTemplate)
                paste.fixes.isEmpty() && paste.unsupported.isEmpty() ->
                    Text(
                        stringResource(R.string.script_paste_clean),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextMuted,
                    )
                else -> {
                    Text(
                        text = if (paste.fixes.size == 1) {
                            stringResource(R.string.script_paste_fixed_one)
                        } else {
                            stringResource(R.string.script_paste_fixed_many, countText(paste.fixes.size.toString(), english))
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    for (fix in paste.fixes) FixRow(fix, english)
                    if (paste.unsupported.isNotEmpty()) UnsupportedRows(paste)
                }
            }
            if (paste.dialect != ScriptPaste.Dialect.PROSE) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    CoineProPrimaryButton(
                        text = stringResource(R.string.script_paste_keep),
                        onClick = onKeep,
                        modifier = Modifier.weight(1f),
                    )
                    if (paste.fixes.isNotEmpty()) {
                        CoineProSecondaryButton(
                            text = stringResource(R.string.script_paste_undo),
                            onClick = onUndo,
                            modifier = Modifier.weight(1f).testTag(PASTE_UNDO),
                        )
                    }
                }
            }
        }
    }
}

/** One repair: what it did, and which lines it touched. */
@Composable
private fun FixRow(fix: ScriptPaste.Fix, english: Boolean) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = if (english) fix.whatEn else fix.what,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (fix.lines.isNotEmpty()) {
                Text(
                    // The line numbers are a count of the reader's own text, so they are prose
                    // digits — Persian in the Persian app — not market figures.
                    text = if (fix.lines.size == 1) {
                        stringResource(R.string.script_paste_line, countText(fix.lines.first().toString(), english))
                    } else {
                        stringResource(R.string.script_paste_lines, fix.lines.joinToString("، ") { countText(it.toString(), english) })
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/** What Pine carried that this language has not — named, never silently dropped. */
@Composable
private fun UnsupportedRows(paste: ScriptPaste.Paste) {
    Text(
        stringResource(R.string.script_paste_unsupported),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
    for (line in paste.unsupported) {
        Text(
            text = line.source.trim(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textDirection = TextDirection.Ltr,
            ),
            color = CoineProColors.TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.Stage, CoineProShapes.small)
                .padding(CoineProSpacing.One)
                .horizontalScroll(rememberScrollState()),
            maxLines = 1,
        )
    }
}

/** A sentence became a shortlist. The reader picks; nothing was guessed on their behalf. */
@Composable
private fun TemplateChoices(
    templates: List<ScriptTemplate>,
    english: Boolean,
    onUseTemplate: (ScriptTemplate) -> Unit,
) {
    Text(
        stringResource(R.string.script_paste_templates),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    for (template in templates) {
        CoineProCard(modifier = Modifier.fillMaxWidth().testTag(PASTE_TEMPLATE + template.id)) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Text(
                    text = if (english) template.titleEn else template.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (english) template.summaryEn else template.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.script_paste_use_template),
                    onClick = { onUseTemplate(template) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * **«یک اسکریپت از هوش مصنوعی بگیر»** — the prompt, ready to hand over (run Σ, S3 B).
 *
 * The prompt itself is [com.coinepro.core.script.ScriptPromptKit]'s and is generated from the
 * language's own reference. What this screen adds is the three taps around it: copy, open the
 * assistant, paste the answer back. Nothing here decides anything; it is a courier.
 *
 * The prompt is shown in full rather than hidden behind «copy». A reader about to send several
 * hundred words to a third party is entitled to read them first, and the ones who do read it learn
 * more about the language from that screen than from the reference tab.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScriptPromptSheet(
    prompt: String,
    onDismiss: () -> Unit,
    onOpenAssistant: (String) -> Unit,
    onPasteResult: () -> Unit,
    version: String,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    CoineProSheet(
        title = stringResource(R.string.script_prompt_title),
        subtitle = stringResource(R.string.script_prompt_version, countText(version, english)),
        onDismiss = onDismiss,
        modifier = modifier.testTag(PROMPT_SHEET),
    ) {
        ScriptPromptBody(prompt = prompt, onOpenAssistant = onOpenAssistant, onPasteResult = onPasteResult)
    }
}

/** The prompt kit's content without the sheet. See [ScriptPasteBody] for why. */
@Composable
fun ScriptPromptBody(
    prompt: String,
    onOpenAssistant: (String) -> Unit,
    onPasteResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                stringResource(R.string.script_prompt_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            // **Each line takes its own direction, and the block takes none.**
            //
            // This was `LtrDirection` at first, on the reasoning that a prompt is code. Half of it
            // is: the function lists and the three examples are code and read left to right. The
            // other half is Persian prose, and forcing that block left-to-right put every full stop
            // at the wrong end and every «-» on the wrong side — the frame of it is in
            // `docs/qa/screenshots/`, and it was unreadable.
            //
            // `TextDirection.Content` resolves per paragraph from its first strong character, which
            // is exactly the rule this content wants: Persian sentences right to left, `ta.ema(...)`
            // left to right, decided line by line rather than declared for the block.
            Text(
                text = remember(prompt) { isolatedForDisplay(prompt) },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textDirection = TextDirection.Content,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PROMPT_HEIGHT)
                    .background(CoineProColors.Stage, CoineProShapes.small)
                    .padding(CoineProSpacing.One)
                    .verticalScroll(rememberScrollState())
                    .testTag(PROMPT_TEXT),
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.script_prompt_copy),
                // The **raw** prompt, never the isolated one: the isolates are invisible characters
                // that exist to make the block readable on this screen, and pasting them into an
                // assistant would put control codes in the middle of `ta.ema(close, 20)`.
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt)) },
                modifier = Modifier.fillMaxWidth().testTag(PROMPT_COPY),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.script_prompt_chatgpt),
                    onClick = { onOpenAssistant(CHATGPT) },
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.script_prompt_claude),
                    onClick = { onOpenAssistant(CLAUDE) },
                    modifier = Modifier.weight(1f),
                )
            }
            CoineProSecondaryButton(
                text = stringResource(R.string.script_prompt_paste_result),
                onClick = onPasteResult,
                modifier = Modifier.fillMaxWidth().padding(bottom = CoineProSpacing.Two).testTag(PROMPT_PASTE),
            )
        }
    }
}

/**
 * [prompt] with every code token wrapped in a bidi isolate — **for display only**.
 *
 * ### Why it is needed
 *
 * The Persian prompt is Persian sentences with Latin code inside them, which is the exact shape
 * that bidi reordering gets wrong: in a right-to-left paragraph the neutral characters around a
 * Latin run are pulled to its far side, so `//@version` renders as `version@//` and
 * `indicator(...)` as `(...)indicator`. A reader comparing the prompt against what an assistant
 * returned would be comparing it against something the screen had rearranged.
 *
 * ### Why it is not in the prompt itself
 *
 * Because the prompt is *copied*. An isolate is an invisible control character, and a prompt
 * carrying them would arrive in the assistant with control codes inside its code — so the isolates
 * are added on the way to the screen and nowhere else, and `ScriptPromptDisplayTest` holds that
 * stripping them gives the prompt back exactly.
 *
 * A run qualifies when it is ASCII, unbroken by a space, and contains at least one letter: that is
 * `close[1]`, `ta.rma`, `//@version` and `strategy(...)`, and it is not `«` or `،` or a full stop
 * that belongs to the Persian sentence around it.
 */
internal fun isolatedForDisplay(prompt: String): String =
    CODE_RUN.replace(prompt) { match ->
        val run = match.value
        if (run.none { it in 'A'..'Z' || it in 'a'..'z' }) return@replace run
        // The run is trimmed back to where the code actually starts. Without this the colon in
        // «- سری‌ها: close, open, high» is ASCII, so it joins the run and ends up on the far side
        // of the list — the sentence's own punctuation, moved by a fix meant for the code. The
        // slash is kept because `//@version` begins with one.
        val start = run.indexOfFirst { it.isLetterOrDigit() || it == '/' || it == '_' }
        run.substring(0, start) + BidiText.LRI + run.substring(start) + BidiText.PDI
    }

/**
 * A maximal run of printable ASCII, spaces between ASCII tokens included.
 *
 * The spaces matter: `close, open, high, low` is one run and one isolate, so it reads in the order
 * it was written. Isolating each name on its own left the *list* laid out right to left, and a
 * reader saw the series in reverse — which is a worse error than the one being fixed, because it
 * looks like data rather than like a rendering fault. A Persian word between two Latin ones still
 * breaks the run, which is what makes this safe to apply to the whole prompt.
 */
private val CODE_RUN = Regex("[!-~]+(?: +[!-~]+)*")

/**
 * A count in the reader's own numerals: Persian in Persian prose, Latin in English.
 *
 * A line number and a version number are counts of the reader's own text, not market figures, so
 * they take the prose numerals — which is the house rule stated the other way round.
 */
private fun countText(value: String, english: Boolean): String =
    if (english) value else value.map { PERSIAN_DIGITS.getOrNull(it - '0') ?: it }.joinToString("")

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

private val ScriptPaste.Dialect.label: Int
    get() = when (this) {
        ScriptPaste.Dialect.NAMA -> R.string.script_paste_dialect_nama
        ScriptPaste.Dialect.PINE -> R.string.script_paste_dialect_pine
        ScriptPaste.Dialect.PROSE -> R.string.script_paste_dialect_prose
    }

/** The web front doors, not the apps: a link opens whatever the reader already signed into. */
const val CHATGPT = "https://chatgpt.com/"
const val CLAUDE = "https://claude.ai/new"

private val PROMPT_HEIGHT = 320.dp

const val PASTE_SHEET = "paste-sheet"
const val PASTE_UNDO = "paste-undo"
const val PASTE_TEMPLATE = "paste-template-"
const val PROMPT_SHEET = "prompt-sheet"
const val PROMPT_TEXT = "prompt-text"
const val PROMPT_COPY = "prompt-copy"
const val PROMPT_PASTE = "prompt-paste"
