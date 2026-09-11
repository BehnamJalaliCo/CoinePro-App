package com.coinepro.core.datastore

/**
 * Indicator parameters on the wire: «id/key» and value, alternating, the same shape the periods
 * take, so the three stores that carry them — the symbol's state, a layout, a template — read
 * and write one format. A value that does not parse drops its own pair and keeps the rest.
 */
internal object ChartParamsCodec {

    private const val UNIT = "\u001F"
    private const val PATH = '/'

    fun encode(params: Map<String, Map<String, Double>>): String = params
        .filterKeys { it.isNotBlank() && !it.contains(UNIT) && !it.contains(PATH) }
        .flatMap { (id, values) ->
            values
                .filterKeys { it.isNotBlank() && !it.contains(UNIT) && !it.contains(PATH) }
                .filterValues { it.isFinite() }
                .flatMap { (key, value) -> listOf("$id$PATH$key", value.toString()) }
        }
        .joinToString(UNIT)

    fun decode(field: String?): Map<String, Map<String, Double>> {
        if (field.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, MutableMap<String, Double>>()
        field.split(UNIT).filter(String::isNotBlank).chunked(2).forEach { pair ->
            if (pair.size != 2) return@forEach
            val id = pair[0].substringBefore(PATH)
            val key = pair[0].substringAfter(PATH, "")
            val value = pair[1].toDoubleOrNull() ?: return@forEach
            if (id.isBlank() || key.isBlank() || !value.isFinite()) return@forEach
            out.getOrPut(id) { LinkedHashMap() }[key] = value
        }
        return out
    }
}
