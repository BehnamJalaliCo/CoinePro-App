package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.database.SavedScriptDao
import com.coinepro.core.database.SavedScriptEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the editor is showing.
 *
 * [result] is the last *successful or failed* run, and [dirty] says whether the text has changed
 * since. Kept apart on purpose: a reader who types one character should not have the chart they
 * were reading wiped out, so the old drawing stays until they run again — with the editor plainly
 * marked as out of date rather than pretending to be current.
 */
data class ScriptEditorState(
    val name: String = "",
    val source: String = "",
    /** The row in the database this is a copy of, or null for a script never saved. */
    val savedId: Long? = null,
    val presetId: String? = null,
    /** Reader-set values for `input(...)`, keyed by the input's title. */
    val overrides: Map<String, Double> = emptyMap(),
    val result: ScriptResult? = null,
    /** A syntax error found without running — see [NamaScript.check]. */
    val syntax: ScriptFailure? = null,
    val dirty: Boolean = false,
    val running: Boolean = false,
) {
    /** Whether saving would write anything. A blank script is not worth a row. */
    val canSave: Boolean get() = source.isNotBlank()

    /** The error to show, if any: a failed run first, then a syntax error found while typing. */
    val failure: ScriptFailure? get() = result?.error ?: syntax
}

/**
 * The script editor's state machine.
 *
 * Plain class, plain scope, no Compose — the same shape as every other controller here. Running a
 * script is synchronous and fast enough to do on the calling thread for a few hundred bars, but it
 * is a reader's own code with a node budget rather than a wall clock, so it is launched rather than
 * called: a pathological script must not be able to hold a frame.
 */
class ScriptController(
    private val dao: SavedScriptDao,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(ScriptEditorState())
    val state: StateFlow<ScriptEditorState> = _state.asStateFlow()

    val saved: StateFlow<List<SavedScriptEntity>> =
        dao.scripts().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The series the editor runs against — whatever the chart behind it is showing. */
    private var series: CandleSeries = CandleSeries.EMPTY

    fun setSeries(series: CandleSeries) {
        this.series = series
    }

    /**
     * Text changed.
     *
     * The syntax check runs on every keystroke and the script does not: parsing a few hundred lines
     * is microseconds and evaluating them over a thousand bars is not. The reader gets the caret
     * immediately and the drawing when they ask for it.
     */
    fun edit(source: String) {
        _state.update {
            it.copy(
                source = source,
                dirty = true,
                syntax = if (source.isBlank()) null else NamaScript.check(source),
            )
        }
    }

    fun rename(name: String) = _state.update { it.copy(name = name, dirty = true) }

    /** Runs the current text over the current series. */
    fun run() {
        val current = _state.value
        if (current.source.isBlank()) return
        _state.update { it.copy(running = true) }
        scope.launch {
            val result = NamaScript.run(current.source, series, current.overrides)
            _state.update { old ->
                old.copy(
                    result = result,
                    running = false,
                    dirty = false,
                    syntax = null,
                    // Inputs the script no longer declares are dropped here rather than kept
                    // forever: a stale override is a value the reader cannot see and cannot change.
                    overrides = old.overrides.filterKeys { key -> result.inputs.any { it.name == key } },
                )
            }
        }
    }

    /**
     * Sets one input and re-runs.
     *
     * Re-running immediately is the point of the panel: an input a reader has to run manually after
     * changing is a slower way of editing the number in the source.
     */
    fun setInput(name: String, value: Double) {
        if (!value.isFinite()) return
        _state.update { it.copy(overrides = it.overrides + (name to value)) }
        run()
    }

    /** Opens a shipped preset as a new, unsaved script. */
    fun openPreset(preset: ScriptPreset) {
        _state.value = ScriptEditorState(
            name = preset.title,
            source = preset.source,
            presetId = preset.id,
        )
        run()
    }

    /**
     * Opens one of the shipped strategies.
     *
     * [name] is handed in rather than read off the strategy, because a strategy's name is shown to
     * a reader and so lives in `feature:script`'s twinned resources. This module has no resources
     * of its own and could only carry a Persian literal, which is not a name for half the audience.
     *
     * The strategy's id goes into [ScriptEditorState.presetId] exactly as a preset's does: a saved
     * copy has to remember what it was started from, and the two id spaces do not overlap.
     */
    fun openStrategy(strategy: ScriptStrategy, name: String) {
        _state.value = ScriptEditorState(
            name = name,
            source = strategy.source,
            presetId = strategy.id,
        )
        run()
    }

    /** Opens a blank script. */
    fun openBlank() {
        _state.value = ScriptEditorState(name = "", source = ScriptPresets.BLANK)
        run()
    }

    /** Opens one of the reader's own scripts. */
    fun open(script: SavedScriptEntity) {
        _state.value = ScriptEditorState(
            name = script.name,
            source = script.source,
            savedId = script.id,
            presetId = script.presetId,
            overrides = decodeInputs(script.inputs),
        )
        run()
    }

    /**
     * Saves, creating a row the first time and updating it after.
     *
     * The name falls back to the preset's title and then to a numbered one rather than refusing:
     * a reader who wrote a working script and did not name it should not lose it to a dialog.
     */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        scope.launch {
            val stamp = now()
            val name = current.name.trim().ifBlank {
                ScriptPresets.byId(current.presetId.orEmpty())?.title
                    ?: "اسکریپت ${dao.count() + 1}"
            }
            val existing = current.savedId?.let { dao.byId(it) }
            if (existing == null) {
                val id = dao.insert(
                    SavedScriptEntity(
                        name = name,
                        source = current.source,
                        presetId = current.presetId,
                        inputs = encodeInputs(current.overrides),
                        createdAtEpochMillis = stamp,
                        updatedAtEpochMillis = stamp,
                    ),
                )
                _state.update { it.copy(savedId = id, name = name) }
            } else {
                dao.update(
                    existing.copy(
                        name = name,
                        source = current.source,
                        inputs = encodeInputs(current.overrides),
                        updatedAtEpochMillis = stamp,
                    ),
                )
                _state.update { it.copy(name = name) }
            }
        }
    }

    /** Saves the current text as a new row, leaving the one it came from alone. */
    fun saveAsCopy() {
        _state.update { it.copy(savedId = null, name = it.name.trim().let { name -> if (name.isBlank()) name else "$name — رونوشت" }) }
        save()
    }

    fun delete(id: Long) {
        scope.launch {
            dao.delete(id)
            // The editor keeps whatever is in it. Clearing the text because a row was deleted
            // would throw away unsaved edits the reader is looking at.
            _state.update { if (it.savedId == id) it.copy(savedId = null) else it }
        }
    }

    fun close() {
        _state.value = ScriptEditorState()
    }

    private companion object {
        /**
         * Overrides as `name=value` lines.
         *
         * A name containing a newline or an equals sign is dropped rather than escaped: input
         * titles come from the script's own `title =`, nobody writes one with a newline in it, and
         * an escaping scheme would be more code than the thing it protects.
         */
        fun encodeInputs(values: Map<String, Double>): String = values.entries
            .filter { '\n' !in it.key && '=' !in it.key && it.value.isFinite() }
            .joinToString("\n") { "${it.key}=${it.value}" }

        fun decodeInputs(stored: String): Map<String, Double> = stored.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                val value = line.substring(separator + 1).toDoubleOrNull() ?: return@mapNotNull null
                if (!value.isFinite()) null else line.substring(0, separator) to value
            }
            .toMap()
    }
}
