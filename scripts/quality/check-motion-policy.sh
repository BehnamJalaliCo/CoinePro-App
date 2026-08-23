#!/usr/bin/env bash
set -euo pipefail

violations="$(git grep -nE 'rememberInfiniteTransition|infiniteRepeatable' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true)"

if [[ -n "$violations" ]]; then
  echo "$violations"
  echo "::error::Continuous/infinite UI motion is blocked by the Phase 15 reduced-motion policy. Use finite state-driven motion that remains usable when system animations are disabled."
  exit 1
fi

echo "Reduced-motion policy passed: no continuous/infinite UI animation primitives found."
