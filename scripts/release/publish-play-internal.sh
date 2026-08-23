#!/usr/bin/env bash
set -euo pipefail

package_name="${1:-}"
aab_path="${2:-}"
expected_version_code="${3:-}"
version_name="${4:-}"
access_token="${PLAY_ACCESS_TOKEN:-}"

if [[ -z "$package_name" || -z "$aab_path" || -z "$expected_version_code" || -z "$version_name" ]]; then
  echo "Usage: $0 <package-name> <aab-path> <version-code> <version-name>" >&2
  exit 2
fi

if [[ -z "$access_token" ]]; then
  echo "::error::PLAY_ACCESS_TOKEN is required."
  exit 1
fi

if [[ ! -f "$aab_path" ]]; then
  echo "::error::AAB not found: $aab_path"
  exit 1
fi

api_base="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${package_name}"
upload_base="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${package_name}"
auth_header="Authorization: Bearer ${access_token}"
edit_id=""
committed="false"

cleanup_edit() {
  if [[ -n "$edit_id" && "$committed" != "true" ]]; then
    curl --silent --show-error --fail-with-body \
      -X DELETE \
      -H "$auth_header" \
      "${api_base}/edits/${edit_id}" >/dev/null || true
  fi
}
trap cleanup_edit EXIT

edit_json="$(curl --silent --show-error --fail-with-body \
  -X POST \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d '{}' \
  "${api_base}/edits")"
edit_id="$(jq -r '.id // empty' <<<"$edit_json")"
if [[ -z "$edit_id" ]]; then
  echo "::error::Android Publisher did not return an edit id."
  exit 1
fi

bundle_json="$(curl --silent --show-error --fail-with-body \
  -X POST \
  -H "$auth_header" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@${aab_path}" \
  "${upload_base}/edits/${edit_id}/bundles?uploadType=media")"
uploaded_version_code="$(jq -r '.versionCode // empty' <<<"$bundle_json")"
if [[ "$uploaded_version_code" != "$expected_version_code" ]]; then
  echo "::error::Uploaded bundle versionCode ${uploaded_version_code:-missing} does not match expected ${expected_version_code}."
  exit 1
fi

track_payload="$(jq -n \
  --arg version_code "$uploaded_version_code" \
  --arg release_name "CoinePro ${version_name}" \
  '{track:"internal",releases:[{name:$release_name,status:"completed",versionCodes:[$version_code]}]}')"

curl --silent --show-error --fail-with-body \
  -X PUT \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d "$track_payload" \
  "${api_base}/edits/${edit_id}/tracks/internal" >/dev/null

curl --silent --show-error --fail-with-body \
  -X POST \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d '{}' \
  "${api_base}/edits/${edit_id}:commit" >/dev/null

committed="true"
echo "Published ${package_name} ${version_name} (${uploaded_version_code}) to Play internal track."
