package com.coinepro.feature.news

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One story the reader kept.
 *
 * ### Why this holds the story and not just its id
 *
 * The obvious shape for "saved" is a set of ids, and it is wrong here. The feed is a **window**,
 * not an archive: the forex cache expires after 7200 seconds and TradeYar serves twenty to fifty
 * rows of `news_posts` with no paging, so a story the reader saved on Tuesday is simply not in
 * Thursday's response. A set of ids would therefore be a list that silently empties itself, and the
 * reader would conclude the app had lost their saves — which, functionally, it would have.
 *
 * So the save copies what it takes to draw the story again, to read it again, and to reach it: the
 * headline, the summary, who published it, when, the link and the picture's address. It does not
 * copy impact or sentiment, because those are a reading of a live market and a stored one goes
 * stale in a way a headline does not.
 *
 * **The summary is kept and the body is not**, and the asymmetry is deliberate rather than
 * unfinished. Without the summary a saved story reopened a week later was a headline over the words
 * «سرور برای این خبر خلاصه‌ای نفرستاده است» — the reader saved something to read and got an empty
 * page, which is the worst outcome this feature has. A body is a different size of object: at the
 * cap this file writes, two hundred saved bodies is more text than every other preference in the
 * app put together, read into memory on every emission of [SavedNewsStore.saved]. A saved story
 * therefore keeps its lede and points at the source for the rest, and when bodies start arriving
 * that is the line at which this store moves to a database rather than the line at which it grows.
 *
 * [savedAt] is when the reader pressed save, not when the story was published, and it is what the
 * saved list orders by: the reader's own sequence is the thing they will look for.
 */
data class SavedArticle(
    val id: String,
    val title: String,
    val summary: String?,
    val source: String?,
    val url: String?,
    val imageUrl: String?,
    val publishedAt: Instant,
    val savedAt: Instant,
)

/**
 * Where a reader's saved stories live.
 *
 * An interface rather than a class at the call site because the persistence belongs in
 * `core:datastore` with the app's other preferences, and this module does not own that module.
 * Moving [PreferencesSavedNewsStore] there and handing the shared `DataStore<Preferences>` in is a
 * file move and one constructor argument, and it is what lets the profile screen list what the
 * reader kept without depending on `feature:news`.
 *
 * Every method is a suspend or a flow. Saving is a disk write and the reader pressed a button, so
 * it happens off the frame; reading is a flow because two surfaces show it at once — the article's
 * own save button and the list's saved filter — and they must never disagree.
 */
interface SavedNewsStore {

    /** Everything the reader kept, newest save first. */
    fun saved(): Flow<List<SavedArticle>>

    /** Keeps one story. Saving one already kept refreshes its copy rather than duplicating it. */
    suspend fun save(article: SavedArticle)

    /** Forgets one story. An unknown id is a no-op, which is what a double tap produces. */
    suspend fun remove(id: String)
}

/**
 * The delimited-preferences store the rest of this app uses, applied to saved stories.
 *
 * ### The encoding
 *
 * The same scheme `ChartDrawingStore`, `SymbolChartStateStore` and `IntervalFavouritesStore` use,
 * and for the reason they give: the alternative is a serialisation library inside a preferences
 * file. Two separators are needed here rather than one, because a record has fields — ASCII's unit
 * separator between the fields of a story, its group separator between stories. Both are stripped
 * from every value on the way in, so no headline can contain one and no round trip can invent a
 * field boundary.
 *
 * **Decoding never throws and never gives up wholesale.** A record whose field count this build does
 * not recognise, or whose timestamps will not parse, is dropped on its own; every record around it
 * survives. That is the tolerant-decode rule the other stores state, and it is what makes a
 * downgrade, a truncated write or a half-flushed file cost the reader one save instead of all of
 * them.
 */
class PreferencesSavedNewsStore(private val dataStore: DataStore<Preferences>) : SavedNewsStore {

    override fun saved(): Flow<List<SavedArticle>> = dataStore.data
        .map { preferences -> decode(preferences[SAVED]) }
        // A preferences file this process cannot read is a reason to show no saved stories, never a
        // reason to take the news screen down with it. `DataStore.data` rethrows an IOException into
        // its collector, and the collector here is a composable inside the feed — so without this,
        // a disk that filled up while the reader was elsewhere would land as a blank screen where
        // the headlines used to be.
        .catch { emit(emptyList()) }
        .distinctUntilChanged()

    override suspend fun save(article: SavedArticle) {
        val clean = article.sanitised() ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[SAVED]).filterNot { it.id == clean.id }
            preferences[SAVED] = encode(listOf(clean) + current)
        }
    }

    override suspend fun remove(id: String) {
        val key = id.trim().takeIf(String::isNotEmpty) ?: return
        dataStore.edit { preferences ->
            val current = decode(preferences[SAVED])
            val remaining = current.filterNot { it.id == key }
            if (remaining.size == current.size) return@edit
            if (remaining.isEmpty()) {
                // Removed rather than written as an empty string. "Never saved anything" and
                // "unsaved the last one" are the same state — there is nothing to tell apart here
                // the way there is for a deliberately emptied interval bar — so the entry should
                // not survive as a blank row for a later version to have to parse.
                preferences.remove(SAVED)
            } else {
                preferences[SAVED] = encode(remaining)
            }
        }
    }

    companion object {

        internal val SAVED = stringPreferencesKey("news_saved_articles")

        /**
         * How many stories the reader may keep.
         *
         * Generous rather than a rule anybody meets, and it exists for the reason the other stores
         * give: a bound on a runaway writer, not a limit on a reader. The oldest save falls off the
         * end, because the reader's most recent interest is the one they came back for.
         */
        const val MAX_SAVED = 200

        /**
         * How long any one stored field may be.
         *
         * A headline is under two hundred characters in every feed this app reads, and a URL is
         * capped where `NewsImagePolicy` caps one. The summary is the field this number is really
         * for: it is the one whose length the servers do not bound, and two thousand characters is
         * several times the longest either feed has sent while still being small enough that two
         * hundred of them are an ordinary preferences file.
         */
        const val MAX_FIELD = 2048

        /** Between the fields of one story. ASCII unit separator. */
        private const val UNIT = "\u001F"

        /** Between stories. ASCII group separator. */
        private const val GROUP = "\u001D"

        /** Between the two timestamps, which share a field because neither can contain a colon. */
        private const val TIME_SEPARATOR = ":"

        /** Fields per record, in the order [encodeOne] writes them. */
        private const val FIELDS = 7

        /**
         * What a record written before the summary was kept looks like.
         *
         * Read as well as the current shape, and the summary of such a record comes back null — the
         * state the reading page already draws for a story the server sent no summary for. The
         * alternative is what the tolerant-decode rule would otherwise do to it: a record whose
         * field count this build does not recognise is dropped, so shipping the seventh field
         * without this line would silently empty every reader's saved list on update. The new field
         * is appended rather than inserted for the same reason — the first six indices mean the
         * same thing in both shapes, so there is one branch here and none below.
         */
        private const val FIELDS_V1 = 6

        internal fun encode(articles: List<SavedArticle>): String =
            articles.take(MAX_SAVED).joinToString(GROUP, transform = ::encodeOne)

        private fun encodeOne(article: SavedArticle): String = listOf(
            article.id,
            article.title,
            article.source.orEmpty(),
            article.url.orEmpty(),
            article.imageUrl.orEmpty(),
            article.publishedAt.epochSecond.toString() + TIME_SEPARATOR + article.savedAt.epochSecond,
            article.summary.orEmpty(),
        ).joinToString(UNIT)

        internal fun decode(stored: String?): List<SavedArticle> {
            if (stored.isNullOrBlank()) return emptyList()
            return stored.split(GROUP).mapNotNull(::decodeOne).take(MAX_SAVED)
        }

        private fun decodeOne(record: String): SavedArticle? {
            val parts = record.split(UNIT)
            if (parts.size != FIELDS && parts.size != FIELDS_V1) return null
            val storedId = parts[0].takeIf(String::isNotEmpty) ?: return null
            val storedTitle = parts[1].takeIf(String::isNotEmpty) ?: return null
            val times = parts[5].split(TIME_SEPARATOR)
            if (times.size != 2) return null
            val published = times[0].toLongOrNull() ?: return null
            val saved = times[1].toLongOrNull() ?: return null
            return SavedArticle(
                id = storedId,
                title = storedTitle,
                summary = parts.getOrNull(6)?.takeIf(String::isNotEmpty),
                source = parts[2].takeIf(String::isNotEmpty),
                url = parts[3].takeIf(String::isNotEmpty),
                imageUrl = parts[4].takeIf(String::isNotEmpty),
                publishedAt = Instant.ofEpochSecond(published),
                savedAt = Instant.ofEpochSecond(saved),
            )
        }

        /**
         * One record that is safe to write, or null if it could never be read back.
         *
         * Both separators are removed rather than escaped. Escaping is the better scheme in general
         * and the wrong one here: it needs an unescaper that agrees with it exactly, for ever, to
         * protect two control characters that cannot legitimately appear in a headline. Dropping
         * them costs a reader nothing they can see and cannot corrupt the record after it.
         */
        internal fun SavedArticle.sanitised(): SavedArticle? {
            val safeId = id.clean() ?: return null
            val safeTitle = title.clean() ?: return null
            return copy(
                id = safeId,
                title = safeTitle,
                summary = summary?.clean(),
                source = source?.clean(),
                url = url?.clean(),
                imageUrl = imageUrl?.clean(),
            )
        }

        private fun String.clean(): String? = replace(UNIT, "")
            .replace(GROUP, "")
            .trim()
            .take(MAX_FIELD)
            .takeIf(String::isNotEmpty)
    }
}

/**
 * The story a reader is looking at, as the thing that gets stored.
 *
 * An undated story cannot be saved, and there is exactly one of those: a public headline whose
 * publication time would not parse. The save is refused rather than dated, for the reason
 * [NewsStory] gives about the epoch — a saved list is the one place in this app where a wrong date
 * would persist and be sorted by. The caller draws no save control for such a story, so this is the
 * second check rather than the reader's first surprise.
 */
internal fun NewsStory.asSavedArticle(savedAt: Instant): SavedArticle? = SavedArticle(
    id = id,
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt ?: return null,
    savedAt = savedAt,
)

/**
 * A saved story rendered back as a story, so one card and one page draw both.
 *
 * The readings a save deliberately does not keep come back unknown, which is exactly what they are:
 * this app does not know today what a story from last week was rated, and the pills for "unknown"
 * are already the ones that stay grey and make no claim. Relevance comes back empty for the same
 * reason, and the page reads that as general market — honest, rather than a market tag preserved
 * from a reading that has since expired.
 *
 * The body comes back null because it was never written; the reading page draws a saved story the
 * way it draws any story whose server sent only a summary, and the line under the lede says so.
 */
internal fun SavedArticle.asStory(): NewsStory = NewsStory(
    id = id,
    title = title,
    summary = summary,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
)

/**
 * The file the saved list lives in.
 *
 * Its own preferences file rather than the app's shared one, and that is the one provisional thing
 * about this store. Two `DataStore` instances over the same file in one process throw, so a feature
 * module cannot quietly join the app's file — it has to be handed the instance, and nothing hands it
 * one today. A separate file is correct, safe and per-reader in the meantime; the move into
 * `core:datastore` changes nothing a reader sees, because the key and the encoding go with it.
 */
private val Context.savedNewsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "news_saved",
)

/**
 * The store for this reader, or null on a host where preferences are not available.
 *
 * Null is not theoretical: this screen is rendered by the app's screenshot test, and a screen that
 * throws on a host with no writable files directory is a screen nobody can look at again. Where it
 * is null the save controls are not drawn at all — the rule the chart entry on a news card already
 * follows, and the reason there is no button in this feature that does nothing.
 */
internal fun savedNewsStoreOrNull(context: Context): SavedNewsStore? = runCatching {
    PreferencesSavedNewsStore(context.applicationContext.savedNewsDataStore)
}.getOrNull()
