#!/usr/bin/env python3
"""Pull the Pro-Chart brand icon set out of its JSX into SVGs.

These are the app's *own* icons — the eleven that name its sections rather than a chart tool — and
they are original geometry rather than anyone's asset file. They were written as JSX fragments on
Phosphor's 256 canvas with six weights generated from two shapes: a stroked outline and a solid
fill. Only those two matter here; Android call sites pick between them, and thin/light/bold are
stroke widths this app never uses.

Usage:
    python3 scripts/design/extract-brand-icons.py <path-to-prochart>/src/icons/brand/index.jsx
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "design" / "ui-icons" / "brand"

# The canvas the geometry was drawn on, and the stroke Phosphor calls "regular" at that size.
CANVAS = 256
REGULAR_STROKE = 16

ICON = re.compile(
    r"export const (\w+) = brandIcon\('\w+',\s*\{(.*?)\n\}\);",
    re.S,
)
FRAGMENT = re.compile(r"(paths|fill|duo):\s*(?:\(\s*<>(.*?)</>\s*\)|(<[^\n]*?/>))", re.S)
SHAPE = re.compile(r"<(path|rect|circle|ellipse|line|polyline|polygon)\b([^>]*?)/?>", re.S)


def snake(name: str) -> str:
    """PCCopyTradeIcon -> copy_trade."""
    core = re.sub(r"^PC|Icon$", "", name)
    return re.sub(r"(?<!^)(?=[A-Z])", "_", core).lower()


def shapes_of(fragment: str) -> str:
    """Re-emit the JSX shapes as plain SVG, dropping React-only attributes."""
    out = []
    for tag, attrs in SHAPE.findall(fragment):
        clean = re.sub(r"\s*\{[^}]*\}", "", attrs).strip()
        out.append(f"<{tag} {clean} />")
    return "".join(out)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    text = Path(sys.argv[1]).read_text(encoding="utf-8")
    OUT.mkdir(parents=True, exist_ok=True)

    written = 0
    for name, body in ICON.findall(text):
        parts = {key: (grouped or single) for key, grouped, single in FRAGMENT.findall(body)}
        stem = snake(name)
        if "paths" in parts:
            # Stroke lives on the wrapping group in the original, inherited by every shape.
            svg = (
                f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}">'
                f'<g fill="none" stroke="currentColor" stroke-width="{REGULAR_STROKE}" '
                f'stroke-linecap="round" stroke-linejoin="round">{shapes_of(parts["paths"])}</g>'
                "</svg>"
            )
            (OUT / f"{stem}.svg").write_text(svg, encoding="utf-8")
            written += 1
        if "fill" in parts:
            svg = (
                f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}">'
                f'<g fill="currentColor">{shapes_of(parts["fill"])}</g>'
                "</svg>"
            )
            (OUT / f"{stem}_fill.svg").write_text(svg, encoding="utf-8")
            written += 1

    print(f"brand icons  {written} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
