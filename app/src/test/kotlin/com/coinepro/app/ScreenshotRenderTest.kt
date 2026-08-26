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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.coinepro.core.designsystem.CoineProColors
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
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.copytrade.CopyTradeScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    @Test
    fun auth() = capture("11-auth") {
        AuthScreen(
            state = SessionState.SignedOut,
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
