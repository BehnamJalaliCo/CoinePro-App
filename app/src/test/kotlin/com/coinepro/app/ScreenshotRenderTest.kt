package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.auth.AuthFailure
import com.coinepro.core.auth.AuthFailureReason
import com.coinepro.core.auth.AuthMethods
import com.coinepro.core.auth.EmailAuthNotice
import com.coinepro.core.auth.EmailAuthStep
import com.coinepro.core.auth.EmailAuthUiState
import com.coinepro.core.auth.LoginConfigState
import com.coinepro.core.auth.SessionState
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.chart.drawDrawing
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingList
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.Structure
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.help.HelpBody
import com.coinepro.core.help.HelpCatalog
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.Indicators
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.persianDigits
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.feature.search.SearchScreen
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.admin.AdminScreen
import com.coinepro.feature.ai.AiStudioScreen
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.auth.EmailAuthScreen
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.copytrade.CopyTradeScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.academy.AcademyScreen
import com.coinepro.feature.academy.LessonScreen
import com.coinepro.feature.portfolio.PortfolioScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.feature.home.HomeSubscription
import com.coinepro.feature.kyc.KycScreen
import com.coinepro.core.account.AccountController
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.ErrorKind

/**
 * Renders the production screen composables off-device and writes the pixels to
 * `app/build/screenshots`. Robolectric's native graphics pipeline draws the same Compose tree the
 * app draws, so these are real renders of real screens rather than mockups; only the gateway data
 * is substituted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
@OptIn(ExperimentalMaterial3Api::class)
class ScreenshotRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Controllers take their own scope, so the gateway work can run eagerly on Unconfined without
     * replacing Dispatchers.Main, which the Compose test clock owns.
     */
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        OUTPUT_DIR.mkdirs()
    }

    @Test
    fun home() = capture("01-home") {
        HomeScreen(state = ScreenshotFixtures.marketState(), onRetry = {})
    }

    /**
     * Home in the shipping default language, with every section populated. This is the reference
     * render for the "آرام" direction: the balance as the hero, one gold object on the page, and
     * cards separated by gap rather than by rules.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun homePersian() = capture("17-home-fa") { PopulatedHome() }

    /** The same screen in the light theme, which must hold the same structure rather than invert. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun homePersianLight() = capture("17-home-fa-light", darkTheme = false) { PopulatedHome() }

    /** The full page, so the sections below the fold are visible for review. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun homePersianFullPage() = capture("18-home-fa-full") { PopulatedHome() }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun homePersianFullPageLight() =
        capture("18-home-fa-full-light", darkTheme = false) { PopulatedHome() }

    /**
     * The screen with nothing signed in and nothing generated. The empty state has to look
     * deliberate rather than broken, because it is what every reader sees first.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun homePersianResting() = capture("19-home-fa-resting") {
        HomeScreen(state = ScreenshotFixtures.marketState(), onRetry = {})
    }

    @Composable
    private fun PopulatedHome() {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            openSignals = ScreenshotFixtures.homeSignals,
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
        )
    }

    /**
     * The subscription card, which only a subscriber ever sees.
     *
     * Rendered with a plan a week from ending, because that is the state the card exists for: the
     * date alone is a fact, and the countdown turning amber is the part that changes what someone
     * does about it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun homeWithSubscription() = capture("24-home-subscription-fa") {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            subscription = HomeSubscription(
                planLabel = "اشتراک ماهانه",
                expiresLabel = BidiText.isolateLtr("2026-09-01"),
                daysRemaining = 6,
                endingSoon = true,
                isVip = true,
            ),
            openSignals = ScreenshotFixtures.homeSignals,
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
        )
    }

    /** Level-one verification, empty and waiting — the state every reader meets first. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun verificationNotStarted() = capture("25-kyc-fa") {
        KycScreen(controller = AccountController(FakeAccountGateway(), scope))
    }

    /**
     * The same screen after the server refused, in the server's own words.
     *
     * The refusal is the whole point of the screen: the app cannot know why a particular national
     * id was rejected, so what the server said is the only useful thing on it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun verificationRefused() = capture("26-kyc-refused-fa") {
        val controller = AccountController(
            FakeAccountGateway(
                submitResult = AppResult.Failure(
                    kind = ErrorKind.VALIDATION,
                    message = "کد ملی واردشده با تاریخ تولد هم‌خوانی ندارد.",
                ),
            ),
            scope,
        )
        controller.submitKycLevel1("بهنام جلالی", "0012345678", "1370/05/12", "09121234567")
        KycScreen(controller = controller)
    }

    /**
     * CoinePro-FX's copy-trading screen, live: a linked account, the switch on, one mirrored
     * position, and the reason the last signal did not open.
     *
     * Rendered because that last card is the one nobody could see before. It is server text in
     * Persian carrying a broker return code, sitting inside a right-to-left column beside Latin
     * figures — exactly the mix that goes wrong silently, and only a render shows it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun copyTrading() {
        val controller = CopyTradeController(FakeCopyTradeGateway(), scope)
        controller.refresh()
        capture("27-copy-trading-fa") { CopyTradeScreen(controller = controller) }
    }

    /** The same screen with nothing linked yet — the form, and the warning above it. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun copyTradingUnlinked() {
        val controller = CopyTradeController(
            FakeCopyTradeGateway(ScreenshotFixtures.copyTradeUnlinked),
            scope,
        )
        controller.refresh()
        capture("28-copy-trading-unlinked-fa") { CopyTradeScreen(controller = controller) }
    }

    /**
     * A wall of instrument logos on the real stage colour, at the real sizes.
     *
     * Worth a render of its own because the archive grew from eight symbols to seven hundred in one
     * change, and the two things that can go wrong with that are both invisible in a file listing:
     * a mark that is near-black on a near-black ground, and a mark whose artwork is an illustration
     * rather than an icon and turns to mush at 24dp. Both are obvious here in one glance.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun assetLogoWall() = capture("29-asset-logos-fa") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            // Deliberately mixed: the majors, the four that ship as raster, and the marks that are
            // dark discs — XRP, XLM, ATOM — which are the whole reason the ring exists.
            val rows = listOf(
                listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT"),
                listOf("DOGEUSDT", "TRXUSDT", "LTCUSDT", "DOTUSDT", "XLMUSDT", "ATOMUSDT"),
                listOf("TONUSDT", "SUIUSDT", "ARBUSDT", "PEPEUSDT", "SEIUSDT", "WIFUSDT"),
                listOf("TIAUSDT", "AVAXUSDT", "LINKUSDT", "UNIUSDT", "MATICUSDT", "NEARUSDT"),
                // The last two have no artwork anywhere and must fall back to the lettered token.
                listOf("XAUUSD", "XAGUSD", "APEUSDT", "OPUSDT", "INJUSDT", "AAVEUSDT"),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                    row.forEach { CoineProAssetLogo(symbol = it, size = 42.dp) }
                }
            }
            // Forex and the metals: two discs each, base in front, and the notch between them.
            listOf(
                listOf("XAUUSD", "XAGUSD", "EURUSD", "GBPUSD", "USDJPY", "USDCHF"),
                listOf("AUDUSD", "NZDUSD", "USDCAD", "EURGBP", "USDTRY", "USDZAR"),
                // The exotics the broker also quotes, whose flags only arrived with the wider
                // TradingView fetch. Before it these were six lettered tokens.
                listOf("USDINR", "USDBRL", "USDKRW", "USDTHB", "USDILS", "USDRUB"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                    row.forEach { CoineProAssetLogo(symbol = it, size = 42.dp) }
                }
            }
            // Equity, index and ETF marks. These arrive on the same wire as the coins — LBank
            // lists all five as perpetuals — and no crypto icon pack draws any of them.
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                listOf("QQQUSDT", "NAS100", "US500", "TSLAUSDT", "SAMSUNGUSDT", "COINUSDT")
                    .forEach { CoineProAssetLogo(symbol = it, size = 42.dp) }
            }
            Row(
                modifier = Modifier.padding(top = CoineProSpacing.One),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The three sizes the app draws, so a mark that only works large is caught.
                listOf(24.dp, 32.dp, 42.dp).forEach { size ->
                    CoineProAssetLogo(symbol = "XRPUSDT", size = size)
                    CoineProAssetLogo(symbol = "XLMUSDT", size = size)
                    CoineProAssetLogo(symbol = "SOLUSDT", size = size)
                    CoineProAssetLogo(symbol = "EURUSD", size = size)
                }
            }
        }
    }

    /**
     * Every TradingView icon the app now carries, tinted as the interface would tint them.
     *
     * A hundred and twenty vectors arrived in one change, converted from a form that was never
     * meant to leave a web bundle. Three separate converter bugs so far have produced artwork that
     * was wrong rather than missing — a backwards arc, a dropped clip, a gradient in the wrong
     * coordinate space — and every one of them was invisible until something drew it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun tradingViewIcons() = capture("30-tv-icons-fa") {
        val ids = ScreenshotFixtures.tradingViewIconIds(LocalContext.current)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            ids.chunked(12).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    row.forEach { id ->
                        Icon(
                            painter = painterResource(id),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = CoineProColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }

    /**
     * The app's own icons — the ones that name its sections — beside the Phosphor glyphs they would
     * replace, at nav size, outline over fill.
     *
     * Rendered side by side because that is the only way to judge the swap. The Phosphor set is a
     * general icon family and reads as one; the brand set was drawn for these eleven meanings
     * specifically, and whether that is an improvement is a question about how they sit together,
     * not about either one alone.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun brandIcons() = capture("31-brand-icons-fa") {
        val pairs = ScreenshotFixtures.navIconComparison() + ScreenshotFixtures.brandIconComparison()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoineProColors.Stage)
                .padding(CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            pairs.forEach { (brand, phosphor) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(brand.first, brand.second, phosphor.first, phosphor.second)
                        .forEach { id ->
                            if (id == 0) {
                                Spacer(Modifier.size(26.dp))
                            } else {
                                Icon(
                                    painter = painterResource(id),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = CoineProColors.TextPrimary,
                                )
                            }
                        }
                }
            }
        }
    }

    @Test
    fun signals() {
        val controller = SignalController(FakeSignalGateway(), scope)
        controller.refresh()
        capture("02-signals") { SignalsScreen(controller = controller, onOpenSignal = {}) }
    }

    /**
     * The pilot screen in the shipping default language. Robolectric picks resources from the
     * `qualifiers` on the test, so this renders the same widget tree a Persian device would draw:
     * translated copy, mirrored layout, and Latin price columns held left-to-right.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun signalsPersian() {
        val controller = SignalController(FakeSignalGateway(), scope)
        controller.refresh()
        capture("12-signals-fa") { SignalsScreen(controller = controller, onOpenSignal = {}) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-rUS-w411dp-h914dp-xxhdpi")
    fun signalsEnglish() {
        val controller = SignalController(FakeSignalGateway(), scope)
        controller.refresh()
        capture("13-signals-en") { SignalsScreen(controller = controller, onOpenSignal = {}) }
    }

    @Test
    fun signalDetail() {
        val signals = SignalController(FakeSignalGateway(), scope)
        val intel = MarketIntelController(FakeMarketIntelGateway(), scope)
        intel.refresh()
        signals.loadDetail(4821L)
        capture("03-signal-detail") {
            SignalDetailScreen(
                controller = signals,
                marketIntelController = intel,
                signalId = 4821L,
                onExecute = {},
            )
        }
    }

    /**
     * The same signal with its bars behind it, on a viewport tall enough to reach them.
     *
     * The chart sits above the setup card, which on a phone-height capture is below the fold — so
     * this one is deliberately tall, for design review rather than for a device.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1600dp-xxhdpi")
    fun signalDetailWithChart() {
        val signals = SignalController(FakeSignalGateway(), scope)
        val intel = MarketIntelController(FakeMarketIntelGateway(), scope)
        intel.refresh()
        signals.loadDetail(4821L)
        capture("51-signal-detail-chart-fa") {
            SignalDetailScreen(
                controller = signals,
                marketIntelController = intel,
                signalId = 4821L,
                onExecute = {},
                chartController = ScreenshotFixtures.signalChartController(scope),
            )
        }
    }

    /** The AI section with a completed result, so the chart and evidence panels both render. */
    @Test
    fun aiStudio() {
        val controller = AiSignalController(FakeAiSignalGateway(ScreenshotFixtures.aiJob), scope)
        controller.refreshQuota()
        controller.submit(ScreenshotFixtures.aiRequest)
        capture("14-ai-studio") {
            AiStudioScreen(
                controller = controller,
                onOpenSignal = {},
                onOpenChartAnalysis = {},
            )
        }
    }

    /**
     * A full-page render on an unusually tall viewport. A LazyColumn only composes what fits, so a
     * phone-height capture stops at the fold and never shows the result. This is for design review
     * only — no device is this tall.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h2600dp-xxhdpi")
    fun aiStudioFullPage() {
        val controller = AiSignalController(FakeAiSignalGateway(ScreenshotFixtures.aiJob), scope)
        controller.refreshQuota()
        controller.submit(ScreenshotFixtures.aiRequest)
        capture("16-ai-studio-full") {
            AiStudioScreen(
                controller = controller,
                onOpenSignal = {},
                onOpenChartAnalysis = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun aiStudioPersian() {
        val controller = AiSignalController(FakeAiSignalGateway(ScreenshotFixtures.aiJob), scope)
        controller.refreshQuota()
        controller.submit(ScreenshotFixtures.aiRequest)
        capture("15-ai-studio-fa") {
            AiStudioScreen(
                controller = controller,
                onOpenSignal = {},
                onOpenChartAnalysis = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h2200dp-xxhdpi")
    fun portfolio() {
        capture("52-portfolio-fa") {
            PortfolioScreen(
                controller = ScreenshotFixtures.portfolioController(scope),
                onOpenConnections = {},
                zone = java.time.ZoneOffset.UTC,
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun academy() {
        capture("53-academy-fa") {
            AcademyScreen(
                controller = ScreenshotFixtures.academyController(scope),
                onOpenLesson = {},
                onOpenProfile = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun academyLesson() {
        capture("54-academy-lesson-fa") {
            LessonScreen(
                controller = ScreenshotFixtures.academyController(scope),
                slug = "leverage",
                onClose = {},
                onOpenProfile = {},
            )
        }
    }

    /**
     * The four page accents side by side, which is the only way to review the rule.
     *
     * The rule is that a domain colour is never decorative: gold executes, blue analyses, green is
     * social, premium gold is subscription. Reviewing it one screen at a time cannot catch the
     * failure it is guarding against — two domains that ended up the same colour — because on any
     * single screen both look fine.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xxhdpi")
    fun designKit() = capture("55-design-kit-fa") { DesignKit() }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xxhdpi")
    fun designKitLight() {
        capture("56-design-kit-fa-light", darkTheme = false) { DesignKit() }
    }

    @Test
    fun tools() = capture("05-tools") {
        ToolsScreen(onOpenConnections = {}, onOpenNews = {}, onOpenCalendar = {})
    }

    @Test
    fun activity() {
        val notifications = NotificationController(FakeNotificationGateway(), scope)
        val executions = ExecutionController(FakeExecutionGateway(), scope)
        val signals = SignalController(FakeSignalGateway(), scope)
        notifications.refresh()
        executions.refreshExecutions()
        signals.refreshHistory()
        capture("06-activity") {
            ActivityScreen(
                controller = notifications,
                executionController = executions,
                signalController = signals,
                onOpenSignal = {},
            )
        }
    }

    @Test
    fun connections() {
        val controller = ExecutionController(FakeExecutionGateway(), scope)
        controller.refreshConnections()
        capture("07-connections") { ConnectionsScreen(controller = controller) }
    }

    @Test
    fun news() {
        val controller = MarketIntelController(FakeMarketIntelGateway(), scope)
        controller.refresh()
        capture("08-news") { NewsScreen(controller = controller, onOpenCalendar = {}) }
    }

    @Test
    fun calendar() {
        val controller = MarketIntelController(FakeMarketIntelGateway(), scope)
        controller.refresh()
        capture("09-calendar") { EconomicCalendarScreen(controller = controller, onOpenNews = {}) }
    }

    @Test
    fun launchReadiness() = capture("10-launch-readiness") {
        LaunchReadinessScreen(
            notificationPermissionState = NotificationPermissionUiState.AVAILABLE_TO_REQUEST,
            onRequestNotificationPermission = {},
            onOpenNotificationSettings = {},
            onSendFeedback = {},
        )
    }

    /** The panel five taps behind the version number, with a dead route among the live ones. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun adminPanel() = capture("23-admin-fa") {
        AdminScreen(state = ScreenshotFixtures.adminState, hub = ScreenshotFixtures.controlHub)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun emailSignIn() = capture("20-auth-email-sign-in") {
        EmailAuthScreen(
            state = EmailAuthUiState(
                methods = AuthMethods(emailPassword = true, google = true),
                methodsKnown = true,
            ),
            onSignIn = { _, _ -> },
            onRegister = { _, _, _ -> },
            onVerify = {},
            onStartOver = {},
            onRequestReset = {},
            onResetPassword = { _, _ -> },
            onGoTo = {},
            onRetryMethods = {},
            onGoogleSignIn = {},
            onTelegramPayload = {},
        )
    }

    /**
     * The state the whole flow turns on: a code was sent, the server's cooldown is running, and a
     * previous attempt was refused in the server's own words. If any of the three is drawn wrongly
     * the reader is either told something untrue or left tapping a button that cannot work.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun emailVerify() = capture("21-auth-email-verify") {
        EmailAuthScreen(
            state = EmailAuthUiState(
                methods = AuthMethods(emailPassword = true),
                methodsKnown = true,
                step = EmailAuthStep.VERIFY_CODE,
                pendingEmail = "reader@example.com",
                notice = EmailAuthNotice.CODE_SENT,
                resendAvailableIn = 42,
            ),
            onSignIn = { _, _ -> },
            onRegister = { _, _, _ -> },
            onVerify = {},
            onStartOver = {},
            onRequestReset = {},
            onResetPassword = { _, _ -> },
            onGoTo = {},
            onRetryMethods = {},
            onGoogleSignIn = {},
            onTelegramPayload = {},
        )
    }

    /** Google disabled server-side: the button is absent, not present and doomed. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun emailSignInWithoutGoogle() = capture("22-auth-email-no-google-light", darkTheme = false) {
        EmailAuthScreen(
            state = EmailAuthUiState(
                methods = AuthMethods(emailPassword = true, google = false),
                methodsKnown = true,
                failure = AuthFailure(
                    reason = AuthFailureReason.RATE_LIMITED,
                    message = "بیش از حد تلاش شد. کمی بعد دوباره امتحان کنید.",
                    retryAfterSeconds = 90,
                ),
                retryAvailableIn = 90,
            ),
            onSignIn = { _, _ -> },
            onRegister = { _, _, _ -> },
            onVerify = {},
            onStartOver = {},
            onRequestReset = {},
            onResetPassword = { _, _ -> },
            onGoTo = {},
            onRetryMethods = {},
            onGoogleSignIn = {},
            onTelegramPayload = {},
        )
    }

    /**
     * The screen a reader gets when a session exists but could not be revalidated.
     *
     * Rendered in `RevalidationRequired` rather than `SignedOut`, because signing out no longer
     * reaches this screen at all — `EmailAuthScreen` does. A capture in `SignedOut` was showing a
     * Telegram sign-in button nobody can get to, which is a screenshot claiming the app looks like
     * something it does not.
     */
    @Test
    fun auth() = capture("11-auth") {
        AuthScreen(
            state = SessionState.RevalidationRequired("نشست شما دیگر معتبر نیست."),
            loginConfigState = LoginConfigState.Ready("CoineProBot"),
            onTelegramPayload = {},
            onRetryLoginConfig = {},
            onRetry = {},
            onLogout = {},
        )
    }

    /**
     * The whole screen a reader actually sees: Home inside the app's own chrome, with the real
     * bottom bar. Home carries no top bar by design, so this is the full page.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun appShellPersian() = captureRaw("00-app-shell-fa") { ShellAroundHome() }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun appShellPersianLight() =
        captureRaw("00-app-shell-fa-light", darkTheme = false) { ShellAroundHome() }

    @Composable
    private fun ShellAroundHome() {
        Scaffold(
            containerColor = com.coinepro.core.designsystem.CoineProColors.Stage,
            bottomBar = {
                CoineProBottomBar(
                    currentRoute = AppDestination.HOME.route,
                    onSelect = {},
                )
            },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) { PopulatedHome() }
        }
    }

    /**
     * Market search over a catalogue the size the real one now is.
     *
     * Rendered twice — browsing, and mid-query — because the two are different screens to a reader
     * and only the second shows what the ranking and the match highlighting actually do.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun marketSearchBrowse() {
        val controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
        controller.start()
        capture("32-search-browse-fa") { SearchScreen(controller = controller) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun marketSearchQuery() {
        val controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
        controller.start()
        controller.setQuery("eur")
        capture("33-search-query-fa") { SearchScreen(controller = controller) }
    }

    /**
     * The chart, in the three states that actually have to be looked at.
     *
     * A renderer is the one part of this app that unit tests cannot judge. The arithmetic is
     * covered thirty ways over in core:chart; what is left is whether a wick is visible, whether a
     * doji survives, whether the axis labels collide, and whether the risk band reads as risk. All
     * four of those are questions about pixels.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartCandles() = capture("34-chart-candles-fa") {
        val series = ScreenshotFixtures.chartSeries()
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(320.dp),
                decoration = ChartDecoration(
                    overlays = listOf(
                        ChartLine(Indicators.ema(series.close, 20), 0xFFD8A848, label = "EMA 20"),
                        ChartLine(Indicators.ema(series.close, 50), 0xFF6E8BE0, label = "EMA 50"),
                    ),
                ),
            )
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                type = ChartType.HEIKIN_ASHI,
                decoration = ChartDecoration(showVolume = false),
            )
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                type = ChartType.AREA,
                decoration = ChartDecoration(showVolume = false, showAxes = false),
                interactive = false,
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartSignalOverlay() = capture("35-chart-signal-fa") {
        val series = ScreenshotFixtures.chartSeries()
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(360.dp),
                decoration = ChartDecoration(
                    signal = ScreenshotFixtures.chartSignal(series),
                    overlays = listOf(
                        ChartLine(
                            Indicators.supertrend(series.high, series.low, series.close).line,
                            0xFF00B15C,
                            label = "SuperTrend",
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartTypes() = capture("36-chart-types-fa") {
        val series = ScreenshotFixtures.chartSeries()
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            listOf(
                ChartType.HOLLOW,
                ChartType.BARS,
                ChartType.RENKO,
                ChartType.LINE_BREAK,
                ChartType.KAGI,
            ).forEach { type ->
                CoineProChart(
                    series = series,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    type = type,
                    decoration = ChartDecoration(showVolume = false),
                    interactive = false,
                )
            }
        }
    }

    /**
     * The two pickers, and the «؟» that every row of them opens.
     *
     * The help is the reason the picker can offer Kagi and Point & Figure at all: a professional
     * audience still contains people who have never used them, and the value of offering an unusual
     * tool is that somebody can find out what it is without leaving the app.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartTypePicker() = capture("37-chart-type-picker-fa") {
        CoineProSheetBody(
            title = "نوع چارت",
            subtitle = "${persianDigits(ChartCatalog.CHART_TYPES.size)} نوع — برای راهنما «؟» را بزن",
        ) {
            ChartTypePicker(
                selected = ChartType.HEIKIN_ASHI,
                onSelect = {},
                onHelp = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun indicatorPicker() = capture("38-indicator-picker-fa") {
        CoineProSheetBody(
            title = "اندیکاتورها",
            // Counted, not typed. A hand-written total is wrong the first time the list grows.
            subtitle = "${persianDigits(ChartCatalog.INDICATORS.size)} اندیکاتور — برای راهنما «؟» را بزن",
        ) {
            IndicatorPicker(
                active = setOf("ema", "bollinger", "rsi"),
                onToggle = {},
                onHelp = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun helpSheet() {
        val catalog = HelpCatalog.load(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().assets,
        )
        capture("39-help-rsi-fa") {
            HelpBody(entry = catalog["rsi"]!!, modifier = Modifier.fillMaxSize())
        }
    }

    /**
     * The fifty-two drawing tools, with the glyphs TradingView already draws them with.
     *
     * A grid of unlabelled icons would be unusable and a list of Persian names without pictures
     * would be too — a reader looking for "کمان فیبوناچی" needs both. Whether four across is right
     * is a pixel question, which is why this is a screenshot.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun toolRail() = capture("40-tool-rail-fa") {
        CoineProSheetBody(
            title = "ابزارهای ترسیم",
            subtitle = "${persianDigits(DrawingTools.ALL.size)} ابزار — برای راهنما روی هر کدام نگه دار",
        ) {
            ToolRail(selected = "fib", onSelect = {}, onHelp = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun activeToolBar() = capture("41-active-tool-fa") {
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            ActiveToolBar(
                tool = DrawingTools["xabcd"],
                placed = 2,
                onCancel = {},
                onUndo = {},
                onHelp = {},
            )
            DrawingList(
                drawings = listOf(
                    Drawing(1, "trend", listOf(ChartPoint(1, 100.0), ChartPoint(2, 110.0))),
                    Drawing(2, "fib", listOf(ChartPoint(1, 100.0), ChartPoint(2, 110.0)), colour = 0xFF6E8BE0),
                    Drawing(3, "hline", listOf(ChartPoint(1, 105.0)), colour = 0xFF00B15C),
                ),
                onSelect = {},
                onDelete = {},
            )
        }
    }

    /**
     * A chart with the reader's own work on it.
     *
     * The tools render — the four contact sheets prove that — but this is the one that proves the
     * *integration*: drawings anchored in (time, price) landing on the right bars of a real chart,
     * over a real viewport, under the price axis rather than over it, with the selected one showing
     * its handles.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartWithDrawings() = capture("46-chart-drawn-fa") {
        val series = ScreenshotFixtures.chartSeries()
        val at = { fraction: Double -> series.time[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        val price = { fraction: Double -> series.close[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(360.dp),
                decoration = ChartDecoration(
                    drawings = listOf(
                        Drawing(
                            id = 1,
                            toolId = "trend",
                            points = listOf(
                                ChartPoint(at(0.55), price(0.55) * 0.995),
                                ChartPoint(at(0.95), price(0.95) * 1.005),
                            ),
                        ),
                        Drawing(
                            id = 2,
                            toolId = "fib",
                            points = listOf(
                                ChartPoint(at(0.62), series.low.drop(series.size * 62 / 100).min()),
                                ChartPoint(at(0.88), series.high.drop(series.size * 62 / 100).max()),
                            ),
                            colour = 0xFF6E8BE0,
                        ),
                        Drawing(
                            id = 3,
                            toolId = "hline",
                            points = listOf(ChartPoint(at(0.70), price(0.70))),
                            colour = 0xFF4FB3A5,
                        ),
                    ),
                    selectedDrawingId = 1,
                ),
            )
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                decoration = ChartDecoration(
                    showVolume = false,
                    drawings = listOf(
                        Drawing(
                            id = 1,
                            toolId = "longshort",
                            points = listOf(
                                ChartPoint(at(0.72), price(0.72)),
                                ChartPoint(at(0.92), price(0.72) * 0.99),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * The chart screen — the one the whole module existed for and did not have.
     *
     * Fifty-six indicators, fifty-two tools, eleven chart types and eight timeframes were all
     * built, tested and rendered before a single reader could reach any of them. This is the
     * screen that makes them reachable, and the render is the check that the layout survives
     * having all of it: header, timeframe row, chart, toolbar, and nothing crowded out.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartScreen() = capture("49-chart-screen-fa") {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope))
    }

    /** The same screen with four indicators on, which is what a real setup looks like. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartScreenLoaded() = capture("50-chart-screen-loaded-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger", "supertrend", "pivots").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller)
    }

    /**
     * The structure studies, on a chart.
     *
     * The seven that answer "where are the levels" rather than "what is the average here". They
     * needed two drawing shapes the chart did not have, so this is the render that proves both: a
     * horizontal level with its label clear of the bars, and a marker that points at a swing
     * without covering the high it is pointing at.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartStructure() = capture("48-chart-structure-fa") {
        val series = ScreenshotFixtures.chartSeries()
        val zigzag = Structure.zigzag(series, deviationPercent = 1.2)
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            // Support and resistance with the swing points that produced them, so the levels can be
            // checked against the bars they were derived from rather than taken on trust.
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                decoration = ChartDecoration(
                    levels = Structure.supportResistance(series, lookback = 8, tolerancePercent = 0.15),
                    markers = Structure.swings(series, left = 8, right = 8),
                    showVolume = false,
                ),
            )
            // The zigzag and the Fibonacci levels it places on its own last leg.
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                decoration = ChartDecoration(
                    overlays = listOf(zigzag.first),
                    markers = zigzag.second,
                    levels = Structure.autoFibonacci(series, deviationPercent = 1.2),
                    showVolume = false,
                ),
            )
            // The classic pivot ladder: one solid reference and six dashed levels around it.
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                decoration = ChartDecoration(
                    overlays = Structure.pivots(series, Structure.PivotType.CLASSIC),
                    showVolume = false,
                ),
            )
        }
    }

    /**
     * The chart mid-placement: three taps into a five-point pattern.
     *
     * This is the state nothing else covers — the tool armed, the points so far drawn as a live
     * preview, and the bar underneath saying how many taps are left. A reader who cannot see the
     * shape forming has no way to know they mis-tapped until the fifth tap commits it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartMidPlacement() = capture("47-chart-placing-fa") {
        val series = ScreenshotFixtures.chartSeries()
        val bar = { fraction: Double -> (series.size * fraction).toInt().coerceIn(0, series.size - 1) }
        var state = DrawingActions.arm(DrawingState(), DrawingTools["xabcd"]!!)
        for (fraction in listOf(0.55, 0.68, 0.80)) {
            val index = bar(fraction)
            state = DrawingActions.tap(state, ChartPoint(series.time[index], series.close[index]))
        }
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        ) {
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxWidth().height(400.dp),
                drawing = state,
                onDrawing = {},
            )
            ActiveToolBar(
                tool = state.tool,
                placed = state.pending.size,
                onCancel = {},
                onUndo = {},
                onHelp = {},
            )
        }
    }

    /**
     * Every drawing tool, drawn.
     *
     * Four contact sheets rather than one, because fifty tools on one screen is fifty postage
     * stamps — and one test each, because a Compose rule sets its content once per test. Each cell
     * is a real [ChartViewport] over the same bars, so what these show is what a reader gets: a Gann
     * fan's nine rays at their nine angles, a pitchfork's tines running past the last bar, the
     * Fibonacci prices printed on the levels.
     *
     * These are also the coverage test. `drawDrawing` returns whether it recognised the tool, and
     * [sheet] fails when any of them returns false — which is what stops a tool reaching the rail
     * with no way to render it. That is exactly how the icon-derived first attempt at the tool list
     * went wrong, and it went unnoticed until somebody looked.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun drawingToolsLines() =
        sheet("42-tools-lines-fa", ToolGroup.LINES, ToolGroup.CHANNELS)

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun drawingToolsFibonacci() =
        sheet("43-tools-fib-gann-fa", ToolGroup.FIBONACCI, ToolGroup.GANN)

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun drawingToolsPatterns() =
        sheet("44-tools-patterns-shapes-fa", ToolGroup.PATTERNS, ToolGroup.ELLIOTT, ToolGroup.SHAPES)

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun drawingToolsMeasure() =
        sheet("45-tools-measure-annotate-fa", ToolGroup.MEASURE, ToolGroup.POSITION, ToolGroup.ANNOTATION)

    /**
     * The four sheets between them cover every tool that is not a mode.
     *
     * Without this, adding an eleventh group would quietly go unrendered and unreviewed: the four
     * sheets above would still pass, having drawn everything they were asked to draw.
     */
    @Test
    fun theSheetsCoverEveryTool() {
        val covered = SHEET_GROUPS.flatMap { DrawingTools.inGroup(it) }.map { it.id }.toSet()
        val expected = DrawingTools.ALL.filterNot { it.group == ToolGroup.MODES }.map { it.id }.toSet()
        assertEquals(expected, covered)
        assertEquals(50, expected.size)
    }

    private fun sheet(name: String, vararg groups: ToolGroup) {
        val tools = groups.flatMap { DrawingTools.inGroup(it) }
        val drawn = mutableMapOf<String, Boolean>()
        capture(name) { ToolGallery(tools = tools, onDrawn = { id, ok -> drawn[id] = ok }) }
        val missing = tools.map { it.id }.filterNot { drawn[it] == true }
        assertTrue("these tools drew nothing: $missing", missing.isEmpty())
    }

    private fun capture(
        name: String,
        darkTheme: Boolean = true,
        content: @Composable () -> Unit,
    ) = captureRaw(name, darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }

    /**
     * Compose's own captureToImage goes through the platform window-capture path, which has no real
     * window under Robolectric. Drawing the decor view straight into a bitmap produces the same
     * pixels without needing one.
     *
     * [darkTheme] is pinned rather than left to the system setting, so a render captures the theme
     * it is named for regardless of what the host configuration reports.
     */
    private fun captureRaw(
        name: String,
        darkTheme: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CoineProTheme(darkTheme = darkTheme) { content() } }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val view = composeRule.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, WIDTH_PX, HEIGHT_PX)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(OUTPUT_DIR, "$name.png").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private companion object {
        /** Matches the w411dp-h914dp-xxhdpi qualifier on the class. */
        const val WIDTH_PX = 411 * 3
        const val HEIGHT_PX = 914 * 3

        val OUTPUT_DIR = File("build/screenshots")
    }
}

/**
 * One cell per tool: its name, and the tool drawn over a real viewport of the same bars.
 *
 * The points are generated from the tool's own tap count, spread across the visible range, so a
 * five-point pattern gets five points at sensible places rather than five copies of one. A freehand
 * tool gets a sampled squiggle, which is what a finger would have produced.
 */
@Composable
private fun ToolGallery(tools: List<DrawingTool>, onDrawn: (String, Boolean) -> Unit) {
    val measurer = rememberTextMeasurer()
    val series = remember { gallerySeries() }
    // Read out of the theme before the draw lambda: these are @Composable getters, and a draw
    // lambda is not a composition.
    val stage = CoineProColors.Stage
    val ink = CoineProColors.TextSecondary
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().background(stage)) {
        val columns = GALLERY_COLUMNS
        val rows = ((tools.size + columns - 1) / columns).coerceAtLeast(1)
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        tools.forEachIndexed { index, tool ->
            val column = index % columns
            val row = index / columns
            translate(left = column * cellWidth, top = row * cellHeight) {
                val label = measurer.measure(tool.label, TextStyle(color = ink, fontSize = 9.sp))
                drawText(label, topLeft = Offset(4f, 4f))
                val top = label.size.height + 8f
                val view = ChartViewport(series)
                    .sized(cellWidth - GALLERY_PAD * 2, cellHeight - top - GALLERY_PAD)
                // Clipped to its own cell, the same way the chart clips to its plot. Half these
                // tools are unbounded by definition, and without this a single ray crosses the
                // whole contact sheet and makes every other cell unreadable.
                translate(left = GALLERY_PAD, top = top) {
                    clipRect(0f, 0f, view.plotWidth, view.plotHeight) {
                    val ok = drawDrawing(
                        drawing = Drawing(
                            id = index.toLong() + 1,
                            toolId = tool.id,
                            points = galleryPoints(tool, series, view),
                            text = "یادداشت",
                        ),
                        view = view,
                        measurer = measurer,
                    )
                    onDrawn(tool.id, ok)
                    }
                }
            }
        }
    }
}

/** A hundred bars of a gentle up-then-down walk, deterministic so the sheets are comparable. */
private fun gallerySeries(): CandleSeries = CandleSeries(
    (0 until 100).map { index ->
        val phase = index / 99.0
        val base = 100.0 + 18 * kotlin.math.sin(phase * Math.PI) + index * 0.06
        Candle(
            t = 1_772_000_000L + index * 3600L,
            o = base,
            h = base + 1.2,
            l = base - 1.2,
            c = base + 0.4,
        )
    },
)

/**
 * Points for one tool, spread across the middle of the view.
 *
 * Alternating above and below the walk, because a pattern drawn as a straight line of five collinear
 * points shows nothing about whether the pattern renders.
 */
private fun galleryPoints(
    tool: DrawingTool,
    series: CandleSeries,
    view: ChartViewport,
): List<ChartPoint> {
    val count = if (tool.points <= 0) GALLERY_FREEHAND else tool.points
    val low = view.priceRange.start
    val high = view.priceRange.endInclusive
    val first = view.firstVisible + (view.visibleCount * 0.18).toInt()
    val step = ((view.visibleCount * 0.6) / count.coerceAtLeast(1)).toInt().coerceAtLeast(1)
    return (0 until count).map { index ->
        val bar = (first + index * step).coerceIn(0, series.size - 1)
        val swing = if (index % 2 == 0) 0.30 else 0.70
        ChartPoint(series.time[bar], low + (high - low) * swing)
    }
}

private const val GALLERY_COLUMNS = 3
private const val GALLERY_PAD = 6f

/** How many samples stand in for a finger's stroke on a freehand tool. */
private const val GALLERY_FREEHAND = 9

/** The groups the four contact sheets cover, in the order they cover them. */
private val SHEET_GROUPS = listOf(
    ToolGroup.LINES,
    ToolGroup.CHANNELS,
    ToolGroup.FIBONACCI,
    ToolGroup.GANN,
    ToolGroup.PATTERNS,
    ToolGroup.ELLIOTT,
    ToolGroup.SHAPES,
    ToolGroup.MEASURE,
    ToolGroup.POSITION,
    ToolGroup.ANNOTATION,
)
