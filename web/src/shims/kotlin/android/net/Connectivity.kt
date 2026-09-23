@file:Suppress("unused")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package android.net

/*
 * Connectivity, from the browser's own view of it: `navigator.onLine` and its `online`/`offline`
 * events. One network, the page's; "validated" is what the browser says, which is as much as a page
 * can know without a request of its own.
 */

private fun onlineJs(): Boolean = js("(typeof navigator === 'undefined') ? true : navigator.onLine !== false")
private fun listenJs(changed: (Boolean) -> Unit): JsAny = js(
    """(function () {
        var on = function () { changed(true); }, off = function () { changed(false); };
        window.addEventListener('online', on); window.addEventListener('offline', off);
        return { on: on, off: off };
    })()""",
)
private fun unlistenJs(h: JsAny): Unit = js("(function(){ window.removeEventListener('online', h.on); window.removeEventListener('offline', h.off); })()")

class Network internal constructor()

class NetworkCapabilities internal constructor(private val online: Boolean) {
    fun hasCapability(capability: Int): Boolean = online
    fun hasTransport(transport: Int): Boolean = online && transport == TRANSPORT_WIFI
    companion object {
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
        const val NET_CAPABILITY_NOT_METERED = 11
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_ETHERNET = 3
    }
}

class NetworkRequest private constructor() {
    class Builder {
        fun addCapability(capability: Int): Builder = this
        fun addTransportType(transport: Int): Builder = this
        fun build(): NetworkRequest = NetworkRequest()
    }
}

class ConnectivityManager {
    open class NetworkCallback {
        open fun onAvailable(network: Network) {}
        open fun onLost(network: Network) {}
        open fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {}
        open fun onUnavailable() {}
    }

    private val page = Network()
    private val handles = HashMap<NetworkCallback, JsAny>()

    val activeNetwork: Network? get() = if (onlineJs()) page else null
    val isActiveNetworkMetered: Boolean get() = false
    fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = network?.let { NetworkCapabilities(onlineJs()) }

    fun registerNetworkCallback(request: NetworkRequest, callback: NetworkCallback) = registerDefaultNetworkCallback(callback)

    fun registerDefaultNetworkCallback(callback: NetworkCallback) {
        handles[callback] = listenJs { online ->
            if (online) {
                callback.onAvailable(page)
                callback.onCapabilitiesChanged(page, NetworkCapabilities(true))
            } else {
                callback.onLost(page)
            }
        }
        if (onlineJs()) {
            callback.onAvailable(page)
            callback.onCapabilitiesChanged(page, NetworkCapabilities(true))
        }
    }

    fun unregisterNetworkCallback(callback: NetworkCallback) {
        handles.remove(callback)?.let(::unlistenJs)
    }
}
