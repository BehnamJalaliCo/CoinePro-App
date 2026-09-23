@file:Suppress("unused", "UNCHECKED_CAST")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.activity.compose

import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.coinepro.web.content.WebContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ManagedActivityResultLauncher<I, O> internal constructor(
    private val contract: ActivityResultContract<I, O>,
    private val onResult: () -> (O) -> Unit,
) : ActivityResultLauncher<I>() {
    override fun launch(input: I) {
        val deliver = onResult()
        when (contract) {
            is ActivityResultContracts.CreateDocument ->
                deliver(Uri.parse(WebContent.downloadUri(input as String, contract.mimeType)) as O)
            is ActivityResultContracts.OpenDocument ->
                WebContent.choose((input as Array<String>).joinToString(",")) { deliver(it?.let(Uri::parse) as O) }
            is ActivityResultContracts.GetContent ->
                WebContent.choose(input as String) { deliver(it?.let(Uri::parse) as O) }
            is ActivityResultContracts.PickVisualMedia ->
                WebContent.choose((input as PickVisualMediaRequest).mediaType.accept) { deliver(it?.let(Uri::parse) as O) }
            is ActivityResultContracts.TakePicture ->
                WebContent.choose("image/*") { deliver((it != null) as O) }
            is ActivityResultContracts.RequestPermission ->
                com.coinepro.web.content.WebPermissions.request(input as String) { deliver(it as O) }
            is ActivityResultContracts.RequestMultiplePermissions ->
                deliver((input as Array<String>).associateWith { com.coinepro.web.content.WebPermissions.granted(it) } as O)
            else -> {}
        }
    }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I, O> {
    val current = rememberUpdatedState(onResult)
    return remember(contract) { ManagedActivityResultLauncher(contract) { current.value } }
}

/**
 * Back, in a page: the browser's back button and the Escape key. Handlers stack as on the phone —
 * the most recently composed enabled one takes the gesture — and the shell (web Main.kt) calls
 * [WebBack.dispatch] when either arrives.
 */
object WebBack {
    internal class Entry(var enabled: Boolean, var handle: suspend (Flow<BackEventCompat>) -> Unit)
    private val stack = ArrayList<Entry>()
    internal fun push(entry: Entry) { stack += entry }
    internal fun remove(entry: Entry) { stack -= entry }
    val hasHandler: Boolean get() = stack.any { it.enabled }

    /** True when something on screen took the back; false when the page itself should go back. */
    suspend fun dispatch(): Boolean {
        val top = stack.lastOrNull { it.enabled } ?: return false
        top.handle(emptyFlow())
        return true
    }
}

@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<BackEventCompat>) -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { WebBack.Entry(enabled) { current.value(it) } }
    entry.enabled = enabled
    DisposableEffect(entry) {
        WebBack.push(entry)
        onDispose { WebBack.remove(entry) }
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { current.value() }
}

object LocalActivityResultRegistryOwner
object LocalOnBackPressedDispatcherOwner
