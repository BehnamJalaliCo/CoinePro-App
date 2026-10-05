package com.coinepro.app.billing

import androidx.activity.ComponentActivity
import com.coinepro.app.BuildConfig
import com.coinepro.core.account.PaymentsGateway
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pro, bought through Cafe Bazaar (5.24.0). The phone only starts the purchase; the period exists
 * when the server has checked the token with the store and says so through `entitlements`.
 *
 * Android only, and excluded from the web build: the browser pays in USDT on the site.
 *
 * Poolakey checks the purchase's signature against [BuildConfig.BAZAAR_RSA_PUBLIC_KEY] before it
 * reports success, so a forged result never reaches [PaymentsGateway.claimBazaar] — and the server
 * would refuse one anyway.
 *
 * @param onResult called once per purchase with the outcome a reader should be told.
 */
class BazaarBilling(
    private val activity: ComponentActivity,
    private val payments: PaymentsGateway,
    private val scope: CoroutineScope,
    private val onResult: (BillingOutcome) -> Unit,
) {
    private val payment = Payment(
        context = activity,
        config = PaymentConfiguration(
            localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = BuildConfig.BAZAAR_RSA_PUBLIC_KEY),
        ),
    )
    private var connection: Connection? = null

    /** Subscribes to one plan (`monthly`, `quarterly`, `yearly`) — the store's product `pro_<plan>`. */
    fun buy(plan: String) {
        val start = {
            payment.subscribeProduct(
                registry = activity.activityResultRegistry,
                request = PurchaseRequest(productId = "pro_$plan"),
            ) {
                purchaseSucceed { info ->
                    scope.launch {
                        val claimed = payments.claimBazaar(plan, info.purchaseToken)
                        onResult(if (claimed) BillingOutcome.ACTIVATED else BillingOutcome.PENDING)
                    }
                }
                purchaseCanceled { onResult(BillingOutcome.CANCELLED) }
                purchaseFailed { onResult(BillingOutcome.FAILED) }
                failedToBeginFlow { onResult(BillingOutcome.UNAVAILABLE) }
            }
        }
        if (connection != null) {
            start()
            return
        }
        connection = payment.connect {
            connectionSucceed { start() }
            connectionFailed {
                connection = null
                onResult(BillingOutcome.UNAVAILABLE)
            }
            disconnected { connection = null }
        }
    }

    fun release() {
        connection?.disconnect()
        connection = null
    }
}

/** What a reader is told after a purchase. */
enum class BillingOutcome {
    /** The server verified it: Pro is on. */
    ACTIVATED,

    /** The store took the payment and the server has not confirmed it yet. */
    PENDING,
    CANCELLED,
    FAILED,

    /** Cafe Bazaar is not installed or would not start the purchase. */
    UNAVAILABLE,
}
