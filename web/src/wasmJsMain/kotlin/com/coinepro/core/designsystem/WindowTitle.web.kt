package com.coinepro.core.designsystem

import com.coinepro.core.common.BrandConfig
import com.coinepro.web.setDocumentTitle

/** The browser's twin of the phone's `WindowTitle`: the tab's title, as TradingView sets it. */
object WindowTitle {
    fun set(title: String) = setDocumentTitle(title)

    fun reset() = setDocumentTitle(BrandConfig.DISPLAY_NAME)
}
