package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.AlertTriggerCodec
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Price alerts that belong to this phone rather than to an account.
 *
 * One row per alert, fields separated by characters no field can contain, all of it in one
 * preference. It is a small, closed format for at most [LocalPriceAlert.MAX_ALERTS] rows and it has
 * one property that matters more than elegance: **decoding cannot throw**. A malformed row — from a
 * half-written file, or a format a later release changed — is dropped and the rest are kept. An
 * alert screen that crashed on its own stored value would be unreachable without clearing the app's
 * data, and the reader would lose every other alert to fix one.
 */
class LocalAlertStore(private val dataStore: DataStore<Preferences>) {

    val alerts: Flow<List<LocalPriceAlert>> = dataStore.data.map { preferences ->
        decode(preferences[ALERTS])
    }

    suspend fun current(): List<LocalPriceAlert> = alerts.first()

    /**
     * Adds one, and returns whether there was room.
     *
     * False rather than silently dropping the oldest: the reader chose every one of these, and an
     * app that quietly discards a choice to make room for another is worse than one that says it
     * is full.
     */
    suspend fun add(alert: LocalPriceAlert): Boolean {
        var added = false
        dataStore.edit { preferences ->
            val existing = decode(preferences[ALERTS])
            if (existing.size >= LocalPriceAlert.MAX_ALERTS) return@edit
            preferences[ALERTS] = encode(existing + alert)
            added = true
        }
        return added
    }

    /**
     * Replaces the alert carrying this id, or stores it where the list holds no such alert.
     *
     * An edit used to be a [remove] followed by an [add]. That is correct and it writes the whole
     * preference twice, the second write existing only to put back a row the first one took out —
     * and between the two there is a moment in which the reader's alert is not stored at all, which
     * the background evaluator reads this same file across.
     *
     * The answer is [add]'s, and for [add]'s reason: false where a genuinely new alert does not
     * fit, because the reader chose every one of these and an app that quietly drops one to make
     * room is worse than one that says it is full. Replacing an alert that is already here can
     * never be refused, since the row it occupies is one this list already holds.
     */
    suspend fun upsert(alert: LocalPriceAlert): Boolean {
        var written = false
        dataStore.edit { preferences ->
            val existing = decode(preferences[ALERTS])
            val at = existing.indexOfFirst { it.id == alert.id }
            if (at < 0 && existing.size >= LocalPriceAlert.MAX_ALERTS) return@edit
            val next = if (at < 0) {
                existing + alert
            } else {
                // In place rather than removed and appended: the list's order is the order the
                // reader made them in, and an edit is not a reason to move an alert to the end.
                existing.toMutableList().apply { set(at, alert) }
            }
            preferences[ALERTS] = encode(next)
            written = true
        }
        return written
    }

    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(decode(preferences[ALERTS]).filterNot { it.id == id })
        }
    }

    suspend fun setActive(id: String, active: Boolean) {
        update(id) { it.copy(active = active) }
    }

    /**
     * Writes back the alerts that fired.
     *
     * Takes the whole list rather than one id because the evaluator finds them in a batch, and
     * writing them one at a time would be one disk write per alert on a tick that moved several.
     */
    suspend fun markFired(fired: List<LocalPriceAlert>, atEpochMillis: Long) {
        if (fired.isEmpty()) return
        val ids = fired.mapTo(mutableSetOf(), LocalPriceAlert::id)
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(
                decode(preferences[ALERTS]).map { alert ->
                    if (alert.id in ids) alert.fired(atEpochMillis) else alert
                },
            )
        }
    }

    /**
     * Stamps the alerts that fired and leaves every one of them switched on.
     *
     * The whole difference from [markFired] is [LocalPriceAlert.fired], which deactivates an
     * [AlertRepeat.ONCE] alert because that is precisely what a one-shot means. A bar policy is a
     * different promise: an alert held to one firing per bar is meant to speak again on the next
     * bar, and the evaluator still needs somewhere to record that it has already spoken on this
     * one. Stamping such an alert through [markFired] would silence it for ever after its first
     * bar — the alert would look armed on the screen and never fire again — so this writes the
     * timestamp and leaves `active` exactly as the reader left it.
     */
    suspend fun markFiredKeepingActive(fired: List<LocalPriceAlert>, atEpochMillis: Long) {
        if (fired.isEmpty()) return
        val ids = fired.mapTo(mutableSetOf(), LocalPriceAlert::id)
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(
                decode(preferences[ALERTS]).map { alert ->
                    if (alert.id in ids) alert.copy(lastFiredAtEpochMillis = atEpochMillis) else alert
                },
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(ALERTS) }
    }

    private suspend fun update(id: String, transform: (LocalPriceAlert) -> LocalPriceAlert) {
        dataStore.edit { preferences ->
            preferences[ALERTS] = encode(
                decode(preferences[ALERTS]).map { if (it.id == id) transform(it) else it },
            )
        }
    }

    internal companion object {
        val ALERTS = stringPreferencesKey("local_price_alerts")

        /**
         * A semicolon between rows and a vertical bar between fields.
         *
         * Neither can appear in a ticker, in a number Kotlin prints, or in the ids this app
         * generates — which are hexadecimal. Both are printable, which matters when the next person
         * to debug this reads the value out of a preferences file by eye.
         */
        private const val ROW = ";"
        private const val FIELD = "|"

        /**
         * The first nine fields, in the order the first version of this format wrote them.
         *
         * A row shorter than this is not an alert and is dropped. Everything after them was added
         * later and every one of them has a default, which is what makes an old row still readable.
         */
        private const val ORIGINAL_FIELDS = 9

        fun encode(alerts: List<LocalPriceAlert>): String = alerts.joinToString(ROW) { alert ->
            listOf(
                alert.id,
                alert.symbol,
                alert.condition.id,
                alert.value.toString(),
                alert.repeat.id,
                alert.referencePrice?.toString().orEmpty(),
                if (alert.active) "1" else "0",
                alert.createdAtEpochMillis.toString(),
                alert.lastFiredAtEpochMillis?.toString().orEmpty(),
                // Everything below this line was added after the format shipped. Appended rather
                // than inserted, always, so that a row written by an older version still parses
                // field-for-field and simply stops early.
                AlertTriggerCodec.encode(alert.trigger),
                AlertScope.encode(alert.scope),
                alert.frequency?.id.orEmpty(),
                alert.expiresAt?.toString().orEmpty(),
                AlertChannel.encode(alert.channels),
                alert.soundLevel.toString(),
                DelimitedText.escape(alert.message.orEmpty()),
            ).joinToString(FIELD)
        }

        fun decode(raw: String?): List<LocalPriceAlert> = raw
            .orEmpty()
            .split(ROW)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(FIELD)
                if (parts.size < ORIGINAL_FIELDS) return@mapNotNull null
                val id = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val symbol = parts[1].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val condition = LocalAlertCondition.fromId(parts[2]) ?: return@mapNotNull null
                val value = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                LocalPriceAlert(
                    id = id,
                    symbol = symbol,
                    condition = condition,
                    value = value,
                    repeat = AlertRepeat.fromId(parts[4]) ?: AlertRepeat.ONCE,
                    referencePrice = parts[5].toDoubleOrNull(),
                    active = parts[6] == "1",
                    createdAtEpochMillis = parts[7].toLongOrNull() ?: 0L,
                    lastFiredAtEpochMillis = parts[8].toLongOrNull(),
                    // getOrNull, not indexing: a row from before any of these existed has nine
                    // fields, and every one of these has a default that means "as it was".
                    trigger = AlertTriggerCodec.decode(parts.getOrNull(9)),
                    scope = AlertScope.decode(parts.getOrNull(10)),
                    frequency = AlertFrequency.fromId(parts.getOrNull(11)),
                    expiresAt = parts.getOrNull(12)?.toLongOrNull(),
                    channels = AlertChannel.decode(parts.getOrNull(13)) ?: AlertChannel.DEFAULTS,
                    soundLevel = parts.getOrNull(14)
                        ?.toFloatOrNull()
                        ?.let(AlertSound::coerce)
                        ?: AlertSound.DEFAULT_LEVEL,
                    message = parts.getOrNull(15)
                        ?.takeIf(String::isNotBlank)
                        ?.let(DelimitedText::unescape),
                )
            }
    }
}

/**
 * Escaping for the one field in this file's format that a reader can type into.
 *
 * ### Why only one field needs it
 *
 * Every other field is a ticker, a hexadecimal id, a number Kotlin printed, or a key this app
 * chose, and none of those can contain a separator. An alert's message is prose the reader wrote,
 * and there is nothing to stop them typing a semicolon in it. Unescaped, that one character would
 * split their alert into two rows, the second of which would decode as rubbish and be dropped — so
 * a message with a semicolon in it would silently delete the alert it belonged to.
 *
 * ### Why escaping rather than stripping
 *
 * Stripping is easier and it is the wrong trade. The reader typed the character; an app that
 * quietly edits somebody's note is doing something worse than storing it awkwardly. Four
 * substitutions, a backslash to escape the backslash, and the reader gets back exactly what they
 * wrote.
 *
 * [unescape] never throws and never rejects. A trailing lone backslash — which nothing here writes,
 * but a truncated file could produce — is kept as a backslash rather than treated as an error,
 * because a slightly wrong note is recoverable and a crash on the alerts screen is not.
 */
internal object DelimitedText {

    private const val ESCAPE = '\\'

    /** The stored form of a piece of free text, safe to place in a field of a delimited row. */
    fun escape(text: String): String = buildString(text.length) {
        text.forEach { character ->
            when (character) {
                ESCAPE -> append("\\\\")
                ';' -> append("\\s")
                '|' -> append("\\p")
                '\u001F' -> append("\\u")
                '\u001E' -> append("\\r")
                else -> append(character)
            }
        }
    }

    /** Exactly what [escape] was given, for anything [escape] wrote. */
    fun unescape(text: String): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character != ESCAPE || index == text.length - 1) {
                append(character)
                index++
                continue
            }
            when (val next = text[index + 1]) {
                ESCAPE -> append(ESCAPE)
                's' -> append(';')
                'p' -> append('|')
                'u' -> append('\u001F')
                'r' -> append('\u001E')
                else -> append(character).append(next)
            }
            index += 2
        }
    }
}
