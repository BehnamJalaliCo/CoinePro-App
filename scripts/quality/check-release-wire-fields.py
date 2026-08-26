#!/usr/bin/env python3
"""Assert that a built release APK still carries its wire models' field names.

Run after `:app:assembleRelease`:

    python3 scripts/quality/check-release-wire-fields.py

Why this exists. Gson serialises and parses by reflecting over fields, and R8 cannot see that
reflection: to it, a request body whose fields nothing reads is a class whose fields can be renamed
or deleted. The app's rules used to protect only classes named `*Dto`, and nine request bodies are
not named that way — LoginRequest, GoogleRequest, RefreshRequest, RegisterStartRequest,
RegisterVerifyRequest, ForgotPasswordRequest, ResetPasswordRequest, ProgressBody, QuizSubmitBody.
In the release build their fields came out as `a` and `b`, so a sign-in posted `{"a":…,"b":…}` and
every credential request failed — in release only, with nothing anywhere saying why.

A rule in a file is not evidence that the rule worked. This reads the actual dex.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "app/build/outputs/apk/release/app-release.apk"

# One field name per wire model that must survive, chosen to be distinctive enough that a match is
# that model rather than a coincidence elsewhere in the app.
REQUIRED_FIELDS = {
    "email": "LoginRequest / RegisterStartRequest — sign-in and registration",
    "password": "LoginRequest",
    "idToken": "GoogleRequest — Google sign-in",
    "refreshToken": "RefreshRequest — silent re-authentication",
    "registrationToken": "RegisterVerifyRequest — e-mail verification",
    "resetToken": "ResetPasswordRequest — password recovery",
    "newPassword": "ResetPasswordRequest",
    "quizScore": "ProgressBody — academy lesson completion",
    "answers": "QuizSubmitBody — academy quiz submission",
    "nationalId": "KycLevel1Request — identity verification",
}


def dexdump() -> str:
    candidates: list[Path] = []
    for base in (
        Path.home() / "Android/Sdk",
        Path("/opt/android-sdk"),
        ROOT / ".android-sdk",
    ):
        candidates += sorted(base.glob("build-tools/*/dexdump"))
    env_sdk = subprocess.run(
        ["bash", "-lc", "echo $ANDROID_HOME$ANDROID_SDK_ROOT"],
        capture_output=True, text=True,
    ).stdout.strip()
    if env_sdk:
        candidates += sorted(Path(env_sdk).glob("build-tools/*/dexdump"))
    if not candidates:
        print("SKIP: no dexdump found; set ANDROID_HOME to run this check.", file=sys.stderr)
        raise SystemExit(0)
    return str(candidates[-1])


def main() -> None:
    if not APK.exists():
        print(f"SKIP: {APK} not built yet. Run :app:assembleRelease first.", file=sys.stderr)
        raise SystemExit(0)

    import tempfile
    import zipfile

    tool = dexdump()
    names: set[str] = set()
    with tempfile.TemporaryDirectory() as work:
        with zipfile.ZipFile(APK) as archive:
            dexes = [n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
            for name in dexes:
                archive.extract(name, work)
        for name in dexes:
            # Bytes, not text: dexdump echoes string-pool entries verbatim and a dex holds
            # arbitrary bytes, so decoding the whole dump as UTF-8 throws part-way through.
            dumped = subprocess.run(
                [tool, "-d", str(Path(work) / name)],
                capture_output=True,
            ).stdout.decode("utf-8", errors="replace")
            names.update(re.findall(r"^      name          : '([^']+)'$", dumped, re.MULTILINE))

    missing = {field: why for field, why in REQUIRED_FIELDS.items() if field not in names}
    if missing:
        print("RELEASE_WIRE_FIELDS_ERROR: R8 renamed or removed these field names:")
        for field, why in sorted(missing.items()):
            print(f"  {field:<20} {why}")
        print()
        print("Every request using them serialises as {} or with single-letter keys, and the")
        print("failure appears only in a minified build. Check the -keepclassmembers rule in")
        print("app/proguard-rules.pro.")
        raise SystemExit(1)

    print(f"Release wire fields intact: all {len(REQUIRED_FIELDS)} checked names survive R8.")


if __name__ == "__main__":
    main()
