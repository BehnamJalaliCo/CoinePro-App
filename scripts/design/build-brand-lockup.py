#!/usr/bin/env python3
"""Regenerate every brand raster in the app from the one master file.

`core/designsystem/brand/coinepro-lockup-master.png` is the horizontal lockup as the owner supplied
it — 1672x941, RGBA, real transparency. Everything the app ships is cut from it here, so a new
density or a new size is a re-run of this script rather than somebody's second pass in an image
editor. Nothing under `src/main/res` is hand-edited; if it disagrees with this file, this file wins.

What it produces:

* `coinepro_mark` and `coinepro_wordmark`, five densities each, in `core:designsystem`.
* `ic_launcher_foreground`, `ic_launcher_background` and `ic_launcher_monochrome`, five densities
  each, in `app`.
* `design/play/icon-512.png`, the Play listing's icon, composited from the same two layers so the
  store and the home screen cannot drift apart.

Two of those outputs are not straight crops, and the reasons are in `silver_matched` and
`launcher_foreground` below.

Usage:
    python3 scripts/design/build-brand-lockup.py [--check]

`--check` regenerates into a temporary directory and compares, so CI can prove the committed
rasters are what this script produces rather than trusting that they are.
"""

from __future__ import annotations

import argparse
import math
import shutil
import sys
import tempfile
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover - the message is the whole point
    print("Pillow is required: python3 -m pip install Pillow", file=sys.stderr)
    raise SystemExit(2)

REPO = Path(__file__).resolve().parents[2]
MASTER = REPO / "core" / "designsystem" / "brand" / "coinepro-lockup-master.png"
DESIGN_RES = REPO / "core" / "designsystem" / "src" / "main" / "res"
APP_RES = REPO / "app" / "src" / "main" / "res"
PLAY = REPO / "design" / "play"

# The regions of the master. Recorded here rather than in a README so that changing one means
# changing the thing that actually reads it.
MARK_BOX = (81, 223, 522, 711)
WORDMARK_BOX = (540, 401, 1607, 591)

# Density buckets and their scale over mdpi. Android's own ladder; nothing here is a choice.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# Heights at mdpi. Widths follow the artwork's own aspect, because a brand mark that is squeezed a
# percent to hit a round number is a brand mark that is wrong.
MARK_HEIGHT_MDPI = 104
WORDMARK_HEIGHT_MDPI = 35

# The adaptive icon canvas, and the circle every launcher shape is guaranteed to keep. 108 is the
# canvas; the *safe zone* is the middle 66, not the 72 that is often quoted — 72 is the largest
# square, and a round mask eats the corners of it.
ICON_CANVAS_MDPI = 108
ICON_SAFE_DIAMETER = 66

# The viewport: the middle 72 of the canvas is all a launcher ever draws, whatever mask it applies.
# Anything outside it is not "maybe cropped", it is gone — so this is a hard bound and the safe
# circle above is a soft one. One dp is left as margin so a rounding error is not a shaved edge.
ICON_VIEWPORT = 71

# What a launcher actually draws: the middle 72 of the canvas, exactly. ICON_VIEWPORT above is that
# same number minus a dp of margin, and is the bound the artwork is sized against; this one is the
# geometry, and is what the Play listing's crop has to use.
ICON_VIEWPORT_ACTUAL = 72

# Set once by `build`, so `play_icon` composites the same mark rather than re-reading the master.
MARK_CACHE: list = []

# A pixel is gold rather than silver when its channels disagree by more than this. The mark's
# silver is genuinely neutral (mean r = g = b to within half a level), so the split is clean.
CHROMA_SPLIT = 28


def opaque_pixels(image: Image.Image):
    """Every pixel the eye actually sees, with its position. Anti-aliased edges are excluded."""
    pixels = image.load()
    width, height = image.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 200:
                yield x, y, r, g, b


def luminance(r: int, g: int, b: int) -> float:
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def silver_matched(wordmark: Image.Image, mark: Image.Image) -> Image.Image:
    """Give "Coine" the mark's own chrome instead of the near-white it arrived with.

    The complaint this answers is exact and correct: under the mark, "Coine" reads as plain white
    text sitting next to a genuinely metallic "Pro". The two halves of the name are not made of the
    same material, which is the one thing a wordmark beneath a mark has to be.

    Measuring it says the same thing. The mark's silver runs from luminance 63 to 254 with its mass
    around 220; the wordmark's runs 30 to 255 with its mass around 232 and almost all of that in a
    flat top face. Twelve levels brighter and far less of it in the mid tones is the difference
    between chrome and white paint.

    So the fix is a histogram match rather than a colour picked by eye: the silver pixels' luminance
    distribution is remapped onto the mark's, which is "make it the same silver as the C" performed
    by measurement. Chroma is carried through as a ratio, so the neutral stays neutral and the very
    slightly warm reflections stay warm.

    The gold is not touched. Neither is the alpha — every edge in the artwork is the master's.
    """
    gold_start = min(
        (x for x, _, r, g, b in opaque_pixels(wordmark) if max(r, g, b) - min(r, g, b) > CHROMA_SPLIT),
        default=wordmark.size[0],
    )

    def is_silver(x: int, r: int, g: int, b: int) -> bool:
        # Position *and* chroma. Chroma alone would catch the neutral darks inside the gold's own
        # shadow and drag them into the silver ramp, which would smear the two materials together.
        return x < gold_start and max(r, g, b) - min(r, g, b) <= CHROMA_SPLIT

    reference = [
        luminance(r, g, b)
        for _, _, r, g, b in opaque_pixels(mark)
        if max(r, g, b) - min(r, g, b) <= CHROMA_SPLIT
    ]
    source = [
        luminance(r, g, b)
        for x, _, r, g, b in opaque_pixels(wordmark)
        if is_silver(x, r, g, b)
    ]
    if not reference or not source:
        raise SystemExit("the master has no silver to match — the crop boxes are wrong")

    reference.sort()
    source.sort()

    # One lookup for all 256 input levels, so the per-pixel loop is a table read. Built by rank:
    # a source level's percentile becomes the reference level at the same percentile.
    lookup = []
    index = 0
    for level in range(256):
        while index < len(source) and source[index] <= level:
            index += 1
        percentile = (index - 1) / max(len(source) - 1, 1)
        position = min(max(percentile, 0.0), 1.0) * (len(reference) - 1)
        lower = reference[math.floor(position)]
        upper = reference[math.ceil(position)]
        blend = position - math.floor(position)
        lookup.append(lower + (upper - lower) * blend)

    out = wordmark.copy()
    pixels = out.load()
    width, height = out.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0 or not is_silver(x, r, g, b):
                continue
            current = luminance(r, g, b)
            if current <= 0.5:
                continue
            scale = lookup[round(current)] / current
            pixels[x, y] = (
                min(255, round(r * scale)),
                min(255, round(g * scale)),
                min(255, round(b * scale)),
                a,
            )
    return out


def ink_box(image: Image.Image) -> tuple[int, int, int, int]:
    """The artwork's own bounds, ignoring the transparent margin the crop left around it."""
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise SystemExit("the crop is empty")
    return bounds


def ink_geometry(ink: Image.Image) -> tuple[float, float, float]:
    """The artwork's centre of mass, and the radius that holds all but the last 2% of it.

    Both numbers are weighted by alpha, so a soft edge counts for what it actually contributes
    rather than being either present or absent. The 98th percentile rather than the maximum is what
    lets the mark be a usable size: the maximum is set by the single furthest pixel — the tip of the
    C — and sizing to it would shrink everything else to fit one point nobody looks at.
    """
    pixels = ink.getchannel("A").load()
    width, height = ink.size
    total = 0.0
    moment_x = 0.0
    moment_y = 0.0
    for y in range(height):
        for x in range(width):
            weight = pixels[x, y]
            if weight:
                total += weight
                moment_x += (x + 0.5) * weight
                moment_y += (y + 0.5) * weight
    centroid_x = moment_x / total
    centroid_y = moment_y / total

    radii = []
    for y in range(height):
        for x in range(width):
            weight = pixels[x, y]
            if weight > 8:
                radii.append((math.hypot(x + 0.5 - centroid_x, y + 0.5 - centroid_y), weight))
    radii.sort()
    carried = sum(weight for _, weight in radii)
    running = 0.0
    cutoff = radii[-1][0]
    for radius, weight in radii:
        running += weight
        if running >= carried * 0.98:
            cutoff = radius
            break
    return centroid_x, centroid_y, cutoff


def launcher_foreground(mark: Image.Image, size: int) -> Image.Image:
    """The mark on the adaptive icon's canvas.

    The size is unchanged from the icon this replaces, and that is a finding rather than an
    oversight: the old one fitted the mark's bounding box to the 72-of-108 keyline, and measuring
    the artwork says that is very nearly the largest it can be. Fitting it strictly inside the
    guaranteed 66 circle would shrink it to 52 of 108 — small enough to read as timid — so what is
    used instead is the radius that holds 98% of the ink. The extreme tip of the C and the end of
    the P's tail may graze a circular mask; everything that carries the shape is inside it. That
    lands within 3% of where it already was.

    What *is* wrong is the **centring**. It was centred on its bounding box, and for an interlocking
    C and P with a tail hanging off the bottom that is not where the weight is: the mass sits at 44%
    of the height, so a box-centred mark floats high. Centring on the ink centroid drops it to where
    the eye reads as middle. That, the ground underneath it and the themed layer are what this
    function and the two below it are for — the icon was not lifeless because it was small.
    """
    left, top, right, bottom = ink_box(mark)
    ink = mark.crop((left, top, right, bottom))
    width, height = ink.size

    centroid_x, centroid_y, safe_radius = ink_geometry(ink)

    # Two bounds, and the smaller wins.
    #
    # The soft one puts 98% of the ink inside the guaranteed circle. The hard one keeps *all* of it
    # inside the 72 viewport — measured from the centroid, because that is where the art is about to
    # be centred, and a mark whose weight sits above its middle hangs further below it than a
    # bounding box suggests. Sizing to the circle alone clipped the end of the P's tail off the
    # bottom of the canvas, which is the kind of error that only shows up on a phone.
    by_circle = (size * ICON_SAFE_DIAMETER / ICON_CANVAS_MDPI / 2) / safe_radius
    reach_x = max(centroid_x, width - centroid_x)
    reach_y = max(centroid_y, height - centroid_y)
    by_viewport = (size * ICON_VIEWPORT / ICON_CANVAS_MDPI / 2) / max(reach_x, reach_y)
    scale = min(by_circle, by_viewport)
    scaled = ink.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.LANCZOS)
    centroid_x *= scale
    centroid_y *= scale

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(scaled, (round(size / 2 - centroid_x), round(size / 2 - centroid_y)), scaled)
    return canvas


def launcher_background(size: int) -> Image.Image:
    """The ground the mark sits on.

    It used to be `@color/ic_launcher_background`, one flat slab of #0E1118. A flat slab is exactly
    what makes an icon look dead on a home screen full of icons that are not flat: there is nothing
    for the mark to sit *in*.

    This is the smallest thing that fixes it — a radial from #171C26 under the mark to #080A0E at
    the corners. The whole range is fifteen levels of luminance, which is below the threshold at
    which anybody would call it a gradient and above the one at which the icon stops looking
    printed. It is not a glow: it is centred on the canvas rather than on the mark, and it is
    neutral rather than tinted with either brand colour.
    """
    centre = (23, 28, 38)
    edge = (8, 10, 14)
    image = Image.new("RGB", (size, size))
    pixels = image.load()
    half = size / 2
    # The corner, so the darkest value lands exactly where the canvas ends rather than beyond it.
    longest = math.hypot(half, half)
    for y in range(size):
        for x in range(size):
            distance = math.hypot(x + 0.5 - half, y + 0.5 - half) / longest
            # Eased, so the change happens across the middle instead of all at the rim.
            t = distance * distance
            pixels[x, y] = tuple(
                round(centre[channel] + (edge[channel] - centre[channel]) * t) for channel in range(3)
            )
    return image.convert("RGBA")


def launcher_monochrome(foreground: Image.Image) -> Image.Image:
    """The themed-icon layer, where only alpha survives.

    Android tints this one flat, so the difference between the silver C and the gold P is thrown
    away. The icon this replaces pointed `monochrome` at the full-colour foreground and got exactly
    that: one shape, with nothing to say the two letters cross.

    What can be carried across is the crossing itself, cut in as transparency where the two
    materials meet. The band has to be **thin**, and that is a fact about this mark rather than a
    preference: it is drawn as ribbons about six dp wide, so a seam of two would eat a third of a
    stroke. A first attempt at a wide knock-out produced a shape visibly chewed through the middle.

    Both masks are cleaned before anything is dilated. The silver carries warm specular highlights
    that classify as gold and the gold carries cool ones that classify as silver, so the raw split
    is speckled — and dilating a speckled mask sprays blocks across the face of the mark instead of
    drawing a line down the join. An opening removes the specks; a closing fills the pinholes.
    """
    width, height = foreground.size
    source = foreground.load()

    gold = Image.new("L", (width, height), 0)
    silver = Image.new("L", (width, height), 0)
    gold_pixels = gold.load()
    silver_pixels = silver.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = source[x, y]
            if a < 128:
                continue
            if max(r, g, b) - min(r, g, b) > CHROMA_SPLIT:
                gold_pixels[x, y] = 255
            else:
                silver_pixels[x, y] = 255

    unit = max(1, round(width / 108))
    clean = odd(unit * 3)
    gold = despeckle(gold, clean)
    silver = despeckle(silver, clean)

    # Half a dp either side, so the finished seam is about one dp of a six dp stroke.
    reach = odd(max(3, round(unit * 0.6)) * 2 + 1)
    grown_gold = gold.filter(ImageFilter.MaxFilter(reach)).load()
    grown_silver = silver.filter(ImageFilter.MaxFilter(reach)).load()
    alpha_pixels = foreground.getchannel("A").load()

    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    out_pixels = out.load()
    for y in range(height):
        for x in range(width):
            on_seam = grown_gold[x, y] and grown_silver[x, y]
            # White, because the system replaces the colour and keeps the alpha; a coloured
            # monochrome layer is a colour nobody will ever see.
            out_pixels[x, y] = (255, 255, 255, 0 if on_seam else alpha_pixels[x, y])
    return out


def despeckle(mask: Image.Image, kernel: int) -> Image.Image:
    """An opening then a closing: specks out, pinholes filled, edges where they started."""
    return (
        mask.filter(ImageFilter.MinFilter(kernel))
        .filter(ImageFilter.MaxFilter(kernel))
        .filter(ImageFilter.MaxFilter(kernel))
        .filter(ImageFilter.MinFilter(kernel))
    )


def odd(value: int) -> int:
    """Pillow's morphology filters take an odd kernel size and raise on an even one."""
    return value if value % 2 else value + 1


def play_icon(size: int = 512) -> Image.Image:
    """The Play listing's icon, composited from the very layers the launcher draws.

    Generated rather than exported by hand, because a store icon that was made separately drifts
    from the installed one — and the two sitting side by side in a search result is exactly where
    somebody notices.

    It is the 72 viewport rather than the full 108 canvas: Play applies its own rounding, and giving
    it the whole canvas would hand it 18dp of empty margin on every side to round the corners of.
    Flattened onto the background with no alpha, which the store requires.
    """
    source = size * ICON_CANVAS_MDPI // ICON_VIEWPORT_ACTUAL
    composed = Image.alpha_composite(launcher_background(source), launcher_foreground(MARK_CACHE[0], source))
    inset = round(source * (ICON_CANVAS_MDPI - ICON_VIEWPORT_ACTUAL) / 2 / ICON_CANVAS_MDPI)
    viewport = composed.crop((inset, inset, source - inset, source - inset))
    return viewport.resize((size, size), Image.LANCZOS).convert("RGB")


def write(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def scaled_to_height(image: Image.Image, height: int) -> Image.Image:
    width = max(1, round(image.size[0] * height / image.size[1]))
    return image.resize((width, height), Image.LANCZOS)


def build(design_res: Path, app_res: Path, play: Path) -> None:
    master = Image.open(MASTER).convert("RGBA")
    mark = master.crop(MARK_BOX)
    MARK_CACHE.clear()
    MARK_CACHE.append(mark)
    wordmark = silver_matched(master.crop(WORDMARK_BOX), mark)

    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_height(mark, round(MARK_HEIGHT_MDPI * factor)),
            design_res / f"drawable-{bucket}" / "coinepro_mark.png",
        )
        write(
            scaled_to_height(wordmark, round(WORDMARK_HEIGHT_MDPI * factor)),
            design_res / f"drawable-{bucket}" / "coinepro_wordmark.png",
        )

        size = round(ICON_CANVAS_MDPI * factor)
        foreground = launcher_foreground(mark, size)
        write(foreground, app_res / f"mipmap-{bucket}" / "ic_launcher_foreground.png")
        write(launcher_background(size), app_res / f"mipmap-{bucket}" / "ic_launcher_background.png")
        write(launcher_monochrome(foreground), app_res / f"mipmap-{bucket}" / "ic_launcher_monochrome.png")

    write(play_icon(), play / "icon-512.png")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the committed rasters match")
    arguments = parser.parse_args()

    if not arguments.check:
        build(DESIGN_RES, APP_RES, PLAY)
        print("brand rasters regenerated from the master")
        return 0

    with tempfile.TemporaryDirectory() as directory:
        design = Path(directory) / "design"
        app = Path(directory) / "app"
        play = Path(directory) / "play"
        build(design, app, play)
        roots = {design: DESIGN_RES, app: APP_RES, play: PLAY}
        drifted = []
        for root, committed in roots.items():
            for produced in sorted(root.rglob("*.png")):
                target = committed / produced.relative_to(root)
                if not target.exists() or target.read_bytes() != produced.read_bytes():
                    drifted.append(str(target.relative_to(REPO)))
        if drifted:
            print("these rasters are not what the master produces:", file=sys.stderr)
            for path in drifted:
                print("  " + path, file=sys.stderr)
            return 1
    print("brand rasters match the master")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
