package com.coinepro.core.notifications

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.network.ApiErrors
import com.coinepro.core.network.toServerMessage
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Query

interface NotificationGateway {
    suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean
    suspend fun unregisterDevice(token: String): Boolean
    suspend fun preferences(): PushPreferences
    suspend fun updatePreferences(preferences: PushPreferences): PushPreferences
    suspend fun notifications(limit: Int = 50): NotificationPage
    /**
     * Marks everything read and reports how many rows this call changed.
     *
     * The count matters: clearing the badge optimistically shows a zero the server may not agree
     * with, and the next refresh brings it back looking like new mail. Marking what the server
     * says was marked is the only version that cannot lie.
     */
    suspend fun markNotificationsRead(): Int
    suspend fun alerts(): List<PriceAlert>
    suspend fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ): PriceAlert
    suspend fun setAlertActive(alertId: String, active: Boolean): PriceAlert
    suspend fun deleteAlert(alertId: String): Boolean
}

data class NotificationPage(
    val items: List<AppNotification>,
    val unread: Int,
    /**
     * Whether the server held back older entries.
     *
     * Worth carrying rather than ignoring: a truncated list that says nothing looks like the whole
     * history, and a reader looking for something from last week concludes it was never recorded.
     */
    val hasMore: Boolean = false,
)

internal interface NotificationApi {
    @POST
    suspend fun registerDevice(@Url path: String, @Body body: DeviceRegistrationDto): DeviceRegistrationResponseDto

    @HTTP(method = "DELETE", hasBody = true)
    suspend fun unregisterDevice(@Url path: String, @Body body: DeviceUnregisterDto): DeviceUnregisterResponseDto

    @GET
    suspend fun preferences(@Url path: String): PreferencesResponseDto

    @PATCH
    suspend fun updatePreferences(@Url path: String, @Body body: PushPreferencesDto): PreferencesResponseDto

    @GET
    suspend fun notifications(@Url path: String, @Query("limit") limit: Int): NotificationResponseDto

    @POST
    suspend fun markNotificationsRead(@Url path: String): MarkReadResponseDto

    @GET
    suspend fun alerts(@Url path: String): AlertListResponseDto

    @POST
    suspend fun createAlert(@Url path: String, @Body body: PriceAlertCreateDto): AlertResponseDto

    @PATCH
    suspend fun patchAlert(@Url path: String, @Body body: PriceAlertPatchDto): AlertResponseDto

    @HTTP(method = "DELETE")
    suspend fun deleteAlert(@Url path: String): DeleteAlertResponseDto
}

/**
 * Push, notifications and alerts, under each backend's own prefix.
 *
 * CoinePro-FX groups all three under `user/mobile`; TradeYar mounts them as three siblings inside
 * its mobile prefix. Same nine calls either way.
 */
internal class NotificationPaths(private val prefix: String) {
    val devices = "$prefix/push/devices"
    val preferences = "$prefix/push/preferences"
    val notifications = "$prefix/notifications"
    val markRead = "$prefix/notifications/read"
    val alerts = "$prefix/alerts"
    fun alert(alertId: String) = "$prefix/alerts/$alertId"

    companion object {
        fun of(platform: MarketPlatform): NotificationPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> NotificationPaths("user/mobile")
            MarketPlatform.TRADEYAR -> NotificationPaths("api/mobile/v1")
        }
    }
}

internal data class DeviceRegistrationDto(
    val token: String,
    val platform: String = "android",
    val appVersion: String? = null,
    val locale: String? = null,
)
internal data class DeviceRegistrationResponseDto(val registered: Boolean = false)
internal data class DeviceUnregisterDto(val token: String)
internal data class DeviceUnregisterResponseDto(val removed: Boolean = false)
internal data class PushPreferencesDto(
    val newSignals: Boolean = true,
    val signalUpdates: Boolean = true,
    val priceAlerts: Boolean = true,
)
internal data class PreferencesResponseDto(val preferences: PushPreferencesDto = PushPreferencesDto())
internal data class NotificationDto(
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    val data: Map<String, String> = emptyMap(),
    val ts: Long? = null,
    val read: Boolean = false,
)
internal data class NotificationResponseDto(
    val items: List<NotificationDto> = emptyList(),
    val unread: Int = 0,
    val hasMore: Boolean = false,
)
internal data class PriceAlertDto(
    val id: String? = null,
    val market: String? = null,
    val symbol: String? = null,
    val condition: String? = null,
    val value: Double? = null,
    val trigger: String? = null,
    /**
     * The two backends spell the expiry differently: CoinePro-FX sends `expires_at`, TradeYar sends
     * `expires_at_ms` alongside its two other `_ms` fields. Both are epoch milliseconds, so both are
     * read and whichever arrived wins — a single spelling here would leave one platform's alerts
     * looking as though they never expire.
     */
    val expiresAt: Long? = null,
    val expiresAtMs: Long? = null,
    val active: Boolean = false,
    val createdAtMs: Long? = null,
    val lastTriggeredAtMs: Long? = null,
)
internal data class AlertListResponseDto(val items: List<PriceAlertDto> = emptyList())
internal data class AlertResponseDto(val alert: PriceAlertDto? = null)
internal data class PriceAlertCreateDto(
    val symbol: String,
    val condition: String,
    val value: Double,
    val trigger: String,
)
internal data class PriceAlertPatchDto(val active: Boolean)
internal data class DeleteAlertResponseDto(val removed: Boolean = false)
internal data class MarkReadResponseDto(val ok: Boolean = false, val marked: Int = 0)

/**
 * [platform] scopes which symbols may be alerted on.
 *
 * The two backends hold different markets, and an alert is created from whatever symbol is on
 * screen — so without a boundary here a crypto pair could be posted to the forex platform, which is
 * the same class of mistake as showing gold in a crypto watchlist. The server refuses it too, but a
 * refusal the reader has to trigger is a worse answer than never offering it.
 */
class NetworkNotificationGateway private constructor(
    private val api: NotificationApi,
    private val platform: MarketPlatform,
    private val paths: NotificationPaths,
) : NotificationGateway {
    override suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean =
        api.registerDevice(paths.devices, DeviceRegistrationDto(token = token, appVersion = appVersion, locale = locale)).registered

    override suspend fun unregisterDevice(token: String): Boolean =
        api.unregisterDevice(paths.devices, DeviceUnregisterDto(token)).removed

    override suspend fun preferences(): PushPreferences = api.preferences(paths.preferences).preferences.toDomain()

    override suspend fun updatePreferences(preferences: PushPreferences): PushPreferences =
        api.updatePreferences(paths.preferences, preferences.toDto()).preferences.toDomain()

    override suspend fun notifications(limit: Int): NotificationPage {
        val response = api.notifications(paths.notifications, limit)
        return NotificationPage(
            items = response.items.mapNotNull { it.toDomain() },
            unread = response.unread.coerceAtLeast(0),
            hasMore = response.hasMore,
        )
    }

    override suspend fun markNotificationsRead(): Int =
        api.markNotificationsRead(paths.markRead).marked.coerceAtLeast(0)

    override suspend fun alerts(): List<PriceAlert> =
        api.alerts(paths.alerts).items.mapNotNull { it.toDomain(platform) }

    override suspend fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ): PriceAlert {
        val safeSymbol = requireNotNull(normalizeProductAlertSymbol(symbol, platform)) { "Unsupported alert symbol" }
        require(value.isFinite() && value > 0.0) { "Alert value must be a positive finite number" }
        return requireNotNull(
            api.createAlert(
                paths.alerts,
                PriceAlertCreateDto(
                    symbol = safeSymbol,
                    condition = condition.wireValue,
                    value = value,
                    trigger = trigger.wireValue,
                ),
            ).alert?.toDomain(platform),
        ) { "Invalid alert payload" }
    }

    override suspend fun setAlertActive(alertId: String, active: Boolean): PriceAlert = requireNotNull(
        api.patchAlert(paths.alert(alertId), PriceAlertPatchDto(active)).alert?.toDomain(platform),
    ) { "Invalid alert payload" }

    override suspend fun deleteAlert(alertId: String): Boolean = api.deleteAlert(paths.alert(alertId)).removed

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkNotificationGateway =
            NetworkNotificationGateway(
                api = retrofit.create(NotificationApi::class.java),
                platform = platform,
                paths = NotificationPaths.of(platform),
            )
    }
}

internal fun normalizeProductAlertSymbol(raw: String, platform: MarketPlatform): String? {
    val normalized = raw.trim().uppercase().replace("/", "").replace("-", "")
    return when (platform) {
        MarketPlatform.COINEPRO_FX -> normalized.takeIf { it == "XAUUSD" || it == "XAGUSD" }
        MarketPlatform.TRADEYAR -> normalized.takeIf { it.endsWith("USDT") && it.length > 4 }
    }
}

internal fun PushPreferencesDto.toDomain() = PushPreferences(
    newSignals = newSignals,
    signalUpdates = signalUpdates,
    priceAlerts = priceAlerts,
)

internal fun PushPreferences.toDto() = PushPreferencesDto(
    newSignals = newSignals,
    signalUpdates = signalUpdates,
    priceAlerts = priceAlerts,
)

internal fun NotificationDto.toDomain(): AppNotification? {
    val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val seconds = ts ?: return null
    return AppNotification(
        kind = kind.orEmpty(),
        title = safeTitle,
        body = body.orEmpty(),
        data = data,
        timestampEpochMillis = seconds * 1000L,
        read = read,
    )
}

internal fun PriceAlertDto.toDomain(platform: MarketPlatform): PriceAlert? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null
    // Filtered on the way in as well as on the way out. A stored alert from an older build could
    // name the other platform's market, and rendering it here would put a crypto pair on a forex
    // screen just as surely as creating one would.
    val safeSymbol = normalizeProductAlertSymbol(symbol ?: return null, platform) ?: return null
    val safeValue = value?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeCondition = PriceAlertCondition.entries.firstOrNull { it.wireValue == condition } ?: return null
    val safeTrigger = PriceAlertTrigger.entries.firstOrNull { it.wireValue == trigger } ?: return null
    return PriceAlert(
        id = safeId,
        market = market.orEmpty(),
        symbol = safeSymbol,
        condition = safeCondition,
        value = safeValue,
        trigger = safeTrigger,
        expiresAtEpochMillis = expiresAtMs ?: expiresAt,
        active = active,
        createdAtEpochMillis = createdAtMs ?: 0L,
        lastTriggeredAtEpochMillis = lastTriggeredAtMs,
    )
}

/**
 * Turns a thrown failure into something a reader can act on.
 *
 * The server writes its refusals in Persian and means them to be shown — "the 20-alert limit is
 * full, delete one and try again" tells someone exactly what to do next. An HttpException's own
 * message is the status line, so reading that instead would replace real guidance with "HTTP 409".
 * Anything that never reached a verdict falls back to owned copy.
 */
/** Kept as a name local to this module; the behaviour now lives in one place for every caller. */
internal fun Throwable.toNotificationMessage(fallback: MessageKey): UiMessage =
    toServerMessage(fallback)
