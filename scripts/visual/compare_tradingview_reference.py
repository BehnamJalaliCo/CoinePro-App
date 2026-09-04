#!/usr/bin/env python3
"""Compare a CoinePro device capture against a real TradingView Android capture.

This is Layer B of the two golden systems described in docs/design/TRADINGVIEW_VISUAL_PARITY.md.
Layer A — ``app/src/test/goldens`` — asks whether *our* screens changed since the last commit and
is allowed a small frame tolerance because both of its images come from the same renderer. This
asks a different question, against somebody else's software, and it is allowed none.

What it will not do, and the reason for each:

  * **It will not scale.** If the two frames differ in size by one pixel it reports
    ``SIZE_MISMATCH`` and stops. Resampling invents pixels that neither app drew, and everything
    measured afterwards is a measurement of the resampler.

  * **It will not register.** It does not search for the alignment that minimises the difference.
    It measures the translation that *would* be needed and fails when that is more than one pixel,
    because a whole layout being two pixels out is precisely the finding, and an automatic offset
    is how a systematically shifted UI passes a parity gate.

  * **It will not accept a lossy or unverified reference.** The pack's manifest is checked first,
    every time. See verify_reference_manifest.py.

  * **It will not invent a baseline.** With no reference pack it reports ``REFERENCE_MISSING`` for
    every screen, writes the report saying which captures are owed, and exits 3.

Masking is limited to the named semantic classes each screen's spec declares, every mask keeps a
halo so the geometry it sits inside stays compared, and the masked share of every frame is
reported. Past 15 % the run fails: at some point a masked comparison is a comparison of the mask.

Usage:

    python3 -m pip install -r scripts/visual/requirements.txt
    python3 scripts/visual/compare_tradingview_reference.py --all
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
REFERENCE_ROOT = REPO / "visual-parity" / "references"
DEFAULT_ACTUAL = REPO / "build" / "visual-parity" / "capture"
DEFAULT_OUT = REPO / "build" / "visual-parity"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_reference_manifest import MANIFEST_NAME, newest_pack, verify  # noqa: E402

# ── budgets ─────────────────────────────────────────────────────────────────────────────────────

#: Every geometry gate, in **physical pixels on the canonical device**. Not points, not a share of
#: the frame. A share of the frame is a measure of how much of the screen is wrong, which is not a
#: question anybody was asking: the questions are whether a thing is in the right place and whether
#: it is the right colour.
GEOMETRY_BUDGET_PX = 1

#: How far the whole frame may be out before the misalignment is the finding rather than noise.
REGISTRATION_BUDGET_PX = 1

#: How far the search for that translation looks. Wide enough to measure a real shift and report
#: its size; the result is never applied.
REGISTRATION_SEARCH_PX = 12

#: Colour is compared on all three channels at the centre of a fill, where anti-aliasing is not.
COLOUR_BUDGET = 0

#: Past this, a masked comparison is a comparison of the mask.
MASK_BUDGET_RATIO = 0.15

REFERENCE_MISSING = 3


# ── plumbing ────────────────────────────────────────────────────────────────────────────────────


def imaging():
    """Pillow and numpy, or a message that says what to install rather than a stack trace."""
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
    """A repo-relative path where that reads better, and an absolute one where it would lie."""
    try:
        return str(path.relative_to(REPO))
    except ValueError:
        return str(path)


@dataclass
class Finding:
    """One thing the comparison has to say. Every field is meant to be readable on its own."""

    screen: str
    measurement: str
    status: str  # PASS | FAIL | REFERENCE_MISSING | REFERENCE_ANCHOR_MISSING | ACTUAL_MISSING | SKIPPED
    detail: str
    expected: Any = None
    actual: Any = None
    delta_px: float | None = None
    budget_px: float | None = None
    label: str | None = None

    def as_dict(self) -> dict[str, Any]:
        return {k: v for k, v in self.__dict__.items() if v is not None}


@dataclass
class ScreenResult:
    screen: str
    status: str
    findings: list[Finding] = field(default_factory=list)
    masked_area_ratio: float | None = None
    outputs: dict[str, str] = field(default_factory=dict)
    known_differences: list[dict[str, Any]] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {
            "screen": self.screen,
            "status": self.status,
            "findings": [f.as_dict() for f in self.findings],
            "known_differences": self.known_differences,
        }
        if self.masked_area_ratio is not None:
            out["masked_area_ratio"] = round(self.masked_area_ratio, 6)
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
    # Rec. 601 luma. The exact weights matter little; what matters is that both sides use one.
    return (
        image[..., 0].astype("float32") * 0.299
        + image[..., 1].astype("float32") * 0.587
        + image[..., 2].astype("float32") * 0.114
    )


def edges(image):
    """A Sobel magnitude map.

    Edges rather than pixels, for the two jobs that need structure and not content: measuring how
    far the whole frame is out, and showing a reader *where* the geometry disagrees once the fills
    have been masked away.
    """
    _, np = imaging()
    g = grey(image)
    gx = np.zeros_like(g)
    gy = np.zeros_like(g)
    gx[:, 1:-1] = g[:, 2:] - g[:, :-2]
    gy[1:-1, :] = g[2:, :] - g[:-2, :]
    return np.hypot(gx, gy)


def required_translation(reference, actual, radius: int = REGISTRATION_SEARCH_PX):
    """How far the actual frame would have to move to line up — measured, never applied.

    Scored on edge maps rather than on pixels so that a colour difference does not read as a
    displacement, and searched over a small window so that a genuinely large shift comes back at
    the window's edge rather than silently finding some distant local minimum.

    Two details that decide whether the number means anything:

    * **Every candidate is scored over the same pixels.** The reference is cropped once, inset by
      the search radius, and each shift slides the *actual* across that fixed window. Scoring each
      shift over whatever happens to overlap rewards large shifts — they overlap less, and less of
      a mostly-flat frame averages lower — which is how a comparator ends up confidently reporting
      that two identical images are twelve pixels apart.
    * **Ties go to the smaller shift.** On a frame with large plain areas many offsets score
      identically, and the honest reading of a tie is that nothing moved.
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
            top = radius + dy
            left = radius + dx
            candidate = act_e[top : top + win_h, left : left + win_w]
            score = float(np.abs(window - candidate).mean())
            if best_score is None or score < best_score - 1e-9:
                best_score, best = score, (dx, dy)
            elif abs(score - best_score) <= 1e-9 and abs(dx) + abs(dy) < abs(best[0]) + abs(best[1]):
                best = (dx, dy)
    return best, best_score


def horizontal_rules(image, band: tuple[int, int] | None = None, min_span: float = 0.5):
    """The y of every horizontal rule in the image, top to bottom.

    A list's rows are separated by hairlines, and a hairline is the one feature both apps draw the
    same way whatever is written in the row. Finding them by their own contrast — rather than by
    trusting a coordinate somebody typed — is what lets row cadence be measured on the reference
    side at all.
    """
    _, np = imaging()
    g = grey(image)
    top, bottom = band if band else (0, g.shape[0])
    top = max(1, top)
    bottom = min(g.shape[0] - 1, bottom)
    if bottom <= top:
        return []
    rows = []
    for y in range(top, bottom):
        above = g[y - 1]
        here = g[y]
        below = g[y + 1]
        contrast = np.abs(here - (above + below) / 2.0)
        # A rule is a line that differs from both of its neighbours across most of the width, and
        # does so consistently — a row of text differs too, but only where the glyphs are.
        if float((contrast > 6.0).mean()) >= min_span and float(here.std()) < 24.0:
            rows.append(y)
    # Collapse runs: a two-pixel rule is one rule.
    collapsed: list[int] = []
    for y in rows:
        if collapsed and y - collapsed[-1] <= 2:
            continue
        collapsed.append(y)
    return collapsed


def rule_thickness(image, y: int) -> int:
    """How many rows the rule at ``y`` actually occupies."""
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

#: The only classes a mask may belong to. Not a suggestion: a free-form rectangle is how a parity
#: gate quietly becomes theatre, so the comparator refuses any class not on this list.
MASK_CLASSES = {
    "price",
    "percent-change",
    "timestamp",
    "avatar",
    "sparkline",
    "chart-plot",
    "user-name",
}


def build_mask(spec: dict[str, Any], elements: dict[str, list[int]], shape) -> tuple[Any, list[str]]:
    """A boolean array — True where pixels are excluded — plus every complaint about the spec.

    Each declared element's box is shrunk by its halo before being masked, so the row's own bounds,
    its dividers and its column edges stay in the comparison. A mask that swallows the edge it was
    meant to prove is not a mask, it is a deletion.
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
                x0 = max(0, x + halo)
                y0 = max(0, y + halo)
                x1 = min(shape[1], x + w - halo)
                y1 = min(shape[0], y + h - halo)
                if x1 <= x0 or y1 <= y0:
                    problems.append(
                        f"{tag}: the {halo}px halo consumes the whole element, so masking it "
                        "would remove its own bounds from the comparison"
                    )
                    continue
                mask[y0:y1, x0:x1] = True
    return mask, problems


def matching(elements: dict[str, list[int]], pattern: str):
    """Element tags matching a ``*``-pattern, with their boxes."""
    if "*" not in pattern:
        if pattern in elements:
            yield pattern, elements[pattern]
        return
    head, _, tail = pattern.partition("*")
    for tag, box in sorted(elements.items()):
        if tag.startswith(head) and tag.endswith(tail):
            yield tag, box


# ── measurements ────────────────────────────────────────────────────────────────────────────────


def anchor(locate: dict[str, Any], reference, screen: str, measurement: str):
    """Where the reference's own element is, or a finding saying nobody has said.

    Three strategies, and the third is deliberately not automated. ``horizontal_rules`` and
    ``bottom_bar`` are structural — a hairline is a hairline in either app, and it can be found by
    contrast without anybody's help. Anything else needs a human who has looked at the reference to
    say where the element is; until they have, the honest answer is that this measurement has no
    anchor, not a coordinate the script guessed.
    """
    strategy = (locate or {}).get("strategy")
    if strategy == "horizontal_rules":
        rules = horizontal_rules(reference)
        return {"kind": "rules", "rules": rules}, None
    if strategy == "bottom_bar":
        rules = horizontal_rules(reference)
        height = reference.shape[0]
        # The bar's own hairline: the lowest full-width rule that is not the very bottom row.
        candidates = [y for y in rules if y > height * 0.75 and y < height - 4]
        if not candidates:
            return None, Finding(
                screen,
                measurement,
                "FAIL",
                "No horizontal rule found in the bottom quarter of the reference, so the bar's "
                "own hairline could not be located. Either the capture is not of a screen with "
                "the bar visible, or the bar has no divider — both are findings.",
            )
        return {"kind": "bottom_bar", "divider_y": candidates[0], "frame_h": height}, None
    if strategy == "manual":
        boxes = (locate or {}).get("boxes")
        point = (locate or {}).get("point")
        if boxes or point:
            return {"kind": "manual", "boxes": boxes, "point": point}, None
        return None, Finding(
            screen,
            measurement,
            "REFERENCE_ANCHOR_MISSING",
            "This measurement is anchored by hand and no coordinates have been recorded. Somebody "
            "has to look at the reference capture and write the element's box into this spec — the "
            "script will not guess one, because a guessed anchor makes every number after it "
            "meaningless.",
        )
    return None, Finding(
        screen,
        measurement,
        "FAIL",
        f"Unknown locate strategy {strategy!r}.",
    )


def measure(spec: dict[str, Any], reference, actual, elements: dict[str, list[int]]) -> list[Finding]:
    screen = spec["screen"]
    findings: list[Finding] = []

    for item in spec.get("measurements", []):
        mid = item["id"]
        kind = item["kind"]
        budget = float(item.get("budget_px", GEOMETRY_BUDGET_PX))

        if kind == "single_node":
            findings.append(
                Finding(
                    screen,
                    mid,
                    "SKIPPED",
                    "A semantic assertion, not a pixel one. It is asserted on the device by "
                    "VisualParityCaptureTest and its result travels in the capture manifest; this "
                    "comparator does not re-derive it from an image, because counting headings in "
                    "a picture is exactly the guess this pipeline exists to avoid.",
                )
            )
            continue

        if kind == "self_stability":
            findings.append(stability(spec, item, elements, screen))
            continue

        anchored, problem = anchor(item.get("locate", {}), reference, screen, mid)
        if problem is not None:
            findings.append(problem)
            continue

        if kind == "cadence":
            findings.extend(cadence(item, anchored, elements, screen, budget))
        elif kind == "divider":
            findings.append(divider(item, anchored, reference, actual, elements, screen, budget))
        elif kind in {"element_box", "gap", "centre"}:
            findings.append(
                boxed(item, anchored, elements, screen, budget, kind)
            )
        else:
            findings.append(Finding(screen, mid, "FAIL", f"Unknown measurement kind {kind!r}."))

    return findings


def stability(spec, item, elements, screen) -> Finding:
    """Ours against ours: does the page hold still while its own data lands?

    The only measurement here that has nothing to do with TradingView. It is on this list because
    it is the fault that opened this pass, and because a steady-state comparison — against any
    reference, however perfect — is structurally incapable of seeing it.
    """
    mid = item["id"]
    before_key, after_key = item.get("compare", [None, None])
    before = elements.get(f"__{before_key}__")
    after = elements.get(f"__{after_key}__")
    if not isinstance(before, dict) or not isinstance(after, dict):
        return Finding(
            screen,
            mid,
            "ACTUAL_MISSING",
            f"The capture does not carry both states ({before_key!r} and {after_key!r}). "
            "VisualParityCaptureTest writes element boxes for each named state; without both "
            "there is nothing to compare either side of.",
        )
    budget = float(item.get("budget_px", GEOMETRY_BUDGET_PX))
    worst = 0.0
    worst_tag = None
    for tag in item.get("elements", []):
        if tag not in before or tag not in after:
            return Finding(
                screen, mid, "ACTUAL_MISSING", f"{tag} is not in both captured states."
            )
        delta = abs(before[tag][1] - after[tag][1])
        if delta > worst:
            worst, worst_tag = float(delta), tag
    status = "PASS" if worst <= budget else "FAIL"
    return Finding(
        screen,
        mid,
        status,
        f"The page moves {worst:.1f}px between {before_key} and {after_key}"
        + (f" (worst: {worst_tag})" if worst_tag else "")
        + ". The placeholder exists to make this zero.",
        delta_px=worst,
        budget_px=budget,
    )


def cadence(item, anchored, elements, screen, budget) -> list[Finding]:
    """Every gap measured against the **first**, never against its neighbour.

    Neighbour-to-neighbour hides a half-pixel that accumulates: five rows down a list that is two
    and a half pixels, and by row forty it is a slope somebody can see. Measuring against the first
    gap is what turns drift into a failure instead of into an average.
    """
    mid = item["id"]
    tags = item.get("elements", [])
    actual_tops = [elements[t][1] for t in tags if t in elements]
    if len(actual_tops) < 2:
        return [
            Finding(
                screen,
                mid,
                "ACTUAL_MISSING",
                f"Only {len(actual_tops)} of {len(tags)} rows were captured; a cadence needs at "
                "least two gaps to say anything.",
            )
        ]

    if anchored["kind"] != "rules":
        return [
            Finding(
                screen,
                mid,
                "REFERENCE_ANCHOR_MISSING",
                "A cadence is read off the reference's own hairlines; this spec anchors it some "
                "other way.",
            )
        ]

    rules = anchored["rules"]
    wanted = int(item.get("locate", {}).get("count", len(tags)))
    # The first run of rules that is evenly spaced enough to be a list rather than chrome.
    reference_gaps = pick_list_gaps(rules, wanted)
    if reference_gaps is None:
        return [
            Finding(
                screen,
                mid,
                "FAIL",
                f"Could not find {wanted} consecutive evenly-spaced rules in the reference, so "
                "its row cadence could not be read. A list whose rows are not evenly spaced is "
                "itself the finding.",
            )
        ]

    findings: list[Finding] = []
    actual_gaps = [actual_tops[i + 1] - actual_tops[i] for i in range(len(actual_tops) - 1)]

    first_ref, first_act = reference_gaps[0], actual_gaps[0]
    delta = abs(first_ref - first_act)
    findings.append(
        Finding(
            screen,
            f"{mid}/pitch",
            "PASS" if delta <= budget else "FAIL",
            f"Row pitch: reference {first_ref}px, ours {first_act}px.",
            expected=first_ref,
            actual=first_act,
            delta_px=float(delta),
            budget_px=budget,
        )
    )

    for index, gap in enumerate(actual_gaps[1:], start=1):
        drift = abs(gap - first_act)
        findings.append(
            Finding(
                screen,
                f"{mid}/drift-{index}",
                "PASS" if drift <= budget else "FAIL",
                f"Gap {index} is {gap}px against the first gap's {first_act}px — measured against "
                "the first, so drift accumulates into the number instead of out of it.",
                delta_px=float(drift),
                budget_px=budget,
            )
        )
    return findings


def pick_list_gaps(rules: list[int], count: int) -> list[int] | None:
    """The first run of ``count`` rules whose spacing is consistent enough to be a list."""
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


def divider(item, anchored, reference, actual, elements, screen, budget) -> Finding:
    mid = item["id"]
    if anchored["kind"] == "bottom_bar":
        ref_y = anchored["divider_y"]
    elif anchored["kind"] == "rules":
        rules = anchored["rules"]
        index = int(item.get("locate", {}).get("index", 0))
        if index >= len(rules):
            return Finding(
                screen, mid, "FAIL", f"The reference has no rule at index {index}."
            )
        ref_y = rules[index]
    else:
        return Finding(
            screen, mid, "REFERENCE_ANCHOR_MISSING", "A divider is located by contrast, not by hand."
        )

    ref_thickness = rule_thickness(reference, ref_y)
    tag = item.get("element")
    if tag in elements:
        act_thickness = elements[tag][3]
    else:
        act_rules = horizontal_rules(actual)
        if not act_rules:
            return Finding(
                screen, mid, "ACTUAL_MISSING", "No horizontal rule found in our own capture."
            )
        act_thickness = rule_thickness(actual, act_rules[0])

    delta = abs(ref_thickness - act_thickness)
    return Finding(
        screen,
        mid,
        "PASS" if delta <= budget else "FAIL",
        f"Divider thickness: reference {ref_thickness}px, ours {act_thickness}px. A hairline is "
        "one physical pixel at this density or it is not a hairline.",
        expected=ref_thickness,
        actual=act_thickness,
        delta_px=float(delta),
        budget_px=budget,
    )


def boxed(item, anchored, elements, screen, budget, kind) -> Finding:
    """Position, size, gap and centre — all of them a comparison of two boxes."""
    mid = item["id"]
    if anchored["kind"] == "bottom_bar" and kind == "element_box":
        # The bar's app-owned chrome: from its own hairline to the bottom of the frame. The system
        # navigation inset is inside that on both sides and is reported, never subtracted here.
        ref_value = anchored["frame_h"] - anchored["divider_y"]
        tag = item.get("element")
        if tag not in elements:
            return Finding(screen, mid, "ACTUAL_MISSING", f"{tag} is not in the capture.")
        act_value = elements[tag][3]
        delta = abs(ref_value - act_value)
        return Finding(
            screen,
            mid,
            "PASS" if delta <= budget else "FAIL",
            f"Bar height: reference {ref_value}px from its hairline to the foot of the frame, "
            f"ours {act_value}px.",
            expected=ref_value,
            actual=act_value,
            delta_px=float(delta),
            budget_px=budget,
        )

    if anchored["kind"] != "manual" or not anchored.get("boxes"):
        return Finding(
            screen,
            mid,
            "REFERENCE_ANCHOR_MISSING",
            "No recorded box for this element in the reference. Somebody has to look at the "
            "capture and write it into the spec.",
        )

    ref_boxes = anchored["boxes"]
    tags = item.get("elements") or [item.get("element") or item.get("to")]
    missing = [t for t in tags if t not in elements]
    if missing:
        return Finding(screen, mid, "ACTUAL_MISSING", f"not in the capture: {', '.join(missing)}")

    axis = item.get("axis")
    index = {"x": 0, "y": 1, "w": 2, "h": 3}
    worst = 0.0
    for position, tag in enumerate(tags):
        if position >= len(ref_boxes):
            break
        ours = elements[tag]
        theirs = ref_boxes[position]
        components = [index[axis]] if axis in index else range(4)
        for component in components:
            worst = max(worst, abs(float(ours[component]) - float(theirs[component])))

    return Finding(
        screen,
        mid,
        "PASS" if worst <= budget else "FAIL",
        f"Worst component difference across {len(tags)} element(s): {worst:.1f}px.",
        delta_px=worst,
        budget_px=budget,
    )


def sample_colours(spec, reference, actual, screen) -> list[Finding]:
    findings: list[Finding] = []
    for item in spec.get("colour_samples", []):
        sid = item["id"]
        anchored, problem = anchor(item.get("locate", {}), reference, screen, sid)
        if problem is not None:
            findings.append(problem)
            continue
        point = None
        if anchored["kind"] == "manual":
            point = anchored.get("point")
        elif anchored["kind"] == "bottom_bar":
            point = [reference.shape[1] // 2, anchored["divider_y"]]
        elif anchored["kind"] == "rules" and anchored["rules"]:
            index = int(item.get("locate", {}).get("index", 0))
            if index < len(anchored["rules"]):
                point = [reference.shape[1] // 2, anchored["rules"][index]]
        if not point:
            findings.append(
                Finding(
                    screen,
                    sid,
                    "REFERENCE_ANCHOR_MISSING",
                    "No sample point recorded. A colour is sampled at the centre of a fill and "
                    "never on an edge, so the point is a decision somebody makes while looking at "
                    "the capture.",
                )
            )
            continue
        x, y = int(point[0]), int(point[1])
        if not (0 <= y < reference.shape[0] and 0 <= x < reference.shape[1]):
            findings.append(Finding(screen, sid, "FAIL", f"Sample point {point} is off the frame."))
            continue
        theirs = [int(c) for c in reference[y, x]]
        ours = [int(c) for c in actual[y, x]]
        worst = max(abs(a - b) for a, b in zip(theirs, ours))
        findings.append(
            Finding(
                screen,
                sid,
                "PASS" if worst <= COLOUR_BUDGET else "FAIL",
                f"#{theirs[0]:02X}{theirs[1]:02X}{theirs[2]:02X} against "
                f"#{ours[0]:02X}{ours[1]:02X}{ours[2]:02X} at ({x}, {y}).",
                expected=theirs,
                actual=ours,
                delta_px=float(worst),
                budget_px=float(COLOUR_BUDGET),
            )
        )
    return findings


# ── the run ─────────────────────────────────────────────────────────────────────────────────────


def write_images(out: Path, reference, actual, mask):
    Image, np = imaging()
    out.mkdir(parents=True, exist_ok=True)

    keep = ~mask
    ref_masked = reference.copy()
    act_masked = actual.copy()
    ref_masked[mask] = 0
    act_masked[mask] = 0

    Image.fromarray(reference).save(out / "reference.png")
    Image.fromarray(actual).save(out / "actual.png")

    overlay = (reference.astype("float32") * 0.5 + actual.astype("float32") * 0.5).astype("uint8")
    Image.fromarray(overlay).save(out / "overlay-50.png")

    absolute = np.abs(ref_masked.astype("int16") - act_masked.astype("int16")).astype("uint8")
    Image.fromarray(absolute).save(out / "absolute-diff.png")

    edge = np.abs(edges(ref_masked) - edges(act_masked))
    if edge.max() > 0:
        edge = (edge / edge.max() * 255.0)
    Image.fromarray(edge.astype("uint8")).save(out / "edge-diff.png")

    preview = actual.copy()
    # Magenta, which this palette contains nowhere, so a mask cannot be mistaken for content.
    preview[mask] = [255, 0, 255]
    Image.fromarray(preview).save(out / "mask-preview.png")

    return float(mask.mean()), int(keep.sum())


def run_screen(spec: dict, pack: Path, actual_dir: Path, out_root: Path, theme: str) -> ScreenResult:
    screen = spec["screen"]
    result = ScreenResult(screen=screen, status="PASS")
    result.known_differences = spec.get("known_differences", [])

    ref_name = (spec.get("reference") or {}).get(theme)
    act_name = (spec.get("actual") or {}).get(theme)
    if not ref_name or not act_name:
        result.status = "SKIPPED"
        result.findings.append(
            Finding(screen, "capture", "SKIPPED", f"No {theme} capture is specified for {screen}.")
        )
        return result

    ref_path = pack / ref_name
    act_path = actual_dir / act_name
    if not ref_path.is_file():
        result.status = "REFERENCE_MISSING"
        result.findings.append(
            Finding(
                screen,
                "reference",
                "REFERENCE_MISSING",
                f"{ref_name} is not in the reference pack. Nothing may be substituted for it — "
                "see docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md.",
            )
        )
        return result
    if not act_path.is_file():
        result.status = "ACTUAL_MISSING"
        result.findings.append(
            Finding(
                screen,
                "actual",
                "ACTUAL_MISSING",
                f"{act_name} is not in {actual_dir}. Run VisualParityCaptureTest on the canonical "
                "device.",
            )
        )
        return result

    reference = load_rgb(ref_path)
    actual = load_rgb(act_path)

    if reference.shape != actual.shape:
        result.status = "SIZE_MISMATCH"
        result.findings.append(
            Finding(
                screen,
                "frame-size",
                "FAIL",
                f"Reference is {reference.shape[1]}×{reference.shape[0]} and ours is "
                f"{actual.shape[1]}×{actual.shape[0]}. Nothing is scaled to make these agree: a "
                "resampled frame is a frame neither app drew.",
                expected=[reference.shape[1], reference.shape[0]],
                actual=[actual.shape[1], actual.shape[0]],
            )
        )
        return result

    elements_path = actual_dir / f"{Path(act_name).stem}-elements.json"
    elements: dict[str, Any] = {}
    if elements_path.is_file():
        elements = json.loads(elements_path.read_text(encoding="utf-8"))

    mask, mask_problems = build_mask(spec, elements, actual.shape)
    for problem in mask_problems:
        result.findings.append(Finding(screen, "mask", "FAIL", problem))

    out = out_root / f"{screen}-{theme}"
    ratio, _ = write_images(out, reference, actual, mask)
    result.masked_area_ratio = ratio
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

    result.findings.append(
        Finding(
            screen,
            "masked-area",
            "PASS" if ratio <= MASK_BUDGET_RATIO else "FAIL",
            f"{ratio * 100:.2f}% of the frame is masked (budget {MASK_BUDGET_RATIO * 100:.0f}%). "
            "Past the budget a masked comparison is a comparison of the mask.",
            actual=round(ratio, 6),
            budget_px=MASK_BUDGET_RATIO,
        )
    )

    (dx, dy), score = required_translation(reference, actual)
    shift = max(abs(dx), abs(dy))
    result.findings.append(
        Finding(
            screen,
            "registration",
            "PASS" if shift <= REGISTRATION_BUDGET_PX else "FAIL",
            f"The frames line up best at ({dx}, {dy}) — measured, not applied. A layout that has "
            "to move to match is misplaced, and an automatic offset is how that passes a gate.",
            delta_px=float(shift),
            budget_px=float(REGISTRATION_BUDGET_PX),
        )
    )

    result.findings.extend(measure(spec, reference, actual, elements))
    result.findings.extend(sample_colours(spec, reference, actual, screen))

    statuses = {f.status for f in result.findings}
    if "FAIL" in statuses:
        result.status = "FAIL"
    elif {"REFERENCE_ANCHOR_MISSING", "ACTUAL_MISSING"} & statuses:
        result.status = "INCOMPLETE"
    (out / "measurement-report.json").write_text(
        json.dumps(result.as_dict(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    result.outputs["measurement-report.json"] = rel(out / "measurement-report.json")
    return result


def markdown(results: list[ScreenResult], pack: Path | None) -> str:
    lines = ["# TradingView visual parity", ""]
    if pack is None:
        lines += [
            "**`REFERENCE_MISSING`** — there is no TradingView reference pack in this repository.",
            "",
            "The pipeline is complete and runnable. What is missing is the one thing that cannot",
            "be manufactured: a capture of the real TradingView Android app on the same device",
            "profile as ours. No web screenshot, store image, JPG or resized PNG may stand in for",
            "it, so this run measures nothing and claims nothing.",
            "",
            "The capture checklist is `docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md`.",
            "",
            "**Parity status: `PARITY NOT YET PROVEN`.**",
            "",
        ]
    else:
        lines += [f"Reference pack: `{pack.name}`", ""]

    lines += ["| Screen | Status | Measurements | Failing | Masked |", "|---|---|---|---|---|"]
    for result in results:
        failing = sum(1 for f in result.findings if f.status == "FAIL")
        masked = (
            f"{result.masked_area_ratio * 100:.2f}%" if result.masked_area_ratio is not None else "—"
        )
        lines.append(
            f"| {result.screen} | `{result.status}` | {len(result.findings)} | {failing} | {masked} |"
        )
    lines.append("")

    for result in results:
        lines += [f"## {result.screen}", ""]
        for finding in result.findings:
            lines.append(f"- `{finding.status}` **{finding.measurement}** — {finding.detail}")
        if result.known_differences:
            lines += ["", "### Declared differences", ""]
            for entry in result.known_differences:
                why = entry.get("why")
                lines.append(
                    f"- `{entry['label']}` {entry['what']}" + (f" — {why}" if why else "")
                )
        lines.append("")
    return "\n".join(lines)


def validate(specs: list[dict]) -> list[str]:
    """Everything wrong with the specs themselves, before any image is opened.

    Worth having on its own because these files are the argument, not the plumbing: a mask class
    nobody named, a measurement with no budget, a declared difference with no label — each of
    those is a hole in the reasoning that would otherwise only show up on the day somebody finally
    has a reference pack in their hands, which is the worst possible day to find it.
    """
    problems: list[str] = []
    labels = {
        "INTENTIONAL_BRAND_DIFFERENCE",
        "INTENTIONAL_ACCESSIBILITY_DEVIATION",
        "DYNAMIC_CONTENT",
        "REFERENCE_VERSION_DIFFERENCE",
    }
    for spec in specs:
        screen = spec.get("screen", "<unnamed>")
        if spec.get("schema") != 1:
            problems.append(f"{screen}: unknown schema {spec.get('schema')!r}")
        if not spec.get("why"):
            problems.append(f"{screen}: no 'why'. A screen in this matrix has to earn its place.")
        for mask in spec.get("masks", []):
            if mask.get("class") not in MASK_CLASSES:
                problems.append(
                    f"{screen}: mask class {mask.get('class')!r} is not a named semantic class"
                )
            if not mask.get("reason"):
                problems.append(f"{screen}: mask {mask.get('class')!r} carries no reason")
        for item in spec.get("measurements", []):
            if not item.get("id") or not item.get("kind"):
                problems.append(f"{screen}: a measurement is missing its id or kind")
            if item.get("kind") not in {
                "element_box",
                "gap",
                "centre",
                "cadence",
                "divider",
                "single_node",
                "self_stability",
            }:
                problems.append(f"{screen}: unknown measurement kind {item.get('kind')!r}")
            budget = item.get("budget_px")
            if budget is not None and float(budget) > GEOMETRY_BUDGET_PX:
                problems.append(
                    f"{screen}/{item.get('id')}: budget {budget}px is looser than the "
                    f"{GEOMETRY_BUDGET_PX}px gate. A local slack has to be argued, not typed."
                )
        for entry in spec.get("known_differences", []):
            if entry.get("label") not in labels:
                problems.append(
                    f"{screen}: {entry.get('label')!r} is not one of the four labels. A difference "
                    "with no label is not a tolerance, it is an unfinished sentence."
                )
            if not entry.get("what"):
                problems.append(f"{screen}: a declared difference says nothing about what it is")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--screen", action="append", help="one screen; repeatable")
    parser.add_argument("--all", action="store_true", help="every screen with a spec")
    parser.add_argument("--theme", default="dark", choices=("dark", "light"))
    parser.add_argument("--pack", type=Path, default=None)
    parser.add_argument("--actual", type=Path, default=DEFAULT_ACTUAL)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument(
        "--validate",
        action="store_true",
        help="check the specs themselves and stop; needs no reference pack and no capture",
    )
    args = parser.parse_args()

    specs = sorted(SPEC_DIR.glob("*.json"))
    if args.screen:
        wanted = set(args.screen)
        specs = [p for p in specs if p.stem in wanted]
    elif not (args.all or args.validate):
        parser.error("pass --all, --screen NAME, or --validate")
    if not specs:
        print(f"No specs found under {rel(SPEC_DIR)}.")
        return 1

    loaded = [json.loads(p.read_text(encoding="utf-8")) for p in specs]

    problems = validate(loaded)
    if problems:
        print(f"FAIL {len(problems)} problem(s) in the specs:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    if args.validate:
        print(f"OK   {len(loaded)} spec(s) well-formed.")
        return 0

    pack = args.pack or newest_pack(REFERENCE_ROOT)
    args.out.mkdir(parents=True, exist_ok=True)

    if pack is None:
        results = [
            ScreenResult(
                screen=spec["screen"],
                status="REFERENCE_MISSING",
                findings=[
                    Finding(
                        spec["screen"],
                        "reference",
                        "REFERENCE_MISSING",
                        "No TradingView reference pack exists. Nothing is substituted and nothing "
                        "is claimed.",
                    )
                ],
                known_differences=spec.get("known_differences", []),
            )
            for spec in loaded
        ]
        summary = {
            "status": "REFERENCE_MISSING",
            "parity": "PARITY NOT YET PROVEN",
            "reference_pack": None,
            "screens": [r.as_dict() for r in results],
        }
        (args.out / "summary.json").write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        (args.out / "report.md").write_text(markdown(results, None), encoding="utf-8")
        print("REFERENCE_MISSING")
        print()
        print("The TradingView → CoinePro gate is halted. It has no reference to measure against,")
        print("and a baseline invented once is a baseline trusted forever by people who were not")
        print("there when it was invented.")
        print()
        print("Owed captures:")
        for spec in loaded:
            for theme, name in (spec.get("reference") or {}).items():
                if name:
                    print(f"  {spec['screen']:<12} {theme:<6} {name}")
        print()
        print("  docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md")
        print(f"  report: {rel(args.out / 'report.md')}")
        return REFERENCE_MISSING

    if not (pack / MANIFEST_NAME).is_file():
        print(f"FAIL {pack}: no {MANIFEST_NAME}. An unverified pack is not a reference.")
        return 1
    problems = verify(pack)
    if problems:
        print(f"FAIL {pack.name}: the reference pack does not verify.")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    results = [run_screen(spec, pack, args.actual, args.out, args.theme) for spec in loaded]
    failed = any(r.status in {"FAIL", "SIZE_MISMATCH"} for r in results)
    incomplete = any(r.status in {"INCOMPLETE", "REFERENCE_MISSING", "ACTUAL_MISSING"} for r in results)
    parity = "PARITY NOT YET PROVEN"
    if not failed and not incomplete:
        parity = "ZERO UNEXPLAINED DIFF"

    summary = {
        "status": "FAIL" if failed else ("INCOMPLETE" if incomplete else "PASS"),
        "parity": parity,
        "reference_pack": pack.name,
        "theme": args.theme,
        "screens": [r.as_dict() for r in results],
    }
    (args.out / "summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (args.out / "report.md").write_text(markdown(results, pack), encoding="utf-8")

    for result in results:
        print(f"{result.status:<18} {result.screen}")
    print()
    print(f"parity: {parity}")
    print(f"report: {rel(args.out / 'report.md')}")
    return 1 if failed or incomplete else 0


if __name__ == "__main__":
    sys.exit(main())
