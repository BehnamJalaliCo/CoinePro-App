#!/usr/bin/env bash
# The 30-minute Monkey soak on the attached device (run E, item 5): the app driven by random
# touches, motions and navigation, with the log written where the report expects it.
#
#   bash scripts/qa/monkey-soak.sh            # 30 minutes, the default
#   bash scripts/qa/monkey-soak.sh 10         # a shorter dry run
#
# Writes docs/qa/soak/<model>-<date>.log and prints the crash and ANR counts; the target is zero.
# Nothing here retries or filters: a crash that happened is in the log.
set -euo pipefail

MINUTES="${1:-30}"
PACKAGE="${PACKAGE:-com.coinepro.app}"
THROTTLE_MS=200
# Events for the requested duration at one every THROTTLE_MS, plus what the device spends itself.
EVENTS=$(( MINUTES * 60 * 1000 / THROTTLE_MS ))
MODEL=$(adb shell getprop ro.product.model | tr -d '\r' | tr 'A-Z ' 'a-z-')
STAMP=$(date -u +%Y%m%dT%H%MZ)
OUT_DIR="$(cd "$(dirname "$0")/../.." && pwd)/docs/qa/soak"
mkdir -p "$OUT_DIR"
LOG="$OUT_DIR/${MODEL}-${STAMP}.log"

{
  echo "# monkey soak — package $PACKAGE — $MINUTES min — $EVENTS events — $(adb shell getprop ro.product.model | tr -d '\r') / Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "# started $STAMP"
} > "$LOG"

adb logcat -c
adb shell monkey -p "$PACKAGE" \
  --throttle "$THROTTLE_MS" \
  --pct-touch 60 --pct-motion 30 --pct-nav 5 --pct-syskeys 0 --pct-appswitch 0 \
  --ignore-security-exceptions \
  -s 4670 -v -v "$EVENTS" >> "$LOG" 2>&1 || true

echo "# finished $(date -u +%Y%m%dT%H%MZ)" >> "$LOG"
adb logcat -d -b crash >> "$LOG" 2>/dev/null || true

CRASHES=$(grep -c -E "^// CRASH|FATAL EXCEPTION" "$LOG" || true)
ANRS=$(grep -c -E "^// NOT RESPONDING|ANR in $PACKAGE" "$LOG" || true)
echo "log: $LOG"
echo "crashes: $CRASHES"
echo "anrs: $ANRS"
[ "$CRASHES" -eq 0 ] && [ "$ANRS" -eq 0 ]
