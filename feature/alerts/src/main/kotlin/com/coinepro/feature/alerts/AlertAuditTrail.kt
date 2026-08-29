package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * One line the log is about to gain, decided before anything is written.
 *
 * A value rather than a call into the store, so the *decision* — whether this save is worth a line
 * at all, and which line — is a pure function that a test can hold. The store's own test covers
 * encoding; what has never been covered anywhere is the judgement above it.
 */
data class AlertAuditWrite(val event: AuditEvent, val note: String? = null)

/**
 * What an alert's own history says about the reader, rather than about the market.
 *
 * ### The log used to begin at the first firing
 *
 * `AuditEvent` has nine cases and the evaluator wrote four of them: fired, delivered, not
 * delivered, expired. Everything the *reader* did — making the alert, changing it, switching it
 * off, deleting it — was written nowhere, so an alert's history opened at the moment it first
 * spoke and «کِی ساخته شد» was a question the sheet could not answer. Worse, the one question the
 * sheet exists for — "why did this fire at three in the morning" — usually has an answer of the
 * form "because it was edited on Tuesday", and the Tuesday was not recorded.
 *
 * This is the missing half. It decides nothing about *when* to write; [AlertsController] does that
 * at the moment the reader acts. What is here is the part that has to be judged: whether a save
 * changed anything, and in what words.
 *
 * ### A save that changed nothing writes nothing
 *
 * The editor is opened far more often to *read* an alert than to change one — it is the only place
 * the full condition, the channels and the loudness are visible at once — and «ذخیره» is how a
 * reader closes it. A log that gained «ویرایش شد» every time somebody looked at an alert would bury
 * the two lines that matter under a column of identical ones, which is the same failure as having
 * no log: nobody reads it. So the comparison below is against what the reader can actually see and
 * choose, and an identical save is silent.
 *
 * ### Compared as sentences, not as fields
 *
 * The condition is compared through [AlertSentence] rather than field by field, because the store
 * holds two shapes of the same alert. Every alert written before `AlertTrigger` existed carries a
 * flat `condition` and no trigger, and re-saving one gives it a trigger for the first time — a real
 * change to the row on disk, and no change whatsoever to what the alert waits for. A field
 * comparison calls that an edit and writes «شرط تغییر کرد» under an alert nobody touched. The
 * rendered predicate is what the reader was shown, so comparing that answers the reader's question
 * instead of the storage layer's.
 *
 * The same reasoning covers the rest: the scope through [LocalPriceAlert.effectiveScope], the
 * repeat policy through the frequency the editor actually offers, and the loudness through the
 * three named steps [AlertLoudness] gives — a stored 0.68 and a stored 0.70 are the same position
 * of the same control, and reporting them as a change would be reporting arithmetic.
 */
object AlertAuditTrail {

    /**
     * The line a save deserves, or null where it deserves none.
     *
     * [previous] is null for a new alert, and a new alert is always [AuditEvent.CREATED] with no
     * note — at the instant it is made, the sheet's own subtitle already says what it waits for, and
     * a note repeating it would be the first line of the log arguing with the heading above it.
     *
     * For an existing one there are three answers and they are ordered by what the reader would
     * want to read:
     *
     * * something they chose is different → [AuditEvent.EDITED], with the note naming which things.
     * * nothing they chose is different, but the alert is now armed when it was not →
     *   [AuditEvent.ARMED]. This is the paused alert re-saved, and the spent one-shot re-saved:
     *   `AlertDraft.toAlert` re-arms both on purpose, and without this line the alert starts
     *   speaking again with nothing in its history to say why.
     * * neither → nothing at all.
     *
     * One line per save rather than an edit *and* an arming for an edit that did both. They happen
     * in the same millisecond, so the sheet — which orders oldest first — would have to break the
     * tie by insertion order to keep them the right way up, and a log whose meaning depends on the
     * stability of a sort is a log that will read backwards one day. The note on the edit is where
     * the detail belongs anyway.
     */
    fun save(previous: LocalPriceAlert?, next: LocalPriceAlert): AlertAuditWrite? {
        if (previous == null) return AlertAuditWrite(AuditEvent.CREATED)
        val changed = changes(previous, next)
        if (changed.isNotEmpty()) return AlertAuditWrite(AuditEvent.EDITED, phrase(changed))
        if (rearms(previous, next)) return AlertAuditWrite(AuditEvent.ARMED)
        return null
    }

    /**
     * The things the reader changed, named as they are named in the sheet, in the sheet's order.
     *
     * Empty means the two alerts are the same alert as far as anybody looking at them can tell.
     * Public because it is the assertion a test wants: pinning the *note string* would pin the
     * wording of a sentence, and pinning the list pins the behaviour.
     */
    fun changes(previous: LocalPriceAlert, next: LocalPriceAlert): List<String> = buildList {
        if (previous.symbol != next.symbol) add(SYMBOL)
        if (previous.effectiveScope != next.effectiveScope) add(SCOPE)
        if (predicateOf(previous) != predicateOf(next)) add(CONDITION)
        if (frequencyOf(previous) != frequencyOf(next)) add(FREQUENCY)
        if (previous.channels != next.channels) add(CHANNELS)
        if (loudnessOf(previous) != loudnessOf(next)) add(LOUDNESS)
        if (previous.message.orEmpty().trim() != next.message.orEmpty().trim()) add(MESSAGE)
    }

    /**
     * Whether this save put the alert back on watch without changing what it watches for.
     *
     * Two ways in, and they are the same event to a reader: an alert they had switched off, and an
     * alert that had already fired its one shot. `AlertDraft.toAlert` clears both — see its own
     * reasoning — so a save is the moment either becomes eligible again.
     */
    fun rearms(previous: LocalPriceAlert, next: LocalPriceAlert): Boolean =
        next.active && (!previous.active || previous.lastFiredAtEpochMillis != null)

    /**
     * «شرط و تکرار تغییر کرد» — the changed things as one clause.
     *
     * Persian joins a list with «، » and the last pair with « و », and the verb stays singular
     * whatever the count, which is why nothing here is pluralised and why no number appears: a
     * count would be prose and would have to be in Persian digits, and «۳ مورد تغییر کرد» tells the
     * reader strictly less than naming the three.
     */
    private fun phrase(changed: List<String>): String {
        val subject = if (changed.size == 1) {
            changed.single()
        } else {
            changed.dropLast(1).joinToString(SEPARATOR) + LAST + changed.last()
        }
        return "$subject تغییر کرد"
    }

    /** What the alert waits for, as the reader was shown it. See the class note for why. */
    private fun predicateOf(alert: LocalPriceAlert): String =
        alert.trigger?.let(AlertSentence::predicate)
            ?: AlertSentence.predicate(alert.condition, alert.value)

    /**
     * The repeat policy in the units the editor speaks.
     *
     * An alert stored before frequencies existed has only [LocalPriceAlert.repeat]; the editor
     * shows it as the nearest frequency and writes that back, so comparing the raw fields would
     * report a change on every first re-save of an older alert.
     */
    private fun frequencyOf(alert: LocalPriceAlert): AlertFrequency =
        alert.frequency ?: alert.repeat.asFrequency()

    /** The named step the loudness control is on, not the float underneath it. */
    private fun loudnessOf(alert: LocalPriceAlert): AlertLoudness =
        AlertLoudness.of(alert.effectiveSoundLevel)

    /**
     * The vocabulary of a change, in Kotlin rather than in `strings.xml`.
     *
     * The same split `AlertVocabulary` documents and for the same reason: these are fragments
     * assembled into one sentence by [phrase], and held as seven separate resources they would be
     * translated by somebody who never sees the sentence they land in. What a *screen* says is a
     * resource; what the domain calls its own parts is here, where the test that asserts the
     * sentence can reach it without a `Context`.
     */
    private const val SYMBOL = "نماد"
    private const val SCOPE = "دامنه"
    private const val CONDITION = "شرط"
    private const val FREQUENCY = "تکرار"
    private const val CHANNELS = "کانال‌ها"
    private const val LOUDNESS = "صدا"
    private const val MESSAGE = "پیام"

    private const val SEPARATOR = "، "
    private const val LAST = " و "
}

/**
 * One entry about the reader rather than about the market.
 *
 * [AlertAuditEntry.price] is left out of every one of these on purpose, and the omission is the
 * honest answer rather than a gap: the entry's own documentation says a recorded price is what the
 * market was doing at that instant, and none of these events is a reading of the market. Somebody
 * deleting an alert on the underground is not looking at a price, and writing whatever the last
 * poll happened to hold would put a number in the log that never had anything to do with the line
 * it sits on.
 *
 * The timeframe is different and is kept: it is a property of the alert, not of the moment, so it
 * is as true when the alert is made as it is when it fires.
 */
internal fun AlertAuditWrite.entryFor(
    alert: LocalPriceAlert,
    at: Long,
    timeframe: String?,
): AlertAuditEntry = AlertAuditEntry(
    alertId = alert.id,
    event = event,
    at = at,
    symbol = alert.symbol,
    price = null,
    timeframe = timeframe,
    note = note,
)
