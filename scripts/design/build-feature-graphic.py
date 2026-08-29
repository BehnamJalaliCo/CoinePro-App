#!/usr/bin/env python3
"""The Play listing's 1024x500 feature graphic.

Generated from the same brand master as the icon and the wordmark, for the same reason: a store
asset exported by hand drifts from the one in the app, and the two sit side by side in a search
result.

The Persian line is laid out by **Chromium**, not by drawing glyphs. Arabic script is cursive and
bidirectional — letters change shape by position and the run reads right to left — and an image
library that places one glyph after another produces disconnected letters in reversed order. It
looks like text to somebody who does not read it, which is the worst possible failure for a store
banner in a Persian app.

Usage:
    python3 scripts/design/build-feature-graphic.py [--check]
"""

from __future__ import annotations

import argparse
import base64
import pathlib
import sys
import tempfile

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / "design" / "play" / "feature-graphic-1024x500.png"
WORDMARK = REPO / "core" / "designsystem" / "src" / "main" / "res" / "drawable-xxxhdpi" / "prochart_wordmark.png"
FONT = REPO / "core" / "designsystem" / "src" / "main" / "res" / "font" / "iranyekanx_bold.ttf"

WIDTH = 1024
HEIGHT = 500

# The one sentence. Three words, no verb, no claim about returns — a banner that promises
# performance is a banner a reviewer reads twice.
TAGLINE = "سیگنال، چارت، کپی‌ترید"


def data_uri(path: pathlib.Path, mime: str) -> str:
    return f"data:{mime};base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def page() -> str:
    """The banner as one self-contained document."""
    return f"""<!doctype html>
<html lang="fa" dir="rtl">
<head><meta charset="utf-8">
<style>
  @font-face {{
    font-family: 'IRANYekanX';
    src: url('{data_uri(FONT, "font/ttf")}') format('truetype');
    font-weight: 700;
  }}
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  html, body {{ width: {WIDTH}px; height: {HEIGHT}px; overflow: hidden; }}
  body {{
    /* The icon's ground, widened. Fifteen levels of luminance from the middle to the corners —
       enough that the mark sits in something, far too little to read as a gradient. */
    background: radial-gradient(120% 140% at 38% 44%, #171C26 0%, #0E1118 55%, #080A0E 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 56px;
    font-family: 'IRANYekanX', sans-serif;
  }}
  /* Everything stays inside the middle 80%. Play crops this asset differently on different
     surfaces, and a mark that touches an edge is a mark that loses a limb on one of them. */
  /* Stacked, and no separate mark beside it.
     The supplied wordmark already carries the mark to the left of the name, so a second copy of it
     on this banner is the same drawing twice — which is how a logo stops reading as a logo. The
     tagline goes underneath rather than beside, so the whole lockup is centred instead of leaning
     into one half of a 1024x500 crop Play may cut differently on each of its surfaces. */
  .lockup {{
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 34px;
    max-width: {int(WIDTH * 0.8)}px;
  }}
  .text {{ display: flex; flex-direction: column; align-items: center; gap: 22px; }}
  .wordmark {{ width: 520px; height: auto; display: block; }}
  .tagline {{
    color: #DBDBDB;              /* the palette's silver — the metal the mark is made of */
    font-size: 40px;
    line-height: 1.25;
    letter-spacing: -0.01em;
    white-space: nowrap;
  }}
</style></head>
<body>
  <div class="lockup">
    <div class="text">
      <img class="wordmark" src="{data_uri(WORDMARK, "image/png")}" alt="">
      <div class="tagline">{TAGLINE}</div>
    </div>
  </div>
</body></html>"""


def render(destination: pathlib.Path) -> None:
    from playwright.sync_api import sync_playwright

    with tempfile.TemporaryDirectory() as directory:
        source = pathlib.Path(directory) / "banner.html"
        source.write_text(page(), encoding="utf-8")
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(
                executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
            )
            # No device scale factor: Play wants exactly 1024x500, and a 2x render downsampled
            # afterwards would soften the wordmark's bevel for nothing.
            browser_page = browser.new_page(viewport={"width": WIDTH, "height": HEIGHT})
            browser_page.goto(source.as_uri())
            browser_page.wait_for_timeout(400)  # let the embedded font settle before the shot
            destination.parent.mkdir(parents=True, exist_ok=True)
            browser_page.screenshot(path=str(destination))
            browser.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the committed banner matches")
    arguments = parser.parse_args()

    if not arguments.check:
        render(OUT)
        print(f"wrote {OUT.relative_to(REPO)}")
        return 0

    with tempfile.TemporaryDirectory() as directory:
        produced = pathlib.Path(directory) / "feature.png"
        render(produced)
        if not OUT.exists() or OUT.read_bytes() != produced.read_bytes():
            print("the committed feature graphic is not what this script produces", file=sys.stderr)
            return 1
    print("feature graphic matches")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
