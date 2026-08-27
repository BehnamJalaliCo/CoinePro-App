#!/usr/bin/env python3
"""The animated banner at the top of the README.

An SVG rather than a video or a GIF, for three reasons that all matter on GitHub: it stays sharp at
any width, it is a few kilobytes rather than a few megabytes, and its CSS animations run when the
file is referenced from an `<img>` — which is the only way GitHub will render it, since it strips
`<script>` and inline event handlers from Markdown.

The mark is the real brand asset, embedded as a data URI rather than redrawn. A hand-traced
approximation of a logo is a second logo, and the two drift.

Everything animated holds a complete static frame under `prefers-reduced-motion`. Nothing is only
visible mid-animation.

Usage:
    python3 scripts/design/build-readme-banner.py [--check]
"""

from __future__ import annotations

import argparse
import base64
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
MARK = REPO / "core" / "designsystem" / "src" / "main" / "res" / "drawable-xxxhdpi" / "coinepro_mark.png"
OUT = REPO / "design" / "readme" / "banner.svg"

WIDTH, HEIGHT = 1200, 300

# Where each block lives. Written down rather than scattered through the markup, because the one
# thing that goes wrong in a banner like this is two blocks quietly overlapping.
MARK_X, MARK_W = 74, 132
TEXT_X = 246
CHART_X = 782


def candles() -> str:
    """Twelve bars and the line through them, drawn left to right as the reader arrives."""
    bars = [
        (0, 165, 50, True), (1, 150, 52, False), (2, 135, 58, True), (3, 155, 55, False),
        (4, 118, 60, True), (5, 108, 62, True), (6, 128, 58, False), (7, 88, 66, True),
        (8, 78, 66, True), (9, 96, 62, False), (10, 62, 72, True), (11, 52, 74, True),
    ]
    step = 34
    out = ['<g class="wick" opacity=".75">']
    for index, top, height, _ in bars:
        x = 14 + index * step
        out.append(f'<line x1="{x}" y1="{top - 16}" x2="{x}" y2="{top + height + 16}"/>')
    out.append("</g>")
    for index, top, height, up in bars:
        x = 14 + index * step - 6
        klass = "candle-up" if up else "candle-down"
        delay = 0.05 + index * 0.07
        out.append(
            f'<rect class="{klass} rise" style="animation-delay:{delay:.2f}s" '
            f'x="{x}" y="{top}" width="12" height="{height}"/>'
        )
    points = " ".join(f"{14 + i * step} {t + h // 2}" for i, t, h, _ in bars)
    out.append(
        f'<polyline class="line draw" style="animation-delay:.6s" points="{points}"/>'
    )
    last_x = 14 + 11 * step
    last_y = bars[-1][1] + bars[-1][2] // 2
    out.append(f'<circle cx="{last_x}" cy="{last_y}" r="6" fill="#D8A848" class="pulse"/>')
    return "\n      ".join(out)


def svg() -> str:
    mark = "data:image/png;base64," + base64.b64encode(MARK.read_bytes()).decode("ascii")
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {WIDTH} {HEIGHT}" width="{WIDTH}" height="{HEIGHT}" role="img" aria-label="CoinePro">
  <title>CoinePro — signals, charts and copy trading, for Forex and Crypto</title>
  <defs>
    <linearGradient id="ground" x1="0.35" y1="0" x2="0.9" y2="1">
      <stop offset="0%" stop-color="#171C26"/><stop offset="55%" stop-color="#0E1118"/><stop offset="100%" stop-color="#080A0E"/>
    </linearGradient>
    <linearGradient id="silver" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#F6F6F6"/><stop offset="46%" stop-color="#DBDBDB"/>
      <stop offset="56%" stop-color="#9E9E9E"/><stop offset="100%" stop-color="#EDEDED"/>
    </linearGradient>
    <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#F0CC60"/><stop offset="46%" stop-color="#D8A848"/>
      <stop offset="56%" stop-color="#966A1F"/><stop offset="100%" stop-color="#E4BE55"/>
    </linearGradient>
    <linearGradient id="sheen" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#FFF" stop-opacity="0"/>
      <stop offset="50%" stop-color="#FFF" stop-opacity="0.5"/>
      <stop offset="100%" stop-color="#FFF" stop-opacity="0"/>
    </linearGradient>
    <clipPath id="plate"><rect width="{WIDTH}" height="{HEIGHT}" rx="16"/></clipPath>
  </defs>

  <style>
    .grid line {{ stroke: #1A2030; stroke-width: 1; }}
    .candle-up {{ fill: #00B15C; }} .candle-down {{ fill: #F6465D; }}
    .wick {{ stroke: #5E6673; stroke-width: 1.5; }}
    .line {{ fill: none; stroke: #D8A848; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }}
    .name {{ font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-weight: 700; letter-spacing: -1px; }}
    .sub {{ font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; fill: #848E9C; }}
    .fa {{ font-family: 'Vazirmatn', 'Segoe UI', Tahoma, sans-serif; fill: #6E7784; }}

    .draw {{ stroke-dasharray: 620; stroke-dashoffset: 620; animation: draw 2.4s cubic-bezier(.2,0,0,1) forwards; }}
    .rise {{ transform-box: fill-box; transform-origin: bottom; animation: rise .45s cubic-bezier(.2,0,0,1) backwards; }}
    .fade {{ opacity: 0; animation: fade .7s ease-out forwards; }}
    .sweep {{ animation: sweep 6s ease-in-out 1.8s infinite; }}
    .pulse {{ animation: pulse 2.4s ease-in-out infinite; }}

    @keyframes draw  {{ to {{ stroke-dashoffset: 0; }} }}
    @keyframes rise  {{ from {{ transform: scaleY(0); opacity: 0; }} to {{ transform: scaleY(1); opacity: 1; }} }}
    @keyframes fade  {{ to {{ opacity: 1; }} }}
    @keyframes sweep {{ 0% {{ transform: translateX(-380px); }} 60%,100% {{ transform: translateX(1300px); }} }}
    @keyframes pulse {{ 0%,100% {{ opacity: .3; }} 50% {{ opacity: .95; }} }}

    /* A complete, legible frame for anyone who has asked their device to stop moving things. */
    @media (prefers-reduced-motion: reduce) {{
      .draw {{ stroke-dashoffset: 0; animation: none; }}
      .rise, .pulse {{ animation: none; }}
      .fade {{ opacity: 1; animation: none; }}
      .sweep {{ display: none; }}
    }}
  </style>

  <g clip-path="url(#plate)">
    <rect width="{WIDTH}" height="{HEIGHT}" fill="url(#ground)"/>
    <g class="grid"><line x1="0" y1="75" x2="{WIDTH}" y2="75"/><line x1="0" y1="150" x2="{WIDTH}" y2="150"/><line x1="0" y1="225" x2="{WIDTH}" y2="225"/></g>

    <g transform="translate({CHART_X},18)">
      {candles()}
    </g>

    <image class="fade" style="animation-delay:.1s" href="{mark}" x="{MARK_X}" y="{(HEIGHT - int(MARK_W * 416 / 376)) // 2}" width="{MARK_W}"/>

    <g class="fade" style="animation-delay:.35s">
      <text class="name" x="{TEXT_X}" y="142" font-size="66" fill="url(#silver)">Coine<tspan fill="url(#gold)">Pro</tspan></text>
      <text class="sub" x="{TEXT_X + 4}" y="182" font-size="21">Signals · Charts · Copy trading</text>
      <text class="fa" x="{TEXT_X + 4}" y="182" font-size="19" dy="34" direction="rtl" text-anchor="start"
            transform="translate(330,0)">سیگنال، چارت، کپی‌ترید</text>
    </g>

    <rect class="sweep" x="-380" y="0" width="260" height="{HEIGHT}" fill="url(#sheen)" opacity=".24"/>
    <rect width="{WIDTH}" height="{HEIGHT}" rx="16" fill="none" stroke="#1E2329" stroke-width="2"/>
  </g>
</svg>
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()

    produced = svg()
    if arguments.check:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != produced:
            print("the committed banner is not what this script produces", file=sys.stderr)
            return 1
        print("readme banner matches")
        return 0

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(produced, encoding="utf-8")
    print(f"wrote {OUT.relative_to(REPO)} ({len(produced) // 1024} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
