package com.coinepro.feature.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.TextRanking
import java.util.Locale

/**
 * One place in the app a search can land on.
 *
 * @param id stable identity, and **not a route**. Three of these open a screen that needs a symbol
 *   in its path — the studio, the script editor — and one is a tab inside another screen, so the
 *   app resolves an id to navigation rather than this module pretending to know how the graph is
 *   spelled. The ids are written down once, here, and never localised: they are what a caller
 *   matches on.
 * @param keywords everything a reader might type to mean this, in both languages at once rather
 *   than per locale. A Persian reader types «هیت مپ» and «heatmap» in the same session, often in
 *   the same minute, and a keyword list that followed the interface language would answer only one
 *   of them. Folded through [foldForSearch] before matching, so spacing, ZWNJ and Arabic letter
 *   forms cost nothing to get wrong.
 * @param platform the only platform that serves this, or null for both. See [AppSurfaceSearch] for
 *   why a mismatch hides the row rather than explaining it.
 * @param account whether the screen behind this needs a signed-in reader.
 */
data class AppSurface(
    val id: String,
    @get:StringRes val titleRes: Int,
    @get:StringRes val bodyRes: Int,
    @get:DrawableRes val icon: Int,
    val keywords: List<String>,
    val platform: MarketPlatform? = null,
    val account: Boolean = false,
)

/**
 * What this reader can actually reach, so a result is never an invitation to a wall.
 *
 * @param absent ids this build or this deployment does not offer at all — a server that reports no
 *   chart vision, a terminal with no configured URL. Distinct from [signedIn] because the two are
 *   answered differently: an absent surface is not drawn, since there is nothing the reader can do
 *   about it and naming it would be advertising a feature that does not exist here.
 */
data class SurfaceAccess(
    val platform: MarketPlatform,
    val signedIn: Boolean,
    val absent: Set<String> = emptySet(),
)

/**
 * One matched section, and whether the reader can open it.
 *
 * [locked] is true only ever for one reason — the surface needs an account and this reader has not
 * signed in — because that is the single kind of unavailability worth putting on screen. It is
 * something the reader can change from inside the app, in two taps, which is exactly what a row
 * that explains itself is for. Every other kind is filtered out by [AppSurfaceSearch.search].
 */
data class AppSurfaceMatch(val surface: AppSurface, val score: Int, val locked: Boolean)

/**
 * Everything this app can do, written down so it can be found by typing.
 *
 * ### Why this list exists
 *
 * The app has thirty-odd feature modules and five bottom-bar destinations. Between those two
 * numbers sits the academy, the journal, paper trading, the backtest, the screener, the heat map,
 * the toolbox, the economic calendar, the alert centre, the chart studio and the terminal — all
 * built, all shipping, and all reached only by somebody who already knew to look on the Home screen
 * or behind the chart's toolbar. Search is the cheapest discovery route there is: the reader is
 * already typing, and the answer costs no request and no screen.
 *
 * ### The order is the tie-break
 *
 * Two sections that match a query equally well are separated by their position here, the way
 * `SymbolRanking` separates two equally-matched markets by liquidity. So the order is deliberate:
 * the things a reader is most likely to be hunting for — the ones with no other entry point —
 * first, and the settings screens they can also reach from the profile corner last.
 *
 * ### What is deliberately not in it
 *
 * The order-book ladder. Neither backend serves depth today and the screen's honest state is
 * «هنوز سرو نمی‌شود», so a search result for «عمق بازار» would be a result whose only content is an
 * apology. It goes in the day the route answers.
 */
object AppSurfaces {

    val ALL: List<AppSurface> = listOf(
        AppSurface(
            id = "academy",
            titleRes = R.string.surface_academy_title,
            bodyRes = R.string.surface_academy_body,
            icon = CoineProIcons.Help,
            keywords = listOf(
                "آکادمی", "اکادمی", "آموزش", "اموزش", "درس", "دوره", "یادگیری",
                "academy", "learn", "lesson", "course", "education",
            ),
            // CoinePro-FX only: TradeYar has no `/academy` surface at all.
            platform = MarketPlatform.COINEPRO_FX,
            account = true,
        ),
        AppSurface(
            id = "journal",
            titleRes = R.string.surface_journal_title,
            bodyRes = R.string.surface_journal_body,
            icon = DesignR.drawable.tv_tool_note,
            keywords = listOf(
                "ژورنال", "دفترچه", "دفتر معاملات", "یادداشت معامله", "ثبت معامله",
                "journal", "diary", "trade log", "notes",
            ),
        ),
        AppSurface(
            id = "paper-trade",
            titleRes = R.string.surface_paper_title,
            bodyRes = R.string.surface_paper_body,
            icon = DesignR.drawable.tv_tool_longshort,
            keywords = listOf(
                "معامله آزمایشی", "معاملهٔ آزمایشی", "آزمایشی", "دمو", "تمرین", "شبیه‌ساز",
                "paper trading", "paper", "demo", "practice", "simulator",
            ),
        ),
        AppSurface(
            id = "backtest",
            titleRes = R.string.surface_backtest_title,
            bodyRes = R.string.surface_backtest_body,
            icon = DesignR.drawable.tv_code2,
            keywords = listOf(
                "بک تست", "بک‌تست", "بکتست", "اسکریپت", "استراتژی", "کد", "آزمون استراتژی",
                "backtest", "script", "strategy", "pine", "code",
            ),
        ),
        AppSurface(
            id = "screener",
            titleRes = R.string.surface_screener_title,
            bodyRes = R.string.surface_screener_body,
            icon = CoineProIcons.Filter,
            keywords = listOf(
                "اسکرینر", "غربال", "پویش بازار", "فیلتر بازار", "جست‌وجوی شرطی",
                "screener", "scanner", "scan", "filter",
            ),
        ),
        AppSurface(
            id = "heatmap",
            titleRes = R.string.surface_heatmap_title,
            bodyRes = R.string.surface_heatmap_body,
            icon = DesignR.drawable.tv_layout_grid,
            keywords = listOf(
                "هیت مپ", "هیت‌مپ", "هیتمپ", "نقشه حرارتی", "نقشهٔ حرارتی", "نقشه بازار",
                "heatmap", "heat map", "market map",
            ),
        ),
        AppSurface(
            id = "tools",
            titleRes = R.string.surface_tools_title,
            bodyRes = R.string.surface_tools_body,
            icon = CoineProIcons.Tools,
            keywords = listOf(
                "ابزار", "ابزارها", "جعبه ابزار", "ماشین حساب", "ماشین‌حساب", "ریسک",
                "حجم پوزیشن", "لات", "پیپ", "مرکب", "افت سرمایه",
                "tools", "toolkit", "calculator", "risk", "lot", "pip", "position size",
            ),
        ),
        AppSurface(
            id = "chart-studio",
            titleRes = R.string.surface_studio_title,
            bodyRes = R.string.surface_studio_body,
            icon = DesignR.drawable.tv_pencil,
            keywords = listOf(
                "استودیو", "استودیوی نمودار", "اندیکاتور", "ترسیم", "ابزار ترسیم", "فیبوناچی",
                "قالب نمودار", "studio", "indicators", "drawing", "fibonacci", "templates",
            ),
        ),
        AppSurface(
            id = "alerts",
            titleRes = R.string.surface_alerts_title,
            bodyRes = R.string.surface_alerts_body,
            icon = CoineProIcons.Bell,
            keywords = listOf(
                "هشدار", "هشدارها", "آلارم", "الارم", "زنگ", "اعلان قیمت", "وب‌هوک",
                "alert", "alerts", "alarm", "price alert", "webhook",
            ),
        ),
        AppSurface(
            id = "watchlist",
            titleRes = R.string.surface_watchlist_title,
            bodyRes = R.string.surface_watchlist_body,
            icon = DesignR.drawable.icon_star,
            keywords = listOf(
                "دیده‌بان", "دیده بان", "دیدهبان", "واچ لیست", "لیست دلخواه", "ستاره", "علاقه‌مندی",
                "watchlist", "favourites", "favorites", "starred",
            ),
        ),
        AppSurface(
            id = "news",
            titleRes = R.string.surface_news_title,
            bodyRes = R.string.surface_news_body,
            icon = CoineProIcons.News,
            keywords = listOf(
                "اخبار", "خبر", "تیتر", "خبرها", "news", "headlines", "feed",
            ),
        ),
        AppSurface(
            id = "calendar",
            titleRes = R.string.surface_calendar_title,
            bodyRes = R.string.surface_calendar_body,
            icon = CoineProIcons.Calendar,
            keywords = listOf(
                "تقویم", "تقویم اقتصادی", "رویداد", "داده اقتصادی", "نرخ بهره", "اشتغال",
                "calendar", "economic calendar", "events", "macro",
            ),
            account = true,
        ),
        AppSurface(
            id = "portfolio",
            titleRes = R.string.surface_portfolio_title,
            bodyRes = R.string.surface_portfolio_body,
            icon = CoineProIcons.Wallet,
            keywords = listOf(
                "پرتفوی", "پورتفولیو", "سبد", "عملکرد", "سود و زیان", "شارپ", "سورتینو",
                "افت سرمایه", "منحنی سرمایه",
                "portfolio", "performance", "pnl", "equity curve", "sharpe", "drawdown",
            ),
            account = true,
        ),
        AppSurface(
            id = "signals",
            titleRes = R.string.surface_signals_title,
            bodyRes = R.string.surface_signals_body,
            icon = CoineProIcons.Signals,
            keywords = listOf(
                "سیگنال", "سیگنال‌ها", "سیگنالها", "ورود و خروج", "حد ضرر",
                "signals", "trade ideas", "setups",
            ),
            account = true,
        ),
        AppSurface(
            id = "ai",
            titleRes = R.string.surface_ai_title,
            bodyRes = R.string.surface_ai_body,
            icon = CoineProIcons.Ai,
            keywords = listOf(
                "هوش مصنوعی", "ای آی", "تحلیل خودکار", "تولید سیگنال",
                "ai", "artificial intelligence", "generate signal",
            ),
            account = true,
        ),
        AppSurface(
            id = "ai-vision",
            titleRes = R.string.surface_vision_title,
            bodyRes = R.string.surface_vision_body,
            icon = CoineProIcons.Camera,
            keywords = listOf(
                "تحلیل تصویر", "عکس چارت", "اسکرین شات", "اسکرین‌شات", "تصویر نمودار",
                "vision", "screenshot", "chart image", "image analysis",
            ),
            account = true,
        ),
        AppSurface(
            id = "ai-assistant",
            titleRes = R.string.surface_assistant_title,
            bodyRes = R.string.surface_assistant_body,
            icon = CoineProIcons.Assistant,
            keywords = listOf(
                "دستیار", "چت", "پرسش", "سوال", "گفت‌وگو",
                "assistant", "chat", "ask", "bot",
            ),
            account = true,
        ),
        AppSurface(
            id = "terminal",
            titleRes = R.string.surface_terminal_title,
            bodyRes = R.string.surface_terminal_body,
            icon = DesignR.drawable.tv_chart_candles,
            keywords = listOf(
                "ترمینال", "ترمینال وب", "نسخه وب", "پرو چارت",
                "terminal", "web terminal", "pro chart", "desktop",
            ),
            account = true,
        ),
        AppSurface(
            id = "connections",
            titleRes = R.string.surface_connections_title,
            bodyRes = R.string.surface_connections_body,
            icon = CoineProIcons.Link,
            keywords = listOf(
                "اتصال", "اتصال‌ها", "اتصالها", "کارگزار", "بروکر", "صرافی", "کلید api",
                "کپی ترید", "کپی‌ترید", "اجرای سیگنال",
                "connections", "broker", "exchange", "api key", "copy trading",
            ),
            account = true,
        ),
        AppSurface(
            id = "activity",
            titleRes = R.string.surface_activity_title,
            bodyRes = R.string.surface_activity_body,
            icon = CoineProIcons.Activity,
            keywords = listOf(
                "فعالیت", "تاریخچه", "رویدادها", "گزارش فعالیت",
                "activity", "history", "log",
            ),
            account = true,
        ),
        AppSurface(
            id = "membership",
            titleRes = R.string.surface_membership_title,
            bodyRes = R.string.surface_membership_body,
            icon = CoineProIcons.Balance,
            keywords = listOf(
                "اشتراک", "عضویت", "پلن", "تمدید", "خرید اشتراک", "اشتراک ویژه",
                "membership", "subscription", "plan", "upgrade", "pro",
            ),
            account = true,
        ),
        AppSurface(
            id = "verify",
            titleRes = R.string.surface_verify_title,
            bodyRes = R.string.surface_verify_body,
            icon = CoineProIcons.IdentityCard,
            keywords = listOf(
                "احراز هویت", "کی وای سی", "مدارک", "تایید هویت",
                "kyc", "verify", "identity", "verification",
            ),
            account = true,
        ),
        AppSurface(
            id = "notifications",
            titleRes = R.string.surface_notifications_title,
            bodyRes = R.string.surface_notifications_body,
            icon = CoineProIcons.Settings,
            keywords = listOf(
                "اعلان", "اعلان‌ها", "نوتیفیکیشن", "پوش", "تنظیمات اعلان", "سکوت شبانه",
                "notifications", "push", "quiet hours",
            ),
        ),
        AppSurface(
            id = "profile",
            titleRes = R.string.surface_profile_title,
            bodyRes = R.string.surface_profile_body,
            icon = DesignR.drawable.tv_settings2,
            keywords = listOf(
                "پروفایل", "حساب کاربری", "تنظیمات", "پوسته", "تم", "زبان", "قفل", "امنیت",
                "profile", "settings", "account", "theme", "language", "security",
            ),
        ),
    )
}

/**
 * Ranked search over [AppSurfaces.ALL], merged into the same field the market search uses.
 *
 * ### Only a contiguous match counts
 *
 * A market row can afford a scattered match because it draws the matched span in the accent colour,
 * so a fuzzy hit explains itself on sight. A section row has no span to draw — its title is a
 * translated string and the query matched an untranslated keyword behind it — so a scattered hit
 * would appear as a suggestion with no visible reason, which reads as the app guessing. Contiguous
 * only, which is exactly what [TextHit.range] being non-null means.
 *
 * ### Two characters before anything is offered
 *
 * A single letter is a substring of half this catalogue, and a section list that reshuffles on the
 * first keystroke gets in the way of the market the reader is actually typing.
 */
object AppSurfaceSearch {

    /**
     * @param limit how many sections may appear above the markets. Four, because the section block
     *   is a detour from what the reader came here to do: long enough to hold the near-synonyms of
     *   one word, short enough that the first market is still on the first screen.
     */
    fun search(
        query: String,
        access: SurfaceAccess,
        catalogue: List<AppSurface> = AppSurfaces.ALL,
        limit: Int = DEFAULT_LIMIT,
    ): List<AppSurfaceMatch> {
        val needle = foldForSearch(query)
        if (needle.length < MIN_QUERY) return emptyList()
        return catalogue
            .asSequence()
            .withIndex()
            // A platform that does not serve this has no route to send the reader down, and no
            // control on this screen that would change that — the platform switch lives behind the
            // profile and only a member with both accounts has one. Naming the section anyway
            // would be telling somebody about a screen they cannot open, which is the dead end
            // this filter exists to prevent. A missing capability is dropped for the same reason.
            .filter { (_, surface) ->
                (surface.platform == null || surface.platform == access.platform) &&
                    surface.id !in access.absent
            }
            .mapNotNull { (position, surface) -> scored(surface, position, needle) }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.position })
            .take(limit)
            .map { AppSurfaceMatch(it.surface, it.score, locked = it.surface.account && !access.signedIn) }
            .toList()
    }

    private data class Ranked(val surface: AppSurface, val position: Int, val score: Int)

    /** The best of a section's keywords, or null when none of them matched contiguously. */
    private fun scored(surface: AppSurface, position: Int, needle: String): Ranked? {
        val best = surface.keywords
            .mapNotNull { keyword -> TextRanking.score(foldForSearch(keyword), needle) }
            .filter { it.range != null }
            .maxOfOrNull { it.score }
            ?: return null
        return Ranked(surface, position, best)
    }

    /** Below this a query is not yet a word. See the class note. */
    const val MIN_QUERY = 2

    private const val DEFAULT_LIMIT = 4
}

/**
 * Persian, reduced to the one spelling a keyword list can be written in.
 *
 * Every one of these is a real thing a reader types and a real thing that would otherwise miss:
 *
 * * **Arabic letter forms.** Most Persian keyboards on Android are fine, but a phone set up in
 *   Arabic, a pasted string or an older IME sends `ي` and `ك` where Persian uses `ی` and `ک` — and
 *   they are different code points, so «آكادمي» matches nothing at all against «آکادمی».
 * * **The zero-width non-joiner.** «دیده‌بان» and «دیده بان» and «دیدهبان» are one word typed three
 *   ways, and only one of them is the way the catalogue happens to spell it.
 * * **Spacing.** «هیت مپ» against `heat map`, and the same word run together.
 * * **Alef with hamza.** «آموزش» and «اموزش» differ by a mark nobody types deliberately.
 *
 * Dropping the separators means the folded string's indices no longer line up with the original,
 * which is why nothing here returns a span — the section row draws no highlight, so there is
 * nothing to line them up with. The market search deliberately does **not** fold, because its rows
 * do draw one and a shifted span underlines the wrong letters.
 */
internal fun foldForSearch(value: String): String {
    val builder = StringBuilder(value.length)
    for (character in value) {
        val folded = when (character) {
            'ي', 'ى' -> 'ی'
            'ك' -> 'ک'
            'ة' -> 'ه'
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ؤ' -> 'و'
            // The non-joiner, the ordinary space, the non-breaking space and the hyphen a reader
            // may or may not put between two halves of one name.
            '\u200C', ' ', '\u00A0', '-', '_' -> continue
            else -> character
        }
        builder.append(folded)
    }
    return builder.toString().lowercase(Locale.ROOT)
}
