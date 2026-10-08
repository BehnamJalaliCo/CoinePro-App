package com.coinepro.app

import android.content.Context
import com.coinepro.core.network.MirrorMemory

/**
 * [MirrorMemory] across launches (5.27.0): a reader on a network that filters the forex host is
 * on it again tomorrow, and the probe that found it out should not be paid on every cold start.
 */
class PreferencesMirrorMemory(context: Context) : MirrorMemory {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun mirrorUntil(host: String): Long = runCatching { preferences.getLong(host, 0L) }.getOrDefault(0L)

    override fun useMirror(host: String, untilEpochMillis: Long) {
        runCatching { preferences.edit().putLong(host, untilEpochMillis).apply() }
    }

    private companion object {
        const val FILE = "host_mirror"
    }
}
