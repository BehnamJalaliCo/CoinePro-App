package com.coinepro.core.journal

import com.coinepro.core.database.JournalEntryEntity
import kotlin.math.abs

/**
 * What a set of journal entries adds up to.
 *
 * Ported from the web terminal's journal statistics, with one rule enforced throughout that the
 * original left implicit: **an entry with no recorded P&L is not a break-even trade, it is a trade
 * with no recorded P&L.** It counts towards nothing here — not the wins, not the losses, not the
 * denominator of the win rate. A journal is kept in a hurry and half its rows will have a note and
 * no number; treating those as zeros would quietly halve every percentage on the screen.
 */
data class JournalStats(
    /** Entries carrying a P&L. Every figure below is over these, not over the whole journal. */
    val graded: Int,
    val wins: Int,
    val losses: Int,
    val netPnl: Double,
    val grossProfit: Double,
    val grossLoss: Double,
    val best: Double?,
    val worst: Double?,
) {
    /** Null on an empty set: a win rate over nothing is not zero per cent. */
    val winRate: Double? get() = if (graded == 0) null else wins * 100.0 / graded

    /**
     * Gross profit over gross loss.
     *
     * Null where there is no loss to divide by, rather than infinity. A journal of three winners
     * has not achieved an infinite profit factor; it has not yet produced one.
     */
    val profitFactor: Double? get() = if (grossLoss == 0.0) null else grossProfit / grossLoss

    /** Average outcome per graded trade. The number that says whether the habit pays. */
    val expectancy: Double? get() = if (graded == 0) null else netPnl / graded

    val averageWin: Double? get() = if (wins == 0) null else grossProfit / wins
    val averageLoss: Double? get() = if (losses == 0) null else grossLoss / losses
}

object Journal {

    fun stats(entries: List<JournalEntryEntity>): JournalStats {
        val graded = entries.mapNotNull(JournalEntryEntity::pnl).filter(Double::isFinite)
        // Zero is neither a win nor a loss. It is a scratch, and counting it either way moves a
        // win rate the reader will quote at themselves for months.
        val wins = graded.filter { it > 0 }
        val losses = graded.filter { it < 0 }
        return JournalStats(
            graded = graded.size,
            wins = wins.size,
            losses = losses.size,
            netPnl = graded.sum(),
            grossProfit = wins.sum(),
            grossLoss = abs(losses.sum()),
            best = graded.maxOrNull(),
            worst = graded.minOrNull(),
        )
    }

    /** Every tag used, most-used first. What the reader actually trades, in their own words. */
    fun tags(entries: List<JournalEntryEntity>): List<Pair<String, Int>> = entries
        .flatMap { it.tags.split(",") }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }

    /**
     * The journal as a spreadsheet.
     *
     * Comma-separated with a UTF-8 byte-order mark, which is the web terminal's choice and the
     * right one: without the mark Excel opens a Persian CSV as mojibake, and a reader who exports
     * their own trading diary and gets `Ø¨ÛŒØªÚ©ÙˆÛŒÙ†` will not export it twice.
     *
     * Fields are quoted and inner quotes doubled, because a lesson written after a bad trade is
     * exactly where a comma appears.
     */
    fun toCsv(entries: List<JournalEntryEntity>): String {
        val header = listOf(
            "date_epoch_ms", "symbol", "side", "entry", "exit", "size", "pnl",
            "emotion", "tags", "note", "lesson",
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
            )
        }
        return BOM + (listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { field -> "\"" + field.replace("\"", "\"\"") + "\"" }
        }
    }

    /** The six the web terminal offers, in Persian, and deliberately not free text — see below. */
    val EMOTIONS = listOf("آرام", "ترس", "طمع", "انتقام", "بی‌صبری", "مطمئن")

    /**
     * Suggested tags, from the web terminal's set.
     *
     * Suggestions rather than a fixed vocabulary: the reader can type anything. But the suggestions
     * matter more than they look, because a journal whose tags are all typed by hand ends up with
     * "بریک اوت", "بریک‌اوت" and "breakout" as three different setups, and the grouping — the only
     * reason to tag at all — quietly stops working.
     */
    val SUGGESTED_TAGS = listOf(
        "بریک‌اوت", "ریتست", "پولبک", "رنج", "ترند",
        "حمایت", "مقاومت", "دایورجنس", "نیوز", "اسکالپ", "سوینگ", "اوردرفلو",
    )

    private const val BOM = "﻿"
}
