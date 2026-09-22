package com.coinepro.core.database

import com.coinepro.core.common.ArchiveFields
import com.coinepro.core.common.ArchiveFields.field

/**
 * A journal entry as one string, for the reader's archive — and back again as the entry itself.
 *
 * ### Why this is not the CSV the screen already exports
 *
 * The journal screen offers a spreadsheet export, and that is for reading: it flattens, it quotes,
 * and a note containing a comma and a newline comes out of Excel looking approximately right. This
 * is for **restoring**, and approximately right is the one thing a restore may not be. A note that
 * comes back with its last line folded into the next entry's is a record the reader will believe,
 * because they will not remember exactly what they wrote — which is why they wrote it down.
 *
 * So every field is length-prefixed ([ArchiveFields]) and nothing is escaped, quoted or dropped.
 * Whatever the reader typed is what comes back, including the control characters a paste from a
 * chat app brings with it.
 *
 * ### The id is deliberately not carried
 *
 * It is a Room autoincrement, meaningful only inside the database it came from. Writing it would
 * invite an import to restore an entry *over* an unrelated one on the new phone. An imported entry
 * is a new row; what identifies it to a reader is its symbol and the moment it was written, which
 * [JournalArchiveCodec.sameEntry] uses so a backup imported twice does not double the journal.
 */
object JournalArchiveCodec {

    /**
     * The field order. Appending is safe — a build that has not heard of a later field reads the
     * ones it knows — and reordering is not, so new fields go on the end.
     */
    private const val SYMBOL = 0
    private const val BUY = 1
    private const val ENTRY = 2
    private const val EXIT = 3
    private const val SIZE = 4
    private const val PNL = 5
    private const val EMOTION = 6
    private const val NOTE = 7
    private const val LESSON = 8
    private const val TAGS = 9
    private const val CREATED_AT = 10

    fun encode(entry: JournalEntryEntity): String = ArchiveFields.write(
        listOf(
            entry.symbol,
            if (entry.buy) "1" else "0",
            // Empty rather than a zero: the journal's prices are nullable on purpose, because
            // «took the EURUSD long, was impatient» is the part that matters and demanding four
            // numbers is how a journal stops being kept. A zero would restore as a real price.
            entry.entry.orEmptyNumber(),
            entry.exit.orEmptyNumber(),
            entry.size.orEmptyNumber(),
            entry.pnl.orEmptyNumber(),
            entry.emotion,
            entry.note,
            entry.lesson,
            entry.tags,
            entry.createdAtEpochMillis.toString(),
        ),
    )

    /**
     * The entry in [record], or null when it is not one.
     *
     * Null on a blank symbol as well as on a malformed block: an entry with nothing to say which
     * market it is about cannot be shown in a list a reader reads backwards through time, and
     * restoring it would put a row on their journal that they cannot act on or delete by name.
     */
    fun decode(record: String): JournalEntryEntity? {
        val fields = ArchiveFields.read(record)
        if (fields.isEmpty()) return null
        val symbol = fields.field(SYMBOL).trim()
        if (symbol.isEmpty()) return null
        return JournalEntryEntity(
            symbol = symbol,
            buy = fields.field(BUY) != "0",
            entry = fields.field(ENTRY).toDoubleOrNull(),
            exit = fields.field(EXIT).toDoubleOrNull(),
            size = fields.field(SIZE).toDoubleOrNull(),
            pnl = fields.field(PNL).toDoubleOrNull(),
            emotion = fields.field(EMOTION),
            note = fields.field(NOTE),
            lesson = fields.field(LESSON),
            tags = fields.field(TAGS),
            createdAtEpochMillis = fields.field(CREATED_AT).toLongOrNull() ?: 0L,
        )
    }

    /**
     * The name this entry carries in an archive: what a reader would call it if asked.
     *
     * The archive keys records by name, and two entries with the same name would silently become
     * one — so this is the symbol **and** the instant it was written, which together are unique
     * unless the reader filed two notes on one market in the same millisecond.
     */
    fun nameOf(entry: JournalEntryEntity): String = "${entry.symbol} ${entry.createdAtEpochMillis}"

    /** Whether two entries are the same record, for an import that must not double a journal. */
    fun sameEntry(a: JournalEntryEntity, b: JournalEntryEntity): Boolean =
        a.symbol == b.symbol && a.createdAtEpochMillis == b.createdAtEpochMillis

    private fun Double?.orEmptyNumber(): String = this?.toString().orEmpty()
}
