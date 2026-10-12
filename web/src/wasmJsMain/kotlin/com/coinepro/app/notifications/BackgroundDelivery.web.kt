package com.coinepro.app.notifications

import android.content.Context

/** The browser's twin (5.29.0): a page has no battery optimisation and no background check. */
object BackgroundDelivery {
    fun restricted(context: Context): Boolean? = null

    fun lastCheck(context: Context): Long? = null

    fun openFix(context: Context) = Unit
}
