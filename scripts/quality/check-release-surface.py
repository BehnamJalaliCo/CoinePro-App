#!/usr/bin/env python3
"""Assert that a built release APK carries only what a store build should.

Run after `:app:assembleRelease`:

    python3 scripts/quality/check-release-surface.py [path/to/app-release.apk]

Three things are read off the actual artefact rather than trusted from the build script:

* **No admin panel.** `BuildConfig.ADMIN_PANEL` is false in release, R8 drops the destination and
  the resource shrinker drops the `admin_*` strings behind it. A hundred and seventy-five strings
  naming base URLs, log levels and crash traces have no business in the APK a reader installs.
* **Phone ABIs only.** `lib/x86` and `lib/x86_64` are for emulators, which the debug and benchmark
  variants serve; a store build carrying them is two ABIs no phone has.
* **No editor droppings.** `content.json.orig` shipped for nine releases as 847 KB of a file nobody
  meant to package. Anything ending in `.orig`, `.bak`, `.rej` or `.tmp` fails.

`aapt2` is looked up under `ANDROID_HOME`/`ANDROID_SDK_ROOT`; without it the string check is
skipped with a warning rather than pretending to have passed.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_APK = ROOT / "app/build/outputs/apk/release/app-release.apk"

STRAY_SUFFIXES = (".orig", ".bak", ".rej", ".tmp")
EMULATOR_ABIS = ("lib/x86/", "lib/x86_64/")


def aapt2() -> Path | None:
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(var)
        if not sdk:
            continue
        tools = sorted((Path(sdk) / "build-tools").glob("*/aapt2"))
        if tools:
            return tools[-1]
    return None


def main() -> int:
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_APK
    if not apk.exists():
        print(f"::error::{apk} does not exist; build the release first")
        return 2
    failures: list[str] = []

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
    stray = [n for n in names if n.lower().endswith(STRAY_SUFFIXES)]
    if stray:
        failures.append(f"stray editor files packaged: {stray}")
    emulator = sorted({n.split('/')[1] for n in names if n.startswith(EMULATOR_ABIS)})
    if emulator:
        failures.append(f"emulator ABIs in a store build: {emulator}")

    tool = aapt2()
    if tool is None:
        print("::warning::aapt2 not found under ANDROID_HOME; the admin-string check did not run")
    else:
        dump = subprocess.run(
            [str(tool), "dump", "resources", str(apk)],
            capture_output=True, text=True, check=True,
        ).stdout
        admin = sorted(set(re.findall(r"string/(admin_\w+)", dump)))
        if admin:
            failures.append(f"{len(admin)} admin_* strings survived into the release: {admin[:8]}…")

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        return 1
    print(f"Release surface is clean: no admin strings, no emulator ABIs, no stray files in {apk.name}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
