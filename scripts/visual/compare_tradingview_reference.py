#!/usr/bin/env python3
"""Compare a CoinePro device capture against a real TradingView Android capture.

Layer B of the two golden systems in ``docs/design/TRADINGVIEW_VISUAL_PARITY.md``. Layer A —
``app/src/test/goldens`` — asks whether *our* screens changed since the last commit, and is allowed
a small frame tolerance because both of its images come from the same renderer. This asks a
different question, against somebody else's software, and is allowed none.

What it will not do, and why each:

  * **It will not scale.** A one-pixel size difference is ``SIZE_MISMATCH`` and a stop. Resampling
    invents pixels neither app drew, and every measurement afterwards measures the resampler.
  * **It will not register.** It does not search for the alignment that minimises the difference.
    It *measures* the translation that would be needed and fails past one pixel — a layout two
    pixels out is the finding, and an automatic offset is how a systematically shifted UI passes.
  * **It will not guess an anchor.** An anchor the reference cannot supply is ``ANCHOR_MISSING``
    and a non-zero exit, not a quietly skipped row.
  * **It will not accept a lossy or unverified reference.** The manifest is checked first, always.
  * **It will not invent a baseline.** With no pack it reports ``REFERENCE_MISSING`` and exits 3.

Failure has a vocabulary, and every word in it exits non-zero:

    REFERENCE_MISSING (3) · REFERENCE_INVALID (4) · SIZE_MISMATCH (2) · ANCHOR_MISSING (5)
    MASK_BUDGET_EXCEEDED (6) · GEOMETRY_FAIL (1) · STATIC_DIFF_FAIL (1)

``REFERENCE_MISSING`` is not success.

Usage::

    python3 -m pip install -r scripts/visual/requirements.txt
    python3 scripts/visual/compare_tradingview_reference.py --all --level certification
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[2]
SPEC_DIR = REPO / "visual-parity" / "specs"
DEFAULT_ACTUAL = REPO / "build" / "visual-parity" / "capture"
DEFAULT_OUT = REPO / "build" / "visual-parity"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_reference_manifest import (  # noqa: E402
    MANIFEST_NAME,
    REFERENCE_ROOT,
    newest_pack,
    verify,
)

# ── exit codes ──────────────────────────────────────────────────────────────────────────────────

EXIT_PASS = 0
EXIT_FAIL = 1           # GEOMETRY_FAIL, STATIC_DIFF_FAIL
EXIT_SIZE_MISMATCH = 2
EXIT_REFERENCE_MISSING = 3
EXIT_REFERENCE_INVALID = 4
EXIT_ANCHOR_MISSING = 5
EXIT_MASK_BUDGET = 6

# ── budgets ─────────────────────────────────────────────────────────────────────────────────────

#: Every geometry gate, in **physical pixels on the canonical device**. Not points, not a share of
#: the frame. A share of the frame measures how much of the screen is wrong, which is not the
#: question: the questions are whether a thing is in the right place and the right colour.
GEOMETRY_BUDGET_PX = 1

#: Target rather than budget. One is the allowance; zero is what a correct layout produces.
GEOMETRY_TARGET_PX = 0

REGISTRATION_BUDGET_PX = 1
REGISTRATION_SEARCH_PX = 12

#: Colour is compared on all three channels at the centre of a fill, where anti-aliasing is not.
COLOUR_BUDGET = 0

#: How far a channel may move before a pixel counts as *statically* different. Small enough that a
#: changed token cannot hide inside it; wide enough that a glyph's anti-aliased edge does not
#: register as a layout fault. Geometry is judged by anchors, not by this.
STATIC_CHANNEL_SLACK = 8

#: Mask budgets, and there are two on purpose. Development is for debugging a screen you are still
#: working on. Certification is the one a release is allowed to quote, and 15 % of a frame is far
#: too much to leave unexamined when the claim is Zero Unexplained Diff.
MASK_BUDGET = {"development": 0.15, "certification": 0.08}

#: Anything not classified is a bug. These are the only words a difference may carry.
CLASSIFICATIONS = {
    "BUG",
    "INTENTIONAL_BRAND_DIFFERENCE",
    "INTENTIONAL_ACCESSIBILITY_DEVIATION",
    "COPY_DIFFERENCE",
    "DYNAMIC_CONTENT",
    "REFERENCE_VERSION_DIFFERENCE",
}

#: The only classes of thing a mask may cover. A free-form rectangle is how a parity gate quietly
#: becomes theatre, so anything not on this list is refused.
MASK_CLASSES = {
    "price",
    "percent-change",
    "timestamp",
    "avatar",
    "sparkline",
    "chart-plot",
    "user-name",
    "symbol-artwork",
    "brand-mark",
    "app-copy",
    "system-chrome",
}


# ── plumbing ────────────────────────────────────────────────────────────────────────────────────


def imaging():
    """Pillow and numpy, or a message naming what to install rather than a stack trace."""
    try:
        import numpy as np
        from PIL import Image
    except ImportError as error:  # pragma: no cover - environment, not logic
        raise SystemExit(
            f"{error}\n\nThis comparator needs Pillow and numpy, pinned:\n"
            "    python3 -m pip install -r scripts/visual/requirements.txt\n"
            "They are development-only; nothing here ships in the app."
        ) from error
    return Image, np


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(REPO))
    except ValueError:
        return str(path)


@dataclass
class Finding:
    """One thing the comparison has to say. Every field readable on its own."""

    screen: str
    measurement: str
    status: str
    detail: str
    expected: Any = None
    actual: Any = None
    delta_px: float | None = None
    budget_px: float | None = None

    def as_dict(self) -> dict[str, Any]:
        return {k: v for k, v in self.__dict__.items() if v is not None}


@dataclass
class ScreenResult:
    screen: str
    status: str
    findings: list[Finding] = field(default_factory=list)
    metrics: dict[str, Any] = field(default_factory=dict)
    outputs: dict[str, str] = field(default_factory=dict)
    known_differences: list[dict[str, Any]] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "screen": self.screen,
            "status": self.status,
            "metrics": self.metrics,
            "findings": [f.as_dict() for f in self.findings],
            "known_differences": self.known_differences,
        }
        if self.outputs:
            out["outputs"] = self.outputs
        return out


# ── image work ──────────────────────────────────────────────────────────────────────────────────


def load_rgb(path: Path):
    Image, np = imaging()
    with Image.open(path) as handle:
        if handle.format != "PNG":
            raise ValueError(
                f"{path.name} is {handle.format}, not PNG. A lossy image cannot carry an exact "
                "colour and is not admissible here. See Rule Zero."
            )
        return np.asarray(handle.convert("RGB"), dtype="uint8")


def grey(image):
    _, np = imaging()
    return (
        image[..., 0].astype("float32") * 0.299
        + image[..., 1].astype("float32") * 0.587
        + image[..., 2].astype("float32") * 0.114
    )


def edges(image):
    """A Sobel magnitude map — structure rather than content."""
    _, np = imaging()
    g = grey(image)
    gx = np.zeros_like(g)
    gy = np.zeros_like(g)
    gx[:, 1:-1] = g[:, 2:] - g[:, :-2]
    gy[1:-1, :] = g[2:, :] - g[:-2, :]
    return np.hypot(gx, gy)


def distance_to(mask):
    """Chamfer distance, in pixels, from every point to the nearest True in ``mask``.

    Two sweeps of a 3×3 kernel — forward then backward — the classic two-pass approximation, exact
    enough at the scale that matters here, where the question is whether an edge moved by one pixel
    or by nine. Written out rather than imported so the dependency list stays at Pillow and numpy.

    The along-a-row propagation looks like it needs a loop and does not. ``d[x] = min(d[x],
    d[x-1] + 1)`` over a whole row is a running minimum in disguise: subtract the column index,
    take a cumulative minimum, add it back. On a 1080 × 2400 frame that is the difference between
    a comparator that answers in a moment and one nobody runs twice.
    """
    _, np = imaging()
    big = float(mask.shape[0] + mask.shape[1])
    d = np.where(mask, 0.0, big).astype("float32")
    diag = 1.41421356
    columns = np.arange(d.shape[1], dtype="float32")

    def sweep_row(row):
        np.minimum(row, columns + np.minimum.accumulate(row - columns), out=row)
        flipped = row[::-1]
        np.minimum(flipped, columns + np.minimum.accumulate(flipped - columns), out=flipped)

    for y in range(1, d.shape[0]):
        prev = d[y - 1]
        row = d[y]
        np.minimum(row, prev + 1.0, out=row)
        np.minimum(row[1:], prev[:-1] + diag, out=row[1:])
        np.minimum(row[:-1], prev[1:] + diag, out=row[:-1])
        sweep_row(row)

    for y in range(d.shape[0] - 2, -1, -1):
        nxt = d[y + 1]
        row = d[y]
        np.minimum(row, nxt + 1.0, out=row)
        np.minimum(row[1:], nxt[:-1] + diag, out=row[1:])
        np.minimum(row[:-1], nxt[1:] + diag, out=row[:-1])
        sweep_row(row)

    return d


def required_translation(reference, actual, radius: int = REGISTRATION_SEARCH_PX):
    """How far the actual frame would have to move to line up — measured, never applied.

    Two details decide whether the number means anything:

    * **Every candidate is scored over the same pixels.** The reference is cropped once, inset by
      the search radius, and each shift slides the actual across that fixed window. Scoring each
      shift over whatever happens to overlap rewards large shifts — they overlap less, and less of
      a mostly-flat frame averages lower — which is how a comparator confidently reports that two
      identical images are twelve pixels apart.
    * **Ties go to the smaller shift.** On a frame with large plain areas many offsets score the
      same, and the honest reading of a tie is that nothing moved.
    """
    _, np = imaging()
    ref_e = edges(reference)
    act_e = edges(actual)
    height, width = ref_e.shape
    if height <= 2 * radius or width <= 2 * radius:
        return (0, 0), 0.0

    window = ref_e[radius : height - radius, radius : width - radius]
    win_h, win_w = window.shape
    best = (0, 0)
    best_score = None
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            candidate = act_e[radius + dy : radius + dy + win_h, radius + dx : radius + dx + win_w]
            score = float(np.abs(window - candidate).mean())
            if best_score is None or score < best_score - 1e-9:
                best_score, best = score, (dx, dy)
            elif abs(score - best_score) <= 1e-9 and abs(dx) + abs(dy) < abs(best[0]) + abs(best[1]):
                best = (dx, dy)
    return best, best_score


def horizontal_rules(image, min_span: float = 0.5):
    """The y of every horizontal rule, top to bottom.

    A list's rows are separated by hairlines, and a hairline is the one feature both apps draw the
    same way whatever is written in the row. Finding them by their own contrast — rather than
    trusting a coordinate somebody typed — is what lets row cadence be read off the reference side.
    """
    _, np = imaging()
    g = grey(image)
    rows = []
    for y in range(1, g.shape[0] - 1):
        here = g[y]
        contrast = np.abs(here - (g[y - 1] + g[y + 1]) / 2.0)
        if float((contrast > 6.0).mean()) >= min_span and float(here.std()) < 24.0:
            rows.append(y)
    collapsed: list[int] = []
    for y in rows:
        if collapsed and y - collapsed[-1] <= 2:
            continue
        collapsed.append(y)
    return collapsed


def rule_thickness(image, y: int) -> int:
    _, np = imaging()
    g = grey(image)
    base = g[y]
    thickness = 1
    for probe in range(y + 1, min(g.shape[0], y + 6)):
        if float(np.abs(g[probe] - base).mean()) < 3.0:
            thickness += 1
        else:
            break
    return thickness


# ── masks ───────────────────────────────────────────────────────────────────────────────────────


def matching(elements: dict[str, Any], pattern: str):
    if "*" not in pattern:
        if isinstance(elements.get(pattern), list):
            yield pattern, elements[pattern]
        return
    head, _, tail = pattern.partition("*")
    for tag, box in sorted(elements.items()):
        if isinstance(box, list) and tag.startswith(head) and tag.endswith(tail):
            yield tag, box


def build_mask(spec: dict[str, Any], elements: dict[str, Any], shape) -> tuple[Any, list[str]]:
    """A boolean array — True where pixels are excluded — plus every complaint about the spec.

    Each declared element's box is shrunk by its halo before being masked, so the row's own bounds,
    its dividers and its column edges stay in the comparison. **A mask that swallows the edge it
    was meant to prove is not a mask, it is a deletion** — so a halo that consumes the element is
    an error rather than a silently larger rectangle.
    """
    _, np = imaging()
    mask = np.zeros(shape[:2], dtype=bool)
    problems: list[str] = []
    for entry in spec.get("masks", []):
        klass = entry.get("class")
        if klass not in MASK_CLASSES:
            problems.append(
                f"mask class {klass!r} is not one of the named semantic classes "
                f"({', '.join(sorted(MASK_CLASSES))}). Free-form regions are not allowed."
            )
            continue
        if not entry.get("reason"):
            problems.append(f"mask class {klass!r} carries no reason")
        halo = int(entry.get("halo_px", 1))
        for pattern in entry.get("elements", []):
            for tag, box in matching(elements, pattern):
                x, y, w, h = box
                x0, y0 = max(0, x + halo), max(0, y + halo)
                x1, y1 = min(shape[1], x + w - halo), min(shape[0], y + h - halo)
                if x1 <= x0 or y1 <= y0:
                    problems.append(
                        f"{tag}: the {halo}px halo consumes the whole element, so masking it would "
                        "remove its own bounds from the comparison"
                    )
                    continue
                mask[y0:y1, x0:x1] = True
    return mask, problems


# ── anchors ─────────────────────────────────────────────────────────────────────────────────────

EDGES = {
    "top": lambda b: float(b[1]),
    "bottom": lambda b: float(b[1] + b[3]),
    "left": lambda b: float(b[0]),
    "right": lambda b: float(b[0] + b[2]),
    "centerX": lambda b: b[0] + b[2] / 2.0,
    "centerY": lambda b: b[1] + b[3] / 2.0,
    "width": lambda b: float(b[2]),
    "height": lambda b: float(b[3]),
}


def actual_value(locator: dict[str, Any], elements: dict[str, Any]) -> tuple[float | None, str]:
    """One number off our own capture, or why there isn't one."""
    tag = locator.get("tag")
    if locator.get("baseline"):
        key = f"{tag}@baseline"
        value = elements.get(key)
        if not isinstance(value, (int, float)):
            return None, f"{key} is not in the capture — VisualParityCaptureTest emits no baseline for it"
        return float(value), ""
    box = elements.get(tag)
    if not isinstance(box, list) or len(box) != 4:
        return None, f"{tag} is not in the capture"
    edge = locator.get("edge", "top")
    if edge not in EDGES:
        return None, f"unknown edge {edge!r}"
    return EDGES[edge](box), ""


def reference_value(
    locator: dict[str, Any], reference, cache: dict[str, Any]
) -> tuple[float | None, str]:
    """One number off their capture, or why there isn't one.

    Three strategies, and the third is deliberately not automated. ``bottom_bar`` and
    ``horizontal_rules`` are **structural** — a hairline is a hairline in either app and can be
    found by contrast without anybody's help. Anything else needs a person who has looked at the
    reference to say where the element is; until they have, the honest answer is that this anchor
    is missing, not a coordinate the script guessed.
    """
    strategy = locator.get("strategy")

    if strategy == "manual":
        value = locator.get("value")
        if isinstance(value, (int, float)):
            return float(value), ""
        return None, (
            "anchored by hand and no coordinate recorded. Somebody has to look at the reference "
            "capture and write the number into this spec; a guessed anchor makes every number "
            "after it meaningless."
        )

    if "rules" not in cache:
        cache["rules"] = horizontal_rules(reference)
    rules = cache["rules"]

    if strategy == "bottom_bar":
        height = reference.shape[0]
        candidates = [y for y in rules if height * 0.75 < y < height - 4]
        if not candidates:
            return None, (
                "no horizontal rule in the bottom quarter of the reference, so the bar's own "
                "hairline could not be located — either the capture does not show the bar, or the "
                "bar has no divider, and both are findings"
            )
        divider = candidates[0]
        part = locator.get("part", "divider")
        if part == "divider":
            return float(divider), ""
        if part == "bottom":
            return float(height), ""
        if part == "height":
            return float(height - divider), ""
        return None, f"unknown bottom_bar part {part!r}"

    if strategy == "horizontal_rules":
        index = int(locator.get("index", 0))
        if index >= len(rules):
            return None, f"the reference has no horizontal rule at index {index}"
        return float(rules[index]), ""

    return None, f"unknown reference strategy {strategy!r}"


def check_anchors(spec, reference, elements, screen) -> tuple[list[Finding], float | None]:
    findings: list[Finding] = []
    cache: dict[str, Any] = {}
    worst: float | None = None

    for anchor in spec.get("anchors", []):
        aid = anchor["id"]
        budget = float(anchor.get("budget_px", GEOMETRY_BUDGET_PX))

        ours, why_ours = actual_value(anchor.get("actual", {}), elements)
        theirs, why_theirs = reference_value(anchor.get("reference", {}), reference, cache)

        if theirs is None:
            findings.append(Finding(screen, aid, "ANCHOR_MISSING", f"reference: {why_theirs}"))
            continue
        if ours is None:
            findings.append(Finding(screen, aid, "ANCHOR_MISSING", f"actual: {why_ours}"))
            continue

        delta = abs(ours - theirs)
        worst = delta if worst is None else max(worst, delta)
        findings.append(
            Finding(
                screen,
                aid,
                "PASS" if delta <= budget else "GEOMETRY_FAIL",
                f"TradingView {theirs:.1f}px, CoinePro {ours:.1f}px "
                f"(target {GEOMETRY_TARGET_PX}px, budget {budget:g}px).",
                expected=round(theirs, 1),
                actual=round(ours, 1),
                delta_px=round(delta, 2),
                budget_px=budget,
            )
        )
    return findings, worst


def check_cadence(spec, reference, elements, screen) -> list[Finding]:
    """Every gap measured against the **first**, never against its neighbour.

    Neighbour-to-neighbour hides a half-pixel that accumulates: five rows down a list that is two
    and a half pixels, and by row forty it is a slope somebody can see. Measuring against the first
    gap turns drift into a failure instead of into an average.
    """
    findings: list[Finding] = []
    cache: dict[str, Any] = {}

    for item in spec.get("cadence", []):
        cid = item["id"]
        budget = float(item.get("budget_px", GEOMETRY_BUDGET_PX))
        tags = item.get("tags", [])
        tops = [elements[t][1] for t in tags if isinstance(elements.get(t), list)]
        if len(tops) < 3:
            findings.append(
                Finding(
                    screen,
                    cid,
                    "ANCHOR_MISSING",
                    f"only {len(tops)} of {len(tags)} rows were captured; a cadence needs at least "
                    "two gaps to say anything about drift.",
                )
            )
            continue

        if "rules" not in cache:
            cache["rules"] = horizontal_rules(reference)
        wanted = int(item.get("reference_count", len(tags)))
        ref_gaps = pick_list_gaps(cache["rules"], wanted)
        if ref_gaps is None:
            findings.append(
                Finding(
                    screen,
                    cid,
                    "ANCHOR_MISSING",
                    f"could not find {wanted} consecutive evenly-spaced rules in the reference, so "
                    "its row cadence could not be read.",
                )
            )
            continue

        gaps = [tops[i + 1] - tops[i] for i in range(len(tops) - 1)]
        delta = abs(ref_gaps[0] - gaps[0])
        findings.append(
            Finding(
                screen,
                f"{cid}/pitch",
                "PASS" if delta <= budget else "GEOMETRY_FAIL",
                f"Row pitch: TradingView {ref_gaps[0]}px, CoinePro {gaps[0]}px.",
                expected=ref_gaps[0],
                actual=gaps[0],
                delta_px=float(delta),
                budget_px=budget,
            )
        )
        for index, gap in enumerate(gaps[1:], start=1):
            drift = abs(gap - gaps[0])
            findings.append(
                Finding(
                    screen,
                    f"{cid}/drift-{index}",
                    "PASS" if drift <= budget else "GEOMETRY_FAIL",
                    f"Gap {index} is {gap}px against the first gap's {gaps[0]}px — measured "
                    "against the first, so drift accumulates into the number, not out of it.",
                    delta_px=float(drift),
                    budget_px=budget,
                )
            )
    return findings


def pick_list_gaps(rules: list[int], count: int) -> list[int] | None:
    if len(rules) < count:
        return None
    for start in range(0, len(rules) - count + 1):
        window = rules[start : start + count]
        gaps = [window[i + 1] - window[i] for i in range(len(window) - 1)]
        if not gaps or min(gaps) < 8:
            continue
        if max(gaps) - min(gaps) <= 2:
            return gaps
    return None


def check_stability(spec, elements_by_state, screen) -> list[Finding]:
    """Ours against ours: does the page hold still while its own data lands?

    The only measurement here with nothing to do with TradingView. It is on the list because it is
    the fault that opened this pass, and because a steady-state comparison — against any reference,
    however perfect — is structurally incapable of seeing it.
    """
    findings: list[Finding] = []
    for item in spec.get("self_stability", []):
        sid = item["id"]
        budget = float(item.get("budget_px", GEOMETRY_BUDGET_PX))
        before_key, after_key = item.get("compare", [None, None])
        before = elements_by_state.get(before_key)
        after = elements_by_state.get(after_key)
        if not before or not after:
            findings.append(
                Finding(
                    screen,
                    sid,
                    "ANCHOR_MISSING",
                    f"the capture does not carry both states ({before_key!r} and {after_key!r}).",
                )
            )
            continue
        worst, worst_tag = 0.0, None
        missing = None
        for tag in item.get("tags", []):
            a, b = before.get(tag), after.get(tag)
            if not isinstance(a, list) or not isinstance(b, list):
                missing = tag
                break
            delta = abs(EDGES[item.get("edge", "top")](a) - EDGES[item.get("edge", "top")](b))
            if delta > worst:
                worst, worst_tag = delta, tag
        if missing:
            findings.append(
                Finding(screen, sid, "ANCHOR_MISSING", f"{missing} is not in both captured states.")
            )
            continue
        findings.append(
            Finding(
                screen,
                sid,
                "PASS" if worst <= budget else "GEOMETRY_FAIL",
                f"The page moves {worst:.1f}px between {before_key} and {after_key}"
                + (f" (worst: {worst_tag})" if worst_tag else "")
                + ". The placeholder exists to make this zero.",
                delta_px=round(worst, 2),
                budget_px=budget,
            )
        )
    return findings


def check_colours(spec, reference, actual, screen) -> list[Finding]:
    findings: list[Finding] = []
    cache: dict[str, Any] = {}
    for item in spec.get("colour_samples", []):
        sid = item["id"]
        point = item.get("point")
        if not (isinstance(point, list) and len(point) == 2):
            locator = item.get("reference", {})
            y, why = reference_value(locator, reference, cache)
            if y is None:
                findings.append(Finding(screen, sid, "ANCHOR_MISSING", f"no sample point: {why}"))
                continue
            point = [reference.shape[1] // 2, int(y)]
        x, y = int(point[0]), int(point[1])
        if not (0 <= y < reference.shape[0] and 0 <= x < reference.shape[1]):
            findings.append(Finding(screen, sid, "GEOMETRY_FAIL", f"sample point {point} is off the frame"))
            continue
        theirs = [int(c) for c in reference[y, x]]
        ours = [int(c) for c in actual[y, x]]
        worst = max(abs(a - b) for a, b in zip(theirs, ours))
        findings.append(
            Finding(
                screen,
                sid,
                "PASS" if worst <= COLOUR_BUDGET else "GEOMETRY_FAIL",
                f"#{theirs[0]:02X}{theirs[1]:02X}{theirs[2]:02X} against "
                f"#{ours[0]:02X}{ours[1]:02X}{ours[2]:02X} at ({x}, {y}).",
                expected=theirs,
                actual=ours,
                delta_px=float(worst),
                budget_px=float(COLOUR_BUDGET),
            )
        )
    return findings


# ── artefacts ───────────────────────────────────────────────────────────────────────────────────


def write_artefacts(out: Path, reference, actual, mask) -> dict[str, float]:
    """The six pictures and the numbers that come out of making them."""
    Image, np = imaging()
    out.mkdir(parents=True, exist_ok=True)

    keep = ~mask
    ref_m, act_m = reference.copy(), actual.copy()
    ref_m[mask] = 0
    act_m[mask] = 0

    Image.fromarray(reference).save(out / "reference.png")
    Image.fromarray(actual).save(out / "actual.png")
    Image.fromarray(
        (reference.astype("float32") * 0.5 + actual.astype("float32") * 0.5).astype("uint8")
    ).save(out / "overlay-50.png")

    channel_diff = np.abs(reference.astype("int16") - actual.astype("int16"))
    Image.fromarray(np.where(mask[..., None], 0, channel_diff).astype("uint8")).save(
        out / "absolute-diff.png"
    )

    # **Edge diff in two colours, not in grey.** Reference edges cyan, ours red, agreement white.
    # A grey magnitude map says "something differs here"; two colours side by side say *which way
    # it moved*, which is the only thing a person can act on without opening a measuring tool.
    ref_e, act_e = edges(ref_m), edges(act_m)
    threshold = 24.0
    ref_edge, act_edge = ref_e > threshold, act_e > threshold
    edge_img = np.zeros(reference.shape, dtype="uint8")
    edge_img[..., 0] = np.where(act_edge, 255, 0)
    edge_img[..., 1] = np.where(ref_edge, 255, 0)
    edge_img[..., 2] = np.where(ref_edge, 255, 0)
    edge_img[ref_edge & act_edge] = (255, 255, 255)
    Image.fromarray(edge_img).save(out / "edge-diff.png")

    preview = actual.copy()
    preview[mask] = (255, 0, 255)  # magenta: a colour this palette contains nowhere
    Image.fromarray(preview).save(out / "mask-preview.png")

    # How far each of our edges sits from the nearest of theirs. Max is the worst single
    # displacement; mean is whether the whole frame drifted or one element did.
    shifts = distance_to(ref_edge)[act_edge & keep]
    max_shift = float(shifts.max()) if shifts.size else 0.0
    mean_shift = float(shifts.mean()) if shifts.size else 0.0

    unexplained = (channel_diff.max(axis=2) > STATIC_CHANNEL_SLACK) & keep
    count = int(unexplained.sum())

    return {
        "maskedAreaRatio": float(mask.mean()),
        "maxEdgeShiftPx": round(max_shift, 2),
        "meanEdgeShiftPx": round(mean_shift, 3),
        "unexplainedStaticPixelCount": count,
        "unexplainedStaticPixelRatio": round(count / float(mask.size), 6),
    }


# ── one screen ──────────────────────────────────────────────────────────────────────────────────


def load_elements(actual_dir: Path, name: str) -> dict[str, Any]:
    path = actual_dir / f"{Path(name).stem}-elements.json"
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def run_screen(spec, pack: Path, actual_dir: Path, out_root: Path, theme: str, level: str):
    screen = spec["screen"]
    result = ScreenResult(screen=screen, status="PASS")
    result.known_differences = spec.get("known_differences", [])

    ref_name = (spec.get("reference") or {}).get(theme)
    act_name = (spec.get("actual") or {}).get(theme)
    if not ref_name or not act_name:
        result.status = "SKIPPED"
        result.findings.append(
            Finding(screen, "capture", "SKIPPED", f"no {theme} capture is specified for {screen}.")
        )
        return result

    ref_path, act_path = pack / ref_name, actual_dir / act_name
    if not ref_path.is_file():
        result.status = "REFERENCE_MISSING"
        result.findings.append(
            Finding(
                screen,
                "reference",
                "REFERENCE_MISSING",
                f"{ref_name} is not in the reference pack. Nothing may be substituted for it — see "
                "docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md.",
            )
        )
        return result
    if not act_path.is_file():
        result.status = "ANCHOR_MISSING"
        result.findings.append(
            Finding(
                screen,
                "actual",
                "ANCHOR_MISSING",
                f"{act_name} is not in {rel(actual_dir)}. Run VisualParityCaptureTest on the "
                "canonical device.",
            )
        )
        return result

    reference, actual = load_rgb(ref_path), load_rgb(act_path)
    if reference.shape != actual.shape:
        result.status = "SIZE_MISMATCH"
        result.findings.append(
            Finding(
                screen,
                "frame-size",
                "SIZE_MISMATCH",
                f"TradingView is {reference.shape[1]}×{reference.shape[0]} and CoinePro is "
                f"{actual.shape[1]}×{actual.shape[0]}. Nothing is scaled to make these agree: a "
                "resampled frame is a frame neither app drew.",
                expected=[reference.shape[1], reference.shape[0]],
                actual=[actual.shape[1], actual.shape[0]],
            )
        )
        return result

    elements = load_elements(actual_dir, act_name)
    elements_by_state = {
        state: load_elements(actual_dir, name)
        for state, name in (spec.get("actual") or {}).items()
        if name
    }

    mask, mask_problems = build_mask(spec, elements, actual.shape)
    for problem in mask_problems:
        result.findings.append(Finding(screen, "mask", "MASK_BUDGET_EXCEEDED", problem))

    out = out_root / f"{screen}-{theme}"
    metrics = write_artefacts(out, reference, actual, mask)
    result.outputs = {
        name: rel(out / name)
        for name in (
            "reference.png",
            "actual.png",
            "overlay-50.png",
            "absolute-diff.png",
            "edge-diff.png",
            "mask-preview.png",
        )
    }

    budget = float((spec.get("mask_budget") or {}).get(level, MASK_BUDGET[level]))
    ratio = metrics["maskedAreaRatio"]
    result.findings.append(
        Finding(
            screen,
            "masked-area",
            "PASS" if ratio <= budget else "MASK_BUDGET_EXCEEDED",
            f"{ratio * 100:.2f}% of the frame is masked ({level} budget {budget * 100:.0f}%). "
            "Past the budget a masked comparison is a comparison of the mask.",
            actual=round(ratio, 6),
            budget_px=budget,
        )
    )

    (dx, dy), _ = required_translation(reference, actual)
    shift = max(abs(dx), abs(dy))
    result.findings.append(
        Finding(
            screen,
            "registration",
            "PASS" if shift <= REGISTRATION_BUDGET_PX else "GEOMETRY_FAIL",
            f"The frames line up best at ({dx}, {dy}) — measured, not applied. A layout that has "
            "to move to match is misplaced, and an automatic offset is how that passes a gate.",
            delta_px=float(shift),
            budget_px=float(REGISTRATION_BUDGET_PX),
        )
    )

    anchor_findings, worst_anchor = check_anchors(spec, reference, elements, screen)
    result.findings += anchor_findings
    result.findings += check_cadence(spec, reference, elements, screen)
    result.findings += check_stability(spec, elements_by_state, screen)
    result.findings += check_colours(spec, reference, actual, screen)

    for assertion in spec.get("semantic_assertions", []):
        result.findings.append(
            Finding(
                screen,
                assertion["id"],
                "SKIPPED",
                assertion.get("note", "")
                + " Asserted on the device by the instrumented suite, not re-derived from an image.",
            )
        )

    metrics["maxAnchorDriftPx"] = round(worst_anchor, 2) if worst_anchor is not None else None
    metrics["level"] = level
    result.metrics = metrics

    result.findings.append(
        Finding(
            screen,
            "unexplained-static-pixels",
            "PASS" if metrics["unexplainedStaticPixelCount"] == 0 else "STATIC_DIFF_FAIL",
            f"{metrics['unexplainedStaticPixelCount']} pixels outside every declared mask differ "
            f"by more than {STATIC_CHANNEL_SLACK}/255 on a channel "
            f"({metrics['unexplainedStaticPixelRatio'] * 100:.4f}% of the frame). The gate is "
            "zero — every differing pixel is either fixed or inside a named, argued exception.",
            actual=metrics["unexplainedStaticPixelCount"],
            budget_px=0,
        )
    )

    statuses = {f.status for f in result.findings}
    for bad in ("SIZE_MISMATCH", "MASK_BUDGET_EXCEEDED", "GEOMETRY_FAIL", "STATIC_DIFF_FAIL", "ANCHOR_MISSING"):
        if bad in statuses:
            result.status = bad
            break

    (out / "measurement-report.json").write_text(
        json.dumps(result.as_dict(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    result.outputs["measurement-report.json"] = rel(out / "measurement-report.json")
    return result


# ── spec validation ─────────────────────────────────────────────────────────────────────────────


def validate(specs: list[dict]) -> list[str]:
    """Everything wrong with the specs themselves, before an image is opened.

    Worth having on its own because these files are the argument, not the plumbing: a mask class
    nobody named, an anchor with a budget looser than the gate, a declared difference with no
    classification. Each is a hole in the reasoning that would otherwise surface on the day
    somebody finally has a reference pack in their hands — the worst day to find it.
    """
    problems: list[str] = []
    for spec in specs:
        screen = spec.get("screen", "<unnamed>")
        if spec.get("schema") != 2:
            problems.append(f"{screen}: unknown schema {spec.get('schema')!r}, expected 2")
        if not spec.get("why"):
            problems.append(f"{screen}: no 'why'. A screen in this matrix has to earn its place.")

        for mask in spec.get("masks", []):
            if mask.get("class") not in MASK_CLASSES:
                problems.append(f"{screen}: mask class {mask.get('class')!r} is not a named class")
            if not mask.get("reason"):
                problems.append(f"{screen}: mask {mask.get('class')!r} carries no reason")

        budgets = spec.get("mask_budget") or {}
        for level, value in budgets.items():
            if level not in MASK_BUDGET:
                problems.append(f"{screen}: unknown mask budget level {level!r}")
            elif float(value) > MASK_BUDGET[level]:
                problems.append(
                    f"{screen}: {level} mask budget {value} is looser than the global "
                    f"{MASK_BUDGET[level]}. A screen may tighten this, never loosen it."
                )

        ids: set[str] = set()
        for anchor in spec.get("anchors", []):
            aid = anchor.get("id")
            if not aid:
                problems.append(f"{screen}: an anchor has no id")
            elif aid in ids:
                problems.append(f"{screen}: duplicate anchor id {aid!r}")
            ids.add(aid)
            if not anchor.get("actual") or not anchor.get("reference"):
                problems.append(f"{screen}/{aid}: an anchor needs both an actual and a reference locator")
            edge = (anchor.get("actual") or {}).get("edge")
            if edge is not None and edge not in EDGES:
                problems.append(f"{screen}/{aid}: unknown edge {edge!r}")
            budget = anchor.get("budget_px")
            if budget is not None and float(budget) > GEOMETRY_BUDGET_PX:
                problems.append(
                    f"{screen}/{aid}: budget {budget}px is looser than the {GEOMETRY_BUDGET_PX}px "
                    "gate. Local slack has to be argued, not typed."
                )

        for entry in spec.get("known_differences", []):
            if entry.get("label") not in CLASSIFICATIONS:
                problems.append(
                    f"{screen}: {entry.get('label')!r} is not one of the classifications. A "
                    "difference with no classification is not a tolerance, it is an unfinished "
                    "sentence."
                )
            if not entry.get("what"):
                problems.append(f"{screen}: a declared difference says nothing about what it is")
    return problems


# ── reporting ───────────────────────────────────────────────────────────────────────────────────


def markdown(results: list[ScreenResult], pack: Path | None, level: str) -> str:
    lines = ["# TradingView visual parity", ""]
    if pack is None:
        lines += [
            "**`REFERENCE_MISSING`** — there is no TradingView reference pack in this repository.",
            "",
            "The pipeline is complete and runnable. What is missing is the one thing that cannot be",
            "manufactured: a capture of the real TradingView Android app on the same device profile",
            "as ours. No web screenshot, store image, JPG or resized PNG may stand in for it, so",
            "this run measures nothing and claims nothing.",
            "",
            "Capture checklist: `docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md`.",
            "",
            "**Parity status: `PARITY NOT YET PROVEN`.**",
            "",
        ]
    else:
        lines += [f"Reference pack: `{pack.name}` · certification level: `{level}`", ""]

    lines += [
        "| Screen | Result | Max anchor drift | Max edge shift | Mean edge shift | Masked | Unexplained static px |",
        "|---|---|---:|---:|---:|---:|---:|",
    ]
    for r in results:
        m = r.metrics
        def num(key, suffix="px"):
            v = m.get(key)
            return "—" if v is None else f"{v}{suffix}"
        masked = "—" if m.get("maskedAreaRatio") is None else f"{m['maskedAreaRatio'] * 100:.2f}%"
        static = m.get("unexplainedStaticPixelCount")
        lines.append(
            f"| {r.screen} | `{r.status}` | {num('maxAnchorDriftPx')} | {num('maxEdgeShiftPx')} | "
            f"{num('meanEdgeShiftPx')} | {masked} | {'—' if static is None else static} |"
        )
    lines.append("")

    for r in results:
        lines += [f"## {r.screen}", ""]
        for f in r.findings:
            lines.append(f"- `{f.status}` **{f.measurement}** — {f.detail}")
        if r.known_differences:
            lines += ["", "### Declared differences", ""]
            for entry in r.known_differences:
                why = entry.get("why")
                lines.append(f"- `{entry['label']}` {entry['what']}" + (f" — {why}" if why else ""))
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--screen", action="append", help="one screen; repeatable")
    parser.add_argument("--all", action="store_true", help="every screen with a spec")
    parser.add_argument("--theme", default="dark", choices=("dark", "light"))
    parser.add_argument(
        "--level",
        default="certification",
        choices=tuple(MASK_BUDGET),
        help="which mask budget applies; certification is the one a release may quote",
    )
    parser.add_argument("--pack", type=Path, default=None)
    parser.add_argument("--actual", type=Path, default=DEFAULT_ACTUAL)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--validate", action="store_true", help="check the specs and stop")
    args = parser.parse_args()

    spec_files = sorted(SPEC_DIR.glob("*.json"))
    if args.screen:
        wanted = set(args.screen)
        spec_files = [p for p in spec_files if p.stem in wanted]
    elif not (args.all or args.validate):
        parser.error("pass --all, --screen NAME, or --validate")
    if not spec_files:
        print(f"No specs found under {rel(SPEC_DIR)}.")
        return EXIT_FAIL

    specs = [json.loads(p.read_text(encoding="utf-8")) for p in spec_files]

    problems = validate(specs)
    if problems:
        print(f"FAIL {len(problems)} problem(s) in the specs:")
        for problem in problems:
            print(f"  - {problem}")
        return EXIT_FAIL
    if args.validate:
        print(f"OK   {len(specs)} spec(s) well-formed.")
        return EXIT_PASS

    pack = args.pack or newest_pack()
    args.out.mkdir(parents=True, exist_ok=True)

    if pack is None:
        results = [
            ScreenResult(
                screen=s["screen"],
                status="REFERENCE_MISSING",
                findings=[
                    Finding(
                        s["screen"],
                        "reference",
                        "REFERENCE_MISSING",
                        "No TradingView reference pack exists. Nothing is substituted and nothing "
                        "is claimed.",
                    )
                ],
                known_differences=s.get("known_differences", []),
            )
            for s in specs
        ]
        summary = {
            "status": "REFERENCE_MISSING",
            "parity": "PARITY NOT YET PROVEN",
            "reference_pack": None,
            "level": args.level,
            "screens": [r.as_dict() for r in results],
        }
        (args.out / "summary.json").write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        (args.out / "report.md").write_text(markdown(results, None, args.level), encoding="utf-8")
        print("REFERENCE_MISSING")
        print()
        print("The TradingView → CoinePro gate is halted. It has no reference to measure against,")
        print("and a baseline invented once is a baseline trusted forever by people who were not")
        print("there when it was invented. This is a failure, not a pass.")
        print()
        print("Owed captures:")
        for s in specs:
            for theme, name in (s.get("reference") or {}).items():
                if name:
                    print(f"  {s['screen']:<12} {theme:<14} {name}")
        print()
        print(f"  expected under: {rel(REFERENCE_ROOT)}/<versionName>/")
        print("  checklist:      docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md")
        print(f"  report:         {rel(args.out / 'report.md')}")
        return EXIT_REFERENCE_MISSING

    if not (pack / MANIFEST_NAME).is_file():
        print(f"REFERENCE_INVALID {pack}: no {MANIFEST_NAME}. An unverified pack is not a reference.")
        return EXIT_REFERENCE_INVALID
    invalid = verify(pack)
    if invalid:
        print(f"REFERENCE_INVALID {pack.name}: the reference pack does not verify.")
        for problem in invalid:
            print(f"  - {problem}")
        return EXIT_REFERENCE_INVALID

    results = [run_screen(s, pack, args.actual, args.out, args.theme, args.level) for s in specs]

    statuses = {r.status for r in results}
    exit_code = EXIT_PASS
    for status, code in (
        ("SIZE_MISMATCH", EXIT_SIZE_MISMATCH),
        ("MASK_BUDGET_EXCEEDED", EXIT_MASK_BUDGET),
        ("ANCHOR_MISSING", EXIT_ANCHOR_MISSING),
        ("GEOMETRY_FAIL", EXIT_FAIL),
        ("STATIC_DIFF_FAIL", EXIT_FAIL),
        ("REFERENCE_MISSING", EXIT_REFERENCE_MISSING),
    ):
        if status in statuses:
            exit_code = code
            break

    parity = "ZERO UNEXPLAINED DIFF" if exit_code == EXIT_PASS else "PARITY NOT YET PROVEN"
    summary = {
        "status": "PASS" if exit_code == EXIT_PASS else sorted(statuses - {"PASS", "SKIPPED"})[0],
        "parity": parity,
        "reference_pack": pack.name,
        "theme": args.theme,
        "level": args.level,
        "screens": [r.as_dict() for r in results],
    }
    (args.out / "summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (args.out / "report.md").write_text(markdown(results, pack, args.level), encoding="utf-8")

    for r in results:
        print(f"{r.status:<22} {r.screen}")
    print()
    print(f"parity: {parity}")
    print(f"report: {rel(args.out / 'report.md')}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
