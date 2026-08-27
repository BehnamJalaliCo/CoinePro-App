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

    /** What the published signals actually did. See [GuestTrackRecord.available]. */
    suspend fun trackRecord(limit: Int = 12): AppResult<GuestTrackRecord>

    /** The public channels and their member counts, each carrying its own availability. */
    suspend fun community(): AppResult<GuestCommunity>
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

    override suspend fun trackRecord(limit: Int): AppResult<GuestTrackRecord> = call {
        val dto = api.trackRecord(limit = limit)
        val entries = dto.signals.orEmpty().mapNotNull { row ->
            val symbol = row.symbol?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            // A row with no recorded outcome is not a record of anything. Dropped rather than
            // drawn as a flat zero, which would read as a trade that went nowhere.
            val gain = row.pctGain ?: return@mapNotNull null
            TrackRecordEntry(
                symbol = symbol,
                timeframe = row.timeframe?.takeIf(String::isNotBlank),
                buy = !row.direction.equals("SHORT", ignoreCase = true),
                // The server's own verdict, not `gain > 0`. It defines a win by the ladder rungs
                // banked, which is not the same test, and disagreeing with it here would put two
                // different win rates in front of the same reader.
                win = row.isWin ?: (gain > 0),
                percentGain = gain,
                riskReward = row.riskReward,
            )
        }
        GuestTrackRecord(entries = entries, available = dto.dataAvailable ?: entries.isNotEmpty())
    }

    override suspend fun community(): AppResult<GuestCommunity> = call {
        val dto = api.community()
        GuestCommunity(
            channels = dto.channels.orEmpty().mapNotNull { row ->
                val key = row.key?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                // The label is the server's Persian one. Falling back to the @username keeps a
                // channel on screen when the label is missing, which is better than dropping a
                // real place people can go because one field was empty.
                val label = row.label?.takeIf(String::isNotBlank)
                    ?: row.username?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                CommunityChannel(
                    key = key,
                    label = label,
                    url = row.url?.takeIf(String::isNotBlank),
                    members = row.members.availableOr(row.available),
                )
            },
            total = dto.telegramMembersTotal.availableOr(dto.telegramMembersTotalAvailable),
            botUsers = dto.botUsers?.value.availableOr(dto.botUsers?.available),
            note = dto.note?.takeIf(String::isNotBlank),
        )
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

/**
 * A count is [MemberCount.Known] only when the server both sent a number **and** said it is good.
 *
 * Both halves matter and neither implies the other. An absent flag reads as unavailable rather than
 * as available, because the route documents the flag as the thing to read: a number that arrives
 * without one is a number nothing is vouching for, and the whole rule beside that route is that an
 * unvouched count must not be drawn.
 */
private fun Long?.availableOr(available: Boolean?): MemberCount =
    if (available == true && this != null) MemberCount.Known(this) else MemberCount.Unavailable
