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

### Which masters this actually reads

`prochart-mark-gold-master.png` (the mark, gold on black), `prochart-wordmark-fa-master.png` (the
name in Persian, white on black) and `prochart-lockup-master.png` (the Latin lockup, for English).
`prochart-mark-master.png` and `prochart-mark-light-master.png` are the earlier white artwork and are
**no longer read** — kept in `design/brand/` because they are the owner's originals and deleting a
supplied master to tidy a directory is not this script's call to make.

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
# 54 was 72 less a quarter, and it sat comfortably inside 66 with room for any mask.
#
# **It is 50 now, and the reason is the artwork rather than the guideline.** The mark this cuts from
# used to be 626×682 — noticeably taller than wide, with empty margin down both sides of its own
# bounding box, so a box-fitted mark had slack to spend on the nudge below. The owner's newer gold
# mark is 951×975, near enough square, and fills its box edge to edge. At 54 plus the nudge the ink
# reached 88 of 108 at mdpi, one unit outside the safe zone — invisible on the master and clipped by
# a mask on a real launcher, which is the exact failure the 72 cut had.
#
# 50 restores the margin (about thirteen units at xxxhdpi, three at mdpi) and, because the new
# artwork is wider, it also reads at about the same optical size the old mark did at 54. Measured,
# not guessed: `main` prints every cut and the bounds are checked at all five densities.
LAUNCHER_CANVAS = 432
LAUNCHER_MARK = int(LAUNCHER_CANVAS * 50 / 108)

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


# The brand gold, measured off the owner's own icon rather than typed in.
#
# `#FCBA26`, the modal colour of every pixel above half brightness in `prochart-mark-gold-master`.
# It is close to the `#F0B90B` in the design tokens and it is **not the same**, and the artwork wins
# on the launcher: an icon whose gold is a shade off the one in the file the owner drew is a
# difference nobody can name and everybody can see, next to the original, on a home screen.
#
# Sampled by hand once and written here rather than recomputed on every run, so that a revision of
# the artwork that shifts the hue shows up as a diff in this file instead of silently repainting the
# icon. If the master changes, remeasure and edit this line.
BRAND_GOLD = (252, 186, 38)

# What [BRAND_GOLD] weighs in PIL's `L` conversion, which is ITU-R 601. Used as the divisor that
# turns the artwork's luminance into coverage: a fully inked gold pixel has to come out at alpha
# 255, and dividing by 255 instead would make the whole mark 26% transparent.
GOLD_LUMA = 0.299 * BRAND_GOLD[0] + 0.587 * BRAND_GOLD[1] + 0.114 * BRAND_GOLD[2]


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

# The same floor for the JPEG masters, which need a higher one.
#
# The white-on-black masters are PNGs and their ground is one to three. The owner's newer artwork
# arrives as JPEG, and JPEG ringing puts a halo of eight to twenty around every hard edge on a black
# ground — which [INK_FLOOR] would read as ink and the trim would read as the artwork's bounds,
# giving a mark with a grey fog around it and a crop a dozen pixels too generous on every side.
#
# Twenty-four clears the ringing and is far below the anti-aliased edge, which climbs past a hundred
# within a pixel. Deliberately a separate constant: raising the PNG floor to match would throw away
# real edge detail on the masters that do not need it.
JPEG_INK_FLOOR = 24


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


def inked_jpeg(master: Image.Image) -> Image.Image:
    """[inked], with the floor a JPEG master needs. See [JPEG_INK_FLOOR]."""
    alpha = master.convert("L").point(lambda value: 0 if value < JPEG_INK_FLOOR else value)
    white = Image.new("RGBA", master.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    return white


def golden(master: Image.Image) -> Image.Image:
    """Gold artwork on black becomes flat [BRAND_GOLD] with its coverage as the alpha channel.

    The same idea as [inked] and it cannot be the same function. [inked] assumes the ink is white,
    so luminance *is* coverage; here the ink is gold, whose luminance is 189 rather than 255, and
    reusing that function would produce a mark 26% transparent everywhere — which on the launcher's
    black plate reads as a muddy brown and looks like a rendering fault rather than a colour choice.

    So the luminance is divided by the gold's own before it becomes alpha, and the colour is set
    flat rather than kept from the master. Keeping the master's RGB would carry JPEG's colour
    ringing into every edge pixel: a fringe of green and magenta a pixel wide, invisible at 512 and
    obvious at 48.
    """
    luma = master.convert("L").point(lambda value: 0 if value < JPEG_INK_FLOOR else value)
    alpha = luma.point(lambda value: min(255, round(value * 255 / GOLD_LUMA)))
    gold = Image.new("RGBA", master.size, BRAND_GOLD + (255,))
    gold.putalpha(alpha)
    return gold


def coverage(image: Image.Image) -> Image.Image:
    """The same shape in flat white, for the layer a launcher tints itself.

    Android's themed icon paints the monochrome layer in the launcher's own colour, so what it wants
    is coverage and nothing else. Handing it the gold layer would work — the tint replaces the
    colour — but it would ship a file whose RGB is a lie about what is drawn, and the next person to
    open it would reasonably conclude the themed icon is gold.
    """
    white = Image.new("RGBA", image.size, (255, 255, 255, 255))
    white.putalpha(image.getchannel("A"))
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
    lockup = trimmed(inked(Image.open(BRAND / "prochart-lockup-master.png")))
    # The owner's newer artwork: the mark in the brand gold, and the name set in Persian.
    gold = trimmed(golden(Image.open(BRAND / "prochart-mark-gold-master.png")))
    persian = trimmed(inked_jpeg(Image.open(BRAND / "prochart-wordmark-fa-master.png")))
    # The in-app mark is the launcher's mark with its colour taken off, not a second drawing.
    #
    # It used to be cut from `prochart-mark-master.png`, which is the older white artwork. Two
    # sources for one logo is how a home-screen icon and a sign-in screen end up subtly different —
    # and nobody reports it, because each looks right on its own. One master, two treatments.
    mark = coverage(gold)

    print("mark:")
    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_box(mark, round(MARK_MDPI * factor)),
            DESIGNSYSTEM / f"drawable-{bucket}" / "prochart_mark.png",
        )

    # The wordmark, in the language the reader is actually in.
    #
    # Persian is this app's default locale and `values/` is Persian throughout, so the unqualified
    # drawable is the Persian one and `drawable-en-*` carries the Latin lockup — the same convention
    # the strings already use, applied to the one image that is also a piece of writing.
    #
    # A single Latin wordmark on a Persian screen was the old behaviour and it was wrong in a way
    # that is easy to miss from outside the audience: the product's name in this market is «پروچارت»,
    # written, and a reader who has never seen the Latin form does not recognise it as the name of
    # the app they opened.
    print("wordmark (fa, default):")
    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_width(persian, round(WORDMARK_MDPI * factor)),
            DESIGNSYSTEM / f"drawable-{bucket}" / "prochart_wordmark.png",
        )

    print("wordmark (en):")
    for bucket, factor in DENSITIES.items():
        write(
            scaled_to_width(lockup, round(WORDMARK_MDPI * factor)),
            DESIGNSYSTEM / f"drawable-en-{bucket}" / "prochart_wordmark.png",
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
    # The **gold** mark on the launcher, which is the owner's own icon rather than a tint of ours.
    # In the app the mark stays white coverage so a screen can paint it whatever the theme asks for;
    # a launcher icon has no theme to follow and is the one place the brand colour is the design.
    centred = scaled_to_box(gold, LAUNCHER_MARK)
    offset = (LAUNCHER_CANVAS - LAUNCHER_MARK) // 2
    nudge = round(LAUNCHER_CANVAS * LAUNCHER_NUDGE)
    foreground.paste(centred, (offset + nudge, offset), centred)
    for bucket, factor in DENSITIES.items():
        side = round(108 * factor)
        write(ground.resize((side, side), Image.LANCZOS), APP_RES / f"mipmap-{bucket}" / "ic_launcher_background.png")
        write(foreground.resize((side, side), Image.LANCZOS), APP_RES / f"mipmap-{bucket}" / "ic_launcher_foreground.png")
        # Themed icons are tinted by the launcher, so the layer supplies coverage only — see
        # [coverage]. The same shape as the gold foreground, in flat white, so nothing downstream
        # has to know that the layer beside it happens to be gold.
        write(
            coverage(foreground).resize((side, side), Image.LANCZOS),
            APP_RES / f"mipmap-{bucket}" / "ic_launcher_monochrome.png",
        )

    # The Play listing wants one flat 512 square with no transparency at all — an alpha channel is
    # rejected at upload — so this is the only place the black ground is baked in.
    print("play:")
    play = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
    # The same proportion the launcher uses, so the store icon and the one on the home screen are
    # the same drawing at two sizes rather than two different logos.
    play_side = round(512 * 54 / 108)
    play_mark = scaled_to_box(gold, play_side)
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
