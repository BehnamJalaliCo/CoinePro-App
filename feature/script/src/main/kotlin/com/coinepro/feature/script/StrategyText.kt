package com.coinepro.feature.script

import androidx.annotation.StringRes
import com.coinepro.core.script.ScriptStrategy

/**
 * Where a shipped strategy's name and description live.
 *
 * `core:script` holds the strategies themselves — their ids, their warm-ups and their NamaScript —
 * and deliberately holds no prose. Everything a reader is shown has to exist in Persian and in
 * English, and a library module with no resource directory can only carry one literal string, which
 * would leave every English reader choosing between eleven Persian names.
 *
 * So the two halves are joined here, by id. The `when` is exhaustive by construction: [nameOf]
 * throws on an id it does not know, and `StrategyTextTest` walks `ScriptStrategies.ALL` through it,
 * so a strategy added without its twinned strings fails the build rather than reaching a screen as
 * a blank row.
 */
@StringRes
internal fun nameOf(strategy: ScriptStrategy): Int = when (strategy.id) {
    "hull-cross" -> R.string.script_strategy_hull_cross_name
    "supertrend-flip" -> R.string.script_strategy_supertrend_flip_name
    "channel-breakout" -> R.string.script_strategy_channel_breakout_name
    "band-reversion" -> R.string.script_strategy_band_reversion_name
    "trend-pullback" -> R.string.script_strategy_trend_pullback_name
    "triple-confluence" -> R.string.script_strategy_triple_confluence_name
    "confluence-score" -> R.string.script_strategy_confluence_score_name
    "supertrend-momentum" -> R.string.script_strategy_supertrend_momentum_name
    "channel-momentum" -> R.string.script_strategy_channel_momentum_name
    "ichimoku-cloud" -> R.string.script_strategy_ichimoku_cloud_name
    "directional-strength" -> R.string.script_strategy_directional_strength_name
    else -> error("«${strategy.id}» نامی در منابع ندارد")
}

@StringRes
internal fun descriptionOf(strategy: ScriptStrategy): Int = when (strategy.id) {
    "hull-cross" -> R.string.script_strategy_hull_cross_description
    "supertrend-flip" -> R.string.script_strategy_supertrend_flip_description
    "channel-breakout" -> R.string.script_strategy_channel_breakout_description
    "band-reversion" -> R.string.script_strategy_band_reversion_description
    "trend-pullback" -> R.string.script_strategy_trend_pullback_description
    "triple-confluence" -> R.string.script_strategy_triple_confluence_description
    "confluence-score" -> R.string.script_strategy_confluence_score_description
    "supertrend-momentum" -> R.string.script_strategy_supertrend_momentum_description
    "channel-momentum" -> R.string.script_strategy_channel_momentum_description
    "ichimoku-cloud" -> R.string.script_strategy_ichimoku_cloud_description
    "directional-strength" -> R.string.script_strategy_directional_strength_description
    else -> error("«${strategy.id}» توضیحی در منابع ندارد")
}
