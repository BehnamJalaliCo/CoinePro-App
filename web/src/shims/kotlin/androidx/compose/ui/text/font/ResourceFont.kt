package androidx.compose.ui.text.font

import com.coinepro.web.WebFonts
import com.coinepro.web.WebResources

/**
 * `Font(R.font.x, weight)` — the phone's way of naming a bundled face. In the browser the bytes are
 * fetched before the first frame (`WebFonts.preload`), so the face this returns is already there.
 */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    WebFonts.font(WebResources.nameOf(resId), weight, style)

fun Font(resId: Int, weight: FontWeight, style: FontStyle, variationSettings: FontVariation.Settings): Font =
    Font(resId, weight, style)
