package com.coinepro.feature.chart

/**
 * Whether a thing drawn on this chart can change after it has been drawn.
 *
 * ### Why this exists at all
 *
 * «ریپینت» is the accusation that decides whether a Persian-speaking trader trusts a study. It has
 * a precise meaning — a mark that appears on a bar and is then moved or removed once later bars
 * print, so a backtest of it is a backtest of hindsight — and it is levelled at every app in this
 * category, usually without evidence and occasionally with it. The community's own answer is not a
 * disclaimer at the bottom of a settings screen; it is a mark on the study itself.
 *
 * The reason to state it plainly is the same reason the venue is named under the chart: a reader
 * who cannot check anything has to decide whether to believe everything, and the app that says
 * least is the one that gets accused loudest. The one rule that makes the mark worth anything is
 * that it is never applied to something that repaints. A false «repaint نمی‌کند» is worse than no
 * mark at all — it converts an honest limitation into a lie the reader will eventually catch.
 *
 * ### The forming bar is the whole subtlety
 *
 * This chart draws the bar that is still open — that is what the countdown in the price gutter is
 * counting down to — so "computed only on closed bars" is a real distinction and not a pedantic
 * one. A pivot level is derived from the last *completed* session and cannot move while today
 * runs. A swing high is a bar that beat its neighbours on both sides, and one of those neighbours
 * may be the bar that is still printing, so the newest swing can still be taken back. Those are
 * two genuinely different promises and they get two different marks.
 *
 * Anything this file does not have an entry for gets **no mark**, not a guessed one. See
 * [RepaintClaims.of].
 */
enum class RepaintClaim {
    /**
     * Never revised. Drawn from closed bars only, and what is on the chart today will be on it
     * tomorrow unless the feed itself revises a bar.
     */
    SETTLED,

    /**
     * Confirmed a fixed number of bars late, and provisional only inside that window.
     *
     * A swing high needs the bars on its right to exist before it is a swing at all, so the study
     * is honest about the past and unsettled at the edge. This is not the same claim as [SETTLED]
     * and must not be dressed as one — a reader trading the newest mark is trading something that
     * can be withdrawn.
     */
    LATE,

    /**
     * The newest reading moves until the bar closes; nothing behind it changes.
     *
     * True of every ordinary study that takes one value per bar — the last point of a moving
     * average follows the live close. It is not repainting and readers do not call it that, but it
     * is also not a promise that the number on screen is final.
     */
    LIVE_BAR,

    /**
     * A reading already drawn can be rewritten by later bars.
     *
     * The zigzag's last leg extends, and every level derived from that leg moves with it. It is a
     * legitimate way to draw a chart and an illegitimate thing to backtest, and it is exactly what
     * the accusation is about — so it is named rather than hidden.
     */
    REPAINTS,
    ;

    /** The short mark, for a chip beside the study's name. */
    val label: String
        get() = when (this) {
            SETTLED -> "repaint نمی‌کند"
            LATE -> "با تأخیر قطعی می‌شود"
            LIVE_BAR -> "تا بسته‌شدن کندل تغییر می‌کند"
            REPAINTS -> "عقب‌تر بازنویسی می‌شود"
        }

    /** The sentence under it, which is what makes the mark checkable rather than reassuring. */
    val note: String
        get() = when (this) {
            SETTLED -> "فقط از کندل‌های بسته حساب می‌شود. آنچه رسم شده، جابه‌جا نمی‌شود."
            LATE -> "هر نشانه چند کندل بعد قطعی می‌شود. تازه‌ترین نشانه تا آن موقع ممکن است برداشته شود."
            LIVE_BAR -> "مقدارِ آخرین کندل تا بسته‌شدنش حرکت می‌کند. مقدارهای قبلی ثابت‌اند."
            REPAINTS -> "با آمدن کندل‌های تازه، بخشی از آنچه قبلاً رسم شده دوباره نوشته می‌شود. برای بک‌تست به آن تکیه نکنید."
        }

    /** Whether this is the claim the mark is *for*. Only these two are drawn as a trust mark. */
    val isTrustworthy: Boolean get() = this == SETTLED || this == LATE
}

/**
 * Which claim each thing on this chart may honestly make.
 *
 * Every entry below was read out of `core:chart` rather than assumed, and the two that repaint are
 * named as repainting. The list is deliberately short: an id with no entry answers null, and a
 * null draws nothing. Marking eighty-three indicators on the strength of "most of them are causal"
 * would be the same guess the mark exists to replace.
 */
object RepaintClaims {

    /**
     * The claim for one indicator id, or null where this file has not read the arithmetic.
     *
     * The structure studies are here because they are the ones a reader argues about — they place
     * levels and markers rather than a value per bar, so «آن خط از کجا آمد» is a question they
     * invite. Each verdict:
     *
     * * **pivots** — `Structure.sessionLevels` closes a bucket only when the next one opens and
     *   hands the closed bucket's levels forward. Nothing in it looks at the bar it is drawn on,
     *   let alone the one still printing. [RepaintClaim.SETTLED].
     * * **swings**, **fractals** — a bar that beat `left` behind and `right` ahead. The verdict is
     *   final once those bars are closed and provisional while one of them is the live bar.
     *   [RepaintClaim.LATE].
     * * **sr** — the same left/right test, clustered. Same window, same claim.
     * * **supplydemand** — a base bar is named by the bar *after* it, so it is one bar late and
     *   never revised afterwards. [RepaintClaim.LATE].
     * * **zigzag** — `Structure.zigzagSwings` appends the final swing although it is unconfirmed
     *   and price may still extend it; the last leg genuinely moves. [RepaintClaim.REPAINTS].
     * * **autofib** — every level is measured across that same last leg, so it inherits it.
     * * **chopzone** — an ordinary per-bar study on this chart's own bars. [RepaintClaim.LIVE_BAR].
     */
    fun of(indicatorId: String): RepaintClaim? = CLAIMS[indicatorId]

    /**
     * The claim an AI setup drawn over the bars may make.
     *
     * A `SignalOverlay` is three or four fixed prices and the moment they were issued. It is a
     * record of something the model said, not a function recomputed as bars arrive — nothing in
     * the app rewrites an issued setup — so it settles the moment it appears. That is worth saying
     * out loud precisely because a signal is the thing readers most expect to be quietly moved.
     */
    val SIGNAL: RepaintClaim = RepaintClaim.SETTLED

    /**
     * The claim the three-card reading row may make.
     *
     * ADX, ATR and two moving averages over the bars on screen, including the one still printing.
     * Its history is fixed and its current word can change before the bar closes, which is exactly
     * [RepaintClaim.LIVE_BAR] and is not the trust mark.
     */
    val READING: RepaintClaim = RepaintClaim.LIVE_BAR

    /**
     * Every claim this build is prepared to make, by indicator id.
     *
     * Public so the test can walk it: the rule that matters — that nothing marked [
     * RepaintClaim.SETTLED] or [RepaintClaim.LATE] is one of the two known repainters — is checked
     * against this map rather than against a copy of it in the test file.
     */
    val CLAIMS: Map<String, RepaintClaim> = mapOf(
        "pivots" to RepaintClaim.SETTLED,
        "swings" to RepaintClaim.LATE,
        "fractals" to RepaintClaim.LATE,
        "sr" to RepaintClaim.LATE,
        "supplydemand" to RepaintClaim.LATE,
        "zigzag" to RepaintClaim.REPAINTS,
        "autofib" to RepaintClaim.REPAINTS,
        "chopzone" to RepaintClaim.LIVE_BAR,
    )

    /**
     * The studies switched on right now that rewrite their own past, by id.
     *
     * What the chart has to be able to say in one line under the candles. Order follows the
     * catalogue's rather than the set's, so the sentence reads the same way twice running — a set
     * has no order and an unordered list of names shuffling between recompositions is the kind of
     * detail that makes a screen feel unreliable for no reason anybody can name.
     */
    fun repaintingAmong(activeIds: Collection<String>): List<String> =
        CLAIMS.entries.filter { it.value == RepaintClaim.REPAINTS && it.key in activeIds }.map { it.key }

    /** The same, for what may carry the mark. */
    fun trustedAmong(activeIds: Collection<String>): List<String> =
        CLAIMS.entries.filter { it.value.isTrustworthy && it.key in activeIds }.map { it.key }
}
