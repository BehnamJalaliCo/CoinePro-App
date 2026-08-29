package com.coinepro.feature.journal

import com.coinepro.core.database.JournalEntryEntity

/**
 * The journal as a spreadsheet, over exactly what the screen is showing.
 *
 * ### Why this is not `Journal.toCsv`
 *
 * It is, for every column that one writes — the format, the byte-order mark and the quoting rule
 * are deliberately identical, so a reader who exported last month gets the same file shape. What
 * this adds is the one column that one cannot have: whether the entry has a **screenshot**. The
 * picture is not stored on the row — [JournalScreenshots] explains why, and the short version is
 * that a `content://` grant means nothing outside this device — so `core:journal`, which only ever
 * sees the row, has no way to know.
 *
 * ### The column is there because the export has to agree with the screen
 *
 * Once the filter can say «فقط با تصویر», a CSV that says nothing about pictures is a file that
 * cannot be reconciled with the list it came from. A reader who filters to their eleven documented
 * trades, exports, and finds eleven rows with no way to tell which had charts has been given a file
 * that only half-answers the question they asked. One column, `has_screenshot`, and the file stands
 * on its own.
 *
 * `1` and `0` rather than «بله» and «خیر»: this column is read by a spreadsheet's own filter and by
 * whatever the reader pivots it with, and a numeric flag sorts, sums and filters everywhere. The
 * prose in this app is Persian; a CSV header is not prose.
 */
object JournalExport {

    /**
     * [entries] as a CSV, with the screenshot column filled in from [withShot].
     *
     * The caller passes the same list it is showing and the same id set it filtered with. That is
     * the whole contract of this function and it is why it takes two arguments instead of reading
     * anything itself: an export that recomputed its own subset could disagree with the screen, and
     * a reader would only find that out in a spreadsheet an hour later.
     */
    fun csv(entries: List<JournalEntryEntity>, withShot: Set<Long>): String {
        val header = listOf(
            "date_epoch_ms", "symbol", "side", "entry", "exit", "size", "pnl",
            "emotion", "tags", "note", "lesson", "has_screenshot",
        )
        val rows = entries.map { entry ->
            listOf(
                entry.createdAtEpochMillis.toString(),
                entry.symbol,
                if (entry.buy) "buy" else "sell",
                entry.entry?.toString().orEmpty(),
                entry.exit?.toString().orEmpty(),
                entry.size?.toString().orEmpty(),
                entry.pnl?.toString().orEmpty(),
                entry.emotion,
                entry.tags,
                entry.note,
                entry.lesson,
                if (entry.id in withShot) "1" else "0",
            )
        }
        // Quoted throughout and inner quotes doubled, which is `Journal.toCsv`'s rule and the right
        // one: a lesson written after a bad trade is exactly where a comma turns up. The
        // byte-order mark is what stops Excel opening a Persian journal as mojibake.
        return BOM + (listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { field -> "\"" + field.replace("\"", "\"\"") + "\"" }
        }
    }

    private const val BOM = "﻿"
}
