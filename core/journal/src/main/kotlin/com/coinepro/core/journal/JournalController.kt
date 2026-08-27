package com.coinepro.core.journal

import com.coinepro.core.database.JournalDao
import com.coinepro.core.database.JournalEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JournalUiState(
    val entries: List<JournalEntryEntity> = emptyList(),
    val stats: JournalStats = Journal.stats(emptyList()),
    val tags: List<Pair<String, Int>> = emptyList(),
    /** Null shows everything. Set by tapping a tag, cleared by tapping it again. */
    val tagFilter: String? = null,
)

class JournalController(
    private val dao: JournalDao,
    private val scope: CoroutineScope,
    /** Injected so a test is not at the mercy of the wall clock. */
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val filter = MutableStateFlow<String?>(null)

    /**
     * The statistics are computed over the **filtered** set, not the whole journal.
     *
     * That is the point of the filter. A reader tapping "بریک‌اوت" is asking what their breakouts
     * do, and a win rate underneath that stayed stubbornly at the journal-wide figure would be
     * answering a different question while looking like an answer to theirs.
     */
    val state: StateFlow<JournalUiState> = combine(dao.entries(), filter) { entries, tag ->
        val shown = if (tag == null) entries else entries.filter { tag in it.tagList() }
        JournalUiState(
            entries = shown,
            stats = Journal.stats(shown),
            // The tag cloud is over everything: a filter that hid the other tags would leave the
            // reader no way back except a button they have to find.
            tags = Journal.tags(entries),
            tagFilter = tag,
        )
    }.stateIn(scope, SharingStarted.Eagerly, JournalUiState())

    fun setTagFilter(tag: String?) {
        filter.value = if (filter.value == tag) null else tag
    }

    fun add(
        symbol: String,
        buy: Boolean,
        entry: Double?,
        exit: Double?,
        size: Double?,
        pnl: Double?,
        emotion: String,
        note: String,
        lesson: String,
        tags: List<String>,
    ) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return
        scope.launch {
            dao.insert(
                JournalEntryEntity(
                    symbol = ticker,
                    buy = buy,
                    entry = entry,
                    exit = exit,
                    size = size,
                    // Never derived from entry and exit. The reader knows their fees, their swap
                    // and their partial fills; a P&L this app calculated would be confidently
                    // wrong in exactly the cases the journal exists to record.
                    pnl = pnl,
                    emotion = emotion.trim(),
                    note = note.trim(),
                    lesson = lesson.trim(),
                    tags = tags.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(","),
                    createdAtEpochMillis = now(),
                ),
            )
        }
    }

    fun delete(entry: JournalEntryEntity) {
        scope.launch { dao.delete(entry) }
    }

    fun csv(): String = Journal.toCsv(state.value.entries)
}

internal fun JournalEntryEntity.tagList(): List<String> =
    tags.split(",").map(String::trim).filter(String::isNotEmpty)
