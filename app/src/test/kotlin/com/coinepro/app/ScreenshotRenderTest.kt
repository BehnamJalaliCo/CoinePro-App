package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.app.notifications.channelDescriptionRes
import com.coinepro.app.notifications.channelNameRes
import com.coinepro.app.security.TamperedScreen
import com.coinepro.core.academy.AcademyExtra
import com.coinepro.core.account.AccountController
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
import com.coinepro.core.community.CommunityCategory
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityFeedPage
import com.coinepro.core.community.CommunityGateway
import com.coinepro.core.community.CommunityLeaderboard
import com.coinepro.core.community.CommunityLikeOutcome
import com.coinepro.core.community.CommunityLockedException
import com.coinepro.core.community.CommunityPost
import com.coinepro.core.community.CommunityReactionOutcome
import com.coinepro.core.community.CommunityReply
import com.coinepro.core.community.CommunityThread
import com.coinepro.core.community.CommunityWriteOutcome
import com.coinepro.core.common.UiMessage
import com.coinepro.core.common.MessageKey
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingList
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.copytrade.CopyBook
import com.coinepro.core.copytrade.CopyPreferences
import com.coinepro.core.copytrade.CopyTradeGateway
import com.coinepro.core.copytrade.CopyTradeStatus
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.Structure
import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.papertrade.PaperSide
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.chart.drawDrawing
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.StoredProfile
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.CoineProListDetail
import com.coinepro.core.designsystem.PriceFeedReading
import com.coinepro.core.designsystem.CoineProPriceFeedBar
import com.coinepro.core.designsystem.CoineProOfflineBar
import com.coinepro.core.designsystem.CoineProNavigationRail
import com.coinepro.core.designsystem.CoineProRailHeader
import com.coinepro.core.designsystem.CoineProRailItem
import com.coinepro.core.designsystem.CoineProReading
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.ProChartWordmark
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.guest.GuestController
import com.coinepro.core.help.HelpBody
import com.coinepro.core.help.HelpCatalog
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarMark
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationSettings
import com.coinepro.core.notifications.QuietHours
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.academy.AcademyExtraBody
import com.coinepro.feature.academy.AcademyScreen
import com.coinepro.feature.academy.LessonScreen
import com.coinepro.feature.account.DeleteAccountScreen
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.core.diagnostics.AdminGateState
import com.coinepro.feature.admin.AdminScreen
import com.coinepro.feature.ai.AiStudioScreen
import com.coinepro.feature.alerts.AlertCenterScreen
import com.coinepro.feature.alerts.AlertsController
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.auth.EmailAuthScreen
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.community.CommunityScreen
import com.coinepro.feature.community.CommunityThreadScreen
import com.coinepro.feature.explore.ExploreScreen
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartPanesScreen
import com.coinepro.feature.chart.ChartWorkspaceStore
import com.coinepro.feature.chart.ChartStudioScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.copytrade.CopyTradeScreen
import com.coinepro.feature.guest.GuestGate
import com.coinepro.feature.guest.GuestGateScreen
import com.coinepro.feature.guest.GuestScreen
import com.coinepro.feature.guest.MembershipGate
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookState
import com.coinepro.feature.dom.DepthOfMarketBody
import com.coinepro.feature.heatmap.HeatmapController
import com.coinepro.feature.heatmap.HeatmapScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.home.HomeSubscription
import com.coinepro.feature.journal.JournalScreen
import com.coinepro.feature.kyc.KycScreen
import com.coinepro.feature.legal.LegalDocument
import com.coinepro.feature.legal.LegalDocumentBody
import com.coinepro.feature.legal.LegalMarkdown
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.notifications.AlertComposerBody
import com.coinepro.feature.notifications.NotificationSection
import com.coinepro.feature.notifications.NotificationSettingsScreen
import com.coinepro.feature.papertrade.PaperTradeScreen
import com.coinepro.feature.portfolio.PortfolioScreen
import com.coinepro.feature.profile.AvatarComposerBody
import com.coinepro.feature.profile.ProfileAction
import com.coinepro.feature.profile.ProfileScreen
import com.coinepro.feature.screener.ScreenerController
import com.coinepro.feature.screener.ScreenerScreen
import com.coinepro.feature.script.ScriptScreen
import com.coinepro.feature.search.MarketsScreen
import com.coinepro.feature.search.MarketsSignalStrip
import com.coinepro.feature.search.SearchScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
            // Not passed. The shipping app supplies no `openSignals` — home has never rendered
            // this card — so the reference render carried 233dp the product does not have.
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
            // Passed because the running app passes them. A render that leaves an optional
            // callback null draws a screen the app never shows — the balance without the control
            // that hides it, the quick row without any of its three slots — and then the review
            // the screenshots exist for is a review of something else.
            onToggleBalanceHidden = {},
            onOpenPortfolio = {},
            onOpenTools = {},
            onOpenActivity = {},
            onOpenNews = {},
            watchlist = listOf("ETHUSDT", "SOLUSDT"),
        )
    }

    /** The same screen with the balance put away, which is what the eye beside it does. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun homeBalanceHidden() = capture("92-home-balance-hidden-fa") {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            openSignals = ScreenshotFixtures.homeSignals,
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
            balanceHidden = true,
            onToggleBalanceHidden = {},
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
     * The first screen, before anybody has signed in.
     *
     * This is the render that matters most of the sixty-odd here, because it is the only one a
     * person who has never heard of the product will ever see. It has to answer "what is this" and
     * "what does it cost" without a password field, and the membership card has to read as an
     * explanation rather than a paywall — there is no wall and nothing to pay, and a card that
     * looks like a price tag would be lying about the business.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun guestMarket() = capture("66-guest-fa") {
        GuestScreen(
            controller = GuestController(FakeGuestGateway(), scope, pollMillis = 60_000),
            onSignIn = {},
            avatar = AvatarSpec(AvatarBase.Mark(AvatarMark.ROCKET), AvatarRing.GOLD),
            onOpenProfile = {},
            onOpenSymbol = {},
            onOpenMarket = {},
            onOpenTools = {},
        )
    }

    /**
     * What a guest finds on the signals tab.
     *
     * Rendered because it is the one place in the guest experience where the app asks for
     * something, and the render is the only way to check it still reads as an offer rather than as
     * a wall: the track record has to be *below* the card and visible without a scroll, because the
     * argument for the account is what the signals did, not the sentence saying they exist.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun guestSignalsGate() = capture("83-guest-signals-gate-fa") {
        val controller = GuestController(FakeGuestGateway(), scope, pollMillis = 60_000)
        controller.refreshTrackRecord()
        GuestGateScreen(gate = GuestGate.SIGNALS, controller = controller, onSignIn = {})
    }

    /**
     * The profile, for somebody who has not signed in.
     *
     * A guest has a real page here — their own avatar, their own name, their own watchlist counted
     * — and one offer. The render is the check that the offer reads as an offer: it sits under the
     * hero rather than over it, and nothing above it is withheld or blurred.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun profileGuest() = capture("84-profile-guest-fa") {
        ProfileScreen(
            profile = StoredProfile(
                avatar = AvatarSpec(AvatarBase.Mark(AvatarMark.TREND), AvatarRing.ANALYSIS),
            ),
            guest = true,
            platformLabel = "کریپتو",
            readings = listOf(CoineProReading(label = "بازارهای دنبال‌شده", value = "۷")),
            onSignIn = {},
            actions = listOf(
                ProfileAction(label = "ایمنی و نسخه", note = "اعلان‌ها، گزارش خطا و شماره‌ی نسخه") {},
            ),
        )
    }

    /**
     * The same page with an account behind it.
     *
     * The five account rows are the ones that used to be a dropdown off Home's avatar, and the
     * render is what says whether they read as a list rather than as a menu that escaped. The
     * deletion row is last and in the refusal colour; if it does not look different from "sign out"
     * at a glance, the screen has failed.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun profileMember() = capture("85-profile-member-fa") {
        ProfileScreen(
            profile = StoredProfile(
                displayName = "بهنام",
                tagline = "طلا و بیت‌کوین، روزانه",
                avatar = AvatarSpec(AvatarBase.Symbol("XAUUSD"), AvatarRing.PREMIUM),
            ),
            accountName = "بهنام جلالی",
            email = "behnam@example.com",
            planLabel = "اشتراک حرفه‌ای",
            platformLabel = "فارکس",
            readings = listOf(
                CoineProReading(label = "بازارهای دنبال‌شده", value = "۱۲"),
                CoineProReading(label = "معامله‌های ثبت‌شده", value = "۳۴"),
            ),
            actions = listOf(
                ProfileAction(label = "احراز هویت") {},
                ProfileAction(label = "هشدارهای قیمت") {},
                ProfileAction(label = "ایمنی و نسخه", note = "اعلان‌ها، گزارش خطا و شماره‌ی نسخه") {},
                ProfileAction(label = "خروج") {},
                ProfileAction(label = "حذف حساب", destructive = true) {},
            ),
        )
    }

    /**
     * The avatar composer, on the marks shelf.
     *
     * This is the render that matters for the whole feature. Ten marks are drawn here — each one a
     * Compose path rather than an emoji font — and the capture is the only thing that says they are
     * legible at forty-two points rather than a smudge. The preview at the top is the live avatar,
     * so the same render also checks the ring and the base compose into one object.
     *
     * They are drawn at rest: [continuousMotionAllowed] reports false under a render, which holds
     * every mark on the frame it looks best on and keeps the capture deterministic.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun avatarComposerMarks() = capture("86-avatar-composer-fa") {
        AvatarComposerBody(
            current = AvatarSpec(AvatarBase.Mark(AvatarMark.ROCKET), AvatarRing.GOLD),
            initial = "ب",
            photoPath = null,
            onSave = {},
            onCancel = {},
            onPickPhoto = {},
        )
    }

    /**
     * Every mark and every ring, side by side.
     *
     * A contact sheet rather than a screen, for the same reason the drawing tools have one: ten
     * marks reviewed one at a time is ten judgements about whether something looks right, and what
     * actually matters is whether they look like one set.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun avatarGallery() = capture("87-avatar-gallery-fa") {
        AvatarGallery()
    }

    /**
     * Deleting the account, on a server that serves the route.
     *
     * Rendered because it is the one screen whose job is to be read rather than used: three cards
     * of consequence before a field. If the "what is kept" card is not legible at a glance the
     * screen has failed, and only a render says whether it is.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun deleteAccountSupported() = capture("64-delete-account-fa") {
        DeleteAccountScreen(
            controller = AccountController(FakeAccountGateway(), scope),
            supported = true,
            onDeleted = {},
        )
    }

    /**
     * The same screen where the server has no deletion route.
     *
     * The published out-of-app page replaces the field and the button. This is what ships today —
     * neither backend serves deletion yet — so it is the state a Play reviewer will actually see.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun deleteAccountUnsupported() = capture("65-delete-account-web-fa") {
        DeleteAccountScreen(
            controller = AccountController(FakeAccountGateway(), scope),
            supported = false,
            onDeleted = {},
        )
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

    /**
     * The tab with nothing in it, which is most of a quiet week on «فعال».
     *
     * Rendered because it is the state a reader is most likely to mistake for a failure, and the
     * only way to know it does not look like one is to look at it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun signalsEmpty() {
        val controller = SignalController(EmptySignalGateway(), scope)
        controller.refresh()
        capture("93-signals-empty-fa") { SignalsScreen(controller = controller, onOpenSignal = {}) }
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

    /**
     * The three lists that hang off the roadmap — badges, leaderboard, glossary.
     *
     * Rendered as sheet bodies rather than through the sheets themselves: the capture takes the
     * activity's decor view, and a ModalBottomSheet draws in its own window, so a screenshot of one
     * comes out blank. Same reason `CoineProSheetBody` exists.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1000dp-xxhdpi")
    fun academyExtras() {
        val extras = ScreenshotFixtures.academyExtras()
        capture("57-academy-extras-fa") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoineProSheetBody(title = "نشان‌ها") {
                    AcademyExtraBody(AcademyExtra.ACHIEVEMENTS, extras)
                }
                CoineProSheetBody(title = "جدول امتیازات") {
                    AcademyExtraBody(AcademyExtra.LEADERBOARD, extras)
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1000dp-xxhdpi")
    fun academyGlossary() {
        capture("58-academy-glossary-fa") {
            CoineProSheetBody(title = "واژه‌نامه") {
                AcademyExtraBody(AcademyExtra.GLOSSARY, ScreenshotFixtures.academyExtras())
            }
        }
    }

    @Test
    fun tools() = capture("05-tools") {
        ToolsScreen(
            onOpenConnections = {},
            onOpenNews = {},
            onOpenCalendar = {},
            onOpenJournal = {},
            onOpenPaperTrade = {},
        )
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

    /**
     * The in-app legal reader, on an excerpt carrying every construct the renderer supports: title,
     * revision line, blockquote, rule, headings, a paragraph with bold and inline code, bullets, a
     * Persian-numbered list, a table and a named link.
     *
     * The blockquote is a line from the risk warning. It used to be the editors' note that opened
     * `TERMS.md` — and that note was not a construct being exercised, it was a note to us that
     * shipped: the first paragraph a reader saw inside the app's own terms of use, and on the
     * public site with it. It lives in `docs/legal/README.md` now. Neither document currently uses
     * a blockquote at all; the construct stays covered here because the renderer supports it.
     *
     * Parsed from a literal rather than read through the AssetManager, so the picture is identical
     * on every run and does not depend on where a merged library asset landed.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun legalReader() = capture("98-legal-fa") {
        LegalDocumentBody(
            document = LegalDocument.TERMS,
            reading = LegalMarkdown.read(LEGAL_EXCERPT),
            onOpenDocument = {},
        )
    }

    @Test
    fun menu() = capture("96-menu") {
        MenuScreen(
            access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = true),
            onOpen = {},
            name = "بهنام",
            email = "trader@example.com",
            planLabel = "حرفه‌ای",
            platformLabel = "کریپتو",
            watchlistCount = 12,
        )
    }

    /**
     * The same page with nobody signed in, and it is the case worth having a picture of.
     *
     * Every account-only row is still on it, marked rather than missing — so a capture that came
     * back shorter than the member's would be the bug this screen exists to prevent.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun menuGuestPersian() = capture("97-menu-guest-fa") {
        MenuScreen(
            access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = false),
            onOpen = {},
            platformLabel = "کریپتو",
            watchlistCount = 3,
            onSignIn = {},
        )
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

    /* ----------------------------------------------------- the Persian six */

    /*
     * Six screens had only ever been rendered in the default English locale, left over from before
     * Persian became the app's default. That is precisely where the bugs of this app hide: a Latin
     * figure inside a right-to-left paragraph reorders, a label and its value swap ends, a form's
     * suffix lands on the wrong side of its field. None of it shows in an English render.
     *
     * A screen the app ships in Persian and has only been looked at in English has not been looked
     * at.
     */

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun toolsPersian() = capture("67-tools-fa") {
        ToolsScreen(
            onOpenConnections = {},
            onOpenNews = {},
            onOpenCalendar = {},
            onOpenJournal = {},
            onOpenPaperTrade = {},
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun activityPersian() {
        val notifications = NotificationController(FakeNotificationGateway(), scope)
        val executions = ExecutionController(FakeExecutionGateway(), scope)
        val signals = SignalController(FakeSignalGateway(), scope)
        notifications.refresh()
        executions.refreshExecutions()
        signals.refreshHistory()
        capture("68-activity-fa") {
            ActivityScreen(
                controller = notifications,
                executionController = executions,
                signalController = signals,
                onOpenSignal = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun connectionsPersian() {
        val controller = ExecutionController(FakeExecutionGateway(), scope)
        controller.refreshConnections()
        capture("69-connections-fa") { ConnectionsScreen(controller = controller) }
    }

    /**
     * Connections on the **forex** platform, which until now was not this screen at all.
     *
     * The route drew the copy-trading screen there, so the one thing a CoinePro-FX reader comes to
     * Connections for — linking their MetaTrader 5 account — had nowhere to be. This is the gate on
     * it: the same card shape as the LBank surface beside it, the four fields the server's
     * `user/account/link` actually takes, and a status line that says *not linked* rather than
     * implying a connection nobody has made.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun connectionsForexPersian() {
        val copy = CopyTradeController(NotLinkedCopyGateway(), scope)
        copy.refresh()
        capture("71-connections-mt5-fa") {
            ConnectionsScreen(
                controller = ExecutionController(FakeExecutionGateway(), scope),
                platform = MarketPlatform.COINEPRO_FX,
                copyTrade = copy,
            )
        }
    }

    /**
     * A server that answers copy-status with no account on it: linked to nothing, and no error.
     *
     * Deliberately the *empty* case rather than a connected one. A screenshot of a working link is
     * a picture of a happy path nobody doubts; the picture worth reviewing is the one a reader sees
     * on the day they arrive, because that is the screen that has to teach them what to do.
     */
    private class NotLinkedCopyGateway : CopyTradeGateway {
        override suspend fun status() = CopyTradeStatus(
            account = null,
            preferences = CopyPreferences(),
            master = CopyBook(),
            mirrored = emptyList(),
            mode = null,
            accountMismatch = false,
            liveAccount = null,
            events = emptyList(),
            slotState = null,
        )

        override suspend fun setEnabled(enabled: Boolean) = CopyPreferences(enabled = enabled)

        override suspend fun linkAccount(broker: String, server: String, login: String, password: String) = Unit

        override suspend fun unlinkAccount() = Unit
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun newsPersian() {
        val controller = MarketIntelController(FakeMarketIntelGateway(), scope)
        controller.refresh()
        capture("70-news-fa") { NewsScreen(controller = controller, onOpenCalendar = {}) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun calendarPersian() {
        val controller = MarketIntelController(FakeMarketIntelGateway(), scope)
        controller.refresh()
        capture("71-calendar-fa") { EconomicCalendarScreen(controller = controller, onOpenNews = {}) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun launchReadinessPersian() = capture("72-launch-readiness-fa") {
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

    /** The door in front of it, which is the first thing anybody now sees. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1800dp-xxhdpi")
    fun adminLocked() = capture("23-admin-locked-fa") {
        AdminScreen(
            state = ScreenshotFixtures.adminState.copy(
                gate = AdminGateState(provisioned = true),
            ),
        )
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
        )
    }

    /**
     * The copy that somebody re-signed, refusing to be it.
     *
     * Rendered because it is the one screen in the app whose whole job is to be believed by
     * somebody who was told to trust the file they installed. It has no way out on purpose: a
     * "continue anyway" button is the first thing a repackager would tell them to press.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun tampered() = capture("88-tampered-fa") {
        TamperedScreen(
            actualFingerprint = "3F:A1:0C:88:D2:47:B9:5E:11:6A:C4:70:29:8B:DD:04:E6:52:97:1F:" +
                "AB:30:C9:75:44:E8:12:66:BF:0D:53:AA",
        )
    }

    /**
     * The notification settings, for somebody with an account.
     *
     * The render is the check on the one thing this screen can get wrong invisibly: fifteen
     * switches in four groups either read as a list somebody can find their way around, or as a
     * wall they scroll past to reach the one they came for. Persian labels are long, and a switch
     * whose label wraps onto three lines turns the wall into a certainty.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun notificationSettings() = capture("89-notifications-fa") {
        val context = LocalContext.current
        NotificationSettingsScreen(
            settings = NotificationSettings(
                quietHours = QuietHours(enabled = true, fromMinuteOfDay = 23 * 60, toMinuteOfDay = 7 * 60),
            ),
            sections = listOf(
                NotificationSection(
                    title = "سیگنال و معامله",
                    categories = listOf(
                        NotificationCategory.NEW_SIGNAL,
                        NotificationCategory.TARGET_HIT,
                        NotificationCategory.STOP_HIT,
                        NotificationCategory.COPY_FAILED,
                    ),
                ),
                NotificationSection(
                    title = "بازار",
                    categories = listOf(
                        NotificationCategory.PRICE_ALERT,
                        NotificationCategory.WATCHLIST_MOVE,
                        NotificationCategory.NEWS,
                    ),
                ),
                NotificationSection(
                    title = "حساب",
                    categories = listOf(NotificationCategory.SECURITY, NotificationCategory.MARKETING),
                ),
            ),
            alerts = listOf(
                LocalPriceAlert(
                    id = "1",
                    symbol = "BTCUSDT",
                    condition = LocalAlertCondition.ABOVE,
                    value = 65_000.0,
                ),
                LocalPriceAlert(
                    id = "2",
                    symbol = "ETHUSDT",
                    condition = LocalAlertCondition.PERCENT_DOWN,
                    value = 5.0,
                    repeat = AlertRepeat.DAILY,
                    referencePrice = 3_142.0,
                    active = false,
                ),
            ),
            // The app's own strings, through the app's own mapping. The fixture had a second copy
            // of this table and a fallback of «توضیح کوتاه این دسته» for the categories nobody had
            // hand-written — so the review looked at placeholder copy on eight of the eleven rows
            // and could not have caught a real string being wrong.
            labelFor = { category -> context.getString(category.channelNameRes()) },
            noteFor = { category -> context.getString(category.channelDescriptionRes()) },
        )
    }

    /**
     * Making an alert, on the shelf a guest can actually use.
     *
     * Six conditions as chips and three repeat rules as chips: the render is what says whether
     * fifteen Persian words fit two scrolling rows without either wrapping or being cut mid-word.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun alertComposer() = capture("90-alert-composer-fa") {
        AlertComposerBody(
            symbol = "BTCUSDT",
            currentPrice = 64_182.4,
            onCreate = {},
            onCancel = {},
        )
    }

    /**
     * The membership card, with both exchanges on it.
     *
     * Rendered because this is the screen where the product's whole commercial arrangement is put
     * to a reader, and because the two marks are the point of this version: a reader choosing
     * between copy trading and signals-only should be able to tell the two buttons apart without
     * reading either of them.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun membershipGate() = capture("91-membership-fa") {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            MembershipGate(
                onSignIn = {},
                terms = ScreenshotFixtures.membershipTerms,
            )
        }
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
            state = SessionState.RevalidationRequired(UiMessage.of(MessageKey.SESSION_NOT_REVALIDATED)),
            loginConfigState = LoginConfigState.Ready("CoineProBot"),
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
        val browsed = controller.state.value.results.size
        controller.setQuery("eur")
        // The controller debounces by 80 ms. A capture taken on the same tick rendered the whole
        // browse list under a box with «eur» in it — a picture of the screen not searching, which
        // is exactly what this case exists to rule out. Wait for the state a reader would be
        // looking at instead of for a fixed number of milliseconds.
        runBlocking { withTimeout(2_000) { controller.state.first { it.results.size != browsed } } }
        capture("33-search-query-fa") { SearchScreen(controller = controller) }
    }

    /**
     * The heatmap, over the same catalogue the search cases use.
     *
     * Worth a render case rather than a unit test alone because the thing that can be wrong here is
     * not the arithmetic — the treemap has its own tests — it is whether a tile at phone width is
     * still big enough to carry its ticker, and whether the colour ramp reads as a scale rather
     * than as noise. Neither is visible in an assertion.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun marketHeatmap() {
        val controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
        controller.start()
        val heatmap = HeatmapController(controller, scope, ScreenshotFixtures.heatmapBars(), null)
        heatmap.start()
        // The bars resolve one symbol at a time, and a tile exists only once its market has a
        // price. Captured before they land the map is eleven tiles — the handful the fixture
        // catalogue quotes directly — which is a real state and not the one worth reviewing.
        val covered = controller.state.value.results.size
        runBlocking { withTimeout(30_000) { heatmap.state.first { it.assets.size >= covered } } }
        capture("34-heatmap-fa") { HeatmapScreen(controller = heatmap, onOpenSymbol = {}) }
    }

    /**
     * The screener, over the same catalogue.
     *
     * TradingView ships seven of these on the web and none at all on a phone — they say so in their
     * own help centre. That makes this screen one of the few where we have no reference to copy, so
     * it is rendered rather than only asserted: the question is whether a filter row, a sortable
     * header and a dense result table can share a 411dp width without any of the three becoming
     * unreadable.
     */
    /**
     * The order-book ladder, with a book in it.
     *
     * Rendered because the ladder is the one screen in this app whose whole job is a proportion:
     * a bar's length against the heaviest visible level. A bar that saturates, or one that never
     * fills, reads as a market with no shape — and neither is visible in a unit test.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun depthOfMarket() {
        val book = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(68_420.0 to 3.4, 68_410.0 to 11.2, 68_400.0 to 6.1, 68_390.0 to 1.8)
                .map { (p, q) -> DepthLevel(p, q) },
            asks = listOf(68_450.0 to 2.2, 68_460.0 to 8.7, 68_470.0 to 4.0, 68_480.0 to 12.9)
                .map { (p, q) -> DepthLevel(p, q) },
            at = 1_772_000_000_000L,
        )
        capture("36-dom-fa") {
            DepthOfMarketBody(
                state = OrderBookState(symbol = "BTCUSDT", book = book, sourceName = "LBank Futures"),
                onPickPrice = {},
                onRetry = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun marketScreener() {
        val controller = ScreenerController(gateway = ScreenshotFixtures.searchCatalog(), scope = scope)
        controller.start()
        capture("35-screener-fa") { ScreenerScreen(controller = controller, onOpenSymbol = {}) }
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
            subtitle = "${(ChartCatalog.CHART_TYPES.size).toPersianDigits()} نوع — برای راهنما «؟» را بزن",
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
            subtitle = "${(ChartCatalog.INDICATORS.size).toPersianDigits()} اندیکاتور — برای راهنما «؟» را بزن",
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
            subtitle = "${(DrawingTools.ALL.size).toPersianDigits()} ابزار — برای راهنما روی هر کدام نگه دار",
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
        // `onOpenStudio` is passed because the app passes it. Without it the studio row at the foot
        // is absent and the capture shows a page ending in a screenful of empty stage — a picture
        // of a layout nobody ever sees, which is worse than no picture: it was read as dead space
        // in the design and it was the fixture's.
        ChartScreen(controller = ScreenshotFixtures.chartController(scope), onOpenStudio = {})
    }

    /** The same screen with four indicators on, which is what a real setup looks like. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartScreenLoaded() = capture("50-chart-screen-loaded-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger", "supertrend", "pivots").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /**
     * A drawing tool armed, which is the state that used to be invisible.
     *
     * The report was that pressing any tool did nothing on screen, and it was accurate: the strip
     * that says which tool is armed sat below the command band, below the chart, off the bottom of
     * a phone. Arming closed the sheet and the reader saw no change at all. This render is the gate
     * on that — the bar has to be **on the plot**, above the candles, saying which tool and which
     * point it is waiting for.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartScreenToolArmed() = capture("51-chart-tool-armed-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        controller.arm(DrawingTools.ALL.first { it.id == "trend" })
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /**
     * The venue's relay in its three broken states, and the phone's own offline bar beside them.
     *
     * Four bars in one render because the thing worth looking at is whether they read as four
     * different facts. They must not: the offline bar is the reader's network and is drawn in the
     * sell red; the three relay bars are the *server's* upstream and are drawn in the warning ink,
     * because the day's figures on the screen behind them are still correct. A reader who reads
     * «قیمت زنده قطع است» as «برو اینترنتت را چک کن» has been sent to fix something that is not
     * broken.
     *
     * The healthy state is deliberately absent from this render, because it is absent from the
     * app: a bar that says everything is fine is a bar nobody reads, and a badge that says it on a
     * server which never sent the field would be the exact silence this whole feature ends.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun priceFeedBars() = capture("99-feed-bars-fa") {
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProOfflineBar(online = false)
            CoineProPriceFeedBar(status = PriceFeedReading.PARTIAL)
            CoineProPriceFeedBar(status = PriceFeedReading.FULL)
            CoineProPriceFeedBar(status = PriceFeedReading.UNKNOWN)
        }
    }

    /* ── the tablet layer ─────────────────────────────────────────────────────────────────────
     *
     * Every case above this line renders at `w411dp`, and until now every case in the file did.
     * That is what let a phone layout stretched across a metre of glass ship unremarked: the gate
     * this repository judges its design with had never once looked at a tablet. `targetSdk = 36`
     * means Android 16 ignores `resizeableActivity`, so the app *is* run at these widths today.
     *
     * The two windows are [TABLET_LANDSCAPE] and [TABLET_PORTRAIT] and they are deliberately the
     * only two. Portrait is not landscape with the numbers swapped — at 800dp the rail carries
     * glyphs alone, the chart affords one side column and a list-detail refuses to split; at
     * 1280dp all three go the other way. A gate with only the landscape case would pass while the
     * more common way to hold a tablet was never looked at.
     */

    /**
     * What the owner judges first: the rail, and a list that is not thrown away to open one row.
     *
     * Markets beside the chart of the row that is open, with the real navigation rail down the
     * start edge — the right, in Persian, and that is the single most visible thing this render is
     * here to prove. A rail on the left of a right-to-left screen is not a subtle bug; it is the
     * first thing anybody sees and it looks like a port rather than a design.
     *
     * At 1280dp the rail is the labelled form, so this render also covers the wordmark in its
     * header and the labels beside the glyphs.
     */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_LANDSCAPE)
    fun tabletShell() = captureRaw("94-tablet-shell-fa") { TabletShell() }

    /** The same, in the light theme, which must hold the same structure rather than invert. */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_LANDSCAPE)
    fun tabletShellLight() = captureRaw("94-tablet-shell-fa-light", darkTheme = false) { TabletShell() }

    /**
     * The same shell held upright.
     *
     * Three things change and all three are decisions rather than reflow: the rail drops its labels
     * (800dp is under `LABELLED_RAIL_WIDTH_DP`, and the 160dp they cost would come out of the
     * content), the list-detail refuses to split, and the chart keeps only its tool column. This is
     * the render that catches a threshold moved by mistake — each of those would silently become
     * the landscape answer.
     */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_PORTRAIT)
    fun tabletShellPortrait() = captureRaw("95-tablet-shell-portrait-fa") { TabletShell() }

    /**
     * The chart with the room a tablet has, and nothing between the reader and it.
     *
     * The plot takes the middle at [TABLET_PLOT_SCREEN_FRACTION]-equivalent height, the drawing
     * palette is open at the start edge instead of behind a sheet, and the readings have their own
     * column at the end instead of a band under the plot that scrolls away. The band's «ترسیم»
     * button is gone, because the palette it opens is already on screen.
     */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_LANDSCAPE)
    fun tabletChart() = capture("96-tablet-chart-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger", "supertrend", "rsi").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /**
     * The same chart on the tablet held upright: the palette column, and no readings column.
     *
     * 800dp less the palette's 280 leaves 520 of plot, which clears the floor; adding the readings
     * as well would leave 200 and put the chart below the width it has on a phone. That trade is
     * the whole of `columnsFor`, and this is the picture of it being made.
     */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_PORTRAIT)
    fun tabletChartPortrait() = capture("97-tablet-chart-portrait-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "rsi").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /**
     * Eight panes, which is the number this screen refuses to draw on a phone.
     *
     * The count is driven through the workspace store rather than by tapping, because a render
     * cannot tap — and going through the store is the better test anyway: it is the same path a
     * reader's saved arrangement takes on the next cold start, so this render also proves that
     * eight panes survive being written down and read back.
     */
    @Test
    @Config(sdk = [34], qualifiers = TABLET_LANDSCAPE)
    fun tabletChartPanes() = capture("98-tablet-panes-fa") {
        val workspace = remember {
            ChartWorkspaceStore(FakeScreenshotPreferences()).also { store ->
                // Inside the `remember` so the arrangement is written once rather than on every
                // recomposition, and on Unconfined so it has already landed by the time the
                // screen's own effect reads it back.
                scope.launch {
                    store.setPaneCount(8)
                    store.setExtraPaneSymbols(
                        listOf("BTCUSDT", "ETHUSDT", "XAGUSD", "BTCUSDT", "XAUUSD", "ETHUSDT", "XAGUSD"),
                    )
                }
            }
        }
        val controllers = remember { mutableMapOf<String, ChartController>() }
        ChartPanesScreen(
            firstSymbol = "XAUUSD",
            controllerFor = { symbol ->
                controllers.getOrPut(symbol) { ScreenshotFixtures.chartController(scope, symbol) }
            },
            watchlist = listOf("XAUUSD", "BTCUSDT", "ETHUSDT", "XAGUSD"),
            workspace = workspace,
            onBack = {},
        )
    }

    /**
     * The rail and a list-detail around the markets list, which is what a tablet reader sees.
     *
     * `CoineProListDetail` is handed a detail because a tablet reader opening a row keeps the list;
     * on the portrait render the same call draws the list alone and the row tap stays a navigation,
     * which is the phone behaviour and is correct there.
     */
    @Composable
    private fun TabletShell() {
        Row(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
            CoineProNavigationRail(
                items = railItems(),
                selectedKey = AppDestination.EXPLORE.route,
                onSelect = {},
                header = {
                    CoineProRailHeader {
                        // Only where the rail is wide enough to hold it. The wordmark is 160dp at
                        // its smallest legible size and the icon rail is 80dp wide; scaling it down
                        // to fit would put an illegible logo on the screen, which is worse than no
                        // logo at all.
                        if (coineProWindowClass().prefersLabelledRail) {
                            ProChartWordmark(modifier = Modifier.width(RAIL_WORDMARK))
                        }
                    }
                },
            )
            CoineProListDetail(
                modifier = Modifier.weight(1f),
                detail = {
                    ChartScreen(
                        controller = ScreenshotFixtures.chartController(scope),
                        onOpenStudio = {},
                    )
                },
            ) {
                MarketsScreen(
                    controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                        .also { it.start() },
                    sparklines = ScreenshotFixtures.sparklineStore(scope),
                    watchlist = listOf("BTCUSDT", "XAUUSD"),
                    onOpenSymbol = {},
                    onOpenSearch = {},
                )
            }
        }
    }

    /**
     * The five destinations as the rail wants them.
     *
     * Built here rather than imported because the destination-to-glyph mapping is private to
     * `AppChrome`, where `CoineProBottomBar` keeps it — and it is private for the reason that file
     * gives: `core:navigation` has no Compose dependency, so the glyphs cannot live on the enum.
     * The shell wiring builds the same list; see the note in the tablet work's report.
     */
    @Composable
    private fun railItems(): List<CoineProRailItem> = AppDestination.entries.map { destination ->
        CoineProRailItem(
            key = destination.route,
            label = stringResource(destination.labelRes),
            icon = destination.railIcon(selected = false),
            selectedIcon = destination.railIcon(selected = true),
        )
    }

    /** The same pairs `CoineProBottomBar` uses, since a reader must meet one glyph per tab. */
    @DrawableRes
    private fun AppDestination.railIcon(selected: Boolean): Int = when (this) {
        AppDestination.HOME -> if (selected) CoineProIcons.Filled.Home else CoineProIcons.Home
        AppDestination.SIGNALS -> if (selected) CoineProIcons.Filled.Signals else CoineProIcons.Signals
        AppDestination.AI -> if (selected) CoineProIcons.Filled.Ai else CoineProIcons.Ai
        AppDestination.EXPLORE ->
            if (selected) DesignR.drawable.icon_compass_fill else DesignR.drawable.icon_compass
        AppDestination.CHART -> if (selected) CoineProIcons.Filled.Chart else CoineProIcons.Chart
    }

    /** The wordmark at the head of the labelled rail: 240dp less the rail's own gutters. */
    private val RAIL_WORDMARK = 160.dp


    /**
     * The four screens that had no render case.
     *
     * Every feature module is a screen, and a screen nobody has looked at is a screen nobody knows
     * is broken — which is what the eight modules missing from this file were. The gate in
     * `check-cross-phase-consistency.py` now refuses a new one without a case here.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun journal() = capture("80-journal-fa") {
        JournalScreen(controller = ScreenshotFixtures.journalController(scope))
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun paperTrade() = capture("81-paper-trade-fa") {
        PaperTradeScreen(
            controller = ScreenshotFixtures.paperTradeController(scope),
            // The live price the position is marked against. Passed in, exactly as the real screen
            // takes it, so the open row shows a profit rather than an em dash.
            priceFor = { symbol -> if (symbol == "XAUUSD") 2_671.4 else null },
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun alerts() = capture("82-alerts-fa") {
        // The alert centre, not the old single-condition screen it replaced. Rendered because the
        // list row is where the condition becomes a Persian sentence with a Latin price inside it,
        // and a bidi run that comes out reversed is invisible to every assertion in this suite.
        AlertCenterScreen(
            controller = AlertsController(
                store = LocalAlertStore(FakeScreenshotPreferences()),
                audit = AlertAuditStore(FakeScreenshotPreferences()),
                catalogOf = { ScreenshotFixtures.alertSymbols() },
                scope = scope,
            ),
        )
    }

    /**
     * The chart page in the «طلایی» direction the owner picked.
     *
     * The render is the check on the two things that are easy to get wrong here and invisible in a
     * unit test: the gold rule under the heading has to fade towards the *far* edge in a
     * right-to-left page, and the forty-point price has to sit beside its percentage without either
     * clipping the other.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartPremium() = capture("76-chart-premium-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /** The same page with a setup drawn on it, so the R:R card is in the picture. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartPremiumSetup() = capture("77-chart-premium-setup-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        controller.toggleIndicator("ema")
        val series = ScreenshotFixtures.chartSeries()
        val at = { fraction: Double -> series.time[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        val price = { fraction: Double -> series.close[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        controller.onDrawing(
            DrawingState(
                drawings = listOf(
                    Drawing(
                        id = 1,
                        toolId = "longshort",
                        points = listOf(
                            ChartPoint(at(0.72), price(0.72)),
                            ChartPoint(at(0.92), price(0.72) * 0.995),
                        ),
                    ),
                ),
            ),
        )
        ChartScreen(controller = controller, onOpenStudio = {})
    }

    /**
     * The chart with the reader's **own open position** on it.
     *
     * The render is the check, and it is the only one that can be: the rule is "no green and no red
     * left of the candle the trade opened on", and no unit test can look at the pixels either side
     * of that candle.
     *
     * The entry is counted back from the **newest** bar rather than taken as a fraction of the
     * series, and the first version of this case got that wrong: the chart opens on roughly the last
     * seventy bars of two hundred, so an entry at 60% of the series was off the left of the plot and
     * the picture came back shaded edge to edge — the exact thing this is here to catch, rendered as
     * if nothing had been fixed. Off-screen is a real and correct case (`SetupSpan.entryX` is null
     * and the band still reaches the edge, because the position genuinely was open across every bar
     * shown), but it is not the case worth a screenshot.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartOpenPosition() = capture("99-chart-position-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        val series = ScreenshotFixtures.chartSeries()
        val index = (series.size - 25).coerceIn(0, series.size - 1)
        val entry = series.close[index]
        ChartScreen(
            controller = controller,
            onOpenStudio = {},
            position = PaperPosition(
                id = 1L,
                symbol = "XAUUSD",
                side = PaperSide.BUY,
                size = 0.25,
                entry = entry,
                // The book keeps milliseconds and the bars keep seconds. If that conversion is ever
                // wrong the zone lands several thousand years off the plot and this picture is bare.
                openedAtEpochMillis = series.time[index] * 1_000L,
                stopLoss = entry * 0.985,
                takeProfit = entry * 1.03,
            ),
        )
    }

    /**
     * The chart studio — the page the tool strip became.
     *
     * Every section closed but the one that matters on arrival, each carrying what it would say if
     * opened. That closed state is the whole argument for the page: a reader learns what is on
     * their chart without opening anything.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartStudio() = capture("78-chart-studio-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger", "rsi", "macd").forEach(controller::toggleIndicator)
        ChartStudioScreen(controller = controller, onOpenScript = {}, onBackToChart = {})
    }

    /**
     * The markets tab in the dense «ترمینال» direction.
     *
     * The row's three columns have to hold their places down the whole list — a ticker that pushed
     * the trend line sideways would make the column unreadable — and the sparkline has to be the
     * change's own colour rather than a fixed one.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun marketsTerminal() = capture("79-markets-fa") {
        MarketsScreen(
            controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                .also { it.start() },
            sparklines = ScreenshotFixtures.sparklineStore(scope),
            watchlist = listOf("BTCUSDT", "XAUUSD"),
            onOpenSymbol = {},
            onOpenSearch = {},
            openSignals = MarketsSignalStrip(
                count = 2,
                summary = "BTCUSDT خرید · XAUUSD خرید",
                onClick = {},
            ),
        )
    }

    /**
     * The oscillator panes.
     *
     * Until the pane renderer existed, switching on an RSI in the picker did nothing at all: the
     * option was in the catalogue, the arithmetic was in `Indicators`, and there was nowhere on the
     * canvas to put a second scale. This is the render that proves the join — three strips under
     * the price, each on its own extremes, with the reference levels inside them.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun chartPanes() = capture("73-chart-panes-fa") {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "rsi", "macd", "atr").forEach(controller::toggleIndicator)
        ChartScreen(controller = controller)
    }

    /**
     * The NamaScript studio, with a preset run.
     *
     * The render checks the thing that is easy to get wrong on this screen and invisible in a unit
     * test: the code field is laid out left-to-right inside a right-to-left page, so `close - atr *
     * 2` reads in the order it will run rather than mirrored.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun scriptStudio() = capture("74-script-studio-fa") {
        val series = ScreenshotFixtures.chartSeries()
        val controller = ScreenshotFixtures.scriptController(scope)
        controller.setSeries(series)
        controller.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
        ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
    }

    /** The same studio failing: a script with a stray bracket, and the caret's line and column. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun scriptFailure() = capture("75-script-failure-fa") {
        val series = ScreenshotFixtures.chartSeries()
        val controller = ScreenshotFixtures.scriptController(scope)
        controller.setSeries(series)
        controller.edit("fast = ta.ema(close, 9\nplot(fast)")
        ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
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
        assertEquals(85, expected.size)
    }

    private fun sheet(name: String, vararg groups: ToolGroup) {
        val tools = groups.flatMap { DrawingTools.inGroup(it) }
        val drawn = mutableMapOf<String, Boolean>()
        capture(name) { ToolGallery(tools = tools, onDrawn = { id, ok -> drawn[id] = ok }) }
        val missing = tools.map { it.id }.filterNot { drawn[it] == true }
        assertTrue("these tools drew nothing: $missing", missing.isEmpty())
    }


    /* ------------------------------------------------- explore and community */

    /**
     * Explore, on a viewport tall enough to reach the stories under the market strip.
     *
     * Everything on it comes from the fixtures the markets tab and the news screen already use —
     * `searchCatalog` for the cards, `sparklineStore` for the lines, `FakeMarketIntelGateway` for
     * the headlines — so a card that appeared here without artwork, or a headline that disagreed
     * with the one on the news screen, would be a difference this render shows rather than one a
     * reader finds.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xxhdpi")
    fun explorePersian() {
        val markets = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
        val intel = MarketIntelController(FakeMarketIntelGateway(), scope)
        markets.start()
        intel.refresh()
        capture("100-explore-fa") {
            ExploreScreen(
                controller = markets,
                intel = intel,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenNews = {},
                onOpenCalendar = {},
                // Every optional entry supplied, so the capture shows the widest arrangement: three
                // tiles rather than two, and the search affordance in the header.
                onOpenHeatmap = {},
                onOpenSearch = {},
                onOpenMarkets = {},
                onOpenStory = {},
            )
        }
    }

    /** The board with posts on it, which is the state the card design has to be legible in. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xxhdpi")
    fun communityPersian() {
        val controller = CommunityController(FakeCommunityGateway(), scope)
        controller.start()
        capture("101-community-fa") {
            CommunityScreen(
                controller = controller,
                onOpenThread = {},
                onSignIn = {},
                onOpenMembership = {},
            )
        }
    }

    /**
     * The tier refusal, which is the case worth having a picture of.
     *
     * A `403` from `require_vip` and a `401` from `current_student` are one line apart in the
     * server and have opposite buttons — «تهیهٔ اشتراک» and «ورود». A render of the locked state is
     * how a change that collapsed the two into one screen would be caught.
     */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun communityLockedPersian() {
        val locked = CommunityLockedException("این بخش ویژهٔ اعضای VIP است. برای دسترسی، اشتراک تهیه کنید.")
        val controller = CommunityController(FakeCommunityGateway(failure = locked), scope)
        controller.start()
        capture("102-community-locked-fa") {
            CommunityScreen(
                controller = controller,
                onOpenThread = {},
                onSignIn = {},
                onOpenMembership = {},
            )
        }
    }

    /** One thread: the post at full length, its replies, the crowned one, and the reply box. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xxhdpi")
    fun communityThreadPersian() {
        val controller = CommunityController(FakeCommunityGateway(), scope)
        capture("103-community-thread-fa") {
            CommunityThreadScreen(controller = controller, postId = 41L, onClose = {})
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
            // The fallback used to be the two constants that spell out this class's own
            // `w411dp-h914dp-xxhdpi`, and that was fine while every case in the file rendered at
            // that qualifier. It is not fine now: a case that overrides the qualifier to a tablet
            // and lands here would be measured at phone width, and the capture would be a phone
            // layout filed under a tablet name — a screenshot gate that passes by rendering the
            // wrong thing. The metrics come from the configuration the test is actually running
            // in, so the fallback follows whatever `@Config` asked for.
            val metrics = composeRule.activity.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(OUTPUT_DIR, "$name.png").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private companion object {
        val OUTPUT_DIR = File("build/screenshots")

        /**
         * The two tablet qualifiers every large-screen case in this file renders at.
         *
         * Named rather than typed out per case, because the point of them is that they are the
         * *same* two windows every time: a picture of the chart at 1280dp is only comparable with
         * last week's picture of the chart at 1280dp.
         *
         * `sw800dp` is on both because it is the qualifier Android itself uses to mean "a tablet",
         * so a resource bucket added later resolves in these renders exactly as it will on the
         * device. The density is `xhdpi` and not the phone cases' `xxhdpi`: a 1280dp window at 3x
         * is a 3840-pixel bitmap, which is thirty-seven megabytes per capture for no extra
         * information — real ten-inch tablets are near 2x.
         */
        const val TABLET_LANDSCAPE = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
        const val TABLET_PORTRAIT = "fa-rIR-ldrtl-sw800dp-w800dp-h1280dp-xhdpi"
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
            // A volume column, so the three volume tools draw something in the gallery rather than
            // correctly refusing. They return early on a feed that reports none — which is the right
            // behaviour on MT5 and the wrong thing to be reviewing a blank sheet for here.
            v = 400.0 + 260 * kotlin.math.sin(phase * Math.PI * 3),
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
    ToolGroup.VOLUME,
)

/**
 * Every mark, and every ring, in one grid.
 *
 * Two rows of five for the marks with the gold ring, then one row showing the same mark in each of
 * the six rings. That second row is the one that earns the sheet: a ring is only right if it works
 * on every colour the mark can be, and the two that carry their own colour — the bull and the bear
 * — have to still read as themselves inside a ring that disagrees with them.
 */
@Composable
private fun AvatarGallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "نشان‌های نیمرخ",
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        AvatarMark.entries.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { mark ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CoineProAvatar(
                            spec = AvatarSpec(AvatarBase.Mark(mark), AvatarRing.GOLD),
                            size = 62.dp,
                        )
                        Text(
                            text = mark.name.lowercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                        )
                    }
                }
            }
        }
        Text(
            text = "حلقه‌ها",
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        // 48dp, not 56: six of them at 56 with gaps overflow a 411dp screen, and the one that
        // overflows is squashed into a pill rather than clipped — which is what the first capture
        // showed, and is worth knowing about any avatar in a tight row.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarRing.entries.forEach { ring ->
                CoineProAvatar(
                    spec = AvatarSpec(AvatarBase.Mark(AvatarMark.DIAMOND), ring),
                    size = 48.dp,
                )
            }
        }
        Text(
            text = "بازارها و حرف اول",
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("BTC", "ETH", "XAUUSD", "EURUSD").forEach { symbol ->
                CoineProAvatar(
                    spec = AvatarSpec(AvatarBase.Symbol(symbol), AvatarRing.ANALYSIS),
                    size = 56.dp,
                )
            }
            CoineProAvatar(
                spec = AvatarSpec(AvatarBase.Initial, AvatarRing.GOLD),
                initial = "ب",
                size = 56.dp,
            )
        }
    }
}

/** A `DataStore` that holds its preferences in memory, for the screens that read one. */
private class FakeScreenshotPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}

/** Every construct the legal documents use, in the order the screen draws them. */
private val LEGAL_EXCERPT = """
    # شرایط استفاده — پرو چارت

    **آخرین بازنگری:** ۱۴۰۵/۰۶/۰۴

    > هرگز با سرمایه‌ای که توان از دست دادنش را ندارید معامله نکنید.

    ---

    ## ۲) ماهیت سرویس — ابزار تحلیل، نه توصیهٔ مالی

    پرو چارت **کارگزار نیست**، پول شما را نگه نمی‌دارد و مشاورهٔ مالی نمی‌دهد. شرح کامل در
    [سیاست حریم خصوصی](PRIVACY_POLICY.md) آمده است.

    * ما کارگزار نیستیم و پول یا دارایی شما را نگه نمی‌داریم.
    * خروجی هوش مصنوعی می‌تواند خطا داشته باشد و باید مستقل بازبینی شود.

    ### ۶-۲) شرایط عضویت

    ۱. در **LBank** یا **Ourbit** از طریق لینک اختصاصی ثبت‌نام کنید.
    ۲. موجودی حساب را حداقل به **۵۰ تتر (USDT)** برسانید.

    | داده | کجا | چرا |
    | --- | --- | --- |
    | توکن نشست | DataStore رمزنگاری‌شده با `AES-GCM` | شما را وارد نگه می‌دارد |
    | کش قیمت و سیگنال | Room | نمایش آخرین وضعیت وقتی شبکه نیست |

    ## ۱۰) تماس و پشتیبانی

    پشتیبانی در تلگرام: <https://t.me/CoinePro_Admin>
""".trimIndent()

/**
 * The board, without a server.
 *
 * Two posts rather than twenty: the render is checking the card, the strip and the composer, and a
 * page of twenty renders the same first screen as one of two. [failure] is what every call throws
 * instead, which is how the tier-locked capture is produced.
 */
private class FakeCommunityGateway(
    private val failure: Throwable? = null,
) : CommunityGateway {

    private val posts = listOf(
        CommunityPost(
            id = 41L,
            author = "رضا محمدی",
            content = "طلا از سقف کانال روزانه برگشت و الان روی ۲۶۴۰ حمایت دارد. تا وقتی این سطح " +
                "نگه داشته شود، سناریوی اصلی من ادامهٔ صعود تا ۲۶۹۰ است.",
            category = CommunityCategory.ANALYSIS,
            categoryLabel = "تحلیل",
            likes = 12,
            liked = true,
            replyCount = 2,
            reactions = mapOf("🔥" to 4, "👍" to 2),
            bestReplyId = 88L,
            createdAt = java.time.Instant.parse("2026-08-30T09:14:00Z"),
            pending = false,
        ),
        CommunityPost(
            id = 40L,
            author = "sara",
            content = "کسی با بروکر جدید کار کرده؟ اسپرد شب‌ها چطور است؟",
            category = CommunityCategory.QUESTION,
            categoryLabel = "سوال",
            likes = 0,
            liked = false,
            replyCount = 0,
            reactions = emptyMap(),
            bestReplyId = null,
            createdAt = java.time.Instant.parse("2026-08-30T08:02:11Z"),
            pending = false,
        ),
    )

    private fun <T> answer(value: T): T = failure?.let { throw it } ?: value

    override suspend fun feed(page: Int, category: CommunityCategory?): CommunityFeedPage =
        answer(CommunityFeedPage(posts = posts, page = page, received = posts.size))

    override suspend fun search(query: String): List<CommunityPost> = answer(posts)

    override suspend fun thread(id: Long): CommunityThread = answer(
        CommunityThread(
            post = posts.first(),
            replies = listOf(
                CommunityReply(
                    id = 87L,
                    author = "ali",
                    content = "به کانال روزانه هم نگاه کن، سقفش دقیقاً همان‌جاست.",
                    parentId = null,
                    best = false,
                    createdAt = java.time.Instant.parse("2026-08-30T09:20:00Z"),
                ),
                CommunityReply(
                    id = 88L,
                    author = "نگار",
                    content = "حمایت بعدی ۲۶۳۰ است؛ زیر آن سناریو باطل می‌شود.",
                    parentId = 87L,
                    best = true,
                    createdAt = java.time.Instant.parse("2026-08-30T09:31:00Z"),
                ),
            ),
        ),
    )

    override suspend fun post(content: String, category: CommunityCategory): CommunityWriteOutcome =
        answer(CommunityWriteOutcome(id = 42L, published = true, message = "منتشر شد."))

    override suspend fun reply(postId: Long, content: String, parentId: Long?): CommunityWriteOutcome =
        answer(CommunityWriteOutcome(id = null, published = true, message = null))

    override suspend fun like(postId: Long, currentLikes: Int): CommunityLikeOutcome =
        answer(CommunityLikeOutcome(likes = currentLikes + 1, liked = true))

    override suspend fun react(postId: Long, emoji: String): CommunityReactionOutcome =
        answer(CommunityReactionOutcome(counts = mapOf(emoji to 1), mine = setOf(emoji)))

    override suspend fun report(postId: Long) = answer(Unit)

    override suspend fun bestReply(postId: Long, replyId: Long) = answer(Unit)

    override suspend fun leaderboard(): CommunityLeaderboard =
        answer(CommunityLeaderboard(leaders = emptyList(), myRank = null, totalStudents = 0))
}
