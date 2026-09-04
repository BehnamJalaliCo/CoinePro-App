#!/usr/bin/env python3
"""Prove that a TradingView reference pack is what it says it is.

A pixel golden is a claim about somebody else's software, on a particular device, on a particular
day, at a particular version of their app. A pack without that provenance cannot support the
claim; a pack whose files no longer match its manifest is worse than none at all, because it looks
authoritative and is not.

So this refuses a pack that:

  * has no ``reference-manifest.json``;
  * is missing any required field — **including ``versionName`` and ``versionCode`` read off the
    device**, because a reference that is not tied to a version of their app cannot ever be
    distinguished from the next one, and ``REFERENCE_VERSION_DIFFERENCE`` becomes unarguable;
  * lists an image that is not on disk, or leaves an image on disk that the manifest does not list;
  * contains anything that is not a PNG — JPG and WebP are lossy, so a colour read from one is a
    colour the encoder invented;
  * contains a PNG whose pixel size is neither the device resolution nor the declared crop, which
    is what a resized or viewer-cropped image looks like from here;
  * has a single checksum that does not match.

Exit status: 0 verified, 4 ``REFERENCE_INVALID``, 3 ``REFERENCE_MISSING`` — which is not a failure
of this script but the documented state of this repository. See
``docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md``.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

#: Where a pack belongs, versioned by *their* app version.
REFERENCE_ROOT = REPO / "docs" / "design" / "reference" / "tradingview-android"

#: The first home of this directory, still read so an older pack is not orphaned by a rename.
LEGACY_ROOT = REPO / "visual-parity" / "references"

MANIFEST_NAME = "reference-manifest.json"

REQUIRED_TOP = (
    "package",
    "versionName",
    "versionCode",
    "device",
    "resolution",
    "densityDpi",
    "fontScale",
    "orientation",
    "theme",
    "locale",
    "captureMethod",
    "captureDate",
)

REFERENCE_MISSING = 3
REFERENCE_INVALID = 4


def packs(*roots: Path) -> list[Path]:
    """Every directory under the given roots that carries a manifest."""
    found: list[Path] = []
    for root in roots:
        if root.is_dir():
            found += [p for p in sorted(root.iterdir()) if p.is_dir() and (p / MANIFEST_NAME).is_file()]
    return found


def all_packs() -> list[Path]:
    return packs(REFERENCE_ROOT, LEGACY_ROOT)


def newest_pack(_: Path | None = None) -> Path | None:
    """The last pack by directory name — packs are named by version, so the last is the newest."""
    found = all_packs()
    return found[-1] if found else None


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def png_size(path: Path) -> tuple[int, int] | None:
    """Pixel size out of the IHDR chunk, read from the file's own header.

    From the header rather than through a decoder, so a file that merely *has* a ``.png``
    extension is caught here rather than three steps later inside the comparator.
    """
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        return None
    return int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")


def declared_sizes(manifest: dict) -> list[tuple[int, int]]:
    """The sizes a capture in this pack is allowed to be: the screen, and the declared crop."""
    sizes: list[tuple[int, int]] = []
    resolution = manifest.get("resolution")
    if isinstance(resolution, str) and "x" in resolution:
        w, _, h = resolution.partition("x")
        if w.strip().isdigit() and h.strip().isdigit():
            sizes.append((int(w), int(h)))
    elif isinstance(resolution, list) and len(resolution) == 2:
        sizes.append((int(resolution[0]), int(resolution[1])))

    crop = manifest.get("crop")
    if isinstance(crop, dict) and {"left", "top", "right", "bottom"} <= crop.keys():
        sizes.append((int(crop["right"]) - int(crop["left"]), int(crop["bottom"]) - int(crop["top"])))
    return sizes


def verify(pack: Path) -> list[str]:
    """Every complaint about this pack, in the order they were found."""
    problems: list[str] = []
    manifest_path = pack / MANIFEST_NAME
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        return [f"{manifest_path}: not valid JSON — {error}"]

    for field in REQUIRED_TOP:
        if manifest.get(field) in (None, "", []):
            problems.append(
                f"{field} is missing. Every one of these is read off the device with adb; none is "
                "assumed, and a pack that cannot name the version of their app it came from can "
                "never be told apart from the next one."
            )

    sizes = declared_sizes(manifest)
    if not sizes:
        problems.append("resolution is missing or unreadable, so no capture size can be checked")

    checksums = manifest.get("sha256")
    if not isinstance(checksums, dict) or not checksums:
        problems.append(
            "sha256 is missing or empty. Without a checksum per screenshot there is no way to say "
            "that the file measured is the file committed."
        )
        checksums = {}

    on_disk = {p.name for p in pack.iterdir() if p.is_file() and p.name != MANIFEST_NAME}

    for name, expected in checksums.items():
        path = pack / name
        if not path.is_file():
            problems.append(f"{name}: listed in the manifest and not on disk")
            continue
        if path.suffix.lower() != ".png":
            problems.append(
                f"{name}: not a PNG. JPG and WebP are lossy, so a colour read from one is a colour "
                "the encoder invented. See Rule Zero."
            )
            continue
        size = png_size(path)
        if size is None:
            problems.append(f"{name}: has a .png name and is not a PNG file")
            continue
        if sizes and size not in sizes:
            allowed = " or ".join(f"{w}×{h}" for w, h in sizes)
            problems.append(
                f"{name}: is {size[0]}×{size[1]} and the manifest declares {allowed}. A capture "
                "that is neither the screen nor the declared crop has been resized, and a resized "
                "reference is not one."
            )
        actual = sha256(path)
        if actual != expected:
            problems.append(
                f"{name}: checksum does not match — manifest says {expected}, the file is "
                f"{actual}. The file that was measured is not the file that is here."
            )

    for stray in sorted(on_disk - set(checksums)):
        problems.append(
            f"{stray}: on disk and not in the manifest. An unlisted image has no provenance, which "
            "is the one thing a reference has to have."
        )

    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pack", type=Path, default=None, help="a specific pack directory")
    parser.add_argument("--all", action="store_true", help="verify every pack, not just the newest")
    args = parser.parse_args()

    if args.pack:
        chosen = [args.pack]
    elif args.all:
        chosen = all_packs()
    else:
        newest = newest_pack()
        chosen = [newest] if newest else []

    if not chosen:
        print("REFERENCE_MISSING")
        print()
        print(f"No TradingView reference pack under {REFERENCE_ROOT.relative_to(REPO)}.")
        print()
        print("This is the documented state of this repository, not a broken script. A pixel")
        print("golden can only come from the real TradingView Android app on the same device")
        print("profile as our own capture, and nobody has taken one yet. Nothing may be")
        print("substituted for it — not a web screenshot, not a store image, not a JPG, not a")
        print("resized PNG.")
        print()
        print("To produce one, work through:")
        print("  docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md")
        return REFERENCE_MISSING

    failed = False
    for pack in chosen:
        if not (pack / MANIFEST_NAME).is_file():
            print(f"REFERENCE_INVALID {pack}: no {MANIFEST_NAME}")
            failed = True
            continue
        problems = verify(pack)
        if problems:
            failed = True
            print(f"REFERENCE_INVALID {pack.name}")
            for problem in problems:
                print(f"  - {problem}")
        else:
            print(f"OK   {pack.name}")

    return REFERENCE_INVALID if failed else 0


if __name__ == "__main__":
    sys.exit(main())
