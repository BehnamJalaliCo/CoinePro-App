#!/usr/bin/env python3
"""Pull the TradingView icon set out of the Pro-Chart bundle into standalone SVGs.

The icons were extracted once already, into three JSX files that hold each one as a `{vb, inner}`
pair and inject it with `dangerouslySetInnerHTML`. That form is fine for a web app and useless to
this one, so this turns them back into files the vector converter can read.

Usage:
    python3 scripts/design/extract-tv-icons.py <path-to-prochart>/src/bazaarnama
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "design" / "ui-icons" / "tradingview"

# Each file holds a different family. Kept apart so the chart-only ones stay identifiable — a
# drawing-tool glyph is not interchangeable with an interface icon even when both are 28×28.
SOURCES = {
    "tvIcons.jsx": "",             # interface: camera, bell, search, lock …
    "tvGlyphs.jsx": "tool_",       # the 79 drawing-tool glyphs
    "tvChartIcons.jsx": "chart_",  # the 18 chart types
}

ENTRY = re.compile(
    r"(?:^|[\s,{])([A-Za-z_][\w]*)\s*:\s*\{\s*vb\s*:\s*'([^']*)'\s*,\s*inner\s*:\s*'(.*?)'\s*\}",
    re.S,
)


def snake(name: str) -> str:
    """CamelCase to the snake_case Android resource names need."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower().replace("__", "_")


def extract(source: Path, prefix: str) -> int:
    text = source.read_text(encoding="utf-8")
    written = 0
    for name, viewbox, inner in ENTRY.findall(text):
        # The JSX carries these as single-quoted JS strings, so the only escape that can appear is
        # for a quote; unescaping it keeps the path data byte-identical to TradingView's.
        body = inner.replace("\\'", "'").replace("\\\\", "\\")
        svg = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}">{body}</svg>'
        )
        (OUT / f"{prefix}{snake(name)}.svg").write_text(svg, encoding="utf-8")
        written += 1
    return written


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    root = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    total = 0
    for filename, prefix in SOURCES.items():
        path = root / filename
        if not path.exists():
            print(f"MISSING  {path}", file=sys.stderr)
            continue
        count = extract(path, prefix)
        print(f"{filename:<18} {count}")
        total += count
    print(f"{'total':<18} {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
