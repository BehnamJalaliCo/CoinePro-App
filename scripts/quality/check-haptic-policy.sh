#!/usr/bin/env bash
# Doctrine D6 — physical feedback — as something that runs and fails.
#
# `scripts/quality/check-motion-policy.sh` holds the other half of D6: no tween on a spatial
# transition, continuous motion guarded. This holds the haptics, and there are three claims worth
# holding rather than one:
#
#   1. **One vocabulary.** `CoineProHaptics` has five words — select, commit, reject, longPress,
#      contextClick — and nothing else in the app may call `performHapticFeedback` or reach for
#      `LocalHapticFeedback` directly. A sixth weight invented at a call site is a buzz a reader
#      cannot learn, and it is invisible in review because it looks like every other line.
#
#   2. **The primitives keep theirs.** Every confirmation is a haptic (D6's own words), and the way
#      that is true everywhere at once is that the *components* carry it: a primary button, a
#      secondary button, a confirm dialog, a chip row, a switch row. A screen that uses them gets it
#      for nothing. If one of those loses its call, hundreds of call sites go quiet in one edit and
#      nothing else fails.
#
#   3. **The count does not collapse.** A floor, not an exact number: features are added and the
#      number goes up. What this catches is the other direction — a refactor that took the haptics
#      out of a surface and left the rest compiling.
#
# Run:  bash scripts/quality/check-haptic-policy.sh

set -euo pipefail

cd "$(dirname "$0")/../.."

FAILED=0
SOURCES=(app/src core feature chart)
HAPTICS_FILE="core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProHaptics.kt"

note() { printf '%s\n' "$1"; }
fail() { note "DOCTRINE_D6_ERROR: $1"; FAILED=1; }

# ── 1. one vocabulary ───────────────────────────────────────────────────────────────────────

bypasses=$(grep -rn --include=*.kt -E 'performHapticFeedback|LocalHapticFeedback' "${SOURCES[@]}" \
  | grep -v "/build/" \
  | grep -v "$HAPTICS_FILE" \
  || true)
if [[ -n "$bypasses" ]]; then
  fail "the platform's haptics are called directly, outside CoineProHaptics:"
  printf '%s\n' "$bypasses" | sed 's/^/    /'
  note "  D6 has five words. A sixth invented at a call site is a buzz nobody can learn."
fi

# ── 2. the primitives keep theirs ───────────────────────────────────────────────────────────
#
# Named one by one rather than «every file in designsystem», because the list *is* the claim: these
# are the surfaces a reader touches to confirm something, and each one is here because a screen
# somewhere relies on it rather than calling haptics itself.

declare -a PRIMITIVES=(
  "core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProSurfaces.kt"
  "core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProConfirmDialog.kt"
  "core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProBrandButton.kt"
  "core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProMarketRow.kt"
  "core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProPullToRefresh.kt"
)

for file in "${PRIMITIVES[@]}"; do
  if [[ ! -f "$file" ]]; then
    fail "a primitive that must carry haptics has moved or gone: $file"
    continue
  fi
  if ! grep -q "rememberCoineProHaptics()" "$file"; then
    fail "$(basename "$file") no longer takes haptics — every screen that uses it just went quiet"
  fi
done

# ── 3. the count does not collapse ──────────────────────────────────────────────────────────

FLOOR=40
count=$(grep -rn --include=*.kt -E 'haptics\.(select|commit|reject|longPress|contextClick)\(' "${SOURCES[@]}" \
  | grep -v "/build/" | wc -l | tr -d ' ')
if (( count < FLOOR )); then
  fail "only $count haptic call sites, below the floor of $FLOOR — something removed them in bulk"
fi

if (( FAILED )); then
  note ""
  note "D6: every confirmation is a haptic. See docs/DOCTRINE.md."
  exit 1
fi

note "Doctrine D6 passed: one vocabulary, ${#PRIMITIVES[@]} primitives carrying it, $count call sites."
