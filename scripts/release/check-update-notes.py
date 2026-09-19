#!/usr/bin/env python3
"""`docs/release/UPDATE_NOTES.md` is read by a machine now, so its shape is a contract.

Where this came from
--------------------

The file was written as prose — a place for whoever published a release to put the two sentences a
reader sees on the update card, instead of writing them from a boilerplate release body. Then the
Pro Chart server started **parsing** it: `app-latest.py` reads the entry for the version being
published, unwraps the blockquote, flattens the line breaks and puts the result straight into
`notes_fa` and `notes_en`.

That turned a document into an interface, and an interface nobody checks is one that breaks
silently. The failure mode is specific and bad: reformat a heading, or bump the version without
writing an entry, and the server finds nothing. It does not crash — it publishes a release whose
card says nothing, or falls back to whatever it can find, and **nobody notices, because the card is
on a screen most readers open twice a year.** This product has met that shape of fault more than
once (`docs/runs/RUN_ALEF/CHECKLIST.md` §א7 is a list of them); a gate is how it stops being a shape
it can meet again.

What it checks
--------------

1. **The current version has an entry.** `version.properties` says `MAJOR.MINOR.PATCH`; there must
   be a `## MAJOR.MINOR.PATCH` heading. Bumping the version without writing the note is the most
   likely mistake and the one with no other symptom.
2. **Every entry parses**, by exactly the rule the server uses: an `**fa**` and an `**en**` block,
   each a blockquote, each non-empty once flattened.
3. **Both languages are present in every entry.** A note that exists in one language is a card that
   is blank for half the readership.
4. **The Persian obeys the house orthography** that `tools/i18n/lint_strings.py` enforces in
   `strings.xml` — «به‌روز» not «بروز», no hamza-on-heh — because this text reaches a reader on the
   same screen as those strings and nothing else was checking it.
5. **Neither language runs long.** It is a card, not a changelog. 400 characters is about six lines
   on a phone, and past that the button moves below the fold.

It prints what the server will read, with the character counts, so a CI log answers «what is on the
card?» without anybody opening a browser.

Run:  python3 scripts/release/check-update-notes.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "docs" / "release" / "UPDATE_NOTES.md"
VERSION_FILE = ROOT / "version.properties"

LANGUAGES = ("fa", "en")

# A card, not a changelog. Six-ish lines on a phone.
MAX_CHARS = 400

# The same two rules the string lint applies to Persian copy, for the same reason: this text lands
# on the same screen. Not the whole glossary — these are the two that are always wrong.
PERSIAN_FAULTS = (
    (re.compile(r"بروز"), "«بروز» is the wrong spelling — «به‌روز», with a ZWNJ"),
    (re.compile("[ٔۀ]"), "hamza-on-heh — the app writes «نسخه‌ی», not «نسخهٔ»"),
)

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def released_version() -> str:
    """`MAJOR.MINOR.PATCH` from `version.properties`, without the build number.

    The build is not in the key on purpose: the server keys the lookup on the *released* version, so
    5.0.0+4 reads 5.0.0's entry. A note per build would be a note nobody writes.
    """
    values = {}
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key, _, value = stripped.partition("=")
            values[key.strip()] = value.strip()
    return ".".join(values.get(part, "?") for part in ("MAJOR", "MINOR", "PATCH"))


def entries(text: str) -> dict[str, dict[str, str]]:
    """Every `## X.Y.Z` entry, parsed the way `app-latest.py` parses it."""
    found: dict[str, dict[str, str]] = {}
    for match in re.finditer(r"(?m)^## (\d+\.\d+\.\d+)\s*$(.*?)(?=^## |\Z)", text, re.S):
        version, body = match.group(1), match.group(2)
        note: dict[str, str] = {}
        for language in LANGUAGES:
            block = re.search(
                r"(?m)^\*\*" + language + r"\*\*\s*$(.*?)(?=^\*\*|^---|\Z)", body, re.S
            )
            if block is None:
                continue
            # Blockquote lines only, `>` stripped, joined with a space — the server's own rule.
            lines = [
                line[1:].strip()
                for line in block.group(1).strip().splitlines()
                if line.startswith(">")
            ]
            flattened = " ".join(lines).strip()
            if flattened:
                note[language] = flattened
        found[version] = note
    return found


def main() -> int:
    if not NOTES.exists():
        print(f"UPDATE_NOTES_ERROR: {NOTES.relative_to(ROOT)} is missing.", file=sys.stderr)
        return 1

    text = NOTES.read_text(encoding="utf-8")
    parsed = entries(text)
    if not parsed:
        fail("no `## X.Y.Z` entries at all — the server would find nothing to publish")

    current = released_version()
    if current not in parsed:
        fail(
            f"version.properties is {current} and there is no `## {current}` entry. "
            "Write it before the release: it is the sentence on the update card."
        )

    for version, note in sorted(parsed.items()):
        for language in LANGUAGES:
            if language not in note:
                fail(f"{version}: no `**{language}**` blockquote, or it is empty")
        for language, body in note.items():
            if len(body) > MAX_CHARS:
                fail(f"{version} ({language}): {len(body)} characters; the card's budget is {MAX_CHARS}")
        for pattern, message in PERSIAN_FAULTS:
            if "fa" in note and pattern.search(note["fa"]):
                fail(f"{version} (fa): {message}")

    if failures:
        print("UPDATE_NOTES_ERROR: the update card's text does not parse or does not obey the house rules\n", file=sys.stderr)
        for message in failures:
            print(f"  {message}", file=sys.stderr)
        print(
            "\nThis file is read by the Pro Chart server (`app-latest.py`) and its shape is a "
            "contract. See docs/release/UPDATE_NOTES.md and docs/web/SERVER.md §4.6.",
            file=sys.stderr,
        )
        return 1

    print(f"Update notes parse: {len(parsed)} entr{'y' if len(parsed) == 1 else 'ies'}, {current} present.")
    for language, body in sorted(parsed[current].items()):
        print(f"  {current} {language}: {len(body)} chars — {body}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
