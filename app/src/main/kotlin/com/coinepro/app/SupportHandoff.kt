package com.coinepro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.coinepro.core.common.BrandConfig

/**
 * The one door from this app to a person: the product's Telegram support account.
 *
 * ### Why a chat and not a form
 *
 * Support here was a share sheet — «پشتیبانی و بازخورد» composed a message and handed it to whatever
 * app the reader picked. That is a good way to *send a report* and a bad way to *ask a question*: it
 * ends with a message in the reader's outbox and no indication that it arrived anywhere, which for
 * somebody whose money is involved is the worst possible silence. `BrandConfig.SUPPORT_URL` had been
 * in the source since the brand file was written and nothing in the app opened it. This is the
 * wiring, and the two now sit next to each other doing the two different things: a chat to ask, a
 * share sheet to report.
 *
 * ### Opened in Telegram, not in a WebView
 *
 * `t.me/<handle>` resolves in the installed Telegram app when there is one and in a browser when
 * there is not, and either way it lands in the reader's own session with their own account. A
 * WebView here would be this app's storage on Telegram's origin and a login screen for an account
 * the reader already has open one icon away — see `NewsHandoff` for the same decision about news.
 *
 * ### It reports whether it opened
 *
 * A device with no Telegram and no browser is unusual and real. [open] answers false there so the
 * caller can say something, rather than leaving a row that looks like it did nothing — which from
 * the reader's side is indistinguishable from an app that ignored them.
 */
internal object SupportHandoff {

    fun open(context: Context): Boolean {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(BrandConfig.SUPPORT_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(view) }.isSuccess
    }
}
