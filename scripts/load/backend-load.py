#!/usr/bin/env python3
"""Put the two backends under the load the owner asked about, and report what breaks first.

    python3 scripts/load/backend-load.py --base https://staging.example --users 5000

WHAT THIS IS, AND WHAT IT IS NOT
--------------------------------
The owner asked to see the app under five to ten thousand concurrent users. That number is a
**server** question, not a client one: an Android app runs on one phone with one user, and no amount
of load on the phone reproduces ten thousand people. What ten thousand people do is hit
`/public/prices`, `/signals/active` and the price socket at the same moment, and what gives way is a
connection pool, a rate limiter or a database, on CoinePro-FX and TradeYar.

So this script drives the *server* with the exact request mix the app produces, at a concurrency the
caller sets, and reports latency percentiles, error rates and the point at which the failure rate
turns. The client half of the question — what the app itself does when the feed is fast and the
catalogue is large — is a different measurement and lives in
`core/marketdata/src/test/.../MarketFeedLoadTest.kt`, which runs on every build.

SAFETY, AND WHY IT IS NOT OPTIONAL
-----------------------------------
Five thousand concurrent requests is indistinguishable from an attack, and pointed at production it
*is* an outage. So:

  * `--base` is required and has no default. There is no way to run this by accident.
  * A host that looks like production is refused unless `--i-own-this-server` is passed as well.
  * It ramps rather than starting flat, and it stops early when the error rate passes `--abort-above`
    — a load test that keeps hammering a server it has already knocked over is measuring nothing and
    costing something.
  * Read-only routes only. Nothing here signs in, places an order, or writes.

Run it against staging. If staging is not a copy of production's shape, the number it gives you is
about staging.

THE MIX
-------
Taken from what the app actually does in a session, in the proportions it does it — not an even
split across endpoints, which would over-test the routes nobody opens. See `SESSION_MIX`.
"""

from __future__ import annotations

import argparse
import asyncio
import random
import statistics
import sys
import time
from dataclasses import dataclass, field

try:
    import aiohttp
except ImportError:  # pragma: no cover - the message is the whole point
    sys.exit(
        "aiohttp is required: python3 -m pip install aiohttp\n"
        "It is not vendored because this script is not part of the build.",
    )


# What one reader's session hits, and how often, as weights. A market list refresh happens on every
# open and on every pull; a signal detail happens when somebody taps a row, which is far rarer.
# Weighting this wrongly is how a load test concludes the wrong endpoint is the bottleneck.
SESSION_MIX: list[tuple[str, int]] = [
    ("/public/prices", 40),
    ("/public/signals/active", 20),
    ("/public/track-record", 8),
    ("/public/news", 6),
    ("/academy/chart/BTCUSDT", 14),
    ("/academy/chart/symbols", 4),
    ("/public/market/catalog", 8),
]


@dataclass
class Outcome:
    latencies_ms: list[float] = field(default_factory=list)
    statuses: dict[int, int] = field(default_factory=dict)
    failures: dict[str, int] = field(default_factory=dict)

    def record(self, millis: float, status: int | None, failure: str | None) -> None:
        self.latencies_ms.append(millis)
        if status is not None:
            self.statuses[status] = self.statuses.get(status, 0) + 1
        if failure is not None:
            self.failures[failure] = self.failures.get(failure, 0) + 1

    @property
    def total(self) -> int:
        return len(self.latencies_ms)

    @property
    def bad(self) -> int:
        wrong_status = sum(n for s, n in self.statuses.items() if s >= 400)
        return wrong_status + sum(self.failures.values())

    @property
    def error_rate(self) -> float:
        return self.bad / self.total if self.total else 0.0

    def percentile(self, p: float) -> float:
        if not self.latencies_ms:
            return 0.0
        ordered = sorted(self.latencies_ms)
        index = min(len(ordered) - 1, int(len(ordered) * p))
        return ordered[index]


def looks_like_production(base: str) -> bool:
    """Refuse anything that is not obviously a test target unless the caller insists."""
    lowered = base.lower()
    safe_markers = ("localhost", "127.0.0.1", "staging", "stage.", "test.", "dev.", ".local")
    return not any(marker in lowered for marker in safe_markers)


async def one_request(session: aiohttp.ClientSession, url: str, outcome: Outcome) -> None:
    started = time.perf_counter()
    try:
        async with session.get(url) as response:
            await response.read()
            outcome.record((time.perf_counter() - started) * 1000, response.status, None)
    except asyncio.TimeoutError:
        outcome.record((time.perf_counter() - started) * 1000, None, "timeout")
    except aiohttp.ClientError as error:
        outcome.record((time.perf_counter() - started) * 1000, None, type(error).__name__)


async def run_stage(base: str, users: int, seconds: int, timeout: float) -> Outcome:
    outcome = Outcome()
    paths = [p for p, weight in SESSION_MIX for _ in range(weight)]
    limit = aiohttp.TCPConnector(limit=users, ttl_dns_cache=300)
    deadline = time.perf_counter() + seconds

    async with aiohttp.ClientSession(
        connector=limit,
        timeout=aiohttp.ClientTimeout(total=timeout),
        headers={"User-Agent": "CoinePro-load/1.0"},
    ) as session:
        async def one_user() -> None:
            while time.perf_counter() < deadline:
                await one_request(session, base + random.choice(paths), outcome)
                # A real reader is not a tight loop. Without this the script measures how fast the
                # server can refuse, not how it behaves under a plausible arrival rate.
                await asyncio.sleep(random.uniform(0.4, 1.6))

        await asyncio.gather(*(one_user() for _ in range(users)))
    return outcome


def report(label: str, outcome: Outcome) -> None:
    print(f"\n=== {label} ===")
    print(f"  requests      {outcome.total}")
    print(f"  error rate    {outcome.error_rate:.2%}")
    print(f"  p50 / p95 / p99   "
          f"{outcome.percentile(0.50):.0f} / {outcome.percentile(0.95):.0f} / "
          f"{outcome.percentile(0.99):.0f} ms")
    if outcome.latencies_ms:
        print(f"  max           {max(outcome.latencies_ms):.0f} ms")
    if outcome.statuses:
        codes = ", ".join(f"{s}×{n}" for s, n in sorted(outcome.statuses.items()))
        print(f"  statuses      {codes}")
    if outcome.failures:
        fails = ", ".join(f"{k}×{v}" for k, v in sorted(outcome.failures.items()))
        print(f"  failures      {fails}")


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--base", required=True, help="base URL, e.g. https://staging.example")
    parser.add_argument("--users", type=int, default=5000, help="peak concurrent readers")
    parser.add_argument("--seconds", type=int, default=30, help="seconds at each stage")
    parser.add_argument("--timeout", type=float, default=15.0, help="per-request timeout")
    parser.add_argument(
        "--abort-above",
        type=float,
        default=0.25,
        help="stop ramping once a stage's error rate passes this",
    )
    parser.add_argument("--i-own-this-server", action="store_true")
    args = parser.parse_args()

    base = args.base.rstrip("/")
    if looks_like_production(base) and not args.i_own_this_server:
        sys.exit(
            f"{base} does not look like a test target.\n"
            "Five thousand concurrent requests is an outage, not a measurement. Point this at "
            "staging, or pass --i-own-this-server if you are certain.",
        )

    # Ramped, because the number worth having is not "does it survive 5,000" but "where does it
    # turn". A flat run at the peak tells you it failed and not when.
    stages = [max(1, args.users // 10), max(1, args.users // 4), max(1, args.users // 2), args.users]
    print(f"target {base}  stages {stages}  {args.seconds}s each")

    for users in stages:
        outcome = await run_stage(base, users, args.seconds, args.timeout)
        report(f"{users} concurrent", outcome)
        if outcome.error_rate > args.abort_above:
            print(
                f"\nStopping: {outcome.error_rate:.0%} of requests failed at {users} concurrent, "
                f"above the {args.abort_above:.0%} abort threshold.\n"
                "That is the answer — the ceiling is below this stage.",
            )
            return 1

    print("\nEvery stage stayed under the abort threshold.")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
