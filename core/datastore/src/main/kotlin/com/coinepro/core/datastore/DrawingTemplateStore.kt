package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A saved style for one drawing tool: a name, a colour and a width the reader settled on once.
 *
 * ### Why a template is per tool and not global
 *
 * The width that makes a trend line readable is not the width that makes a rectangle readable, and
 * the colour somebody uses for support is not the one they use for a note. A single global style
 * would be re-adjusted on every second drawing, which is the state the app is in today. [toolId] is
 * therefore part of the template rather than something the caller filters on afterwards.
 *
 * ### [toolId] is a plain String
 *
 * The same rule every other store in this package follows: `core:datastore` does not depend on
 * `core:chart` and must not start. A tool id is a stable string the catalogue already keys on, and
 * an id this build no longer recognises stays on disk and is simply never resolved — which is what
 * should happen when a tool is renamed or somebody downgrades.
 *
 * [id] is the caller's to generate and must be stable, because it is what [DrawingTemplateStore]
 * points a per-tool default at. [createdAt] is epoch milliseconds and is load-bearing rather than
 * informational: it is the order the list comes back in and the order the cap evicts by, so a
 * caller that leaves it at zero is saying this template is the first to be thrown away.
 */
data class DrawingTemplate(
    val id: String,
    val toolId: String,
    val name: String,
    val colour: Long,
    val widthDp: Float,
    val createdAt: Long,
)

/**
 * The reader's saved drawing styles, and which one each tool reaches for by default.
 *
 * ### Why this exists
 *
 * Somebody who has decided that their trend lines are a 2dp amber has, today, no way to say so:
 * every line is placed at the default and then recoloured and re-widened by hand, one at a time,
 * forever. The web terminals all solved this the same way — save the style, name it, apply it — and
 * a chart tool without it quietly taxes the readers who use it most.
 *
 * ### One preferences entry, and a second for the defaults
 *
 * Templates are packed into a single string the way [SymbolChartStateStore] packs symbols, because
 * every one of this store's jobs — [all], the per-tool filter, evicting the oldest — needs to see
 * every row at once. The per-tool defaults are a second entry rather than a field on the template,
 * because "which template is the default for the trend line" is a property of the *tool*, and
 * storing it on the template would allow two templates to both claim it and make clearing it mean
 * rewriting a row.
 *
 * ### The encoding
 *
 * The delimited-string scheme [ChartDrawingStore] and [SymbolChartStateStore] use, for the same
 * reason: the alternative is a serialisation library in a preferences module. ASCII's group
 * separator between templates, its record separator between one template's fields, its unit
 * separator between a tool id and the template it defaults to. All three are control characters,
 * so no id the app generates can contain one, and a name that does is written with the separator
 * stripped rather than as a record that would parse back as different fields.
 *
 * ### Decoding never throws, and short rows are the point
 *
 * A row written by an older build is *short*, and every missing field takes its default rather
 * than discarding the row: only [DrawingTemplate.id] and [DrawingTemplate.toolId] are required,
 * because a template with neither belongs to nothing. A row written by a *newer* build carries
 * fields this version has never heard of, and those are ignored rather than treated as corruption.
 * The rule anything added later has to follow is the same one: a new field goes on the **end**, its
 * absence has a meaning, and nothing already written is ever reinterpreted. The failure this avoids
 * is the one that matters — a reader who updates the app and finds their saved styles gone.
 */
class DrawingTemplateStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Every template saved for one tool, newest first.
     *
     * Newest first because that is the order the cap evicts in as well, so the template at the
     * bottom of a reader's list is the one that will go if they ever reach two hundred.
     */
    fun templates(toolId: String): Flow<List<DrawingTemplate>> = all()
        .map { templates -> templates.filter { it.toolId == toolId } }
        .distinctUntilChanged()

    /** Every saved template, newest first. What a "manage templates" screen lists. */
    fun all(): Flow<List<DrawingTemplate>> = dataStore.data
        .map { preferences -> decodeAll(preferences[TEMPLATES].orEmpty()) }
        .distinctUntilChanged()

    /**
     * Writes one template, replacing whatever was stored under the same id.
     *
     * An upsert rather than an append, so the edit sheet can save the same template repeatedly
     * without the list growing a copy each time. A template with a blank id or a blank tool id is
     * dropped: neither can be resolved back to anything, and storing it would put a row in the
     * reader's list that no tool can ever apply.
     */
    suspend fun save(template: DrawingTemplate) {
        if (encode(template) == null) return
        dataStore.edit { preferences ->
            val kept = decodeAll(preferences[TEMPLATES].orEmpty())
                .filterNot { it.id == template.id }
            preferences[TEMPLATES] = write(kept + template)
        }
    }

    /**
     * Renames one template and changes nothing else.
     *
     * Its own operation rather than a [save] of a copied row, because renaming is the one edit that
     * must not disturb [DrawingTemplate.createdAt] — a reader who fixes a typo does not expect the
     * template to jump to the top of their list, or to outlive an older one at the cap.
     */
    suspend fun rename(id: String, name: String) {
        dataStore.edit { preferences ->
            val templates = decodeAll(preferences[TEMPLATES].orEmpty())
            if (templates.none { it.id == id }) return@edit
            preferences[TEMPLATES] = write(
                templates.map { if (it.id == id) it.copy(name = name) else it },
            )
        }
    }

    /**
     * Forgets one template, and any tool that was defaulting to it.
     *
     * The second half is not tidiness. A default pointing at a template that no longer exists would
     * be a tool that silently falls back to the app's own style with no way for the reader to see
     * why, so the pointer is cleared at the same moment the row goes. [defaultFor] tolerates a
     * dangling pointer anyway, because a row can also be lost to a truncated write.
     */
    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val kept = decodeAll(preferences[TEMPLATES].orEmpty()).filterNot { it.id == id }
            if (kept.isEmpty()) {
                // Removed rather than stored as an empty string, so a reader who deletes their last
                // template leaves nothing behind for the next version to have to parse.
                preferences.remove(TEMPLATES)
            } else {
                preferences[TEMPLATES] = write(kept)
            }
        }
        clearDefaultsPointingAt(id)
    }

    /**
     * The template one tool reaches for when the reader picks it up, or null.
     *
     * Null in both of the cases that mean "no default": nothing was ever set for this tool, and the
     * template that was set has since been deleted. The second is resolved rather than trusted —
     * the pointer is looked up in the templates on every emission — so a dangling id reads as no
     * default instead of as a style nobody can see.
     */
    fun defaultFor(toolId: String): Flow<DrawingTemplate?> = dataStore.data
        .map { preferences ->
            val wanted = decodeDefaults(preferences[DEFAULTS].orEmpty())[toolId] ?: return@map null
            decodeAll(preferences[TEMPLATES].orEmpty()).firstOrNull { it.id == wanted }
        }
        .distinctUntilChanged()

    /**
     * Points one tool at a template, or clears it with a null [templateId].
     *
     * Nullable rather than a second `clearDefault` function, because "no default" is a value the
     * reader can choose from the same menu they chose a template from, and two functions would be
     * two paths to keep in step.
     */
    suspend fun setDefault(toolId: String, templateId: String?) {
        if (toolId.isBlank() || hasSeparator(toolId)) return
        dataStore.edit { preferences ->
            val defaults = decodeDefaults(preferences[DEFAULTS].orEmpty()).toMutableMap()
            if (templateId.isNullOrBlank() || hasSeparator(templateId)) {
                defaults.remove(toolId)
            } else {
                defaults[toolId] = templateId
            }
            if (defaults.isEmpty()) {
                preferences.remove(DEFAULTS)
            } else {
                preferences[DEFAULTS] = encodeDefaults(defaults)
            }
        }
    }

    private suspend fun clearDefaultsPointingAt(templateId: String) {
        dataStore.edit { preferences ->
            val defaults = decodeDefaults(preferences[DEFAULTS].orEmpty())
                .filterValues { it != templateId }
            if (defaults.isEmpty()) {
                preferences.remove(DEFAULTS)
            } else {
                preferences[DEFAULTS] = encodeDefaults(defaults)
            }
        }
    }

    companion object {
        internal val TEMPLATES = stringPreferencesKey("drawing_templates")

        internal val DEFAULTS = stringPreferencesKey("drawing_template_defaults")

        /** Between templates, and between one tool's default and the next. ASCII group separator. */
        private const val GROUP = "\u001D"

        /** Between one template's fields. ASCII record separator. */
        private const val RECORD = "\u001E"

        /** Between a tool id and the template it defaults to. ASCII unit separator. */
        private const val UNIT = "\u001F"

        /**
         * How many templates the reader may keep.
         *
         * A cap rather than none, because this whole string is parsed on every emission and every
         * chart open resolves a default through it: a reader who saves a style every day for two
         * years should not pay for seven hundred of them on every launch. Two hundred is far past
         * the number of distinct styles anybody keeps straight — the tools themselves number
         * ninety-one — so in practice the eviction never runs; it exists so that unbounded saving
         * cannot turn into an unbounded read. The oldest goes first, by
         * [DrawingTemplate.createdAt], which is also the bottom of the list the reader sees.
         */
        const val MAX_TEMPLATES = 200

        /** Newest first, capped, dropping anything that cannot be written back. */
        private fun write(templates: List<DrawingTemplate>): String = templates
            .sortedByDescending(DrawingTemplate::createdAt)
            .take(MAX_TEMPLATES)
            .mapNotNull { encode(it) }
            .joinToString(GROUP)

        internal fun encode(template: DrawingTemplate): String? {
            val id = template.id.takeIf { it.isNotBlank() && !hasSeparator(it) } ?: return null
            val toolId = template.toolId.takeIf { it.isNotBlank() && !hasSeparator(it) } ?: return null
            return listOf(
                id,
                toolId,
                // The name is the reader's own text, so it is the one field that can carry a
                // separator. Stripped rather than dropping the template with it: losing the name of
                // a style is a smaller failure than losing the style.
                stripSeparators(template.name),
                template.colour.toString(),
                template.widthDp.toString(),
                template.createdAt.toString(),
            ).joinToString(RECORD)
        }

        internal fun decodeAll(stored: String): List<DrawingTemplate> = stored
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull { decode(it) }
            .sortedByDescending(DrawingTemplate::createdAt)

        internal fun decode(record: String): DrawingTemplate? {
            val parts = record.split(RECORD)
            // Only the two ids are required. Every other field takes its default when a shorter row
            // — one written before that field existed — does not carry it.
            val id = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
            val toolId = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
            return DrawingTemplate(
                id = id,
                toolId = toolId,
                name = parts.getOrNull(2).orEmpty(),
                colour = parts.getOrNull(3)?.toLongOrNull() ?: DEFAULT_COLOUR,
                widthDp = parts.getOrNull(4)?.toFloatOrNull() ?: DEFAULT_WIDTH_DP,
                createdAt = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            )
        }

        internal fun encodeDefaults(defaults: Map<String, String>): String = defaults
            .filterKeys { it.isNotBlank() && !hasSeparator(it) }
            .filterValues { it.isNotBlank() && !hasSeparator(it) }
            .entries
            .joinToString(GROUP) { (toolId, templateId) -> "$toolId$UNIT$templateId" }

        internal fun decodeDefaults(stored: String): Map<String, String> = stored
            .split(GROUP)
            .filter(String::isNotBlank)
            .mapNotNull { pair ->
                val halves = pair.split(UNIT)
                if (halves.size != 2) return@mapNotNull null
                val toolId = halves[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
                val templateId = halves[1].takeIf(String::isNotBlank) ?: return@mapNotNull null
                toolId to templateId
            }
            .toMap()

        private fun hasSeparator(value: String) =
            value.contains(GROUP) || value.contains(RECORD) || value.contains(UNIT)

        private fun stripSeparators(value: String) =
            value.filterNot { it == GROUP[0] || it == RECORD[0] || it == UNIT[0] }

        /** Matches `Drawing.DEFAULT_DRAWING_COLOUR`; duplicated rather than depended on. */
        const val DEFAULT_COLOUR = 0xFFD8A848

        /** Matches the width a `Drawing` is placed at. Duplicated for the same reason. */
        const val DEFAULT_WIDTH_DP = 1.6f
    }
}
