package com.coinepro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.coinepro.core.update.AppRelease
import com.coinepro.core.update.AppUpdate

/**
 * Hands a published build's address to the browser.
 *
 * ### Why the browser and not this app
 *
 * Because downloading and installing a package in-process means holding
 * `REQUEST_INSTALL_PACKAGES` — permission to put *any* package on the phone — plus a download
 * manager, a file provider and a directory of half-fetched APKs to keep clean. That is a great deal
 * of machinery and one genuinely dangerous permission, bought in exchange for saving the reader
 * three taps they have already performed once, on the day they installed this app. `AppUpdate`'s
 * own note is the longer argument.
 *
 * ### The check is here as well as in `AppUpdate`, on purpose
 *
 * [AppUpdate.publishable] already decided whether to *offer* the release; this asks again before
 * *acting* on it. The two are a decision and an action separated by however long the card sat on
 * the screen, and the thing at the end of this one is an installable package. A guard at the point
 * of action costs one comparison and closes the gap.
 */
internal object UpdateHandoff {

    fun open(context: Context, release: AppRelease): Boolean {
        if (!AppUpdate.publishable(release)) return false
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(release.url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(view) }.isSuccess
    }
}
