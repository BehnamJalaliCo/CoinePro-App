package com.coinepro.feature.alerts

import com.coinepro.core.notifications.FX_ALERT_SYMBOLS
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PriceOp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Where an alert is decided: on this phone, or on the server.
 *
 * ### Why this is one screen with a label rather than two screens
 *
 * The app shipped with both. The alert centre held thirteen triggers, four frequencies, scopes,
 * per-alert delivery and an audit log; the activity screen held a second, primitive one — a typed
 * ticker, above or below, once, account required — sitting beside it under its own heading. Nobody
 * chose that. It is what happens when a local feature is built next to a server feature and neither
 * is told about the other, and the cost lands entirely on the reader: two lists to check after a
 * move, two places to go when an alert did not arrive, and no way to tell from a notification which
 * of the two sent it.
 *
 * So there is one list and one editor, and the difference between the two becomes a property of an
 * alert rather than a property of a screen. It is still a real difference and the app says so in
 * words on the row and in the editor — see `alerts_venue_device_note` and `alerts_venue_server_note`
 * — because the honest statement is short and the discovery is expensive: a device alert needs this
 * app installed and the phone awake, and a server alert keeps watching with the app closed and
 * needs an account.
 */
enum class AlertVenue {
    /** Evaluated here, by `AlertEvaluator`, against the same public feed the guest home polls. */
    DEVICE,

    /** Evaluated by the backend, which does not need this app to be running. */
    SERVER,
}

/**
 * A server alert as this app's editor states it.
 *
 * Four fields, because that is genuinely all the server's route takes. The narrowness is the point
 * of the type: it is what makes «this condition cannot be a server alert» a compile-checked
 * conversion returning null rather than a comment somebody has to remember.
 */
data class ServerAlertRequest(
    val symbol: String,
    val condition: PriceAlertCondition,
    val value: Double,
    val trigger: PriceAlertTrigger,
    /** The advanced condition, for [PriceAlertCondition.SPEC] (5.17.0). See [ServerAlertSpec]. */
    val spec: Map<String, String>? = null,
    /** Delivery beyond push: `telegram`, `email` (5.17.0). */
    val channels: List<String> = listOf("push"),
    /** The reader's expiry, epoch milliseconds, or null for the server's own. */
    val expiresAtMs: Long? = null,
)

/**
 * The server's alerts, behind the one interface this feature needs from them.
 *
 * Narrow on purpose. `NotificationGateway` also carries the notification list, the read count and
 * the push preferences, none of which is this screen's business, and depending on it here would
 * make the alert centre re-render whenever a news notification arrived.
 *
 * [supports] exists because the two backends quote different products — the crypto route takes
 * `…USDT` pairs, the forex one takes gold and silver — and a reader who has chosen a symbol the
 * server cannot watch must be told before they fill the form in, not after they press save. The
 * default implementation says no to everything, which is what a build with no account layer wired
 * should say.
 */
interface ServerAlerts {

    /** What the server holds now. Empty while there is no account, which is not an error. */
    val alerts: Flow<List<PriceAlert>>

    /** Whether the server can watch this instrument at all. */
    fun supports(symbol: String): Boolean

    /** Re-reads the list. Called when the alert centre opens; failure leaves what was there. */
    suspend fun refresh()

    /** Creates one, and says whether the server took it. */
    suspend fun create(request: ServerAlertRequest): Boolean

    /** Switches one off or back on. */
    suspend fun setActive(id: String, active: Boolean): Boolean

    /** Removes one. False leaves the row in place; see `NotificationController.deleteAlert`. */
    suspend fun delete(id: String): Boolean
}

/**
 * The server path, absent.
 *
 * The default for every caller that has no account layer to offer — the screenshot tests, and any
 * build where the alert centre is shown before a gateway exists. Everything answers "no" rather
 * than throwing, so the editor simply never offers the second venue and the list holds only device
 * alerts. A stub that threw would turn "no account" into a crash on a screen that works perfectly
 * well without one.
 */
object NoServerAlerts : ServerAlerts {
    override val alerts: Flow<List<PriceAlert>> = flowOf(emptyList())
    override fun supports(symbol: String): Boolean = false
    override suspend fun refresh() = Unit
    override suspend fun create(request: ServerAlertRequest): Boolean = false
    override suspend fun setActive(id: String, active: Boolean): Boolean = false
    override suspend fun delete(id: String): Boolean = false
}

/**
 * Between the server's alert shape and this app's.
 *
 * ### One list means one row type
 *
 * The alert centre groups, sorts, renders and audits [LocalPriceAlert]. Teaching every one of those
 * about a second shape would be four places to get wrong and four places to forget; converting once,
 * here, means a server alert is grouped by the same rules and read as the same sentence. What is
 * *not* converted away is which venue it came from — that travels beside the row, because it is the
 * one thing about a server alert the reader has to be able to see.
 *
 * ### The conversion is lossy in exactly one direction and that is checked
 *
 * The server understands one price against one level. A channel, a move, an indicator, a drawing
 * touch and a multi-condition have no server spelling at all, and [requestOf] answers null for each
 * of them rather than picking the nearest — an alert that quietly became a different alert on the
 * way to the server is the worst outcome available here, because it would fire, correctly, for a
 * condition the reader never asked about.
 */
object ServerAlertRows {

    /**
     * What a converted server alert's id looks like in the list.
     *
     * Prefixed so that the two id spaces cannot collide, and so that any code holding a row id can
     * still tell the two apart — the actions the reader takes on a row have to reach the server for
     * one of them and the local store for the other. The colon is safe: `LocalAlertStore` reserves
     * `;` and `|`, and these rows are never written to it.
     */
    const val SERVER_ID_PREFIX = "server:"

    /** The row id for a server alert. */
    fun rowId(serverId: String): String = SERVER_ID_PREFIX + serverId

    /** The server's own id back out of a row id, or null for a device alert's id. */
    fun serverIdOf(rowId: String): String? =
        rowId.removePrefix(SERVER_ID_PREFIX).takeIf { it != rowId && it.isNotBlank() }

    /**
     * A server alert as a row of this list.
     *
     * The flat [LocalAlertCondition] is written for all five conditions and a [AlertTrigger] only
     * for the three crossings. That is not an omission: above and below on the server are the same
     * inclusive comparison the flat pair has always been, while a crossing is the thing the flat
     * pair cannot say — see `PriceOp` for why the two are kept apart rather than merged.
     *
     * [AlertChannel.PUSH] alone, because that is the only way a server alert can reach anybody: it
     * is decided somewhere else and arrives as a notification. Offering the in-app or sound choices
     * against it in the editor would be offering settings that do nothing.
     */
    fun asLocal(alert: PriceAlert): LocalPriceAlert = LocalPriceAlert(
        id = rowId(alert.id),
        symbol = alert.symbol,
        condition = conditionOf(alert.condition),
        value = alert.value,
        repeat = if (alert.trigger == PriceAlertTrigger.ONCE) AlertRepeat.ONCE else AlertRepeat.ALWAYS,
        active = alert.active,
        createdAtEpochMillis = alert.createdAtEpochMillis,
        lastFiredAtEpochMillis = alert.lastTriggeredAtEpochMillis,
        trigger = if (alert.condition == PriceAlertCondition.SPEC) {
            ServerAlertSpec.triggerOf(alert.spec)
        } else {
            crossingOf(alert.condition)?.let { AlertTrigger.Price(it, alert.value) }
        },
        scope = AlertScope.Symbol(alert.symbol),
        expiresAt = alert.expiresAtEpochMillis,
        channels = setOf(AlertChannel.PUSH),
    )

    /**
     * The server request a draft describes, or null where the server cannot express it.
     *
     * Null for every scope that is not a single symbol, for every trigger that is not a single price
     * against a single level, and for a compound condition. The editor reads the same answer to
     * decide whether to offer the server venue at all, so the reader is never given a choice that
     * would be refused at save.
     */
    fun requestOf(draft: AlertDraft): ServerAlertRequest? {
        if (draft.scopeListId != null) return null
        val symbol = draft.symbol.trim().uppercase().takeIf(String::isNotEmpty) ?: return null
        val built = draft.trigger() ?: return null
        val advanced = built !is AlertTrigger.Price
        // The advanced conditions are CoinePro-FX's alone (5.17.0); TradeYar's route takes the plain five.
        if (advanced && symbol !in FX_ALERT_SYMBOLS) return null
        val spec = if (advanced) ServerAlertSpec.specOf(built) ?: return null else null
        val price = built as? AlertTrigger.Price
        val condition = if (price != null) conditionOf(price.op) ?: return null else PriceAlertCondition.SPEC
        val value = price?.value ?: 1.0
        if (!value.isFinite() || value <= 0.0) return null
        return ServerAlertRequest(
            symbol = symbol,
            condition = condition,
            value = value,
            spec = spec,
            channels = draft.serverChannels.toList().sorted(),
            expiresAtMs = draft.expiresAt,
            // Anything that is not «یک‌بار» is «هر بار» to the server: it has two settings and the
            // bar-aware ones are a promise only the device evaluator can keep, because only it
            // knows which timeframe the reader is looking at.
            trigger = if (draft.frequency == AlertFrequency.ONCE) {
                PriceAlertTrigger.ONCE
            } else {
                PriceAlertTrigger.RECURRING
            },
        )
    }

    /** The server's word for one of this app's comparisons, or null for one it does not have. */
    fun conditionOf(op: PriceOp): PriceAlertCondition? = when (op) {
        PriceOp.GREATER_THAN -> PriceAlertCondition.ABOVE
        PriceOp.LESS_THAN -> PriceAlertCondition.BELOW
        PriceOp.CROSSING_UP -> PriceAlertCondition.CROSS_UP
        PriceOp.CROSSING_DOWN -> PriceAlertCondition.CROSS_DOWN
        PriceOp.CROSSING -> PriceAlertCondition.CROSS
    }

    private fun conditionOf(condition: PriceAlertCondition): LocalAlertCondition = when (condition) {
        PriceAlertCondition.ABOVE, PriceAlertCondition.CROSS_UP, PriceAlertCondition.CROSS, PriceAlertCondition.SPEC ->
            LocalAlertCondition.ABOVE
        PriceAlertCondition.BELOW, PriceAlertCondition.CROSS_DOWN -> LocalAlertCondition.BELOW
    }

    private fun crossingOf(condition: PriceAlertCondition): PriceOp? = when (condition) {
        PriceAlertCondition.CROSS_UP -> PriceOp.CROSSING_UP
        PriceAlertCondition.CROSS_DOWN -> PriceOp.CROSSING_DOWN
        PriceAlertCondition.CROSS -> PriceOp.CROSSING
        PriceAlertCondition.ABOVE, PriceAlertCondition.BELOW, PriceAlertCondition.SPEC -> null
    }
}

/**
 * This app's advanced conditions in CoinePro-FX's server spelling (5.17.0), and back.
 *
 * The same three the server evaluates (`src/api/mobile/alert_specs.py`): a channel, a move over
 * bars, and RSI against a level. A moving-average condition stays on the device — here it compares
 * the average to a level, on the server it would compare the price to the average, and a condition
 * that means two things depending on where it runs is not a condition to send.
 */
object ServerAlertSpec {

    /** The server timeframe a bar-counted condition is measured on — the device's own default. */
    const val TIMEFRAME: String = "H1"

    /**
     * Every value as text: the web build's serializer has no `Any`, and the server reads numbers
     * from strings (`_num`) and answers with numbers, which [triggerOf] reads either way.
     */
    fun specOf(trigger: AlertTrigger): Map<String, String>? = specValuesOf(trigger)?.mapValues { (_, value) -> value.toString() }

    private fun specValuesOf(trigger: AlertTrigger): Map<String, Any>? = when (trigger) {
        is AlertTrigger.Channel -> mapOf(
            "kind" to "channel",
            "low" to trigger.low,
            "high" to trigger.high,
            "op" to when (trigger.op) {
                ChannelOp.ENTERING -> "enter"
                ChannelOp.EXITING -> "exit"
                ChannelOp.INSIDE -> "inside"
                ChannelOp.OUTSIDE -> "outside"
            },
        )
        is AlertTrigger.Move -> when (trigger.op) {
            MoveOp.UP_PERCENT, MoveOp.DOWN_PERCENT -> mapOf(
                "kind" to "move",
                "op" to trigger.op.id,
                "amount" to trigger.amount,
                "bars" to trigger.bars,
                "tf" to TIMEFRAME,
            )
            else -> null
        }
        is AlertTrigger.Indicator -> if (trigger.indicatorId == "rsi") {
            val op = when (trigger.op) {
                PriceOp.GREATER_THAN -> "above"
                PriceOp.LESS_THAN -> "below"
                PriceOp.CROSSING_UP -> "cross_up"
                PriceOp.CROSSING_DOWN -> "cross_down"
                PriceOp.CROSSING -> null
            }
            op?.let {
                mapOf("kind" to "indicator", "id" to "rsi", "period" to (trigger.period ?: 14), "op" to it, "value" to trigger.value, "tf" to TIMEFRAME)
            }
        } else {
            null
        }
        else -> null
    }

    /** The server's spec read back as the trigger this app draws it with, or null for one it cannot read. */
    fun triggerOf(spec: Map<String, String>?): AlertTrigger? {
        spec ?: return null
        fun number(key: String): Double? = spec[key]?.toDoubleOrNull()
        return runCatching {
            when (spec["kind"]) {
                "channel" -> {
                    val op = when (spec["op"]) {
                        "enter" -> ChannelOp.ENTERING
                        "exit" -> ChannelOp.EXITING
                        "inside" -> ChannelOp.INSIDE
                        "outside" -> ChannelOp.OUTSIDE
                        else -> return null
                    }
                    AlertTrigger.Channel(op, number("low") ?: return null, number("high") ?: return null)
                }
                "move" -> AlertTrigger.Move(
                    MoveOp.entries.firstOrNull { it.id == spec["op"] } ?: return null,
                    number("amount") ?: return null,
                    number("bars")?.toInt() ?: 1,
                )
                "indicator" -> AlertTrigger.Indicator(
                    indicatorId = spec["id"] ?: return null,
                    period = number("period")?.toInt(),
                    op = when (spec["op"]) {
                        "above" -> PriceOp.GREATER_THAN
                        "below" -> PriceOp.LESS_THAN
                        "cross_up" -> PriceOp.CROSSING_UP
                        "cross_down" -> PriceOp.CROSSING_DOWN
                        else -> return null
                    },
                    value = number("value") ?: return null,
                )
                else -> null
            }
        }.getOrNull()
    }
}
