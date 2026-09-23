package com.coinepro.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartWeb
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.currentTimeMillis
import com.coinepro.core.designsystem.CoineProDarkPalette
import com.coinepro.core.designsystem.LocalCoineProPalette
import kotlinx.coroutines.delay

/**
 * The terminal: an instrument, a timeframe, the chart, and a line that says how fresh it is.
 *
 * This is W1b's milestone — `CoineProChart` in a browser on the relay's real candles — dressed only
 * as much as a public page needs to be usable. The workbench, the rails, the layout grid and the
 * script studio are W2 and W3 (docs/web/TERMINAL_BUILD_PROMPT.md) and are not faked here.
 */
@Composable
fun TerminalApp() {
    val palette = CoineProDarkPalette
    var fontsReady by remember { mutableStateOf(false) }
    var fontsFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val family = loadYekan()
        if (family != null) ChartWeb.fontFamily = family else fontsFailed = true
        fontsReady = true
        hideSplash()
    }

    // Without IRANYekanX there is no face in the page that can draw Persian, so a Persian page
    // would be a row of empty boxes. English is shown instead, which is honest and readable.
    val route = remember { Route.parse(pagePath()) }
    var persian by remember { mutableStateOf(stored(LANGUAGE_KEY) != "en") }
    val readsPersian = persian && !fontsFailed
    ChartWeb.persian = readsPersian
    setDocumentLanguage(if (readsPersian) "fa" else "en", rtl = readsPersian)

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = palette.stage,
            surface = palette.surface,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.accent,
        ),
        typography = Typography().withFamily(ChartWeb.fontFamily),
    ) {
        CompositionLocalProvider(
            LocalCoineProPalette provides palette,
            LocalLayoutDirection provides if (readsPersian) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            Box(Modifier.fillMaxSize().background(palette.stage)) {
                if (fontsReady) {
                    Terminal(
                        initial = route,
                        words = if (readsPersian) Words.Persian else Words.English,
                        canSwitchLanguage = !fontsFailed,
                        onSwitchLanguage = {
                            persian = !persian
                            store(LANGUAGE_KEY, if (persian) "fa" else "en")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Terminal(
    initial: Route,
    words: Words,
    canSwitchLanguage: Boolean,
    onSwitchLanguage: () -> Unit,
) {
    val palette = LocalCoineProPalette.current
    var instrument by remember { mutableStateOf(initial.instrument) }
    var timeframe by remember { mutableStateOf(initial.timeframe) }
    var bars by remember { mutableStateOf<List<Candle>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var lastFreshMillis by remember { mutableStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableStateOf(currentTimeMillis()) }

    LaunchedEffect(instrument, timeframe) {
        replacePagePath(initial.prefix + "/" + instrument.symbol + "/" + timeframe.path)
        setDocumentTitle(instrument.display + " · " + timeframe.label + " · Pro Chart")
    }

    // The data loop. One per instrument and timeframe; changing either cancels it and starts over.
    LaunchedEffect(instrument, timeframe, attempt) {
        bars = null
        failed = false
        // One quiet retry before the page says anything: the relay answers 503 for a moment while
        // an upstream reconnects, and a reader should not be shown a failure the next second fixes.
        val loaded = loadCandles(instrument, timeframe)
            ?: run { delay(RETRY_AFTER_MS); loadCandles(instrument, timeframe) }
        if (loaded == null) {
            failed = true
            return@LaunchedEffect
        }
        bars = loaded
        lastFreshMillis = currentTimeMillis()
        var ticks = 0
        while (true) {
            delay(if (instrument.venue == Venue.CRYPTO) CRYPTO_TICK_MS else FOREX_TICK_MS)
            nowMillis = currentTimeMillis()
            // A tab nobody is looking at asks for nothing (SERVER.md §6). It resumes on the first
            // tick after it is shown again, which is at most one interval.
            if (pageHidden()) continue
            ticks++
            when (instrument.venue) {
                // Crypto: the venue's snapshot every two seconds moves the forming bar, and every
                // five minutes the bars are fetched again so closed ones are the venue's own.
                Venue.CRYPTO -> {
                    if (ticks % CRYPTO_REFETCH_TICKS == 0) {
                        loadCandles(instrument, timeframe)?.let { bars = it; lastFreshMillis = nowMillis }
                    } else {
                        val price = loadPrice(instrument)
                        val current = bars
                        if (price != null && current != null) {
                            bars = applyPrice(current, price, nowMillis / 1000, timeframe)
                            lastFreshMillis = nowMillis
                        }
                    }
                }
                // Forex: the bars themselves, again. The live forex price and the forex candles
                // come from different upstreams (SERVER.md §4.10), so one is never spliced into
                // the other — the forming bar is the candle route's own.
                Venue.FOREX -> loadCandles(instrument, timeframe)?.let { bars = it; lastFreshMillis = nowMillis }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            instrument = instrument,
            timeframe = timeframe,
            words = words,
            canSwitchLanguage = canSwitchLanguage,
            onInstrument = { instrument = it },
            onTimeframe = { timeframe = it },
            onSwitchLanguage = onSwitchLanguage,
        )
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val current = bars
            when {
                current != null -> {
                    val series = remember(current) { CandleSeries(current) }
                    CoineProChart(
                        series = series,
                        modifier = Modifier.fillMaxSize(),
                        symbol = instrument.symbol,
                        interval = timeframe.path,
                        seriesLabel = instrument.display + " " + timeframe.label,
                    )
                }
                failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(words.failed, color = palette.textSecondary, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Chip(text = words.retry, selected = true, onClick = { attempt++ })
                }
                else -> Text(words.loading, color = palette.textSecondary, fontSize = 15.sp)
            }
        }
        StatusLine(
            words = words,
            venue = instrument.venue,
            stale = bars != null && nowMillis - lastFreshMillis > STALE_AFTER_MS,
        )
    }
}

@Composable
private fun TopBar(
    instrument: Instrument,
    timeframe: Timeframe,
    words: Words,
    canSwitchLanguage: Boolean,
    onInstrument: (Instrument) -> Unit,
    onTimeframe: (Timeframe) -> Unit,
    onSwitchLanguage: () -> Unit,
) {
    val palette = LocalCoineProPalette.current
    // One row on a wide window; on a phone-width one the instruments get a row of their own, so the
    // chosen instrument is never the thing scrolled out of sight.
    BoxWithConstraints(Modifier.fillMaxWidth().background(palette.surface)) {
        val narrow = maxWidth < NARROW_BAR
        Column {
            BarRow {
                Text("Pro Chart", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                // The language first and the long instrument list last, so on a narrow window
                // what scrolls out of view is instruments rather than the control that changes
                // every word.
                if (canSwitchLanguage) {
                    Chip(text = words.otherLanguage, selected = false, onClick = onSwitchLanguage)
                }
                Spacer(Modifier.width(8.dp))
                Timeframe.entries.forEach { item ->
                    Chip(text = item.label, selected = item == timeframe, onClick = { onTimeframe(item) })
                }
                if (!narrow) {
                    Spacer(Modifier.width(8.dp))
                    InstrumentChips(instrument, onInstrument)
                }
            }
            if (narrow) BarRow { InstrumentChips(instrument, onInstrument) }
        }
    }
}

@Composable
private fun BarRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun InstrumentChips(instrument: Instrument, onInstrument: (Instrument) -> Unit) {
    Instruments.forEach { item ->
        Chip(text = item.display, selected = item == instrument, onClick = { onInstrument(item) })
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalCoineProPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .background(if (selected) palette.accent.copy(alpha = 0.18f) else Color.Transparent, shape)
            .border(1.dp, if (selected) palette.accent else palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StatusLine(words: Words, venue: Venue, stale: Boolean) {
    val palette = LocalCoineProPalette.current
    Text(
        text = when {
            stale -> words.stale
            venue == Venue.CRYPTO -> words.cryptoCadence
            else -> words.forexCadence
        },
        color = if (stale) palette.textPrimary else palette.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Where the address bar says to start, and the prefix the page is mounted under. */
data class Route(val prefix: String, val instrument: Instrument, val timeframe: Timeframe) {
    companion object {
        /**
         * `/terminal/BTCUSDT/4h` → BTCUSDT on four hours. Anything the path does not name falls
         * back to the default, so a bare `/terminal/` and a mistyped symbol both open a chart.
         */
        fun parse(path: String): Route {
            val segments = path.split('/').filter { it.isNotEmpty() }
            val mount = segments.indexOfFirst { it.equals("terminal", ignoreCase = true) }
            val prefix = if (mount >= 0) "/" + segments.take(mount + 1).joinToString("/") else ""
            val rest = if (mount >= 0) segments.drop(mount + 1) else segments
            val instrument = rest.getOrNull(0)?.let { name ->
                Instruments.firstOrNull { it.symbol.equals(name, ignoreCase = true) }
            } ?: Instruments.first()
            val timeframe = Timeframe.fromPath(rest.getOrNull(1)) ?: Timeframe.H1
            return Route(prefix, instrument, timeframe)
        }
    }
}

/** The label a timeframe wears. Latin in both languages: it is a market figure, as on the phone. */
val Timeframe.label: String
    get() = when (this) {
        Timeframe.M15 -> "15m"
        Timeframe.H1 -> "1H"
        Timeframe.H4 -> "4H"
        Timeframe.D1 -> "1D"
    }

/**
 * Every word the page prints, in both languages.
 *
 * A table here rather than `values/` and `values-fa/`: a browser bundle has no Android resources.
 * The same house rules hold — «به‌روز» with its half-space, Persian digits for a prose count.
 */
class Words(
    val loading: String,
    val failed: String,
    val retry: String,
    val cryptoCadence: String,
    val forexCadence: String,
    val stale: String,
    val otherLanguage: String,
) {
    companion object {
        val Persian = Words(
            loading = "در حال بارگذاری نمودار…",
            failed = "نمودار بارگذاری نشد.",
            retry = "تلاش دوباره",
            cryptoCadence = "قیمت هر ۲ ثانیه به‌روز می‌شود.",
            forexCadence = "نمودار هر ۳۰ ثانیه به‌روز می‌شود.",
            stale = "اتصال برقرار نیست؛ آخرین داده‌ی دریافت‌شده نمایش داده می‌شود.",
            otherLanguage = "English",
        )
        val English = Words(
            loading = "Loading the chart…",
            failed = "The chart could not be loaded.",
            retry = "Try again",
            cryptoCadence = "The price updates every 2 seconds.",
            forexCadence = "The chart refreshes every 30 seconds.",
            stale = "No connection — showing the last data received.",
            otherLanguage = "فارسی",
        )
    }
}

/** IRANYekanX in its four weights, from the bundle, or null when any of them fails to arrive. */
private suspend fun loadYekan(): FontFamily? {
    val faces = listOf(
        "regular" to FontWeight.Normal,
        "medium" to FontWeight.Medium,
        "semibold" to FontWeight.SemiBold,
        "bold" to FontWeight.Bold,
    )
    val fonts = faces.map { (name, weight) ->
        val bytes = fetchBytes("fonts/iranyekanx_$name.ttf") ?: return null
        Font(identity = "IRANYekanX-$name", data = bytes, weight = weight)
    }
    return FontFamily(fonts)
}

private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private const val LANGUAGE_KEY = "pc_language"
private const val CRYPTO_TICK_MS = 2_000L
private const val FOREX_TICK_MS = 30_000L
private const val RETRY_AFTER_MS = 3_000L

/** Every five minutes at a two-second tick. */
private const val CRYPTO_REFETCH_TICKS = 150

/** Below this width the instruments move to a row of their own. */
private val NARROW_BAR = 900.dp

/** Past this with nothing new, the line says so rather than letting a frozen chart pass as live. */
private const val STALE_AFTER_MS = 90_000L
