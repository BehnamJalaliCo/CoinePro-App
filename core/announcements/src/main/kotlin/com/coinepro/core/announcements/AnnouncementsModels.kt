package com.coinepro.core.announcements

import com.coinepro.core.common.ErrorKind
import java.time.Instant

/**
 * How loudly an announcement is meant to be read.
 *
 * The same three words the news adapter already reads, because this is the same column: TradeYar
 * serves both surfaces out of `news_posts`, and `importance` is what it stores. Reusing the
 * vocabulary means a server that already fills the column for news needs to do nothing extra, and
 * a server that leaves it empty for announcements produces [UNKNOWN], which the screen draws as
 * nothing at all rather than as a guess.
 */
enum class AnnouncementImportance { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * One thing the service said, kept for as long as the server keeps it.
 *
 * ### There is no `isStale` here, and its absence is the design
 *
 * `MarketNewsItem` carries one and must: a headline is history a day later, and a feed that
 * presented last Tuesday's rate decision with the same weight as this morning's would be lying
 * about what is current. An announcement is the opposite kind of statement. "The exchange
 * connection is down" is true until it is not, and fading it after twenty-four hours would tell a
 * reader the outage had passed when nobody had said so. So the route sends no such flag, and this
 * type has nowhere to put one — which is what stops a later change from quietly adding the fading
 * behaviour back by copying the news card.
 *
 * ### [source] is optional here and mandatory there
 *
 * `MarketNewsDto.toDomain` drops a row with no source, correctly: an unattributed market claim is
 * worth less than no claim. An announcement's author is this service, which is the one attribution
 * a reader does not need spelled out, so a row without it is complete and is kept.
 */
data class Announcement(
    val id: String,
    val title: String,
    /** The announcement itself. Null where the server sent a title and nothing more. */
    val body: String?,
    /** Who said it, when that is somebody other than us. Usually null. See the note above. */
    val source: String?,
    /** Where to read more, `https` only. Null far more often than not. */
    val url: String?,
    val publishedAt: Instant,
    val importance: AnnouncementImportance,
)

/**
 * What the announcements screen knows.
 *
 * ### Why [loaded] exists, and what goes wrong without it
 *
 * This is the one screen in the app whose correct state on the day it ships is empty — nothing has
 * been announced yet, and that is not a fault. So the empty list has to mean two different things
 * at two different moments, and only one of them can be said out loud: before the first read
 * finishes, an empty list means *we have not asked*, and a screen that answers that with «هنوز
 * اطلاعیه‌ای منتشر نشده» is stating as fact something it does not know. After a read succeeds, the
 * same empty list means *we asked and there is nothing*, which is exactly what the reader should be
 * told.
 *
 * Without this flag the two are indistinguishable and the screen picks one. Picking the reassuring
 * sentence means it appears for a frame on every cold start, including the one where the request is
 * about to fail; picking the spinner means a reader on a dead connection watches it forever. The
 * flag is what lets the screen say nothing until it has something true to say.
 *
 * [failure] and [failureText] are separate for the reason [failureText] is nullable: the server's
 * own wording is shown when it gave one, and when it did not — a connection that never reached a
 * verdict has no server text by definition — the kind is what the screen writes its own sentence
 * from. An audience on unstable connections meets [ErrorKind.NETWORK] far more often than anything
 * else, and "you are offline" and "the service refused" are not the same news.
 */
data class AnnouncementsState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Whether a read has ever completed. See the note above; this is not the same as non-empty. */
    val loaded: Boolean = false,
    val announcements: List<Announcement> = emptyList(),
    /** The last failure's kind, or null when the last read succeeded. */
    val failure: ErrorKind? = null,
    /** The server's own wording for [failure]. Null when it never answered at all. */
    val failureText: String? = null,
)
