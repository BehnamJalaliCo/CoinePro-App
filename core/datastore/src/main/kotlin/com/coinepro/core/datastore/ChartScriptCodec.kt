package com.coinepro.core.datastore

/**
 * A NamaScript instance on a chart, as one row of `SymbolChartState` (4.73.0).
 *
 * ### Why the source travels with the instance
 *
 * The obvious design stores the saved script's **id** and looks the text up on restore. It is
 * wrong, and the failure is the one that matters most: a reader deletes or renames the saved
 * script, opens their chart a week later, and the study they had been reading is gone with no way
 * to tell what it was. The source is a few hundred bytes and it is the only thing that makes a
 * restored chart the chart that was saved. [scriptId] rides along beside it as provenance — «this
 * came from your row 7» — so the studio can open the original for editing when it is still there,
 * and «Restore script» has something to look for when it is not.
 *
 * ### Why everything is Base64
 *
 * `SymbolChartStateStore` packs records into one preferences string with three ASCII control
 * separators, and a reader's script contains *arbitrary text* — including, sooner or later, one of
 * those bytes. Every other field in that store can be filtered by `hasSeparator` because every
 * other field is an id the app chose. This one cannot: dropping a script because of a character in
 * a comment would be losing the reader's work to an encoding detail. So each field is encoded and
 * nothing in the payload can collide with the frame around it.
 */
data class ChartScriptRow(
    val instanceId: String,
    val name: String,
    val source: String,
    val scriptId: String? = null,
    val ordinal: Int = 1,
    /** The reader's values for the script's inputs, by the input's title. */
    val overrides: Map<String, Double> = emptyMap(),
)

internal object ChartScriptCodec {

    /** Between two instances. */
    private const val ROW = "\u0001"

    /** Between an instance's fields. */
    private const val FIELD = "\u0002"

    /** Between an override's name and its value, and between overrides. */
    private const val PAIR = "\u0003"

    fun encode(scripts: List<ChartScriptRow>): String = scripts
        .filter { it.instanceId.isNotBlank() && it.source.isNotBlank() }
        .joinToString(ROW) { script ->
            listOf(
                b64(script.instanceId),
                b64(script.name),
                b64(script.source),
                b64(script.scriptId.orEmpty()),
                script.ordinal.toString(),
                script.overrides
                    .filterValues { it.isFinite() }
                    .entries
                    .joinToString(PAIR) { (name, value) -> b64(name) + PAIR + value.toString() },
            ).joinToString(FIELD)
        }

    fun decode(field: String?): List<ChartScriptRow> {
        if (field.isNullOrBlank()) return emptyList()
        return field.split(ROW).filter(String::isNotBlank).mapNotNull { row ->
            val parts = row.split(FIELD)
            val instanceId = parts.getOrNull(0)?.let(::unB64)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val source = parts.getOrNull(2)?.let(::unB64)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            ChartScriptRow(
                instanceId = instanceId,
                name = parts.getOrNull(1)?.let(::unB64).orEmpty(),
                source = source,
                scriptId = parts.getOrNull(3)?.let(::unB64)?.takeIf(String::isNotBlank),
                ordinal = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                overrides = parts.getOrNull(5)
                    .orEmpty()
                    .split(PAIR)
                    .filter(String::isNotBlank)
                    .chunked(2)
                    .mapNotNull { pair ->
                        if (pair.size != 2) return@mapNotNull null
                        val name = unB64(pair[0]).takeIf(String::isNotBlank) ?: return@mapNotNull null
                        val value = pair[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@mapNotNull null
                        name to value
                    }
                    .toMap(),
            )
        }
    }

    /**
     * URL-safe Base64 without padding.
     *
     * URL-safe so the alphabet is `A-Za-z0-9-_`, which contains none of this file's separators and
     * none of the store's; unpadded because `=` is not needed to decode a field whose length is
     * known and a padding character is one more byte per script per write.
     */
    private fun b64(text: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.encodeToByteArray())

    /** The inverse, and **never** a throw: a corrupt field loses that script, not the whole row. */
    private fun unB64(text: String): String =
        runCatching { java.util.Base64.getUrlDecoder().decode(text).decodeToString() }.getOrDefault("")
}
