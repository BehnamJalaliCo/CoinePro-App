package com.coinepro.web.assets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.coinepro.web.net.browserFetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/*
 * The phone's `assets/`, in the bundle under `assets/` (terminalBundle copies them from the modules
 * that ship them). `AssetManager.open` is synchronous on the phone, so the text assets the app reads
 * at start — the help catalogue and the legal pages — are fetched before the first frame, and a
 * picture is fetched the first time it is asked for: the ask fails, the picture lands, [revision]
 * moves, and whatever reads it asks again.
 */
object Assets {
    /** What the page fetches before its first frame. Everything else arrives on first use. */
    val preloaded: List<String> = listOf(
        "help/content.json",
        "legal/privacy.md", "legal/privacy-en.md", "legal/terms.md", "legal/terms-en.md",
    )

    private val cache = HashMap<String, ByteArray>()
    private val missing = HashSet<String>()
    private val inFlight = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Moves each time an asset lands, for a composable that read one too early. */
    var revision: Int by mutableIntStateOf(0)
        private set

    suspend fun preload() {
        kotlinx.coroutines.coroutineScope {
            preloaded.map { path -> async { fetch(path) } }.awaitAll()
        }
    }

    fun bytes(path: String): ByteArray? {
        cache[path]?.let { return it }
        if (path !in missing && inFlight.add(path)) scope.launch { fetch(path) }
        return null
    }

    fun list(path: String): Array<String> =
        cache.keys.filter { it.startsWith(path.trimEnd('/') + "/") }.map { it.removePrefix(path.trimEnd('/') + "/").substringBefore('/') }
            .distinct().toTypedArray()

    private suspend fun fetch(path: String) {
        val result = browserFetch("assets/$path", "GET", emptyList(), null, 60_000)
        if (result.status in 200..299) cache[path] = result.body else missing += path
        inFlight.remove(path)
        revision++
    }
}
