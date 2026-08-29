package com.coinepro.feature.free

import androidx.annotation.StringRes

/**
 * One line of the comparison: something a rival charges for, and what this app does about it.
 *
 * The three verdicts are deliberately not two. A table with only "we have it" and "they charge for
 * it" is an advertisement, and a reader who has used any of these products will find the first
 * thing it overstates within a minute — after which nothing else on the page is believed either.
 * [Verdict.ABSENT] is what buys the other two their credibility, and it is why on-chain data,
 * market cap and volume profile are named on this screen rather than left out of it.
 */
data class FreeClaim(
    /** The rival, by name. Latin, so it is isolated where it is drawn. */
    val rival: String,
    /** What that product puts behind its paywall. */
    @StringRes val paywalled: Int,
    /** What they ask for it, with the currency spelled out. Null where we could not verify one. */
    @StringRes val price: Int?,
    val verdict: Verdict,
    /** What this app does instead — the honest sentence, including when the answer is "nothing". */
    @StringRes val answer: Int,
)

/** What this app can honestly say about one paywalled thing. */
enum class Verdict {
    /** Built, working, and free here. */
    FREE,

    /**
     * Partly. Some of it is here and some is not, and the row says which half.
     *
     * This verdict exists because the alternative is rounding: a row that is two-thirds true gets
     * written as [FREE] and the missing third is the thing the reader was actually looking for.
     */
    PARTIAL,

    /**
     * Not here, and — for most of these — not possible for us at all.
     *
     * On-chain metrics, circulating supply and a global market-cap rank all need an aggregator or a
     * chain indexer. This app relays two exchanges. Saying so is not a confession of weakness; it
     * is the only reason a reader should believe the rows above.
     */
    ABSENT,
}

/**
 * The table.
 *
 * Ordered by how much it is worth to *this* reader rather than by rival, which is why TradingView's
 * chart limits lead: a Persian-speaking trader on a phone hits the two-indicator ceiling on their
 * first evening, and that is the single most expensive limit any of these products impose.
 *
 * Every [Verdict.FREE] row was checked against the code before it was written here — the counts it
 * quotes come from [FreeFacts], which reads the app's own catalogues.
 */
object FreeComparison {

    val claims: List<FreeClaim> = listOf(
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_indicators,
            price = R.string.free_tv_price,
            verdict = Verdict.FREE,
            answer = R.string.free_tv_indicators_answer,
        ),
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_alerts,
            price = null,
            verdict = Verdict.FREE,
            answer = R.string.free_tv_alerts_answer,
        ),
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_watchlists,
            price = null,
            verdict = Verdict.FREE,
            answer = R.string.free_tv_watchlists_answer,
        ),
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_panes,
            price = null,
            // Two, not eight. Their Plus tier is two and their Premium is eight, so this beats the
            // free tier and matches a paid one — which is a true sentence and a smaller one than
            // the row above it. Writing it as FREE would be the rounding this enum exists to stop.
            verdict = Verdict.PARTIAL,
            answer = R.string.free_tv_panes_answer,
        ),
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_history,
            price = null,
            // The one row on this page where the rival's *free* tier beats ours, and it is here
            // because the server measured it and told us so rather than because anybody wanted it.
            // Their free plan gives 5,000 bars a chart; the venue's retention gives us about 1,900,
            // and on the daily that is 3.7 years against roughly 13. Leaving it out would have
            // been the easy choice and would have made every row above it worth less.
            verdict = Verdict.ABSENT,
            answer = R.string.free_tv_history_answer,
        ),
        FreeClaim(
            rival = "TradingView",
            paywalled = R.string.free_tv_volume_profile,
            price = null,
            verdict = Verdict.ABSENT,
            answer = R.string.free_tv_volume_profile_answer,
        ),
        FreeClaim(
            rival = "CoinGecko",
            paywalled = R.string.free_gecko_ads,
            price = R.string.free_gecko_price,
            verdict = Verdict.FREE,
            answer = R.string.free_gecko_ads_answer,
        ),
        FreeClaim(
            rival = "CoinGecko",
            paywalled = R.string.free_gecko_wallets,
            price = null,
            verdict = Verdict.ABSENT,
            answer = R.string.free_gecko_wallets_answer,
        ),
        FreeClaim(
            rival = "CoinMarketCap",
            paywalled = R.string.free_cmc_marketcap,
            price = R.string.free_cmc_price,
            verdict = Verdict.ABSENT,
            answer = R.string.free_cmc_marketcap_answer,
        ),
        FreeClaim(
            rival = "Glassnode",
            paywalled = R.string.free_glassnode_onchain,
            price = R.string.free_glassnode_price,
            verdict = Verdict.ABSENT,
            answer = R.string.free_glassnode_onchain_answer,
        ),
        FreeClaim(
            rival = "Messari",
            paywalled = R.string.free_messari_screener,
            price = R.string.free_messari_price,
            verdict = Verdict.FREE,
            answer = R.string.free_messari_screener_answer,
        ),
        FreeClaim(
            rival = "CoinStats",
            paywalled = R.string.free_coinstats_portfolio,
            price = R.string.free_coinstats_price,
            verdict = Verdict.FREE,
            answer = R.string.free_coinstats_portfolio_answer,
        ),
        FreeClaim(
            rival = "Investing.com",
            paywalled = R.string.free_investing_ai,
            price = R.string.free_investing_price,
            verdict = Verdict.FREE,
            answer = R.string.free_investing_ai_answer,
        ),
        FreeClaim(
            rival = "Investing.com",
            paywalled = R.string.free_investing_fair_value,
            price = null,
            verdict = Verdict.ABSENT,
            answer = R.string.free_investing_fair_value_answer,
        ),
    )

    /** How many rows say each thing — the screen prints this above the table, honestly. */
    fun tally(verdict: Verdict): Int = claims.count { it.verdict == verdict }
}
