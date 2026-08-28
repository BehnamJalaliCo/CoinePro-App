package com.coinepro.app.widget

import android.content.Context
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.core.datastore.WidgetSnapshot
import com.coinepro.core.datastore.WidgetSnapshotStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * How a broadcast receiver reaches the app's dependency graph.
 *
 * ### Why an entry point and not `@AndroidEntryPoint`
 *
 * An `AppWidgetProvider` is a `BroadcastReceiver`, and Hilt can inject those — but only through a
 * receiver it constructs, and the system constructs this one. `EntryPointAccessors` is the
 * supported way in: it reaches the singleton component that already exists rather than building a
 * second graph.
 *
 * ### Why `runBlocking`, which is normally the wrong answer
 *
 * Because the caller is `onUpdate`, which is not a coroutine and cannot become one — the receiver
 * is torn down the moment it returns, so anything launched into a scope would be killed before it
 * finished. `goAsync` exists for this and buys ten seconds, but it is the wrong tool here for a
 * simpler reason: **this read does no I/O worth waiting for.** DataStore serves an already-loaded
 * preferences object, so the block is microseconds. The fetch that *does* touch the network is in
 * [WidgetRefreshWorker], where it belongs.
 *
 * Wrapped so a failure renders an empty widget rather than crashing the launcher's host process,
 * which is a thing a widget can genuinely do and which readers experience as their home screen
 * restarting.
 */
object WidgetSnapshotBridge {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetDependencies {
        fun widgetSnapshotStore(): WidgetSnapshotStore
        fun userPreferencesStore(): UserPreferencesStore
    }

    fun read(context: Context): WidgetSnapshot = runCatching {
        runBlocking { dependencies(context).widgetSnapshotStore().read() }
    }.getOrDefault(WidgetSnapshot())

    fun colours(context: Context): MarketColorScheme = runCatching {
        runBlocking { dependencies(context).userPreferencesStore().marketColors.first() }
    }.getOrDefault(MarketColorScheme.GREEN_UP)

    private fun dependencies(context: Context): WidgetDependencies =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependencies::class.java,
        )
}
