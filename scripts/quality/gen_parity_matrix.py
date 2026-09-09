#!/usr/bin/env python3
"""Generate docs/qa/PARITY_MATRIX.md from the render tests' own qualifiers.

The plan asks for a phone/tablet/fold parity matrix. A matrix typed by hand is a matrix that
says what somebody hoped; this one is read out of the test sources, so a cell is filled only
when a Robolectric render, a golden or a JVM assertion actually exists for that screen at that
window. Re-run after adding a render; `check-cross-phase-consistency.py` fails when the
committed copy is stale.

Columns are the five windows the product decides at:

  phone            w393/w411 (compact; the closed cover of a foldable is this window too)
  tablet-portrait  w840 / sw800-w800 (the two-pane threshold; an open foldable is this window)
  tablet-landscape sw800-w1280 (labelled rail, workbench chart)
  fold-open        window class of an unfolded device = tablet-portrait, plus the hinge tests
  fold-closed      window class of a closed foldable = phone (w393), plus nothing hinge-specific

Rows are the top-level screens and the shell pieces. A cell lists the test functions that render
the screen at that window; `—` means no render exists, and the doc says so rather than a tick.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "qa" / "PARITY_MATRIX.md"

TEST_FILES = [
    ROOT / "app/src/test/kotlin/com/coinepro/app/GoldenScreenshotTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/ChartTypeGoldenTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/ScreenshotRenderTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/ChartDeskPointerTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/ChartKeyboardTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/NavigationParityTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/FoldMetricsTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/TouchTargetTest.kt",
    ROOT / "app/src/test/kotlin/com/coinepro/app/SheetShapeTest.kt",
]

# Pure-JVM tests that pin a window decision without rendering it.
JVM_EVIDENCE = {
    "fold-open": [
        ("core/designsystem/src/test/kotlin/com/coinepro/core/designsystem/CoineProFoldTest.kt", "CoineProFoldTest"),
        ("feature/chart/src/test/kotlin/com/coinepro/feature/chart/ChartFoldTest.kt", "ChartFoldTest"),
        ("core/designsystem/src/test/kotlin/com/coinepro/core/designsystem/WindowClassTest.kt", "WindowClassTest"),
        ("feature/chart/src/test/kotlin/com/coinepro/feature/chart/ChartPaneCapTest.kt", "ChartPaneCapTest"),
    ],
    "fold-closed": [
        ("core/designsystem/src/test/kotlin/com/coinepro/core/designsystem/WindowClassTest.kt", "WindowClassTest"),
        ("feature/chart/src/test/kotlin/com/coinepro/feature/chart/ChartPaneCapTest.kt", "ChartPaneCapTest"),
    ],
}

COLUMNS = ["phone", "tablet-portrait", "tablet-landscape", "fold-open", "fold-closed"]

SCREENS = [
    ("watchlist", ("watchlist", "home", "fold")),
    ("chart", ("chart", "tablet-chart", "desk", "keyboard", "keys")),
    ("explore", ("explore", "market", "guest")),
    ("ideas / signals", ("ideas", "signal")),
    ("menu / profile", ("menu", "profile", "avatar", "delete")),
    ("shell: bar and rail", ("bottom-bar", "bottombar", "shell", "navigation", "rail")),
    ("sheets and dialogs", ("sheet", "dialog", "picker")),
    ("alerts", ("alert",)),
    ("screener", ("screener",)),
    ("terminal / trade", ("terminal", "trade", "order", "dom", "ladder")),
]

CONST_RE = re.compile(r'const val ([A-Z_0-9]+) = "([^"]+)"')
CASE_RE = re.compile(
    r'@Config\([^)]*qualifiers = (?:"([^"]+)"|([A-Z_0-9]+))[^)]*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*fun\s+(`[^`]+`|\w+)\s*\(',
    re.S,
)
GOLDEN_RE = re.compile(r'(?:assertMatchesGolden|capture|captureRaw)\("([^"]+)"')


def window_of(qualifier: str) -> str | None:
    if "w1280dp" in qualifier:
        return "tablet-landscape"
    if "w840dp" in qualifier or ("sw800dp" in qualifier and "w800dp" in qualifier):
        return "tablet-portrait"
    if "w393dp" in qualifier or "w411dp" in qualifier:
        return "phone"
    return None


def screen_of(*names: str) -> str:
    """The first name that names a screen wins: the golden's name before the test's, the test's
    before the class's, so `FoldMetricsTest.bottomBarAt393` is the shell and not the watchlist."""
    for name in names:
        text = name.lower()
        for screen, keys in SCREENS:
            if any(k in text for k in keys):
                return screen
    return "other"


def collect() -> dict[str, dict[str, list[str]]]:
    cells: dict[str, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
    for path in TEST_FILES:
        if not path.exists():
            continue
        source = path.read_text(encoding="utf-8")
        consts = dict(CONST_RE.findall(source))
        # A class-level @Config applies to every test in the file.
        class_level = re.search(r'@Config\([^)]*qualifiers = (?:"([^"]+)"|([A-Z_0-9]+))[^)]*\)\s*class\s+(\w+)', source)
        class_name = path.stem
        if class_level:
            qualifier = class_level.group(1) or consts.get(class_level.group(2), "")
            window = window_of(qualifier)
            if window:
                for fn in re.findall(r'@Test\s*(?:@\w+(?:\([^)]*\))?\s*)*fun\s+(`[^`]+`|\w+)\s*\(', source):
                    name = fn.strip("`")
                    cells[screen_of(name, class_name)][window].append(f"{class_name}.{name}")
        for literal, const, fn in CASE_RE.findall(source):
            qualifier = literal or consts.get(const, "")
            window = window_of(qualifier)
            if not window:
                continue
            name = fn.strip("`")
            # The golden or capture name says which screen better than the function does.
            start = source.find(f"fun {fn}")
            body = source[start:start + 600]
            golden = GOLDEN_RE.search(body)
            key = golden.group(1) if golden else name
            cells[screen_of(key, name, class_name)][window].append(f"{class_name}.{name}")
    return cells


def render(cells: dict[str, dict[str, list[str]]]) -> str:
    out: list[str] = []
    out.append("# Parity matrix — phone, tablet, fold")
    out.append("")
    out.append("Generated by `scripts/quality/gen_parity_matrix.py` from the render tests' `@Config(qualifiers = …)`; do not edit by hand. A cell is the count of renders that exist for that screen at that window, with the tests named beneath the table. `—` is a window with no render, and is meant to be read as such.")
    out.append("")
    out.append("| window | qualifier | what it stands for |")
    out.append("| --- | --- | --- |")
    out.append("| phone | `w393dp` / `w411dp` | every phone; the closed cover of a foldable |")
    out.append("| tablet-portrait | `w840dp` / `sw800dp-w800dp` | the two-pane threshold; an unfolded device |")
    out.append("| tablet-landscape | `sw800dp-w1280dp` | labelled rail, workbench chart, eight panes |")
    out.append("| fold-open | = tablet-portrait window class, plus the hinge | `CoineProFold` table-top and book postures — JVM, Robolectric has no hinge |")
    out.append("| fold-closed | = phone window class | no posture; the cover display is a narrow phone |")
    out.append("")
    out.append("| screen | " + " | ".join(COLUMNS) + " |")
    out.append("| --- | " + " | ".join("---" for _ in COLUMNS) + " |")
    order = [s for s, _ in SCREENS] + ["other"]
    for screen in order:
        row = cells.get(screen, {})
        counts = []
        for column in COLUMNS:
            if column == "fold-open":
                n = len(row.get("tablet-portrait", [])) + (len(JVM_EVIDENCE["fold-open"]) if screen == "chart" else 0)
            elif column == "fold-closed":
                n = len(row.get("phone", []))
            else:
                n = len(row.get(column, []))
            counts.append(str(n) if n else "—")
        if any(c != "—" for c in counts):
            out.append(f"| {screen} | " + " | ".join(counts) + " |")
    out.append("")
    out.append("## The renders behind each cell")
    out.append("")
    for screen in order:
        row = cells.get(screen, {})
        if not row:
            continue
        out.append(f"### {screen}")
        out.append("")
        for column in ["phone", "tablet-portrait", "tablet-landscape"]:
            tests = sorted(set(row.get(column, [])))
            if tests:
                out.append(f"- **{column}** ({len(tests)}): " + ", ".join(f"`{t}`" for t in tests))
        out.append("")
    out.append("## The fold, on the JVM")
    out.append("")
    out.append("A hinge cannot be rendered off-device — `androidx.window` needs an activity and Robolectric reports no folding features — so the fold columns are the window-class columns plus these pure assertions on the posture mapping and the decisions taken from it:")
    out.append("")
    for path, name in JVM_EVIDENCE["fold-open"]:
        out.append(f"- `{name}` — `{path}`")
    out.append("")
    out.append("What a device would add, and this matrix does not claim: the hinge's actual dp on a Galaxy Z Fold / Pixel Fold, the table-top chart on glass, and the transition when a fold happens mid-gesture. See `docs/engineering/REPORT.md` §4.")
    out.append("")
    return "\n".join(out)


def main() -> int:
    text = render(collect())
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print(f"{OUT.relative_to(ROOT)} is stale; run scripts/quality/gen_parity_matrix.py")
            return 1
        print("parity matrix up to date")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
