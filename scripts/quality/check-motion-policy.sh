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

# ── surface discipline ───────────────────────────────────────────────────────────────────────
#
# Three rules from the design direction, which are the reason this app reads as sharp rather than
# glassy. They are cheap to break by accident and expensive to notice: a single blurred panel or
# one coloured glow does not look wrong on its own, it looks wrong next to the forty surfaces that
# do not have one.
#
# 1. No blur. Elevation is a hairline plus at most one very soft shadow.
# 2. No coloured glow — a shadow tinted with an accent rather than with black.
# 3. Gradients only where allow-listed below.
#
# On Android the blur rule matters more than it does on the web: `Modifier.blur` is a render-effect
# pass per frame, so a blurred panel behind a scrolling list is a measurable cost as well as a
# design one.

blur_hits="$( { git grep -nE '\.blur\(|BlurEffect|RenderEffect\.createBlur' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true; } )"
if [[ -n "$blur_hits" ]]; then
  echo "$blur_hits"
  echo "::error::Blur is not part of this design system. Elevation is a hairline plus one soft shadow."
  exit 1
fi

# A shadow is black at low alpha. `ambientColor`/`spotColor` set to anything else is a coloured
# glow, which is the thing being banned.
glow_hits="$( { git grep -nE 'ambientColor\s*=|spotColor\s*=' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true; } )"
if [[ -n "$glow_hits" ]]; then
  echo "$glow_hits"
  echo "::error::Coloured shadows are not allowed. Shadows are black at low alpha; colour goes in the fill or the border."
  exit 1
fi

# Gradients are allow-listed by file, and the list is short because every entry has to earn its
# place. Two kinds qualify and nothing else does:
#
#   * The brand mark and the agent orb, which *are* the metal — the logo's gold has three stops and
#     reproducing it with a flat fill would be reproducing a different logo.
#   * A moving band that reports work in flight, and a chart's own area fill. In both the gradient
#     is the shape carrying the meaning, not decoration applied to a surface.
#
# What is banned is the third kind: a gradient on a card, a header or a button, which is what makes
# an interface look like a skin rather than a system.
# `ChartSeriesTypes.kt` joins the list for the reason the error message names: a baseline chart's
# two fills and an area chart's ramp are the chart's own fill, which is one of the three places a
# gradient belongs. The ramp itself is 0.28 to 0.05 — TradingView's own, and much shallower than the
# 0.4-to-0 most clones reach for.
gradient_allow='CoineProBrand.kt|CoineProSurfaces.kt|CoineProThinking.kt|CoineProMotionEffects.kt|EquityCurve.kt|CoineProChart.kt|ChartSeriesTypes.kt'
gradient_hits="$( { git grep -lE 'Brush\.(vertical|horizontal|linear|radial|sweep)Gradient' -- 'app/**/*.kt' 'core/**/*.kt' 'feature/**/*.kt' || true; } | grep -vE "$gradient_allow" || true)"
if [[ -n "$gradient_hits" ]]; then
  echo "$gradient_hits"
  echo "::error::Gradients belong to the brand mark, a busy indicator, or a chart's own fill — not to cards, headers or buttons. Add a file to the allow-list in this script only if it is genuinely one of those."
  exit 1
fi

echo "Surface discipline passed: no blur, no coloured shadows, gradients only where allow-listed."
