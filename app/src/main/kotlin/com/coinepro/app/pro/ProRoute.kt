package com.coinepro.app.pro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.coinepro.app.R
import com.coinepro.core.account.PaymentPlans
import com.coinepro.core.account.PaymentsGateway
import com.coinepro.core.account.ProPeriod
import com.coinepro.core.common.FeatureFlags
import com.coinepro.core.common.proseDigits
import com.coinepro.core.designsystem.inEnglish
import kotlinx.coroutines.launch

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
 * @param onLinkAccount opens the TradeYar account for a reader signed in to the forex side only
 *   (5.25.1): null when it worked, otherwise the server's sentence or an empty string. Pressed by
 *   the reader's own «خرید» and said so on the page first, because it can create an account.
 */
@Composable
internal fun ProRoute(
    payments: PaymentsGateway?,
    onBuyInStore: ((String) -> Unit)?,
    account: Boolean,
    onSignIn: (() -> Unit)?,
    onLinkAccount: (suspend () -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    var linking by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
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
                onLinkAccount != null && !linking -> {
                    pending = plan
                    linking = true
                    linkError = null
                    scope.launch {
                        val failure = onLinkAccount()
                        linking = false
                        if (failure != null) {
                            pending = null
                            linkError = failure
                        }
                    }
                }
                onLinkAccount != null -> Unit
                else -> askAccount = true
            }
        }
    }
    // The link landed: the session flips `account`, and the purchase the reader pressed goes on.
    LaunchedEffect(account, pending) {
        val plan = pending ?: return@LaunchedEffect
        if (account && start != null) {
            pending = null
            start(plan)
        }
    }
    val linkFailed = stringResource(R.string.pro_link_failed)
    val unit = stringResource(R.string.pro_price_usdt_unit)
    // A price in a sentence, beside the table's «۱۶» and «۱۰۰» and the toman plans' «۳۹۹٬۰۰۰»:
    // Persian digits in Persian (5.25.1). The checkout keeps Latin, where it is pasted into a wallet.
    val english = inEnglish()
    val prices = if (usdt) {
        plans?.plans.orEmpty().mapNotNull { plan ->
            plan.priceUsdt?.let { price -> plan.id to "${price.toIntOrNull()?.proseDigits(english) ?: price} $unit" }
        }.toMap()
    } else {
        emptyMap()
    }

    ProScreen(
        onBuy = buy,
        activeUntil = period?.endsAt?.take(DATE_LENGTH),
        prices = prices,
        note = when {
            linking -> stringResource(R.string.pro_link_running)
            linkError != null -> linkError?.takeIf { it.isNotBlank() } ?: linkFailed
            !account && onLinkAccount != null && start != null -> stringResource(R.string.pro_link_explained)
            askAccount -> stringResource(R.string.usdt_sign_in)
            else -> null
        },
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
