package com.coinepro.core.common

/**
 * The languages CoinePro ships.
 *
 * Persian is the product default: [Default] is what the app opens in until the reader chooses,
 * and `AppLanguageStore.apply` pins the activity's locale to it. The *resources* are arranged the
 * other way round since 4.52.0 — English is the unqualified `values/` set and Persian the qualified
 * `values-fa/` — so a device in neither language falls back to English, and a build with a Persian
 * word in the default set fails (`checkDefaultLocaleIsEnglish`).
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
