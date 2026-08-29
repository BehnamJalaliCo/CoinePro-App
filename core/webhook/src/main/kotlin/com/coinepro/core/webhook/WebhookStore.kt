package com.coinepro.core.webhook

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The reader's webhooks, and what happened to each delivery — [142].
 *
 * ### Two keys, one store
 *
 * The targets change when a person edits them, a few times ever. The log changes every time an
 * alert fires. Keeping them in one preference would mean rewriting the whole target list on every
 * delivery, so they are two keys in the same `DataStore` — one small and stable, one append-only
 * and trimmed.
 *
 * ### The secret is stored and never leaves
 *
 * [WebhookTarget.secret] is written here because the poster needs it at fire time, and that is the
 * only place it is ever read. Nothing in this file logs it, no screen is offered it back, and no
 * [WebhookAttempt] has a field it could fit in. The encoding below refuses a target whose fields
 * contain a separator rather than sanitising them: silently rewriting somebody's secret would
 * produce a webhook that signs with a key the receiver does not have, and the only symptom would be
 * a receiver rejecting every request for no visible reason.
 *
 * ### The encoding
 *
 * The delimited scheme this app's other preference stores use, with ASCII's own separators: file
 * between records, unit between one record's fields. They are control characters, so nothing a
 * reader can type contains one. Decoding never throws and never fails a whole list — a row this
 * build cannot read is dropped and the rest survive, because losing one webhook is recoverable and
 * losing the list is not.
 */
class WebhookStore(private val dataStore: DataStore<Preferences>) {

    /** Every webhook the reader has, oldest first. */
    val targets: Flow<List<WebhookTarget>> = dataStore.data.map { preferences ->
        decodeTargets(preferences[TARGETS])
    }

    /** The whole delivery log, newest first, across every webhook. */
    val deliveries: Flow<List<WebhookAttempt>> = dataStore.data.map { preferences ->
        decodeAttempts(preferences[LOG])
    }

    /**
     * One alert's delivery history, newest first.
     *
     * Filtered from the same preference rather than stored per alert, exactly as `AlertAuditStore`
     * does and for the same reason: the log is written far more often than it is read. This is the
     * call an alert's own history sheet makes, so that «تحویل شد» and «پاسخی نرسید» sit beside the
     * notification's own audit lines.
     */
    fun deliveriesFor(alertId: String): Flow<List<WebhookAttempt>> =
        deliveries.map { all -> all.filter { it.alertId == alertId } }

    /** The webhooks as they stand now, for the poster, which wants one reading rather than a stream. */
    suspend fun currentTargets(): List<WebhookTarget> = targets.first()

    /**
     * Saves [target], replacing any with the same id.
     *
     * Refused — and returning false — when the URL is not one this app will post to, so that an
     * unusable webhook cannot be stored at all. The caller has already been told why by
     * [WebhookUrl.validate]; this is the second gate, the one that holds when a caller forgets.
     */
    suspend fun save(target: WebhookTarget): Boolean {
        if (WebhookUrl.validate(target.url) != null) return false
        if (encodeTarget(target) == null) return false
        dataStore.edit { preferences ->
            val existing = decodeTargets(preferences[TARGETS]).filterNot { it.id == target.id }
            preferences[TARGETS] = encodeTargets((existing + target).takeLast(MAX_TARGETS))
        }
        return true
    }

    /** Forgets one webhook. Its delivery history stays; see [WebhookAttempt]. */
    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            preferences[TARGETS] =
                encodeTargets(decodeTargets(preferences[TARGETS]).filterNot { it.id == id })
        }
    }

    /** Switches one webhook on or off without losing the URL the reader pasted. */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TARGETS] = encodeTargets(
                decodeTargets(preferences[TARGETS]).map {
                    if (it.id == id) it.copy(enabled = enabled) else it
                },
            )
        }
    }

    /** Writes one delivery record, newest first. */
    suspend fun record(attempt: WebhookAttempt) = recordAll(listOf(attempt))

    /** Writes several records in one edit — one alert fanning out to three webhooks is one write. */
    suspend fun recordAll(attempts: List<WebhookAttempt>) {
        if (attempts.isEmpty()) return
        dataStore.edit { preferences ->
            val existing = decodeAttempts(preferences[LOG])
            preferences[LOG] = encodeAttempts((attempts.reversed() + existing).take(MAX_LOG))
        }
    }

    /** Forgets the whole delivery log. For a reader who asks; nothing calls it on their behalf. */
    suspend fun clearLog() {
        dataStore.edit { it.remove(LOG) }
    }

    internal companion object {
        val TARGETS = stringPreferencesKey("webhook_targets")
        val LOG = stringPreferencesKey("webhook_delivery_log")

        /** ASCII file separator, between two records. */
        private const val RECORD = "\u001C"

        /** ASCII unit separator, between one record's fields. */
        private const val UNIT = "\u001F"

        private val SEPARATORS = listOf(RECORD, UNIT)

        /**
         * A fuse rather than a product limit: a caller bug must not be able to grow one preference
         * string without bound. No person keeps this many webhooks and nothing in the app presents
         * this number to a reader.
         */
        const val MAX_TARGETS = 50

        /**
         * How many delivery records are kept, newest first.
         *
         * The same five hundred `AlertAuditStore` keeps, and for the same reason: this is a whole
         * string read and written at once, so an unbounded log becomes a cold-start cost everybody
         * pays for a screen few people open. Five hundred is months of ordinary firing.
         */
        const val MAX_LOG = 500

        fun encodeTargets(targets: List<WebhookTarget>): String =
            targets.mapNotNull(::encodeTarget).joinToString(RECORD)

        /** One webhook as a row, or null when a field carries a separator and cannot be written. */
        fun encodeTarget(target: WebhookTarget): String? {
            if (target.id.isBlank()) return null
            val fields = listOf(target.id, target.name, target.url, target.secret.orEmpty())
            if (fields.any { field -> SEPARATORS.any(field::contains) }) return null
            return (fields + listOf(if (target.enabled) "1" else "0", target.createdAt.toString()))
                .joinToString(UNIT)
        }

        fun decodeTargets(raw: String?): List<WebhookTarget> = raw
            .orEmpty()
            .split(RECORD)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(UNIT)
                if (parts.size < TARGET_FIELDS) return@mapNotNull null
                val id = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                WebhookTarget(
                    id = id,
                    // A row saved with no name falls back to its URL rather than to an empty
                    // string: a list of webhooks where one has no label is a row nobody can choose.
                    name = parts[1].takeIf(String::isNotBlank) ?: parts[2],
                    url = parts[2],
                    secret = parts[3].takeIf(String::isNotBlank),
                    enabled = parts[4] != "0",
                    createdAt = parts[5].toLongOrNull() ?: 0L,
                )
            }
            .take(MAX_TARGETS)

        fun encodeAttempts(attempts: List<WebhookAttempt>): String = attempts
            .take(MAX_LOG)
            .mapNotNull(::encodeAttempt)
            .joinToString(RECORD)

        fun encodeAttempt(attempt: WebhookAttempt): String? {
            val text = listOf(
                attempt.targetId,
                attempt.targetName,
                attempt.alertId,
                attempt.error.orEmpty(),
            )
            if (text.any { field -> SEPARATORS.any(field::contains) }) return null
            return listOf(
                attempt.targetId,
                attempt.targetName,
                attempt.alertId,
                attempt.at.toString(),
                attempt.outcome.id,
                attempt.status?.toString().orEmpty(),
                attempt.latencyMillis.toString(),
                attempt.error.orEmpty(),
            ).joinToString(UNIT)
        }

        fun decodeAttempts(raw: String?): List<WebhookAttempt> = raw
            .orEmpty()
            .split(RECORD)
            .filter(String::isNotBlank)
            .mapNotNull { row ->
                val parts = row.split(UNIT)
                if (parts.size < ATTEMPT_FIELDS) return@mapNotNull null
                val at = parts[3].toLongOrNull() ?: return@mapNotNull null
                // An outcome this build does not know is a row from a later release. Dropping it is
                // right: showing it as some other outcome would be worse than not showing it.
                val outcome = WebhookOutcome.fromId(parts[4]) ?: return@mapNotNull null
                WebhookAttempt(
                    targetId = parts[0],
                    targetName = parts[1],
                    alertId = parts[2],
                    at = at,
                    outcome = outcome,
                    status = parts[5].toIntOrNull(),
                    latencyMillis = parts[6].toLongOrNull() ?: 0L,
                    error = parts[7].takeIf(String::isNotBlank),
                )
            }
            .take(MAX_LOG)

        /** Every field each format writes. A shorter row is half-written and is dropped. */
        private const val TARGET_FIELDS = 6
        private const val ATTEMPT_FIELDS = 8
    }
}
