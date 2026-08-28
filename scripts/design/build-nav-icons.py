#!/usr/bin/env python3
"""Build the five bottom-navigation icons in both weights, from the vendored outlines.

The archive under ``design/ui-icons/nav`` holds the **outline** weight only, exactly as the vendor
draws it. The filled weight — what marks the selected tab — is derived here rather than stored,
because for four of the five there is no published filled counterpart to store.

Deriving it is not a guess. Every one of these glyphs is a font conversion: a solid outer contour
with the counter drawn inside it as a second, oppositely wound subpath. That is literally how the
outline weight is constructed, so removing the counters gives back the solid shape the outline was
cut from — the same relationship Phosphor's regular and fill weights have to each other. A subpath
is treated as a counter when its bounding box sits inside another subpath's; a wick that runs past
its candle body is therefore kept, and a bell's clapper, which does not, is not.

AI is the exception and is stored as two files, because OKX publishes both weights of it.

Run this and the ten drawables are regenerated together::

    python3 scripts/design/build-nav-icons.py

Provenance is in ``design/README.md``. The owner directed these to be taken from the exchanges'
own sets and holds the licensing question; that decision is recorded there rather than argued here.
"""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
NAV = REPO / "design" / "ui-icons" / "nav"

SVG_NS = "http://www.w3.org/2000/svg"
PATH = f"{{{SVG_NS}}}path"

# One SVG path command: its letter, then everything up to the next letter.
COMMAND = re.compile(r"([MmZzLlHhVvCcSsQqTtAa])([^MmZzLlHhVvCcSsQqTtAa]*)")

# Every number inside one command's argument list.
NUMBER = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")

# How many numbers each command takes per repetition, and where in that group the endpoint sits.
# Only the endpoint matters here: a bounding box drawn from on-path points is what decides whether
# one contour sits inside another, and a control point that overshoots the curve would make an
# enclosed counter look like it escaped its own body.
ARITY = {
    "m": (2, 0), "l": (2, 0), "t": (2, 0),
    "h": (1, 0), "v": (1, 0),
    "c": (6, 4), "s": (4, 2), "q": (4, 2),
    "a": (7, 5),
    "z": (0, 0),
}


# The five destinations, in AppDestination order. The value is the outline file's stem; a name in
# PUBLISHED_FILL has its filled weight shipped by the vendor instead of derived.
ICONS = ("home", "signals", "ai", "tools", "activity")
PUBLISHED_FILL = {"ai"}

# Markets and Chart have no glyph in the ``nav`` archive: they borrow shapes the reader has already
# met elsewhere in the app. They still need both weights, though, or two of the five tabs would mark
# selection by a shade of grey while the other three change shape — which is what shipped, and it
# read as a bar where the selection had failed to register.
#
# Markets takes Phosphor's published pair, so nothing is derived. Chart takes TradingView's
# candlestick, whose outline is built exactly like the nav glyphs — a rounded body with its counter
# as a second subpath, and the wick as a separate solid path that no counter rule touches — so the
# same `solidify` gives back the filled candles the outline was cut from.
BORROWED = (
    ("markets", REPO / "design/ui-icons/phosphor-regular/chart-line-up.svg", None),
    ("markets_fill", REPO / "design/ui-icons/phosphor-fill/chart-line-up-fill.svg", None),
    ("chart", REPO / "design/ui-icons/tradingview/chart_candles.svg", None),
    ("chart_fill", REPO / "design/ui-icons/tradingview/chart_candles.svg", "solidify"),
)


def subpaths(data: str) -> list[str]:
    """Split path data at every move-to, which is where a new contour begins."""
    return [part for part in re.split(r"(?=[Mm])", data) if part.strip()]


def contours(data: str) -> list[tuple[tuple[float, float, float, float] | None, tuple[float, float]]]:
    """The bounding box of each contour in one path, in absolute coordinates.

    Walked over the whole path in one pass rather than over each split contour on its own, and that
    is the part that matters. A contour after the first usually opens with a *relative* move-to, so
    where it actually sits depends on where the contour before it ended; measuring it in isolation
    puts it at the origin. Both mistakes were made here in turn, and neither fails loudly — a wrong
    box simply decides that nothing encloses anything, and `solidify` hands back the outline it was
    asked to fill.
    """
    boxes: list[tuple[tuple[float, float, float, float] | None, tuple[float, float]]] = []
    current: list[tuple[float, float]] = []
    x = y = 0.0
    start_x = start_y = 0.0

    def close() -> None:
        if not current:
            boxes.append((None, (start_x, start_y)))
            return
        xs = [point[0] for point in current]
        ys = [point[1] for point in current]
        boxes.append(((min(xs), min(ys), max(xs), max(ys)), (start_x, start_y)))

    for letter, argument in COMMAND.findall(data):
        lower = letter.lower()
        relative = letter.islower()
        if lower == "z":
            x, y = start_x, start_y
            continue
        size, endpoint = ARITY[lower]
        numbers = [float(value) for value in NUMBER.findall(argument)]
        for offset in range(0, len(numbers) - size + 1, size):
            group = numbers[offset:offset + size]
            if lower == "h":
                x = x + group[0] if relative else group[0]
            elif lower == "v":
                y = y + group[0] if relative else group[0]
            else:
                dx, dy = group[endpoint], group[endpoint + 1]
                x, y = (x + dx, y + dy) if relative else (dx, dy)
            # A move-to opens a contour; a repeated pair after it is an implicit line-to and stays
            # in the one it opened.
            if lower == "m" and offset == 0:
                if current:
                    close()
                current = []
                start_x, start_y = x, y
            current.append((x, y))
    close()
    return boxes


def encloses(outer: tuple, inner: tuple, tolerance: float = 0.5) -> bool:
    return (
        outer != inner
        and inner[0] >= outer[0] - tolerance
        and inner[1] >= outer[1] - tolerance
        and inner[2] <= outer[2] + tolerance
        and inner[3] <= outer[3] + tolerance
    )


def solidify(data: str) -> str:
    """Drop every counter, leaving the outer contours — the outline glyph's filled weight."""
    parts = subpaths(data)
    walked = contours(data)
    if len(walked) != len(parts):
        raise SystemExit(f"path walker and splitter disagree: {len(walked)} vs {len(parts)} contours")
    boxes = [box for box, _ in walked]
    kept = [
        # A contour that follows another usually opens with a *relative* move-to, measured from
        # where the one before it ended. Remove that one and the survivor moves. So each kept
        # contour is re-anchored to the absolute point the walk found it at — which is a no-op for
        # a contour that was already absolute, and the difference between a glyph and a smear for
        # one that was not.
        anchor(part, walked[index][1])
        for index, part in enumerate(parts)
        if boxes[index] is None
        or not any(
            boxes[other] and encloses(boxes[other], boxes[index])
            for other in range(len(parts))
            if other != index
        )
    ]
    if not kept:
        raise SystemExit("solidify removed every contour; the enclosure test is wrong")
    return "".join(kept)


def anchor(subpath: str, start: tuple[float, float]) -> str:
    """Rewrite a contour's opening move-to as an absolute one at [start]."""
    match = COMMAND.match(subpath.lstrip())
    if match is None or match.group(1) not in "Mm":
        return subpath
    numbers = NUMBER.findall(match.group(2))
    rest = subpath.lstrip()[match.end():]
    # Anything after the first pair in a move-to is an implicit line-to and is left as written; it
    # is relative to the endpoint this line is now pinning, so it still lands where it did.
    trailing = "".join(match.group(2).split(numbers[1], 1)[1:]) if len(numbers) > 2 else ""
    return f"M {start[0]:g} {start[1]:g}{trailing}{rest}"


def write_fill(name: str) -> None:
    ET.register_namespace("", SVG_NS)
    tree = ET.parse(NAV / f"{name}.svg")
    for path in tree.getroot().iter(PATH):
        data = path.get("d")
        if data:
            path.set("d", solidify(data))
        # The counters are gone, so no winding rule should be able to punch them back in.
        path.set("fill-rule", "nonzero")
    (NAV / f"{name}_fill.svg").write_text(
        "<!-- Generated by scripts/design/build-nav-icons.py from "
        f"{name}.svg. Do not hand-edit. -->\n"
        + ET.tostring(tree.getroot(), encoding="unicode"),
        encoding="utf-8",
    )


def write_borrowed() -> list[str]:
    """Stage the two borrowed glyphs into the nav archive, deriving a fill where none is published."""
    ET.register_namespace("", SVG_NS)
    staged = []
    for name, source, transform in BORROWED:
        tree = ET.parse(source)
        if transform == "solidify":
            for path in tree.getroot().iter(PATH):
                data = path.get("d")
                if data:
                    path.set("d", solidify(data))
                path.set("fill-rule", "nonzero")
        (NAV / f"{name}.svg").write_text(
            "<!-- Generated by scripts/design/build-nav-icons.py from "
            f"{source.relative_to(REPO)}. Do not hand-edit. -->\n"
            + ET.tostring(tree.getroot(), encoding="unicode"),
            encoding="utf-8",
        )
        staged.append(name)
    return staged


def main() -> int:
    derived = [name for name in ICONS if name not in PUBLISHED_FILL]
    for name in derived:
        write_fill(name)

    borrowed = write_borrowed()
    names = [name for name in ICONS] + [f"{name}_fill" for name in ICONS] + borrowed
    result = subprocess.run(
        [
            sys.executable, str(REPO / "scripts/design/svg-to-vector.py"),
            "--ui", "--set", "nav", "--prefix", "nav_", *names,
        ],
        cwd=REPO,
        text=True,
    )
    if result.returncode != 0:
        return result.returncode
    print(f"{len(names)} drawables, {len(derived)} filled weights derived")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
