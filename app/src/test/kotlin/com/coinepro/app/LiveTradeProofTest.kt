package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.execution.ExecutionGateway
import com.coinepro.core.execution.ExecutionUnsupportedException
import com.coinepro.core.execution.ExecutionVenue
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.LiveAccount
import com.coinepro.core.execution.LiveOrder
import com.coinepro.core.execution.LiveOrderRequest
import com.coinepro.core.execution.LiveOrderStatus
import com.coinepro.core.execution.LiveOrderType
import com.coinepro.core.execution.LivePosition
import com.coinepro.core.execution.LiveSide
import com.coinepro.core.execution.LiveTradeController
import com.coinepro.core.execution.LiveTradeGateway
import com.coinepro.core.execution.PositionSide
import com.coinepro.core.execution.ProtectionState
import com.coinepro.core.execution.SignalExecution
import com.coinepro.core.execution.VenueConnection
import com.coinepro.feature.chart.LiveTicketSeed
import com.coinepro.feature.chart.LiveTradeSheetBody
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live LBank ticket (5.18.0) as a frame: the real-money warning, the balance, a limit ticket
 * seeded from «order here» and reviewed, the position with its controls and the resting order.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveTradeProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h1400dp-xhdpi")
    fun theLiveTicketReviewsBeforeItSends() {
        val gateway = ProofLiveTrade()
        val controller = LiveTradeController(gateway, ProofVenue(), CoroutineScope(Dispatchers.Unconfined))
        controller.refreshAvailability()
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    ) {
                        LiveTradeSheetBody(
                            controller = controller,
                            symbol = "BTCUSDT",
                            livePrice = 60_000.0,
                            seed = LiveTicketSeed(LiveSide.BUY, LiveOrderType.LIMIT, 59_200.0),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("مقدار (BTC)").performTextInput("0.01")
        composeRule.onNodeWithText("بازبینی سفارش").performClick()
        composeRule.waitForIdle()
        capture("live-ticket-review-fa-dark")
        assertTrue("nothing is sent by the review", gateway.placed.isEmpty())
        composeRule.onNodeWithText("تأیید و ارسال به LBank").performClick()
        composeRule.waitForIdle()
        assertTrue("confirm sends exactly one order", gateway.placed.size == 1)
        capture("live-ticket-sent-fa-dark")
    }

    private fun capture(name: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val view = composeRule.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val metrics = composeRule.activity.resources.displayMetrics
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/proof").mkdirs()
        File("build/proof", "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private class ProofVenue : ExecutionGateway {
    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> =
        null to VenueConnection(ExecutionVenue.LBANK, configured = true, connected = true, status = "connected", lbankPermission = LbankPermission.FUTURES)
    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) = Unit
    override suspend fun disconnectMt5() = Unit
    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) = Unit
    override suspend fun disconnectLbank() = Unit
    override suspend fun executeSignal(signalId: Long, venue: ExecutionVenue, quantity: Double, clientRequestId: String): SignalExecution =
        throw ExecutionUnsupportedException()
    override suspend fun executions(limit: Int): List<SignalExecution> = emptyList()
    override suspend fun execution(executionId: String): SignalExecution = throw ExecutionUnsupportedException()
    override suspend fun requestClose(executionId: String): SignalExecution = throw ExecutionUnsupportedException()
}

private class ProofLiveTrade : LiveTradeGateway {
    val placed = mutableListOf<LiveOrderRequest>()
    private fun order(id: Long, request: LiveOrderRequest?, status: LiveOrderStatus) = LiveOrder(
        id = id, symbol = "BTCUSDT", side = request?.side ?: LiveSide.SELL, type = LiveOrderType.LIMIT,
        reduceOnly = false, quantity = request?.quantity ?: 0.02, price = request?.price ?: 61_500.0,
        stopLoss = null, takeProfit = null, status = status, filledQuantity = null, avgPrice = null,
        protection = ProtectionState.NONE, errorMessage = null, canCancel = true, canAmend = true, createdAt = null,
    )
    override suspend fun account() = LiveAccount(900.5, 1_004.2, 103.7, 3.2, true)
    override suspend fun positions(symbol: String?) =
        listOf(LivePosition("BTCUSDT", PositionSide.LONG, 0.05, 58_840.0, 60_000.0, 58.0, 10.0, 53_400.0))
    override suspend fun orders(limit: Int) =
        listOf(order(1, null, LiveOrderStatus.OPEN)) + placed.mapIndexed { i, r -> order(i + 2L, r, LiveOrderStatus.OPEN) }
    override suspend fun place(request: LiveOrderRequest): LiveOrder {
        placed += request
        return order(placed.size + 1L, request, LiveOrderStatus.OPEN)
    }
    override suspend fun amend(orderId: Long, price: Double) = order(orderId, null, LiveOrderStatus.OPEN)
    override suspend fun cancel(orderId: Long) = order(orderId, null, LiveOrderStatus.CANCELLED)
    override suspend fun protect(symbol: String, side: PositionSide, stopLoss: Double?, takeProfit: Double?) = Unit
    override suspend fun close(symbol: String, side: PositionSide, quantity: Double?, clientRequestId: String) =
        order(9, null, LiveOrderStatus.FILLED)
    override suspend fun setLeverage(symbol: String, leverage: Int) = Unit
}
