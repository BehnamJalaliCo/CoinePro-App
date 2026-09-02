#!/usr/bin/env python3
"""
Side-by-side render diffs: the app's screenshot beside TradingView's phone app, same width.

The parity work in `docs/design/TRADINGVIEW_PARITY.md` was done by measuring the owner's own
screenshots of TradingView's phone app at 3× and building to the numbers. This script is the
check that survives the measuring: it lays each app screenshot (as `ScreenshotRenderTest` renders
it into `app/build/screenshots/`) beside the reference it was measured against, both scaled to the
same width, and writes the composite to `app/build/screenshots/parity/`. A reviewer opens one
picture and reads the two columns against each other; a regression in the toolbar or the legend is
then a thing you can see rather than a number you have to remember.

The references are the owner's screenshots at 1× (`docs/design/reference/tradingview-phone/`),
cropped to nothing and annotated by hand in a few places — the circles are the owner's and they
are left in, because they say what the comparison is *for*.

Run after `./gradlew :app:testDebugUnitTest --tests '*ScreenshotRenderTest*'`:

    python3 scripts/design/parity-diff.py
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - the build host has PIL; a bare checkout may not.
    print("Pillow is required: pip install pillow", file=sys.stderr)
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs" / "design" / "reference" / "tradingview-phone"
SCREENSHOTS = ROOT / "app" / "build" / "screenshots"
OUT = SCREENSHOTS / "parity"

# App screenshot → the reference it was measured against.
PAIRS = {
    "103-chart-toolbar-fa.png": "chart-mark.jpg",
    "76-chart-premium-fa.png": "chart-legend.jpg",
    "99-chart-position-fa.png": "chart-live-tag.jpg",
    "40-tool-rail-fa.png": "sheet-drawings.jpg",
}

WIDTH = 440
GUTTER = 16
LABEL_HEIGHT = 22


def fit(image: Image.Image, width: int) -> Image.Image:
    height = round(image.size[1] * width / image.size[0])
    return image.convert("RGB").resize((width, height), Image.LANCZOS)


def compose(app: Path, reference: Path) -> Image.Image:
    left = fit(Image.open(app), WIDTH)
    right = fit(Image.open(reference), WIDTH)
    height = max(left.size[1], right.size[1]) + LABEL_HEIGHT
    sheet = Image.new("RGB", (WIDTH * 2 + GUTTER, height), "white")
    draw = ImageDraw.Draw(sheet)
    draw.text((4, 4), f"app: {app.name}", fill="black")
    draw.text((WIDTH + GUTTER + 4, 4), f"TradingView: {reference.name}", fill="black")
    sheet.paste(left, (0, LABEL_HEIGHT))
    sheet.paste(right, (WIDTH + GUTTER, LABEL_HEIGHT))
    return sheet


def main() -> int:
    if not SCREENSHOTS.is_dir():
        print(f"no screenshots at {SCREENSHOTS}; run the ScreenshotRenderTest first", file=sys.stderr)
        return 1
    OUT.mkdir(parents=True, exist_ok=True)
    written = 0
    for app_name, reference_name in PAIRS.items():
        app = SCREENSHOTS / app_name
        reference = REFERENCE / reference_name
        if not app.is_file():
            print(f"skip {app_name}: not rendered", file=sys.stderr)
            continue
        if not reference.is_file():
            print(f"skip {app_name}: reference {reference_name} missing", file=sys.stderr)
            continue
        target = OUT / f"parity-{app.stem}.png"
        compose(app, reference).save(target, optimize=True)
        print(target.relative_to(ROOT))
        written += 1
    return 0 if written else 1


if __name__ == "__main__":
    sys.exit(main())
