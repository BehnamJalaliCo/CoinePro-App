#!/usr/bin/env bash
set -euo pipefail

release_url="https://release-only.invalid/"
debug_url="https://debug-only.invalid/"
release_project="release-project-marker"
debug_project="debug-project-marker"

rm -rf app/build/generated/source/buildConfig

gradle --no-daemon --no-configuration-cache \
  -PCOINEPRO_API_BASE_URL="$release_url" \
  -PCOINEPRO_FIREBASE_PROJECT_ID="$release_project" \
  -PCOINEPRO_FIREBASE_APPLICATION_ID="release-app-marker" \
  -PCOINEPRO_FIREBASE_API_KEY="release-api-marker" \
  -PCOINEPRO_FIREBASE_SENDER_ID="release-sender-marker" \
  -PCOINEPRO_DEBUG_API_BASE_URL="$debug_url" \
  -PCOINEPRO_DEBUG_FIREBASE_PROJECT_ID="$debug_project" \
  -PCOINEPRO_DEBUG_FIREBASE_APPLICATION_ID="debug-app-marker" \
  -PCOINEPRO_DEBUG_FIREBASE_API_KEY="debug-api-marker" \
  -PCOINEPRO_DEBUG_FIREBASE_SENDER_ID="debug-sender-marker" \
  :app:generateDebugBuildConfig :app:generateReleaseBuildConfig

debug_file="$(find app/build/generated/source/buildConfig/debug -name BuildConfig.java -print -quit)"
release_file="$(find app/build/generated/source/buildConfig/release -name BuildConfig.java -print -quit)"

if [[ -z "$debug_file" || -z "$release_file" ]]; then
  echo "::error::Could not locate generated debug/release BuildConfig files."
  exit 1
fi

assert_contains() {
  local file="$1"
  local marker="$2"
  if ! grep -Fq -- "$marker" "$file"; then
    echo "::error file=$file::Expected build-specific marker is missing."
    exit 1
  fi
}

assert_absent() {
  local file="$1"
  local marker="$2"
  if grep -Fq -- "$marker" "$file"; then
    echo "::error file=$file::Build configuration leaked across debug/release boundary."
    exit 1
  fi
}

assert_contains "$debug_file" "$debug_url"
assert_contains "$debug_file" "$debug_project"
assert_absent "$debug_file" "$release_url"
assert_absent "$debug_file" "$release_project"

assert_contains "$release_file" "$release_url"
assert_contains "$release_file" "$release_project"
assert_absent "$release_file" "$debug_url"
assert_absent "$release_file" "$debug_project"

echo "Debug/release BuildConfig isolation passed."
