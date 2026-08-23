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


def check_bottom_navigation() -> None:
    text = read("core/navigation/src/main/kotlin/com/coinepro/core/navigation/AppDestination.kt")
    entries = re.findall(r'^\s*([A-Z]+)\("([^"]+)",\s*"([^"]+)",', text, flags=re.MULTILINE)
    expected = [
        ("HOME", "home", "Home"),
        ("SIGNALS", "signals", "Signals"),
        ("AI", "ai", "AI"),
        ("TOOLS", "tools", "Tools"),
        ("ACTIVITY", "activity", "Activity"),
    ]
    require(entries == expected, f"Bottom navigation contract drifted: {entries}")


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
    require("isSupportedCachedMarketQuote" in cache, "Market cache must independently enforce product scope")
    require('normalized == "XAUUSD" || normalized == "XAGUSD"' in cache, "Cache Forex scope drifted")
    require('normalized.endsWith("USDT")' in cache, "Cache Crypto scope drifted")


def check_baseline_profile() -> None:
    profile = read("app/src/main/baseline-prof.txt")
    require("Lcom/coinepro/core/designsystem/CoineProThemeKt;" in profile, "Baseline Profile does not target current theme class")
    require("Lcom/coinepro/core/designsystem/ThemeKt;" not in profile, "Baseline Profile still references removed ThemeKt class")


def check_release_version_claim() -> None:
    contract = read("docs/PHASE16_RELEASE_ENGINEERING_CONTRACT.md")
    require("Play enforces cross-release monotonicity" in contract, "Phase 16 contract must distinguish local version validation from Play monotonicity")


def main() -> None:
    check_module_map()
    check_bottom_navigation()
    check_environment_contract()
    check_persisted_signal_identity()
    check_market_truth()
    check_cache_scope()
    check_baseline_profile()
    check_release_version_claim()
    print("Phase 1-17 repository consistency gate passed.")


if __name__ == "__main__":
    main()
