package com.coinepro.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketLean
import com.coinepro.core.marketdata.MarketMood
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.symbols.SymbolClassifier

/**
 * **حال‌وهوای بازار** — what the whole board did today, above the reader's own markets (run Ω4).
 *
 * ### Why this is on Home and what it replaces
 *
 * Home answers «what should I look at». It answered it with the reader's own six markets, which is
 * the right answer for somebody who already has an opinion and no answer at all for somebody who
 * does not — and the second is most people, most mornings. One strip of the board's own numbers is
 * the shortest honest version of «here is what is happening», and every one of them is a tap into
 * the market it is about.
 *
 * ### Graceful in parts, and why that is not an excuse
 *
 * The strip draws whatever it has. A platform with no day-figures route draws nothing at all rather
 * than a row of dashes, a half-loaded table draws the parts that arrived, and no part waits for
 * another. That is not a way of hiding a missing feed — [MarketMood] is explicit about what each
 * figure measures, and the thing this strip must never do is *infer*: the breadth is named breadth
 * and not fear-and-greed, «شلوغ» says busy rather than «unusual», and a number nobody sent is not
 * drawn as a zero.
 */
@Composable
internal fun MarketMoodStrip(
    mood: MarketMood,
    onOpenSymbol: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (mood.isEmpty) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CoineProShapes.large)
            .background(CoineProColors.Surface)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.large)
            .padding(CoineProSpacing.Gutter)
            .semantics { contentDescription = MOOD_TAG },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        mood.breadth?.let { breadth ->
            BreadthBlock(breadth = breadth, advancing = mood.advancing, declining = mood.declining, lean = mood.lean)
        }
        if (mood.movers.isNotEmpty()) {
            MoodRow(
                heading = stringResource(R.string.mood_movers),
                tickers = mood.movers,
                onOpenSymbol = onOpenSymbol,
            )
        }
        if (mood.unusual.isNotEmpty()) {
            MoodRow(
                heading = stringResource(R.string.mood_busy),
                tickers = mood.unusual,
                onOpenSymbol = onOpenSymbol,
            )
        }
    }
}

/**
 * The board's lean: a word, a bar, and the two counts it came from.
 *
 * The counts are not decoration. A breadth of 70 % over ten markets and over four hundred are two
 * different claims, and the figure alone cannot tell them apart — which is the same rule the Signal
 * Layer's «۶۱٪ از ۱۸» follows, applied to the board.
 */
@Composable
private fun BreadthBlock(breadth: Int, advancing: Int, declining: Int, lean: MarketLean) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mood_title),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            Text(
                text = stringResource(
                    when (lean) {
                        MarketLean.UP -> R.string.mood_lean_up
                        MarketLean.DOWN -> R.string.mood_lean_down
                        else -> R.string.mood_lean_mixed
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = when (lean) {
                    MarketLean.UP -> CoineProColors.MarketUp
                    MarketLean.DOWN -> CoineProColors.MarketDown
                    else -> CoineProColors.TextSecondary
                },
            )
        }
        // One bar, split at the breadth. Green and red, and here they are the market's own colours
        // meaning exactly what they mean on every chart in the app — this bar *is* up against down.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(CoineProPillShape),
        ) {
            Box(
                modifier = Modifier
                    .weight(breadth.coerceIn(1, 99).toFloat())
                    .fillMaxWidth()
                    .background(CoineProColors.MarketUp),
            )
            Box(
                modifier = Modifier
                    .weight((100 - breadth).coerceIn(1, 99).toFloat())
                    .fillMaxWidth()
                    .background(CoineProColors.MarketDown),
            )
        }
        Text(
            // Prose counts, so Persian digits — this is a sentence about the board, not a figure off
            // a chart. See the app's own rule on numerals.
            text = stringResource(R.string.mood_counts, advancing.proseDigits(), declining.proseDigits()),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/** One heading and up to three market chips, each a tap into that market. */
@Composable
private fun MoodRow(heading: String, tickers: List<MarketTicker>, onOpenSymbol: ((String) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            for (ticker in tickers) {
                MoodChip(ticker = ticker, onOpenSymbol = onOpenSymbol)
            }
        }
    }
}

/**
 * One market: its name, and what it did today.
 *
 * The percentage is in the market's colour and the ticker is not, which is the rule the whole app
 * follows — the figure carries the direction and the name carries nothing.
 */
@Composable
private fun MoodChip(ticker: MarketTicker, onOpenSymbol: ((String) -> Unit)?) {
    val haptics = rememberCoineProHaptics()
    val change = ticker.changePercent24h
    val ink = when {
        change == null -> CoineProColors.TextMuted
        change >= 0.0 -> CoineProColors.MarketUp
        else -> CoineProColors.MarketDown
    }
    Row(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .then(
                if (onOpenSymbol == null) {
                    Modifier
                } else {
                    Modifier.clickable {
                        haptics.select()
                        onOpenSymbol(ticker.symbol)
                    }
                },
            )
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // The slashed form, isolated, so a Latin ticker keeps its own direction inside a Persian
            // row — the same treatment every symbol in this app gets.
            text = BidiText.isolateLtr(SymbolClassifier.classify(ticker.symbol).pretty),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextPrimary,
            maxLines = 1,
        )
        change?.let {
            Text(
                text = BidiText.isolateLtr(MarketNumberFormatter.signedPercent(it)),
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

/** The split bar's height. Six points: a rule with a job, not a chart. */
private val BAR_HEIGHT = 6.dp

/** What a screenshot test looks for. */
private const val MOOD_TAG = "market-mood"
