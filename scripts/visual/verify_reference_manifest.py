#!/usr/bin/env python3
"""Prove that a TradingView reference pack is what it says it is.

A pixel golden is a claim about somebody else's software on a particular device on a particular
day. A pack without provenance cannot support that claim, and a pack whose files no longer match
its manifest is worse than none at all — it looks authoritative and is not.

So this refuses a pack that:

  * has no ``reference-manifest.json``;
  * is missing any required field of source or device metadata;
  * lists an image that is not on disk, or has an image on disk that the manifest does not list;
  * contains anything that is not a PNG — a JPG or a WebP is lossy and cannot carry an exact
    colour, so it can never be a colour reference;
  * contains a PNG whose pixel size is not the device resolution the manifest declares, which is
    what a resized or cropped-by-a-viewer image looks like from here;
  * has a single checksum that does not match.

Exit status is 0 for a verified pack, 1 for a pack that fails verification, and 3 when there is no
pack at all — which is not a failure of this script, it is the documented state of this repository.
See docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
REFERENCES = REPO / "visual-parity" / "references"
MANIFEST_NAME = "reference-manifest.json"

REQUIRED_SOURCE = ("app", "package", "version_name", "version_code", "captured_at", "captured_by")
REQUIRED_DEVICE = (
    "model",
    "api_level",
    "resolution",
    "density_dpi",
    "font_scale",
    "navigation_mode",
    "orientation",
)
REQUIRED_IMAGE = ("file", "screen", "theme", "locale", "sha256")

NO_PACK = 3


def packs(root: Path) -> list[Path]:
    """Every directory under ``references`` that carries a manifest, newest name last."""
    if not root.is_dir():
        return []
    return sorted(p for p in root.iterdir() if p.is_dir() and (p / MANIFEST_NAME).is_file())


def newest_pack(root: Path) -> Path | None:
    found = packs(root)
    return found[-1] if found else None


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def png_size(path: Path) -> tuple[int, int] | None:
    """The pixel size out of the IHDR chunk, without needing an imaging library.

    Read from the file's own header rather than from a decoder, so that a file which merely has a
    ``.png`` extension is caught here rather than three steps later in the comparator.
    """
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        return None
    return int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")


def verify(pack: Path) -> list[str]:
    """Every complaint about this pack, in the order they were found."""
    problems: list[str] = []
    manifest_path = pack / MANIFEST_NAME
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        return [f"{manifest_path}: not valid JSON — {error}"]

    if manifest.get("schema") != 1:
        problems.append(f"{manifest_path}: unknown schema {manifest.get('schema')!r}, expected 1")

    source = manifest.get("source") or {}
    for field in REQUIRED_SOURCE:
        if not source.get(field):
            problems.append(
                f"source.{field} is missing. A reference pack without it cannot be told apart "
                "from the next one, and REFERENCE_VERSION_DIFFERENCE becomes unarguable."
            )

    device = manifest.get("device") or {}
    for field in REQUIRED_DEVICE:
        if device.get(field) in (None, ""):
            problems.append(
                f"device.{field} is missing. Every one of these is read off the device with adb; "
                "none of them is assumed."
            )

    resolution = device.get("resolution")
    expected_size: tuple[int, int] | None = None
    if isinstance(resolution, list) and len(resolution) == 2:
        expected_size = (int(resolution[0]), int(resolution[1]))

    images = manifest.get("images") or []
    if not images:
        problems.append("the manifest lists no images")

    listed: set[str] = set()
    for entry in images:
        name = entry.get("file")
        if not name:
            problems.append("an image entry has no 'file'")
            continue
        listed.add(name)
        for field in REQUIRED_IMAGE:
            if entry.get(field) in (None, ""):
                problems.append(f"{name}: {field} is missing")

        path = pack / name
        if not path.is_file():
            problems.append(f"{name}: listed in the manifest and not on disk")
            continue

        if path.suffix.lower() != ".png":
            problems.append(
                f"{name}: not a PNG. JPG and WebP are lossy, so a colour read from one is a "
                "colour the encoder invented. See Rule Zero."
            )
            continue

        size = png_size(path)
        if size is None:
            problems.append(f"{name}: has a .png name and is not a PNG file")
            continue
        if expected_size and size != expected_size and not entry.get("crop"):
            problems.append(
                f"{name}: is {size[0]}×{size[1]} and the device is "
                f"{expected_size[0]}×{expected_size[1]}. An uncropped capture that is not the "
                "device's own resolution has been resized, and a resized reference is not one."
            )

        actual = sha256(path)
        if actual != entry.get("sha256"):
            problems.append(
                f"{name}: checksum does not match — manifest says {entry.get('sha256')}, "
                f"the file is {actual}. The file that was measured is not the file that is here."
            )

    on_disk = {p.name for p in pack.iterdir() if p.is_file() and p.name != MANIFEST_NAME}
    for stray in sorted(on_disk - listed):
        problems.append(
            f"{stray}: on disk and not in the manifest. An unlisted image has no provenance, "
            "which is the one thing a reference has to have."
        )

    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--pack",
        type=Path,
        default=None,
        help="a specific pack directory; the default is the newest one under visual-parity/references",
    )
    parser.add_argument("--all", action="store_true", help="verify every pack, not just the newest")
    args = parser.parse_args()

    if args.pack:
        chosen = [args.pack]
    elif args.all:
        chosen = packs(REFERENCES)
    else:
        newest = newest_pack(REFERENCES)
        chosen = [newest] if newest else []

    if not chosen:
        print("REFERENCE_MISSING")
        print()
        print(f"No TradingView reference pack under {REFERENCES.relative_to(REPO)}.")
        print()
        print("This is the documented state of this repository, not a broken script. A pixel")
        print("golden can only come from the real TradingView Android app on the same device")
        print("profile as our own capture, and nobody has taken one yet. Nothing may be")
        print("substituted for it — not a web screenshot, not a store image, not a JPG, not a")
        print("resized PNG.")
        print()
        print("To produce one, work through:")
        print("  docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md")
        return NO_PACK

    failed = False
    for pack in chosen:
        if not (pack / MANIFEST_NAME).is_file():
            print(f"FAIL {pack}: no {MANIFEST_NAME}")
            failed = True
            continue
        problems = verify(pack)
        if problems:
            failed = True
            print(f"FAIL {pack.name}")
            for problem in problems:
                print(f"  - {problem}")
        else:
            print(f"OK   {pack.name}")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
