package com.coinepro.app.pulse

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.coinepro.app.AppLanguageStore
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.alerts.AlertDeepLink
import com.coinepro.app.di.CryptoPlatform
import com.coinepro.app.di.ForexPlatform
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.app.widget.BackgroundQuotes
import com.coinepro.app.widget.WidgetLogo
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BrandConfig
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.proseDigits
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.AssetArtwork
import com.coinepro.core.marketdata.MarketTickerGateway
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The market notifications (5.27.0), worked out on the phone every quarter of an hour.
 *
 * The owner chose the phone over a server push: it needs no Firebase project and no server work,
 * and it arrives in Iran, where Google's push often does not. The cost is up to fifteen minutes of
 * delay, which [MarketPulse]'s four-hour window is built around.
 *
 * Four kinds, each its own switch in the notification settings: a sharp move in bitcoin, gold or
 * the major pairs ([NotificationCategory.MARKET_MOVE]); a starred market's ([WATCHLIST_MOVE]); the
 * morning summary with the screener's movers ([MARKET_DIGEST]); and the reminder before a
 * high-importance release ([CALENDAR]).
 */
class MarketPulseEngine @Inject constructor(
    @ApplicationContext private val base: Context,
    private val quotes: BackgroundQuotes,
    private val watchlists: WatchlistStore,
    private val settingsStore: NotificationSettingsStore,
    private val preferences: UserPreferencesStore,
    @CryptoPlatform private val tickers: MarketTickerGateway,
    @ForexPlatform private val intel: MarketIntelGateway,
) {

    // `canPost` checks POST_NOTIFICATIONS before anything is posted; lint cannot follow the helper.
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun run(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        if (!canPost()) return
        val settings = settingsStore.settings.first()
        if (!settings.enabled || settings.mutedUntilEpochMillis?.let { nowMillis < it } == true) return
        val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minute = local.hour * 60 + local.minute
        val today = local.toLocalDate().toEpochDay()
        val quiet = MarketPulse.quiet(minute) || settings.quietHours.contains(minute)
        val store = MarketPulseStore(base)
        val context = AppLanguageStore.localized(base)
        val english = AppLanguageStore.current(base) == AppLanguage.ENGLISH
        val colours = runCatching { preferences.marketColors.first() }.getOrDefault(MarketColorScheme.GREEN_UP)

        val majors = if (settings.isOn(NotificationCategory.MARKET_MOVE)) MarketPulse.MAJORS else emptyList()
        val starred = if (settings.isOn(NotificationCategory.WATCHLIST_MOVE)) starred() else emptyList()
        val digestDue = settings.isOn(NotificationCategory.MARKET_DIGEST) &&
            MarketPulse.digestDue(minute, today, store.lastDigestDay())
        val watched = (majors + starred + if (digestDue) MarketPulse.DIGEST_MAJORS else emptyList()).distinct()
        val read = if (watched.isEmpty()) null else quotes.read(watched)

        // Moves: the samples are kept up to date in the quiet night too, so the morning does not
        // open with a stale window; only the notification waits.
        val moves = mutableListOf<Pair<PulseMove, NotificationCategory>>()
        (majors + starred).distinct().forEach { symbol ->
            val quote = read?.quotes?.get(symbol) ?: return@forEach
            val (kept, move) = MarketPulse.observe(symbol, store.samples(symbol), nowMillis, quote.price, MarketPulse.thresholdFor(symbol))
            store.setSamples(symbol, kept)
            if (move != null && nowMillis - store.lastFired(symbol) >= MarketPulse.COOLDOWN_MILLIS) {
                moves += move to if (symbol in majors) NotificationCategory.MARKET_MOVE else NotificationCategory.WATCHLIST_MOVE
            }
        }
        if (quiet) return

        var budget = MarketPulse.DAILY_CAP - store.sentToday(today)
        // Bitcoin first, then the largest move: the cap should spend itself on what matters most.
        moves.sortedWith(compareByDescending<Pair<PulseMove, NotificationCategory>> { it.first.symbol == MarketPulse.BITCOIN }
            .thenByDescending { abs(it.first.percent) })
            .take(MAX_MOVES_PER_RUN)
            .forEach { (move, category) ->
                if (budget <= 0) return@forEach
                postMove(context, move, category, english, colours)
                store.setFired(move.symbol, nowMillis)
                store.countOne(today)
                budget--
            }

        if (digestDue && budget > 0 && read != null) {
            if (postDigest(context, read, english, colours)) {
                store.setDigestDay(today)
                store.countOne(today)
                budget--
            }
        }

        if (settings.isOn(NotificationCategory.CALENDAR) && budget > 0) {
            calendar(context, store, nowMillis, english, today, budget)
        }
    }

    private suspend fun starred(): List<String> = runCatching {
        val lists = watchlists.lists().first()
        (lists.firstOrNull { it.id == Watchlist.DEFAULT_LIST_ID } ?: lists.firstOrNull())
            ?.symbols.orEmpty()
            .map { it.trim().uppercase() }
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_STARRED)
    }.getOrDefault(emptyList())

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(base, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(base).areNotificationsEnabled()
    }

    private fun colourOf(direction: Int, colours: MarketColorScheme): Int {
        val greenUp = colours == MarketColorScheme.GREEN_UP
        return when {
            direction > 0 -> if (greenUp) BUY else SELL
            direction < 0 -> if (greenUp) SELL else BUY
            else -> MUTED
        }
    }

    private fun signed(percent: Double): String {
        val sign = if (percent < 0) "−" else "+"
        return sign + MarketNumberFormatter.price(abs(percent), 2) + "%"
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun postMove(
        context: Context,
        move: PulseMove,
        category: NotificationCategory,
        english: Boolean,
        colours: MarketColorScheme,
    ) {
        val meta = SymbolClassifier.classify(move.symbol)
        val name = meta.description(english)
        val percent = signed(move.percent)
        val price = MarketNumberFormatter.priceAuto(move.toPrice)
        val hours = (move.windowMillis / 3_600_000.0).roundToInt()
        val span = if (hours >= 1) {
            context.getString(R.string.pulse_span_hours, hours.proseDigits(english))
        } else {
            context.getString(R.string.pulse_span_minutes, (move.windowMillis / 60_000).toInt().coerceAtLeast(15).proseDigits(english))
        }
        val title = context.getString(
            if (move.percent >= 0) R.string.pulse_move_up_title else R.string.pulse_move_down_title,
            name,
            percent,
        )
        val body = context.getString(R.string.pulse_move_line, price, span)
        val colour = colourOf(if (move.percent >= 0) 1 else -1, colours)
        val card = PulseCards.move(
            context = context,
            symbol = move.symbol,
            name = name,
            percent = percent,
            price = price,
            caption = span,
            closes = quotes.hourly(move.symbol),
            colour = colour,
        )
        val open = activity(context, AlertDeepLink.chart(move.symbol), move.symbol.hashCode())
        val builder = NotificationCompat.Builder(context, NotificationChannels.channelId(category))
            .setSmallIcon(R.drawable.ic_stat_coinepro)
            .setColor(colour)
            .setContentTitle(title)
            .setContentText(body)
            .setLargeIcon(WidgetLogo.bitmap(context, move.symbol, LARGE_ICON_DP))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.pulse_action_chart), open)
        if (card != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(card).bigLargeIcon(null as android.graphics.Bitmap?).setSummaryText(body))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        NotificationManagerCompat.from(context).notify(ID_MOVE + (move.symbol.hashCode() and 0xFFFF), builder.build())
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun postDigest(
        context: Context,
        read: BackgroundQuotes.Read,
        english: Boolean,
        colours: MarketColorScheme,
    ): Boolean {
        val tiles = MarketPulse.DIGEST_MAJORS.mapNotNull { symbol ->
            val quote = read.quotes[symbol] ?: return@mapNotNull null
            val change = quote.changePercent
            DigestTile(
                symbol = symbol,
                name = SymbolClassifier.classify(symbol).description(english),
                price = MarketNumberFormatter.priceAuto(quote.price),
                change = change?.let(::signed) ?: "—",
                direction = change?.let { if (it > 0) 1 else if (it < 0) -1 else 0 } ?: 0,
            )
        }
        if (tiles.isEmpty()) return false
        val movers = movers()
        val title = context.getString(R.string.pulse_digest_title)
        val line = tiles.joinToString(" · ") { "${it.name} ${it.change}" }
        val moversText = movers.joinToString("، ") { "${it.symbol.removeSuffix("USDT")} ${it.change}" }
        val moversLabel = context.getString(R.string.pulse_digest_movers)
        val card = PulseCards.digest(
            context = context,
            title = title,
            tiles = tiles,
            moversLabel = moversLabel,
            movers = movers,
            colourOf = { colourOf(it, colours) },
        )
        val body = if (moversText.isBlank()) line else "$line\n$moversLabel $moversText"
        val open = activity(context, "${BrandConfig.SCHEME_PREFIX}screener", REQUEST_DIGEST)
        val builder = NotificationCompat.Builder(context, NotificationChannels.channelId(NotificationCategory.MARKET_DIGEST))
            .setSmallIcon(R.drawable.ic_stat_coinepro)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(line)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.pulse_action_screener), open)
        if (card != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(card).setSummaryText(line))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        NotificationManagerCompat.from(context).notify(ID_DIGEST, builder.build())
        return true
    }

    /** The screener's three biggest risers among the hundred most traded coins with a mark. */
    private suspend fun movers(): List<DigestMover> = try {
        tickers.load(null).tickers.values
            .filter { it.symbol.endsWith("USDT") && SymbolArtwork.covers(it.symbol) && it.changePercent24h != null }
            .sortedByDescending { it.turnover24h ?: 0.0 }
            .take(LIQUID)
            .sortedByDescending { it.changePercent24h }
            .take(3)
            .filter { (it.changePercent24h ?: 0.0) > 0.0 }
            .map { DigestMover(it.symbol, signed(it.changePercent24h ?: 0.0), 1) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        emptyList()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun calendar(
        context: Context,
        store: MarketPulseStore,
        nowMillis: Long,
        english: Boolean,
        today: Long,
        budget: Int,
    ) {
        if (nowMillis - store.calendarFetchedAt() > CALENDAR_REFRESH_MILLIS) {
            val events = try {
                intel.snapshot().calendar
                    .filter { it.impact == MarketImpact.HIGH && it.scheduledAt.toEpochMilli() > nowMillis }
                    .sortedBy { it.scheduledAt }
                    .take(MAX_EVENTS)
                    .map { PulseEvent(it.id, it.title, it.currency, it.scheduledAt.toEpochMilli(), it.forecast, it.previous) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }
            if (events != null) store.setEvents(events, nowMillis)
        }
        val announced = store.announced().toMutableSet()
        var left = budget
        store.events()
            .filter { it.id !in announced && it.atMillis - nowMillis in 0..MarketPulse.CALENDAR_LEAD_MILLIS }
            .take(2)
            .forEach { event ->
                if (left <= 0) return@forEach
                val minutes = ((event.atMillis - nowMillis) / 60_000).toInt().coerceAtLeast(1)
                val title = context.getString(R.string.pulse_calendar_title, minutes.proseDigits(english), event.title)
                val details = listOfNotNull(
                    context.getString(R.string.pulse_calendar_high),
                    event.currency,
                    event.forecast?.let { context.getString(R.string.pulse_calendar_forecast, it) },
                    event.previous?.let { context.getString(R.string.pulse_calendar_previous, it) },
                ).joinToString(" · ")
                val flag = event.currency?.let(AssetArtwork::currency)?.let { res ->
                    runCatching { ContextCompat.getDrawable(context, res)?.toBitmap(128, 128) }.getOrNull()
                }
                val open = activity(context, "${BrandConfig.SCHEME_PREFIX}calendar", REQUEST_CALENDAR)
                val notification = NotificationCompat.Builder(context, NotificationChannels.channelId(NotificationCategory.CALENDAR))
                    .setSmallIcon(R.drawable.ic_stat_coinepro)
                    .setColor(GOLD)
                    .setContentTitle(title)
                    .setContentText(details)
                    .setLargeIcon(flag)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .setTimeoutAfter((event.atMillis - nowMillis) + EVENT_LINGER_MILLIS)
                    .build()
                NotificationManagerCompat.from(context).notify(ID_CALENDAR + (event.id.hashCode() and 0xFFFF), notification)
                announced += event.id
                store.countOne(today)
                left--
            }
        store.setAnnounced(announced)
    }

    private fun activity(context: Context, uri: String, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val MAX_STARRED = 12
        const val MAX_MOVES_PER_RUN = 2
        const val MAX_EVENTS = 20
        const val LIQUID = 100
        const val LARGE_ICON_DP = 48
        const val CALENDAR_REFRESH_MILLIS = 3L * 60 * 60 * 1000
        const val EVENT_LINGER_MILLIS = 30L * 60 * 1000
        const val ID_MOVE = 0x5_0000
        const val ID_DIGEST = 0x6_0001
        const val ID_CALENDAR = 0x7_0000
        const val REQUEST_DIGEST = 0x6_0002
        const val REQUEST_CALENDAR = 0x7_FFFF + 1
        const val BUY = 0xFF00B15C.toInt()
        const val SELL = 0xFFF6465D.toInt()
        const val MUTED = 0xFF848E9C.toInt()
        const val GOLD = 0xFFD8A848.toInt()
    }
}
