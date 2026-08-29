package com.coinepro.core.announcements

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.RetryAfter
import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.network.ApiErrors
import java.io.IOException
import java.net.URI
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * The durable list of things this service has said.
 *
 * [AppResult] rather than a thrown exception, because the caller has to be able to tell an empty
 * answer from a failed one and the two are the same object once a `runCatching` has swallowed the
 * difference. An empty success is the expected state on this route — see [AnnouncementsState] —
 * and a failure that arrived as an empty list would be reported to the reader as "nothing has been
 * announced", which is the single most misleading sentence this feature could print.
 */
interface AnnouncementsGateway {
    /**
     * The most recent [limit] announcements, newest first as the server ordered them.
     *
     * There is no paging and none is wanted. An announcement list that needs a second page is a
     * list nobody is reading; thirty is the server's own default and is more than this channel is
     * expected to hold in a year.
     */
    suspend fun announcements(limit: Int = DEFAULT_ANNOUNCEMENT_LIMIT): AppResult<List<Announcement>>
}

/** Thirty, which is the number the route was delivered with. See [AnnouncementsApi]. */
const val DEFAULT_ANNOUNCEMENT_LIMIT: Int = 30

/**
 * TradeYar's announcements, and TradeYar's only.
 *
 * ### Why there is no platform parameter here, unlike every gateway beside it
 *
 * `NetworkMarketIntelGateway`, `NetworkAccountGateway` and `NetworkNotificationGateway` all take a
 * [com.coinepro.core.model.MarketPlatform] and switch a prefix on it, because both backends serve
 * those surfaces. CoinePro-FX serves nothing at this address. A platform parameter here would be a
 * type saying the feature exists on both, with a `when` branch pointing at a path that answers 404
 * — and a 404 reaches a screen as an ordinary outage, so a reader on the forex platform would be
 * told the announcements service was down when in fact it was never built for them.
 *
 * `AiAssistantGateway` is the precedent and takes the same shape: one constructor, wired to one
 * platform's Retrofit, with the entry point absent on the other rather than present and failing.
 */
class NetworkAnnouncementsGateway internal constructor(
    private val api: AnnouncementsApi,
) : AnnouncementsGateway {

    override suspend fun announcements(limit: Int): AppResult<List<Announcement>> = try {
        AppResult.Success(api.announcements(limit).rows.mapNotNull(AnnouncementDto::toDomain))
    } catch (error: HttpException) {
        val apiError = ApiErrors.from(error)
        val kind = when (error.code()) {
            401, 403 -> ErrorKind.AUTH
            400, 409, 422 -> ErrorKind.VALIDATION
            429 -> ErrorKind.RATE_LIMIT
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.UNKNOWN
        }
        AppResult.Failure(
            kind = kind,
            message = apiError.message,
            cause = error,
            retryAfterSeconds = if (kind == ErrorKind.RATE_LIMIT) {
                apiError.retryAfterSeconds
                    ?: RetryAfter.parseSeconds(error.response()?.headers()?.get("Retry-After"))
            } else {
                null
            },
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        // Where a body in a shape this module cannot read lands. See [AnnouncementListDto] for why
        // that is reported as a failure rather than absorbed into an empty list.
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        /**
         * [retrofit] must be the TradeYar one. There is no assertion available to enforce that — a
         * `Retrofit` does not know which platform built it — so the binding is the only place it
         * can be got right, and the app's provider takes `@CryptoPlatform` for exactly that reason.
         */
        fun create(retrofit: Retrofit): AnnouncementsGateway =
            NetworkAnnouncementsGateway(retrofit.create(AnnouncementsApi::class.java))
    }
}

/**
 * A published row, or null where it is not one.
 *
 * Four fields are required and a row missing any of them is dropped rather than shown with a hole
 * in it. An announcement with no title is a blank card; one with no timestamp cannot be placed in
 * the sequence, and an undated statement about whether a service is currently down is worse than
 * no statement — the reader has no way to tell this morning's outage notice from last spring's.
 */
internal fun AnnouncementDto.toDomain(): Announcement? {
    val safeId = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val published = parseWireInstant(publishedAt) ?: return null
    return Announcement(
        id = safeId,
        title = safeTitle,
        body = summary?.trim()?.takeIf(String::isNotEmpty),
        source = source?.trim()?.takeIf(String::isNotEmpty),
        url = safeHttpsUrl(url),
        publishedAt = published,
        importance = parseImportance(importance),
    )
}

/**
 * Anything the server did not say is [AnnouncementImportance.UNKNOWN].
 *
 * Not `LOW`. An unfilled column means nobody graded this announcement, and defaulting to the
 * quietest grade would let a genuine outage notice arrive marked as the least urgent thing on the
 * screen. Unknown draws no label at all, which says nothing and therefore says nothing false.
 */
internal fun parseImportance(value: String?): AnnouncementImportance =
    when (value?.trim()?.lowercase()) {
        "low" -> AnnouncementImportance.LOW
        "medium" -> AnnouncementImportance.MEDIUM
        "high", "critical" -> AnnouncementImportance.HIGH
        else -> AnnouncementImportance.UNKNOWN
    }

/**
 * One address worth handing to a browser, or null.
 *
 * The same rule `core:marketintel` applies to a story's link, repeated rather than shared because
 * the two modules do not depend on each other. Anything but `https` with a real host is discarded
 * here so no other scheme is ever carried as far as an `ACTION_VIEW`.
 */
internal fun safeHttpsUrl(value: String?): String? = runCatching {
    value?.trim()?.takeIf(String::isNotEmpty)?.let { raw ->
        val uri = URI(raw)
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) raw else null
    }
}.getOrNull()
