package com.coinepro.core.help

import android.content.res.AssetManager
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader

/**
 * Every «؟» entry the app ships, read once from the packaged asset.
 *
 * The whole catalogue is one 800 KB JSON file rather than 177 small ones, because it is read as a
 * unit the first time anybody opens a help sheet and never again. Parsing it costs about a tenth of
 * a second, which is why [load] is a suspending call the caller runs off the main thread and the
 * result is held for the process's lifetime.
 *
 * It is an asset rather than string resources on purpose. These are 177 entries × 6 fields × 2
 * languages — around two thousand strings — and none of them is ever referenced from a layout or
 * looked up by name at compile time. As `strings.xml` they would bloat every build's resource table
 * and gain nothing; as an asset they are one file that can be regenerated from the export whole.
 */
class HelpCatalog private constructor(private val entries: Map<String, HelpEntry>) {

    val size: Int get() = entries.size

    val ids: Set<String> get() = entries.keys

    /**
     * The entry for an id, or null.
     *
     * Null is an ordinary answer, not a failure: a tool the app adds before the help is written has
     * no entry, and the right response is to hide its «؟» rather than to open an empty sheet.
     */
    operator fun get(id: String): HelpEntry? = entries[id] ?: ALIASES[id]?.let(entries::get)

    fun imagePath(image: HelpImage): String = "$IMAGE_DIRECTORY/${image.file}"

    companion object {
        const val ASSET_PATH = "help/content.json"

        /**
         * Old ids that resolve to the entry that replaced them.
         *
         * Four indicators had two entries each: one exported from the web terminal under a
         * camel-case id, and one written for this app under the indicator's own id — with a
         * pitfall, an example on a market this app quotes, and steps that name this app's own
         * picker. The indicators pointed at the export and the better entry was dead content, and
         * the two ids differed only in case, which is the kind of pair that reads as a typo until
         * a saved layout or a link names the wrong one. The export entries are gone; anything that
         * still says their name lands here.
         */
        val ALIASES: Map<String, String> = mapOf(
            "chandeKroll" to "chandekroll",
            "massIndex" to "massindex",
            "netVolume" to "netvolume",
            "volumeProfile" to "volumeprofile_ind",
        )
        /** Where the pictures live inside this module's assets. They ship in the APK. */
        const val IMAGE_DIRECTORY = "help/images"

        /** Read and parse the packaged catalogue. Call from a background dispatcher. */
        fun load(assets: AssetManager): HelpCatalog =
            assets.open(ASSET_PATH).use { stream ->
                parse(InputStreamReader(stream, Charsets.UTF_8).readText())
            }

        /** Parse catalogue JSON. Separated from [load] so it can be tested without Android. */
        fun parse(json: String): HelpCatalog {
            val root = JsonParser.parseString(json).asJsonObject
            val entries = LinkedHashMap<String, HelpEntry>(root.size())
            for ((id, value) in root.entrySet()) {
                val entry = value as? JsonObject ?: continue
                entries[id] = HelpEntry(
                    id = id,
                    title = entry.bilingual("title") ?: Bilingual(id, id),
                    useCase = entry.bilingual("useCase"),
                    what = entry.bilingual("what"),
                    how = entry.bilingualList("how"),
                    tips = entry.bilingualList("tips"),
                    example = entry.bilingual("example"),
                    pitfall = entry.bilingual("pitfall"),
                    images = entry.images(),
                )
            }
            return HelpCatalog(entries)
        }

        private fun JsonObject.bilingual(name: String): Bilingual? {
            val node = get(name) as? JsonObject ?: return null
            val fa = node.string("fa")
            val en = node.string("en")
            if (fa.isNullOrBlank() && en.isNullOrBlank()) return null
            // One language missing falls back to the other rather than to an empty panel. The
            // export has both everywhere today; this is here so a future entry with only Persian
            // still reads in English instead of showing a blank section.
            return Bilingual(fa = fa ?: en.orEmpty(), en = en ?: fa.orEmpty())
        }

        private fun JsonObject.bilingualList(name: String): BilingualList {
            val node = get(name) as? JsonObject ?: return BilingualList.EMPTY
            val fa = node.strings("fa")
            val en = node.strings("en")
            if (fa.isEmpty() && en.isEmpty()) return BilingualList.EMPTY
            return BilingualList(fa = fa.ifEmpty { en }, en = en.ifEmpty { fa })
        }

        private fun JsonObject.images(): List<HelpImage> {
            val array = get("images")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return array.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val file = item.string("file")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HelpImage(file = file, alt = item.bilingual("alt") ?: Bilingual(file, file))
            }
        }

        private fun JsonObject.string(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asString

        private fun JsonObject.strings(name: String): List<String> {
            val element: JsonElement = get(name) ?: return emptyList()
            if (!element.isJsonArray) return emptyList()
            return element.asJsonArray.mapNotNull {
                it.takeIf(JsonElement::isJsonPrimitive)?.asString?.takeIf(String::isNotBlank)
            }
        }
    }
}
