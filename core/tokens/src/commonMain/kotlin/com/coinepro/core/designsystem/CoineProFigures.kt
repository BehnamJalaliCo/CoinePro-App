package com.coinepro.core.designsystem

/**
 * Tabular figures, so a column of prices lines up and a ticking price does not shift its neighbours.
 *
 * Here rather than beside the typeface in `CoineProType.kt`, because the typeface is an Android
 * resource and this is a string: the browser's chart asks for the same OpenType feature and should
 * not have to depend on a module with fonts in `res/` to spell four letters.
 */
const val TABULAR_FIGURES = "tnum"
