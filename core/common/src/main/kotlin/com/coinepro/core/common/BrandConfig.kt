package com.coinepro.core.common

/**
 * The product's name and the few addresses that carry it — in one place.
 *
 * ### Why this exists
 *
 * The name was spelt five ways across the app: `Pro CHart` in twenty-nine strings (a capital H that
 * nobody chose), «پرو چارت» and «پروچارت» beside each other, `Pro-Chart` in the privacy policy, and
 * `CoinePro` in the package, the repository and the website. A reader cannot tell a product with
 * five names from five products. The spelling is decided here — the owner's decision, recorded —
 * and `scripts/quality/check-cross-phase-consistency.py` fails the build on any of the variants.
 *
 * ### What is and is not in it
 *
 * The display names, the deep-link scheme, the host that password-recovery links come from and the
 * base of the published legal pages: the things code builds addresses and sentences out of. The
 * manifest cannot read Kotlin, so the scheme and host are repeated there by hand; the gate above
 * checks that the two agree.
 *
 * `CoinePro` stays as the company and the repository, which is a different thing from the product's
 * name on the glass, and is why the package id is not in this file.
 */
object BrandConfig {
    /** The product's name in Latin script, as it appears on the sign-in screen and in every sentence. */
    const val DISPLAY_NAME = "Pro Chart"

    /** The same name in Persian. One spelling, with the space. */
    const val DISPLAY_NAME_FA = "پرو چارت"

    /** The URI scheme the app claims for its own links: `coinepro://signal/…`, `coinepro://market/…`. */
    const val SCHEME = "coinepro"

    /** The host password-recovery links arrive from. Declared in the manifest as an App Link too. */
    const val RESET_HOST = "coineprofx.com"

    /** Where the published terms, privacy policy and account-deletion page live. */
    const val LEGAL_BASE_URL = "https://behnamjalalico.github.io/CoinePro-App"

    /** Support, which is a Telegram channel and not an e-mail address. */
    const val SUPPORT_URL = "https://t.me/CoinePro_Admin"

    /** `coinepro://` — the scheme with its separator, for building a link. */
    const val SCHEME_PREFIX = "$SCHEME://"
}
