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

    fun parse(raw: String): ParsedPriceBatch? {
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
