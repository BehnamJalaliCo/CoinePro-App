package com.coinepro.app.pro

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.coinepro.app.AllTeachingDismissed
import com.coinepro.core.account.PaymentPlan
import com.coinepro.core.account.PaymentPlans
import com.coinepro.core.account.PaymentsGateway
import com.coinepro.core.account.ProPeriod
import com.coinepro.core.account.UsdtClaim
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pro is for sale (5.25.0): Cafe Bazaar on the phone, USDT on the site. The site's way is the one
 * built here, so it is the one walked end to end — plans from the server, the sheet, the hash, the
 * period.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-ldltr-w411dp-h914dp-420dpi")
class ProCheckoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakePayments : PaymentsGateway {
        val claims = mutableListOf<Pair<String, String>>()
        var period: ProPeriod? = null
        override suspend fun claimBazaar(plan: String, purchaseToken: String) = false
        override suspend fun plans() = PaymentPlans(
            plans = listOf(
                PaymentPlan("monthly", 30, 399_000, "15"),
                PaymentPlan("quarterly", 90, 999_000, "35"),
                PaymentPlan("yearly", 365, 1_999_000, "120"),
            ),
            usdtWallet = WALLET,
            usdtNetwork = "BEP20",
            bazaar = true,
        )
        override suspend fun current() = period
        override suspend fun claimUsdt(plan: String, txHash: String): UsdtClaim {
            claims += plan to txHash
            period = ProPeriod(plan, "2026-11-05T10:00:00+00:00")
            return UsdtClaim.Activated(period?.endsAt)
        }
    }

    private fun render(payments: PaymentsGateway, account: Boolean, onSignIn: (() -> Unit)? = null, store: ((String) -> Unit)? = null) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ProRoute(payments = payments, onBuyInStore = store, account = account, onSignIn = onSignIn)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the site sells in USDT and a verified hash turns Pro on`() {
        val payments = FakePayments()
        render(payments, account = true)

        composeRule.onNodeWithText("35 USDT").assertIsDisplayed()
        composeRule.onNodeWithTag("pro-buy-quarterly").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(USDT_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(WALLET).assertIsDisplayed()
        composeRule.onNodeWithTag(USDT_HASH_TAG).performTextInput(HASH)
        composeRule.onNodeWithTag(USDT_SUBMIT_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("quarterly" to HASH), payments.claims)
        assertTrue(composeRule.onAllNodesWithTag(USDT_SHEET_TAG).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Pro is active until 2026-11-05.").assertIsDisplayed()
    }

    @Test
    fun `a guest is sent to sign in, not to a payment that would land nowhere`() {
        var asked = 0
        render(FakePayments(), account = false, onSignIn = { asked++ })

        composeRule.onNodeWithTag("pro-buy-monthly").performClick()
        composeRule.waitForIdle()

        assertEquals(1, asked)
        assertTrue(composeRule.onAllNodesWithTag(USDT_SHEET_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the phone buys through the store, at the store's toman price`() {
        val bought = mutableListOf<String>()
        render(FakePayments(), account = true, store = { bought += it })

        composeRule.onNodeWithText("399,000 toman").assertIsDisplayed()
        composeRule.onNodeWithTag("pro-buy-yearly").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("yearly"), bought)
        assertTrue(composeRule.onAllNodesWithTag(USDT_SHEET_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a forex-only reader's buy links the crypto account and goes on to the checkout`() {
        var links = 0
        composeRule.setContent {
            var account by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ProRoute(
                            payments = FakePayments(),
                            onBuyInStore = null,
                            account = account,
                            onSignIn = null,
                            onLinkAccount = {
                                links++
                                account = true
                                null
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "Pro is held on a crypto account. If you have one with this email, Buy connects to it; if not, one is created — then the purchase continues.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("pro-buy-monthly").performClick()
        composeRule.waitForIdle()

        assertEquals(1, links)
        composeRule.onNodeWithTag(USDT_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun `a refused link says the server's sentence and opens nothing`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ProRoute(
                            payments = FakePayments(),
                            onBuyInStore = null,
                            account = false,
                            onSignIn = null,
                            onLinkAccount = { "Verify your email first." },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pro-buy-yearly").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Verify your email first.").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(USDT_SHEET_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a hash is checked for shape before it is sent`() {
        assertTrue(isTxHash(HASH))
        assertTrue(isTxHash(HASH.removePrefix("0x")))
        assertFalse(isTxHash("0x1234"))
        assertFalse(isTxHash(WALLET))
    }

    private companion object {
        const val WALLET = "0xaB828f7864A9d3a90acB12260CFeD5b6Ff0BE1E3"
        const val HASH = "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060"
    }
}
