#!/usr/bin/env python3
"""Phase 17 production read-only smoke.

This script intentionally performs GET requests only. It never creates, executes,
closes, retries, or mutates a trade/provider connection. Output is sanitized so
bearer tokens, credentials, raw AI content, and account identifiers are not
written to the evidence artifact.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ALLOWED_SYMBOLS = {"XAUUSD", "XAGUSD"}
FUTURE_SKEW_MS = 10_000


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def get_json(base_url: str, path: str, token: str) -> dict:
    url = urllib.parse.urljoin(base_url, path)
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "User-Agent": "CoinePro-Phase17-ReadOnly-Smoke/1",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status < 200 or response.status >= 300:
                fail(f"GET {path} returned HTTP {response.status}")
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        fail(f"GET {path} returned HTTP {error.code}")
    except urllib.error.URLError as error:
        fail(f"GET {path} failed: {error.reason}")
    if not isinstance(payload, dict):
        fail(f"GET {path} did not return a JSON object")
    return payload


def normalize_symbols(raw: str) -> list[str]:
    values = [value.strip().upper() for value in raw.split(",") if value.strip()]
    if not values:
        fail("At least one smoke symbol is required")
    for value in values:
        if value not in ALLOWED_SYMBOLS and not (value.endswith("USDT") and len(value) > 4):
            fail(f"Unsupported smoke symbol: {value}")
    return values


def first_present(value: dict, *names: str):
    for name in names:
        if name in value and value[name] is not None:
            return value[name]
    return None


def quote_freshness_threshold_ms(source: str) -> int:
    lowered = source.lower()
    if "lbank" in lowered:
        return 15_000
    if "finnhub" in lowered:
        return 90_000
    return 30_000


def validate_snapshot(payload: dict, symbols: list[str], now_ms: int) -> list[dict]:
    prices = payload.get("prices")
    if not isinstance(prices, dict):
        fail("Market snapshot is missing prices object")

    evidence: list[dict] = []
    for symbol in symbols:
        quote = prices.get(symbol)
        if not isinstance(quote, dict):
            fail(f"Market snapshot missing requested symbol {symbol}")
        value = quote.get("price")
        timestamp = first_present(quote, "ts", "received_at_ms", "receivedAtMs")
        source = first_present(quote, "source", "venue")
        if not isinstance(value, (int, float)) or not math.isfinite(float(value)) or float(value) <= 0:
            fail(f"Invalid price for {symbol}")
        if not isinstance(timestamp, int) or timestamp <= 0:
            fail(f"Missing/invalid source timestamp for {symbol}")
        if not isinstance(source, str) or not source.strip():
            fail(f"Missing provider/source identity for {symbol}")

        source = source.strip()
        age_ms = now_ms - timestamp
        threshold_ms = quote_freshness_threshold_ms(source)
        if age_ms < -FUTURE_SKEW_MS:
            fail(f"Source timestamp for {symbol} is implausibly in the future")
        if age_ms > threshold_ms:
            fail(
                f"Stale production quote for {symbol}: age={age_ms}ms exceeds "
                f"{threshold_ms}ms threshold for {source}"
            )

        evidence.append(
            {
                "symbol": symbol,
                "source": source,
                "timestamp_ms": timestamp,
                "age_ms": age_ms,
                "freshness_threshold_ms": threshold_ms,
                "fresh": True,
                "price_present_and_positive": True,
            }
        )
    return evidence


def sanitize_connections(payload: dict) -> dict:
    result: dict[str, dict] = {}
    for venue in ("mt5", "lbank"):
        value = payload.get(venue)
        if value is None:
            result[venue] = {"configured": False, "connected": False, "status": "missing"}
            continue
        if not isinstance(value, dict):
            fail(f"Invalid {venue} connection payload")
        result[venue] = {
            "configured": bool(value.get("configured", False)),
            "connected": bool(value.get("connected", False)),
            "status": str(value.get("status") or "unknown")[:80],
        }
    return result


def sanitize_execution_history(payload: dict) -> dict:
    items = payload.get("items")
    if not isinstance(items, list):
        fail("Execution history payload is missing items array")
    statuses: dict[str, int] = {}
    for item in items:
        if not isinstance(item, dict):
            continue
        status = str(item.get("status") or "unknown").lower()[:50]
        statuses[status] = statuses.get(status, 0) + 1
    return {"count": len(items), "status_counts": statuses}


def sanitize_ai_vision(payload: dict) -> dict:
    job = payload.get("job")
    if not isinstance(job, dict):
        fail("AI Vision response is missing job object")
    result = job.get("result")
    signal_id = first_present(result, "signal_id", "signalId") if isinstance(result, dict) else None
    return {
        "status": str(job.get("status") or "unknown")[:50],
        "has_structured_result": isinstance(result, dict),
        "validated": bool(result.get("validated", False)) if isinstance(result, dict) else False,
        "has_positive_signal_id": isinstance(signal_id, int) and signal_id > 0,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbols", default="XAUUSD,XAGUSD")
    parser.add_argument("--ai-vision-job-id", default="")
    parser.add_argument("--output", default="phase17-production-readonly-smoke.json")
    args = parser.parse_args()

    base_url = os.environ.get("COINEPRO_PRODUCTION_API_BASE_URL", "").strip()
    token = os.environ.get("COINEPRO_PRODUCTION_SMOKE_BEARER_TOKEN", "").strip()
    if not base_url.startswith("https://"):
        fail("COINEPRO_PRODUCTION_API_BASE_URL must be an HTTPS URL")
    if not base_url.endswith("/"):
        base_url += "/"
    if not token:
        fail("COINEPRO_PRODUCTION_SMOKE_BEARER_TOKEN is required")

    symbols = normalize_symbols(args.symbols)
    query = urllib.parse.urlencode({"symbols": ",".join(symbols)})
    snapshot = get_json(base_url, f"ws/snapshot?{query}", token)
    connections = get_json(base_url, "user/signals/execution/connections", token)
    executions = get_json(base_url, "user/signals/execution/executions?limit=10", token)
    now_ms = int(time.time() * 1000)

    evidence = {
        "schema": 2,
        "mode": "production-readonly",
        "writes_performed": False,
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "market": validate_snapshot(snapshot, symbols, now_ms),
        "connections": sanitize_connections(connections),
        "execution_history": sanitize_execution_history(executions),
        "ai_vision": {"checked": False},
    }

    job_id = args.ai_vision_job_id.strip()
    if job_id:
        if not all(character.isalnum() or character in "-_" for character in job_id):
            fail("AI Vision job ID contains unsupported characters")
        payload = get_json(base_url, f"user/ai/vision/jobs/{urllib.parse.quote(job_id)}", token)
        evidence["ai_vision"] = {"checked": True, **sanitize_ai_vision(payload)}

    Path(args.output).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Read-only production smoke passed; sanitized evidence written to {args.output}")


if __name__ == "__main__":
    main()
