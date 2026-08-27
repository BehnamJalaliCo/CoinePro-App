package com.coinepro.core.guest

import com.google.gson.FieldNamingPolicy
import com.coinepro.core.common.AppResult
import com.google.gson.GsonBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public routes' JSON, parsed by the app's own Gson configuration.
 *
 * This exists because the wire naming here is mixed — `age_ms` is snake_case and `titleFa` is
 * camelCase, from the same server — while the app's Gson is configured for snake_case throughout.
 * A camelCase key with no `@SerializedName` does not fail: it parses as null, and the row renders
 * blank. That is the sort of wrongness that survives a review and a release, so it is asserted
 * against a payload copied from the routes rather than left to the annotations being right.
 *
 * The Gson here is built with the same policy as `NetworkFactory.retrofit`. If that policy ever
 * changes, this test is where the change is caught rather than on somebody's phone.
 */
class GuestWireTest {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    @Test
    fun `the one-letter price row parses, and an omitted key stays null`() {
        val json = """
            {"v":1,"type":"snapshot","ts":1756000000000,"source":"lbank-ws","age_ms":340,
             "stale":false,"count":2,
             "data":[{"s":"BTCUSDT","p":64182.4,"t":1756000000000,"c":2.14,"h":64900.0,"l":62800.0,"v":1.2E9},
                     {"s":"TONUSDT","p":5.118,"t":1756000000000}]}
        """.trimIndent()

        val dto = gson.fromJson(json, PriceSnapshotDto::class.java)

        assertEquals(340L, dto.ageMs)
        assertEquals(false, dto.stale)
        val rows = dto.data.orEmpty()
        assertEquals("BTCUSDT", rows[0].symbol)
        assertEquals(64_182.4, rows[0].price!!, 0.0)
        assertEquals(2.14, rows[0].changePercent24h!!, 0.0)
        // Omitted, not zero. A zero here would draw a flat day the server never claimed.
        assertNull(rows[1].changePercent24h)
        assertNull(rows[1].volume24h)
    }

    @Test
    fun `the camelCase news keys parse despite the snake_case policy`() {
        val json = """
            {"data":[{"id":7,"slug":"btc-etf-inflow","source":"TradeYar",
                      "sourceUrl":"https://example.invalid/a","titleFa":"عنوان",
                      "summaryFa":"خلاصه","publishedAt":"2026-08-26T10:00:00+00:00","importance":2}],
             "meta":{"count":1,"type":"news"}}
        """.trimIndent()

        val item = gson.fromJson(json, NewsListDto::class.java).data!!.single()

        assertEquals("عنوان", item.titleFa)
        assertEquals("خلاصه", item.summaryFa)
        assertEquals("2026-08-26T10:00:00+00:00", item.publishedAt)
        assertEquals("https://example.invalid/a", item.sourceUrl)
    }

    @Test
    fun `a snapshot that does not say whether it is stale is treated as stale`() = runTest {
        val gateway = NetworkGuestGateway(FakeApi("""{"data":[{"s":"BTCUSDT","p":64182.4}]}"""))

        val result = gateway.prices(listOf("BTCUSDT"))

        // Absent reads as stale, not as fresh. A feed that did not say is one the app must not
        // vouch for, and the cost of being wrong lands on somebody's trade.
        assertTrue(result is AppResult.Success)
        assertEquals(true, (result as AppResult.Success).value.stale)
    }

    @Test
    fun `a row with no price is dropped rather than drawn as a dash`() = runTest {
        val gateway = NetworkGuestGateway(
            FakeApi("""{"stale":false,"data":[{"s":"BTCUSDT","p":64182.4},{"s":"BROKEN"}]}"""),
        )

        val result = gateway.prices(listOf("BTCUSDT", "BROKEN"))

        // The reader is looking at a price list. A row that cannot carry a price is not a row.
        assertEquals(1, (result as AppResult.Success).value.quotes.size)
    }

    @Test
    fun `the community payload parses, including its snake_case availability flags`() {
        val json = """
            {"channels":[{"key":"signals","username":"@coinepro","url":"https://t.me/coinepro",
                          "label":"کانال سیگنال","available":true,"members":18420,"source":"telegram"},
                         {"key":"chat","username":"@coinepro_chat","url":"https://t.me/coinepro_chat",
                          "label":"گروه گفت‌وگو","available":false,"members":null,"source":"telegram"}],
             "telegram_members_total":18420,"telegram_members_total_available":true,
             "bot_users":{"available":true,"value":7915,"label":"کاربر ربات","source":"db"},
             "fetched_at":"2026-08-26T10:00:00+00:00","note":"شمارش‌ها هر ساعت به‌روز می‌شود."}
        """.trimIndent()

        val dto = gson.fromJson(json, CommunityDto::class.java)

        assertEquals(true, dto.telegramMembersTotalAvailable)
        assertEquals(18_420L, dto.telegramMembersTotal)
        assertEquals(7_915L, dto.botUsers?.value)
        assertEquals(false, dto.channels!![1].available)
    }

    @Test
    fun `a channel the server could not read is unavailable, never a zero`() = runTest {
        val gateway = NetworkGuestGateway(
            FakeApi(
                community = CommunityDto(
                    channels = listOf(
                        CommunityChannelDto(key = "a", label = "الف", available = true, members = 12),
                        // The shape the route actually returns when Telegram refuses: the flag is
                        // false and `members` is whatever it last had, or nothing at all. Both must
                        // render as unavailable.
                        CommunityChannelDto(key = "b", label = "ب", available = false, members = 99),
                        CommunityChannelDto(key = "c", label = "ج", available = null, members = null),
                    ),
                    telegramMembersTotal = 500,
                    telegramMembersTotalAvailable = false,
                ),
            ),
        )

        val community = (gateway.community() as AppResult.Success).value

        assertEquals(MemberCount.Known(12), community.channels[0].members)
        // 99 is on the wire and is *not* used. The flag is the thing the route documents, and a
        // stale count drawn as current is the failure it documents it against.
        assertEquals(MemberCount.Unavailable, community.channels[1].members)
        assertEquals(MemberCount.Unavailable, community.channels[2].members)
        // Likewise the total: a number with a false flag beside it is not a number.
        assertEquals(MemberCount.Unavailable, community.total)
        assertEquals(MemberCount.Unavailable, community.botUsers)
    }

    @Test
    fun `a community with no channel and no readable count reports itself empty`() = runTest {
        val gateway = NetworkGuestGateway(FakeApi(community = CommunityDto()))

        // The screen draws no heading for this. A section headed "the community" over nothing at
        // all reads as a community nobody joined, which is a worse claim than staying quiet.
        assertTrue((gateway.community() as AppResult.Success).value.isEmpty)
    }
}

private class FakeApi(
    private val body: String = """{"data":[]}""",
    private val community: CommunityDto = CommunityDto(),
) : GuestApi {
    override suspend fun prices(symbols: String): PriceSnapshotDto =
        GsonBuilder().create().fromJson(body, PriceSnapshotDto::class.java)

    override suspend fun news(type: String, limit: Int): NewsListDto = NewsListDto(emptyList())

    override suspend fun trackRecord(limit: Int): TrackRecordDto = TrackRecordDto(emptyList())

    override suspend fun community(): CommunityDto = community
}
