#!/usr/bin/env python3
"""The web app's icons, from the phone's launcher icon.

The phone's icon is an adaptive icon: a background layer and a foreground layer, 108 dp each, of
which a launcher shows the middle 72 dp through its own mask. A web app manifest's `maskable` icon
is the same idea with the same safe zone (the middle 80 %), so the maskable icons are the two layers
stacked, full bleed. The `any` icons and the favicon are the same stack, cut to the rounded square
most launchers use, so a browser that does not mask still shows the phone's shape and not a slab.

Run after the launcher icon changes:  python3 scripts/design/build-web-icons.py
Writes web/src/wasmJsMain/resources/icons/. Needs Pillow; the outputs are committed, so a build
does not.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/mipmap-xxxhdpi"
OUT = ROOT / "web/src/wasmJsMain/resources/icons"


def stacked() -> Image.Image:
    background = Image.open(RES / "ic_launcher_background.png").convert("RGBA")
    foreground = Image.open(RES / "ic_launcher_foreground.png").convert("RGBA").resize(background.size, Image.LANCZOS)
    return Image.alpha_composite(background, foreground)


def rounded(image: Image.Image) -> Image.Image:
    """The visible 72 of 108, with the 22 % corner radius a squircle launcher approximates."""
    width = image.width
    inset = round(width * 18 / 108)
    face = image.crop((inset, inset, width - inset, width - inset))
    mask = Image.new("L", face.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, face.width - 1, face.height - 1), radius=round(face.width * 0.22), fill=255)
    face.putalpha(mask)
    return face


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    full = stacked()
    face = rounded(full)
    for size in (192, 512):
        full.resize((size, size), Image.LANCZOS).save(OUT / f"maskable-{size}.png", optimize=True)
        face.resize((size, size), Image.LANCZOS).save(OUT / f"icon-{size}.png", optimize=True)
    face.resize((180, 180), Image.LANCZOS).save(OUT / "apple-touch-icon.png", optimize=True)
    face.resize((32, 32), Image.LANCZOS).save(OUT / "favicon-32.png", optimize=True)
    for path in sorted(OUT.iterdir()):
        print(f"{path.relative_to(ROOT)}  {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
