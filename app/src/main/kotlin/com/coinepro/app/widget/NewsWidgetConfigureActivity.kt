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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme

/**
 * The news widget's settings (5.27.0): which feed it reads. Opened when the widget is placed on a
 * launcher that asks, and from the gear in its header on every launcher.
 */
class NewsWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CoinePro)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = NewsWidgetStore(this)
        setContent {
            CoineProTheme {
                var chosen by rememberSaveable { mutableStateOf(store.feedFor(widgetId).id) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(CoineProSpacing.Gutter),
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
                ) {
                    Text(
                        text = stringResource(R.string.widget_news_configure_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    NewsFeed.entries.forEach { feed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = feed.id }
                                .padding(vertical = CoineProSpacing.Half),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        ) {
                            RadioButton(
                                selected = chosen == feed.id,
                                onClick = { chosen = feed.id },
                                colors = RadioButtonDefaults.colors(selectedColor = CoineProColors.AccentFill),
                            )
                            Text(
                                text = stringResource(if (feed == NewsFeed.CRYPTO) R.string.widget_news_crypto else R.string.widget_news_forex),
                                style = MaterialTheme.typography.titleSmall,
                                color = CoineProColors.TextPrimary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    CoineProPrimaryButton(
                        text = stringResource(R.string.widget_news_configure_save),
                        onClick = {
                            store.setFeed(widgetId, NewsFeed.of(chosen))
                            WidgetRefreshWorker.schedule(this@NewsWidgetConfigureActivity)
                            WidgetRefreshWorker.requestNow(this@NewsWidgetConfigureActivity)
                            NewsWidget.refreshAll(this@NewsWidgetConfigureActivity)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
