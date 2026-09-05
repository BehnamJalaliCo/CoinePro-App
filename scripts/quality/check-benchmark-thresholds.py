#!/usr/bin/env python3
"""Fail when a macrobenchmark result misses the budget.

Reads the JSON files the Macrobenchmark library writes
(`benchmark/build/outputs/connected_android_test_additional_output/**/*-benchmarkData.json`) and
checks each benchmark's metrics against the budget below. The budget is the audit's:

  * chart fling, pinch, drag: `frameDurationCpuMs` P95 ≤ 8 ms, `frameOverrunMs` P95 ≤ 0
    (no jank frame during the gesture);
  * cold start with a baseline profile: `timeToInitialDisplayMs` median ≤ 800 ms.

No JSON is not a pass. With nothing to read the script says so and exits 0 only when told the
run was skipped on purpose (`--allow-missing`), which is how CI behaves on a runner without a
device; a runner that did run the benchmark and produced nothing fails.

Usage: `python3 scripts/quality/check-benchmark-thresholds.py [--root DIR] [--allow-missing]`
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# benchmark name → {metric → (statistic, ceiling)}
BUDGET: dict[str, dict[str, tuple[str, float]]] = {
    "flingAcrossHistory": {"frameDurationCpuMs": ("P95", 8.0), "frameOverrunMs": ("P95", 0.0)},
    "pinchZoom": {"frameDurationCpuMs": ("P95", 8.0), "frameOverrunMs": ("P95", 0.0)},
    "panAndHold": {"frameDurationCpuMs": ("P95", 8.0), "frameOverrunMs": ("P95", 0.0)},
    "coldStartupWithBaselineProfile": {"timeToInitialDisplayMs": ("median", 800.0)},
}


def results(root: Path) -> list[Path]:
    return sorted(root.glob("benchmark/build/outputs/**/*-benchmarkData.json"))


def statistic(metric: dict, name: str) -> float | None:
    # The library writes `minimum`, `maximum`, `median` and, for sampled metrics, `P50`…`P99`.
    value = metric.get(name)
    if value is None and name == "median":
        value = metric.get("P50")
    return float(value) if value is not None else None


def check(path: Path) -> list[str]:
    failures: list[str] = []
    data = json.loads(path.read_text(encoding="utf-8"))
    for benchmark in data.get("benchmarks", []):
        name = benchmark.get("name", "?")
        budget = BUDGET.get(name)
        if budget is None:
            continue
        metrics = benchmark.get("metrics", {})
        # Sampled metrics (frame timing) live under `sampledMetrics`, one-shot ones under `metrics`.
        metrics = {**metrics, **benchmark.get("sampledMetrics", {})}
        for metric_name, (stat, ceiling) in budget.items():
            metric = metrics.get(metric_name)
            if metric is None:
                failures.append(f"{name}: no {metric_name} in {path.name}")
                continue
            value = statistic(metric, stat)
            if value is None:
                failures.append(f"{name}: {metric_name} has no {stat}")
            elif value > ceiling:
                failures.append(f"{name}: {metric_name} {stat} = {value:.2f} > {ceiling:g}")
            else:
                print(f"{name}: {metric_name} {stat} = {value:.2f} ≤ {ceiling:g}")
    return failures


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=Path(__file__).resolve().parents[2], type=Path)
    parser.add_argument("--allow-missing", action="store_true", help="exit 0 when no results exist")
    arguments = parser.parse_args()
    files = results(arguments.root)
    if not files:
        message = "no benchmark results under benchmark/build/outputs"
        if arguments.allow_missing:
            print(f"check-benchmark-thresholds: {message}; skipped")
            sys.exit(0)
        print(f"::error::{message}")
        sys.exit(1)
    failures = [failure for path in files for failure in check(path)]
    for failure in failures:
        print(f"::error::{failure}")
    if failures:
        sys.exit(1)
    print(f"Benchmark budget met in {len(files)} result file(s).")


if __name__ == "__main__":
    main()
