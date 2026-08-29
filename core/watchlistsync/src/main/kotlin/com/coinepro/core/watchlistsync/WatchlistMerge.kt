package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSnapshot
import com.coinepro.core.datastore.WatchlistStore

/**
 * What a merge produced, and what arrived from elsewhere in the course of it.
 *
 * The counts are not diagnostics — they are what the reader is told. "Synced" on its own is a
 * sentence that could equally mean nothing happened; «۲ فهرست و ۹ نماد از دستگاه دیگر آمد» is the
 * difference between a reader trusting this feature and a reader checking their lists by hand
 * afterwards to see whether it did anything.
 *
 * [listsAdopted] and [symbolsAdopted] count only what came **in** — lists and symbols this device
 * did not have. What went out is deliberately not counted: the reader already knows what is on the
 * phone in their hand, and a number for it would read as though it had been taken away.
 *
 * [listsDropped] is the tombstones this device had never seen honoured — a list deleted on the
 * other phone, disappearing here. It is counted separately because it is the one outcome of a sync
 * that removes something, and a reader who is not told it happened will read it as data loss.
 */
data class WatchlistMergeResult(
    val snapshot: WatchlistSnapshot,
    val listsAdopted: Int = 0,
    val symbolsAdopted: Int = 0,
    val listsDropped: Int = 0,
) {
    /** Whether anything at all came in from the other side. */
    val changed: Boolean get() = listsAdopted > 0 || symbolsAdopted > 0 || listsDropped > 0
}

/**
 * Reconciling this device's watchlists with the copy the server is holding.
 *
 * ### Why this is not last-write-wins
 *
 * Last-write-wins is one line and it is what a watchlist sync is usually built as. It is also the
 * rule under which a reader who spent a bus ride building a list on their tablet loses all of it
 * because they later opened the phone that had been sitting in a drawer. There is no way for them
 * to know it happened, no way to get it back, and the thing they lost is the only thing this app
 * ever asked them to build by hand. The two facts that make something better affordable are that
 * the server hands back a `version` on every read, and that its `409` carries the **whole current
 * document** rather than just a refusal — so a merge costs no extra round trip on a connection
 * that has just demonstrated it is unreliable.
 *
 * ### The rule, in the order it is applied
 *
 * 1. **Lists are unioned by id.** A list either side has and the other does not is kept. This is
 *    the rule the whole file exists for: a list made on the other phone survives a sync from this
 *    one, no matter which device wrote last.
 *
 * 2. **A deletion is a fact, not an absence.** `WatchlistStore.delete` leaves a tombstone — the
 *    id and the millisecond — and tombstones travel in the payload. A list is dropped when a
 *    tombstone for it is **at least as new as** the newest edit either side has for it. Without
 *    this, deleting a list would do nothing at all: the other device still holds it, rule 1 would
 *    take the union, and the list would be back within a minute of the next sync, which is a worse
 *    experience than no sync at all.
 *
 * 3. **An edit after a deletion wins.** If either side edited the list *after* the tombstone was
 *    written, the list comes back and the tombstone is discarded. Somebody adding symbols to a list
 *    is newer evidence about whether they want it than somebody having deleted it earlier, and the
 *    consequences of the two mistakes are not symmetric: a resurrected list is visible and deleted
 *    again in one tap, a wrongly deleted list is gone.
 *
 * 4. **Symbols inside a surviving list are unioned too.** The side that touched the list more
 *    recently supplies the order — that arrangement is the reader's most recent intent about it —
 *    and symbols only the other side had are appended, in their order, at the end. Appended and
 *    never interleaved: interleaving would rearrange a list the reader dragged into place, which is
 *    the one thing a watchlist must never do to itself.
 *
 * 5. **Name, columns, sort and flags go to the newer `updatedAt`.** These are the fields where a
 *    union means nothing — two names cannot both be the name — so they are the only place
 *    last-write-wins is used, and they are deliberately the cheap ones: every one of them is
 *    restored in a tap or two by a reader who notices. Flags are unioned per symbol first, with
 *    the newer side winning where both coloured the same ticker, so a flag set on one phone and a
 *    different flag set on the other cost one colour rather than the whole map.
 *
 * ### What this still loses — every merge rule loses something, and these are the ones
 *
 * * **A symbol removed on one device comes back**, if the other device still holds it. There are
 *   no per-symbol tombstones: a list runs to a thousand tickers, and remembering every removal
 *   from every list for ninety days is most of the 64 KB budget spent on things the reader threw
 *   away. The trade is deliberate and it is the right way round — an unwanted symbol is visible in
 *   the list and removed with one swipe, whereas a symbol quietly dropped by a merge is invisible
 *   and is discovered when an alert that was supposed to fire never does.
 *
 * * **A rename, a column choice, a sort or a flag made on the device with the slower clock is
 *   lost.** Rule 5 compares `updatedAt`, and `updatedAt` is a *device* clock — there is no server
 *   timestamp per list to use instead, because the server does not read the payload. A phone whose
 *   clock is a day fast wins every one of these conflicts until it is corrected. This is why rules
 *   1 to 4, which govern everything that cannot be recovered by hand, use a union and never a
 *   clock: a wrong clock costs a column layout here, never a list.
 *
 * * **A list deleted on one device and edited on another comes back**, carrying the other device's
 *   edits, by rule 3. This is a choice, not an oversight.
 *
 * * **A deletion older than the tombstone window stops propagating.** `WatchlistStore` keeps the
 *   newest fifty deletions for ninety days. A device that has been offline for longer than that
 *   re-uploads a list this one deleted, and rule 1 takes it back.
 *
 * * **Symbol order on the less-recently-edited side is discarded**, by rule 4 — its symbols keep
 *   their relative order but land after the other side's, not where the reader had dragged them.
 *
 * * **Over 64 KB nothing syncs at all.** The server never writes part of a document, so a reader
 *   past the cap keeps a perfectly working local watchlist and is told plainly that it is too
 *   large to copy. Losing the tail of somebody's lists silently would be far worse than not
 *   copying them.
 *
 * ### It is never destructive on its own
 *
 * Every path above either keeps local data or removes it because of an explicit tombstone the
 * reader created by deleting a list. Nothing here can empty a list, and nothing here can drop a
 * list that no one deleted. That property is what makes it safe for a sync to run without being
 * asked — which matters, because the moment a reader would most like this to have run is the
 * moment their old phone is already wiped.
 */
object WatchlistMerge {

    /**
     * @param local what is stored on this device right now.
     * @param remote what the server was holding, decoded from the payload.
     */
    fun merge(local: WatchlistSnapshot, remote: WatchlistSnapshot): WatchlistMergeResult {
        val tombstones = mergeTombstones(local.tombstones, remote.tombstones)
        val localById = local.lists.associateBy { it.id }
        val remoteById = remote.lists.associateBy { it.id }

        var listsAdopted = 0
        var symbolsAdopted = 0
        var listsDropped = 0
        val merged = mutableListOf<Watchlist>()
        val settings = mutableMapOf<String, WatchlistSettings>()
        val survivingTombstones = mutableMapOf<String, Long>()

        // Tombstone ids are walked as well as live ones. A deletion both devices have already
        // applied names no live list on either side, and dropping it here would stop it travelling
        // — so a third device that still holds that list would put it back on its next sync, and
        // keep putting it back forever. A tombstone stops travelling when it expires, and not
        // before.
        (localById.keys + remoteById.keys + tombstones.keys).forEach { id ->
            val mine = localById[id]
            val theirs = remoteById[id]
            val deletedAt = tombstones[id]
            val newestEdit = maxOf(mine?.updatedAt ?: 0L, theirs?.updatedAt ?: 0L)

            // The default list is exempt from every deletion path. `WatchlistStore.delete` refuses
            // it, so a tombstone naming it can only have come from a corrupted or hand-edited
            // document — and honouring one would strand every alert scoped to the watchlist,
            // which resolves membership by this id at evaluation time.
            if (deletedAt != null && deletedAt >= newestEdit && id != Watchlist.DEFAULT_LIST_ID) {
                survivingTombstones[id] = deletedAt
                if (mine != null) listsDropped++
                return@forEach
            }

            when {
                mine != null && theirs != null -> {
                    val (list, adopted) = union(mine, theirs)
                    merged += list
                    symbolsAdopted += adopted
                    settingsFor(id, mine, theirs, local, remote)?.let { settings[id] = it }
                }

                theirs != null -> {
                    merged += theirs
                    listsAdopted++
                    symbolsAdopted += theirs.symbols.size
                    remote.settings[id]?.let { settings[id] = it }
                }

                mine != null -> {
                    merged += mine
                    local.settings[id]?.let { settings[id] = it }
                }
            }
        }

        val ordered = order(merged)
        val orderedIds = ordered.map { it.id }.toSet()
        return WatchlistMergeResult(
            snapshot = WatchlistSnapshot(
                lists = ordered,
                settings = settings.filterKeys { it in orderedIds },
                tombstones = survivingTombstones,
            ),
            listsAdopted = listsAdopted,
            symbolsAdopted = symbolsAdopted,
            listsDropped = listsDropped,
        )
    }

    /** The newer of two records of the same deletion; every deletion either side knows about. */
    private fun mergeTombstones(mine: Map<String, Long>, theirs: Map<String, Long>): Map<String, Long> =
        (mine.keys + theirs.keys).associateWith { id ->
            maxOf(mine[id] ?: 0L, theirs[id] ?: 0L)
        }

    /**
     * One list held by both devices. Rules 4 and 5.
     *
     * @return the merged list, and how many of its symbols this device did not already have.
     */
    private fun union(mine: Watchlist, theirs: Watchlist): Pair<Watchlist, Int> {
        // A tie goes to this device. Two lists whose clocks agree to the millisecond are the same
        // list, and a reader is never surprised by their own phone's copy of their own name.
        val newer = if (theirs.updatedAt > mine.updatedAt) theirs else mine
        val older = if (newer === mine) theirs else mine
        val extras = older.symbols.filterNot { it in newer.symbols }
        val adopted = theirs.symbols.count { it !in mine.symbols }
        return Watchlist(
            id = mine.id,
            name = newer.name,
            symbols = (newer.symbols + extras).take(WatchlistStore.MAX_SYMBOLS),
            // The earlier of the two creation stamps, ignoring the zero a record written before
            // this store kept them reads back as. Taking the max would date a list to whenever the
            // second device first heard of it, which is not when the reader made it.
            createdAt = listOf(mine.createdAt, theirs.createdAt).filter { it > 0L }.minOrNull() ?: 0L,
            updatedAt = maxOf(mine.updatedAt, theirs.updatedAt),
        ) to adopted
    }

    /**
     * The flags, columns and sort of a list both devices hold. Rule 5.
     *
     * Returns null where the result is the plain default, so that a list nobody has customised
     * contributes no settings entry at all — on either side of the wire or in the store.
     */
    private fun settingsFor(
        id: String,
        mine: Watchlist,
        theirs: Watchlist,
        local: WatchlistSnapshot,
        remote: WatchlistSnapshot,
    ): WatchlistSettings? {
        val localSettings = local.settings[id] ?: WatchlistSettings()
        val remoteSettings = remote.settings[id] ?: WatchlistSettings()
        val newerIsRemote = theirs.updatedAt > mine.updatedAt
        val newer = if (newerIsRemote) remoteSettings else localSettings
        val older = if (newerIsRemote) localSettings else remoteSettings
        val settings = WatchlistSettings(
            // Per symbol rather than wholesale: two devices that each coloured a different ticker
            // should end up with both colours, and only a ticker they disagree about costs one.
            flags = older.flags + newer.flags,
            columns = newer.columns,
            sort = newer.sort,
        )
        return settings.takeIf { it != WatchlistSettings() }
    }

    /**
     * The order the switcher will show these in.
     *
     * The store's own rule is "the default list first, then the order they were made", and a merge
     * has two stored orders and no way to interleave them — so creation time is what is left, and
     * it is the field that rule was always a proxy for. Ties break on id so that two devices
     * merging the same pair of lists arrive at the same order rather than at two orders that
     * disagree forever.
     */
    private fun order(lists: List<Watchlist>): List<Watchlist> = lists
        .sortedWith(compareBy({ !it.isDefault }, { it.createdAt }, { it.id }))
        .take(WatchlistStore.MAX_LISTS)
}
