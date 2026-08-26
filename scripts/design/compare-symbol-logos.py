#!/usr/bin/env python3
"""Render every candidate logo for a symbol side by side, on both themes.

Three archives overlap and none of them wins outright, so the choice has to be made by looking
rather than by asserting. This renders one row per symbol — Binance SVG, crypto-icons SVG, Binance
PNG — over the dark stage and the light surface the app actually uses, and writes a contact sheet.

The dark ground is the point. Most of these marks were drawn for a white page; a few are dark ink on
transparency and vanish against the app's near-black, which is invisible in any file listing and
obvious the moment they are rendered.
"""
import io
import os
import sys

import cairosvg
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCES = [
    ("binance", os.path.join(ROOT, "design/asset-logos/binance-icons/crypto"), ".svg"),
    ("crypto-icons", os.path.join(ROOT, "design/asset-logos/crypto-icons"), ".svg"),
    ("binance-png", os.path.join(ROOT, "design/asset-logos/binance"), ".png"),
]
# The two grounds the app actually paints behind an instrument logo.
STAGE = (11, 14, 17, 255)
LIGHT = (240, 242, 245, 255)
CELL = 64
PAD = 10


def load(path: str, size: int) -> Image.Image | None:
    try:
        if path.endswith(".svg"):
            png = cairosvg.svg2png(url=path, output_width=size, output_height=size)
            return Image.open(io.BytesIO(png)).convert("RGBA")
        return Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)
    except Exception:
        return None


def candidates(ticker: str) -> list[tuple[str, str]]:
    out = []
    for name, folder, ext in SOURCES:
        # The PNG archive is upper-cased; the SVG sets are lower.
        for stem in (ticker, ticker.upper()):
            p = os.path.join(folder, stem + ext)
            if os.path.exists(p):
                out.append((name, p))
                break
    return out


def sheet(tickers: list[str], out_path: str) -> None:
    rows = [(t, candidates(t)) for t in tickers]
    rows = [r for r in rows if r[1]]
    width = PAD + 90 + (CELL + PAD) * len(SOURCES) * 2
    height = PAD + (CELL + PAD) * len(rows) + 30
    sheet = Image.new("RGBA", (width, height), (24, 26, 33, 255))
    draw = ImageDraw.Draw(sheet)

    x = PAD + 90
    for ground in ("dark", "light"):
        for name, _, _ in SOURCES:
            draw.text((x + 2, PAD - 2), f"{name[:9]}·{ground[0]}", fill=(184, 190, 198, 255))
            x += CELL + PAD

    y = PAD + 22
    for ticker, found in rows:
        draw.text((PAD, y + CELL // 2 - 6), ticker.upper(), fill=(240, 241, 242, 255))
        x = PAD + 90
        for ground_colour in (STAGE, LIGHT):
            for name, _, _ in SOURCES:
                tile = Image.new("RGBA", (CELL, CELL), ground_colour)
                path = dict(found).get(name)
                if path:
                    art = load(path, CELL - 16)
                    if art is not None:
                        tile.alpha_composite(art, (8, 8))
                else:
                    draw_x = ImageDraw.Draw(tile)
                    draw_x.line((20, 20, CELL - 20, CELL - 20), fill=(80, 84, 92, 255), width=2)
                    draw_x.line((CELL - 20, 20, 20, CELL - 20), fill=(80, 84, 92, 255), width=2)
                sheet.alpha_composite(tile, (x, y))
                x += CELL + PAD
        y += CELL + PAD

    sheet.convert("RGB").save(out_path)
    print(f"{out_path}  ({len(rows)} symbols)")


if __name__ == "__main__":
    args = sys.argv[1:]
    out = args[0] if args else "symbol-logo-sheet.png"
    names = args[1:] or ["btc", "eth", "sol", "bnb", "xrp", "ada", "doge", "trx"]
    sheet(names, out)
