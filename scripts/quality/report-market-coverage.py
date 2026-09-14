#!/usr/bin/env python3
"""How much of the exchange's book this app can draw, and what the gap is made of.

The owner asked why the crypto tab lists 189 markets when LBank quotes far more. The answer is
neither a cap in the app nor the backend's scope: it is **artwork**. `SymbolArtwork.covers` is the
filter at the catalogue and at the live feed — a symbol with no mark would reach a list as a grey
disc with a letter in it, which is the one defect the house rules name outright — and the set of
marks this repository holds is what decides the number.

So this script asks LBank what it lists, intersects that with the marks under
``design/asset-logos``, and ranks what is missing by the exchange's own 24-hour turnover. The
ranking is the point: «829 markets missing» is not actionable and «these twenty carry six million
dollars a day each» is.

Usage:
    python3 scripts/quality/report-market-coverage.py            # summary + top 25 uncovered
    python3 scripts/quality/report-market-coverage.py --top 100  # a longer list to source art for

Network: two public LBank endpoints, no key. It is a *report*, not a gate — nothing in CI depends
on an exchange being reachable.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ARTWORK = REPO / "core/symbols/src/main/kotlin/com/coinepro/core/symbols/SymbolArtwork.kt"

PAIRS_URL = "https://api.lbkex.com/v2/currencyPairs.do"
TICKER_URL = "https://api.lbkex.com/v2/ticker/24hr.do?symbol=all"


def fetch(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def bases() -> set[str]:
    """The crypto bases the app has a mark for, read out of the Kotlin set itself."""
    text = ARTWORK.read_text(encoding="utf-8")
    block = re.search(r"val BASES: Set<String> = setOf\((.*?)\n\s*\)", text, re.S)
    if block is None:
        raise SystemExit("SymbolArtwork.BASES not found — has the file been restructured?")
    return {b.replace("\\$", "$") for b in re.findall(r'"((?:[^"\\]|\\.)*)"', block.group(1))}


def turnover(row: dict) -> float:
    try:
        return float(row.get("ticker", {}).get("turnover") or 0)
    except (TypeError, ValueError):
        return 0.0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--top", type=int, default=25, help="how many uncovered markets to list")
    args = parser.parse_args()

    try:
        pairs = fetch(PAIRS_URL)["data"]
        rows = fetch(TICKER_URL).get("data", [])
    except Exception as error:  # noqa: BLE001 — a report, not a gate: say so and stop.
        print(f"LBank unreachable: {type(error).__name__}: {error}", file=sys.stderr)
        return 0

    art = bases()
    usdt_pairs = [p for p in pairs if p.endswith("_usdt")]
    traded = [r for r in rows if str(r.get("symbol", "")).endswith("_usdt")]
    traded.sort(key=turnover, reverse=True)

    def base_of(symbol: str) -> str:
        return symbol.split("_")[0].upper()

    covered_pairs = [p for p in usdt_pairs if base_of(p) in art]
    covered_rows = [r for r in traded if base_of(r["symbol"]) in art]
    total = sum(turnover(r) for r in traded)
    ours = sum(turnover(r) for r in covered_rows)

    print(f"marks in SymbolArtwork.BASES        {len(art)}")
    print(f"LBank USDT pairs listed             {len(usdt_pairs)}")
    print(f"  of those, we have a mark for      {len(covered_pairs)}")
    print(f"LBank USDT pairs traded in 24h      {len(traded)}")
    print(f"  of those, we have a mark for      {len(covered_rows)}")
    if total > 0:
        print(f"share of 24h turnover we can draw   {100 * ours / total:.1f}%")

    missing = [r for r in traded if base_of(r["symbol"]) not in art]
    print(f"\ntop {args.top} markets we cannot draw, by 24h turnover (USDT):")
    for row in missing[: args.top]:
        print(f"  {base_of(row['symbol']):14s} {turnover(row):>16,.0f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
