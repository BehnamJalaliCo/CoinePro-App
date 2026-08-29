package com.coinepro.feature.journal

import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.database.JournalEntryEntity
import com.coinepro.core.journal.Journal
import com.coinepro.core.journal.JournalStats
import java.util.Locale

/** What a journal entry did, as a filter rather than as a fact about the trade. */
enum class JournalOutcome {
    ANY,
    WINS,
    LOSSES,

    /**
     * Entries with no recorded profit or loss.
     *
     * Worth filtering to on its own, and the reason is the habit rather than the statistics: these
     * are the rows written in a hurry and never finished, and the only way a reader finds them
     * again is by asking for them.
     */
    UNGRADED,
}

/**
 * Whether an entry has a chart attached to it.
 *
 * A dimension of the filter rather than a badge on the row, and that is the point of it. The
 * screenshot slot has existed since the journal did, and until now nothing could *ask* about it:
 * a reader who wanted «معامله‌هایی که عکسشان را دارم» had to scroll and look, and the statistics
 * above the list could not be computed over that set at all because no filter could express it.
 *
 * [WITHOUT] is the more useful half in practice. It is the list of entries that are still missing
 * the one thing that makes a note readable six weeks later, which is the chart it is about.
 */
enum class JournalShot {
    ANY,
    WITH,
    WITHOUT,
}

/**
 * What the reader is currently looking at.
 *
 * ### Why this is not in the controller
 *
 * `JournalController` already carries a single-tag filter, and this screen deliberately does not
 * use it. One filter object has to own the whole question, because the statistics are computed over
 * whatever it selects — and a filter split across two layers is a filter that will eventually
 * disagree with itself, showing a win rate over one subset above a list showing another. The
 * controller's version also cannot express «بریک‌اوت» *and* «ریتست» together, which is what pressing
 * a second chip plainly means.
 *
 * ### The rule the whole feature turns on
 *
 * **The statistics are over the filtered subset, never over the whole journal.** A reader who taps
 * «ریتست» is asking what their retests do. A win rate underneath that answer which quietly stayed at
 * the journal-wide figure is not a smaller mistake than a wrong number — it is a wrong number that
 * looks like an answer to the question just asked, and a journal whose statistics ignore the filter
 * is a journal nobody has any reason to trust. [statsOf] exists so the subset and the figures over
 * it can only ever be computed from the same list.
 */
data class JournalFilter(
    /**
     * Tags that must **all** be present, not any of them.
     *
     * Intersection rather than union, because adding a second chip is universally read as narrowing
     * the list. A union would widen it, so the reader's list would grow as they filtered it — which
     * reads as a bug even to somebody who wanted a union.
     */
    val tags: Set<String> = emptySet(),
    /** Free text over the symbol, the note, the lesson, the mood and the tags. */
    val query: String = "",
    val outcome: JournalOutcome = JournalOutcome.ANY,
    /** Whether the list is narrowed to the entries that do — or do not — carry a screenshot. */
    val shot: JournalShot = JournalShot.ANY,
) {
    /** True when nothing is being filtered, so the screen can say "everything" rather than "0 of 0". */
    val isEverything: Boolean
        get() = tags.isEmpty() && query.isBlank() &&
            outcome == JournalOutcome.ANY && shot == JournalShot.ANY

    /** Tapping a chip that is already on turns it off. There is no other way back to everything. */
    fun toggling(tag: String): JournalFilter =
        copy(tags = if (tag in tags) tags - tag else tags + tag)

    /**
     * Narrow a list of entries.
     *
     * Order is preserved — the DAO already hands them over newest first, and re-sorting a filtered
     * journal would move rows the reader was looking at.
     *
     * @param withShot the ids of the entries that have a screenshot attached. It is a parameter
     *   rather than something read off the entry because the picture is *not* stored on the row —
     *   [JournalScreenshots] explains why at length — and it is required rather than defaulted
     *   because a default of "nobody has one" is a wrong answer that looks like a working filter:
     *   «فقط با تصویر» would come back empty and nothing would say why.
     */
    fun apply(
        entries: List<JournalEntryEntity>,
        withShot: Set<Long>,
    ): List<JournalEntryEntity> {
        if (isEverything) return entries
        val needle = normalise(query)
        return entries.filter { entry ->
            matchesOutcome(entry) &&
                matchesShot(entry, withShot) &&
                tags.all { it in tagsOf(entry) } &&
                matchesQuery(entry, needle)
        }
    }

    /**
     * The statistics over exactly what [apply] selects.
     *
     * Delegated to `Journal.stats` rather than reimplemented, so the filtered figures and the
     * journal-wide ones are the same arithmetic — including its rule that an entry with no recorded
     * P&L counts towards nothing rather than being averaged in as a zero.
     *
     * It takes the same [withShot] set as [apply] for one reason, and it is the reason this class
     * exists: the figures and the list have to be computed from the same call, over the same
     * arguments. A `statsOf` that could be called with a different screenshot set from the one the
     * list was built with is a win rate over a set the reader is not looking at.
     */
    fun statsOf(entries: List<JournalEntryEntity>, withShot: Set<Long>): JournalStats =
        Journal.stats(apply(entries, withShot))

    private fun matchesShot(entry: JournalEntryEntity, withShot: Set<Long>): Boolean =
        when (shot) {
            JournalShot.ANY -> true
            JournalShot.WITH -> entry.id in withShot
            JournalShot.WITHOUT -> entry.id !in withShot
        }

    private fun matchesOutcome(entry: JournalEntryEntity): Boolean {
        val pnl = entry.pnl?.takeIf { it.isFinite() }
        return when (outcome) {
            JournalOutcome.ANY -> true
            JournalOutcome.WINS -> pnl != null && pnl > 0.0
            JournalOutcome.LOSSES -> pnl != null && pnl < 0.0
            JournalOutcome.UNGRADED -> pnl == null
        }
    }

    private fun matchesQuery(entry: JournalEntryEntity, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return listOf(entry.symbol, entry.note, entry.lesson, entry.emotion, entry.tags)
            .any { normalise(it).contains(needle) }
    }

    /**
     * Case folded, digits folded to Latin, trimmed.
     *
     * The digit fold is the part that matters and the part that is easy to leave out. A Persian
     * keyboard produces ۰-۹ by default, so a reader searching for the entry they wrote about
     * `BTC` at `64000` types «۶۴۰۰۰» — which shares not one character with what is stored, and the
     * search returns nothing while looking like it worked.
     */
    private fun normalise(value: String): String =
        value.foldDigitsToLatin().lowercase(Locale.ROOT).trim()
}

/**
 * The tags on one entry, as a set.
 *
 * They are stored as one comma-separated string because a journal's tags are read as a set and
 * never joined against anything, which is a reasonable shape for the column and a bad shape for
 * every caller — so the split happens once, here, rather than at three call sites with three
 * slightly different ideas about whitespace.
 */
internal fun tagsOf(entry: JournalEntryEntity): Set<String> =
    entry.tags.split(",").map(String::trim).filter(String::isNotEmpty).toSet()
