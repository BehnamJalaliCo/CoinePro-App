#!/usr/bin/env python3
"""Deterministic Phase 1-17 cross-phase consistency gate.

This gate checks repository-owned invariants only. It intentionally does not turn
missing external legal/provider/production evidence into a pass.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def fail(message: str) -> None:
    raise SystemExit(f"CROSS_PHASE_CONSISTENCY_ERROR: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def settings_modules() -> list[str]:
    text = read("settings.gradle.kts")
    return [match.lstrip(":") for match in re.findall(r'include\("(:[^\"]+)"\)', text)]


def roadmap_modules() -> list[str]:
    text = read("docs/PRODUCT_ROADMAP.md")
    heading = "## Product module map"
    require(heading in text, "PRODUCT_ROADMAP is missing Product module map")
    tail = text.split(heading, 1)[1]
    match = re.search(r"```text\n(.*?)\n```", tail, flags=re.DOTALL)
    require(match is not None, "Product module map must be a fenced text block")
    return [line.strip() for line in match.group(1).splitlines() if line.strip()]


def check_module_map() -> None:
    actual = settings_modules()
    documented = roadmap_modules()
    require(actual == documented, f"Gradle modules != roadmap module map\nactual={actual}\ndocumented={documented}")


def check_every_screen_is_rendered() -> None:
    """Every feature module must have a case in the screenshot render test.

    A feature module is a screen, and a screen nobody has looked at is a screen nobody knows is
    broken. Eight of them had no case, which is how a toolbar two hundred and sixty points tall
    shipped: the render existed for the chart but not for what sat under it.

    Modules that are deliberately not renderable are listed with the reason. The list is short and
    every entry has to earn its place — "it is hard to fake" is not one, since a fake gateway is
    what every other case here uses.
    """
    unrenderable = {
        # A WebView. Robolectric has no renderer for one, so an off-device capture is a white
        # rectangle that would pass this gate while proving nothing.
        "terminal",
        # Both AI screens stream. A capture is one frame of an animation, and which frame it is
        # depends on the scheduler — the picture would change between runs without the code
        # changing, which is a screenshot test that cries wolf.
        "ai-vision",
        "ai-assistant",
        # Two screens whose whole content is a server's answer about one account. Rendering them
        # means fabricating a membership or an execution, and a picture of an invented account
        # state is a picture of something that cannot happen.
        "membership",
        "execution",
    }
    modules = sorted(
        path.name
        for path in (ROOT / "feature").iterdir()
        if path.is_dir() and (path / "build.gradle.kts").exists()
    )
    rendered = read("app/src/test/kotlin/com/coinepro/app/ScreenshotRenderTest.kt")
    missing = [
        module
        for module in modules
        if module not in unrenderable
        and f"com.coinepro.feature.{module.replace('-', '')}." not in rendered
    ]
    require(
        not missing,
        "Feature modules with no screenshot render case: "
        + ", ".join(missing)
        + "\nAdd one to ScreenshotRenderTest, or list the module in this gate with the reason.",
    )


def check_bottom_navigation() -> None:
    """Bottom navigation identity and order are repository-owned; the labels are not.

    Display labels moved to string resources when Persian became the default language, so asserting
    them here would assert one translation. Route names stay constant because deep links, saved
    back-stack state and server payloads all key off them.
    """
    text = read("core/navigation/src/main/kotlin/com/coinepro/core/navigation/AppDestination.kt")
    entries = re.findall(r'^\s*([A-Z]+)\("([^"]+)",', text, flags=re.MULTILINE)
    expected = [
        # **The bar is a list of jobs, not a list of modules.** It held six — Home, Explore, Chart,
        # Signals, AI and Community — and every one of them was a real screen, which is exactly how
        # a bar becomes a feature catalogue. See `AppDestination` for the whole argument.
        #
        # The watchlist first, because it is where somebody lands when they have no other
        # question, and because a curated list two taps down was the clearest symptom of a shell
        # built around a dashboard. Same route it already had.
        ("WATCHLIST", "watchlist"),
        # Not "chart": that route belongs to the chart *of a symbol* and has for every release so
        # far. The tab is a different destination that redirects into it, and giving the two the
        # same name would break every saved back stack that holds one.
        ("CHART", "chart-tab"),
        # Explore took this position from MARKETS: it is the same catalogue with the day's move,
        # a spark line and the news, calendar and heat-map doors on it, and the full list is one
        # tap away from it.
        ("EXPLORE", "explore"),
        # Signals and the board, which are two answers to one question and had a tab each. A route
        # of its own rather than a redirect: `signals` and `community` are still routes, and a
        # saved back stack naming one must open that screen alone rather than a tabbed page.
        ("IDEAS", "ideas"),
        # The directory, and the pressure valve that stops this list from growing a sixth entry
        # the next time a feature ships. Same route the menu already had.
        ("MENU", "menu"),
    ]
    require(entries == expected, f"Bottom navigation contract drifted: {entries}")

    for language, directory in (("Persian", "values"), ("English", "values-en")):
        labels = read(f"core/navigation/src/main/res/{directory}/strings.xml")
        for destination, _ in expected:
            key = f'name="nav_{destination.lower()}"'
            require(key in labels, f"{language} bottom-navigation label missing: {key}")


def check_learned_surfaces() -> None:
    """The three surfaces a reader learns by position, pinned the way the bottom nav is — item 158.

    The bottom navigation has been pinned here since the first release, on the reading that a person
    who has learned where a thing is should not have to learn again. The same reading applies to the
    chart, and did not reach it: the toolbar under the plot, the drawing rail's groups and the
    studio's sections were all free to be reordered by any change that touched them, and a reader
    who reaches for «ابزارها» in the fourth position and gets the backtest has lost the muscle
    memory this gate exists to protect.

    Identity and order only, and never the labels. Labels are Persian prose, they are edited, and
    asserting one here would assert one translation — the same reason
    :func:`check_bottom_navigation` stops at the route names.

    Adding to the end of any of these is an ordinary change: extend the list here in the same
    commit. Reordering or renaming is the thing that has to be deliberate, which is what a failure
    from this gate is asking for.
    """
    sheets = read("feature/chart/src/main/kotlin/com/coinepro/feature/chart/ChartScreen.kt")
    match = re.search(r"enum class ChartSheet \{ ([^}]+) \}", sheets)
    require(match is not None, "ChartSheet is no longer a single-line enum; this gate cannot read it.")
    entries = [name.strip() for name in match.group(1).split(",")]
    expected_sheets = [
        "TYPE",
        "INDICATORS",
        "TOOLS",
        "DRAWINGS",
        "SETUP",
        "BACKTEST",
        "LAYOUTS",
        "INTERVAL",
        "SCALE",
        "COMPARE",
        # The marks on the time axis, given somewhere to be listed. A glyph on an axis can say that
        # something happened at a moment and cannot say what; this is the sheet that answers the
        # tap, and it sits before MORE because it is the newest entry and this list is ordered by
        # the enum rather than by importance.
        "EVENTS",
        # Added when the chart's six control bands became one: everything a reader touches monthly
        # rather than every session moved behind this one entry. See `ChartMoreSheetBody`.
        "MORE",
        # «معامله با کارگزار». The hub's trade card used to jump straight into this app's own
        # terminal; it now opens the list of venues a reader can actually put the trade on, with
        # the in-app route at the top of it. See `TradePartnersSheetBody`.
        "PARTNERS",
    ]
    require(
        entries == expected_sheets,
        f"The chart toolbar's sheets drifted: {entries}\nExpected {expected_sheets}.",
    )

    rail = read("core/chart/src/main/kotlin/com/coinepro/core/chart/Drawings.kt")
    groups = re.findall(r'^\s{4}([A-Z_]+)\("', rail, flags=re.MULTILINE)
    expected_groups = [
        "MODES",
        "LINES",
        "CHANNELS",
        "FIBONACCI",
        "GANN",
        "ELLIOTT",
        "PATTERNS",
        "SHAPES",
        "ANNOTATION",
        "MEASURE",
        "POSITION",
        "VOLUME",
    ]
    require(
        groups == expected_groups,
        f"The drawing rail's groups drifted: {groups}\nExpected {expected_groups}.",
    )


def check_environment_contract() -> None:
    auth = read("docs/AUTH_CONTRACT.md")
    for name in (
        "COINEPRO_DEBUG_API_BASE_URL",
        "COINEPRO_STAGING_API_BASE_URL",
        "COINEPRO_PRODUCTION_API_BASE_URL",
    ):
        require(name in auth, f"AUTH_CONTRACT missing {name}")
    require("-PCOINEPRO_API_BASE_URL=" not in auth, "AUTH_CONTRACT still documents obsolete shared API property")


def check_persisted_signal_identity() -> None:
    signals = read("core/signals/src/main/kotlin/com/coinepro/core/signals/SignalGateway.kt")
    execution = read("core/execution/src/main/kotlin/com/coinepro/core/execution/ExecutionGateway.kt")
    notifications = read("core/notifications/src/main/kotlin/com/coinepro/core/notifications/NotificationModels.kt")
    deep_links = read("app/src/main/kotlin/com/coinepro/app/DeepLinkValidation.kt")
    require("id?.takeIf { it > 0L }" in signals, "Signal mapper must require positive persisted signal IDs")
    require("signalId?.takeIf { it > 0L }" in execution, "Execution mapper must require positive signal IDs")
    require("takeIf { it > 0L }" in notifications, "Notification signal links must require positive signal IDs")
    require("takeIf { it > 0L }" in deep_links, "Deep links must require positive signal IDs")


def check_market_truth() -> None:
    market = read("core/marketdata/src/main/kotlin/com/coinepro/core/marketdata/MarketDataController.kt")
    smoke = read("scripts/release/production-readonly-smoke.py")
    for kotlin_value, python_value in (("15_000L", "15_000"), ("90_000L", "90_000"), ("30_000L", "30_000")):
        require(kotlin_value in market, f"Android market freshness threshold missing {kotlin_value}")
        require(python_value in smoke, f"Production smoke freshness threshold missing {python_value}")
    require('method="GET"' in smoke, "Production smoke must use explicit GET requests")
    for forbidden in ('method="POST"', 'method="PUT"', 'method="PATCH"', 'method="DELETE"'):
        require(forbidden not in smoke, f"Production read-only smoke contains write method {forbidden}")


def check_cache_scope() -> None:
    cache = read("core/database/src/main/kotlin/com/coinepro/core/database/RoomReadCaches.kt")
    require("isSupportedProductSymbol" in cache, "Market and Signal cache must independently enforce shared product scope")
    require('normalized == "XAUUSD" || normalized == "XAGUSD"' in cache, "Cache Forex scope drifted")
    require('normalized.endsWith("USDT")' in cache, "Cache Crypto scope drifted")
    require("isStale = true" in cache, "Restored market cache must remain explicitly stale")


def check_baseline_profile() -> None:
    profile = read("app/src/main/baseline-prof.txt")
    # The theme is on the critical path of the first frame — every composable on the first screen
    # reads it — so the profile has to cover it. Either the explicit class or the package wildcard
    # counts: AGP expands wildcards at build time, and a wildcard is the better of the two because
    # it survives the design system growing a file.
    covers_theme = (
        "Lcom/coinepro/core/designsystem/CoineProThemeKt;" in profile
        or "Lcom/coinepro/core/designsystem/**;" in profile
    )
    require(covers_theme, "Baseline Profile does not cover the theme on the first-frame path")
    require("Lcom/coinepro/core/designsystem/ThemeKt;" not in profile, "Baseline Profile still references removed ThemeKt class")
    # The launch path is more than the activity. If this shrinks back to a handful of hand-listed
    # classes, the profile has stopped being worth shipping.
    for required in (
        "Lcom/coinepro/app/MainActivity;",
        "Lcom/coinepro/core/auth/**;",
        "Lcom/coinepro/core/marketdata/**;",
        "Lcom/coinepro/feature/home/**;",
    ):
        require(required in profile, f"Baseline Profile no longer covers {required}")


def check_release_version_claim() -> None:
    contract = read("docs/PHASE16_RELEASE_ENGINEERING_CONTRACT.md")
    require("Play enforces cross-release monotonicity" in contract, "Phase 16 contract must distinguish local version validation from Play monotonicity")


def check_staging_validation() -> None:
    workflow = read(".github/workflows/android-ci.yml")
    require(":app:lintStaging" in workflow, "Android CI must lint the real staging variant")
    require(":app:assembleStaging" in workflow, "Android CI must assemble the real staging variant")
    require(":app:testStagingUnitTest" not in workflow, "Android CI must not execute nonexistent testStagingUnitTest")

    phase16 = read("docs/PHASE16_RELEASE_ENGINEERING_CONTRACT.md")
    phase17 = read("docs/PHASE17_LAUNCH_READINESS_CONTRACT.md")
    audit = read("docs/PHASE1_17_CROSS_PHASE_AUDIT.md")
    checklist = read("docs/ROADMAP_CHECKLIST.md")
    for name, text in (
        ("Phase 16 contract", phase16),
        ("Phase 17 contract", phase17),
        ("cross-phase audit", audit),
        ("roadmap checklist", checklist),
    ):
        require("lintStaging" in text and "assembleStaging" in text, f"{name} must document real staging gates")
    require("does not expose `:app:testStagingUnitTest`" in phase17, "Phase 17 must explicitly document the absent staging unit-test task")


# ── brand, assets and figures ──────────────────────────────────────────────────────────────────
#
# Three invariants a static audit of 4.32.1 found broken, each pinned here so it stays fixed.

# The product is «Pro Chart» / «پرو چارت» — see core/common BrandConfig. Every other spelling the
# app has used is listed, and any of them in a string resource or a document is a failure. `CoinePro`
# is the company and the repository, which is a different thing, and is not on this list.
# «کوین‌پرو اف‌ایکس» is the forex *platform*, CoinePro-FX, and is not the app; it is not listed.
FORBIDDEN_BRAND_SPELLINGS = ("Pro CHart", "Pro-Chart", "پروچارت", "ProChart ")

# Words a reader never uses for the thing they are looking at. The glossary in docs/audit names the
# replacement for each; a new occurrence in a user-facing string is a regression.
FORBIDDEN_UI_WORDS = {
    "values/strings.xml": ("شیءها", "واگرد", "ازنو", "بازپخش نوار", "دیدبان<", "نما اسکریپت", "نقشهٔ حرارتی"),
    "values-en/strings.xml": (">Studies<", ">Bar length<", "Connected surfaces", "Provider truth", ">STALE<", "server-side setting", "this build is pointed"),
}

STRAY_ASSET_SUFFIXES = (".orig", ".bak", ".rej", ".tmp")


def string_files() -> list[Path]:
    return [
        path for path in ROOT.glob("*/src/main/res/values*/strings.xml")
    ] + [path for path in ROOT.glob("*/*/src/main/res/values*/strings.xml")]


def check_brand_spelling() -> None:
    offenders: list[str] = []
    documents = [
        path
        for path in list(ROOT.glob("docs/legal/*.md")) + list(ROOT.glob("feature/legal/src/main/assets/legal/*.md"))
        # The working note beside the documents names the old source tree by its old name.
        if path.name != "README.md"
    ]
    for path in string_files() + documents:
        text = path.read_text(encoding="utf-8")
        for spelling in FORBIDDEN_BRAND_SPELLINGS:
            if spelling in text:
                offenders.append(f"{path.relative_to(ROOT)}: {spelling!r}")
    require(not offenders, "brand spelt a way BrandConfig does not allow:\n" + "\n".join(offenders))


def check_ui_vocabulary() -> None:
    offenders: list[str] = []
    for path in string_files():
        # The admin panel is internal-only and is allowed its engineering vocabulary.
        if "feature/admin/" in str(path):
            continue
        text = path.read_text(encoding="utf-8")
        for suffix, words in FORBIDDEN_UI_WORDS.items():
            if not str(path).endswith(suffix):
                continue
            for word in words:
                if word in text:
                    offenders.append(f"{path.relative_to(ROOT)}: {word!r}")
    require(not offenders, "a word the glossary retired is back in a user-facing string:\n" + "\n".join(offenders))


ARABIC_SCRIPT = re.compile(r"[\u0600-\u06FF]")


def check_english_locale_is_english() -> None:
    """No Persian text in `values-en/`.

    Persian is the default locale by the owner's decision, so the failure that matters is the
    inverse of the audit's: an English reader shown Persian because a key was copied across without
    being translated. Keys marked `translatable="false"` are asset paths and brand names and are
    allowed whatever they contain.
    """
    # English strings that quote a Persian word on purpose — a search hint showing what a Persian
    # name looks like. Each entry is a decision, not a leak, and the list should stay this short.
    quoting_persian = {"search_empty_hint"}
    offenders: list[str] = []
    for path in string_files():
        if not str(path).endswith("values-en/strings.xml"):
            continue
        for match in re.finditer(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', path.read_text(encoding="utf-8"), re.S):
            if 'translatable="false"' in match.group(2) or match.group(1) in quoting_persian:
                continue
            if ARABIC_SCRIPT.search(match.group(3)):
                offenders.append(f"{path.relative_to(ROOT)}: {match.group(1)}")
    require(not offenders, "Persian text in the English locale:\n" + "\n".join(offenders))


SECRET_MODULES = ("core/security", "core/auth", "core/execution", "core/copytrade", "feature/connections", "core/network")
SECRET_NAMES = re.compile(r"(?i)(apiKey|apiSecret|secretKey|password|passphrase|bearer|token)")
LOG_CALL = re.compile(r"\b(Log\.[dievw]|println|appLog\.\w+|log\.\w+)\s*\(")


def check_no_secret_logging() -> None:
    """No log call in a module that handles credentials names one on the same line.

    The audit asked for a test that fails if a `Log.*` receives a value typed `ApiKey`/`Secret`.
    There are no such wrapper types and Gson serialises the request bodies those values sit in by
    field, so a wrapper is a hazard in its own right. What can be checked, and is: a log statement
    in a credential-handling module whose arguments name a key, a secret, a password or a token.
    Today there are none; this keeps it that way.
    """
    offenders: list[str] = []
    for module in SECRET_MODULES:
        for path in (ROOT / module / "src" / "main").rglob("*.kt"):
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith(("//", "*", "/*")):
                    continue
                if LOG_CALL.search(line) and SECRET_NAMES.search(line):
                    offenders.append(f"{path.relative_to(ROOT)}:{number}: {stripped[:100]}")
    require(not offenders, "a credential-handling module logs something that names a secret:\n" + "\n".join(offenders))


def check_assets_clean() -> None:
    stray = [
        str(path.relative_to(ROOT))
        for path in ROOT.glob("*/*/src/main/assets/**/*")
        if path.is_file() and path.suffix.lower() in STRAY_ASSET_SUFFIXES
    ]
    require(not stray, f"editor droppings under src/main/assets would ship in the APK: {stray}")


def _ttf_digit_advances(path: Path) -> dict[str, int]:
    """Advance widths of U+0030..U+0039, read from the font's own tables.

    A deliberately small TrueType reader — `cmap` format 4 and 12, `hhea`, `hmtx` — so the gate
    has no dependency on fontTools being installed where CI runs.
    """
    import struct

    data = path.read_bytes()
    count = struct.unpack(">H", data[4:6])[0]
    tables = {}
    for index in range(count):
        tag, _, offset, length = struct.unpack(">4sIII", data[12 + index * 16: 28 + index * 16])
        tables[tag.decode("latin-1")] = (offset, length)
    hhea = tables["hhea"][0]
    metrics = struct.unpack(">H", data[hhea + 34: hhea + 36])[0]
    hmtx = tables["hmtx"][0]
    cmap = tables["cmap"][0]
    sub_count = struct.unpack(">H", data[cmap + 2: cmap + 4])[0]
    glyph_of: dict[int, int] = {}
    for index in range(sub_count):
        _, _, sub_offset = struct.unpack(">HHI", data[cmap + 4 + index * 8: cmap + 12 + index * 8])
        sub = cmap + sub_offset
        fmt = struct.unpack(">H", data[sub: sub + 2])[0]
        if fmt == 4:
            seg_x2 = struct.unpack(">H", data[sub + 6: sub + 8])[0]
            segs = seg_x2 // 2
            ends = struct.unpack(f">{segs}H", data[sub + 14: sub + 14 + seg_x2])
            starts_at = sub + 16 + seg_x2
            starts = struct.unpack(f">{segs}H", data[starts_at: starts_at + seg_x2])
            deltas = struct.unpack(f">{segs}h", data[starts_at + seg_x2: starts_at + 2 * seg_x2])
            ranges_at = starts_at + 2 * seg_x2
            ranges = struct.unpack(f">{segs}H", data[ranges_at: ranges_at + seg_x2])
            for code in range(0x30, 0x3A):
                for seg in range(segs):
                    if starts[seg] <= code <= ends[seg]:
                        if ranges[seg] == 0:
                            glyph_of[code] = (code + deltas[seg]) & 0xFFFF
                        else:
                            at = ranges_at + seg * 2 + ranges[seg] + (code - starts[seg]) * 2
                            glyph = struct.unpack(">H", data[at: at + 2])[0]
                            glyph_of[code] = (glyph + deltas[seg]) & 0xFFFF if glyph else 0
                        break
        elif fmt == 12 and not glyph_of:
            groups = struct.unpack(">I", data[sub + 12: sub + 16])[0]
            for group in range(groups):
                start, end, first = struct.unpack(">III", data[sub + 16 + group * 12: sub + 28 + group * 12])
                for code in range(0x30, 0x3A):
                    if start <= code <= end:
                        glyph_of[code] = first + (code - start)
        if glyph_of:
            break
    advances = {}
    for code, glyph in glyph_of.items():
        index = min(glyph, metrics - 1)
        advances[chr(code)] = struct.unpack(">H", data[hmtx + index * 4: hmtx + index * 4 + 2])[0]
    return advances


def check_tabular_digits() -> None:
    """Every Latin digit in the app's typeface advances the same width.

    A column of prices only reads as a column if every digit is the same width, and the standing
    rule that market figures are Latin digits rests on this: IRANYekanX's Latin digits are
    monospaced by design (562 units Regular, 572 Bold), its Persian digits are not (۱ at 238 against
    ۳ at 655), and there is no feature tag that changes either. This reads the fonts so a swapped or
    re-subset file cannot quietly make every price wobble.
    """
    for name in ("iranyekanx_regular.ttf", "iranyekanx_bold.ttf"):
        path = ROOT / "core/designsystem/src/main/res/font" / name
        require(path.exists(), f"{name} is missing")
        advances = _ttf_digit_advances(path)
        require(len(advances) == 10, f"{name}: could not read all ten Latin digits ({sorted(advances)})")
        require(
            len(set(advances.values())) == 1,
            f"{name}: Latin digits are no longer tabular: {advances}",
        )


def main() -> None:
    check_module_map()
    check_brand_spelling()
    check_ui_vocabulary()
    check_english_locale_is_english()
    check_no_secret_logging()
    check_assets_clean()
    check_tabular_digits()
    check_every_screen_is_rendered()
    check_bottom_navigation()
    check_learned_surfaces()
    check_environment_contract()
    check_persisted_signal_identity()
    check_market_truth()
    check_cache_scope()
    check_baseline_profile()
    check_release_version_claim()
    check_staging_validation()
    print("Phase 1-17 repository consistency gate passed.")


if __name__ == "__main__":
    main()
