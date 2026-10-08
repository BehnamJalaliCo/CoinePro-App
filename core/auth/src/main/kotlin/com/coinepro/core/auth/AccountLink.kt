package com.coinepro.core.auth

import com.coinepro.core.common.AppResult

/**
 * One account across the two platforms, from the forex side (5.25.1).
 *
 * The two backends keep two user tables, so a reader who signed up on CoinePro-FX has no TradeYar
 * account: every crypto screen answers 401, and Pro — held on TradeYar — cannot be bought. TradeYar
 * answers `auth/link/coinepro` by asking CoinePro-FX whose bearer it was handed and opening the
 * account with the same verified email. Option B of `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`.
 *
 * Asked only when the reader presses for it — on the Pro page — never behind their back: it can
 * create an account, and nobody should find one they did not ask for.
 */
fun interface AccountLink {
    suspend fun fromCoinePro(coineproAccessToken: String): AppResult<EmailAuthSession>
}
