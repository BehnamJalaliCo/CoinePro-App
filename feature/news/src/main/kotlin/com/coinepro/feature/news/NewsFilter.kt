package com.coinepro.feature.news

import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.NewsSentiment

/**
 * The news list narrowed the way TradingView's news flow narrows it (5.17.0): by impact, by
 * sentiment, by publisher and by a symbol or word.
 *
 * Every part is optional and they combine with AND, so a reader asking for «high impact, bullish,
 * gold» gets the stories that are all three. A symbol typed as a ticker also finds the stories that
 * name the market in words — «XAUUSD» finds «gold» and «طلا» — because a headline rarely prints the
 * ticker and a filter that only matched the literal letters would look empty on a busy morning.
 */
data class NewsFilter(
    val highImpact: Boolean = false,
    val sentiment: NewsSentiment? = null,
    val source: String? = null,
    val query: String = "",
) {

    val active: Boolean get() = highImpact || sentiment != null || source != null || query.isNotBlank()

    fun apply(stories: List<NewsStory>): List<NewsStory> {
        if (!active) return stories
        val words = wordsOf(query)
        return stories.filter { story ->
            (!highImpact || story.impact == MarketImpact.HIGH) &&
                (sentiment == null || story.sentiment == sentiment) &&
                (source == null || story.source.equals(source, ignoreCase = true)) &&
                (words.isEmpty() || matches(story, words))
        }
    }

    companion object {

        /** The publishers worth a chip: the most frequent first, at most [limit]. */
        fun sourcesOf(stories: List<NewsStory>, limit: Int = 6): List<String> = stories
            .mapNotNull { it.source?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

        /** Whether the feed carries a classification at all — the guest feed does not, so its chips are not drawn. */
        fun classified(stories: List<NewsStory>): Boolean =
            stories.any { it.impact != MarketImpact.UNKNOWN || it.sentiment != NewsSentiment.UNKNOWN }

        private fun matches(story: NewsStory, words: List<String>): Boolean {
            val text = listOfNotNull(story.title, story.summary, story.source).joinToString(" ").lowercase()
            return words.any { it in text }
        }

        /** The typed text and, for a known ticker, the words a headline names its market with. */
        internal fun wordsOf(query: String): List<String> {
            val typed = query.trim().lowercase()
            if (typed.isEmpty()) return emptyList()
            val ticker = typed.uppercase().replace("/", "").replace("-", "")
            val aliases = ALIASES.entries.firstOrNull { ticker.startsWith(it.key) }?.value.orEmpty()
            return listOf(typed) + aliases
        }

        private val ALIASES: Map<String, List<String>> = mapOf(
            "XAU" to listOf("gold", "طلا"),
            "XAG" to listOf("silver", "نقره"),
            "BTC" to listOf("bitcoin", "بیت‌کوین", "بیت کوین"),
            "ETH" to listOf("ethereum", "اتریوم"),
            "EUR" to listOf("euro", "یورو", "ecb"),
            "GBP" to listOf("pound", "sterling", "پوند", "boe"),
            "USDJPY" to listOf("yen", "ین", "boj"),
            "XTI" to listOf("oil", "crude", "نفت"),
            "NAS" to listOf("nasdaq", "نزدک"),
            "US500" to listOf("s&p", "اس‌اندپی"),
        )
    }
}
