#!/usr/bin/env python3
"""The one place that turns `version.properties` into the two numbers Android wants.

Android carries two versions and they answer different questions. `versionName` is for the reader —
it is what the profile screen shows and what a release is called. `versionCode` is for the package
manager — an integer, and the *only* thing it is asked is "is this higher than what is installed?".
If it is not, the install is refused with "app not installed" and no explanation.

Keeping the two by hand goes wrong in one direction: somebody bumps the name, forgets the code, and
every device in the field silently refuses the update. So only the name is written down, and the
code is computed from it:

    versionCode = MAJOR*10_000_000 + MINOR*100_000 + PATCH*1_000 + BUILD

Read it right to left and each field has room to move without disturbing the one above it:

  * BUILD    0-999    commits since `version.properties` last changed. Every push is a new build.
  * PATCH    0-99     x1_000, so a patch bump outruns any 999 builds under it.
  * MINOR    0-99     x100_000, outrunning 99 patches and their builds.
  * MAJOR    0-200    x10_000_000, outrunning 99 minors — and 200 is where Play's 2,100,000,000
                      ceiling lands, so the top field is bounded by the platform, not by taste.

The property is that a bump *anywhere* is strictly larger than anything reachable below it. That is
the whole reason for the widths, and it is why the numbers are round rather than tight.

Usage:
    version.py                     # print "1.0.0 (10000000)"
    version.py --name              # print the version name, with build metadata when there is any
    version.py --code              # print the version code only
    version.py --json              # print every field, for a script that wants one
    version.py --github-output     # append name/full/tag/code/build to $GITHUB_OUTPUT
    version.py --bump patch        # rewrite version.properties, in place
    version.py --check             # validate, including that build.gradle.kts agrees
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION_FILE = ROOT / "version.properties"

MAJOR_WEIGHT = 10_000_000
MINOR_WEIGHT = 100_000
PATCH_WEIGHT = 1_000

MAX_MAJOR = 200
MAX_MINOR = 99
MAX_PATCH = 99
MAX_BUILD = 999

# Google Play's hard ceiling. Above this the upload is rejected outright.
PLAY_CEILING = 2_100_000_000

# The highest versionCode that ever left this repository under the *old* scheme — CI's
# `run_number + 1000`. Anything the new scheme produces has to clear it, or a device holding one of
# those builds would refuse every future update. 1.0.0 lands at 10,000,000, so the margin is large;
# the check exists so that a careless MAJOR=0 in version.properties fails here rather than on a
# phone.
LEGACY_CODE_FLOOR = 2_000

# Deliberately the same expression as the one in app/build.gradle.kts and
# scripts/release/validate-version.sh. Three copies is two too many, but Gradle Kotlin, bash and
# Python cannot share one, so they are kept identical and this comment is the reason why.
SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$")
PRE_RELEASE_RE = re.compile(r"^[0-9A-Za-z.-]+$")


class VersionError(RuntimeError):
    pass


def read_properties() -> dict[str, str]:
    if not VERSION_FILE.exists():
        raise VersionError(f"{VERSION_FILE} is missing.")
    values: dict[str, str] = {}
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        values[key.strip()] = value.strip()
    return values


def field(values: dict[str, str], key: str, maximum: int) -> int:
    raw = values.get(key)
    if raw is None:
        raise VersionError(f"version.properties is missing {key}.")
    if not re.fullmatch(r"0|[1-9][0-9]*", raw):
        raise VersionError(f"{key} must be a non-negative integer without leading zeroes, not {raw!r}.")
    number = int(raw)
    if number > maximum:
        raise VersionError(f"{key} is {number}; the scheme reserves {maximum} for it. See docs/VERSIONING.md.")
    return number


def build_number() -> int:
    """Commits since `version.properties` last changed.

    Git is asked rather than a counter being stored, because a stored counter has to be committed,
    and committing it would change `version.properties` and reset the very thing it counts.

    A shallow clone cannot answer this — `actions/checkout` fetches one commit by default — so CI
    checks out with `fetch-depth: 0`. Outside a git tree at all (a source tarball, a sandbox) the
    answer is 0, which is right for a build that is not being distributed.
    """
    try:
        anchor = subprocess.run(
            ["git", "log", "-1", "--format=%H", "--", str(VERSION_FILE.relative_to(ROOT))],
            cwd=ROOT, capture_output=True, text=True, check=True,
        ).stdout.strip()
        if not anchor:
            # The file is not committed yet — this is its first build.
            return 0
        count = subprocess.run(
            ["git", "rev-list", "--count", f"{anchor}..HEAD"],
            cwd=ROOT, capture_output=True, text=True, check=True,
        ).stdout.strip()
        return int(count or "0")
    except (subprocess.CalledProcessError, FileNotFoundError, ValueError):
        return 0


def resolve() -> dict[str, object]:
    values = read_properties()
    major = field(values, "MAJOR", MAX_MAJOR)
    minor = field(values, "MINOR", MAX_MINOR)
    patch = field(values, "PATCH", MAX_PATCH)
    pre = values.get("PRE_RELEASE", "").strip()
    if pre and not PRE_RELEASE_RE.fullmatch(pre):
        raise VersionError(f"PRE_RELEASE must be semver-shaped, for example rc.1, not {pre!r}.")

    build = build_number()
    if build > MAX_BUILD:
        raise VersionError(
            f"{build} commits since the last version bump; the scheme reserves {MAX_BUILD}. "
            "Bump the version — `python3 scripts/release/version.py --bump patch`."
        )

    code = major * MAJOR_WEIGHT + minor * MINOR_WEIGHT + patch * PATCH_WEIGHT + build
    if code <= LEGACY_CODE_FLOOR:
        raise VersionError(
            f"versionCode {code} does not clear {LEGACY_CODE_FLOOR}, which builds already in the "
            "field carry. Devices would refuse to update."
        )
    if code > PLAY_CEILING:
        raise VersionError(f"versionCode {code} exceeds Play's ceiling of {PLAY_CEILING}.")

    name = f"{major}.{minor}.{patch}"
    if pre:
        name = f"{name}-{pre}"
    if not SEMVER.fullmatch(name):
        raise VersionError(f"Computed version name {name!r} is not semver-shaped.")

    # Three names, because three readers want different things.
    #
    #   name  1.0.0        the release. What CHANGELOG.md calls it and what a person says out loud.
    #   full  1.0.0+4      semver build metadata: the same release, four commits on. This is what
    #                      goes on the device, so a bug report names the exact build, not the
    #                      nearest version. Semver defines `+` as metadata that does not affect
    #                      precedence, which is exactly right — the build number is already carried
    #                      by versionCode, where precedence actually happens.
    #   tag   v1.0.0-b4    the git tag. Same information, spelled in the characters a refname and a
    #                      URL both take without escaping; `+` would survive git and then need
    #                      encoding everywhere it was linked.
    full = name if build == 0 else f"{name}+{build}"
    tag = f"v{name}" if build == 0 else f"v{name}-b{build}"

    return {
        "name": name, "full": full, "tag": tag, "code": code, "build": build,
        "major": major, "minor": minor, "patch": patch, "preRelease": pre,
    }


def bump(part: str) -> None:
    values = read_properties()
    major = field(values, "MAJOR", MAX_MAJOR)
    minor = field(values, "MINOR", MAX_MINOR)
    patch = field(values, "PATCH", MAX_PATCH)
    if part == "major":
        major, minor, patch = major + 1, 0, 0
    elif part == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    for key, number, maximum in (("MAJOR", major, MAX_MAJOR), ("MINOR", minor, MAX_MINOR), ("PATCH", patch, MAX_PATCH)):
        if number > maximum:
            raise VersionError(f"Bumping {part} would put {key} at {number}; the scheme reserves {maximum}.")

    text = VERSION_FILE.read_text(encoding="utf-8")
    for key, number in (("MAJOR", major), ("MINOR", minor), ("PATCH", patch)):
        text = re.sub(rf"(?m)^{key}=.*$", f"{key}={number}", text)
    VERSION_FILE.write_text(text, encoding="utf-8")
    print(f"version.properties -> {major}.{minor}.{patch}")


GRADLE_FILE = ROOT / "app" / "build.gradle.kts"


def check_gradle_agrees() -> None:
    """The build computes the code too. Prove it computes the same one.

    Gradle Kotlin and Python cannot share an expression, so the arithmetic exists twice. Two copies
    that are supposed to agree and are never compared will eventually disagree — and the symptom
    would be a CI build and a local build claiming the same version with different codes, which
    nobody notices until an install is refused. So the literals are read back out of the build file
    and matched here.
    """
    if not GRADLE_FILE.exists():
        raise VersionError(f"{GRADLE_FILE} is missing.")
    source = GRADLE_FILE.read_text(encoding="utf-8")

    formula = re.search(
        r"val declaredVersionCode\s*=\s*versionMajor \* ([\d_]+) \+ versionMinor \* ([\d_]+) \+ versionPatch \* ([\d_]+)",
        source,
    )
    if not formula:
        raise VersionError("app/build.gradle.kts no longer computes declaredVersionCode in the expected shape.")
    weights = tuple(int(group.replace("_", "")) for group in formula.groups())
    if weights != (MAJOR_WEIGHT, MINOR_WEIGHT, PATCH_WEIGHT):
        raise VersionError(
            f"app/build.gradle.kts weights {weights} disagree with "
            f"{(MAJOR_WEIGHT, MINOR_WEIGHT, PATCH_WEIGHT)} here. One of the two is wrong."
        )

    for key, maximum in (("MAJOR", MAX_MAJOR), ("MINOR", MAX_MINOR), ("PATCH", MAX_PATCH)):
        found = re.search(rf'versionField\("{key}", (\d+)\)', source)
        if not found or int(found.group(1)) != maximum:
            raise VersionError(
                f"app/build.gradle.kts caps {key} at {found.group(1) if found else 'nothing'}; "
                f"this script caps it at {maximum}."
            )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", action="store_true", help="print the version name only")
    parser.add_argument("--code", action="store_true", help="print the version code only")
    parser.add_argument("--json", action="store_true", help="print every resolved field as JSON")
    parser.add_argument("--github-output", action="store_true", help="append name/code to $GITHUB_OUTPUT")
    parser.add_argument("--check", action="store_true", help="validate and say nothing on success")
    parser.add_argument("--bump", choices=("major", "minor", "patch"), help="rewrite version.properties")
    args = parser.parse_args()

    try:
        if args.bump:
            bump(args.bump)
            return 0
        resolved = resolve()
    except VersionError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1

    if args.check:
        try:
            check_gradle_agrees()
        except VersionError as error:
            print(f"::error::{error}", file=sys.stderr)
            return 1
        return 0
    if args.name:
        print(resolved["full"])
    elif args.code:
        print(resolved["code"])
    elif args.json:
        print(json.dumps(resolved, indent=2))
    else:
        print(f"{resolved['full']} ({resolved['code']})")

    if args.github_output:
        target = os.environ.get("GITHUB_OUTPUT")
        if not target:
            print("::error::--github-output needs $GITHUB_OUTPUT.", file=sys.stderr)
            return 1
        with open(target, "a", encoding="utf-8") as handle:
            for key in ("name", "full", "tag", "code", "build"):
                handle.write(f"{key}={resolved[key]}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
