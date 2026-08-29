package com.coinepro.app.alerts

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fired alerts, for the app to show while it is open.
 *
 * ### Why an alert needs an in-app path at all
 *
 * A system notification that arrives while the reader is looking at the chart is the worst of both:
 * it covers the thing they are watching, and on some phones it does not appear at all because the
 * app is in the foreground. [com.coinepro.core.notifications.AlertChannel.IN_APP] is the reader's
 * answer to that — a banner where they are already looking — and it is a separate choice from
 * `PUSH` rather than a fallback for it, because «tell me quietly while I am here» and «wake me when
 * I am not» are different requests.
 *
 * ### [publish] answers whether anybody was actually there
 *
 * A shared flow with no collectors accepts an emission and drops it, and a delivery that was dropped
 * must not be recorded as delivered — that is precisely the silence the audit log exists to expose.
 * So the subscriber count is checked, and «the app was not open» becomes an honest
 * [AlertDeliveryOutcome.Failed] rather than a line claiming the reader was told.
 *
 * No replay, and a small buffer that drops the oldest. An alert is about a price now; showing a
 * reader the banner for a level that was reached before they opened the app is worse than showing
 * them nothing, and the notification shade is where a missed one belongs.
 */
@Singleton
class InAppAlertBus @Inject constructor() {

    private val stream = MutableSharedFlow<FiredAlert>(
        replay = 0,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** What the app chrome collects while it is composed. */
    val fired: SharedFlow<FiredAlert> = stream.asSharedFlow()

    /** Offers one firing to whatever is on screen, and reports whether there was anything. */
    fun publish(alert: FiredAlert): Boolean =
        stream.subscriptionCount.value > 0 && stream.tryEmit(alert)

    private companion object {
        /**
         * How many firings may queue for a collector that is momentarily busy.
         *
         * Enough for a watchlist alert that catches several members on one pass, and small enough
         * that nothing accumulates: past this the oldest goes, because the newest is the one the
         * reader is still in time to act on.
         */
        const val BUFFER = 8
    }
}
