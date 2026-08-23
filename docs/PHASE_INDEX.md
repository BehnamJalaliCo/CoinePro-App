# CoinePro Phase Index

This file is the canonical map between delivery phases, Git branches, milestone commits and Android CI.

## Source of truth

- Project repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative integration branch and currently contains all completed Android work through Phase 6.
- `feat/...` branches below are milestone pointers to the exact end state of each completed phase. They are not separate competing implementations.
- External production runtime setup, vendor credentials, broker connectivity, IP whitelisting and end-to-end live smoke tests are intentionally deferred to Phase 17.
- No other repository is part of this phase ledger.

## Completed phase milestones

| Phase | Scope | Milestone branch | End commit | Android CI |
| --- | --- | --- | --- | --- |
| 0 | Foundation bootstrap | `bootstrap/android-foundation` | foundation history on integration branch | green |
| 1 | Design system + architecture skeleton | `bootstrap/android-foundation` | architecture history on integration branch | green |
| 2 | Authentication / Session / Entitlements | `feat/android-mobile-auth` | `12cc837ac02e378f3ca4452a95bfed224ad3222b` | Run #11 — success |
| 3 | Realtime Market Data Foundation | `feat/phase3-realtime-market-data` | `7158a78ef6ee378ec531576bf7d9364816d25b56` | Run #14 — success |
| 4 | Signals Core | `feat/phase4-signals-core` | `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` | Run #17 — success |
| 5 | Alerts & Push | `feat/phase5-alerts-push` | `60dfd64259ec92775b38288f2a4dc8e4c50169e9` | Run #41 — success |
| 6 | Connections & Signal Execution Bridge | `feat/phase6-signal-execution` | `710ede98b19c74244e61048174fdd3939b0cb98a` | Run #65 — success |

## Current integration head

`bootstrap/android-foundation` = `710ede98b19c74244e61048174fdd3939b0cb98a`

That commit is the completed Phase 6 Android state. Phase 6 includes signal-scoped execution, connection UI, idempotency request IDs, quantity validation, explicit execution truth states, active executed-signal loading, and safe close-action gating.

## Next phase

Phase 7 — AI Generated Market Signal.

## Branch rule from Phase 7 onward

For every new phase:

1. Create `feat/phaseN-<scope>` in this repository only.
2. Build the phase on top of the current cumulative integration head.
3. Run the phase-specific unit tests plus app lint/test/assemble CI.
4. When green, record the exact end SHA and CI run in this file.
5. Advance `bootstrap/android-foundation` to the green phase end.
6. Keep production-only external connectivity validation for Phase 17 unless the phase explicitly requires a local deterministic contract test.

This prevents phase ownership from being split across repositories or inferred from memory.