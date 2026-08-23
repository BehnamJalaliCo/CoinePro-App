#!/usr/bin/env python3
import json
import pathlib
import sys
import urllib.error
import urllib.request

OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
DEPENDENCIES = pathlib.Path("build/security/resolved-dependencies.tsv")
ALLOWLIST = pathlib.Path("scripts/security/osv-allowlist.txt")
BATCH_SIZE = 500


def load_dependencies():
    dependencies = []
    for line in DEPENDENCIES.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 3 or not all(parts):
            raise SystemExit(f"Malformed dependency coordinate: {line!r}")
        group, artifact, version = parts
        dependencies.append((f"{group}:{artifact}", version))
    if not dependencies:
        raise SystemExit("No resolved dependencies were exported for OSV audit.")
    return dependencies


def load_allowlist():
    if not ALLOWLIST.exists():
        return set()
    return {
        line.strip()
        for line in ALLOWLIST.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }


def query_batch(batch):
    payload = {
        "queries": [
            {
                "package": {"ecosystem": "Maven", "name": name},
                "version": version,
            }
            for name, version in batch
        ]
    }
    request = urllib.request.Request(
        OSV_BATCH_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "CoinePro-Security-CI/1"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.load(response)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        raise SystemExit(f"OSV audit could not complete reliably: {error}") from error
    results = body.get("results")
    if not isinstance(results, list) or len(results) != len(batch):
        raise SystemExit("OSV batch response did not match the dependency query set.")
    return results


def main():
    dependencies = load_dependencies()
    allowlist = load_allowlist()
    findings = []

    for start in range(0, len(dependencies), BATCH_SIZE):
        batch = dependencies[start:start + BATCH_SIZE]
        results = query_batch(batch)
        for coordinate, result in zip(batch, results):
            name, version = coordinate
            for vulnerability in result.get("vulns", []) or []:
                vulnerability_id = vulnerability.get("id")
                if not vulnerability_id:
                    continue
                allow_key = f"{vulnerability_id}|{name}|{version}"
                if allow_key not in allowlist:
                    findings.append((vulnerability_id, name, version))

    if findings:
        print("Known vulnerabilities found in resolved Android runtime dependencies:", file=sys.stderr)
        for vulnerability_id, name, version in sorted(set(findings)):
            print(f"  {vulnerability_id}: {name}:{version}", file=sys.stderr)
        print(
            "Fix/upgrade the dependency. If a finding is proven non-applicable, add an exact "
            "VULN_ID|group:artifact|version entry to scripts/security/osv-allowlist.txt and document the rationale.",
            file=sys.stderr,
        )
        return 1

    print(f"OSV audit passed for {len(dependencies)} resolved Maven dependencies.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
