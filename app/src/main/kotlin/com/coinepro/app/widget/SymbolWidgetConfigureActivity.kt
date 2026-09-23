package com.coinepro.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.coinepro.app.R
import com.coinepro.core.common.BidiText
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Which market should this tile show? — run Τ2, B7.
 *
 * ### Why this one asks and the markets widget does not
 *
 * `WidgetConfigureActivity` asks which *list* to follow and stores the answer once for every
 * widget, because a reader who starred their markets has already answered «which markets». There
 * is no such inherited answer for «which **one**»: it is a question only this widget can ask, and
 * the answer belongs to this widget id rather than to the app.
 *
 * ### The choice is the reader's own starred markets
 *
 * Not the whole catalogue. A picker over a few thousand tickers on a screen the launcher opened
 * for two seconds is a search problem, and the instruments somebody wants on their home screen are
 * — with near certainty — instruments they have already starred. A reader with an empty watchlist
 * is told so and sent to the app, rather than shown a list with nothing in it.
 *
 * `RESULT_CANCELED` first, for `WidgetConfigureActivity`'s reason: a configure activity that dies
 * without setting a result leaves a widget the launcher believes is configured and the app never
 * drew.
 */
@AndroidEntryPoint
class SymbolWidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var watchlist: WatchlistStore

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CoinePro)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            CoineProTheme {
                val lists by watchlist.lists().collectAsStateWithLifecycle(initialValue = emptyList())
                // Every starred market across every list, in the reader's own order, deduplicated.
                // One instrument can be in two lists and this screen is about instruments.
                val symbols = lists.flatMap { it.symbols }.distinct()
                SymbolChoiceScreen(
                    symbols = symbols,
                    initial = SymbolWidgetBridge.symbolFor(this, widgetId) ?: symbols.firstOrNull(),
                    onSave = { chosen ->
                        lifecycleScope.launch {
                            SymbolWidgetBridge.remember(this@SymbolWidgetConfigureActivity, widgetId, chosen)
                            // A fetch and a draw, so the new tile is not empty until the next
                            // quarter hour — the same courtesy the markets widget's configure
                            // screen pays.
                            WidgetRefreshWorker.requestNow(this@SymbolWidgetConfigureActivity)
                            SymbolWidget.refreshAll(this@SymbolWidgetConfigureActivity)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SymbolChoiceScreen(
    symbols: List<String>,
    initial: String?,
    onSave: (String) -> Unit,
) {
    var chosen by rememberSaveable(initial) { mutableStateOf(initial) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        Text(
            text = stringResource(R.string.widget_symbol_configure_title),
            style = MaterialTheme.typography.headlineSmall,
            color = CoineProColors.TextPrimary,
        )
        if (symbols.isEmpty()) {
            // Told, rather than shown an empty list with a disabled button under it. A reader with
            // nothing starred has a thing to go and do, and this says what it is.
            Text(
                text = stringResource(R.string.widget_symbol_configure_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.widget_symbol_configure_body),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            items(symbols, key = { it }) { symbol ->
                SymbolRow(
                    symbol = symbol,
                    selected = symbol == chosen,
                    onSelect = { chosen = symbol },
                )
            }
        }
        CoineProPrimaryButton(
            text = stringResource(R.string.widget_configure_save),
            onClick = { chosen?.let(onSave) },
            enabled = chosen != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SymbolRow(symbol: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            // A Latin ticker inside a right-to-left line, isolated so a row never reorders.
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.bodyLarge,
            color = CoineProColors.TextPrimary,
        )
    }
}
