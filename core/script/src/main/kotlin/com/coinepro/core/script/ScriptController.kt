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
    /** The id a share link addresses, or empty for a script never shared or imported. */
    val publicId: String = "",
    val presetId: String? = null,
    /** Reader-set values for `input(...)`, keyed by the input's title. */
    val overrides: Map<String, Double> = emptyMap(),
    /**
     * What the reader has made this script *theirs* with (4.82.0, run Σ item S3 C).
     *
     * A description, a colour, tags and a pane. Held here rather than only in the row, because the
     * reader edits them in the editor and a value that only existed after a save would be a value
     * that vanished if they changed their mind.
     */
    val description: String = "",
    val colour: Long = ScriptDocument.DEFAULT_COLOUR,
    val tags: List<String> = emptyList(),
    val ownPane: Boolean = false,
    /** Earlier versions of this script, newest first, at most five. */
    val history: List<ScriptRevision> = emptyList(),
    val result: ScriptResult? = null,
    /** A syntax error found without running — see [NamaScript.check]. */
    val syntax: ScriptFailure? = null,
    val dirty: Boolean = false,
    val running: Boolean = false,
    /** Whether the last run re-used the previous result and evaluated only the tail. */
    val incremental: Boolean = false,
    /**
     * What the last paste was and what was done to it, until the reader dismisses it (run Σ, S3 A).
     *
     * Held in state rather than handed to a dialog, because the diff is the *explanation* of why the
     * text in the editor is not the text they pasted. A reader who pastes an assistant's answer and
     * sees different code has to be told what changed, in their language, with the option to put it
     * back — and a banner that survives a rotation is the only place that fits.
     */
    val paste: ScriptPaste.Paste? = null,
    /** Exactly what the reader pasted, so the repairs can be undone in one tap. */
    val pastedRaw: String? = null,
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

    /**
     * The script as compiled for [compiledSource], and the runner that keeps its last result.
     * A changed source recompiles; the same source with new bars runs only its tail — see
     * `IncrementalRunner`.
     */
    private var compiledSource: String? = null
    private var runner: IncrementalRunner? = null

    private fun evaluate(source: String, overrides: Map<String, Double>): Pair<ScriptResult, Boolean> {
        if (source != compiledSource) {
            val compilation = NamaScript.compile(source)
            compiledSource = source
            runner = compilation.script?.let { IncrementalRunner(it) }
            compilation.failure?.let { return ScriptResult(error = it) to false }
        }
        val active = runner ?: return ScriptResult(error = NamaScript.check(source)) to false
        val result = active.run(series, overrides)
        return result to active.lastRunWasIncremental
    }

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
            val (result, incremental) = evaluate(current.source, current.overrides)
            _state.update { old ->
                old.copy(
                    result = result,
                    running = false,
                    dirty = false,
                    incremental = incremental,
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

    /**
     * **Paste** — reads [text], repairs what can be repaired, and loads the result (run Σ, S3 A).
     *
     * Reads and repairs; it does not run. A paste that quietly ran would be a paste that quietly
     * ran somebody else's code, and «اجرا» is one tap away. What the reader gets instead is the
     * repaired text, a list of what changed, and — for a sentence rather than code — a picker.
     *
     * The editor is *replaced* rather than appended to. A reader pasting into a script they were
     * writing means «this instead», and a paste box that concatenated two scripts would produce
     * something neither of them asked for; [pastedRaw] is what makes that recoverable.
     */
    fun paste(text: String) {
        val read = ScriptPaste.read(text)
        _state.update {
            it.copy(
                source = if (read.dialect == ScriptPaste.Dialect.PROSE) it.source else read.fixed,
                dirty = true,
                syntax = if (read.dialect == ScriptPaste.Dialect.PROSE) it.syntax else NamaScript.check(read.fixed),
                paste = read,
                pastedRaw = text,
                // A pasted script is not the saved one it landed on top of. Keeping the row id
                // would make the next «ذخیره» overwrite a script the reader did not mean to touch.
                savedId = null,
                presetId = null,
                result = null,
            )
        }
    }

    /**
     * Puts back exactly what was pasted, repairs and all undone.
     *
     * The brief's «undoable», and the reason the repairs can be applied without asking first: a
     * one-tap fix a reader cannot reverse is a fix they have to read carefully before accepting,
     * which is the thing this whole feature exists to spare them.
     */
    fun undoPaste() {
        val raw = _state.value.pastedRaw ?: return
        _state.update {
            it.copy(source = raw, dirty = true, syntax = NamaScript.check(raw), paste = null, pastedRaw = null)
        }
    }

    /** Dismisses the paste banner, keeping the repaired text. */
    fun dismissPaste() = _state.update { it.copy(paste = null, pastedRaw = null) }

    /**
     * Takes one of the templates a prose paste matched, filled with the numbers that sentence named.
     *
     * The sentence itself is kept as [ScriptEditorState.pastedRaw] so «برگردان» still answers with
     * what the reader typed — which for a template is the only version of their intention that
     * exists in their own words.
     */
    fun openTemplate(template: ScriptTemplate, sentence: String = _state.value.pastedRaw.orEmpty()) {
        val source = template.source(ScriptTemplates.numbersIn(sentence))
        _state.update {
            it.copy(
                name = template.title,
                source = source,
                savedId = null,
                presetId = null,
                overrides = emptyMap(),
                dirty = true,
                syntax = NamaScript.check(source),
                paste = null,
            )
        }
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

    /**
     * Opens a blank script — three lines rather than an empty editor.
     *
     * [english] chooses the language of those three lines. It is a parameter rather than something
     * read here because this class has no screen and no configuration; the studio knows what
     * language it is drawn in and says so.
     */
    fun openBlank(english: Boolean = false) {
        _state.value = ScriptEditorState(name = "", source = ScriptPresets.blank(english))
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
            description = script.description,
            colour = script.colour,
            tags = script.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            ownPane = script.ownPane,
            history = decodeHistory(script.history),
        )
        run()
    }

    // ── what makes a script the reader's own (4.82.0, run Σ item S3 C) ───────────────────────

    fun describe(description: String) = _state.update {
        it.copy(description = description.replace('\n', ' ').take(ScriptDocument.DESCRIPTION_LIMIT), dirty = true)
    }

    fun setColour(colour: Long) = _state.update { it.copy(colour = colour, dirty = true) }

    /**
     * Sets the tags from what the reader typed, comma-separated.
     *
     * Split here rather than in the screen so the rule — trim, drop the empties, keep the order —
     * is one rule. A tag list is a small thing to get inconsistent between the editor and the row.
     */
    fun setTags(typed: String) = _state.update {
        it.copy(tags = typed.split(',', '،').map(String::trim).filter(String::isNotEmpty).take(TAG_LIMIT), dirty = true)
    }

    fun setOwnPane(own: Boolean) = _state.update { it.copy(ownPane = own, dirty = true) }

    /**
     * Puts an earlier version back in the editor.
     *
     * It does not save. A reader looking through their history is *looking*, and a restore that
     * wrote itself to the row would make «what did this used to say» a destructive question.
     */
    fun restore(revision: ScriptRevision) = _state.update {
        it.copy(source = revision.source, dirty = true, syntax = NamaScript.check(revision.source))
    }

    /** This editor as a document — for export, for a link, for anything outside the database. */
    fun document(): ScriptDocument {
        val current = _state.value
        return ScriptDocument(
            id = current.publicId.ifEmpty { ScriptDocument.idFor(current.source, current.savedId ?: 0L) },
            name = current.name,
            description = current.description,
            source = current.source,
            colour = current.colour,
            tags = current.tags,
            ownPane = current.ownPane,
            defaults = current.overrides,
            origin = current.presetId,
            updatedAt = now(),
            history = current.history,
        )
    }

    /**
     * Loads an imported document into the editor as a **new, unsaved** script.
     *
     * Unsaved on purpose. An import that wrote itself to the library would be a file deciding what
     * is in the reader's list; this way the file offers and the reader keeps.
     */
    fun openDocument(document: ScriptDocument) {
        _state.value = ScriptEditorState(
            name = document.name,
            source = document.source,
            presetId = document.origin,
            overrides = document.defaults,
            description = document.description,
            colour = document.colour,
            tags = document.tags,
            ownPane = document.ownPane,
            publicId = document.id,
        )
        run()
    }

    /**
     * Opens a script the reader is looking at somewhere else — «open in the editor» (4.73.0).
     *
     * Text and a name, because that is all a script instance on the chart carries: the instance's
     * source is the source of truth for what is *drawn*, and it may differ from the saved row it
     * started as. Opening the row instead would show the reader the wrong code — the code that is
     * not on their chart — which is the more confusing of the two answers by a distance.
     */
    fun openText(name: String, source: String, overrides: Map<String, Double> = emptyMap()) {
        _state.value = ScriptEditorState(name = name, source = source, overrides = overrides)
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
                        description = current.description,
                        colour = current.colour,
                        tags = current.tags.joinToString(", "),
                        ownPane = current.ownPane,
                        publicId = current.publicId,
                        history = encodeHistory(current.history),
                    ),
                )
                _state.update { it.copy(savedId = id, name = name) }
            } else {
                // **The version being replaced goes onto the history, here and nowhere else.**
                //
                // Not on every keystroke — five revisions of a half-typed line is a history that
                // has lost what it was kept for — and not on a save that changed nothing, which is
                // what a reader does when they open a script, look at it and press save out of
                // habit. A save that changes the source is the one moment that means «the old one
                // is gone unless somebody kept it».
                val history = if (existing.source == current.source) {
                    current.history
                } else {
                    (listOf(ScriptRevision(existing.source, existing.updatedAtEpochMillis)) + current.history)
                        .take(ScriptDocument.REVISIONS)
                }
                dao.update(
                    existing.copy(
                        name = name,
                        source = current.source,
                        inputs = encodeInputs(current.overrides),
                        updatedAtEpochMillis = stamp,
                        description = current.description,
                        colour = current.colour,
                        tags = current.tags.joinToString(", "),
                        ownPane = current.ownPane,
                        publicId = current.publicId,
                        history = encodeHistory(history),
                    ),
                )
                _state.update { it.copy(name = name, history = history) }
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

        /** At most this many tags. A list a reader cannot read is not a list. */
        const val TAG_LIMIT = 8

        /**
         * The history as length-prefixed records: `at:length:source` repeated, nothing between.
         *
         * ### Why lengths and not a separator
         *
         * The first version of this used two control characters, U+001E and U+001F, on the
         * reasoning that NamaScript cannot contain them. Half true, and the wrong half:
         * `ScriptHistoryTest` shows the lexer refuses them in code and **accepts them inside a
         * string literal**, so a reader whose label happened to carry one would have had a record
         * split in half — and, with the filter that was guarding it, a whole earlier version of
         * their script quietly dropped instead.
         *
         * A length prefix has no forbidden character. The source is never scanned; the parser is
         * told how many characters to take and takes them, so quotes, newlines, colons, Persian and
         * control characters all pass through as themselves. The two colons are unambiguous
         * because everything before them is digits.
         */
        fun encodeHistory(history: List<ScriptRevision>): String = buildString {
            for (revision in history) {
                append(revision.at).append(':').append(revision.source.length).append(':').append(revision.source)
            }
        }

        /**
         * Reads what [encodeHistory] wrote; stops at the first record that does not parse.
         *
         * Stops rather than skips, because the records after a malformed one cannot be found — the
         * next one begins wherever this one ended, and that is the thing that is unknown. Keeping
         * the versions read so far is the most that can honestly be recovered.
         */
        fun decodeHistory(stored: String): List<ScriptRevision> {
            val history = mutableListOf<ScriptRevision>()
            var cursor = 0
            while (cursor < stored.length) {
                val firstColon = stored.indexOf(':', cursor).takeIf { it > cursor } ?: break
                val secondColon = stored.indexOf(':', firstColon + 1).takeIf { it > firstColon } ?: break
                val at = stored.substring(cursor, firstColon).toLongOrNull() ?: break
                val length = stored.substring(firstColon + 1, secondColon).toIntOrNull() ?: break
                val start = secondColon + 1
                if (length < 0 || start + length > stored.length) break
                history += ScriptRevision(stored.substring(start, start + length), at)
                cursor = start + length
            }
            return history
        }

        fun decodeInputs(stored: String): Map<String, Double> = stored.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                val value = line.substring(separator + 1).toDoubleOrNull() ?: return@mapNotNull null
                if (!value.isFinite()) null else line.substring(0, separator) to value
            }
            .toMap()
    }
}
