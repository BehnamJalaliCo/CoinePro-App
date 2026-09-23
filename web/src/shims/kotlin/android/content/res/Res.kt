@file:Suppress("unused")

package android.content.res

import java.util.Locale

/** What the phone's code reads off `LocalConfiguration`: the window in dp, the language, the mode. */
class Configuration(
    var screenWidthDp: Int = 1280,
    var screenHeightDp: Int = 800,
    var smallestScreenWidthDp: Int = 800,
    var locales: LocaleList = LocaleList(Locale.getDefault()),
    var orientation: Int = ORIENTATION_LANDSCAPE,
    var uiMode: Int = UI_MODE_NIGHT_YES,
    var fontScale: Float = 1f,
    var densityDpi: Int = 160,
    var layoutDirection: Int = 1,
) {
    constructor(other: Configuration) : this(
        other.screenWidthDp, other.screenHeightDp, other.smallestScreenWidthDp, other.locales, other.orientation,
        other.uiMode, other.fontScale, other.densityDpi, other.layoutDirection,
    )
    fun setLocale(locale: Locale) { locales = LocaleList(locale) }
    fun setLocales(list: LocaleList) { locales = list }
    fun setLayoutDirection(locale: Locale) { layoutDirection = android.text.TextUtils.getLayoutDirectionFromLocale(locale) }
    val locale: Locale get() = locales[0] ?: Locale.US
    fun getLayoutDirection(): Int = layoutDirection
    val isScreenRound: Boolean get() = false

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_YES = 0x20
        const val UI_MODE_NIGHT_NO = 0x10
        const val SCREENLAYOUT_SIZE_MASK = 0x0f
    }
}

class LocaleList(vararg locales: Locale) {
    private val list = locales.toList()
    operator fun get(index: Int): Locale = list.getOrNull(index) ?: Locale.getDefault()
    fun size(): Int = list.size
    val isEmpty: Boolean get() = list.isEmpty()
    fun toLanguageTags(): String = list.joinToString(",") { it.toLanguageTag() }
    companion object {
        fun getDefault(): LocaleList = LocaleList(Locale.getDefault())
        fun forLanguageTags(tags: String): LocaleList = LocaleList(*tags.split(',').filter { it.isNotBlank() }.map { Locale.forLanguageTag(it) }.toTypedArray())
    }
}

class Resources {
    val configuration: Configuration get() = Configuration()
    val displayMetrics: android.util.DisplayMetrics get() = android.util.DisplayMetrics()
    fun getString(id: Int): String = com.coinepro.web.Strings.get(com.coinepro.web.WebResources.nameOf(id))
    fun getString(id: Int, vararg args: Any?): String = com.coinepro.web.formatAndroid(getString(id), *args)
    fun getQuantityString(id: Int, count: Int, vararg args: Any?): String =
        com.coinepro.web.formatAndroid(com.coinepro.web.Strings.plural(com.coinepro.web.WebResources.nameOf(id), count), *args)
    fun getBoolean(id: Int): Boolean = com.coinepro.web.WebResources.valueOf(id)?.toBooleanStrictOrNull() ?: false
    fun getInteger(id: Int): Int = com.coinepro.web.WebResources.valueOf(id)?.toIntOrNull() ?: 0
    val assets: AssetManager = AssetManager()
    fun getResourceEntryName(id: Int): String = com.coinepro.web.WebResources.nameOf(id)
    fun getResourceName(id: Int): String = com.coinepro.web.WebResources.nameOf(id)
}

class AssetManager {
    /** The asset's bytes, or `FileNotFoundException` while it is still on its way — see `Assets`. */
    fun open(path: String): java.io.InputStream =
        com.coinepro.web.assets.Assets.bytes(path)?.let { java.io.ByteArrayInputStream(it) }
            ?: throw java.io.FileNotFoundException(path)
    fun open(path: String, mode: Int): java.io.InputStream = open(path)
    fun list(path: String): Array<String> = com.coinepro.web.assets.Assets.list(path)
}
