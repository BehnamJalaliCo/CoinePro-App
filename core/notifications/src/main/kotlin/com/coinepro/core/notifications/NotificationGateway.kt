package com.coinepro.core.notifications

import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationGateway {
    suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean
    suspend fun unregisterDevice(token: String): Boolean
    suspend fun preferences(): PushPreferences
    suspend fun updatePreferences(preferences: PushPreferences): PushPreferences
    suspend fun notifications(limit: Int = 50): NotificationPage
    suspend fun markNotificationsRead()
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
)

internal interface NotificationApi {
    @POST("user/signals/mobile/push/devices")
    suspend fun registerDevice(@Body body: DeviceRegistrationDto): DeviceRegistrationResponseDto

    @HTTP(method = "DELETE", path = "user/signals/mobile/push/devices", hasBody = true)
    suspend fun unregisterDevice(@Body body: DeviceUnregisterDto): DeviceUnregisterResponseDto

    @GET("user/signals/mobile/push/preferences")
    suspend fun preferences(): PreferencesResponseDto

    @PATCH("user/signals/mobile/push/preferences")
    suspend fun updatePreferences(@Body body: PushPreferencesDto): PreferencesResponseDto

    @GET("user/signals/mobile/notifications")
    suspend fun notifications(@Query("limit") limit: Int): NotificationResponseDto

    @POST("user/signals/mobile/notifications/read")
    suspend fun markNotificationsRead()

    @GET("user/signals/mobile/alerts")
    suspend fun alerts(): AlertListResponseDto

    @POST("user/signals/mobile/alerts")
    suspend fun createAlert(@Body body: PriceAlertCreateDto): AlertResponseDto

    @PATCH("user/signals/mobile/alerts/{alertId}")
    suspend fun patchAlert(
        @Path("alertId") alertId: String,
        @Body body: PriceAlertPatchDto,
    ): AlertResponseDto

    @DELETE("user/signals/mobile/alerts/{alertId}")
    suspend fun deleteAlert(@Path("alertId") alertId: String): DeleteAlertResponseDto
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
)
internal data class PriceAlertDto(
    val id: String? = null,
    val market: String? = null,
    val symbol: String? = null,
    val condition: String? = null,
    val value: Double? = null,
    val trigger: String? = null,
    val expiresAt: String? = null,
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

class NetworkNotificationGateway private constructor(
    private val api: NotificationApi,
) : NotificationGateway {
    override suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean =
        api.registerDevice(DeviceRegistrationDto(token = token, appVersion = appVersion, locale = locale)).registered

    override suspend fun unregisterDevice(token: String): Boolean =
        api.unregisterDevice(DeviceUnregisterDto(token)).removed

    override suspend fun preferences(): PushPreferences = api.preferences().preferences.toDomain()

    override suspend fun updatePreferences(preferences: PushPreferences): PushPreferences =
        api.updatePreferences(preferences.toDto()).preferences.toDomain()

    override suspend fun notifications(limit: Int): NotificationPage {
        val response = api.notifications(limit)
        return NotificationPage(
            items = response.items.mapNotNull { it.toDomain() },
            unread = response.unread.coerceAtLeast(0),
        )
    }

    override suspend fun markNotificationsRead() {
        api.markNotificationsRead()
    }

    override suspend fun alerts(): List<PriceAlert> = api.alerts().items.mapNotNull { it.toDomain() }

    override suspend fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ): PriceAlert = requireNotNull(
        api.createAlert(
            PriceAlertCreateDto(
                symbol = symbol.trim().uppercase().replace("/", "").replace("-", ""),
                condition = condition.wireValue,
                value = value,
                trigger = trigger.wireValue,
            ),
        ).alert?.toDomain(),
    ) { "Invalid alert payload" }

    override suspend fun setAlertActive(alertId: String, active: Boolean): PriceAlert = requireNotNull(
        api.patchAlert(alertId, PriceAlertPatchDto(active)).alert?.toDomain(),
    ) { "Invalid alert payload" }

    override suspend fun deleteAlert(alertId: String): Boolean = api.deleteAlert(alertId).removed

    companion object {
        fun create(retrofit: Retrofit): NetworkNotificationGateway =
            NetworkNotificationGateway(retrofit.create(NotificationApi::class.java))
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

internal fun PriceAlertDto.toDomain(): PriceAlert? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null
    val safeSymbol = symbol?.takeIf { it.isNotBlank() } ?: return null
    val safeValue = value?.takeIf { it > 0 } ?: return null
    val safeCondition = PriceAlertCondition.entries.firstOrNull { it.wireValue == condition } ?: return null
    val safeTrigger = PriceAlertTrigger.entries.firstOrNull { it.wireValue == trigger } ?: return null
    return PriceAlert(
        id = safeId,
        market = market.orEmpty(),
        symbol = safeSymbol,
        condition = safeCondition,
        value = safeValue,
        trigger = safeTrigger,
        expiresAt = expiresAt,
        active = active,
        createdAtEpochMillis = createdAtMs ?: 0L,
        lastTriggeredAtEpochMillis = lastTriggeredAtMs,
    )
}
