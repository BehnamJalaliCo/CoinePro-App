package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes

/**
 * The product's UI icon set.
 *
 * Phosphor Icons (MIT), converted from `design/ui-icons/phosphor-regular` by
 * `scripts/design/svg-to-vector.py`. Chosen rather than drawn here, and rather than lifted out of
 * another product, for three reasons that all point the same way: it is the family the Pro-Chart
 * web app already runs on, so the two products look related; every glyph in it is built on one
 * grid with one stroke weight, which is exactly what a hand-assembled set fails to give you the
 * moment a fifth icon lands beside four others; and it is a thousand-odd icons rather than the
 * dozen anyone would draw, so a screen never has to settle for the nearest available shape.
 *
 * The archive holds the whole family in both weights. Only the icons the app uses are converted,
 * so the APK carries thirty-eight vectors rather than three thousand, and adding one is a command:
 *
 *     python3 scripts/design/svg-to-vector.py --ui --set phosphor-regular --prefix icon_ <name>
 *
 * Every icon here is a tintable black silhouette — Android has no `currentColor` — so **every call
 * site must pass a tint**. An untinted one renders black on black.
 *
 * [Filled] is the same five navigation glyphs in Phosphor's fill weight, for the selected tab.
 * Weight is what marks the selection; the accent gold stays on the screen's primary action.
 */
object CoineProIcons {

    /* ---------------------------------------------------------------- navigation */

    /**
     * The bottom bar is the one place that does *not* use Phosphor.
     *
     * It is the first thing anyone sees and the only chrome on every screen, so it is drawn from the
     * exchanges' own sets — Bybit's house, OKX's candlesticks, sparkle, controls and bell — rather
     * than from a general-purpose family. They share a grid and a stroke weight with each other,
     * which is what makes five icons read as one bar; Phosphor still covers everything else, where
     * breadth matters more than this kind of specificity.
     *
     * Built by `scripts/design/build-nav-icons.py`; provenance is in `design/README.md`.
     */
    @DrawableRes val Home = R.drawable.nav_home
    @DrawableRes val Signals = R.drawable.nav_signals
    @DrawableRes val Ai = R.drawable.nav_ai
    @DrawableRes val Tools = R.drawable.nav_tools
    @DrawableRes val Activity = R.drawable.nav_activity

    /* ---------------------------------------------------------------- direction */

    /**
     * Auto-mirrored: these point along the reading direction, so in a right-to-left layout a back
     * arrow has to point right. Marked on the drawable rather than swapped at the call site, so no
     * screen can forget.
     */
    @DrawableRes val Back = R.drawable.icon_arrow_left
    @DrawableRes val ChevronBackward = R.drawable.icon_caret_left
    @DrawableRes val ChevronForward = R.drawable.icon_caret_right

    /* ---------------------------------------------------------------- actions */

    @DrawableRes val Refresh = R.drawable.icon_arrows_clockwise
    @DrawableRes val Close = R.drawable.icon_x
    @DrawableRes val Search = R.drawable.icon_magnifying_glass
    @DrawableRes val Settings = R.drawable.icon_gear_six
    @DrawableRes val Filter = R.drawable.icon_funnel
    @DrawableRes val Add = R.drawable.icon_plus
    @DrawableRes val Copy = R.drawable.icon_copy
    @DrawableRes val SignOut = R.drawable.icon_sign_out
    @DrawableRes val Camera = R.drawable.icon_camera
    @DrawableRes val Image = R.drawable.icon_image
    @DrawableRes val Link = R.drawable.icon_link_simple

    /* ---------------------------------------------------------------- state */

    @DrawableRes val Warning = R.drawable.icon_warning
    @DrawableRes val Success = R.drawable.icon_check_circle
    @DrawableRes val Info = R.drawable.icon_info
    @DrawableRes val Secure = R.drawable.icon_shield_check
    @DrawableRes val Locked = R.drawable.icon_lock_key
    @DrawableRes val Visible = R.drawable.icon_eye
    @DrawableRes val Pending = R.drawable.icon_clock

    /* ---------------------------------------------------------------- domain */

    @DrawableRes val Wallet = R.drawable.icon_wallet
    @DrawableRes val Calendar = R.drawable.icon_calendar_dots
    @DrawableRes val News = R.drawable.icon_newspaper
    @DrawableRes val TrendUp = R.drawable.icon_trend_up
    @DrawableRes val TrendDown = R.drawable.icon_trend_down
    @DrawableRes val Balance = R.drawable.icon_currency_circle_dollar
    @DrawableRes val Assistant = R.drawable.icon_robot

    /**
     * Other companies' marks, kept apart from the icon set on purpose.
     *
     * An icon in [CoineProIcons] is ours: it is tinted by whatever draws it, and its colour carries
     * this app's meaning. These are the opposite — they belong to somebody else, their colour is
     * part of the identity, and tinting one produces a mark that company does not have. Anything
     * drawing these must use `Image`, never `Icon`.
     */
    object Brand {
        @DrawableRes val LBank = R.drawable.logo_lbank
        @DrawableRes val Ourbit = R.drawable.logo_ourbit
    }

    /**
     * The navigation glyphs in the fill weight, for the selected tab.
     *
     * OKX publishes both weights of the sparkle. The other four are derived from their own outlines
     * by dropping the counters — see `build-nav-icons.py`, which explains why that is the same shape
     * the vendor would have drawn rather than an approximation of it.
     */
    object Filled {
        @DrawableRes val Home = R.drawable.nav_home_fill
        @DrawableRes val Signals = R.drawable.nav_signals_fill
        @DrawableRes val Ai = R.drawable.nav_ai_fill
        @DrawableRes val Tools = R.drawable.nav_tools_fill
        @DrawableRes val Activity = R.drawable.nav_activity_fill
    }
}
