#!/usr/bin/env python3
"""Extract the TradingView-styled UI icons out of Pro-Chart-App's ``tvIcons.jsx`` into SVG files.

Pro-Chart already carries these as inline JSX fragments in a single module. Kept as a script rather
than done by hand once, because that file is the upstream: when it gains an icon, this re-runs
instead of someone re-copying twenty-three string literals.

Usage:
    python3 scripts/design/extract-tv-icons.py <path-to>/src/bazaarnama/tvIcons.jsx
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "design" / "ui-icons" / "tradingview"

# Each entry is `Name: { vb: '...', inner: '...' }` on one line. Matched rather than parsed because
# the file is generated-shaped and a JS parser here would be more machinery than the job needs.
ENTRY = re.compile(
    r"^\s*(?P<name>[A-Za-z0-9_]+):\s*\{\s*vb:\s*'(?P<vb>[^']*)'\s*,\s*inner:\s*'(?P<inner>.*)'\s*\}\s*,?\s*$"
)


def kebab(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z0-9])", "-", name).lower().replace("--", "-")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="path to tvIcons.jsx")
    args = parser.parse_args()

    text = args.source.read_text(encoding="utf-8")
    OUT.mkdir(parents=True, exist_ok=True)

    written = 0
    for line in text.splitlines():
        match = ENTRY.match(line)
        if not match:
            continue
        name, viewbox, inner = match.group("name"), match.group("vb"), match.group("inner")
        # JSX attribute casing back to SVG's hyphenated form.
        inner = inner.replace("clipPath=", "clip-path=").replace("fillRule=", "fill-rule=")
        svg = (
            '<svg xmlns="http://www.w3.org/2000/svg" '
            f'viewBox="{viewbox}" fill="currentColor">{inner}</svg>\n'
        )
        (OUT / f"{kebab(name)}.svg").write_text(svg, encoding="utf-8")
        written += 1

    if written == 0:
        print("No icons matched — has tvIcons.jsx changed shape?", file=sys.stderr)
        return 1
    print(f"Wrote {written} icons to {OUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
