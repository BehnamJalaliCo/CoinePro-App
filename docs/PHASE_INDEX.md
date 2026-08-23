# CoinePro Phase Index

This file is the canonical map between delivery phases, Git branches, milestone commits and validation state.

## Source of truth

- Project repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative Android integration branch.
- All phase branches, PRs, CI checks and phase bookkeeping for this project belong in this repository only.
- Other repositories are outside the scope of this phase ledger.
- Production credentials, vendor whitelisting and real external smoke tests remain later launch-readiness work unless explicitly moved into an earlier phase.

## Completed milestones

| Phase | Scope | Milestone branch | Validated commit | Android CI |
| --- | --- | --- | --- | --- |
| 0 | Foundation bootstrap | `bootstrap/android-foundation` | foundation history | green |
| 1 | Design system + architecture skeleton | `bootstrap/android-foundation` | architecture history | green |
| 2 | Authentication / Session / Entitlements | `feat/android-mobile-auth` | `12cc837ac02e378f3ca4452a95bfed224ad3222b` | Run #11 — success |
| 3 | Realtime Market Data Foundation | `feat/phase3-realtime-market-data` | `7158a78ef6ee378ec531576bf7d9364816d25b56` | Run #14 — success |
| 4 | Signals Core | `feat/phase4-signals-core` | `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` | Run #17 — success |
| 5 | Alerts & Push | `feat/phase5-alerts-push` | `60dfd64259ec92775b38288f2a4dc8e4c50169e9` | Run #41 — success |
| 6 | Connections & Signal Execution Bridge | `feat/phase6-signal-execution` | `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` | Run #86 — success |
| 7 | AI Generated Market Signal | `feat/phase7-ai-generated-market-signal` | `be8643d9aa07f44031f49b472536fc074a22dbea` | Run #94 — success |

## Phase 7 status

**Closed / Complete.**

Final validated head: `be8643d9aa07f44031f49b472536fc074a22dbea`, Android CI Run #94 — **success**.

## Phase 8 — AI Vision Flagship

Status: **In progress.**

Implementation scope:

- image input from camera or gallery without storing raw image credentials or secrets
- explicit user-selected symbol/timeframe context; no market guessing from pixels
- authenticated multipart upload to an AI Vision analysis job endpoint
- server-truth `QUEUED / RUNNING / DONE / FAILED / EXPIRED` lifecycle
- entitlement and quota states derived from server response
- structured analysis result with trend, entry, stop, targets, confidence and explanation
- strict validation: unsupported symbols/timeframes, invalid prices, invalid target ordering, mismatched request/result context and `validated=false` are blocked
- no raw model output is executable
- result may open only a persisted validated `signal_id`; execution remains Signal Detail → Execution
- local image preview is cleared when the analysis finishes, fails, expires, is cancelled or the user signs out
- retry requires an explicit image selection again after terminal failure/expiry
- Phase 8 unit tests must be part of Android CI with all prior phase gates

Exit gate:

1. Phase 8 module and UI compile.
2. Vision job state tests pass.
3. Structured-result validation tests pass.
4. App lint/test/assemble passes.
5. Exact green SHA and CI run are recorded here before Phase 9 starts.

## Next phase

Phase 9 starts only after Phase 8 reaches the exit gate above.

## Branch rule

All phase work remains in `BehnamJalaliCo/CoinePro-App` only.
