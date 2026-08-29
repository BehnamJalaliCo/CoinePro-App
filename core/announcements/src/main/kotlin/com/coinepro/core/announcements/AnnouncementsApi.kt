package com.coinepro.core.announcements

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The one route this module reads, exactly as TradeYar delivered it.
 *
 * ```
 * GET /api/mobile/v1/announcements?limit=30
 * ```
 *
 * ### Why it is not `news/list?content_type=announcement`
 *
 * That is what the app asked for — `docs/RESEARCH_BINANCE_AND_PAID_TIERS.md`, «آنچه سرور باید
 * بدهد», item 5 — and the server was right to refuse it. A flag on a shared route means the two
 * behaviours behind it have to agree, and these two do not: news is transient and gets a `stale`
 * flag, an announcement is durable and must not have one. One of the two would always have been
 * ignoring the other's parameter, and the first person to add a third caller would not have known
 * which.
 *
 * It reads the same `news_posts` table under the same `status = 'published'` filter as the website,
 * which is the part that matters more than the address: the app and the site cannot disagree about
 * what has been published, because there is one row and one predicate.
 *
 * ### The prefix is TradeYar's and there is no second spelling
 *
 * Every other gateway in this app builds its path from a per-platform prefix, because both
 * backends serve the same surface at two addresses. This one does not, because CoinePro-FX has no
 * such route at all — see [NetworkAnnouncementsGateway].
 */
internal interface AnnouncementsApi {
    @GET("api/mobile/v1/announcements")
    suspend fun announcements(@Query("limit") limit: Int): AnnouncementListDto
}

/**
 * The envelope, read under any of the three keys this server already uses for a list.
 *
 * The route is new and no app build has yet seen a body from it. Guessing one name and being wrong
 * would fail in the worst possible way *for this screen in particular*: an envelope whose key does
 * not match parses into an object with every field null, which becomes an empty list, which is
 * indistinguishable from the empty list this screen is expected to show on day one. The bug would
 * look exactly like the feature working.
 *
 * So all three spellings this backend uses elsewhere are read — `announcements` for the route's own
 * name, `items` as on `notifications` and `alerts`, `news` as on `market-intelligence` — and the
 * first one present wins. This is the same tactic `PriceAlertDto` uses for `expires_at` against
 * `expires_at_ms`, and for the same reason: reading both spellings costs one nullable field, and
 * reading the wrong one costs a feature that silently shows nothing.
 *
 * A **bare JSON array** is the one shape this cannot read, and that is deliberate rather than
 * overlooked. Gson refuses to parse an array into an object and throws, the throw becomes an
 * [com.coinepro.core.common.AppResult.Failure], and the screen shows a failure. A visible failure
 * is the right outcome for a shape we did not expect — the alternative is the silent empty above.
 */
internal data class AnnouncementListDto(
    val announcements: List<AnnouncementDto>? = null,
    val items: List<AnnouncementDto>? = null,
    val news: List<AnnouncementDto>? = null,
) {
    val rows: List<AnnouncementDto> get() = announcements ?: items ?: news ?: emptyList()
}

/**
 * One published row.
 *
 * The field names are `news_posts`' own, mapped the way `docs/NEWS_REQUEST_TRADEYAR.md` already
 * agreed them for the news adapter over the same table: `title_fa` arrives as `title`, `summary_fa`
 * as `summary`, `source_url` as `url`, `importance` as `importance`. Gson is configured with
 * `LOWER_CASE_WITH_UNDERSCORES`, so `publishedAt` here reads `published_at` on the wire.
 *
 * Every field is nullable including the four that are required, because "required" is a statement
 * about a well-formed row and this type has to be able to hold a malformed one long enough for
 * [toDomain] to reject it. A non-null Kotlin field with no value in the JSON is null at runtime
 * anyway under Gson, and declaring it non-null would only move the failure to the first read.
 */
internal data class AnnouncementDto(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val source: String? = null,
    val url: String? = null,
    val publishedAt: String? = null,
    val importance: String? = null,
)
