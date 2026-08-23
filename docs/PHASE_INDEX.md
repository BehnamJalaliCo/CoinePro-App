# CoinePro Phase Index

This file is the canonical map between delivery phases, Git branches, milestone commits and validation state.

## Source of truth

- Project repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative Android integration branch.
- All phase branches and PRs for this project belong in this repository only.
- Production vendor credentials, broker connectivity, IP whitelisting and real external smoke tests remain Phase 17 work.

## Historical Android milestones

| Phase | Scope | Milestone branch | Original end commit | Original Android CI |
| --- | --- | --- | --- | --- |
| 0 | Foundation bootstrap | `bootstrap/android-foundation` | foundation history | green |
| 1 | Design system + architecture skeleton | `bootstrap/android-foundation` | architecture history | green |
| 2 | Authentication / Session / Entitlements | `feat/android-mobile-auth` | `12cc837ac02e378f3ca4452a95bfed224ad3222b` | Run #11 — success |
| 3 | Realtime Market Data Foundation | `feat/phase3-realtime-market-data` | `7158a78ef6ee378ec531576bf7d9364816d25b56` | Run #14 — success |
| 4 | Signals Core | `feat/phase4-signals-core` | `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` | Run #17 — success |
| 5 | Alerts & Push | `feat/phase5-alerts-push` | `60dfd64259ec92775b38288f2a4dc8e4c50169e9` | Run #41 — success |
| 6 | Connections & Signal Execution Bridge | `feat/phase6-signal-execution` | `710ede98b19c74244e61048174fdd3939b0cb98a` | Run #65 — success |

These rows preserve the original phase checkpoints. They do not override later audit findings.

## Phase 1–6 audit hardening

A full client-side audit after Phase 6 found and fixed several gaps on `bootstrap/android-foundation`:

- Phase 2 authentication unit tests are now an explicit CI gate; network revalidation failure is covered and protected navigation stays locked.
- Phase 3 rejects out-of-scope market symbols instead of guessing an unknown symbol as Crypto.
- Phase 4 enforces Forex V1 (`XAUUSD` / `XAGUSD`) and Crypto (`*USDT`) scope in the Android signal mapper.
- Phase 5 validates alert symbols and finite positive prices on both outgoing requests and incoming server payloads.
- Phase 6 renders active executed signals in Activity, prevents duplicate close requests after `CLOSE_REQUESTED`, keeps LBank Close hidden after submit/open, and adds quantity/close-gating tests.

Audited Android code checkpoint:
- SHA: `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26`
- Android CI Run #86: **success**
- CI gate includes `core:auth`, `core:marketdata`, `core:signals`, `core:notifications`, `core:execution`, app lint, app unit tests, debug assembly and APK upload.

## Certification status

- Phase 1: Android/client implementation audited and complete.
- Phase 2: Android/client implementation audited and complete.
- Phase 3: Android/client implementation audited and green; end-to-end certification still requires a verified server implementation of the normalized realtime HTTP/WebSocket contract.
- Phase 4: Android/client implementation audited and green; end-to-end certification still requires a verified authenticated Signal list/detail server contract.
- Phase 5: Android/client implementation audited and green; duplicate push/event suppression and device/alert lifecycle remain server guarantees and must be verified against the actual backend.
- Phase 6: Android/client implementation audited and green; idempotent order creation, signal-scope enforcement, durable execution audit trail and provider state transitions are server guarantees and must be verified against the actual backend.

The Android repository contains the client contracts for Phases 3–6, but it does not currently contain a backend codebase implementing those server guarantees. Production provider activation may wait until Phase 17; the existence and correctness of the application-facing server contract may not be deferred if a phase is to be certified end-to-end.

## Next phase gate

Phase 7 — AI Generated Market Signal is **blocked from certification/start of implementation** until the actual backend location/implementation for the Phase 3–6 application-facing contracts is identified and validated, or an approved backend architecture is added to this repository.

## Branch rule

For every future phase:

1. Create `feat/phaseN-<scope>` in `BehnamJalaliCo/CoinePro-App` only.
2. Build on top of the current cumulative integration head.
3. Run phase-specific unit tests plus app lint/test/assemble CI.
4. Record both client validation and any required server-contract validation; do not call a phase complete from Android CI alone when exit criteria require server guarantees.
5. Keep production-only credentials, whitelisting and live external provider smoke tests for Phase 17 unless an earlier phase explicitly requires them.

This prevents phase ownership from being split across repositories and prevents documentation from claiming completion before the required contract is actually verifiable.
