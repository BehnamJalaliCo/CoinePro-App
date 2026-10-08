package com.coinepro.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/*
 * `HostMirror` as the page has it (5.27.0): a pass-through. The phone retries a filtered host through
 * TradeYar's; the page reaches both platforms through the pro-chart.com relay already, so there is
 * no second road to take here.
 */
class HostMirror(
    @Suppress("UNUSED_PARAMETER") mirrors: Map<String, HttpUrl>,
    @Suppress("UNUSED_PARAMETER") memory: MirrorMemory = MirrorMemory.InProcess(),
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())

    companion object {
        fun parse(@Suppress("UNUSED_PARAMETER") raw: String?): Map<String, HttpUrl> = emptyMap()
    }
}

interface MirrorMemory {
    fun mirrorUntil(host: String): Long

    fun useMirror(host: String, untilEpochMillis: Long)

    class InProcess : MirrorMemory {
        private val until = mutableMapOf<String, Long>()
        override fun mirrorUntil(host: String): Long = until[host] ?: 0L
        override fun useMirror(host: String, untilEpochMillis: Long) {
            until[host] = untilEpochMillis
        }
    }
}
