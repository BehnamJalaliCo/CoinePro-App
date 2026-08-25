package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What each deployment says it can actually do.
 *
 * The same `auth/methods` read the sign-in screen already makes, kept for the rest of the app.
 * Every screen that fronts an optional feature needs it: a deployment that cannot deliver a push
 * must not be asked for the notification permission, and a chart-analysis screen on a server with
 * no vision model is a wait that ends in an error every time.
 *
 * Read once per platform after sign-in rather than per screen. It is deployment configuration, not
 * live state — it changes when someone edits a server's settings, not while a reader is scrolling —
 * so re-reading it on every navigation would spend a request to be told the same thing.
 */
class PlatformCapabilities(
    private val gateways: Map<MarketPlatform, EmailAuthGateway>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<Map<MarketPlatform, AuthMethods>>(emptyMap())

    /**
     * A platform missing from this map has not answered yet.
     *
     * Absence is deliberately not the same as "everything off": until a server has been asked, the
     * app knows nothing, and switching features off on a server that has them would be as wrong as
     * offering ones it does not. [supports] resolves the difference per capability.
     */
    val state: StateFlow<Map<MarketPlatform, AuthMethods>> = mutableState.asStateFlow()

    fun refresh() {
        scope.launch {
            // Both at once, and independently: one server being unreachable says nothing about the
            // other's capabilities, and waiting for it would hold up a screen that is ready.
            val answers = gateways.map { (platform, gateway) ->
                async { platform to (gateway.methods() as? AppResult.Success)?.value }
            }.awaitAll()
            mutableState.value = buildMap {
                putAll(mutableState.value)
                answers.forEach { (platform, methods) -> if (methods != null) put(platform, methods) }
            }
        }
    }

    fun clear() {
        mutableState.value = emptyMap()
    }

    /**
     * Whether [platform] offers a feature, read the safe way round for each kind of answer.
     *
     * [assumeWhenUnknown] is what makes this honest rather than convenient. For a capability every
     * server reports, silence means the app has not asked yet and the feature should stay hidden
     * until it has — offering one that fails is worse than a brief absence. For the two flags only
     * one server sends, silence is the older server's default, and reading it as "off" would switch
     * off two working features on the platform that has them.
     */
    fun supports(
        platform: MarketPlatform,
        assumeWhenUnknown: Boolean = false,
        capability: (AuthMethods) -> Boolean?,
    ): Boolean {
        val methods = mutableState.value[platform] ?: return assumeWhenUnknown
        return capability(methods) ?: assumeWhenUnknown
    }
}
