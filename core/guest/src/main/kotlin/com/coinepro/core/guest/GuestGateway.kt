package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit

/**
 * What the app can show somebody who has not signed in.
 *
 * There is no token in any of this and no `onUnauthorized` to trip: these routes are public, so the
 * failure modes are network and server, and nothing here can log a reader out.
 */
interface GuestGateway {
    /**
     * Prices for the symbols named.
     *
     * The caller passes what is on screen rather than asking for everything. The route will return
     * several hundred rows for an empty list, and a list nobody is looking at is bandwidth spent on
     * nothing — on a mobile connection, someone else's bandwidth.
     */
    suspend fun prices(symbols: List<String>): AppResult<GuestPrices>

    suspend fun news(limit: Int = 12): AppResult<List<GuestHeadline>>
}

class NetworkGuestGateway internal constructor(
    private val api: GuestApi,
) : GuestGateway {

    override suspend fun prices(symbols: List<String>): AppResult<GuestPrices> = call {
        val dto = api.prices(symbols.joinToString(","))
        GuestPrices(
            quotes = dto.data.orEmpty().mapNotNull { row ->
                val symbol = row.symbol?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                // A row with no price is dropped rather than shown as a dash. The reader is looking
                // at a price list; a row that cannot carry a price is not a row.
                val price = row.price ?: return@mapNotNull null
                GuestQuote(
                    symbol = symbol,
                    price = price,
                    changePercent24h = row.changePercent24h,
                    high24h = row.high24h,
                    low24h = row.low24h,
                    volume24h = row.volume24h,
                )
            },
            // Absent reads as stale, not as fresh. A feed that did not say is one the app must not
            // vouch for — the whole cost of being wrong here lands on somebody's trade.
            stale = dto.stale ?: true,
            ageMillis = dto.ageMs,
        )
    }

    override suspend fun news(limit: Int): AppResult<List<GuestHeadline>> = call {
        api.news(limit = limit).data.orEmpty().mapNotNull { item ->
            val slug = item.slug?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            // Persian title or nothing. This app's reader is Persian by default and an English
            // headline in a Persian column is worse than one fewer headline.
            val title = item.titleFa?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            GuestHeadline(
                slug = slug,
                title = title,
                summary = item.summaryFa?.takeIf(String::isNotBlank),
                source = item.source?.takeIf(String::isNotBlank),
                publishedAt = item.publishedAt?.takeIf(String::isNotBlank),
            )
        }
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        AppResult.Failure(
            kind = when (error.code()) {
                429 -> ErrorKind.RATE_LIMIT
                in 500..599 -> ErrorKind.SERVER
                else -> ErrorKind.UNKNOWN
            },
            cause = error,
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkGuestGateway =
            NetworkGuestGateway(retrofit.create(GuestApi::class.java))
    }
}
