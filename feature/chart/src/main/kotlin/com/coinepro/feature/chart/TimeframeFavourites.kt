package com.coinepro.feature.chart

import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.of

/**
 * Which bar lengths sit one tap from the chart, and which ones the picker still offers.
 *
 * ### The problem
 *
 * The strip under the chart drew six lengths and the six were a constant in the source. They are a
 * defensible six — they are the set the keyboard binds and the set chart vision reads — and they
 * are still somebody else's six. A reader who works the two-hour and the weekly had M1 and M5
 * permanently under their thumb and their own two lengths two taps away behind «بیشتر», forever,
 * with nothing on the screen suggesting that could change.
 *
 * ### Where the answer lives, and what this file is for
 *
 * `IntervalFavouritesStore` holds it, in wire spellings, and that store already decides the one
 * genuinely subtle thing: *never stored* and *stored blank* both mean the default six, while an
 * explicitly emptied selection is a sentinel and is honoured. None of that is re-decided here — a
 * second opinion about it is how a reader's deliberate empty bar quietly refills itself.
 *
 * What is left is the part the store cannot do, because it may not depend on `core:marketdata`:
 * turning wires into intervals, ordering them, keeping whatever is in force on the strip, and the
 * cap. That is this file.
 *
 * ### The rules
 *
 * * **Whatever is in force is always shown.** A reader on H2 who has not starred H2 must still be
 *   able to see what they are looking at from the control that sets it.
 * * **The order is the store's.** It appends, so the strip grows to the right as the reader stars
 *   things and never rearranges under a thumb that has learned where the pills are.
 * * **A wire this build cannot resolve is dropped, not fatal.** A row written by a later build can
 *   name a length this one has not got, and losing one pill is better than losing the strip.
 */
object TimeframeFavourites {

    /**
     * The set a reader gets before they have starred anything.
     *
     * The store's own default, not a second copy of it. Two lists that have to agree is one list
     * too many, and the failure — a strip showing six lengths while the store believes in a
     * different six — would only appear on the first star.
     */
    val DEFAULT: List<String> = IntervalFavouritesStore.DEFAULT_FAVOURITES

    /**
     * The most lengths the strip will carry.
     *
     * Eight pills plus «بیشتر» is about the width of a phone. Past that the strip scrolls, and a
     * scrolling strip has the same fault the fifteen-pill version had: the length somebody wants is
     * off the edge and they cannot see that it is there. The store imposes no cap of its own — it
     * is a storage layer and has no idea how wide a phone is — so this is enforced at the star,
     * where the reader can see the answer.
     */
    const val MAX = 8

    /** Whether one more length may be pinned. False silences the star rather than raising a message. */
    fun canStar(starred: List<String>): Boolean = starred.size < MAX

    /**
     * Whether this length may be unstarred.
     *
     * True even for the last one. Unlike the cap, emptying the bar is a thing the store explicitly
     * supports and records as a sentinel, and refusing it here would be this file overruling the
     * setting it is reading. A reader who empties the bar keeps «بیشتر», which is every length.
     */
    fun canUnstar(starred: List<String>): Boolean = starred.isNotEmpty()

    /**
     * What the strip draws: the starred lengths, resolved, plus whatever is in force.
     *
     * The selected interval is appended rather than sorted into place, because it may be a minute
     * count the reader typed and «۲۰۵» does not belong between the three-hour and the four-hour in
     * any sense a reader would recognise. It goes last, next to «بیشتر», which is where it came from.
     */
    fun resolve(starred: List<String>, selected: ChartInterval): List<ChartInterval> {
        val pinned = starred.mapNotNull { wire -> ChartInterval.of(wire) }
        return if (pinned.any { it == selected }) pinned else pinned + selected
    }

    /**
     * The presets the picker sheet should still offer, given what the reader has struck out.
     *
     * Hiding is the other half of the setting and the one that makes fifteen presets bearable:
     * somebody who never trades below the hour can take eight rows out of the sheet. The length in
     * force is never hidden from it, because the sheet is also where a reader goes to *leave* a
     * length, and one they cannot see is one they cannot leave by the obvious route.
     */
    fun offered(presets: List<ChartInterval>, hidden: Set<String>, selected: ChartInterval): List<ChartInterval> =
        presets.filter { it.wire !in hidden || it == selected }
}
