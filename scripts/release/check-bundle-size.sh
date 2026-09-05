#!/usr/bin/env bash
# Fail when the base module's download size, as Play would serve it, exceeds the budget.
#
# Measures the *download* size of the smallest device configuration from the AAB, which is what a
# reader pays for on a metered connection — not the universal APK on disk, which carries every ABI
# and every density at once and is roughly twice as large. `bundletool` is fetched in CI (the
# runner has the network; this repo does not vendor a 6 MB jar) and needs the signing key to
# build the split APKs it measures, so the step runs after the keystore is restored.
#
# Usage: check-bundle-size.sh <bundle.aab> <max-mebibytes> [bundletool.jar]
#   env: KEYSTORE_FILE KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD — as the assemble step sets them.

set -euo pipefail

bundle="${1:?bundle.aab}"
max_mib="${2:?max size in MiB}"
jar="${3:-bundletool.jar}"

if [ ! -f "$jar" ]; then
  echo "::error::$jar not found; download bundletool-all from github.com/google/bundletool/releases"
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

java -jar "$jar" build-apks \
  --bundle="$bundle" \
  --output="$work/app.apks" \
  --mode=default \
  --ks="${KEYSTORE_FILE:?}" --ks-pass="pass:${KEYSTORE_PASSWORD:?}" \
  --ks-key-alias="${KEY_ALIAS:?}" --key-pass="pass:${KEY_PASSWORD:?}" >/dev/null

# `get-size total` prints MIN,MAX in bytes across every device configuration in the set; the
# maximum is the largest download any single phone would make, which is the number to bound.
# bundletool writes CRLF line ends; the carriage return has to go before any arithmetic sees it.
sizes="$(java -jar "$jar" get-size total --apks="$work/app.apks" | tr -d '\r' | tail -n 1)"
min_bytes="${sizes%%,*}"
max_bytes="${sizes##*,}"
limit=$(( max_mib * 1024 * 1024 ))

printf 'Base module download size: %d–%d bytes (%d.%02d–%d.%02d MiB); budget %s MiB\n' \
  "$min_bytes" "$max_bytes" \
  $(( min_bytes / 1048576 )) $(( (min_bytes % 1048576) * 100 / 1048576 )) \
  $(( max_bytes / 1048576 )) $(( (max_bytes % 1048576) * 100 / 1048576 )) \
  "$max_mib"

if [ "$max_bytes" -gt "$limit" ]; then
  echo "::error::base module download size $max_bytes bytes exceeds the ${max_mib} MiB budget"
  exit 1
fi
echo "Download size within budget."
