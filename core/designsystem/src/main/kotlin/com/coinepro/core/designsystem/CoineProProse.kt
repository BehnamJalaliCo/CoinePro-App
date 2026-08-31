package com.coinepro.core.designsystem

import androidx.compose.ui.text.style.TextAlign
import com.coinepro.core.common.BidiText

/**
 * How a paragraph the app did not write is laid out.
 *
 * Everything else in this app is copy we wrote, in Persian, and the rule for it is simple:
 * [TextAlign.Right], never `End`, because the reader's locale is Persian and the paragraph is
 * Persian with it. Wire copy is the exception. A story arrives in whatever language the wire filed
 * it in, and the two screens that show one — the news list and the explore board — must not
 * disagree about what to do with an English sentence.
 *
 * So both questions live here rather than inside one feature: which edge the paragraph hangs from,
 * and which direction it is laid out in. They are two questions and they need two answers — that
 * was the mistake the first attempt made.
 */
object CoineProProse {
    /**
     * Which edge a piece of the server's copy hangs from.
     *
     * `TextAlign.Right` is this app's rule and it is right for Persian — but it is a rule about
     * *Persian*, and a wire story is whatever language the wire wrote it in.
     */
    fun alignment(text: String): TextAlign =
        if (BidiText.isLatinSentence(text)) TextAlign.Left else TextAlign.Right

    /**
     * The same copy, laid out in its own direction.
     *
     * Alignment alone is not enough and that was the first attempt: moving an English sentence to
     * the left edge left the *paragraph* right-to-left, so its full stop stayed at the visual start
     * — «.lifting precious metals», now against the left margin instead of the right. The period is
     * a direction-neutral character and the paragraph decides where it goes, so the paragraph is
     * what has to change. An isolate does exactly that for everything between its two marks.
     *
     * Persian copy is returned untouched: it is already in the paragraph's own direction, and
     * isolating it would be wrapping a sentence against the very thing it agrees with.
     */
    fun paragraph(text: String): String =
        if (BidiText.isLatinSentence(text)) BidiText.isolateLtr(text) else text
}
