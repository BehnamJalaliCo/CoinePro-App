package com.coinepro.core.marketdata

import com.google.gson.Gson
import com.google.gson.JsonObject

internal class MarketWireParser(
    private val gson: Gson = Gson(),
) {
    data class ParsedPriceBatch(
        val quotes: List<WireQuoteDto>,
        val serverTimeMs: Long?,
    )

    /**
     * A frame off the socket, or null for anything this parser cannot use.
     *
     * **Null is the only failure mode**, and the whole body is guarded to keep it that way. The
     * JSON parse was already wrapped; the two accessors after it were not, and both are coercions
     * Gson answers with an exception rather than a null — `asString` on a `type` that arrived as an
     * object throws `UnsupportedOperationException`, `asLong` on a non-numeric `server_time_ms`
     * throws `NumberFormatException`.
     *
     * That matters more here than the same bug would anywhere else: this runs on OkHttp's WebSocket
     * reader thread, inside `MarketDataController.onMessage`, which is not the main thread and has
     * no handler above it. An exception there is not a dropped frame, it is a dead process — and
     * the input is whatever arrived over the network.
     */
    fun parse(raw: String): ParsedPriceBatch? = runCatching { parseOrThrow(raw) }.getOrNull()

    private fun parseOrThrow(raw: String): ParsedPriceBatch? {
        val root = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull() ?: return null
        if (root.get("type")?.asString != "prices") return null
        val data = root.getAsJsonObject("data") ?: return null
        val quotes = buildList {
            data.entrySet().forEach { (symbol, value) ->
                if (!value.isJsonObject) return@forEach
                val dto = runCatching {
                    gson.fromJson(value, WireQuoteDto::class.java)
                }.getOrNull() ?: return@forEach
                add(dto.copy(symbol = dto.symbol?.ifBlank { null } ?: symbol))
            }
        }
        return ParsedPriceBatch(
            quotes = quotes,
            serverTimeMs = root.get("server_time_ms")?.takeIf { !it.isJsonNull }?.asLong,
        )
    }
}
