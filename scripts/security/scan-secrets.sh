#!/usr/bin/env bash
set -euo pipefail

readonly SELF_PATH="scripts/security/scan-secrets.sh"

patterns=(
  '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'
  'AKIA[0-9A-Z]{16}'
  'ASIA[0-9A-Z]{16}'
  'gh[pousr]_[A-Za-z0-9]{20,}'
  'github_pat_[A-Za-z0-9_]{20,}'
  'AIza[0-9A-Za-z_-]{35}'
  'xox[baprs]-[A-Za-z0-9-]{20,}'
  'sk-(proj-)?[A-Za-z0-9_-]{20,}'
  'Authorization[^\n]*Bearer[[:space:]]+[A-Za-z0-9._~+/-]{20,}'
)

failed=0
for pattern in "${patterns[@]}"; do
  if git grep -nEI -- "$pattern" -- . ":!$SELF_PATH"; then
    echo "::error::Potential committed secret matched a blocked signature."
    failed=1
  fi
done

for forbidden in '*.jks' '*.keystore' '*.p12' '*.pfx' '*.key' '.env' '.env.*' 'local.properties' 'google-services.json'; do
  while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    echo "::error file=$path::Sensitive configuration/key file must not be tracked."
    failed=1
  done < <(git ls-files "$forbidden")
done

if [[ "$failed" -ne 0 ]]; then
  echo "Tracked secret scan failed. Remove the credential/key material and rotate any exposed credential before retrying."
  exit 1
fi

echo "Tracked secret scan passed."
