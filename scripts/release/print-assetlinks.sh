#!/usr/bin/env bash
# Prints the assetlinks.json that must be served for the password-recovery App Link.
#
# The app declares an autoVerify intent filter on
#   https://pro-chart.com/reset
# and Android verifies that claim by fetching
#   https://pro-chart.com/.well-known/assetlinks.json
# on install. Until that file is served with the right fingerprint the link is not verified, the
# recovery email opens a browser instead of the app, and nothing anywhere reports why.
#
# WHICH FINGERPRINT
# -----------------
# The key the installed APK is actually signed with — and for this product that is the key in the
# keystore this script reads, because the product is distributed as a downloadable APK.
#
# That used to be a caveat rather than the rule. When an app ships through Play with App Signing on,
# Google re-signs the upload with a key only they hold, so the fingerprint that matters is the one
# in Play Console → Release → Setup → App signing → "App signing key certificate", and the local
# keystore prints the upload key, which is right only for a sideloaded test build. Google Play does
# not serve Iran; this app is downloaded and installed directly, so there is no re-signing and the
# local keystore is the answer. See docs/release/DISTRIBUTION.md.
#
# If that ever changes — if the correspondence with Google succeeds and the app does reach Play —
# list BOTH fingerprints here. `sha256_cert_fingerprints` is an array on purpose, Android accepts an
# install matching any entry, and during a rollout both kinds of install exist at once.
#
# A phone can also be asked directly: the app's «ایمنی و انتشار» screen prints the certificate of
# the running install — SHA-1 and SHA-256 — which is the one answer no console and no script can
# get wrong.
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
  https://pro-chart.com/.well-known/assetlinks.json

It must be Content-Type: application/json, reachable without a redirect, and without
authentication. Verify afterwards with:
  adb shell pm get-app-links com.coinepro.app
NEXT
