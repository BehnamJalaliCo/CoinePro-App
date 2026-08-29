package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import com.coinepro.core.designsystem.R as DesignR
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A point on a drawing, stored in **chart space** — a moment and a price.
 *
 * Never in pixels. That is the single decision the whole drawing layer rests on: a trend line
 * anchored to Tuesday at 2,614 stays on Tuesday at 2,614 through every pan, zoom, chart-type switch
 * and rotation, and nothing in a tool has to be told that the view moved. It is the arrangement the
 * web terminal's tools were built on, and it is why they port without their maths changing.
 */
data class ChartPoint(val time: Long, val price: Double)

/**
 * What a drawing tool is.
 *
 * [points] is how many taps it takes to place one, and it is the tool's defining property rather
 * than a detail: a horizontal line needs one, a trend line two, a pitchfork three, an XABCD five.
 * The placement flow is driven entirely by this number.
 */
data class DrawingTool(
    val id: String,
    val label: String,
    /**
     * The «؟» entry id, or null for the two rail entries that are modes rather than drawings.
     *
     * Checked against the shipped catalogue by `DrawingToolsTest`.
     */
    val helpId: String?,
    val points: Int,
    val group: ToolGroup,
    @DrawableRes val icon: Int,
)

enum class ToolGroup(val label: String) {
    /** Not drawings: the pointer and the selection mode. They lead the rail because they are how a
     * reader gets *out* of a drawing tool, and a rail with no way back is a trap. */
    MODES("حالت"),
    LINES("خط‌ها"),
    CHANNELS("کانال‌ها"),
    FIBONACCI("فیبوناچی"),
    GANN("گن"),
    ELLIOTT("الیوت"),
    PATTERNS("الگوها"),
    SHAPES("شکل‌ها"),
    ANNOTATION("یادداشت"),
    MEASURE("اندازه‌گیری"),
    POSITION("موقعیت معاملاتی"),

    /**
     * The three tools that read volume rather than price.
     *
     * Their own group because they answer a different question from everything above: not «where
     * did price go» but «where did it trade». They are also the only tools that are meaningless on
     * a feed without a volume column — the MT5 forex feed reports none — so grouping them makes the
     * rail honest when that group is hidden.
     */
    VOLUME("حجم"),
}

/**
 * One placed drawing: which tool, where, and in what colours.
 *
 * Colours, plural, since the selection toolbar shipped: [colour] strokes it, [textColour] writes on
 * it and [fillColour] washes inside it, with the last two null until the reader says otherwise so
 * that every mark keeps following the one colour it was drawn in.
 */
data class Drawing(
    val id: Long,
    val toolId: String,
    val points: List<ChartPoint>,
    val colour: Long = DEFAULT_DRAWING_COLOUR,
    val widthDp: Float = 1.6f,
    /**
     * The colour a drawing's *words* are drawn in, or null to follow [colour].
     *
     * Three colours rather than one, because that is what the selection toolbar offers and what a
     * mark actually needs: a callout whose box is drawn in the reader's own gold is unreadable if
     * its text is gold too, and a rectangle marking a range wants a wash that is not the colour of
     * its own edge. One colour for all three forced every tool to derive the other two from it by
     * alpha, which is why every fill in this app is the line colour at ten percent.
     *
     * Null and not a copy of [colour], and that distinction is load-bearing: null means «follow the
     * line», so a reader who later recolours the drawing gets its text recoloured with it, and every
     * mark drawn before this field existed keeps looking exactly as it does now. A stored null is
     * the absence of a choice, which is not the same fact as a stored gold.
     */
    val textColour: Long? = null,
    /**
     * The colour washed inside a shape, a channel or a pattern, or null to follow [colour].
     *
     * Only the *hue* is the reader's. Every tool that fills picks its own alpha — six percent behind
     * a harmonic pattern, ten inside a rectangle, twelve under a risk band — and those numbers are
     * what keep a filled drawing from burying the candles it is drawn over. A fill colour that
     * arrived at full opacity would do exactly that, so [DrawingRenderer] swaps the hue and keeps
     * the tool's own transparency.
     */
    val fillColour: Long? = null,
    /**
     * Solid, dotted, dashed. See [LineStyleKind].
     *
     * The enum from `ChartPixels.kt` rather than a second one of this layer's own: [dashIntervals]
     * already derives every pattern from the stroke's own width, and two enums naming the same five
     * styles would drift the moment one of them gained a sixth.
     *
     * [LineStyleKind.SOLID] is the default and means «whatever the tool draws by default», which is
     * not always a solid line — a price label and a forecast are dashed by construction, and a
     * reader who has not chosen a style must keep seeing them that way.
     */
    val lineStyle: LineStyleKind = LineStyleKind.SOLID,
    /** Set while the reader is still tapping out the points. */
    val complete: Boolean = true,
    /**
     * Whether this drawing refuses to be moved, edited or deleted.
     *
     * A phone chart is a surface a reader pans, pinches and taps constantly, and every one of those
     * gestures passes through the drawings on it. Reviews of every app in this category carry the
     * same complaint — a trend line that took care to place, nudged out of position by a thumb that
     * was trying to scroll — and the fix everyone converges on is a per-object lock rather than a
     * global drawing mode.
     *
     * Locked affects *interaction only*, never rendering: a locked line is drawn exactly as it was
     * and is still selectable, so a reader can find it and unlock it. A lock that also hid the
     * handles would be a lock nobody could undo.
     */
    val locked: Boolean = false,
    /** What a text, callout, note or price label says. Null before the reader has typed anything. */
    val text: String? = null,
    /** Which way a standalone arrow marker points. Ignored by every other tool. */
    val direction: ArrowDirection = ArrowDirection.UP,
    /**
     * The interval this mark was drawn on — "H1", "D1" — or null when nothing said.
     *
     * A drawing's points are times and prices, so it renders on every interval; that is the whole
     * reason [ChartPoint] is not a bar index. What it does *not* say, and what two independent
     * readers of the web terminal asked for and never got, is which interval the reader was
     * looking at when they drew it. A trend line that was right on the daily is a different claim
     * from the same line drawn on the five-minute, and on a chart carrying twenty marks from three
     * sessions there is otherwise no way to tell them apart.
     *
     * Null rather than a default of the current interval, because "nothing said" is the truth about
     * every drawing saved before this field existed, and inventing an interval for one of those
     * would put a confident wrong label on somebody's old work.
     */
    val timeframe: String? = null,
    /** How far this drawing travels between layouts. See [DrawingSync]. */
    val sync: DrawingSync = DrawingSync.LAYOUT,
    /**
     * The `ChartLayout.id` the mark was placed under, or null for the plain working chart.
     *
     * Half of what [sync] is read against: a drawing set to [DrawingSync.LAYOUT] shows where this
     * matches the layout on screen, and a [DrawingSync.GLOBAL] one ignores it entirely.
     */
    val layoutId: String? = null,
    /**
     * How many standard deviations the regression channel's rails sit at.
     *
     * On the drawing rather than on the renderer, because it is a reader's choice about *one*
     * channel. It sat as a hard 2.0 for the life of the tool — `DrawingGeometryA.regressionChannel`
     * has taken the argument since it was written and the renderer never passed one — so two
     * channels on the same chart could not disagree, which is most of what somebody comparing a
     * one-sigma and a two-sigma channel is doing.
     *
     * Ignored by every tool but `regression`, and clamped by [DrawingActions.setDeviations] rather
     * than trusted: a value off a stored row is not a number this build wrote.
     */
    val deviations: Double = DEFAULT_DEVIATIONS,
    /**
     * When this mark stops being drawn, in epoch milliseconds, or null for a permanent one.
     *
     * What makes demonstration mode temporary. Stored as the *moment it dies* rather than as an age
     * plus a birthday, so nothing has to be ticked: every reader of it — the renderer's fade, the
     * visible filter, the reaper — asks the same question of the same clock and gets the same
     * answer, and a mark cannot half-expire because two places disagreed about how old it is.
     */
    val fadesAtMillis: Long? = null,
) {
    companion object {
        const val DEFAULT_DRAWING_COLOUR = 0xFFD8A848

        /**
         * The regression channel's rails, in standard deviations, before anybody chooses.
         *
         * Two, which is the value the web terminal shipped and the one every screenshot of this
         * tool was taken against. Named here rather than left as a literal in three places, because
         * this is now a value a reader can change and a default that drifted between the renderer
         * and the store would move somebody's channel on a restart.
         */
        const val DEFAULT_DEVIATIONS = 2.0
    }
}

/**
 * How far a drawing travels.
 *
 * Three states rather than a switch, and the middle one is the reason: readers do not want one
 * answer for every mark. A support level on gold belongs to gold and should be there whichever
 * layout is applied; a scribble made while thinking belongs to the chart it was made on and nowhere
 * else; and the ordinary case is between them — the marks that are part of *this* setup.
 *
 * This is a top-25 all-time request on the web terminal's own subreddit and it is still not
 * answered there, which is the whole reason it is modelled rather than assumed.
 */
enum class DrawingSync {
    /**
     * Stays on the chart it was drawn on and is not carried into a saved layout.
     *
     * The scratch setting. A mark at this setting is still shown while the reader is on the layout
     * it was made under — hiding it the moment it is placed would be a tool that does nothing —
     * but saving the layout leaves it behind.
     */
    NONE,

    /** Belongs to one layout: shown under that layout, and saved with it. The ordinary case. */
    LAYOUT,

    /** Shown under every layout for this symbol. What a permanent level on an instrument is. */
    GLOBAL,
    ;

    /**
     * Whether a mark at this setting is written into a layout when one is saved.
     *
     * The one place [NONE] and [LAYOUT] differ, and the reason [NONE] is not simply "hidden": both
     * are visible where they were drawn, and only one of them survives being filed.
     */
    val travels: Boolean get() = this != NONE
}

/**
 * The drawing tools, and the icons TradingView already draws them with.
 *
 * The web terminal's own set, plus what was written against TradingView's published inventory, plus
 * the six rail entries that are modes rather than drawings. Every icon here was already in this
 * repository — the `tv_tool_*` set converted earlier — and every one is a small picture of what the
 * tool produces, which is the only way a rail of this length is usable at all.
 *
 * `DrawingToolsTest` asserts that every tool has an icon that exists and a help entry that exists.
 */
object DrawingTools {

    /**
     * The two rail entries that are modes rather than drawings, and have no «؟».
     *
     * Declared before [ALL] and not after. A Kotlin object initialises its properties in source
     * order, so a set referenced by the list above it is still null when the list is built — which
     * fails as an `ExceptionInInitializerError` from every call site at once, naming nothing.
     */
    private val MODES_WITHOUT_HELP = setOf("cursor", "select", DEMONSTRATION_TOOL)

    val ALL: List<DrawingTool> = listOf(
        // ── Modes ───────────────────────────────────────────────────────────────────
        tool("cursor", "نشانگر", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_cursor),
        tool("select", "انتخاب", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_select),
        tool("arrowcursor", "نشانگر پیکانی", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_cursor),
        tool("dot", "نشانگر نقطه‌ای", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_dot),
        tool("magnet", "آهنربا", 0, ToolGroup.MODES, DesignR.drawable.tv_magnet),
        tool("eraser", "پاک‌کن", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_eraser),
        // The one mode that is not about what a tap does but about how long what it draws lasts.
        // It carries no «؟» yet: the shipped help catalogue has no entry keyed `demonstration`, and
        // pointing at a neighbouring one would open a page about a different thing.
        tool(DEMONSTRATION_TOOL, "نمایش موقت", 0, ToolGroup.MODES, DesignR.drawable.tv_tool_projection),

        // ── Lines ───────────────────────────────────────────────────────────────────
        tool("trend", "خط روند", 2, ToolGroup.LINES, DesignR.drawable.tv_tool_trend),
        tool("ray", "نیم‌خط", 2, ToolGroup.LINES, DesignR.drawable.tv_tool_ray),
        tool("extline", "خط امتدادیافته", 2, ToolGroup.LINES, DesignR.drawable.tv_tool_extline),
        tool("hray", "نیم‌خط افقی", 1, ToolGroup.LINES, DesignR.drawable.tv_tool_hray),
        tool("hline", "خط افقی", 1, ToolGroup.LINES, DesignR.drawable.tv_tool_hline),
        tool("vline", "خط عمودی", 1, ToolGroup.LINES, DesignR.drawable.tv_tool_vline),
        tool("crossline", "خط متقاطع", 1, ToolGroup.LINES, DesignR.drawable.tv_tool_crossline),
        tool("angle", "زاویه", 2, ToolGroup.LINES, DesignR.drawable.tv_tool_angle),
        tool("infoline", "خط اطلاعات", 2, ToolGroup.LINES, DesignR.drawable.tv_tool_infoline),

        // ── Channels ────────────────────────────────────────────────────────────────
        tool("channel", "کانال موازی", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_channel),
        tool("regression", "کانال رگرسیون", 2, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_regchannel),
        tool("flattop", "سقف/کف تخت", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_flatchannel),
        tool("disjoint", "کانال گسسته", 4, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_disjointchannel),
        // The four forks differ only in where the handle starts, and that is the entire reason to
        // ship four rather than one: classic anchors on the pivot itself, Schiff halves the price
        // toward the base's midpoint, modified Schiff halves both axes, inside takes the midpoint
        // of the first leg. Each «؟» states its own origin, because a rail of four identical
        // glyphs with four identical descriptions would be worse than offering only the classic.
        tool("pitchfork", "چنگال اندروز", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_pitchfork),
        tool("pitchfork_inside", "چنگال داخلی", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_insidepitchfork),
        tool("pitchfork_schiff", "چنگال شیف", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_schiff),
        tool("pitchfork_schiffmod", "چنگال شیف اصلاح‌شده", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_modschiff),
        tool("pitchfan", "بادبزن چنگال", 3, ToolGroup.CHANNELS, DesignR.drawable.tv_tool_pitchfan),

        // ── Fibonacci ───────────────────────────────────────────────────────────────
        tool("fib", "بازگشت فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fib),
        tool("fibext", "گسترش فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibext),
        tool("fib3", "فیبوناچی سه‌نقطه‌ای", 3, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fib3),
        tool("fibfan", "بادبزن فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibfan),
        tool("fibtime", "منطقهٔ زمانی فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibtime),
        tool("fibtimeext", "گسترش زمانی فیبوناچی", 3, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibtimeext),
        tool("fibchannel", "کانال فیبوناچی", 3, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibchannel),
        tool("fibcircles", "دایرهٔ فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibcircles),
        tool("fibarcs", "کمان فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibarcs),
        tool("fibspiral", "مارپیچ فیبوناچی", 2, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibspiral),
        tool("fibwedge", "گوه فیبوناچی", 3, ToolGroup.FIBONACCI, DesignR.drawable.tv_tool_fibwedge),

        // ── Gann ────────────────────────────────────────────────────────────────────
        tool("gannbox", "جعبهٔ گن", 2, ToolGroup.GANN, DesignR.drawable.tv_tool_gannbox),
        tool("gannfan", "بادبزن گن", 2, ToolGroup.GANN, DesignR.drawable.tv_tool_gannfan),
        tool("gannsquare", "مربع گن", 2, ToolGroup.GANN, DesignR.drawable.tv_tool_gannsquare),
        tool("gannsquarefixed", "مربع گن ثابت", 1, ToolGroup.GANN, DesignR.drawable.tv_tool_gannfixed),

        // ── Patterns ────────────────────────────────────────────────────────────────
        tool("xabcd", "الگوی XABCD", 5, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_xabcd),
        tool("abcd", "الگوی ABCD", 4, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_abcd),
        tool("cypher", "الگوی سایفر", 5, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_cypher),
        tool("tripattern", "الگوی مثلثی", 3, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_tripattern),
        tool("hns", "سر و شانه", 5, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_hns),
        tool("threedrives", "سه رانش", 5, ToolGroup.PATTERNS, DesignR.drawable.tv_tool_threedrives),

        // ── Elliott ─────────────────────────────────────────────────────────────────
        tool("ell_impulse", "موج ایمپالس", 6, ToolGroup.ELLIOTT, DesignR.drawable.tv_tool_ell_impulse),
        tool("ell_abc", "اصلاح ABC", 4, ToolGroup.ELLIOTT, DesignR.drawable.tv_tool_ell_abc),
        tool("ell_triangle", "مثلث الیوت", 5, ToolGroup.ELLIOTT, DesignR.drawable.tv_tool_ell_triangle),
        tool("ell_double", "ترکیب دوگانه", 5, ToolGroup.ELLIOTT, DesignR.drawable.tv_tool_ell_wxy),
        tool("ell_triple", "ترکیب سه‌گانه", 7, ToolGroup.ELLIOTT, DesignR.drawable.tv_tool_ell_wxyxz),

        // ── Shapes ──────────────────────────────────────────────────────────────────
        tool("triangle", "مثلث", 3, ToolGroup.SHAPES, DesignR.drawable.tv_tool_triangle),
        tool("rect", "مستطیل", 2, ToolGroup.SHAPES, DesignR.drawable.tv_tool_rect),
        tool("rotrect", "مستطیل چرخان", 3, ToolGroup.SHAPES, DesignR.drawable.tv_tool_rotrect),
        tool("circle", "دایره", 2, ToolGroup.SHAPES, DesignR.drawable.tv_tool_circle),
        tool("ellipse", "بیضی", 2, ToolGroup.SHAPES, DesignR.drawable.tv_tool_ellipse),
        tool("sine", "موج سینوسی", 2, ToolGroup.SHAPES, DesignR.drawable.tv_tool_sine),
        tool("brush", "قلم‌مو", 0, ToolGroup.SHAPES, DesignR.drawable.tv_tool_brush),
        tool("highlighter", "هایلایتر", 0, ToolGroup.SHAPES, DesignR.drawable.tv_tool_highlighter),
        tool("path", "مسیر", 0, ToolGroup.SHAPES, DesignR.drawable.tv_tool_path),
        tool("polyline", "خط شکسته", 0, ToolGroup.SHAPES, DesignR.drawable.tv_tool_polyline),
        tool("arc", "کمان", 3, ToolGroup.SHAPES, DesignR.drawable.tv_tool_arc),
        tool("curve", "منحنی", 3, ToolGroup.SHAPES, DesignR.drawable.tv_tool_curve),
        tool("doublecurve", "منحنی دوگانه", 4, ToolGroup.SHAPES, DesignR.drawable.tv_tool_doublecurve),
        tool("sector", "قطاع", 3, ToolGroup.SHAPES, DesignR.drawable.tv_tool_sector),

        // ── Position ────────────────────────────────────────────────────────────────
        tool("longshort", "موقعیت خرید/فروش", 2, ToolGroup.POSITION, DesignR.drawable.tv_tool_longshort),

        // ── Measure ─────────────────────────────────────────────────────────────────
        tool("pricerange", "دامنهٔ قیمت", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_pricerange),
        tool("daterange", "دامنهٔ زمان", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_daterange),
        tool("dprange", "دامنهٔ قیمت و زمان", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_dprange),
        tool("forecast", "پیش‌بینی", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_forecast),
        tool("ruler", "خط‌کش", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_ruler),
        tool("cyclic", "خطوط دوره‌ای", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_cyclic),
        tool("timecycles", "چرخه‌های زمانی", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_timecycles),
        tool("barspattern", "الگوی کندل‌ها", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_barspattern),
        tool("ghostfeed", "فید شبح", 2, ToolGroup.MEASURE, DesignR.drawable.tv_tool_ghostfeed),

        // ── Annotation ──────────────────────────────────────────────────────────────
        tool("arrow", "پیکان", 2, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_arrow),
        // Two taps for a one-point marker, and the second tap is the whole feature: it is not
        // another anchor, it is which way the arrow faces. `Drawing.direction` was stored,
        // persisted and rendered from the day the tool shipped and **nothing set it**, so every
        // «پیکان جهت‌دار» on every chart pointed up. `DrawingActions` collapses the pair back to
        // one point on commit, so what is stored is still a single marker.
        tool("arrowdir", "پیکان جهت‌دار", 2, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_arrowdir),
        tool("text", "متن", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_text),
        tool("callout", "بالن متن", 2, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_callout),
        tool("pricelabel", "برچسب قیمت", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_pricelabel),
        tool("note", "یادداشت", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_note),
        // Zero here means "the reader says when", the same as `path` and `polyline` — see
        // `DrawingActions.isVariablePoint`. It used to mean nothing at all: the registry said zero,
        // the tap handler refuses a zero-point tool, and `DrawingGeometryB.arrowMarks` needs two
        // anchors before it returns a single mark, so arming this tool and tapping placed nothing
        // and reported nothing.
        tool("arrowmarks", "علامت پیکانی", 0, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_arrowmarks),
        tool("pricenote", "یادداشت قیمت", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_pricenote),
        tool("pin", "سنجاق", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_note),
        // `tabledraw`, not `table`: the shipped help catalogue already keys `table` to the
        // scripting language's `table.new` primitive, which is a different thing entirely. A tool
        // pointing at that entry would open a page about writing a script.
        tool("tabledraw", "جدول", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_table),
        tool("comment", "دیدگاه", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_callout),
        tool("signpost", "تابلو", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_signpost),
        tool("icon", "آیکن", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_icon),
        tool("image", "تصویر", 1, ToolGroup.ANNOTATION, DesignR.drawable.tv_tool_image),

        // ── Volume ──────────────────────────────────────────────────────────────────
        tool("avwap", "VWAP لنگرانداخته", 1, ToolGroup.VOLUME, DesignR.drawable.tv_tool_avwap),
        tool("volumeprofile", "پروفایل حجم", 2, ToolGroup.VOLUME, DesignR.drawable.tv_tool_volumeprofile),
        tool("avolumeprofile", "پروفایل حجم لنگرانداخته", 1, ToolGroup.VOLUME, DesignR.drawable.tv_tool_avolumeprofile),
    )

    private val BY_ID: Map<String, DrawingTool> = ALL.associateBy { it.id }

    operator fun get(id: String): DrawingTool? = BY_ID[id]

    fun inGroup(group: ToolGroup): List<DrawingTool> = ALL.filter { it.group == group }

    /**
     * Tools whose Persian name or id contains what was typed.
     *
     * A plain substring match rather than the ranked matcher `core:symbols` uses for markets, and
     * deliberately: that one exists to order a thousand candidates, while this filters a rail
     * whose names a reader is choosing between by eye anyway. Ranking here would reorder the groups
     * out from under them for no gain.
     *
     * The id is searched as well as the label, so somebody who knows the tool as "fib" from the web
     * terminal finds it without switching keyboards.
     */
    fun matching(query: String): List<DrawingTool> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return ALL
        return ALL.filter { tool ->
            tool.label.contains(needle, ignoreCase = true) ||
                tool.id.contains(needle, ignoreCase = true)
        }
    }

    /** The groups that actually have tools, in the rail's order. */
    val GROUPS: List<ToolGroup> = ToolGroup.entries.filter { group -> ALL.any { it.group == group } }

    /**
     * The tool id *is* the help id.
     *
     * They agree because this list was taken from the web terminal's own rail rather than written
     * from the icon file names — which is how the first attempt produced twenty-seven tools whose
     * «؟» pointed at nothing. Two entries have no help and are marked [needsHelp] = false: the
     * cursor and the selection mode, which draw nothing and are not tools in that sense.
     */
    /**
     * The rail entry for demonstration mode.
     *
     * Named rather than spelled out, because three places have to agree on it — the catalogue row,
     * the set of modes with no «؟», and [DrawingMode.DEMONSTRATION] — and a mode whose id is
     * mistyped in one of them is a rail button that silently does nothing.
     */
    const val DEMONSTRATION_TOOL = "demonstration"

    private fun tool(
        id: String,
        label: String,
        points: Int,
        group: ToolGroup,
        @DrawableRes icon: Int,
    ) = DrawingTool(
        id = id,
        label = label,
        helpId = id.takeUnless { it in MODES_WITHOUT_HELP },
        points = points,
        group = group,
        icon = icon,
    )


}

/**
 * Which drawing a tap landed on, if any.
 *
 * Hit-testing happens in **screen space**, not chart space, and that is deliberate: a tolerance has
 * to be a finger's width, and a finger is a number of pixels rather than a number of dollars. At a
 * zoomed-out view a price-space tolerance would make a line impossible to grab; zoomed in it would
 * select three lines at once.
 */
object DrawingHitTest {

    /** How close a tap has to land. A finger pad is about 9dp across at the tip. */
    const val TOLERANCE_DP = 12f

    /**
     * The topmost drawing under a screen point, or null.
     *
     * Searched newest first, because a drawing placed on top of another is the one the reader means.
     */
    fun at(
        drawings: List<Drawing>,
        x: Float,
        y: Float,
        view: ChartViewport,
        tolerancePx: Float,
    ): Drawing? = drawings.lastOrNull { drawing ->
        distanceTo(drawing, x, y, view) <= tolerancePx
    }

    /** Distance in pixels from a screen point to a drawing's nearest edge. */
    fun distanceTo(drawing: Drawing, x: Float, y: Float, view: ChartViewport): Float {
        val screen = drawing.points.map { point ->
            view.xOfTime(point.time) to view.yOf(point.price)
        }
        if (screen.isEmpty()) return Float.MAX_VALUE
        if (screen.size == 1) {
            val (px, py) = screen[0]
            // A one-point tool is a level, not a dot: a horizontal line is grabbable anywhere along
            // it, which is the whole way across the plot.
            return when (DrawingTools[drawing.toolId]?.id) {
                "hline", "hray", "pricelabel" -> abs(y - py)
                "vline" -> abs(x - px)
                "crossline" -> minOf(abs(y - py), abs(x - px))
                else -> hypot(x - px, y - py)
            }
        }
        var nearest = Float.MAX_VALUE
        for (index in 0 until screen.size - 1) {
            val (ax, ay) = screen[index]
            val (bx, by) = screen[index + 1]
            nearest = minOf(nearest, distanceToSegment(x, y, ax, ay, bx, by))
        }
        return nearest
    }

    /** Perpendicular distance from a point to a line segment, clamped to the segment's ends. */
    internal fun distanceToSegment(
        x: Float,
        y: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(x - ax, y - ay)
        // How far along the segment the perpendicular foot falls, clamped so a tap beyond either
        // end measures to that end rather than to an imaginary extension of the line.
        val t = (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (ax + t * dx), y - (ay + t * dy))
    }
}
