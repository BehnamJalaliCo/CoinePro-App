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

    /**
     * The brand's own host: the website, the legal pages, and — once the web terminal exists — the
     * terminal itself. Owned by the owner, registered, and **not yet serving** (see
     * `docs/release/DOMAINS.md` for what has to be put there and in what order). The API hosts are
     * not here: they are deployment addresses, set per build in `app/build.gradle.kts`, and stay on
     * the backends' own domains until each backend moves.
     */
    const val WEB_HOST = "pro-chart.com"

    /** `https://pro-chart.com` — the site, for building a link. No trailing slash. */
    const val WEB_URL = "https://$WEB_HOST"

    /**
     * The host password-recovery links arrive from, for the one backend that mails a link rather
     * than a code. This is CoinePro-FX's own host, not the brand's: the link is whatever that
     * server puts in its e-mail, and the app can only claim the host the e-mail actually names.
     * Declared in the manifest as an App Link too. Moves to [WEB_HOST] when that server does.
     */
    const val RESET_HOST = "coineprofx.com"

    /** The recovery path on the brand host, claimed now so the day the e-mail changes needs no release. */
    const val WEB_RESET_PATH = "reset"

    /** Where the published terms, privacy policy and account-deletion page live. */
    const val LEGAL_BASE_URL = "$WEB_URL/legal"

    /** Support, which is a Telegram channel and not an e-mail address. */
    const val SUPPORT_URL = "https://t.me/CoinePro_Admin"

    /** `coinepro://` — the scheme with its separator, for building a link. */
    const val SCHEME_PREFIX = "$SCHEME://"
}
