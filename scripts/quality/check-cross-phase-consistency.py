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
        ("HOME", "home"),
        ("MARKETS", "markets"),
        # Not "chart": that route belongs to the chart *of a symbol* and has for every release so
        # far. The tab is a different destination that redirects into it, and giving the two the
        # same name would break every saved back stack that holds one.
        ("CHART", "chart-tab"),
        ("SIGNALS", "signals"),
        ("AI", "ai"),
    ]
    require(entries == expected, f"Bottom navigation contract drifted: {entries}")

    for language, directory in (("Persian", "values"), ("English", "values-en")):
        labels = read(f"core/navigation/src/main/res/{directory}/strings.xml")
        for destination, _ in expected:
            key = f'name="nav_{destination.lower()}"'
            require(key in labels, f"{language} bottom-navigation label missing: {key}")


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


def main() -> None:
    check_module_map()
    check_every_screen_is_rendered()
    check_bottom_navigation()
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
