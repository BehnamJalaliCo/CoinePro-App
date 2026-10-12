package com.coinepro.app.pulse

import android.content.Context
import java.time.ZoneId

/**
 * The browser's twin of the phone's `ReaderClock` (5.29.0): a page has no SIM and no mobile network
 * to ask, so the browser's own zone is the reader's.
 */
object ReaderClock {
    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    fun zone(context: Context): ZoneId = ZoneId.systemDefault()

    fun inIran(context: Context): Boolean = zone(context).id == TEHRAN.id
}
