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

# Elements Android's vector format has no faithful equivalent for. Refused rather than dropped: a
# silently missing gradient reads as a solid black coin.
UNSUPPORTED = ("linearGradient", "radialGradient", "image")

# Subtrees that define rather than draw. Skipped wholesale — what matters is whether a drawn shape
# *references* them, which is checked separately.
DEFINITIONS = ("defs", "filter", "mask", "clipPath", "symbol", "title", "desc", "metadata")

# Referencing attributes. A gradient or clip reference changes the shape itself, so it is always
# refused; a filter reference is a drop shadow in this artwork and can be dropped on request.
HARD_REFERENCES = ("mask", "clip-path")


class Unsupported(Exception):
    pass


def local(tag: str) -> str:
    return tag[len(SVG_NS):] if tag.startswith(SVG_NS) else tag


def viewport(svg: ET.Element) -> tuple[float, float]:
    box = svg.get("viewBox")
    if box:
        parts = [float(v) for v in re.split(r"[ ,]+", box.strip())]
        if len(parts) == 4:
            if parts[0] or parts[1]:
                raise Unsupported(f"viewBox origin must be 0 0, found {parts[0]} {parts[1]}")
            return parts[2], parts[3]
    width, height = svg.get("width"), svg.get("height")
    if width and height:
        return float(re.sub(r"[^0-9.]", "", width)), float(re.sub(r"[^0-9.]", "", height))
    raise Unsupported("no viewBox and no width/height")


def circle_path(cx: float, cy: float, r: float) -> str:
    """A circle as two half-arcs — the only way to express one as vector path data."""
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def ellipse_path(cx: float, cy: float, rx: float, ry: float) -> str:
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z"


def rect_path(x: float, y: float, w: float, h: float) -> str:
    return f"M{x},{y} h{w} v{h} h{-w} Z"


def number(element: ET.Element, name: str, default: float = 0.0) -> float:
    raw = element.get(name)
    return float(raw) if raw not in (None, "") else default


def collect(
    element: ET.Element,
    fill: str | None,
    rule: str | None,
    out: list[dict],
    drop_shadows: bool,
    stroke: str | None = None,
    width: str | None = None,
    ids: dict[str, ET.Element] | None = None,
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
        if element.get("rx") or element.get("ry"):
            raise Unsupported("<rect> with rounded corners")
        data = rect_path(
            number(element, "x"), number(element, "y"),
            number(element, "width"), number(element, "height"),
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
            shape["fill"] = normalise(colour) if colour else None
            if stroked:
                shape["stroke"] = normalise(stroke)
                shape["width"] = width or "1"
            out.append(shape)

    for child in element:
        collect(child, fill, rule, out, drop_shadows, stroke, width, ids)


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
    width, height = viewport(svg)
    paths: list[dict] = []
    # <use> can point forward or into <defs>, so every id is indexed before the walk begins.
    ids = {node.get("id"): node for node in svg.iter() if node.get("id")}
    collect(svg, None, None, paths, drop_shadows, ids=ids)
    if not paths:
        raise Unsupported("no drawable shapes")

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by scripts/design/svg-to-vector.py from design/"
        f"{(root or ARCHIVE).name}/{source.relative_to(root or ARCHIVE)}. Do not hand-edit. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        f'    android:viewportWidth="{width:g}"',
        f'    android:viewportHeight="{height:g}"',
        ('    android:autoMirrored="true">' if mirror else "    >"),
    ]
    for path in paths:
        lines.append("    <path")
        if path.get("fill"):
            lines.append(f'        android:fillColor="{path["fill"]}"')
        if path.get("stroke"):
            lines.append(f'        android:strokeColor="{path["stroke"]}"')
            lines.append(f'        android:strokeWidth="{path["width"]}"')
            lines.append('        android:strokeLineCap="round"')
            lines.append('        android:strokeLineJoin="round"')
        if path["rule"] == "evenodd":
            lines.append('        android:fillType="evenOdd"')
        lines.append(f'        android:pathData="{path["d"]}" />')
    lines.append("</vector>")

    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(paths)


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
        print(f"ok       {symbol} -> {destination.relative_to(REPO)} ({count} paths)")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
