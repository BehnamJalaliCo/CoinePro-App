package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The strategy tester as a page of its own (5.26.0), the menu's «بک‌تست».
 *
 * It was one row with NamaScript — «بک‌تست و اسکریپت» — that opened the script editor, and the
 * tester itself was reachable only from a chart's toolbar. They are two jobs: one writes an
 * indicator, the other asks how a strategy did over the past. This page is the same report the
 * chart's sheet draws, over the same controller, so a run here and a run from the chart agree.
 */
@Composable
fun BacktestScreen(controller: ChartController, modifier: Modifier = Modifier) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Text(
            text = stringResource(R.string.chart_sheet_backtest) + " · " + state.symbol,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        )
        BacktestSheetBody(
            bars = state.series.bars,
            symbol = state.symbol,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            hasMoreHistory = state.hasMore,
            loadingHistory = state.loadingMore,
            onLoadMoreHistory = controller::loadMore,
            loadMagnifier = controller::magnifierBars,
        )
    }
}
