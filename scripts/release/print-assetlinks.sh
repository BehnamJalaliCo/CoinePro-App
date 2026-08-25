#!/usr/bin/env bash
# Prints the assetlinks.json that must be served for the password-recovery App Link.
#
# The app declares an autoVerify intent filter on
#   https://user.tradeyar.trade-future.ir/reset
# and Android verifies that claim by fetching
#   https://user.tradeyar.trade-future.ir/.well-known/assetlinks.json
# on install. Until that file is served with the right fingerprint the link is not verified, the
# recovery email opens a browser instead of the app, and nothing anywhere reports why.
#
# WHICH FINGERPRINT
# -----------------
# The one Google signs releases with, which is NOT the upload key when Play App Signing is on.
# Take it from Play Console → Release → Setup → App signing → "App signing key certificate".
# Running this script against the local keystore prints the UPLOAD key's fingerprint, which is
# right only for a build installed directly from an APK — a sideloaded test, not a Play install.
# Both may be listed at once, and listing both is usually correct during a rollout.
set -euo pipefail

KEYSTORE="${1:-${COINEPRO_RELEASE_STORE_FILE:-}}"
ALIAS="${2:-${COINEPRO_RELEASE_KEY_ALIAS:-}}"

if [[ -z "$KEYSTORE" || -z "$ALIAS" ]]; then
  cat >&2 <<'USAGE'
usage: print-assetlinks.sh <keystore> <alias>
   or: COINEPRO_RELEASE_STORE_FILE=… COINEPRO_RELEASE_KEY_ALIAS=… print-assetlinks.sh

Neither the keystore nor its password is in this repository, by design.
USAGE
  exit 2
fi

FINGERPRINT="$(
  keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" \
    ${COINEPRO_RELEASE_STORE_PASSWORD:+-storepass "$COINEPRO_RELEASE_STORE_PASSWORD"} \
    | awk -F': ' '/SHA256:/ { print $2; exit }'
)"

if [[ -z "$FINGERPRINT" ]]; then
  echo "Could not read a SHA-256 fingerprint from $KEYSTORE ($ALIAS)." >&2
  exit 1
fi

cat <<JSON
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.coinepro.app",
    "sha256_cert_fingerprints": ["$FINGERPRINT"]
  }
}]
JSON

cat >&2 <<'NEXT'

Serve the JSON above at:
  https://user.tradeyar.trade-future.ir/.well-known/assetlinks.json

It must be Content-Type: application/json, reachable without a redirect, and without
authentication. Verify afterwards with:
  adb shell pm get-app-links com.coinepro.app
NEXT
