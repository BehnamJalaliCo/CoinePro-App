package com.coinepro.core.model

/**
 * What a reader chose to be seen as.
 *
 * An avatar in this app is not one picture. It is a **base** — the thing in the middle — inside a
 * **ring** that colours the edge, and the two are chosen separately because they answer different
 * questions: the base is "who am I", the ring is "what am I here for". A gold ring around a
 * gold disc is a subscriber; the same disc in the analysis blue is somebody who reads charts.
 *
 * Four bases, and the list is closed on purpose.
 *
 * * [AvatarBase.Initial] — the reader's own letter. The default, and the honest one for anybody who
 *   has not chosen: it says whose account this is without inventing a face.
 * * [AvatarBase.Photo] — their own image. Held as a **path inside the app's own storage**, never as
 *   a content URI: a URI is a loan from another app that expires on reboot, and an avatar that
 *   silently blanks a week later is worse than one that was never offered.
 * * [AvatarBase.Symbol] — an instrument, drawn with the same artwork the market rows use. This is
 *   the element set the app supplies and it is a real one: five hundred and sixty crypto marks,
 *   twenty-eight currency flags and four metal discs, already shipped, already correct.
 * * [AvatarBase.Mark] — one of [AvatarMark], the app's own drawn marks, which move.
 *
 * There is deliberately no "upload anything and we will host it". Nothing here leaves the device.
 */
sealed interface AvatarBase {

    /** The reader's own first letter, tinted. The default. */
    data object Initial : AvatarBase

    /**
     * An image the reader picked, copied into the app's own files directory.
     *
     * [path] is absolute and belongs to this app. See the note above about why it is not a URI.
     */
    data class Photo(val path: String) : AvatarBase

    /**
     * An instrument — `BTC`, `XAUUSD`, `EURUSD`.
     *
     * Stored as the wire symbol rather than as a drawable id, so the artwork table can be
     * regenerated without every saved avatar pointing at a resource that moved.
     */
    data class Symbol(val symbol: String) : AvatarBase

    /** One of the app's own marks. */
    data class Mark(val mark: AvatarMark) : AvatarBase
}

/**
 * The marks the app draws itself, each with one small motion.
 *
 * These are the "animated emoji" of the set, and they are drawn rather than borrowed for two
 * reasons. A licensed emoji font cannot be redrawn at avatar size without looking like a sticker
 * pasted on a trading app, and an animated GIF would mean a decoder, a cache and a frame loop for
 * something forty pixels across.
 *
 * Every one of them is about this market and nothing else: no smileys, no hearts, no thumbs. A
 * profile picture in a trading app is a small flag somebody plants, and the set they can plant
 * should be made of the things they are actually here for.
 *
 * The motion is one gesture, once every few seconds, and it stops entirely when the device has
 * animations turned off — see `continuousMotionAllowed()`.
 */
enum class AvatarMark {
    /** A rocket, with a flame that breathes. */
    ROCKET,

    /** A bull's head, the market's own word for somebody who buys. */
    BULL,

    /** A bear's head, the other half of that sentence. */
    BEAR,

    /** A single candle, whose wick grows and settles. */
    CANDLE,

    /** A cut diamond, with a highlight that travels across a facet. */
    DIAMOND,

    /** A flame. */
    FLAME,

    /** A bolt. */
    BOLT,

    /** A trend line that draws itself and starts again. */
    TREND,

    /** A shield — for the readers whose whole point is that they did not lose. */
    SHIELD,

    /** The globe of a market that never closes. */
    GLOBE,
}

/**
 * The edge.
 *
 * Six, mapped onto colours the app already means something by, so a ring is never a decoration
 * picked from a wheel: gold is the brand and the subscription, blue is analysis, green and red are
 * the two sides of a trade, and none is none.
 */
enum class AvatarRing { NONE, GOLD, PREMIUM, ANALYSIS, BUY, SELL }

/**
 * One reader's chosen appearance.
 *
 * Serialised as a short delimited string rather than JSON: it goes into a preferences store beside
 * a watchlist and a platform choice, and a parser that cannot throw is worth more here than a
 * format that can express things this type cannot.
 */
data class AvatarSpec(
    val base: AvatarBase = AvatarBase.Initial,
    val ring: AvatarRing = AvatarRing.GOLD,
) {
    companion object {
        /** The unchosen avatar. Every reader starts here and most will stay. */
        val Default = AvatarSpec()

        private const val FIELD = "|"
        private const val ARG = ":"

        /**
         * `initial|GOLD`, `symbol:BTC|ANALYSIS`, `mark:ROCKET|BUY`, `photo:/data/…/a.png|NONE`.
         *
         * A path can contain neither of the two delimiters on Android — files here are named by
         * this app — so no escaping is needed and none is pretended.
         */
        fun encode(spec: AvatarSpec): String {
            val base = when (val current = spec.base) {
                AvatarBase.Initial -> "initial"
                is AvatarBase.Photo -> "photo" + ARG + current.path
                is AvatarBase.Symbol -> "symbol" + ARG + current.symbol
                is AvatarBase.Mark -> "mark" + ARG + current.mark.name
            }
            return base + FIELD + spec.ring.name
        }

        /**
         * The inverse, and it never throws.
         *
         * Anything unreadable — a format from a future release, a truncated write, an empty
         * string — decodes to [Default]. A profile screen that crashed on its own stored value
         * would be unrecoverable without clearing the app's data.
         */
        fun decode(encoded: String?): AvatarSpec {
            val text = encoded?.trim().orEmpty()
            if (text.isEmpty()) return Default
            val parts = text.split(FIELD)
            val ring = parts.getOrNull(1)
                ?.let { name -> AvatarRing.entries.firstOrNull { it.name == name } }
                ?: AvatarRing.GOLD
            val head = parts.firstOrNull().orEmpty()
            val kind = head.substringBefore(ARG)
            val argument = head.substringAfter(ARG, missingDelimiterValue = "")
            val base = when {
                kind == "photo" && argument.isNotBlank() -> AvatarBase.Photo(argument)
                kind == "symbol" && argument.isNotBlank() -> AvatarBase.Symbol(argument.uppercase())
                kind == "mark" ->
                    AvatarMark.entries.firstOrNull { it.name == argument }
                        ?.let(AvatarBase::Mark)
                        ?: AvatarBase.Initial
                else -> AvatarBase.Initial
            }
            return AvatarSpec(base = base, ring = ring)
        }
    }
}
