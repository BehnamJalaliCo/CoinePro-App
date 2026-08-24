package com.coinepro.core.common

/**
 * The languages CoinePro ships.
 *
 * Persian is the product default and therefore lives in the unqualified `values/` resources, so it
 * is what any device falls back to. English is a qualified alternative in `values-en/`.
 */
enum class AppLanguage(val tag: String) {
    PERSIAN("fa"),
    ENGLISH("en"),
    ;

    companion object {
        val Default: AppLanguage = PERSIAN

        /** Resolves a stored or system tag, falling back to [Default] for anything unrecognised. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag?.take(2), ignoreCase = true) } ?: Default
    }
}
