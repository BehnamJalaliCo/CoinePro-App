package com.coinepro.core.datastore

/**
 * Which colour a rising price is drawn in.
 *
 * ### This is not a preference about taste
 *
 * Green-for-up is the convention in Europe and the Americas and is what every exchange this app
 * talks to assumes. It is **the opposite** of the convention across most of East Asia, where red
 * is the auspicious colour and marks a rise; a reader who learned the market on a Chinese, Korean
 * or Japanese terminal reads a green candle as a fall, instantly and without thinking about it.
 * Binance, OKX and Bybit all ship this switch for exactly that reason, and every one of them
 * defaults it per region rather than shipping one answer.
 *
 * Getting it wrong is not a cosmetic error. It inverts the meaning of every candle, every
 * percentage, every profit-and-loss figure and every signal direction in the product at once.
 *
 * ### The default is green-up, and it stays that way
 *
 * This app's readers are Persian-speaking and Iran follows the Western convention — the Tehran
 * exchange draws rises in green. So [GREEN_UP] is right for almost everyone here, and the switch
 * exists for the reader it is not right for rather than as a coin toss.
 */
enum class MarketColorScheme(
    /** Stable key for storage. Never localise, never reuse. */
    val id: String,
) {
    /** A rise is green and a fall is red. The default. */
    GREEN_UP("green_up"),

    /** A rise is red and a fall is green — the East Asian convention. */
    RED_UP("red_up");

    companion object {
        fun fromId(id: String?): MarketColorScheme = entries.firstOrNull { it.id == id } ?: GREEN_UP
    }
}
