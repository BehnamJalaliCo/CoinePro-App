package com.coinepro.core.designsystem

/**
 * How a figure is drawn when the reader has asked for it not to be.
 *
 * ### Dots, not a blank
 *
 * The mask keeps the shape of what it hides. A hidden balance drawn as an empty space reads as an
 * account with nothing in it, or as a screen that failed to load — both of which are worse than the
 * number. A run of dots reads as "this is deliberately not being shown", which is the whole point.
 *
 * ### The same width every time
 *
 * [MASK] is a fixed six dots regardless of the figure behind it, and that is a decision rather than
 * laziness. A mask whose length tracked the real figure would leak its magnitude: a reader watching
 * over a shoulder learns whether the balance is four digits or seven, which is most of what they
 * wanted to know. Hiding a number badly is worse than not hiding it, because the reader believes
 * they are covered.
 *
 * The dot is U+2022, not an asterisk. Asterisks read as a password field; this is money.
 */
object CoineProPrivacy {

    /** What stands in for any hidden figure. */
    const val MASK: String = "••••••"

    /** [value] when [hidden] is false, the mask when it is true. */
    fun mask(value: String, hidden: Boolean): String = if (hidden) MASK else value

    /** The same, for a figure that may be absent. A missing value stays missing when masked. */
    fun mask(value: String?, hidden: Boolean, absent: String): String = when {
        value == null -> absent
        hidden -> MASK
        else -> value
    }
}
