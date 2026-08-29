package com.coinepro.app.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.LocalPriceAlert
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The stored alerts, behind the port [AlertEvaluator] reads them through.
 *
 * A thin adapter and nothing else. The interesting decision it carries is that [all] returns every
 * alert rather than only the active ones: an alert that has expired is not active and its expiry
 * still has to be written to the audit log exactly once, which cannot happen if the evaluator never
 * sees it.
 */
@Singleton
class StoredAlertRepository @Inject constructor(
    private val alerts: LocalAlertStore,
) : AlertRepository {

    override suspend fun all(): List<LocalPriceAlert> = alerts.current()

    override suspend fun markFired(fired: List<LocalPriceAlert>, atEpochMillis: Long) {
        alerts.markFired(fired, atEpochMillis)
    }
}

/**
 * Watchlist membership, resolved at the moment an alert is evaluated.
 *
 * `WatchlistStore` keeps a single unnamed list, so every list id an alert can carry today is
 * [AlertScope.Watchlist.DEFAULT_LIST_ID]. Any other id answers with nothing rather than with the
 * one list this app happens to have: a stored alert pointing at a list that does not exist should
 * simply never fire, and quietly redirecting it at a different list would send the reader alerts
 * about symbols they never asked about. The day there are named lists this is the one place that
 * changes.
 */
@Singleton
class WatchlistAlertMembership @Inject constructor(
    private val watchlist: WatchlistStore,
) : AlertMembership {

    /**
     * The members of the named list, resolved now rather than when the alert was written.
     *
     * By id, not by "the one that is open". `WatchlistStore.symbols` follows the **active** list
     * now that there are several, so reading it here would resolve a default-scoped alert against
     * whichever list the reader last tapped, and would answer nothing at all for an alert on any
     * other list. Both failures are silent — the alert simply never fires — which is the worst
     * shape a bug in this file can take.
     */
    override suspend fun members(listId: String): List<String> =
        runCatching { watchlist.symbols(listId).first() }.getOrDefault(emptyList())
}

/**
 * The audit log, behind the port the evaluator writes through.
 *
 * Builds its own [AlertAuditStore] over the shared preferences file rather than taking one from the
 * graph, because the store holds no state of its own — everything it knows is in the DataStore — so
 * two instances over one file are the same store. [store] is exposed so that a screen wanting to
 * *show* an alert's history reads the same log this writes, rather than constructing a third.
 */
@Singleton
class PreferencesAlertAuditLog @Inject constructor(
    dataStore: DataStore<Preferences>,
) : AlertAuditLog {

    /** The log itself, for a reader that wants to display it. */
    val store: AlertAuditStore = AlertAuditStore(dataStore)

    override suspend fun record(entries: List<AlertAuditEntry>) {
        store.recordAll(entries)
    }
}
