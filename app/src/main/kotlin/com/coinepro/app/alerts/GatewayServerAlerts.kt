package com.coinepro.app.alerts

import com.coinepro.app.di.CryptoPlatform
import com.coinepro.app.di.ForexPlatform
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.notifications.NotificationGateway
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.feature.alerts.ServerAlertRequest
import com.coinepro.feature.alerts.ServerAlerts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The alert centre's server half, over both backends.
 *
 * ### Why this lives in the application module
 *
 * `feature:alerts` states what it needs as [ServerAlerts] and nothing more. What it deliberately
 * does not know is that there are *two* servers with two accounts, that one of them quotes crypto
 * pairs and the other gold and silver, and which of them a given ticker belongs to. That is the
 * application module's standing job — every other gateway in this app is bound per platform the
 * same way — and putting it in the feature would give the alert screen an opinion about which
 * backend a market is on.
 *
 * ### Membership decides where an action goes
 *
 * The two servers issue their own ids and there is nothing in an id that says which one wrote it.
 * So [setActive] and [delete] are routed by *which cached list the id is in*, and an id in neither
 * is refused rather than sent to both. Sending to both would mean one of the two calls failing
 * every single time, and a screen that has learned to ignore a failure is a screen that will ignore
 * the real one.
 *
 * ### An empty list is not an error
 *
 * A reader with no account gets a failure from every route here, and the honest rendering of that
 * is *no server alerts*, not a red line on a screen that works. The device venue needs no account
 * and is the reason local alerts exist at all — see `LocalPriceAlert`. The one moment a failure is
 * reported is [create], because there the reader has just asked for something and is entitled to
 * know it did not happen.
 */
@Singleton
class GatewayServerAlerts @Inject constructor(
    @ForexPlatform private val forex: NotificationGateway,
    @CryptoPlatform private val crypto: NotificationGateway,
) : ServerAlerts {

    private val held = MarketPlatform.entries.associateWith { MutableStateFlow(emptyList<PriceAlert>()) }

    private fun gatewayOf(platform: MarketPlatform): NotificationGateway = when (platform) {
        MarketPlatform.COINEPRO_FX -> forex
        MarketPlatform.TRADEYAR -> crypto
    }

    override val alerts: Flow<List<PriceAlert>> =
        combine(held.values.toList()) { lists -> lists.toList().flatten() }
            // Distinct by id, because the two backends are asked independently and a build that
            // ever pointed both at one host would otherwise show every alert twice — which looks
            // exactly like an alert that was created twice, and would invite somebody to delete
            // "the duplicate".
            .map { alerts -> alerts.distinctBy(PriceAlert::id) }

    /**
     * Whether either server quotes this instrument.
     *
     * ### The rule is duplicated from `core:notifications`, and that is checked at the seam
     *
     * `normalizeProductAlertSymbol` is internal to that module and cannot be called from here, so
     * this states the same rule: TradeYar takes pairs quoted in USDT, CoinePro-FX takes gold and
     * silver. The trap, written down rather than left to be found: if the two ever drift, the
     * gateway's own `requireNotNull` throws on create and [create] reports a refusal — so the
     * failure is a message on a sheet the reader can act on, never an alert that was accepted here
     * and silently never made. It is the drift being *loud* that makes the duplication safe.
     */
    override fun supports(symbol: String): Boolean = platformFor(symbol) != null

    private fun platformFor(symbol: String): MarketPlatform? {
        val normalized = symbol.trim().uppercase().replace("/", "").replace("-", "")
        return when {
            normalized == "XAUUSD" || normalized == "XAGUSD" -> MarketPlatform.COINEPRO_FX
            normalized.endsWith("USDT") && normalized.length > 4 -> MarketPlatform.TRADEYAR
            else -> null
        }
    }

    /**
     * Re-reads both lists.
     *
     * Independently, and a failure leaves that platform's list as it was rather than emptying it. A
     * signed-in reader whose network dropped for one call should not watch their server alerts
     * disappear and then come back; that is indistinguishable from alerts being deleted.
     */
    override suspend fun refresh() {
        held.forEach { (platform, state) ->
            val read = runCatching { gatewayOf(platform).alerts() }.getOrNull() ?: return@forEach
            state.value = read
        }
    }

    override suspend fun create(request: ServerAlertRequest): Boolean {
        val platform = platformFor(request.symbol) ?: return false
        val created = runCatching {
            gatewayOf(platform).createAlert(
                symbol = request.symbol,
                condition = request.condition,
                value = request.value,
                trigger = request.trigger,
            )
        }.getOrNull() ?: return false
        // Written into the cache rather than re-read, so the new alert is in the list by the time
        // the sheet closes. A round trip here would leave a beat where the reader has saved an
        // alert and cannot see it, which is the moment they press save a second time.
        held[platform]?.update { current -> listOf(created) + current.filterNot { it.id == created.id } }
        return true
    }

    override suspend fun setActive(id: String, active: Boolean): Boolean {
        val platform = holderOf(id) ?: return false
        val updated = runCatching { gatewayOf(platform).setAlertActive(id, active) }.getOrNull()
            ?: return false
        held[platform]?.update { current -> current.map { if (it.id == updated.id) updated else it } }
        return true
    }

    /**
     * Removes one, and only trusts the server's own answer.
     *
     * A response that did not confirm the removal leaves the row in place. The alert is still armed
     * on the backend, and hiding it would mean a notification arriving later from something the
     * reader believes they deleted — the same reasoning `NotificationController.deleteAlert` gives.
     */
    override suspend fun delete(id: String): Boolean {
        val platform = holderOf(id) ?: return false
        val removed = runCatching { gatewayOf(platform).deleteAlert(id) }.getOrDefault(false)
        if (!removed) return false
        held[platform]?.update { current -> current.filterNot { it.id == id } }
        return true
    }

    private fun holderOf(id: String): MarketPlatform? =
        held.entries.firstOrNull { (_, state) -> state.value.any { it.id == id } }?.key
}
