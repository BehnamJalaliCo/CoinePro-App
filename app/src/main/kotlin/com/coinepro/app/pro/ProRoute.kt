package com.coinepro.app.pro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.coinepro.app.R
import com.coinepro.core.account.PaymentPlans
import com.coinepro.core.account.PaymentsGateway
import com.coinepro.core.account.ProPeriod
import com.coinepro.core.common.FeatureFlags

/**
 * The Pro page as the shell draws it (5.25.0): the plans and prices the server serves, the reader's
 * running period, and one way to buy — Cafe Bazaar where the build has it, USDT on the site.
 *
 * The subscription lives on the TradeYar account, one for both platforms, so buying needs that
 * session: a guest is sent to sign in, and a reader signed in to the forex platform alone is told
 * which account to add rather than handed a payment that would land nowhere.
 *
 * @param payments the payments gateway, or null where the build has none (previews, tests).
 * @param onBuyInStore starts a Cafe Bazaar purchase, or null where the build has no store billing —
 *   the site, which pays in USDT instead.
 * @param account whether the reader holds the account Pro is bought on.
 * @param onSignIn offered to a guest; null when the reader is signed in somewhere.
 */
@Composable
internal fun ProRoute(
    payments: PaymentsGateway?,
    onBuyInStore: ((String) -> Unit)?,
    account: Boolean,
    onSignIn: (() -> Unit)?,
) {
    var plans by remember { mutableStateOf<PaymentPlans?>(null) }
    var period by remember { mutableStateOf<ProPeriod?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var checkout by remember { mutableStateOf<String?>(null) }
    var askAccount by remember { mutableStateOf(false) }

    LaunchedEffect(payments, account, refresh) {
        if (payments == null) return@LaunchedEffect
        plans = payments.plans()
        period = if (account) payments.current() else null
    }

    val wallet = plans?.usdtWallet
    val usdt = onBuyInStore == null && wallet != null
    val start: ((String) -> Unit)? = when {
        !FeatureFlags.billingLive -> null
        onBuyInStore != null -> onBuyInStore
        usdt -> { plan -> checkout = plan }
        else -> null
    }
    val buy: ((String) -> Unit)? = start?.let { begin ->
        { plan ->
            when {
                account -> begin(plan)
                onSignIn != null -> onSignIn()
                else -> askAccount = true
            }
        }
    }
    val unit = stringResource(R.string.pro_price_usdt_unit)
    val prices = if (usdt) {
        plans?.plans.orEmpty().mapNotNull { plan -> plan.priceUsdt?.let { plan.id to "$it $unit" } }.toMap()
    } else {
        emptyMap()
    }

    ProScreen(
        onBuy = buy,
        activeUntil = period?.endsAt?.take(DATE_LENGTH),
        prices = prices,
        note = if (askAccount) stringResource(R.string.usdt_sign_in) else null,
    )

    val chosen = checkout
    if (chosen != null && payments != null && wallet != null) {
        val plan = PLANS.firstOrNull { it.id == chosen }
        val amount = plans?.plans?.firstOrNull { it.id == chosen }?.priceUsdt
        if (plan != null && amount != null) {
            UsdtCheckoutSheet(
                planTitle = stringResource(plan.title),
                plan = chosen,
                amount = amount,
                wallet = wallet,
                network = plans?.usdtNetwork ?: "BEP20",
                payments = payments,
                onDismiss = { checkout = null },
                onActivated = {
                    checkout = null
                    refresh++
                },
            )
        }
    }
}

/** `2026-11-05` out of the server's ISO instant. */
private const val DATE_LENGTH = 10
