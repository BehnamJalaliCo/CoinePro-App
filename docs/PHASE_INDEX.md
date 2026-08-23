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

## Phase 1–6 audit closure

A final client-side audit was completed before closing Phase 6. The audit found and fixed the following gaps on `bootstrap/android-foundation`:

- Phase 2 authentication unit tests are an explicit CI gate and network revalidation failure is covered.
- Phase 3 rejects unsupported market symbols instead of guessing their market type.
- Phase 4 enforces Forex V1 (`XAUUSD` / `XAGUSD`) and Crypto (`*USDT`) scope in the Android signal mapper.
- Phase 5 validates alert symbols and finite positive prices for outgoing requests and incoming payloads.
- Phase 6 renders active executed signals in Activity, prevents duplicate close requests after `CLOSE_REQUESTED`, keeps LBank Close hidden after submit/open, and includes explicit quantity/close-gating tests.

Final audited Phase 6 checkpoint:

- SHA: `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26`
- Android CI Run #86: **success**
- CI gate includes `core:auth`, `core:marketdata`, `core:signals`, `core:notifications`, `core:execution`, app lint, app unit tests, debug assembly and APK artifact upload.

## Phase 6 status

**Closed / Complete for the current CoinePro-App project scope.**

Validated Phase 6 behavior includes:

- MT5/LBank connection-state UI and secure credential submission boundary
- signal-scoped execution confirmation with no arbitrary-symbol New Trade surface
- quantity/lot validation before execution request
- idempotency request ID on every execution attempt
- explicit `QUEUED / SUBMITTED / OPEN / CLOSE_REQUESTED / CLOSED / FAILED / CANCELLED / UNKNOWN` truth states
- no fake provider-open state
- active executed-signal tracking in Activity
- LBank Close hidden after submit/open until its external lifecycle is intentionally enabled later
- queued requests may be cancelled before provider acknowledgement
- Android does not persist trading credentials or render them back into logs/UI

No other repository is required to close Phase 6 in this ledger.

## Next phase

**Phase 7 — AI Generated Market Signal**

Status: **Ready to start.**

## Branch rule from Phase 7 onward

For every new phase:

1. Create `feat/phaseN-<scope>` in `BehnamJalaliCo/CoinePro-App` only.
2. Build on top of the current cumulative integration head.
3. Run phase-specific unit tests plus app lint/test/assemble CI.
4. When green, record the exact end SHA and CI run in this file.
5. Keep the project ledger and PR descriptions aligned with the validated code state.

This prevents phase ownership from being split across repositories or inferred from unrelated codebases.
