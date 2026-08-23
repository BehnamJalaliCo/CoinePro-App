#!/usr/bin/env bash
set -euo pipefail

debug_url="https://debug-only.invalid/"
staging_url="https://staging-only.invalid/"
production_url="https://production-only.invalid/"
debug_project="debug-project-marker"
staging_project="staging-project-marker"
production_project="production-project-marker"
benchmark_url="https://benchmark.example.invalid/"

rm -rf app/build/generated/source/buildConfig

gradle --no-daemon --no-configuration-cache \
  -PCOINEPRO_DEBUG_API_BASE_URL="$debug_url" \
  -PCOINEPRO_DEBUG_FIREBASE_PROJECT_ID="$debug_project" \
  -PCOINEPRO_DEBUG_FIREBASE_APPLICATION_ID="debug-app-marker" \
  -PCOINEPRO_DEBUG_FIREBASE_API_KEY="debug-api-marker" \
  -PCOINEPRO_DEBUG_FIREBASE_SENDER_ID="debug-sender-marker" \
  -PCOINEPRO_STAGING_API_BASE_URL="$staging_url" \
  -PCOINEPRO_STAGING_FIREBASE_PROJECT_ID="$staging_project" \
  -PCOINEPRO_STAGING_FIREBASE_APPLICATION_ID="staging-app-marker" \
  -PCOINEPRO_STAGING_FIREBASE_API_KEY="staging-api-marker" \
  -PCOINEPRO_STAGING_FIREBASE_SENDER_ID="staging-sender-marker" \
  -PCOINEPRO_PRODUCTION_API_BASE_URL="$production_url" \
  -PCOINEPRO_PRODUCTION_FIREBASE_PROJECT_ID="$production_project" \
  -PCOINEPRO_PRODUCTION_FIREBASE_APPLICATION_ID="production-app-marker" \
  -PCOINEPRO_PRODUCTION_FIREBASE_API_KEY="production-api-marker" \
  -PCOINEPRO_PRODUCTION_FIREBASE_SENDER_ID="production-sender-marker" \
  :app:generateDebugBuildConfig :app:generateStagingBuildConfig :app:generateReleaseBuildConfig :app:generateBenchmarkBuildConfig

debug_file="$(find app/build/generated/source/buildConfig/debug -name BuildConfig.java -print -quit)"
staging_file="$(find app/build/generated/source/buildConfig/staging -name BuildConfig.java -print -quit)"
release_file="$(find app/build/generated/source/buildConfig/release -name BuildConfig.java -print -quit)"
benchmark_file="$(find app/build/generated/source/buildConfig/benchmark -name BuildConfig.java -print -quit)"

for file in "$debug_file" "$staging_file" "$release_file" "$benchmark_file"; do
  if [[ -z "$file" ]]; then
    echo "::error::Could not locate every generated BuildConfig file."
    exit 1
  fi
done

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
    echo "::error file=$file::Build configuration leaked across environment boundary."
    exit 1
  fi
}

assert_contains "$debug_file" "$debug_url"
assert_contains "$debug_file" "$debug_project"
assert_absent "$debug_file" "$staging_url"
assert_absent "$debug_file" "$production_url"

assert_contains "$staging_file" "$staging_url"
assert_contains "$staging_file" "$staging_project"
assert_absent "$staging_file" "$debug_url"
assert_absent "$staging_file" "$production_url"
assert_absent "$staging_file" "$production_project"

assert_contains "$release_file" "$production_url"
assert_contains "$release_file" "$production_project"
assert_absent "$release_file" "$debug_url"
assert_absent "$release_file" "$staging_url"
assert_absent "$release_file" "$staging_project"

assert_contains "$benchmark_file" "$benchmark_url"
assert_absent "$benchmark_file" "$debug_url"
assert_absent "$benchmark_file" "$staging_url"
assert_absent "$benchmark_file" "$production_url"
assert_absent "$benchmark_file" "$debug_project"
assert_absent "$benchmark_file" "$staging_project"
assert_absent "$benchmark_file" "$production_project"

echo "Debug/staging/production/benchmark BuildConfig isolation passed."
