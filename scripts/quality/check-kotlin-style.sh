#!/usr/bin/env bash
#
# Kotlin style, checked without a linter.
#
# ktlint and detekt are the obvious answer and are deliberately not used: both are Gradle plugins
# resolved from the plugin portal, and a build that cannot reach it fails on formatting rather than
# on anything real. This checks the rules that have actually mattered in this repository, needs
# nothing but git and grep, and runs in under a second.
#
# What it does NOT check is indentation, wrapping and expression style. Those are what a real
# formatter is for; adding half of one here would be a gate arguing with a tool nobody ran.

set -euo pipefail
cd "$(dirname "$0")/../.."

fail=0
report() {
  echo "$2"
  echo "::error::$1"
  fail=1
}

# Wildcard imports. They make a file's dependencies unreadable in a diff, and two of them can
# collide on a name without either import mentioning it.
hits="$(git grep -n '^import .*\*$' -- '*.kt' || true)"
[[ -n "$hits" ]] && report "Wildcard imports hide what a file depends on. Import the names." "$hits"

# Printing to stdout. Android throws it away and it never reaches a bug report; anything worth
# recording goes through core:diagnostics, which the admin screen can show.
hits="$(git grep -n 'println(\|System\.out\.print' -- \
  'core/*/src/main/**/*.kt' 'feature/*/src/main/**/*.kt' 'app/src/main/**/*.kt' || true)"
[[ -n "$hits" ]] && report "println in shipping code goes nowhere. Use core:diagnostics." "$hits"

# Blocking sleeps. Every wait in this app is a coroutine delay; a Thread.sleep on the main thread
# is a frozen frame, and on any other it is a thread held for nothing.
hits="$(git grep -n 'Thread\.sleep(' -- \
  'core/*/src/main/**/*.kt' 'feature/*/src/main/**/*.kt' 'app/src/main/**/*.kt' || true)"
[[ -n "$hits" ]] && report "Thread.sleep blocks a thread. Use delay in a coroutine." "$hits"

# Unfinished work left in the tree. A TODO in shipped code is a promise nobody is tracking; the
# roadmap and the changelog are where work is recorded.
hits="$(git grep -nE 'TODO\(|FIXME' -- '*.kt' || true)"
[[ -n "$hits" ]] && report "TODO/FIXME belong in the roadmap, not in the source." "$hits"

# Tabs and trailing whitespace. Both are invisible and both produce diffs that say nothing.
hits="$(git grep -Pn '\t' -- '*.kt' || true)"
[[ -n "$hits" ]] && report "Tab characters in Kotlin sources." "$hits"

hits="$(git grep -n ' $' -- '*.kt' || true)"
[[ -n "$hits" ]] && report "Trailing whitespace." "$hits"

# Line length is deliberately NOT checked.
#
# A length rule without a formatter is a rule that generates busywork: the lines it flags in this
# repository are catalogue rows (one indicator per line, aligned), Persian prose inside string
# literals, and box-drawing comment separators — and wrapping any of those makes them harder to
# read, not easier. The rule is worth having the day a formatter is wired up to obey it, and not
# before. What is checked below is the invisible damage a formatter would also fix.

# A file with no closing newline makes the next change to it a two-line diff.
missing=""
while IFS= read -r file; do
  [[ -s "$file" ]] || continue
  [[ -z "$(tail -c 1 "$file")" ]] || missing+="$file"$'\n'
done < <(git ls-files '*.kt')
[[ -n "$missing" ]] && report "Files not ending in a newline." "$missing"

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "Kotlin style passed: no wildcard imports, no stdout logging, no blocking sleeps, no stray TODOs."
