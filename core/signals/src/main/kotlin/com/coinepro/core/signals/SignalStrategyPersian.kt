package com.coinepro.core.signals

/**
 * A signal's strategy name, in Persian.
 *
 * ### Why this exists
 *
 * The strategy arrives from both backends as an English phrase — «Range rejection», «Trend
 * continuation» — and it is printed beside the timeframe on every signal row. On a Persian screen
 * that is the only English on the page, and because the line is narrow it does not even arrive
 * whole: the row rendered «H1 · Range rejec…», which is a truncated word in a foreign script
 * standing where the reason for the trade should be.
 *
 * ### Why a table rather than a translator
 *
 * The opposite decision from `CalendarPersian`, and for the opposite reason. An economic calendar's
 * vocabulary is open — a few hundred indicator names assembled from a small stock of words, growing
 * every quarter — so it is taken apart and translated piece by piece. A strategy name is a **closed
 * set**: the analyst picks from a list, and the list is short enough to write down. Splitting these
 * would invite «Range» and «rejection» to be translated separately and reassembled into something
 * no trader says.
 *
 * A name nobody has written down here is returned **unchanged**, which is the honest failure. A
 * reader who sees an English phrase knows exactly what the server said; a reader who sees a wrong
 * Persian one has been told something about their money that is not true.
 */
object SignalStrategyPersian {

    /** [raw] in Persian, or [raw] itself where it is not a name this app knows. */
    fun of(raw: String?): String? {
        val name = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        return NAMES[normalise(name)] ?: name
    }

    /**
     * Case, spacing, dashes and underscores all removed.
     *
     * The two backends do not agree on the spelling: one sends `Range rejection`, the other
     * `range_rejection`, and an analyst typing into a web panel sends `Range Rejection`. Keying on
     * the letters alone means one entry serves all three rather than three entries drifting apart.
     */
    private fun normalise(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    private val NAMES: Map<String, String> = mapOf(
        // Structure
        "rangerejection" to "واکنش به سقف/کف رِنج",
        "rangebreakout" to "شکست رِنج",
        "trendcontinuation" to "ادامه‌ی روند",
        "trendreversal" to "برگشت روند",
        "pullbackentry" to "ورود در اصلاح",
        "pullback" to "اصلاح",
        "breakoutretest" to "شکست و پولبک",
        "breakout" to "شکست",
        "supportbounce" to "واکنش به حمایت",
        "resistancerejection" to "واکنش به مقاومت",
        "meanreversion" to "بازگشت به میانگین",
        "momentumbreakout" to "شکست مومنتومی",

        // Price action
        "liquiditysweep" to "جاروی نقدینگی",
        "orderblock" to "اردر بلاک",
        "fairvaluegap" to "شکاف ارزش منصفانه",
        "supplyzone" to "ناحیه‌ی عرضه",
        "demandzone" to "ناحیه‌ی تقاضا",
        "doubletop" to "سقف دوقلو",
        "doublebottom" to "کف دوقلو",
        "headandshoulders" to "سر و شانه",
        "inverseheadandshoulders" to "سر و شانه‌ی معکوس",
        "engulfing" to "پوشا",
        "pinbar" to "پین‌بار",

        // Indicator-led
        "macrossover" to "تقاطع میانگین متحرک",
        "emacrossover" to "تقاطع میانگین نمایی",
        "goldencross" to "تقاطع طلایی",
        "deathcross" to "تقاطع مرگ",
        "rsidivergence" to "واگرایی آر‌اس‌آی",
        "macddivergence" to "واگرایی مکدی",
        "rsioversold" to "اشباع فروش آر‌اس‌آی",
        "rsioverbought" to "اشباع خرید آر‌اس‌آی",
        "bollingersqueeze" to "فشردگی باندهای بولینگر",
        "vwapreversion" to "بازگشت به وی‌واپ",

        // Where the analysis came from rather than what it saw
        "aivision" to "تحلیل تصویری هوش مصنوعی",
        "ai" to "هوش مصنوعی",
        "manual" to "تحلیل دستی",
        "scalp" to "اسکالپ",
        "swing" to "سوئینگ",
    )
}
