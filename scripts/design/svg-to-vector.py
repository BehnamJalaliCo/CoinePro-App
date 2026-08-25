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
DRAWABLE = REPO / "core" / "designsystem" / "src" / "main" / "res" / "drawable"

SVG_NS = "{http://www.w3.org/2000/svg}"

# Elements Android's vector format has no faithful equivalent for. Refused rather than dropped: a
# silently missing gradient reads as a solid black coin.
UNSUPPORTED = ("linearGradient", "radialGradient", "use", "image")

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
) -> None:
    """Walk the tree, resolving inherited fill and fill-rule down to individual paths."""
    tag = local(element.tag)
    if tag in DEFINITIONS:
        return
    if tag in UNSUPPORTED:
        raise Unsupported(f"<{tag}> has no faithful Android equivalent")
    for attribute in HARD_REFERENCES:
        if element.get(attribute):
            raise Unsupported(f"{attribute}= changes the shape and cannot be flattened")
    if element.get("filter") and not drop_shadows:
        raise Unsupported("filter= is a drop shadow; pass --drop-shadows to convert without it")

    own_fill = element.get("fill")
    own_rule = element.get("fill-rule") or element.get("fillRule")
    if element.get("style"):
        # Presentation attributes are what these sets use; a style attribute would silently win
        # over them and change the result, so it is refused rather than half-parsed.
        raise Unsupported("inline style= is not interpreted")

    fill = own_fill if own_fill is not None else fill
    rule = own_rule if own_rule is not None else rule

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
        colour = fill
        if colour in (None, "none", ""):
            # SVG's default fill is black. A shape inside a fill="none" group that declares no
            # fill of its own is genuinely invisible, so it is dropped rather than painted.
            if fill == "none":
                data = None
            else:
                colour = "#000000"
        if data:
            out.append({"d": " ".join(data.split()), "fill": normalise(colour), "rule": rule})

    for child in element:
        collect(child, fill, rule, out, drop_shadows)


def normalise(colour: str) -> str:
    colour = colour.strip()
    if colour.startswith("#") and len(colour) == 4:
        return "#" + "".join(c * 2 for c in colour[1:])
    if not colour.startswith("#"):
        raise Unsupported(f"colour {colour!r} is not a hex literal")
    return colour.upper()


def convert(source: Path, destination: Path, drop_shadows: bool = False, size_dp: int = 24) -> int:
    svg = ET.parse(source).getroot()
    width, height = viewport(svg)
    paths: list[dict] = []
    collect(svg, None, None, paths, drop_shadows)
    if not paths:
        raise Unsupported("no drawable shapes")

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated by scripts/design/svg-to-vector.py from "
        f"design/asset-logos/{source.relative_to(ARCHIVE)}. Do not hand-edit. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        f'    android:viewportWidth="{width:g}"',
        f'    android:viewportHeight="{height:g}">',
    ]
    for path in paths:
        lines.append("    <path")
        lines.append(f'        android:fillColor="{path["fill"]}"')
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
    parser.add_argument("--prefix", default="asset_", help="drawable name prefix")
    parser.add_argument(
        "--drop-shadows",
        action="store_true",
        help="convert artwork that carries a drop-shadow filter, without the shadow",
    )
    args = parser.parse_args()

    source_dir = ARCHIVE / args.set
    DRAWABLE.mkdir(parents=True, exist_ok=True)

    failures = 0
    for symbol in args.symbols:
        source = source_dir / f"{symbol}.svg"
        if not source.exists():
            print(f"MISSING  {symbol}: {source.relative_to(REPO)}", file=sys.stderr)
            failures += 1
            continue
        destination = DRAWABLE / f"{args.prefix}{symbol.lower()}.xml"
        try:
            count = convert(source, destination, drop_shadows=args.drop_shadows)
        except Unsupported as error:
            print(f"REFUSED  {symbol}: {error}", file=sys.stderr)
            failures += 1
            continue
        print(f"ok       {symbol} -> {destination.relative_to(REPO)} ({count} paths)")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
