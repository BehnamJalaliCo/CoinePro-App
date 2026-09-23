package com.coinepro.app.watch

import com.coinepro.core.chart.ChartWatch
import com.coinepro.core.chart.WatchSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one chart the picture-in-picture window is watching — run Τ2, B8.
 *
 * ### Why the snapshot has to be hoisted this far
 *
 * The activity is what enters the mode. `MainActivity` has no idea what a chart is, and the chart
 * is several routes deep inside a navigation graph that the mode may well tear down — a reader who
 * pops the window out and then goes home has left the chart's composition behind, and the window is
 * still there. So the chart pushes what it knows into this, and the window reads it. Nothing in
 * between has to stay composed.
 *
 * ### The rate limit is here, not in the composable
 *
 * `ChartWatch.shouldPublish` decides, and it decides at the *write*, which is the only place that
 * can actually stop the work. A composable that throttled its own reads would still be recomposed
 * forty times a second by the flow underneath it — the throttle has to be upstream of the state, or
 * it is decoration.
 *
 * In memory and nothing else: a snapshot is a picture of a moment, and a moment does not survive
 * the process that had it. There is nothing here worth writing to disk.
 */
@Singleton
class ChartWatchStore @Inject constructor() {

    private val _snapshot = MutableStateFlow<WatchSnapshot?>(null)
    val snapshot: StateFlow<WatchSnapshot?> = _snapshot.asStateFlow()

    private var lastAtMillis: Long? = null

    /**
     * Offers a snapshot, and keeps it only where it is worth keeping.
     *
     * Returns nothing: the caller is a chart publishing what it has, and whether this instant's
     * version made it through is not a fact the chart has any use for.
     */
    fun offer(next: WatchSnapshot, nowMillis: Long = System.currentTimeMillis()) {
        if (!ChartWatch.shouldPublish(_snapshot.value, next, lastAtMillis, nowMillis)) return
        _snapshot.value = next
        lastAtMillis = nowMillis
    }

    /**
     * Forgets the chart.
     *
     * Called when the reader leaves the mode. Without it a window opened tomorrow would flash
     * yesterday's price before the first snapshot lands — a stale number in the one place on the
     * screen there is no room to qualify it.
     */
    fun clear() {
        _snapshot.value = null
        lastAtMillis = null
    }
}
