package com.coinepro.core.common

/**
 * A label with how many of it there are.
 *
 * ### Why this is not `"$label · $count"`
 *
 * **The Persian zero is a dot.** «۰» and «·» are the same shape at the same height, and Persian
 * numerals are laid out left to right inside a right-to-left line — so a middle dot ending up
 * against a numeral does not read as a separator, it reads as another digit.
 *
 * The journal's tag cloud is where this was found. Three entries, one tag each, and every chip said
 * «شکست ۱۰» — a count of one, a separator, and a font in which those two marks are indistinguishable
 * from ten. Nothing in the string was wrong and nothing in the layout was wrong; the two characters
 * simply compose into a third meaning on screen.
 *
 * Parentheses cannot: there is no numeral shaped like one, and the bidi algorithm mirrors them, so
 * the pair closes around the count in either direction.
 *
 * A separator is still right between two *words*, and between anything and a Latin figure — Latin
 * digits have no dot among them. This is only about a count that lands next to one.
 */
fun countedLabel(label: String, count: Int): String = "$label (${count.toPersianDigits()})"
