package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AuditEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What actually happened to each alert.
 *
 * ### Why an app keeps a log of its own notifications
 *
 * Because the failure mode of an alert is silence, and silence is indistinguishable from nothing
 * having happened. Somebody who sets an alert stops watching the chart — that is the entire point
 * of setting one — so when a notification is not delivered they do not learn that they were not
 * told. They learn, later, that the move happened without them, and they have no way to tell
 * whether the app failed, whether Android dropped it while the phone was dozing, or whether they
 * set the condition wrong in the first place. "Alerts not working" is the second thing the largest
 * chart app's users type into a search box about its alerts, and it is that phrase precisely because
 * nobody can say anything more specific: there is nothing to look at.
 *
 * This is the thing to look at. Every alert can be opened and read as a history — made then,
 * armed, fired at this price on this timeframe, delivered, or *not* delivered and here is why. It
 * turns an argument into a record.
 *
 * ### Five hundred, newest first
 *
 * Newest first because the question is always about the last few hours, and a reader should not
 * scroll a year to reach today. Five hundred because this is the same delimited preference the
 * alerts themselves live in — read whole, parsed whole, written whole — and an unbounded log in
 * that shape becomes a cold-start cost that everybody pays for a screen almost nobody opens. Five
 * hundred entries is months of ordinary use and still a small string.
 *
 * The trim is oldest-out, and it is honest about it: [MAX_ENTRIES] is documented on the constant so
 * that a screen showing a full log can say the older lines are gone rather than implying the alert
 * has no earlier history.
 *
 * ### Kept after the alert is deleted
 *
 * A [AuditEvent.DELETED] entry is written and the rest of that alert's history stays. "Why did I
 * stop getting these" is a question about an alert that no longer exists, and an audit log that
 * erases itself along with its subject cannot answer it. [removeFor] exists for a reader who
 * explicitly asks to forget one, which is a different thing from deleting the alert.
 *
 * Decoding cannot throw, for the same reason it cannot in [LocalAlertStore]: this is read by a
 * screen, and a screen that crashes on its own stored value is unreachable without clearing the
 * app's data.
 */
class AlertAuditStore(private val dataStore: DataStore<Preferences>) {

    /** The whole log, newest first, across every alert. */
    val entries: Flow<List<AlertAuditEntry>> = dataStore.data.map { preferences ->
        decode(preferences[ENTRIES])
    }

    /**
     * One alert's history, newest first.
     *
     * Filtered from the same preference rather than stored per alert, because the log is written
     * far more often than it is read and one string is one write. At five hundred entries the
     * filter is not worth optimising away.
     */
    fun entriesFor(alertId: String): Flow<List<AlertAuditEntry>> =
        entries.map { all -> all.filter { it.alertId == alertId } }

    /** The whole log as it stands now, for a caller that wants one reading rather than a stream. */
    suspend fun current(): List<AlertAuditEntry> = entries.first()

    /**
     * Writes one line.
     *
     * Suspending because it is a disk write, and called from the evaluator rather than from a
     * screen. The caller supplies [AlertAuditEntry.at]; nothing here reads a clock, so a delivery
     * failure recorded during a retry can be stamped with when it actually happened.
     */
    suspend fun record(entry: AlertAuditEntry) {
        dataStore.edit { preferences ->
            preferences[ENTRIES] = encode(prepend(decode(preferences[ENTRIES]), entry))
        }
    }

    /** Records several at once, newest last in the list, in one write. */
    suspend fun recordAll(newEntries: List<AlertAuditEntry>) {
        if (newEntries.isEmpty()) return
        dataStore.edit { preferences ->
            preferences[ENTRIES] = encode(
                newEntries.fold(decode(preferences[ENTRIES])) { log, entry -> prepend(log, entry) },
            )
        }
    }

    /** Forgets one alert's history, for a reader who asked to. Does not touch any other alert's. */
    suspend fun removeFor(alertId: String) {
        dataStore.edit { preferences ->
            preferences[ENTRIES] = encode(
                decode(preferences[ENTRIES]).filterNot { it.alertId == alertId },
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ENTRIES) }
    }

    internal companion object {
        val ENTRIES = stringPreferencesKey("alert_audit_log")

        /** The same two separators [LocalAlertStore] uses, so the two formats read alike by eye. */
        private const val ROW = ";"
        private const val FIELD = "|"

        /**
         * How many lines the log keeps.
         *
         * The reasoning is in this class's own documentation, and the number is here so that a
         * screen can quote it when it tells the reader the log has been trimmed.
         */
        const val MAX_ENTRIES = 500

        /**
         * The log with one more line in it, newest first and trimmed to [MAX_ENTRIES].
         *
         * Pure and separate from the write so that the trim can be tested at its boundary without a
         * DataStore. Prepending rather than appending-and-reversing keeps the stored order the same
         * as the read order, so nothing has to sort five hundred rows on every open.
         */
        fun prepend(existing: List<AlertAuditEntry>, entry: AlertAuditEntry): List<AlertAuditEntry> =
            (listOf(entry) + existing).take(MAX_ENTRIES)

        fun encode(entries: List<AlertAuditEntry>): String =
            entries.take(MAX_ENTRIES).joinToString(ROW) { entry ->
                listOf(
                    entry.alertId,
                    entry.event.id,
                    entry.at.toString(),
                    entry.symbol,
                    entry.price?.toString().orEmpty(),
                    entry.timeframe.orEmpty(),
                    // The only field a reader can type into, and the only one that can contain a
                    // separator. Escaped rather than stripped; see DelimitedText.
                    DelimitedText.escape(entry.note.orEmpty()),
                ).joinToString(FIELD)
            }

        fun decode(raw: String?): List<AlertAuditEntry> = raw
            .orEmpty()
            .split(ROW)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(FIELD)
                if (parts.size < FIELDS) return@mapNotNull null
                val alertId = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                // An event this version does not know is a row from a later release. Dropping the
                // line is right: showing it as some other event would be worse than not showing it.
                val event = AuditEvent.fromId(parts[1]) ?: return@mapNotNull null
                val at = parts[2].toLongOrNull() ?: return@mapNotNull null
                AlertAuditEntry(
                    alertId = alertId,
                    event = event,
                    at = at,
                    symbol = parts[3],
                    price = parts[4].toDoubleOrNull(),
                    timeframe = parts[5].takeIf(String::isNotBlank),
                    note = parts[6].takeIf(String::isNotBlank)?.let(DelimitedText::unescape),
                )
            }
            .take(MAX_ENTRIES)

        /** Every field this format writes. A shorter row is half-written and is dropped. */
        private const val FIELDS = 7
    }
}
