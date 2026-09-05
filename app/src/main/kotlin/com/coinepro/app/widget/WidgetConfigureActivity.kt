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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.coinepro.app.R
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.datastore.WidgetSnapshotStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The screen the launcher opens when a widget is placed: which watchlist should it follow?
 *
 * Every watchlist the reader has, the starred one first, one radio each. The choice is stored
 * once for all widgets (see `WidgetSnapshotStore.preferredListId`), a refresh is asked for so the
 * new widget does not sit empty until the next half hour, and the launcher is told the widget is
 * ready. Cancelling — the back gesture — leaves the launcher's default answer in place, which is
 * "no widget", exactly as Android specifies.
 *
 * `RESULT_CANCELED` is set before anything else because a configure activity that dies without
 * setting a result leaves a widget the launcher believes is configured and the app never drew.
 */
@AndroidEntryPoint
class WidgetConfigureActivity : ComponentActivity() {

    @Inject lateinit var watchlist: WatchlistStore

    @Inject lateinit var snapshots: WidgetSnapshotStore

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
                val preferred by snapshots.preferredListId.collectAsStateWithLifecycle(initialValue = null)
                ConfigureScreen(
                    lists = lists,
                    initial = preferred ?: Watchlist.DEFAULT_LIST_ID,
                    onSave = { chosen ->
                        lifecycleScope.launch {
                            runCatching {
                                snapshots.setPreferredList(chosen.takeIf { it != Watchlist.DEFAULT_LIST_ID })
                            }
                            WidgetRefreshWorker.requestNow(this@WidgetConfigureActivity)
                            MarketsWidget.refreshAll(this@WidgetConfigureActivity)
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
private fun ConfigureScreen(
    lists: List<Watchlist>,
    initial: String,
    onSave: (String) -> Unit,
) {
    var chosen by rememberSaveable(initial) { mutableStateOf(initial) }
    // The starred list first whatever the store's order, because it is the one every reader has.
    val ordered = lists.sortedByDescending { it.isDefault }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        Text(
            text = stringResource(R.string.widget_configure_title),
            style = MaterialTheme.typography.headlineSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.widget_configure_body),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            items(ordered, key = Watchlist::id) { list ->
                ListRow(list = list, selected = list.id == chosen, onSelect = { chosen = list.id })
            }
        }
        CoineProPrimaryButton(
            text = stringResource(R.string.widget_configure_save),
            onClick = { onSave(chosen) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ListRow(list: Watchlist, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = CoineProColors.AccentFill),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (list.isDefault) stringResource(R.string.widget_configure_starred) else list.name,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            // A prose count: Persian digits in Persian, Latin in English.
            val english = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language) == AppLanguage.ENGLISH
            val count = if (english) list.symbols.size.toString() else list.symbols.size.toPersianDigits()
            Text(
                text = stringResource(R.string.widget_configure_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Spacer(modifier = Modifier.padding(end = 4.dp))
    }
}
