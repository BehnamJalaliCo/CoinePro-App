package com.coinepro.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.coinepro.app.AppLanguageStore
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.di.CryptoPlatform
import com.coinepro.app.di.ForexPlatform
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BrandConfig
import com.coinepro.core.common.proseDigits
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.feature.news.NewsImagePolicy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * The news widget (5.27.0) — TradingView's third widget, which this app did not have.
 *
 * One headline at a time, with its time, its source and its picture; arrows to page through the
 * latest ten; «more» into the app's news; refresh; and a gear for which feed it reads — crypto, or
 * forex and gold. Refreshed by [WidgetRefreshWorker] with the price widgets, on one schedule.
 */
class NewsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRefreshWorker.requestNow(context)
        NewsWidgetRenderer.renderAll(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PAGE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                NewsWidgetStore(context).turn(id, intent.getIntExtra(EXTRA_STEP, 0))
                NewsWidgetRenderer.renderAll(context, AppWidgetManager.getInstance(context), intArrayOf(id))
            }
            MarketsWidget.ACTION_REFRESH -> {
                WidgetRefreshWorker.requestNow(context)
                refreshAll(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.requestNow(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        NewsWidgetStore(context).forget(ids)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetPlacement.anyPlaced(context)) WidgetRefreshWorker.cancel(context)
    }

    companion object {
        const val ACTION_PAGE = "com.coinepro.app.widget.NEWS_PAGE"
        const val EXTRA_STEP = "step"

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NewsWidget::class.java))

        fun refreshAll(context: Context) {
            NewsWidgetRenderer.renderAll(context, AppWidgetManager.getInstance(context), ids(context))
        }

        /**
         * Fetches the stories of every placed news widget. Reached through an entry point rather than
         * the worker's constructor, so the shared worker names nothing the browser build lacks.
         */
        suspend fun refreshStories(context: Context) {
            if (ids(context).isEmpty()) return
            EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).newsWidgetEngine().refresh()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun newsWidgetEngine(): NewsWidgetEngine
    }
}

/** The two feeds the widget can read. */
enum class NewsFeed(val id: String) {
    CRYPTO("crypto"),
    FOREX("forex"),
    ;

    companion object {
        fun of(id: String?): NewsFeed = entries.firstOrNull { it.id == id } ?: CRYPTO
    }
}

/** One story as the widget keeps it between the worker that fetched it and the provider. */
data class WidgetStory(
    val title: String,
    val source: String,
    val publishedAtEpochMillis: Long,
    /** A file under the app's own storage, already cut to the widget's size, or null. */
    val imagePath: String?,
)

/** Which feed each widget reads, which story it is on, and the stories themselves. */
class NewsWidgetStore(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun feedFor(widgetId: Int): NewsFeed = NewsFeed.of(runCatching { preferences.getString(FEED + widgetId, null) }.getOrNull())

    fun setFeed(widgetId: Int, feed: NewsFeed) {
        runCatching { preferences.edit().putString(FEED + widgetId, feed.id).putInt(PAGE + widgetId, 0).apply() }
    }

    fun page(widgetId: Int): Int = runCatching { preferences.getInt(PAGE + widgetId, 0) }.getOrDefault(0)

    /** One step forward or back, wrapping at either end as a carousel does. */
    fun turn(widgetId: Int, step: Int) {
        val count = stories(feedFor(widgetId)).size
        if (count == 0) return
        val next = Math.floorMod(page(widgetId) + step, count)
        runCatching { preferences.edit().putInt(PAGE + widgetId, next).apply() }
    }

    fun forget(widgetIds: IntArray) {
        runCatching {
            val editor = preferences.edit()
            widgetIds.forEach { editor.remove(FEED + it).remove(PAGE + it) }
            editor.apply()
        }
    }

    fun stories(feed: NewsFeed): List<WidgetStory> = runCatching {
        val array = JSONArray(preferences.getString(STORIES + feed.id, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            WidgetStory(
                title = item.optString("t").takeIf(String::isNotBlank) ?: return@mapNotNull null,
                source = item.optString("s"),
                publishedAtEpochMillis = item.optLong("p"),
                imagePath = item.optString("i").takeIf(String::isNotBlank),
            )
        }
    }.getOrDefault(emptyList())

    fun capturedAt(feed: NewsFeed): Long = runCatching { preferences.getLong(CAPTURED + feed.id, 0L) }.getOrDefault(0L)

    fun write(feed: NewsFeed, stories: List<WidgetStory>, capturedAt: Long) {
        val array = JSONArray()
        stories.forEach { story ->
            array.put(
                JSONObject()
                    .put("t", story.title)
                    .put("s", story.source)
                    .put("p", story.publishedAtEpochMillis)
                    .put("i", story.imagePath.orEmpty()),
            )
        }
        runCatching { preferences.edit().putString(STORIES + feed.id, array.toString()).putLong(CAPTURED + feed.id, capturedAt).apply() }
    }

    /** The feeds some placed widget reads, so the worker fetches only those. */
    fun feedsInUse(widgetIds: IntArray): Set<NewsFeed> = widgetIds.map(::feedFor).toSet()

    private companion object {
        const val FILE = "news_widget"
        const val FEED = "feed_"
        const val PAGE = "page_"
        const val STORIES = "stories_"
        const val CAPTURED = "captured_"
    }
}

/** Fetches the stories a placed news widget reads, and their pictures. */
class NewsWidgetEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @CryptoPlatform private val crypto: MarketIntelGateway,
    @ForexPlatform private val forex: MarketIntelGateway,
) {
    /**
     * Its own client, not either platform's: a publisher's picture is on a third party's host, and
     * the platform clients send the reader's token with every request.
     */
    private val images by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun refresh() {
        val ids = NewsWidget.ids(context)
        if (ids.isEmpty()) return
        val store = NewsWidgetStore(context)
        store.feedsInUse(ids).forEach { feed ->
            val stories = try {
                (if (feed == NewsFeed.CRYPTO) crypto else forex).snapshot().news
                    .sortedByDescending { it.publishedAt }
                    .take(MAX_STORIES)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return@forEach
            }
            if (stories.isEmpty()) return@forEach
            val directory = File(context.cacheDir, "widget_news").apply { mkdirs() }
            val kept = stories.map { item ->
                WidgetStory(
                    title = item.title.trim(),
                    source = item.source.trim(),
                    publishedAtEpochMillis = item.publishedAt.toEpochMilli(),
                    imagePath = NewsImagePolicy.accept(item.imageUrl)?.let { url -> picture(url, directory) },
                )
            }
            // Pictures no story names any more are deleted, so the folder never grows past twenty.
            val used = store.stories(NewsFeed.entries.first { it != feed }).mapNotNull(WidgetStory::imagePath).toSet() +
                kept.mapNotNull(WidgetStory::imagePath).toSet()
            directory.listFiles()?.filter { it.absolutePath !in used }?.forEach { runCatching { it.delete() } }
            store.write(feed, kept, System.currentTimeMillis())
        }
    }

    /** The picture, cut down to the widget's size and stored once under a name of its address. */
    private suspend fun picture(url: String, directory: File): String? = withContext(Dispatchers.IO) {
        val file = File(directory, sha1(url) + ".png")
        if (file.exists()) return@withContext file.absolutePath
        runCatching {
            images.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes()?.takeIf { it.size <= MAX_IMAGE_BYTES } ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= IMAGE_PX && bounds.outHeight / (sample * 2) >= IMAGE_PX) sample *= 2
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@runCatching null
                val square = rounded(decoded, IMAGE_PX)
                file.outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
                file.absolutePath
            }
        }.getOrNull()
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_STORIES = 10
        const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
        const val IMAGE_PX = 192
    }
}

/** A centre-cropped square with rounded corners, as the widget's picture is drawn. */
internal fun rounded(source: Bitmap, size: Int): Bitmap {
    val side = minOf(source.width, source.height)
    val left = (source.width - side) / 2
    val top = (source.height - side) / 2
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val scale = size.toFloat() / side
    shader.setLocalMatrix(
        android.graphics.Matrix().apply {
            setTranslate(-left.toFloat(), -top.toFloat())
            postScale(scale, scale)
        },
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    val radius = size * 0.16f
    canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
    return output
}

object NewsWidgetRenderer {

    fun renderAll(base: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        // The reader's language, not the phone's (5.27.0): see `AppLanguageStore.localized`.
        val context = com.coinepro.app.AppLanguageStore.localized(base)
        val store = NewsWidgetStore(context)
        ids.forEach { id -> runCatching { manager.updateAppWidget(id, render(context, store, id)) } }
    }

    fun render(context: Context, store: NewsWidgetStore, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_news)
        val feed = store.feedFor(widgetId)
        val stories = store.stories(feed)
        views.setTextViewText(
            R.id.news_title,
            context.getString(if (feed == NewsFeed.CRYPTO) R.string.widget_news_crypto else R.string.widget_news_forex),
        )
        views.setTextViewText(R.id.news_freshness, WidgetFreshness.clock(context, store.capturedAt(feed), stale = false))
        views.setOnClickPendingIntent(R.id.news_refresh, broadcast(context, MarketsWidget.ACTION_REFRESH, widgetId, 0, REQUEST_REFRESH))
        views.setOnClickPendingIntent(R.id.news_settings, settings(context, widgetId))
        views.setOnClickPendingIntent(R.id.news_more, openNews(context))
        views.setOnClickPendingIntent(R.id.news_story, openNews(context))

        if (stories.isEmpty()) {
            views.setViewVisibility(R.id.news_story, View.GONE)
            views.setViewVisibility(R.id.news_empty, View.VISIBLE)
            views.setTextViewText(R.id.news_empty, context.getString(R.string.widget_news_empty))
            views.setViewVisibility(R.id.news_previous, View.INVISIBLE)
            views.setViewVisibility(R.id.news_next, View.INVISIBLE)
            views.setTextViewText(R.id.news_position, "")
            return views
        }
        val index = store.page(widgetId).coerceIn(0, stories.lastIndex)
        val story = stories[index]
        views.setViewVisibility(R.id.news_story, View.VISIBLE)
        views.setViewVisibility(R.id.news_empty, View.GONE)
        views.setTextViewText(R.id.news_headline, story.title)
        views.setTextViewText(R.id.news_meta, meta(story))
        val picture = story.imagePath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        views.setViewVisibility(R.id.news_image, if (picture != null) View.VISIBLE else View.GONE)
        picture?.let { views.setImageViewBitmap(R.id.news_image, it) }

        val english = AppLanguageStore.current(context) == AppLanguage.ENGLISH
        // A count in prose: «۳ از ۱۰» in Persian.
        views.setTextViewText(
            R.id.news_position,
            context.getString(R.string.widget_news_position, (index + 1).proseDigits(english), stories.size.proseDigits(english)),
        )
        val many = stories.size > 1
        views.setViewVisibility(R.id.news_previous, if (many) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.news_next, if (many) View.VISIBLE else View.INVISIBLE)
        views.setOnClickPendingIntent(R.id.news_previous, broadcast(context, NewsWidget.ACTION_PAGE, widgetId, -1, REQUEST_PREVIOUS))
        views.setOnClickPendingIntent(R.id.news_next, broadcast(context, NewsWidget.ACTION_PAGE, widgetId, 1, REQUEST_NEXT))
        return views
    }

    /** `12:43 · Reuters` — the time in Latin digits, as a market figure is written. */
    private fun meta(story: WidgetStory): String {
        val time = Instant.ofEpochMilli(story.publishedAtEpochMillis).atZone(ZoneId.systemDefault())
        val clock = "%02d:%02d".format(Locale.US, time.hour, time.minute)
        return listOf(clock, story.source).filter(String::isNotBlank).joinToString(" · ")
    }

    private fun openNews(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("${BrandConfig.SCHEME_PREFIX}news"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun settings(context: Context, widgetId: Int): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SETTINGS + widgetId,
        Intent(context, NewsWidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData(Uri.parse("${BrandConfig.SCHEME_PREFIX}news-widget/$widgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Distinct per widget and per direction, or Android hands every arrow the same intent. */
    private fun broadcast(context: Context, action: String, widgetId: Int, step: Int, base: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            base + widgetId,
            Intent(context, NewsWidget::class.java)
                .setAction(action)
                .setData(Uri.parse("${BrandConfig.SCHEME_PREFIX}news-widget/$widgetId/$action/$step"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(NewsWidget.EXTRA_STEP, step),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val REQUEST_OPEN = 7
    private const val REQUEST_REFRESH = 600_000
    private const val REQUEST_PREVIOUS = 700_000
    private const val REQUEST_NEXT = 800_000
    private const val REQUEST_SETTINGS = 900_000
}
