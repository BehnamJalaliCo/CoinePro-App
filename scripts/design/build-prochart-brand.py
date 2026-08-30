#!/usr/bin/env python3
"""Cut every Pro-Chart brand asset from the two masters the owner supplied.

### Why a script and not five exports

The mark appears at eleven sizes across five densities, in the launcher's three adaptive layers, in
the Play listing and in the README. Cutting those by hand once is an afternoon; cutting them again
when the artwork is revised is the same afternoon, and the two cuts will not match. This is the one
place the crop, the trim and the alpha are decided, so a new master is one command.

### The alpha is derived, not shipped

Both masters are white artwork on solid black. Black is not the brand's ground — it is the ground
the artwork was rendered against — so shipping it would put a black rectangle behind the mark on
every light surface in the app. Luminance becomes alpha and the ink becomes flat white: a pixel that
was black is transparent, one that was white is opaque white, and the anti-aliased edge in between
becomes a partial alpha rather than a grey fringe. That is what makes the same file work on the
stage, on a card and on the launcher's black plate.

The owner also supplied a white-on-white variant. It is not used: it carries the same shape with no
usable channel to separate figure from ground, and the derived alpha above already gives a mark that
can be tinted to anything.

Run:  python3 scripts/design/build-prochart-brand.py
"""

from __future__ import annotations

import pathlib
import sys

try:
    from PIL import Image, ImageChops
except ImportError:  # pragma: no cover - the message is the whole point
    sys.exit("Pillow is required: pip install Pillow")

ROOT = pathlib.Path(__file__).resolve().parents[2]
BRAND = ROOT / "design" / "brand"
DESIGNSYSTEM = ROOT / "core" / "designsystem" / "src" / "main" / "res"
APP_RES = ROOT / "app" / "src" / "main" / "res"

# Android's five buckets as multiples of mdpi. The launcher wants its own, larger, set.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# The mark at mdpi. 48dp is the largest the app draws it at (the sign-in lockup asks for 96dp, which
# the xxhdpi cut covers); everything else is smaller, and Android downscales far better than it up.
MARK_MDPI = 96

# The lockup is wider than it is tall and is drawn to a width, so it is sized by width alone.
WORDMARK_MDPI = 168

# The adaptive icon's safe zone: the launcher may mask anything outside the middle 66 of 108.
#
# **54, and this number has now been wrong in both directions.** The first cut sat the mark at 60,
# reading the guideline cautiously, and on a real launcher that produced a small mark adrift in a
# black square. The correction went to 72 on the argument that the mark is taller than it is wide,
# so only the empty corners of its bounding box would cross the 66-unit safe circle — and on a real
# device the owner saw the mark running off its own edges. The argument was right about the corners
# and wrong about what a launcher does with them: several mask shapes clip closer than the circle,
# and a mark sized to the safe zone has nothing left to lose when they do.
#
# 54 is 72 less a quarter. It sits comfortably inside 66 on both axes with room for any mask, which
# is what the guideline asks for and what the two previous values each missed from one side.
LAUNCHER_CANVAS = 432
LAUNCHER_MARK = int(LAUNCHER_CANVAS * 54 / 108)

# The mark sits five percent of the icon's width to the **right** of centre.
#
# The owner asked for it after looking at the icon on a device, and it is the kind of call that is
# made that way rather than derived: [scaled_to_box] centres the artwork by its bounding box, and a
# box is only the optical centre when the ink inside it is evenly distributed. This artwork's is
# not — the stem is a solid vertical on the left and the right half is the open bowl of the C with
# a thin arrow through it — so the same box has much more weight on its left, and a box-centred
# mark reads as sitting left. That is what was seen and this is the correction.
#
# Five percent of 432 is 22 pixels, and the result is measured rather than assumed: the ink lands
# at 139..336 inside a safe zone of 84..348, so twelve pixels of margin remain on the side it moved
# towards. A larger nudge spends that margin and puts the icon back where the 72 cut left it.
LAUNCHER_NUDGE = 0.05


# Below this luminance a pixel is ground, not ink.
#
# The masters are rendered artwork, not vector exports, and their "black" is not zero — it runs one
# to three. `Image.getbbox()` treats any non-zero alpha as content, so the trim below found the
# whole 1254-square canvas every time and did nothing at all. The mark was then scaled *canvas and
# all* into the launcher's safe zone, and since the artwork fills about half of that canvas, the
# icon showed a mark at half the size it was supposed to be — which is exactly what the owner saw.
#
# Eight is high enough to clear the noise floor and far below anything on the anti-aliased edge,
# which climbs to 255 within a pixel or two. Nothing visible is lost.
INK_FLOOR = 8


def inked(master: Image.Image) -> Image.Image:
    """White artwork on black becomes flat white with the luminance as its alpha.

    `convert("L")` is the luminance, and on this artwork it is very nearly the coverage: the ink is
    255 and the ground is meant to be 0. It is used as the alpha channel rather than thresholded —
    a threshold would throw away the anti-aliasing the master was rendered with and leave a stair
    edge on every curve of the C — but everything under [INK_FLOOR] is forced to zero first, so the
    ground is *actually* transparent and the trim that follows has an edge to find.
    """
    alpha = master.convert("L").point(lambda value: 0 if value < INK_FLOOR else value)
    white = Image.new("RGBA", master.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    return white


def trimmed(image: Image.Image) -> Image.Image:
    """Crop to the artwork's own bounds.

    The masters are rendered on a square canvas with generous margins, and those margins are the
    renderer's, not the design's. Left in, they become padding this app cannot see or control: a
    `size(96.dp)` would draw a 70dp mark and every gap around it would be wrong. Cropping here makes
    the drawable's box the artwork's box, so a caller's size means what it says.
    """
    box = image.getbbox()
    return image.crop(box) if box else image


def write(image: Image.Image, path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"  {path.relative_to(ROOT)}  {image.width}x{image.height}")


def scaled_to_width(image: Image.Image, width: int) -> Image.Image:
    height = max(1, round(image.height * width / image.width))
    return image.resize((width, height), Image.LANCZOS)


def scaled_to_box(image: Image.Image, box: int) -> Image.Image:
    """Fit inside a square, keeping the aspect and centring what is left over."""
    scale = box / max(image.width, image.height)
    fitted = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.LANCZOS,
    )
    canvas = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    canvas.paste(fitted, ((box - fitted.width) // 2, (box - fitted.height) // 2), fitted)
    return canvas


def main() -> int:
    mark = trimmed(inked(Image.open(BRAND / "prochart-mark-master.png")))
    lockup = trimmed(inked(Image.open(BRAND / "prochart-lockup-master.png")))

    print("mark:")
    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_box(mark, round(MARK_MDPI * factor)),
            DESIGNSYSTEM / f"drawable-{bucket}" / "prochart_mark.png",
        )

    print("wordmark:")
    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_width(lockup, round(WORDMARK_MDPI * factor)),
            DESIGNSYSTEM / f"drawable-{bucket}" / "prochart_wordmark.png",
        )

    # The launcher, in three layers.
    #
    # Background is flat black and foreground is the white mark, which is the owner's icon exactly:
    # white inside, black behind. They are separate layers rather than one flattened image because
    # Android parallaxes them against each other — a flattened icon shifts its own background under
    # the mask and shows the wallpaper at the corner it moved away from.
    print("launcher:")
    ground = Image.new("RGBA", (LAUNCHER_CANVAS, LAUNCHER_CANVAS), (0, 0, 0, 255))
    foreground = Image.new("RGBA", (LAUNCHER_CANVAS, LAUNCHER_CANVAS), (0, 0, 0, 0))
    centred = scaled_to_box(mark, LAUNCHER_MARK)
    offset = (LAUNCHER_CANVAS - LAUNCHER_MARK) // 2
    nudge = round(LAUNCHER_CANVAS * LAUNCHER_NUDGE)
    foreground.paste(centred, (offset + nudge, offset), centred)
    for bucket, factor in DENSITIES.items():
        side = round(108 * factor)
        write(ground.resize((side, side), Image.LANCZOS), APP_RES / f"mipmap-{bucket}" / "ic_launcher_background.png")
        write(foreground.resize((side, side), Image.LANCZOS), APP_RES / f"mipmap-{bucket}" / "ic_launcher_foreground.png")
        # Themed icons are tinted by the launcher, so the layer supplies coverage only. The same
        # white mark is already exactly that: opaque where the ink is, transparent everywhere else.
        write(foreground.resize((side, side), Image.LANCZOS), APP_RES / f"mipmap-{bucket}" / "ic_launcher_monochrome.png")

    # The Play listing wants one flat 512 square with no transparency at all — an alpha channel is
    # rejected at upload — so this is the only place the black ground is baked in.
    print("play:")
    play = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
    # The same proportion the launcher uses, so the store icon and the one on the home screen are
    # the same drawing at two sizes rather than two different logos.
    play_side = round(512 * 54 / 108)
    play_mark = scaled_to_box(mark, play_side)
    # The same nudge, in the same proportion. The store icon and the one on the home screen have to
    # be the same drawing at two sizes; moving one and not the other would make them two logos.
    play_nudge = round(512 * LAUNCHER_NUDGE)
    play.paste(
        play_mark,
        ((512 - play_side) // 2 + play_nudge, (512 - play_side) // 2),
        play_mark,
    )
    write(play.convert("RGB").convert("RGBA"), ROOT / "design" / "play" / "icon-512.png")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
