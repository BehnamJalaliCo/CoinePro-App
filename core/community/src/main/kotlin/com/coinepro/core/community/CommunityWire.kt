package com.coinepro.core.community

import com.coinepro.core.common.parseWireInstant
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * The community bodies, read as bodies rather than as declared types.
 *
 * ### Why not Gson data classes, when the rest of `core:academy` uses them
 *
 * Because the OpenAPI does not describe these responses. Every one of the community operations
 * declares `"content": {"application/json": {"schema": {}}}` — an empty schema, which is FastAPI's
 * way of saying the handler returns a bare `dict` and nothing typed it. The shapes below were read
 * out of `academy.py`'s handlers instead, which is the best evidence there is; but "the best
 * evidence there is" is exactly the situation `MarketIntelGateway`'s KDoc was written about, and
 * the two production bugs it records both came from binding a declared field name to a body nobody
 * had held in their hand:
 *
 * * a Kotlin default that Gson never runs, so one absent key nulls a non-null field and takes the
 *   whole screen down with a `NullPointerException`;
 * * a camel-case spelling on a serializer written next to the handler, so every row parses with a
 *   null date, `toDomain` drops all of them, and the screen is empty behind an HTTP 200.
 *
 * Both are structural, both are invisible from the reader's chair, and both are removed by reading
 * the JSON with an explicit list of spellings per field. So `replies_count` is also accepted as
 * `repliesCount` and `reply_count`, `created_at` as `createdAt` and `created`, and `best_reply_id`
 * as `bestReplyId` — snake **and** camel, everywhere, because the day somebody puts a Pydantic
 * response model in front of these handlers the spelling flips for free and this app must not
 * notice.
 *
 * ### What the looseness stops at
 *
 * Nothing is invented. A row with no usable id, or with no text, is dropped — a post is a number
 * and some words, and a card with neither is not a post that failed to parse, it is not a post. The
 * count of what arrived is carried out in [CommunityFeedPage.received] so that "empty" and
 * "unreadable" never look the same from outside.
 */
internal object CommunityWire {

    /**
     * `GET /academy/community` → `{"posts": [...]}`.
     *
     * The envelope is checked for a bare array too. The handler returns the object today; the array
     * costs one branch and removes a whole class of silent empty screen if the envelope is ever
     * flattened.
     */
    fun readFeed(body: JsonElement?, page: Int): CommunityFeedPage {
        val rows = arrayUnder(body, "posts", "items", "results", "data", "rows")
        val objects = rows.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
        return CommunityFeedPage(
            posts = objects.mapNotNull(::readPost),
            page = page,
            received = objects.size,
            sampleKeys = objects.firstOrNull()?.keySet()?.joinToString(","),
        )
    }

    /**
     * `GET /academy/community/search` → `{"items": [...]}`.
     *
     * A different envelope key from the feed's, and a different row shape: `community_search` builds
     * its dictionaries by hand with a **truncated** body — `(p.content or "")[:200]` — a masked
     * author, and no `status`, `reactions` or `best_reply_id`. Read through the same [readPost] all
     * the same, because every field it does send is spelled the way the feed spells it and the ones
     * it omits already have honest defaults here.
     */
    fun readSearch(body: JsonElement?): List<CommunityPost> =
        arrayUnder(body, "items", "posts", "results", "data")
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .mapNotNull(::readPost)

    /**
     * `GET /academy/community/{pid}` → `{"post": {...}, "replies": [...]}`.
     *
     * Null when the post itself could not be read, which the gateway turns into the same "not
     * found" the route would have sent — a thread screen with a reply list and no post above it is
     * a worse answer than an error.
     */
    fun readThread(body: JsonElement?): CommunityThread? {
        val root = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val postObject = root.get("post")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        // A handler that one day returns the post's own fields at the top level rather than nested.
        // Cheap to accept and the alternative is an empty screen.
            ?: root.takeIf { it.has("content") }
            ?: return null
        val post = readPost(postObject) ?: return null
        val replies = arrayUnder(root, "replies", "items", "comments")
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .mapNotNull { readReply(it, bestReplyId = post.bestReplyId) }
        return CommunityThread(post = post, replies = replies)
    }

    /** `POST /academy/community` and `.../reply`. The reply route sends only `status`. */
    fun readWriteOutcome(body: JsonElement?): CommunityWriteOutcome {
        val row = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val status = row?.text("status", "state").orEmpty()
        return CommunityWriteOutcome(
            id = row?.number("id", "post_id", "postId")?.toLong(),
            // Anything that is not the word `pending` counts as published, and the asymmetry is
            // deliberate: the only outcome worth interrupting a reader for is the one where their
            // post is *not* on the board yet, and a status this build has never seen should not be
            // reported as a hold.
            published = !status.equals("pending", ignoreCase = true),
            message = row?.text("message", "detail", "msg"),
        )
    }

    /**
     * `POST /academy/community/{pid}/like` → `{"likes": n, "liked": bool}`.
     *
     * [fallback] is the count the screen already had. The route's answer is authoritative and this
     * is only reached when the body arrived without a `likes` key at all — in which case keeping
     * the number already on screen is better than showing zero, which would read as "your like
     * removed every other one".
     */
    fun readLike(body: JsonElement?, fallback: Int): CommunityLikeOutcome {
        val row = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        return CommunityLikeOutcome(
            likes = row?.number("likes", "like_count", "likeCount", "count")?.toInt() ?: fallback,
            liked = row?.flag("liked", "is_liked", "isLiked") ?: false,
        )
    }

    /** `POST /academy/community/{pid}/react` → `{"reactions": {emoji: n}, "mine": [emoji]}`. */
    fun readReaction(body: JsonElement?): CommunityReactionOutcome {
        val row = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        return CommunityReactionOutcome(
            counts = readReactions(row?.get("reactions") ?: row?.get("counts")),
            mine = row?.get("mine")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?.filter(JsonElement::isJsonPrimitive)
                ?.map { it.asString }
                ?.toSet()
                .orEmpty(),
        )
    }

    /** `GET /academy/leaderboard` → `{"items": [...], "my_rank": n|null, "total_students": n}`. */
    fun readLeaderboard(body: JsonElement?): CommunityLeaderboard {
        val root = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val rows = arrayUnder(body, "items", "leaders", "results", "data")
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
        return CommunityLeaderboard(
            leaders = rows.mapIndexedNotNull { index, row ->
                val name = row.text("username", "name", "full_name", "fullName") ?: return@mapIndexedNotNull null
                CommunityLeader(
                    // The server numbers the rows and this trusts it, falling back to the position
                    // in the array. A board that renumbered itself client-side would disagree with
                    // the `my_rank` beside it the moment the server started paging.
                    rank = row.number("rank", "position")?.toInt() ?: (index + 1),
                    username = name,
                    xp = row.number("xp", "points", "score")?.toInt() ?: 0,
                    completed = row.number("completed", "lessons", "done")?.toInt() ?: 0,
                    isMe = row.flag("is_me", "isMe", "me") ?: false,
                )
            },
            myRank = root?.number("my_rank", "myRank", "rank")?.toInt(),
            totalStudents = root?.number("total_students", "totalStudents", "total")?.toInt() ?: 0,
        )
    }

    // ── rows ─────────────────────────────────────────────────────────────────────────────────

    private fun readPost(row: JsonObject): CommunityPost? {
        val id = row.number("id", "post_id", "postId", "pid")?.toLong()?.takeIf { it > 0L } ?: return null
        val content = row.text("content", "body", "text") ?: return null
        val categoryLabel = row.text("category", "cat", "topic")
        val status = row.text("status", "state")
        return CommunityPost(
            id = id,
            // «—» rather than an empty string, and it is the same em dash the route itself uses for
            // a student row that has gone. A blank line where a name belongs reads as a rendering
            // fault; a dash reads as "nobody knows", which is what it is.
            author = row.text("author", "username", "full_name", "fullName", "name") ?: "—",
            content = content,
            category = CommunityCategory.of(categoryLabel),
            categoryLabel = categoryLabel,
            likes = row.number("likes", "like_count", "likeCount")?.toInt() ?: 0,
            liked = row.flag("liked", "is_liked", "isLiked") ?: false,
            replyCount = row.number("replies_count", "repliesCount", "reply_count", "replyCount")?.toInt() ?: 0,
            reactions = readReactions(row.get("reactions")),
            // Zero is the route's own "no best reply" on the way back from `best-reply/0`, and it is
            // not a reply id. Without this the thread screen would look for reply zero and crown
            // nothing, which is the same picture as a bug.
            bestReplyId = row.number("best_reply_id", "bestReplyId")?.toLong()?.takeIf { it > 0L },
            createdAt = parseWireInstant(row.text("created_at", "createdAt", "created", "date")),
            pending = status != null && !status.equals("published", ignoreCase = true),
        )
    }

    private fun readReply(row: JsonObject, bestReplyId: Long?): CommunityReply? {
        val id = row.number("id", "reply_id", "replyId", "rid")?.toLong()?.takeIf { it > 0L } ?: return null
        val content = row.text("content", "body", "text") ?: return null
        return CommunityReply(
            id = id,
            author = row.text("author", "username", "full_name", "fullName", "name") ?: "—",
            content = content,
            parentId = row.number("parent_id", "parentId")?.toLong()?.takeIf { it > 0L },
            // The route computes this per reply and sends `is_best`; the post's own `best_reply_id`
            // is the second opinion, and either one is enough. Two sources because the search and
            // detail dictionaries are hand-built in different places in `academy.py` and have
            // already disagreed about which fields they carry.
            best = row.flag("is_best", "isBest", "best") ?: (bestReplyId != null && bestReplyId == id),
            createdAt = parseWireInstant(row.text("created_at", "createdAt", "created", "date")),
        )
    }

    /**
     * The reaction map, keeping only what this build can draw.
     *
     * A count under an emoji outside [CommunityReactions.ALLOWED] cannot be toggled — the route
     * refuses it — so a chip for one would be a control that answers every press with a 400. The
     * server writes the map from the same tuple, so this filter is normally a no-op; it is here for
     * the day the tuple grows a sixth entry and this app has not shipped yet.
     */
    private fun readReactions(element: JsonElement?): Map<String, Int> {
        val row = element?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return emptyMap()
        return CommunityReactions.ALLOWED.mapNotNull { emoji ->
            val count = row.get(emoji)?.takeIf(JsonElement::isJsonPrimitive)
                ?.let { runCatching { it.asInt }.getOrNull() }
                ?.takeIf { it > 0 }
            count?.let { emoji to it }
        }.toMap()
    }

    // ── primitives ───────────────────────────────────────────────────────────────────────────

    /**
     * The first array found: the body itself, or one of [names] inside it.
     *
     * Empty rather than null when nothing matches, because every caller here treats "no array" and
     * "an empty array" the same way — as a page with nothing on it — and the count that tells them
     * apart is taken separately.
     */
    private fun arrayUnder(body: JsonElement?, vararg names: String): List<JsonElement> = when {
        body == null || body.isJsonNull -> emptyList()
        body.isJsonArray -> body.asJsonArray.toList()
        body.isJsonObject -> names.asSequence()
            .mapNotNull { body.asJsonObject.get(it)?.takeIf(JsonElement::isJsonArray)?.asJsonArray }
            .firstOrNull()
            ?.toList()
            .orEmpty()
        else -> emptyList()
    }

    /** The first of [names] present as a non-blank string or number. */
    private fun JsonObject.text(vararg names: String): String? = names.asSequence()
        .mapNotNull { get(it) }
        .filter { it.isJsonPrimitive }
        .map { it.asString.trim() }
        .firstOrNull { it.isNotEmpty() }

    /**
     * The first of [names] present as a number, read tolerantly.
     *
     * A JSON string holding digits counts. Postgres `bigint` through some serializers arrives
     * quoted, and refusing `"41"` where `41` was expected is the silent-drop failure this file
     * exists to prevent.
     */
    private fun JsonObject.number(vararg names: String): Double? = names.asSequence()
        .mapNotNull { get(it) }
        .filter { it.isJsonPrimitive }
        .mapNotNull { element ->
            runCatching { element.asDouble }.getOrNull()
                ?: element.asString.trim().toDoubleOrNull()
        }
        .firstOrNull()

    /**
     * The first of [names] present as a boolean.
     *
     * `asBoolean` on a Gson primitive holding the string `"true"` answers true and on anything else
     * answers false, so a `1` from a backend that spells booleans as integers would be read as
     * false. The numeric branch is what stops that.
     */
    private fun JsonObject.flag(vararg names: String): Boolean? = names.asSequence()
        .mapNotNull { get(it) }
        .filter { it.isJsonPrimitive }
        .map { element ->
            val raw = element.asString.trim().lowercase()
            raw == "true" || raw == "1"
        }
        .firstOrNull()
}
