package com.coinepro.app

import com.coinepro.core.common.SiteAssets
import com.coinepro.core.designsystem.LogoProvider
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier

/**
 * `/assets/logo/<SYMBOL>.webp` on the API host's **site**, for a symbol the artwork does not draw —
 * the phone and the page alike.
 *
 * The forex host publishes the forex, metal and index marks and no coin's. A coin the artwork does
 * not draw asked it anyway: the 5.24.2 browser check counted a 404 per screener row for
 * `NILUSDT.webp` and its neighbours. A coin now asks nobody and keeps its fallback.
 */
internal fun siteLogos(apiBaseUrl: String): LogoProvider = LogoProvider { symbol ->
    if (SymbolClassifier.classify(symbol).category == SymbolCategory.CRYPTO) {
        null
    } else {
        SiteAssets.url(apiBaseUrl, "assets/logo/${symbol.uppercase()}.webp")
    }
}
