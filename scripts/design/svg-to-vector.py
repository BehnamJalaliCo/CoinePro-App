#!/usr/bin/env python3
"""Convert an archived asset logo from SVG to an Android vector drawable.

The SVGs under ``design/asset-logos`` are the source of truth; this turns the ones the app
actually quotes into ``core/designsystem/src/main/res/drawable/asset_<base>.xml``. Only the
converted ones are packaged, so adding a market later is one command rather than a design task,
and the archive costs the APK nothing.

The converter is deliberately narrow. Android's vector format is a strict subset of SVG, and the
failure mode of a lenient converter is an icon that renders *almost* right — a missing counter, a
shape filled black instead of gradient — which nobody notices until it ships. So anything it
cannot represent faithfully is refused by name instead of approximated.

Usage:
    python3 scripts/design/svg-to-vector.py btc eth sol
    python3 scripts/design/svg-to-vector.py --set tv-logos/metal gold silver
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ARCHIVE = REPO / "design" / "asset-logos"
UI_ARCHIVE = REPO / "design" / "ui-icons"
DRAWABLE = REPO / "core" / "designsystem" / "src" / "main" / "res" / "drawable"

SVG_NS = "{http://www.w3.org/2000/svg}"
XLINK_HREF = "{http://www.w3.org/1999/xlink}href"

# Elements Android's vector format has no faithful equivalent for. Refused rather than dropped,
# because a silently missing shape reads as artwork that simply looks wrong.
UNSUPPORTED = ("image",)

# Subtrees that define rather than draw. Skipped wholesale — what matters is whether a drawn shape
# *references* them, which is checked separately.
DEFINITIONS = ("defs", "filter", "mask", "clipPath", "symbol", "title", "desc", "metadata")

# Referencing attributes. A gradient or clip reference changes the shape itself, so it is always
# refused; a filter reference is a drop shadow in this artwork and can be dropped on request.
HARD_REFERENCES = ("mask",)


class Unsupported(Exception):
    pass


def local(tag: str) -> str:
    return tag[len(SVG_NS):] if tag.startswith(SVG_NS) else tag


def viewport(svg: ET.Element) -> tuple[float, float, float, float]:
    """The drawable size, and the offset needed when the viewBox does not start at 0 0.

    Android's vector format has no viewBox origin — its coordinate space always starts at zero — so
    an offset origin is carried as a translation on a wrapping group rather than refused. TradingView
    ships at least one icon with a `-1 -1` origin, which is a one-pixel bleed and not a mistake.
    """
    box = svg.get("viewBox")
    if box:
        parts = [float(v) for v in re.split(r"[ ,]+", box.strip())]
        if len(parts) == 4:
            return parts[2], parts[3], -parts[0], -parts[1]
    width, height = svg.get("width"), svg.get("height")
    if width and height:
        return (
            float(re.sub(r"[^0-9.]", "", width)),
            float(re.sub(r"[^0-9.]", "", height)),
            0.0,
            0.0,
        )
    raise Unsupported("no viewBox and no width/height")


def circle_path(cx: float, cy: float, r: float) -> str:
    """A circle as two half-arcs — the only way to express one as vector path data."""
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def ellipse_path(cx: float, cy: float, rx: float, ry: float) -> str:
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z"


def rect_path(x: float, y: float, w: float, h: float, rx: float = 0.0, ry: float = 0.0) -> str:
    """A rectangle, with corner arcs when it is rounded.

    Android's vector format has no rounded-rect primitive, but it has arcs, and a rounded rect is
    four straight edges and four quarter-circles. Refusing these cost four icons and a chart type
    for a shape that is entirely expressible.
    """
    if not rx and not ry:
        return f"M{x},{y} h{w} v{h} h{-w} Z"
    rx = rx or ry
    ry = ry or rx
    # SVG clamps a radius larger than half the side; so must this, or the arcs cross over.
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    return (
        f"M{x + rx},{y} "
        f"h{w - 2 * rx} a{rx},{ry} 0 0 1 {rx},{ry} "
        f"v{h - 2 * ry} a{rx},{ry} 0 0 1 {-rx},{ry} "
        f"h{-(w - 2 * rx)} a{rx},{ry} 0 0 1 {-rx},{-ry} "
        f"v{-(h - 2 * ry)} a{rx},{ry} 0 0 1 {rx},{-ry} Z"
    )


def number(element: ET.Element, name: str, default: float = 0.0, span: float = 0.0) -> float:
    """A numeric attribute, resolving a percentage against [span] when one is given."""
    raw = element.get(name)
    if raw in (None, ""):
        return default
    raw = raw.strip()
    if raw.endswith("%"):
        if not span:
            raise Unsupported(f"{name}={raw!r} is a percentage with nothing to measure against")
        return float(raw[:-1]) / 100.0 * span
    return float(raw)


def gradient_of(reference: str, ids: dict, viewbox: tuple[float, float]) -> dict | None:
    """Resolve a `url(#id)` paint to a gradient Android can draw, or None if it is not one.

    Android's vector format does support gradients, so these are carried across rather than
    flattened to a representative stop. That matters more than it sounds: a sixth of the Binance
    archive is drawn this way, and flattening turns a brushed-metal coin into a flat disc — close
    enough to pass a file listing and obviously wrong beside the original.

    Every gradient in these sets is `userSpaceOnUse`, so its coordinates are already in viewBox
    space and map straight over. A `gradientTransform` is refused: it re-frames the gradient, and
    ignoring it would put the highlight somewhere the artist did not.
    """
    match = re.fullmatch(r"url\(#(.+)\)", reference.strip())
    if not match:
        return None
    node = ids.get(match.group(1))
    if node is None:
        raise Unsupported(f"paint {reference!r} points at nothing in this file")
    kind = local(node.tag)
    if kind not in ("linearGradient", "radialGradient"):
        return None
    if node.get("gradientTransform"):
        raise Unsupported("gradientTransform= re-frames the gradient and cannot be flattened")
    # SVG defaults this to objectBoundingBox, whose coordinates are fractions of the shape's own
    # bounding box. Mapping that needs the box, which means measuring the path — so it is refused
    # rather than guessed at. Every gradient in the archives that matters says userSpaceOnUse.
    if node.get("gradientUnits", "objectBoundingBox") != "userSpaceOnUse":
        raise Unsupported("gradientUnits=objectBoundingBox is not mapped")

    stops = []
    for stop in node:
        if local(stop.tag) != "stop":
            continue
        colour = normalise(stop.get("stop-color") or "#000000")
        opacity = stop.get("stop-opacity")
        if opacity is not None and float(opacity) < 1:
            alpha = round(float(opacity) * 255)
            colour = f"#{alpha:02X}{colour.lstrip('#')}"
        stops.append((stop.get("offset") or "0", colour))
    if len(stops) < 2:
        raise Unsupported("gradient has fewer than two stops")

    w, h = viewbox
    if kind == "linearGradient":
        return {
            "type": "linear",
            "coords": {
                "startX": number(node, "x1", span=w),
                "startY": number(node, "y1", span=h),
                # SVG's default for x2 is 100%, which is a horizontal sweep; leaving it at zero
                # would collapse the gradient to a point and paint the shape one flat colour.
                "endX": number(node, "x2", default=w, span=w),
                "endY": number(node, "y2", span=h),
            },
            "stops": stops,
        }
    return {
        "type": "radial",
        "coords": {
            "centerX": number(node, "cx", default=w / 2, span=w),
            "centerY": number(node, "cy", default=h / 2, span=h),
            "gradientRadius": number(node, "r", default=min(w, h) / 2, span=min(w, h)),
        },
        "stops": stops,
    }


def covers_viewport(reference: str, ids: dict, viewbox: tuple[float, float]) -> bool:
    """Whether a `clip-path` reference clips away nothing at all.

    Most of these sets wrap their artwork in a clip that is simply the whole canvas — a rectangle
    the size of the viewBox, or a circle inscribed in it. Neither removes a pixel, and both are
    there because the drawing tool emitted them. Refusing those would cost a sixth of the archive
    for no gain, so they are recognised and dropped; anything that genuinely crops is still refused,
    because a silently un-cropped shape is a blob where a glyph should be.

    The inscribed circle counts as no-op only because every logo is clipped to a disc at draw time
    anyway — see CoineProAssetLogo.
    """
    match = re.fullmatch(r"url\(#(.+)\)", reference.strip())
    if not match:
        return False
    node = ids.get(match.group(1))
    if node is None or local(node.tag) != "clipPath":
        return False
    shapes = list(node)
    if len(shapes) != 1:
        return False
    shape = shapes[0]
    tag = local(shape.tag)
    w, h = viewbox
    if tag == "rect":
        full = number(shape, "width") >= w and number(shape, "height") >= h
        rounded = number(shape, "rx") or number(shape, "ry")
        # A fully rounded rect is the inscribed circle; a partly rounded one really does crop.
        return full and (not rounded or rounded >= min(w, h) / 2)
    if tag == "circle":
        return number(shape, "r") * 2 >= min(w, h)
    if tag == "path":
        data = " ".join((shape.get("d") or "").split())
        return data.replace(" ", "").upper() in {
            f"M0 0H{w:g}V{h:g}H0Z".replace(" ", "").upper(),
            f"M0 0H{w:g}V{h:g}H0V0Z".replace(" ", "").upper(),
        }
    return False


def collect(
    element: ET.Element,
    fill: str | None,
    rule: str | None,
    out: list[dict],
    drop_shadows: bool,
    stroke: str | None = None,
    width: str | None = None,
    ids: dict[str, ET.Element] | None = None,
    viewbox: tuple[float, float] = (0.0, 0.0),
) -> None:
    """Walk the tree, resolving inherited fill and fill-rule down to individual paths."""
    ids = ids if ids is not None else {}
    tag = local(element.tag)
    if tag in DEFINITIONS:
        return
    if tag in UNSUPPORTED:
        raise Unsupported(f"<{tag}> has no faithful Android equivalent")
    for attribute in HARD_REFERENCES:
        if element.get(attribute):
            raise Unsupported(f"{attribute}= changes the shape and cannot be flattened")
    clip = element.get("clip-path")
    if clip and not covers_viewport(clip, ids, viewbox):
        raise Unsupported("clip-path= crops the artwork and cannot be flattened")
    if element.get("filter"):
        if not drop_shadows:
            raise Unsupported("filter= is a drop shadow; pass --drop-shadows to convert without it")
        # The shadow is drawn as its own copy of the glyph. Dropping only the filter would leave a
        # solid black duplicate sitting under the real one, so the whole element goes.
        return

    if tag == "use":
        target = ids.get((element.get("href") or element.get(XLINK_HREF) or "").lstrip("#"))
        if target is None:
            raise Unsupported("<use> points at a shape that is not in this file")
        # A <use> is the referenced shape wearing the reference's own paint.
        collect(
            target,
            element.get("fill") or fill,
            element.get("fill-rule") or rule,
            out,
            drop_shadows,
            element.get("stroke") or stroke,
            element.get("stroke-width") or width,
            ids,
            viewbox,
        )
        return

    own_fill = element.get("fill")
    own_rule = element.get("fill-rule") or element.get("clip-rule")
    own_stroke = element.get("stroke")
    own_width = element.get("stroke-width")
    if element.get("style"):
        # Presentation attributes are what these sets use; a style attribute would silently win
        # over them and change the result, so it is refused rather than half-parsed.
        raise Unsupported("inline style= is not interpreted")

    fill = own_fill if own_fill is not None else fill
    rule = own_rule if own_rule is not None else rule
    stroke = own_stroke if own_stroke is not None else stroke
    width = own_width if own_width is not None else width

    data = None
    if tag == "path":
        data = element.get("d")
    elif tag == "circle":
        data = circle_path(number(element, "cx"), number(element, "cy"), number(element, "r"))
    elif tag == "ellipse":
        data = ellipse_path(
            number(element, "cx"), number(element, "cy"),
            number(element, "rx"), number(element, "ry"),
        )
    elif tag == "rect":
        data = rect_path(
            number(element, "x"), number(element, "y"),
            number(element, "width"), number(element, "height"),
            number(element, "rx"), number(element, "ry"),
        )

    if data:
        stroked = stroke not in (None, "none", "")
        colour = fill
        if colour in (None, "none", ""):
            # SVG's default fill is black — but only when the shape is not a stroke-only outline,
            # which is how several of these icons are drawn. Filling one of those turns an outlined
            # glyph into a solid blob, so it stays unfilled and keeps its stroke instead.
            colour = None if (fill == "none" or stroked) else "#000000"
        if colour is None and not stroked:
            data = None
        if data:
            shape = {"d": " ".join(data.split()), "rule": rule}
            gradient = gradient_of(colour, ids, viewbox) if colour and colour.startswith("url(") else None
            shape["gradient"] = gradient
            shape["fill"] = None if gradient else (normalise(colour) if colour else None)
            if stroked:
                shape["stroke"] = normalise(stroke)
                shape["width"] = width or "1"
            out.append(shape)

    for child in element:
        collect(child, fill, rule, out, drop_shadows, stroke, width, ids, viewbox)


# UI icons declare currentColor and are tinted by the caller. Android has no such keyword, so they
# are emitted opaque black and every call site must pass a tint.
TINTABLE = "#FF000000"


def normalise(colour: str) -> str:
    colour = colour.strip()
    if colour == "currentColor":
        return TINTABLE
    if colour.startswith("#") and len(colour) == 4:
        return "#" + "".join(c * 2 for c in colour[1:])
    if not colour.startswith("#"):
        raise Unsupported(f"colour {colour!r} is not a hex literal")
    return colour.upper()


def convert(
    source: Path,
    destination: Path,
    drop_shadows: bool = False,
    mirror: bool = False,
    root: Path | None = None,
    size_dp: int = 24,
) -> int:
    svg = ET.parse(source).getroot()
    width, height, offset_x, offset_y = viewport(svg)
    paths: list[dict] = []
    # <use> can point forward or into <defs>, so every id is indexed before the walk begins.
    ids = {node.get("id"): node for node in svg.iter() if node.get("id")}
    collect(svg, None, None, paths, drop_shadows, ids=ids, viewbox=(width, height))
    if not paths:
        raise Unsupported("no drawable shapes")

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by scripts/design/svg-to-vector.py from design/"
        f"{(root or ARCHIVE).name}/{source.relative_to(root or ARCHIVE)}. Do not hand-edit. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"'
        + (' xmlns:aapt="http://schemas.android.com/aapt"' if any(p.get("gradient") for p in paths) else ""),
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        f'    android:viewportWidth="{width:g}"',
        f'    android:viewportHeight="{height:g}"',
        ('    android:autoMirrored="true">' if mirror else "    >"),
    ]
    # Android's vector format has no viewBox origin, so an offset one becomes a translation on a
    # wrapping group. TradingView ships at least one icon with a -1 -1 origin — a one-pixel bleed,
    # not a mistake — and without this it would be drawn a pixel off in both axes.
    shifted = bool(offset_x or offset_y)
    pad = "    " if shifted else ""
    if shifted:
        lines.append(
            f'    <group android:translateX="{offset_x:g}" android:translateY="{offset_y:g}">'
        )
    for path in paths:
        lines.append(f"{pad}    <path")
        if path.get("fill"):
            lines.append(f'{pad}        android:fillColor="{path["fill"]}"')
        if path.get("stroke"):
            lines.append(f'{pad}        android:strokeColor="{path["stroke"]}"')
            lines.append(f'{pad}        android:strokeWidth="{path["width"]}"')
            lines.append(f'{pad}        android:strokeLineCap="round"')
            lines.append(f'{pad}        android:strokeLineJoin="round"')
        if path["rule"] == "evenodd":
            lines.append(f'{pad}        android:fillType="evenOdd"')
        data = expand_arc_flags(path["d"])
        gradient = path.get("gradient")
        if not gradient:
            lines.append(f'{pad}        android:pathData="{data}" />')
            continue
        lines.append(f'{pad}        android:pathData="{data}">')
        lines.append(f'{pad}        <aapt:attr name="android:fillColor">')
        lines.append(f'{pad}            <gradient android:type="{gradient["type"]}"')
        for name, value in gradient["coords"].items():
            lines.append(f'{pad}                android:{name}="{value:g}"')
        lines.append(f"{pad}                >")
        for offset, colour in gradient["stops"]:
            lines.append(
                f'{pad}                <item android:offset="{offset}" android:color="{colour}" />'
            )
        lines.append(f"{pad}            </gradient>")
        lines.append(f"{pad}        </aapt:attr>")
        lines.append(f"{pad}    </path>")
    if shifted:
        lines.append("    </group>")
    lines.append("</vector>")

    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(paths)


# SVG lets the two flag arguments of an arc run together with each other and with the number after
# them — `a4.4 4.4 0 00.796-1.815` is three arguments, not one. Android's path parser does not
# accept that packing, and its failure is silent: it reads some other number as the flag and draws
# an arc sweeping the wrong way, which shows up as a white blade across the artwork rather than as
# an error. A third of this archive is written that way, so every arc is re-emitted with its
# arguments separated.
_NUMBER = re.compile(r"[-+]?(?:\d*\.\d+(?:[eE][-+]?\d+)?|\d+\.?(?:[eE][-+]?\d+)?)")
_COMMAND = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]")


def expand_arc_flags(data: str) -> str:
    """Re-emit `d` with every arc's seven arguments explicitly separated."""
    out: list[str] = []
    i = 0
    command = ""
    length = len(data)

    def take_number() -> str | None:
        nonlocal i
        while i < length and data[i] in ", \t\r\n":
            i += 1
        match = _NUMBER.match(data, i)
        if not match:
            return None
        i = match.end()
        return match.group(0)

    def take_flag() -> str | None:
        """A flag is exactly one character, whatever is glued to it."""
        nonlocal i
        while i < length and data[i] in ", \t\r\n":
            i += 1
        if i < length and data[i] in "01":
            i += 1
            return data[i - 1]
        return None

    while i < length:
        char = data[i]
        if _COMMAND.match(char):
            command = char
            out.append(char)
            i += 1
            continue
        if char in ", \t\r\n":
            i += 1
            continue
        if command in "Aa":
            args = [take_number(), take_number(), take_number(), take_flag(), take_flag(),
                    take_number(), take_number()]
            if any(a is None for a in args):
                # Malformed rather than merely packed. Refused, because a half-read arc is exactly
                # the silent corruption this function exists to prevent.
                raise Unsupported("arc segment is malformed and cannot be re-emitted safely")
            out.append(" " + " ".join(args))
            continue
        number = take_number()
        if number is None:
            i += 1
            continue
        out.append(" " + number)

    return "".join(out).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("symbols", nargs="+", help="archive file names without the .svg suffix")
    parser.add_argument("--set", default="crypto-icons", help="archive subdirectory")
    parser.add_argument(
        "--ui",
        action="store_true",
        help="read from design/ui-icons rather than design/asset-logos",
    )
    parser.add_argument(
        "--mirror",
        action="store_true",
        help="mark the vector auto-mirroring, for glyphs that point along the reading direction",
    )
    parser.add_argument("--prefix", default="asset_", help="drawable name prefix")
    parser.add_argument(
        "--drop-shadows",
        action="store_true",
        help="convert artwork that carries a drop-shadow filter, without the shadow",
    )
    args = parser.parse_args()

    root = UI_ARCHIVE if args.ui else ARCHIVE
    source_dir = root / args.set
    DRAWABLE.mkdir(parents=True, exist_ok=True)

    failures = 0
    for symbol in args.symbols:
        source = source_dir / f"{symbol}.svg"
        if not source.exists():
            print(f"MISSING  {symbol}: {source.relative_to(REPO)}", file=sys.stderr)
            failures += 1
            continue
        # Android resource names allow only lowercase, digits and underscore, so the kebab-case
        # archive names are folded here rather than the archive being renamed away from upstream.
        safe = re.sub(r"[^a-z0-9_]", "_", symbol.lower())
        destination = DRAWABLE / f"{args.prefix}{safe}.xml"
        try:
            count = convert(
                source,
                destination,
                drop_shadows=args.drop_shadows,
                mirror=args.mirror,
                root=root,
            )
        except Unsupported as error:
            print(f"REFUSED  {symbol}: {error}", file=sys.stderr)
            failures += 1
            continue
        except Exception as error:  # noqa: BLE001
            # One odd file must not take the batch down with it. This started as a crash on a
            # percentage gradient coordinate, and the cost was not that one icon — the traceback
            # killed the run and two hundred and eighty perfectly good icons never got written,
            # which showed up as a suspiciously small archive rather than as an error.
            print(f"FAILED   {symbol}: {type(error).__name__}: {error}", file=sys.stderr)
            failures += 1
            continue
        print(f"ok       {symbol} -> {destination.relative_to(REPO)} ({count} paths)")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
