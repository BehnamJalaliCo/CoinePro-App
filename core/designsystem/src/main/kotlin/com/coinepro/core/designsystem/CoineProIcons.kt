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

    @DrawableRes val Home = R.drawable.icon_house
    @DrawableRes val Signals = R.drawable.icon_chart_line_up
    @DrawableRes val Ai = R.drawable.icon_sparkle
    @DrawableRes val Tools = R.drawable.icon_sliders_horizontal
    @DrawableRes val Activity = R.drawable.icon_bell

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

    /** The navigation glyphs in the fill weight, for the selected tab. */
    object Filled {
        @DrawableRes val Home = R.drawable.icon_filled_house
        @DrawableRes val Signals = R.drawable.icon_filled_chart_line_up
        @DrawableRes val Ai = R.drawable.icon_filled_sparkle
        @DrawableRes val Tools = R.drawable.icon_filled_sliders_horizontal
        @DrawableRes val Activity = R.drawable.icon_filled_bell
    }
}
