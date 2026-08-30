package com.coinepro.core.common

/**
 * The languages CoinePro ships.
 *
 * Persian is the product default and therefore lives in the unqualified `values/` resources, so it
 * is what any device falls back to. English is a qualified alternative in `values-en/`.
 */
enum class AppLanguage(
    val tag: String,
    /**
     * The language's name **in itself** — «فارسی», not «Persian».
     *
     * Not a string resource, and that is the point: a resource would be translated, so a reader
     * whose app is stuck in the wrong language would find both options written in the language
     * they cannot read. The one row that has to work for somebody who cannot read the rest of the
     * screen is this one, and it works by being the same two words whatever the app is set to.
     */
    val displayName: String,
) {
    PERSIAN("fa", "فارسی"),
    ENGLISH("en", "English"),
    ;

    companion object {
        val Default: AppLanguage = PERSIAN

        /** Resolves a stored or system tag, falling back to [Default] for anything unrecognised. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag?.take(2), ignoreCase = true) } ?: Default
    }
}
