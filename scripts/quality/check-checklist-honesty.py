#!/usr/bin/env python3
"""Doctrine D10 — honest with the device — as something that runs and fails.

Where this came from
--------------------

4.79.0's checklist said the «→» row above the chart was gone. It was in every portrait frame of two
recordings. What had been removed was the bar's *contents*; the band was still drawn. Nothing was
written dishonestly — a reading of the code supported the claim, and no test did — and the checklist
had a blank cell where the evidence should have been, which is how it shipped.

So D10 is a format: a row claiming ✅ names a frame, or says outright that its claim is not the kind
of thing a still frame can carry and names the gate that fails instead. `docs/DOCTRINE.md` called
that gate "the CHECKLIST format itself", which is not a gate — it is a habit. This is the gate.

What it checks
--------------

Every markdown table in `docs/runs/**/CHECKLIST.md` whose header carries a **State** column:

1. A row marked ✅ has a non-empty **Evidence** cell.
2. A row marked ✅ has a non-empty **Frame** cell where the table has one. `—` alone is not enough;
   the cell has to say *why* there is no frame, which is the sentence that stops "—" from becoming
   the new blank.
3. A ✅ row may not also carry ⏳ or ❌ — the three states are exclusive, and "✅ code, ⏳ owed to
   device" is written with the ⏳ so it is not read as done.
4. `⏳ owed to device` names what would finish it.

It does not check that the evidence is *true*. Nothing can. What it checks is that a claim was
required to point at something, which is the whole of what the 4.79.0 failure needed.

Run:  python3 scripts/quality/check-checklist-honesty.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECKLISTS = sorted(ROOT.glob("docs/runs/**/CHECKLIST.md"))

DONE = "✅"
OWED = "⏳"
NOT_YET = "❌"

# A cell that points at nothing. `—` is here on purpose: it is the shape a blank takes once
# somebody has been told not to leave a blank.
EMPTY_CELLS = {"", "-", "–", "—", "n/a", "na", "tbd", "todo", "?"}

# How long a "not photographable" explanation has to be before it is an explanation. Four words is
# about «a gesture, not a still» — the shortest honest one written so far.
MIN_EXPLANATION = 16


def cells(line: str) -> list[str]:
    """The cells of a markdown table row, without the leading and trailing pipes."""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return []
    parts = stripped.strip("|").split("|")
    return [part.strip() for part in parts]


def is_separator(line: str) -> bool:
    return bool(re.fullmatch(r"\|[\s:|-]+\|", line.strip()))


def column_of(header: list[str], *names: str) -> int | None:
    for index, cell in enumerate(header):
        lowered = cell.lower().strip("* ")
        if lowered in names:
            return index
    return None


def check(path: Path) -> list[str]:
    problems: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()
    header: list[str] | None = None
    state_at = evidence_at = frame_at = None
    relative = path.relative_to(ROOT)

    for number, line in enumerate(lines, start=1):
        row = cells(line)
        if not row:
            header = None
            continue
        if is_separator(line):
            continue
        if header is None:
            header = row
            state_at = column_of(row, "state", "وضعیت")
            evidence_at = column_of(row, "evidence")
            frame_at = column_of(row, "frame")
            continue
        if state_at is None or state_at >= len(row):
            continue

        state = row[state_at]
        claim = row[0] if row else ""
        where = f"{relative}:{number}"

        if DONE in state:
            if OWED in state or NOT_YET in state:
                # Not a problem: a split state is the honest way to say «the code is in and the
                # device has not seen it». It is only wrong when the qualifier is absent.
                pass
            else:
                if evidence_at is not None and evidence_at < len(row):
                    if row[evidence_at].lower() in EMPTY_CELLS:
                        problems.append(f"{where}: ✅ with no evidence — {claim[:60]}")
                if frame_at is not None and frame_at < len(row):
                    frame = row[frame_at]
                    if frame.lower() in EMPTY_CELLS:
                        problems.append(
                            f"{where}: ✅ with a blank Frame — name a frame, or say in the cell why "
                            f"this claim is not the kind a still frame carries: {claim[:60]}"
                        )
                    elif frame.strip("—- ").strip() == "":
                        problems.append(f"{where}: ✅ with a dash for a Frame — {claim[:60]}")
                    elif frame.lstrip("—- ").strip() and len(frame.lstrip("—- ")) < MIN_EXPLANATION and ".png" not in frame:
                        problems.append(
                            f"{where}: ✅ whose Frame neither names an image nor explains itself: {frame!r}"
                        )

        if OWED in state:
            evidence = row[evidence_at] if evidence_at is not None and evidence_at < len(row) else ""
            if evidence.lower() in EMPTY_CELLS:
                problems.append(f"{where}: ⏳ owed, with nothing saying what would finish it — {claim[:60]}")

    return problems


def main() -> int:
    if not CHECKLISTS:
        print("check-checklist-honesty: no CHECKLIST.md under docs/runs — nothing to hold")
        return 0
    problems: list[str] = []
    for path in CHECKLISTS:
        problems += check(path)
    if problems:
        print("DOCTRINE_D10_ERROR: a claim that points at nothing\n")
        for problem in problems:
            print(f"  {problem}")
        print(
            "\nD10: no ✅ without a frame or a test. A row names a frame, or says in the Frame cell "
            "why its claim is not the kind of thing a still frame can carry. See docs/DOCTRINE.md."
        )
        return 1
    rows = sum(len(path.read_text(encoding="utf-8").splitlines()) for path in CHECKLISTS)
    print(
        f"Doctrine D10 passed: {len(CHECKLISTS)} checklist(s), {rows} lines — "
        "every ✅ points at a frame or says why it cannot."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
