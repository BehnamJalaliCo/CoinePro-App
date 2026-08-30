package com.coinepro.feature.news

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * The two ways a story leaves this app: sent to somebody, or opened where it was published.
 *
 * ### Why both of these go out through an intent rather than into a WebView
 *
 * The app already has two WebViews and both are pinned to a single host — the terminal to its own
 * origin, the Telegram widget to Telegram's — because a WebView in this process carries this
 * process's storage. A news feed is a list of arbitrary third-party addresses chosen by somebody
 * else's wire service, which is the exact case a pinned WebView exists to exclude. So a story opens
 * in the reader's own browser, in the reader's own session, with this app's storage nowhere near
 * it, and the reader can see the address bar and decide for themselves what they are reading.
 *
 * ### `https` only, checked here and not only at the gateway
 *
 * `safeHttpsUrl` in `core:marketintel` already refuses anything else, so this is the second check
 * on the same value. It is here anyway for the reason `MembershipGate` states about the same
 * problem: `ACTION_VIEW` on an `intent://` URI can start a component in another app on this
 * reader's phone, and a URL that reached a screen through some other path one day — a saved record
 * written by an older build, a future caller — must not be the first time anybody looks at its
 * scheme.
 */
internal object NewsHandoff {

    /**
     * Hands the story to whatever the reader shares things with.
     *
     * The text is the headline, the publisher and the link, in that order and on separate lines,
     * because that is what somebody receiving it needs in the order they need it — a bare URL in a
     * chat is a thing the recipient has to open before they know whether they want to.
     *
     * The publisher and the link are each written only where there is one. The public feed sends
     * stories with no attribution and no address — see [NewsStory] — and a shared message with a
     * blank line where the source should be reads as a message that was cut off.
     *
     * Returns false when the device has nothing to share with, so the caller can say so rather than
     * leaving a button that appears to have done nothing. It is rare and it is real: a locked-down
     * device with no mail, no messenger and no browser.
     */
    fun share(context: Context, title: String, source: String?, url: String?, subject: String): Boolean {
        val body = buildString {
            append(title)
            source?.let {
                appendLine()
                append(it)
            }
            safeUrl(url)?.let {
                appendLine()
                append(it)
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    /**
     * Opens the story where it was published.
     *
     * Returns false for an address this app will not follow and for a device with no browser, and
     * the two are deliberately one answer: from the reader's side both mean «this link did not
     * open», and splitting them would mean writing a sentence about URI schemes for somebody who
     * only wanted to read a story about the price of gold.
     */
    fun openSource(context: Context, url: String?): Boolean {
        val safe = safeUrl(url) ?: return false
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(safe))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(view) }.isSuccess
    }

    /** One address this app is willing to hand to another app, or null. */
    internal fun safeUrl(raw: String?): String? = runCatching {
        val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching null
        val uri = URI(trimmed)
        if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching null
        if (uri.host.isNullOrBlank()) return@runCatching null
        trimmed
    }.getOrNull()
}
