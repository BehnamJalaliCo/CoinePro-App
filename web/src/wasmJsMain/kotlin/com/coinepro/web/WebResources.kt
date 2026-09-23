package com.coinepro.web

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.coinepro.web.res.webResourceNames
import com.coinepro.web.res.webResourceValues

/** Generated `R` ids back to what they name. See `generateWebResources` in `web/build.gradle.kts`. */
object WebResources {
    private const val BASE = 0x7f000000

    /** `chart_title` for `R.string.chart_title` — the key into the string tables or the drawable's file name. */
    fun nameOf(id: Int): String = webResourceNames.getOrNull(id - BASE)?.substringAfter('/') ?: id.toString()

    fun kindOf(id: Int): String? = webResourceNames.getOrNull(id - BASE)?.substringBefore('/')

    /** The literal of a `color`/`bool`/`dimen`/`integer` resource. */
    fun valueOf(id: Int): String? = (if (Strings.persian) com.coinepro.web.res.webResourceValuesFa[id] else null) ?: webResourceValues[id]
}

/**
 * The bundled faces, fetched before the first frame so `Font(R.font.x)` can answer at once.
 *
 * IRANYekanX in four weights — the one typeface. A face asked for before `preload` finished, or
 * one the bundle does not carry, falls back to the regular weight's bytes if they are there.
 */
object WebFonts {
    private val bytes = HashMap<String, ByteArray>()

    suspend fun preload(names: List<String>): Boolean {
        var ok = true
        names.forEach { name ->
            val data = fetchBytes("fonts/$name.ttf")
            if (data != null) bytes[name] = data else ok = false
        }
        return ok
    }

    fun bytesOf(name: String): ByteArray? = bytes[name]

    fun font(name: String, weight: FontWeight, style: FontStyle): Font {
        val data = bytes[name] ?: bytes["iranyekanx_regular"] ?: ByteArray(0)
        return androidx.compose.ui.text.platform.Font(identity = "$name-${weight.weight}", data = data, weight = weight, style = style)
    }
}
