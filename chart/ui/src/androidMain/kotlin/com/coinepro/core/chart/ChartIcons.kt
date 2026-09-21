package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import com.coinepro.core.designsystem.R as DesignR

/**
 * The engine names its icons; this is where the names become drawables.
 *
 * `:chart-core` cannot see `R.drawable` — it has no platform — so a catalogue entry carries a
 * [ChartIcon] with the drawable's file name, and the Compose layer resolves it here, statically,
 * one `when` branch per name. Static rather than `getIdentifier` so the shrinker keeps every
 * icon the chart can ask for and a misspelt name is a build error in `ChartIconsTest`, not a
 * blank tile on a phone. Generated from the names in use; `ChartIconsTest` checks the two lists
 * still agree.
 */
object ChartIcons {
    /** The drawable behind [icon], or the candles glyph for a name this build does not carry. */
    @DrawableRes
    fun drawable(icon: ChartIcon): Int = when (icon.name) {
        "tv_chart_area" -> DesignR.drawable.tv_chart_area
        "tv_chart_bars" -> DesignR.drawable.tv_chart_bars
        "tv_chart_baseline" -> DesignR.drawable.tv_chart_baseline
        "tv_chart_candles" -> DesignR.drawable.tv_chart_candles
        "tv_chart_columns" -> DesignR.drawable.tv_chart_columns
        "tv_chart_footprint" -> DesignR.drawable.tv_chart_footprint
        "tv_chart_heikin" -> DesignR.drawable.tv_chart_heikin
        "tv_chart_hlcarea" -> DesignR.drawable.tv_chart_hlcarea
        "tv_chart_hollow" -> DesignR.drawable.tv_chart_hollow
        "tv_chart_kagi" -> DesignR.drawable.tv_chart_kagi
        "tv_chart_line" -> DesignR.drawable.tv_chart_line
        "tv_chart_linebreak" -> DesignR.drawable.tv_chart_linebreak
        "tv_chart_lwm" -> DesignR.drawable.tv_chart_lwm
        "tv_chart_pnf" -> DesignR.drawable.tv_chart_pnf
        "tv_chart_range" -> DesignR.drawable.tv_chart_range
        "tv_chart_renko" -> DesignR.drawable.tv_chart_renko
        "tv_chart_step" -> DesignR.drawable.tv_chart_step
        "tv_chart_tpo" -> DesignR.drawable.tv_chart_tpo
        "tv_chart_volcandles" -> DesignR.drawable.tv_chart_volcandles
        "tv_layout_grid" -> DesignR.drawable.tv_layout_grid
        "tv_magnet" -> DesignR.drawable.tv_magnet
        "tv_ruler" -> DesignR.drawable.tv_ruler
        "tv_tool_abcd" -> DesignR.drawable.tv_tool_abcd
        "tv_tool_angle" -> DesignR.drawable.tv_tool_angle
        "tv_tool_arc" -> DesignR.drawable.tv_tool_arc
        "tv_tool_arrow" -> DesignR.drawable.tv_tool_arrow
        "tv_tool_arrowdir" -> DesignR.drawable.tv_tool_arrowdir
        "tv_tool_arrowmarks" -> DesignR.drawable.tv_tool_arrowmarks
        "tv_tool_avolumeprofile" -> DesignR.drawable.tv_tool_avolumeprofile
        "tv_tool_avwap" -> DesignR.drawable.tv_tool_avwap
        "tv_tool_barspattern" -> DesignR.drawable.tv_tool_barspattern
        "tv_tool_brush" -> DesignR.drawable.tv_tool_brush
        "tv_tool_callout" -> DesignR.drawable.tv_tool_callout
        "tv_tool_channel" -> DesignR.drawable.tv_tool_channel
        "tv_tool_circle" -> DesignR.drawable.tv_tool_circle
        "tv_tool_crossline" -> DesignR.drawable.tv_tool_crossline
        "tv_tool_cursor" -> DesignR.drawable.tv_tool_cursor
        "tv_tool_curve" -> DesignR.drawable.tv_tool_curve
        "tv_tool_cyclic" -> DesignR.drawable.tv_tool_cyclic
        "tv_tool_cypher" -> DesignR.drawable.tv_tool_cypher
        "tv_tool_daterange" -> DesignR.drawable.tv_tool_daterange
        "tv_tool_disjointchannel" -> DesignR.drawable.tv_tool_disjointchannel
        "tv_tool_dot" -> DesignR.drawable.tv_tool_dot
        "tv_tool_doublecurve" -> DesignR.drawable.tv_tool_doublecurve
        "tv_tool_dprange" -> DesignR.drawable.tv_tool_dprange
        "tv_tool_ell_abc" -> DesignR.drawable.tv_tool_ell_abc
        "tv_tool_ell_impulse" -> DesignR.drawable.tv_tool_ell_impulse
        "tv_tool_ell_triangle" -> DesignR.drawable.tv_tool_ell_triangle
        "tv_tool_ell_wxy" -> DesignR.drawable.tv_tool_ell_wxy
        "tv_tool_ell_wxyxz" -> DesignR.drawable.tv_tool_ell_wxyxz
        "tv_tool_ellipse" -> DesignR.drawable.tv_tool_ellipse
        "tv_tool_eraser" -> DesignR.drawable.tv_tool_eraser
        "tv_tool_extline" -> DesignR.drawable.tv_tool_extline
        "tv_tool_fib" -> DesignR.drawable.tv_tool_fib
        "tv_tool_fib3" -> DesignR.drawable.tv_tool_fib3
        "tv_tool_fibarcs" -> DesignR.drawable.tv_tool_fibarcs
        "tv_tool_fibchannel" -> DesignR.drawable.tv_tool_fibchannel
        "tv_tool_fibcircles" -> DesignR.drawable.tv_tool_fibcircles
        "tv_tool_fibext" -> DesignR.drawable.tv_tool_fibext
        "tv_tool_fibfan" -> DesignR.drawable.tv_tool_fibfan
        "tv_tool_fibspiral" -> DesignR.drawable.tv_tool_fibspiral
        "tv_tool_fibtime" -> DesignR.drawable.tv_tool_fibtime
        "tv_tool_fibtimeext" -> DesignR.drawable.tv_tool_fibtimeext
        "tv_tool_fibwedge" -> DesignR.drawable.tv_tool_fibwedge
        "tv_tool_flatchannel" -> DesignR.drawable.tv_tool_flatchannel
        "tv_tool_forecast" -> DesignR.drawable.tv_tool_forecast
        "tv_tool_gannbox" -> DesignR.drawable.tv_tool_gannbox
        "tv_tool_gannfan" -> DesignR.drawable.tv_tool_gannfan
        "tv_tool_gannfixed" -> DesignR.drawable.tv_tool_gannfixed
        "tv_tool_gannsquare" -> DesignR.drawable.tv_tool_gannsquare
        "tv_tool_ghostfeed" -> DesignR.drawable.tv_tool_ghostfeed
        "tv_tool_highlighter" -> DesignR.drawable.tv_tool_highlighter
        "tv_tool_hline" -> DesignR.drawable.tv_tool_hline
        "tv_tool_hns" -> DesignR.drawable.tv_tool_hns
        "tv_tool_hray" -> DesignR.drawable.tv_tool_hray
        "tv_tool_icon" -> DesignR.drawable.tv_tool_icon
        "tv_tool_image" -> DesignR.drawable.tv_tool_image
        "tv_tool_infoline" -> DesignR.drawable.tv_tool_infoline
        "tv_tool_insidepitchfork" -> DesignR.drawable.tv_tool_insidepitchfork
        "tv_tool_longshort" -> DesignR.drawable.tv_tool_longshort
        "tv_tool_modschiff" -> DesignR.drawable.tv_tool_modschiff
        "tv_tool_note" -> DesignR.drawable.tv_tool_note
        "tv_tool_path" -> DesignR.drawable.tv_tool_path
        "tv_tool_pitchfan" -> DesignR.drawable.tv_tool_pitchfan
        "tv_tool_pitchfork" -> DesignR.drawable.tv_tool_pitchfork
        "tv_tool_polyline" -> DesignR.drawable.tv_tool_polyline
        "tv_tool_pricelabel" -> DesignR.drawable.tv_tool_pricelabel
        "tv_tool_pricenote" -> DesignR.drawable.tv_tool_pricenote
        "tv_tool_pricerange" -> DesignR.drawable.tv_tool_pricerange
        "tv_tool_projection" -> DesignR.drawable.tv_tool_projection
        "tv_tool_ray" -> DesignR.drawable.tv_tool_ray
        "tv_tool_rect" -> DesignR.drawable.tv_tool_rect
        "tv_tool_regchannel" -> DesignR.drawable.tv_tool_regchannel
        "tv_tool_rotrect" -> DesignR.drawable.tv_tool_rotrect
        "tv_tool_ruler" -> DesignR.drawable.tv_tool_ruler
        "tv_tool_schiff" -> DesignR.drawable.tv_tool_schiff
        "tv_tool_sector" -> DesignR.drawable.tv_tool_sector
        "tv_tool_select" -> DesignR.drawable.tv_tool_select
        "tv_tool_signpost" -> DesignR.drawable.tv_tool_signpost
        "tv_tool_sine" -> DesignR.drawable.tv_tool_sine
        "tv_tool_sync" -> DesignR.drawable.tv_tool_sync
        "tv_tool_table" -> DesignR.drawable.tv_tool_table
        "tv_tool_text" -> DesignR.drawable.tv_tool_text
        "tv_tool_threedrives" -> DesignR.drawable.tv_tool_threedrives
        "tv_tool_timecycles" -> DesignR.drawable.tv_tool_timecycles
        "tv_tool_trend" -> DesignR.drawable.tv_tool_trend
        "tv_tool_triangle" -> DesignR.drawable.tv_tool_triangle
        "tv_tool_tripattern" -> DesignR.drawable.tv_tool_tripattern
        "tv_tool_vline" -> DesignR.drawable.tv_tool_vline
        "tv_tool_volumeprofile" -> DesignR.drawable.tv_tool_volumeprofile
        "tv_tool_xabcd" -> DesignR.drawable.tv_tool_xabcd
        else -> DesignR.drawable.tv_chart_candles
    }

    /** Every name the map answers, for the test that keeps it and the engine in step. */
    val names: Set<String> = setOf(
        "tv_chart_area",
        "tv_chart_bars",
        "tv_chart_baseline",
        "tv_chart_candles",
        "tv_chart_columns",
        "tv_chart_footprint",
        "tv_chart_heikin",
        "tv_chart_hlcarea",
        "tv_chart_hollow",
        "tv_chart_kagi",
        "tv_chart_line",
        "tv_chart_linebreak",
        "tv_chart_lwm",
        "tv_chart_pnf",
        "tv_chart_range",
        "tv_chart_renko",
        "tv_chart_step",
        "tv_chart_tpo",
        "tv_chart_volcandles",
        "tv_layout_grid",
        "tv_magnet",
        "tv_ruler",
        "tv_tool_abcd",
        "tv_tool_angle",
        "tv_tool_arc",
        "tv_tool_arrow",
        "tv_tool_arrowdir",
        "tv_tool_arrowmarks",
        "tv_tool_avolumeprofile",
        "tv_tool_avwap",
        "tv_tool_barspattern",
        "tv_tool_brush",
        "tv_tool_callout",
        "tv_tool_channel",
        "tv_tool_circle",
        "tv_tool_crossline",
        "tv_tool_cursor",
        "tv_tool_curve",
        "tv_tool_cyclic",
        "tv_tool_cypher",
        "tv_tool_daterange",
        "tv_tool_disjointchannel",
        "tv_tool_dot",
        "tv_tool_doublecurve",
        "tv_tool_dprange",
        "tv_tool_ell_abc",
        "tv_tool_ell_impulse",
        "tv_tool_ell_triangle",
        "tv_tool_ell_wxy",
        "tv_tool_ell_wxyxz",
        "tv_tool_ellipse",
        "tv_tool_eraser",
        "tv_tool_extline",
        "tv_tool_fib",
        "tv_tool_fib3",
        "tv_tool_fibarcs",
        "tv_tool_fibchannel",
        "tv_tool_fibcircles",
        "tv_tool_fibext",
        "tv_tool_fibfan",
        "tv_tool_fibspiral",
        "tv_tool_fibtime",
        "tv_tool_fibtimeext",
        "tv_tool_fibwedge",
        "tv_tool_flatchannel",
        "tv_tool_forecast",
        "tv_tool_gannbox",
        "tv_tool_gannfan",
        "tv_tool_gannfixed",
        "tv_tool_gannsquare",
        "tv_tool_ghostfeed",
        "tv_tool_highlighter",
        "tv_tool_hline",
        "tv_tool_hns",
        "tv_tool_hray",
        "tv_tool_icon",
        "tv_tool_image",
        "tv_tool_infoline",
        "tv_tool_insidepitchfork",
        "tv_tool_longshort",
        "tv_tool_modschiff",
        "tv_tool_note",
        "tv_tool_path",
        "tv_tool_pitchfan",
        "tv_tool_pitchfork",
        "tv_tool_polyline",
        "tv_tool_pricelabel",
        "tv_tool_pricenote",
        "tv_tool_pricerange",
        "tv_tool_projection",
        "tv_tool_ray",
        "tv_tool_rect",
        "tv_tool_regchannel",
        "tv_tool_rotrect",
        "tv_tool_ruler",
        "tv_tool_schiff",
        "tv_tool_sector",
        "tv_tool_select",
        "tv_tool_signpost",
        "tv_tool_sine",
        "tv_tool_sync",
        "tv_tool_table",
        "tv_tool_text",
        "tv_tool_threedrives",
        "tv_tool_timecycles",
        "tv_tool_trend",
        "tv_tool_triangle",
        "tv_tool_tripattern",
        "tv_tool_vline",
        "tv_tool_volumeprofile",
        "tv_tool_xabcd",
    )
}

/** The drawable behind this icon, for a `painterResource` call. */
@DrawableRes
fun ChartIcon.drawableRes(): Int = ChartIcons.drawable(this)
