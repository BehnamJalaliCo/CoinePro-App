package com.coinepro.feature.chart

import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.datastore.ChartEventPrefsStore

/**
 * The reader's axis-event switches, on disk and back.
 *
 * ### Why a mapper exists at all
 *
 * Two modules describe the same five kinds and neither may depend on the other. `core:chart` owns
 * [EventKind], because the placement maths and the renderer need it; `core:datastore` owns the
 * stored ids, because a preferences store that imported an enum from the chart layer would drag the
 * whole chart onto the classpath of every screen that reads a preference. Both are right, and the
 * cost is one translation — which belongs here, at the screen that is the only thing holding both.
 *
 * ### Why this had to be written before the setting worked at all
 *
 * `ChartEventPrefsStore` was written, tested, provided in Hilt — and read by nothing. Nothing in
 * the app ever called `ChartEventController.restoreVisibility`, so a reader who switched the
 * economic calendar on watched it come back off at the next launch, with no way to tell that from
 * a switch that had never worked. This is the half of that gap that lives in a module this screen
 * owns; the other half is one parameter at the call site — see the wiring note on `ChartScreen`.
 *
 * ### An unknown id is dropped, never guessed
 *
 * The store deliberately keeps ids it does not recognise, so a downgrade does not throw away a
 * newer build's sixth kind. This build cannot draw one, so it ignores it — and it must ignore it
 * rather than mapping it onto something it can draw, which would put marks on an axis for a kind
 * the reader never switched on.
 */
internal object ChartEventKinds {

    /** How one kind is spelled on disk, or null for a kind this store has no id for. */
    fun idOf(kind: EventKind): String? = when (kind) {
        EventKind.NEWS -> ChartEventPrefsStore.KIND_NEWS
        EventKind.ECONOMIC -> ChartEventPrefsStore.KIND_ECONOMIC
        EventKind.EARNINGS -> ChartEventPrefsStore.KIND_EARNINGS
        EventKind.DIVIDEND -> ChartEventPrefsStore.KIND_DIVIDEND
        EventKind.SPLIT -> ChartEventPrefsStore.KIND_SPLIT
    }

    /** The filter a stored set of ids means. An empty set is «all off», which is a real choice. */
    fun visibility(stored: Set<String>): EventVisibility = EventVisibility(
        EventKind.entries.filterTo(mutableSetOf()) { kind -> idOf(kind) in stored },
    )

    /**
     * Which kinds changed between two filters, with what they changed to.
     *
     * The store writes one kind at a time — that is what keeps a reader's unknown sixth kind
     * untouched — so a whole-filter change from the settings sheet has to be turned back into the
     * switches that actually moved. Writing all five every time would work and would also rewrite
     * the row on every tap, which is four writes nobody asked for and one chance in four of racing
     * a concurrent edit for nothing.
     */
    fun changes(from: EventVisibility, to: EventVisibility): List<Pair<String, Boolean>> =
        EventKind.entries.mapNotNull { kind ->
            val on = to.isOn(kind)
            if (on == from.isOn(kind)) null else idOf(kind)?.let { id -> id to on }
        }
}
