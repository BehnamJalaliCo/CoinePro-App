package com.coinepro.core.notifications

/**
 * Which alerts have not been looked at yet — run Τ2, B6.
 *
 * ### What «queued» actually means here
 *
 * A local alert is not sent anywhere. It is stored on the phone and `LocalAlertWorker` compares it
 * against prices on Android's own schedule — and that worker runs under a **connected** constraint,
 * because there are no prices without a network. So an alert armed on a plane is not waiting to be
 * uploaded; it is waiting to be *read against a market*, and until that happens it has never been
 * checked even once.
 *
 * That distinction is the whole feature. The alert screen says «فعال», the reader believes it is
 * watching, and it is not watching anything yet. It is the same shape of silence run Τ2 has now
 * removed three times: a thing that reads as armed and cannot fire. C2's orphaned drawing alert was
 * this; so was the morning brief that sometimes did not arrive.
 *
 * ### The rule
 *
 * An alert is **unchecked** when no pass has read prices since it was created. One timestamp for the
 * whole list rather than one per alert, and that is not a shortcut: a pass reads every alert's
 * symbols together, so «the last time prices arrived» is a fact about the pass, and a per-alert copy
 * of it would be the same number written many times and able to disagree with itself.
 */
object AlertReach {

    /**
     * The ids of alerts created since the last pass that read prices.
     *
     * [lastCheckedAtMillis] null means **no pass has ever succeeded**, and then every alert is
     * unchecked — which is right on a fresh install with no network, and is the case the reader is
     * most likely to be in when it matters.
     *
     * An alert with no creation time ([LocalPriceAlert.createdAtEpochMillis] zero) is treated as
     * checked. Those are rows from before the field existed; calling them unchecked would put a
     * pill on every old alert on the first run after an update, which is a lie about all of them to
     * avoid being wrong about none.
     */
    fun uncheckedIds(alerts: List<LocalPriceAlert>, lastCheckedAtMillis: Long?): Set<String> {
        if (alerts.isEmpty()) return emptySet()
        val checkedAt = lastCheckedAtMillis
        return alerts
            .asSequence()
            .filter { it.createdAtEpochMillis > 0L }
            // Inactive alerts are excluded: a paused alert is not failing to watch, it was told
            // not to. Saying «هنوز بررسی نشده» about one would be reporting the reader's own
            // decision back to them as a problem.
            .filter { it.active }
            .filter { checkedAt == null || it.createdAtEpochMillis > checkedAt }
            .map { it.id }
            .toSet()
    }
}
