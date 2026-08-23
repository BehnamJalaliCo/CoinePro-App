#!/usr/bin/env bash
set -euo pipefail

version_name="${1:-}"
version_code="${2:-}"

if [[ ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]]; then
  echo "::error::Version name must use semantic version form, for example 1.2.3 or 1.2.3-rc.1."
  exit 1
fi

if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "::error::Version code must be a positive integer without leading zeroes."
  exit 1
fi

if (( version_code > 2100000000 )); then
  echo "::error::Version code exceeds the Android/Play upper bound of 2100000000."
  exit 1
fi

echo "Release version validated: ${version_name} (${version_code})"
