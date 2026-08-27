#!/usr/bin/env python3
"""Strip the two backends' internal identifiers out of `docs/`.

The Android repository is public. These documents were written while it was private, when naming a
server module or a cache key was the shortest way to say what was meant. None of it is a credential
and none of it appears in the APK, but the CoinePro-FX team asked for it gone and they are right to:
it costs nothing to remove and it shortens the time it takes a stranger to map their estate.

What survives is the HTTP boundary — routes, payload shapes, status codes — which is what a client
document is actually about and what anybody can read off the APK anyway.

Our own scripts are left alone. They live in this repository and naming them is how a reader finds
them; the list below is derived from the tree rather than typed, so a new one is never redacted by
accident and never missed either.
"""
from __future__ import annotations

import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
DOCS = REPO / "docs"

OURS = {p.name for p in (REPO / "scripts").rglob("*.py")}
OURS |= {p.name for p in (REPO / "scripts").rglob("*.sh")}

# Ordered: the line-number form must be tried before the bare filename, or the first rule leaves a
# dangling `:103` behind.
RULES: list[tuple[re.Pattern[str], str]] = [
    # `academy.py:103` and friends — a file *and* a line is the sharpest thing in these documents.
    (re.compile(r"`?\b([a-z_][a-z_0-9]*)\.py:\d+`?"), "«سرویس مربوطه در سمت شما»"),
    # Redis keys.
    (re.compile(r"`?\bbn:[a-z_]+`?"), "«کلید کش شما»"),
    # The environment variable behind the standalone-auth mode, and the signing key's name.
    (re.compile(r"`?\bBN_STANDALONE_AUTH`?"), "«حالت احراز مستقل شما»"),
    (re.compile(r"`?\bJWT_SECRET_KEY`?"), "«کلید امضای شما»"),
    # A dead host that would send anyone who trusted it to a parked domain.
    (re.compile(r"`?\bpro-chart\.ir\b`?"), "«ترمینال»"),
]

# The lookbehind is the whole difference between a surgical edit and a mangled one. Without it,
# `check-cross-phase-consistency.py` matches at `consistency.py` — a name that is not in `OURS`,
# because `OURS` holds whole filenames — and one of this repository's own gates gets redacted out of
# its own documentation. A hyphen, a slash or a dot before the name means this is part of a longer
# token, and a longer token is a path, which is ours.
MODULE = re.compile(r"(?<![\w./-])([a-z_][a-z_0-9]*\.py)\b")


def redact(text: str) -> str:
    for pattern, replacement in RULES:
        text = pattern.sub(replacement, text)

    def module(match: re.Match[str]) -> str:
        name = match.group(1)
        # Belt and braces: exact membership, and then a suffix test, so a hyphenated script of ours
        # that slipped past the lookbehind still survives.
        if name in OURS or any(own.endswith(name) for own in OURS):
            return match.group(0)
        return "«ماژول مربوطه در سمت شما»"

    return MODULE.sub(module, text)


def main() -> int:
    check = "--check" in sys.argv
    touched = []
    for path in sorted(DOCS.rglob("*.md")):
        original = path.read_text(encoding="utf-8")
        cleaned = redact(original)
        if cleaned != original:
            touched.append(str(path.relative_to(REPO)))
            if not check:
                path.write_text(cleaned, encoding="utf-8")
    if check:
        if touched:
            print("these still name backend internals:", file=sys.stderr)
            for path in touched:
                print("  " + path, file=sys.stderr)
            return 1
        print("docs name no backend internals")
        return 0
    print(f"redacted {len(touched)} file(s)")
    for path in touched:
        print("  " + path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
