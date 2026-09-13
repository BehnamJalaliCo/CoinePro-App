package com.coinepro.core.common

import kotlin.math.abs

/**
 * **A reason to come back** — «since your last visit», today's challenge, the streak
 * (run Σ, S5; doctrine D7).
 *
 * ### What this is for, said plainly
 *
 * A charting app is a tool, and a tool is opened when it is needed. That is a perfectly good way to
 * be used and a poor way to be remembered: a reader who opens the app once a fortnight never learns
 * what it can do, and the work in Ω and Σ — the Signal Layer, the Explain sheet, the scripts —
 * reaches them only if they are there to see it.
 *
 * So the top of Home answers one question: **what happened while I was away, and what is worth two
 * minutes now.** Three parts, and all three have to be honest:
 *
 * * [SinceLastVisit] — what actually moved, with the numbers. Never a summary of nothing.
 * * [DailyChallenge] — one task, the same for everybody on a given day, drawn from a fixed table by
 *   the date. Not a nudge invented to fill a card.
 * * The streak, which the arena already keeps.
 *
 * ### Why it is here rather than in the Home screen
 *
 * Because the deciding is the part with rules in it — what counts as «something happened», what
 * counts as nothing, and what a challenge is on a day nobody has opened the app — and those are
 * testable only if they are not tangled with a layout. `ReturnLoopTest` is D7's gate.
 *
 * Pure Kotlin: the web build gets the same loop.
 */
object ReturnLoop {

    /**
     * A move worth mentioning, as a share of the price.
     *
     * Two per cent, because a watchlist row that moved one per cent since yesterday is a row that
     * moved by the width of its own candle, and a card that announced it would be a card a reader
     * learns to ignore — which is worse than no card.
     */
    const val WORTH_MENTIONING = 0.02

    /** How many movers the card names. Three, because a list is not news. */
    const val MOVERS_SHOWN = 3

    /** Below this many hours away, there is no «since your last visit» — they were just here. */
    const val AWAY_HOURS = 4

    /**
     * What changed while the reader was away.
     *
     * [movers] are their own watchlist's biggest moves since [lastVisitEpochMillis], [signals] is
     * how many signals fired on those symbols, and [alerts] how many of their own alerts went off.
     *
     * The null case is not an error and not an empty card: it is «nothing happened», and the screen
     * shows something else entirely. A card that says «no news» every morning teaches a reader that
     * the top of Home is noise.
     */
    fun sinceLastVisit(
        lastVisitEpochMillis: Long,
        nowEpochMillis: Long,
        movers: List<SymbolMove>,
        signals: Int = 0,
        alerts: Int = 0,
    ): SinceLastVisit? {
        if (lastVisitEpochMillis <= 0L) return null
        val awayMillis = nowEpochMillis - lastVisitEpochMillis
        if (awayMillis < AWAY_HOURS * 3_600_000L) return null
        val worth = movers
            .filter { abs(it.change) >= WORTH_MENTIONING }
            .sortedByDescending { abs(it.change) }
            .take(MOVERS_SHOWN)
        if (worth.isEmpty() && signals == 0 && alerts == 0) return null
        return SinceLastVisit(
            awayMillis = awayMillis,
            movers = worth,
            signals = signals,
            alerts = alerts,
        )
    }

    /**
     * Today's challenge: the same one for everybody, decided by the date and nothing else.
     *
     * Not random and not personalised. Random would mean a reader who closes and reopens the app
     * gets a different task, which makes the whole thing feel arbitrary; personalised would mean
     * the app choosing what somebody needs to practise, which it is in no position to know.
     *
     * The date is the seed, so two readers comparing notes see the same challenge — which is the
     * only thing that makes it *a* challenge rather than a chore.
     */
    fun challengeFor(epochDay: Long, english: Boolean = false): DailyChallenge {
        // Not `epochDay % size`: the table is walked in a stride coprime with its length, so
        // consecutive days are not consecutive entries and a reader does not get the three
        // scripting tasks in a row on the three days they happen to open it.
        val index = ((epochDay * STRIDE) % CHALLENGES.size + CHALLENGES.size) % CHALLENGES.size
        val entry = CHALLENGES[index.toInt()]
        return DailyChallenge(
            id = entry.id,
            title = if (english) entry.titleEn else entry.title,
            surface = entry.surface,
        )
    }

    /**
     * Whether a streak survives [today] given the day it was last kept.
     *
     * A day's grace, deliberately. A streak that breaks because somebody was asleep at midnight in
     * the wrong time zone is a streak that teaches them the number is not about them. Two days and
     * it is over, which is what keeps it worth anything.
     */
    fun streakAfter(current: Int, lastKeptEpochDay: Long, today: Long): Int = when {
        lastKeptEpochDay <= 0L -> 0
        today == lastKeptEpochDay -> current
        today == lastKeptEpochDay + 1 -> current
        else -> 0
    }

    /** One entry in the challenge table, in both languages. */
    private class Challenge(
        val id: String,
        val title: String,
        val titleEn: String,
        val surface: ChallengeSurface,
    )

    /**
     * A stride coprime with the table's length, so the walk visits every entry before repeating.
     *
     * Seven and twelve share no factor, so day + 1 moves seven places and the cycle is the full
     * twelve days rather than a short loop.
     */
    private const val STRIDE = 7L

    /**
     * Twelve tasks, each of which takes about a minute and teaches one thing the app does.
     *
     * Twelve because that is a fortnight of not repeating, and because a longer table would be a
     * list nobody finished writing properly. Every one of them is a thing a reader can actually do
     * today with no account and no money.
     */
    private val CHALLENGES: List<Challenge> = listOf(
        Challenge("read-a-signal", "یک سیگنال را باز کن و ببین چرا", "Open one signal and see why", ChallengeSurface.CHART),
        Challenge("add-a-study", "یک اندیکاتور تازه به چارت اضافه کن", "Put one new study on the chart", ChallengeSurface.CHART),
        Challenge("explain-sheet", "برگه‌ی «چرا» را برای یک اندیکاتور بخوان", "Read the Explain sheet for one study", ChallengeSurface.CHART),
        Challenge("paste-a-script", "یک اسکریپت از هوش مصنوعی بگیر و اجرا کن", "Get a script from an AI and run it", ChallengeSurface.SCRIPT),
        Challenge("save-a-script", "یک اسکریپت را به نام خودت ذخیره کن", "Save one script as your own", ChallengeSurface.SCRIPT),
        Challenge("library-strategy", "یک استراتژی از کتابخانه را امتحان کن", "Try one strategy from the library", ChallengeSurface.SCRIPT),
        Challenge("set-an-alert", "یک هشدار روی قیمتی که برایت مهم است بگذار", "Set an alert on a price you care about", ChallengeSurface.ALERTS),
        Challenge("journal-a-trade", "یک معامله را در دفترچه بنویس", "Write one trade in the journal", ChallengeSurface.JOURNAL),
        Challenge("paper-trade", "یک معامله‌ی آزمایشی باز کن", "Open one paper trade", ChallengeSurface.PAPER),
        Challenge("replay-arena", "یک دور در میدان بازپخش بازی کن", "Play one round in the replay arena", ChallengeSurface.ARENA),
        Challenge("watchlist-tidy", "دیده‌بانت را مرتب کن", "Tidy your watchlist", ChallengeSurface.WATCHLIST),
        Challenge("compare-two", "دو نماد را کنار هم بگذار", "Put two symbols side by side", ChallengeSurface.CHART),
    )
}

/**
 * What happened while the reader was away.
 *
 * Every field is a fact with a number behind it. There is deliberately no «summary» string: the
 * screen writes the sentence in the reader's language, and a sentence composed here would bake one
 * language's word order into a model.
 */
data class SinceLastVisit(
    val awayMillis: Long,
    val movers: List<SymbolMove>,
    val signals: Int,
    val alerts: Int,
) {
    /** Roughly how long they were away, in whole hours, for «۹ ساعت پیش». */
    val awayHours: Int get() = (awayMillis / 3_600_000L).toInt()

    val awayDays: Int get() = (awayMillis / 86_400_000L).toInt()
}

/** One symbol and what it did, as a share: `0.043` is up four and a bit per cent. */
data class SymbolMove(val symbol: String, val change: Double) {
    val isUp: Boolean get() = change >= 0
}

/** Today's one task. [surface] is where it happens, so the card can carry a button. */
data class DailyChallenge(val id: String, val title: String, val surface: ChallengeSurface)

/** Where a challenge is done. The screen maps these to routes; this module knows no routes. */
enum class ChallengeSurface { CHART, SCRIPT, ALERTS, JOURNAL, PAPER, ARENA, WATCHLIST }
