package com.coinepro.feature.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.model.MarketPlatform

/**
 * The five blocks of the menu, in the order they are always drawn.
 *
 * Declaration order **is** the layout order, and it is grouped the way somebody who trades thinks
 * about their day rather than the way this repository is laid out: first what they are watching,
 * then what they have open, then what they analyse with, then what they are learning, then who
 * they are. Nothing here follows a module boundary — the journal and paper trading are two
 * separate Gradle modules and one thought, and the chart studio and the script editor are two more.
 *
 * A reader who has learned that the account block is at the bottom should not have to learn again
 * because a screen was added to the middle, which is why this is an enum with a fixed order and
 * not a map assembled at the call site.
 */
enum class MenuGroup(@get:StringRes val titleRes: Int) {
    /** What the reader is watching: their own lists, and the ways to scan everything else. */
    MARKET(R.string.menu_group_market),

    /** What they have open, or are practising with, or have written down. */
    TRADE(R.string.menu_group_trade),

    /** What they analyse with — the chart's own surfaces, the alert centre, the calculators. */
    ANALYSIS(R.string.menu_group_analysis),

    /** Learning, and the app's own honesty page. */
    LEARN(R.string.menu_group_learn),

    /** Who this reader is: the account, what it is entitled to, and how to end it. */
    ACCOUNT(R.string.menu_group_account),
}

/**
 * One place this app can take a reader.
 *
 * @param id stable identity and **not a route**, for the same reason `AppSurface.id` is not one:
 *   three of these open a screen that needs a symbol in its path, and this module has no business
 *   knowing how the navigation graph is spelled or which market to pick. The ids are deliberately
 *   the same strings `AppSurfaces` already uses, so the shell resolves a menu tap and a search
 *   result through one function instead of two that can disagree.
 * @param bodyRes one short line under the name, and only where the name alone is not enough.
 *   «اسکرینر» needs it; «اخبار بازار» does not, and a note under every row would be a wall of grey
 *   text that stops being read by the third screenful.
 * @param platform the only backend that serves this, or null for both.
 * @param account whether the screen behind this needs somebody signed in. It is what makes a row
 *   *locked* rather than *absent* — see [MenuCatalogue.sections].
 * @param destructive the one row that cannot be undone. Drawn in the refusal colour, and last.
 */
data class MenuEntry(
    val id: String,
    @get:StringRes val titleRes: Int,
    @get:DrawableRes val icon: Int,
    val group: MenuGroup,
    @get:StringRes val bodyRes: Int? = null,
    val platform: MarketPlatform? = null,
    val account: Boolean = false,
    val destructive: Boolean = false,
)

/**
 * What this reader can actually reach.
 *
 * @param absent ids this build or this deployment does not offer at all — a server that reports no
 *   chart vision, a terminal with no configured URL, a backend with no account-deletion route.
 *   Distinct from [signedIn] because the two are answered differently and must be: an absent
 *   surface is **not drawn**, since there is nothing the reader can do about it and naming it would
 *   advertise a feature that does not exist here; an account-only surface **is** drawn, because
 *   signing in is two taps and the menu is where somebody finds out what those two taps buy.
 */
data class MenuAccess(
    val platform: MarketPlatform,
    val signedIn: Boolean,
    val absent: Set<String> = emptySet(),
)

/** One row, and whether this reader can open it. */
data class MenuItem(val entry: MenuEntry, val locked: Boolean)

/** One titled block with at least one row in it. A block with none is not drawn at all. */
data class MenuSection(val group: MenuGroup, val items: List<MenuItem>)

/**
 * Everything this app contains, written down in one place.
 *
 * ### Why this list exists
 *
 * The app has thirty-odd feature modules and five bottom-bar destinations. Everything between
 * those two numbers — the watchlist, the screener, the heat map, the journal, paper trading, the
 * backtest, the alert centre, the chart studio, the terminal, the academy, the portfolio,
 * connections, copy trading, activity, membership, verification, notification settings — was
 * reachable only by somebody who already knew which card on Home, which corner of which toolbar,
 * or which avatar to press. A search field finds a screen for a reader who knows its name. A menu
 * is for the reader who does not, and there was not one.
 *
 * ### What is deliberately not a row here
 *
 * * **The five bottom-bar destinations.** Home, Markets, Chart, Signals and AI are learned by
 *   position and are one tap away from every screen; repeating them here would make the menu a
 *   list where a quarter of the rows go where the reader already is.
 * * **Screens that only exist about something.** A signal's detail, an execution, one academy
 *   lesson, the chart of one symbol, the two-pane chart. Each needs an argument that only the
 *   screen it is opened from has, and a menu row for "the detail of which signal?" is not a
 *   destination.
 * * **The portfolio's report.** It is a reading *of* the portfolio, reached from it, and is named
 *   in that row's own line instead — a second row would be two entries for one account.
 * * **The order-book ladder.** Neither backend serves depth today, so its honest state is
 *   «هنوز سرو نمی‌شود». A menu row whose only content is an apology is worse than an absent one.
 * * **Diagnostics.** It is reached from the safety page and is for the owner, not the reader.
 */
object MenuCatalogue {

    val ALL: List<MenuEntry> = listOf(
        // ── بازار و دیده‌بان ──────────────────────────────────────────────────────────────────
        MenuEntry(
            id = "watchlist",
            titleRes = R.string.menu_watchlist_title,
            bodyRes = R.string.menu_watchlist_body,
            icon = DesignR.drawable.icon_star,
            group = MenuGroup.MARKET,
        ),
        MenuEntry(
            id = "search",
            titleRes = R.string.menu_search_title,
            bodyRes = R.string.menu_search_body,
            icon = CoineProIcons.Search,
            group = MenuGroup.MARKET,
        ),
        MenuEntry(
            id = "screener",
            titleRes = R.string.menu_screener_title,
            bodyRes = R.string.menu_screener_body,
            icon = CoineProIcons.Filter,
            group = MenuGroup.MARKET,
        ),
        MenuEntry(
            id = "heatmap",
            titleRes = R.string.menu_heatmap_title,
            bodyRes = R.string.menu_heatmap_body,
            icon = DesignR.drawable.tv_layout_grid,
            group = MenuGroup.MARKET,
        ),
        // The full list, which stopped being a tab when Explore took that position. Explore's own
        // «همهٔ بازارها» is the primary door; this is the second one, because a reader looking for a
        // screen they used yesterday looks in the menu.
        MenuEntry(
            id = "markets",
            titleRes = R.string.menu_markets_title,
            bodyRes = R.string.menu_markets_body,
            icon = CoineProIcons.Markets,
            group = MenuGroup.MARKET,
        ),
        MenuEntry(
            id = "explore",
            titleRes = R.string.menu_explore_title,
            bodyRes = R.string.menu_explore_body,
            icon = DesignR.drawable.icon_sparkle,
            group = MenuGroup.MARKET,
        ),
        // The one row in this block that carried no second line, so it sat as a bare title in a
        // column of two-line rows and read as unfinished. It also now has something worth saying:
        // when neither backend publishes, the app reads the wires itself.
        MenuEntry(
            id = "news",
            titleRes = R.string.menu_news_title,
            bodyRes = R.string.menu_news_body,
            icon = CoineProIcons.News,
            group = MenuGroup.MARKET,
        ),
        MenuEntry(
            id = "calendar",
            titleRes = R.string.menu_calendar_title,
            bodyRes = R.string.menu_calendar_body,
            icon = CoineProIcons.Calendar,
            group = MenuGroup.MARKET,
            // On both backends again, and open to a guest.
            //
            // It was made forex-only on the argument that TradeYar publishes no calendar route, so
            // the row led a crypto reader to a screen that could never fill. The argument was sound
            // and the conclusion was wrong: the answer to a room that cannot be filled from one
            // backend is not to lock the door, it is to fill it from somewhere else. The app now
            // reads the published week itself when neither server sends events — see
            // `PublicMarketIntel` — and a rate decision is *more* consequential for a leveraged
            // USDT pair than for a metal, not less. Nor does it need an account: the week's
            // economic calendar is public information and gating it taught a smaller app than the
            // one the reader installed.
        ),
        // ── معامله ───────────────────────────────────────────────────────────────────────────
        MenuEntry(
            id = "portfolio",
            titleRes = R.string.menu_portfolio_title,
            bodyRes = R.string.menu_portfolio_body,
            icon = CoineProIcons.Wallet,
            group = MenuGroup.TRADE,
            account = true,
        ),
        MenuEntry(
            id = "paper-trade",
            titleRes = R.string.menu_paper_title,
            bodyRes = R.string.menu_paper_body,
            icon = DesignR.drawable.tv_tool_longshort,
            group = MenuGroup.TRADE,
        ),
        MenuEntry(
            id = "journal",
            titleRes = R.string.menu_journal_title,
            bodyRes = R.string.menu_journal_body,
            icon = DesignR.drawable.tv_tool_note,
            group = MenuGroup.TRADE,
        ),
        MenuEntry(
            id = "connections",
            titleRes = R.string.menu_connections_title,
            bodyRes = R.string.menu_connections_body,
            icon = CoineProIcons.Link,
            group = MenuGroup.TRADE,
            account = true,
        ),
        // CoinePro-FX only. TradeYar places orders itself, so on that platform a copy-trading row
        // would lead to a screen that can only report the feature absent.
        MenuEntry(
            id = "copy-trade",
            titleRes = R.string.menu_copy_title,
            bodyRes = R.string.menu_copy_body,
            icon = CoineProIcons.Signals,
            group = MenuGroup.TRADE,
            platform = MarketPlatform.COINEPRO_FX,
            account = true,
        ),
        MenuEntry(
            id = "terminal",
            titleRes = R.string.menu_terminal_title,
            bodyRes = R.string.menu_terminal_body,
            icon = DesignR.drawable.tv_chart_candles,
            group = MenuGroup.TRADE,
            account = true,
        ),
        // ── تحلیل و ابزار ────────────────────────────────────────────────────────────────────
        MenuEntry(
            id = "chart-studio",
            titleRes = R.string.menu_studio_title,
            bodyRes = R.string.menu_studio_body,
            icon = DesignR.drawable.tv_pencil,
            group = MenuGroup.ANALYSIS,
        ),
        MenuEntry(
            id = "backtest",
            titleRes = R.string.menu_backtest_title,
            bodyRes = R.string.menu_backtest_body,
            icon = DesignR.drawable.tv_code2,
            group = MenuGroup.ANALYSIS,
        ),
        MenuEntry(
            id = "alerts",
            titleRes = R.string.menu_alerts_title,
            bodyRes = R.string.menu_alerts_body,
            icon = CoineProIcons.Bell,
            group = MenuGroup.ANALYSIS,
        ),
        MenuEntry(
            id = "tools",
            titleRes = R.string.menu_tools_title,
            bodyRes = R.string.menu_tools_body,
            icon = CoineProIcons.Tools,
            group = MenuGroup.ANALYSIS,
        ),
        MenuEntry(
            id = "ai-vision",
            titleRes = R.string.menu_vision_title,
            bodyRes = R.string.menu_vision_body,
            icon = CoineProIcons.Camera,
            group = MenuGroup.ANALYSIS,
            account = true,
        ),
        // **The two that left the bottom bar.**
        //
        // The AI studio and the signal list each had a tab of their own. Neither was removed and
        // neither should be hard to find: the studio is a screen somebody visits deliberately —
        // which is what a menu row is for — and signals is the first face of the Ideas tab as
        // well as a row here, because a reader who thinks of it by name should be able to look it
        // up by name. See `AppDestination`.
        MenuEntry(
            id = "ai",
            titleRes = R.string.menu_ai_title,
            bodyRes = R.string.menu_ai_body,
            icon = CoineProIcons.Ai,
            group = MenuGroup.ANALYSIS,
            account = true,
        ),
        MenuEntry(
            id = "signals",
            titleRes = R.string.menu_signals_title,
            bodyRes = R.string.menu_signals_body,
            icon = CoineProIcons.Signals,
            group = MenuGroup.ANALYSIS,
            account = true,
        ),
        MenuEntry(
            id = "ai-assistant",
            titleRes = R.string.menu_assistant_title,
            bodyRes = R.string.menu_assistant_body,
            icon = CoineProIcons.Assistant,
            group = MenuGroup.ANALYSIS,
            account = true,
        ),
        // ── آموزش و برنامه ───────────────────────────────────────────────────────────────────
        // CoinePro-FX only: TradeYar has no `/academy` surface at all.
        MenuEntry(
            id = "academy",
            titleRes = R.string.menu_academy_title,
            bodyRes = R.string.menu_academy_body,
            icon = CoineProIcons.Help,
            group = MenuGroup.LEARN,
            platform = MarketPlatform.COINEPRO_FX,
            account = true,
        ),
        // The app's own board, on both platforms and for a guest: it belongs to neither account.
        // Reading needs nothing; writing needs a display name the screen asks for itself.
        MenuEntry(
            id = "community",
            titleRes = R.string.menu_community_title,
            bodyRes = R.string.menu_community_body,
            icon = DesignR.drawable.brand_user,
            group = MenuGroup.LEARN,
        ),
        MenuEntry(
            id = "safety",
            titleRes = R.string.menu_safety_title,
            bodyRes = R.string.menu_safety_body,
            icon = CoineProIcons.Secure,
            group = MenuGroup.LEARN,
        ),
        // ── حساب من ──────────────────────────────────────────────────────────────────────────
        // **The screen that used to be the app's front door.**
        //
        // Its hero is a balance, its cards are a subscription and a set of shortcuts, and its
        // primary action is "generate a signal" — which makes it an account dashboard, and an
        // account dashboard is a thing somebody visits rather than the thing they open the app to
        // do. It kept every one of those cards and its route; what it lost is the first frame of
        // every launch. See `AppDestination`.
        MenuEntry(
            id = "home",
            titleRes = R.string.menu_home_title,
            bodyRes = R.string.menu_home_body,
            icon = CoineProIcons.Home,
            group = MenuGroup.ACCOUNT,
        ),
        MenuEntry(
            id = "profile",
            titleRes = R.string.menu_profile_title,
            bodyRes = R.string.menu_profile_body,
            icon = DesignR.drawable.tv_settings2,
            group = MenuGroup.ACCOUNT,
        ),
        MenuEntry(
            id = "membership",
            titleRes = R.string.menu_membership_title,
            bodyRes = R.string.menu_membership_body,
            icon = CoineProIcons.Balance,
            group = MenuGroup.ACCOUNT,
            account = true,
        ),
        MenuEntry(
            id = "verify",
            titleRes = R.string.menu_verify_title,
            bodyRes = R.string.menu_verify_body,
            icon = CoineProIcons.IdentityCard,
            group = MenuGroup.ACCOUNT,
            account = true,
        ),
        MenuEntry(
            id = "activity",
            titleRes = R.string.menu_activity_title,
            bodyRes = R.string.menu_activity_body,
            icon = CoineProIcons.Activity,
            group = MenuGroup.ACCOUNT,
            account = true,
        ),
        MenuEntry(
            id = "notifications",
            titleRes = R.string.menu_notifications_title,
            bodyRes = R.string.menu_notifications_body,
            icon = CoineProIcons.Settings,
            group = MenuGroup.ACCOUNT,
        ),
        // Last, and drawn in the refusal colour. The one row here that cannot be undone.
        MenuEntry(
            id = "delete",
            titleRes = R.string.menu_delete_title,
            icon = CoineProIcons.Delete,
            group = MenuGroup.ACCOUNT,
            account = true,
            destructive = true,
        ),
    )

    /**
     * The menu this reader gets.
     *
     * Three rules, and the middle one is the point of the whole screen:
     *
     * 1. A surface the **platform** does not serve, or that this deployment reports **absent**, is
     *    dropped. There is no route to send that reader down and no control on this screen that
     *    would change it, so naming it would be a dead end.
     * 2. A surface that needs an account is **drawn, and marked**, never hidden. A menu that hides
     *    half of itself from a guest teaches them that this is a smaller app than it is — and the
     *    thing it hides is precisely the argument for making an account.
     * 3. Group order is [MenuGroup]'s; row order within a group is this catalogue's. Neither is the
     *    caller's to change.
     */
    fun sections(
        access: MenuAccess,
        catalogue: List<MenuEntry> = ALL,
    ): List<MenuSection> {
        val reachable = catalogue.filter { entry ->
            (entry.platform == null || entry.platform == access.platform) && entry.id !in access.absent
        }
        return MenuGroup.entries.mapNotNull { group ->
            val items = reachable
                .filter { it.group == group }
                .map { MenuItem(entry = it, locked = it.account && !access.signedIn) }
            if (items.isEmpty()) null else MenuSection(group = group, items = items)
        }
    }

    /** How many rows this reader can open right now. The menu's header counts them out loud. */
    fun openCount(access: MenuAccess, catalogue: List<MenuEntry> = ALL): Int =
        sections(access, catalogue).sumOf { section -> section.items.count { !it.locked } }
}
