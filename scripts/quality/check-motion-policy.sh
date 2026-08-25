#!/usr/bin/env bash
set -euo pipefail

# Continuous motion is allowed only where it reports that work is genuinely still running — a
# progress or "thinking" indicator — and only when the same file consults
# `continuousMotionAllowed()` and holds a static frame if the device has animations turned off.
#
# The rule used to be a blanket ban. That was enforceable but wrong: it also banned the one honest
# use of a loop, so an AI screen could not show that a request was in flight. What matters for
# accessibility is not that loops never exist, it is that a person who disabled animations never
# sees one.

violations=""
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if ! grep -q "continuousMotionAllowed" "$file"; then
    violations+="$file"$'\n'
  fi
done < <(git grep -lE 'rememberInfiniteTransition|infiniteRepeatable' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true)

if [[ -n "$violations" ]]; then
  echo "$violations"
  echo "::error::Continuous motion must be gated on continuousMotionAllowed() so it stops when the device has animations turned off. Add the guard, or use finite state-driven motion instead."
  exit 1
fi

# git grep exits 1 on no matches, which pipefail would turn into a failed gate.
guarded="$( { git grep -lE 'rememberInfiniteTransition|infiniteRepeatable' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true; } | wc -l | tr -d ' ')"
echo "Reduced-motion policy passed: ${guarded} file(s) use continuous motion, all guarded."
