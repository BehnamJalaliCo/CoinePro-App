package com.coinepro.core.help

/**
 * One piece of text in both languages.
 *
 * The app is Persian by default and English is a real second language rather than a fallback, so
 * every string here exists twice and neither is derived from the other at runtime.
 */
data class Bilingual(val fa: String, val en: String) {
    fun inLanguage(persian: Boolean): String = if (persian) fa else en
}

/** One screenshot, with the caption a reader would hear if they could not see it. */
data class HelpImage(
    /** File name inside `assets/help/images`. */
    val file: String,
    val alt: Bilingual,
)

/**
 * The «؟» content for one indicator, drawing tool, chart type, timeframe or scripting primitive.
 *
 * This is the Bazaarnama web app's own help, exported field by field and verified against the live
 * site — not a rewrite. That matters: a trader who learned Fibonacci from the web terminal and then
 * opens the phone must read the same explanation, not a second one that disagrees at the edges.
 *
 * [useCase] is the one exception and is marked as such in the export: it is an editorial one-line
 * summary added for the export, not text the site serves. It is used for the subtitle in a list,
 * where the full [what] would not fit.
 *
 * [pitfall] is the second exception, and it is editorial for a reason worth stating. The exported
 * text explains what a tool *is*; it rarely says where it lies to you. An oscillator pinned at
 * seventy through an entire trend, a Parabolic SAR whipsawing in a range, an Alligator whose lines
 * are displaced so they do not belong to the bar being read — those are the ways a reader actually
 * loses money with these, and an entry that omits them is accurate and still misleading. Null on
 * every entry exported before the field existed, which is most of them.
 */
data class HelpEntry(
    /** Canonical id — `rsi`, `fib`, `heikin`, `H4`. Stable, and what every call site keys on. */
    val id: String,
    val title: Bilingual,
    val useCase: Bilingual?,
    /** What the thing *is*. Several paragraphs. */
    val what: Bilingual?,
    /** How to use it, as ordered steps. */
    val how: BilingualList,
    /** Things worth knowing that are not steps. */
    val tips: BilingualList,
    /** A worked example. */
    val example: Bilingual?,
    /** The specific mistake people make with this one. See the class KDoc. */
    val pitfall: Bilingual? = null,
    /** Ordered gallery. Empty for the entries the site never illustrated. */
    val images: List<HelpImage>,
) {
    val hasImages: Boolean get() = images.isNotEmpty()
}

/** A list of strings in both languages — the steps and tips. */
data class BilingualList(val fa: List<String>, val en: List<String>) {
    fun inLanguage(persian: Boolean): List<String> = if (persian) fa else en
    val isEmpty: Boolean get() = fa.isEmpty() && en.isEmpty()

    companion object {
        val EMPTY = BilingualList(emptyList(), emptyList())
    }
}
