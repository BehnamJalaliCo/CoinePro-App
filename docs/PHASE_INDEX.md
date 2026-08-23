# CoinePro Phase Index

Canonical repository: `BehnamJalaliCo/CoinePro-App`.

`main` is the stable base. `bootstrap/android-foundation` is the cumulative integration base. Phase PRs remain Draft and unmerged unless merge is explicitly approved.

## Canonical milestones

| Phase | Scope | Milestone branch | Validated checkpoint | Validation |
| --- | --- | --- | --- | --- |
| 0 | Foundation bootstrap | `bootstrap/android-foundation` | foundation history | green |
| 1 | Design system + architecture skeleton | `bootstrap/android-foundation` | architecture history | green |
| 2 | Authentication / Session / Entitlements | `feat/android-mobile-auth` | `12cc837ac02e378f3ca4452a95bfed224ad3222b` | Android #11 success |
| 3 | Realtime Market Data Foundation | `feat/phase3-realtime-market-data` | `7158a78ef6ee378ec531576bf7d9364816d25b56` | Android #14 success |
| 4 | Signals Core | `feat/phase4-signals-core` | `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` | Android #17 success |
| 5 | Alerts & Push | `feat/phase5-alerts-push` | `60dfd64259ec92775b38288f2a4dc8e4c50169e9` | Android #41 success |
| 6 | Connections & Signal Execution Bridge | `feat/phase6-signal-execution` | `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` | Android #86 success |
| 7 | AI Generated Market Signal | `feat/phase7-ai-generated-market-signal` | `f718d9ad310ab37d4b109297c4fadcb33e287775` | Android #91 success |
| 8 | AI Vision Flagship | `feat/phase8-ai-vision` | `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` | Android #101 success |
| 9 | AI Assistant | `feat/phase9-ai-assistant` | `3d158c9d0fc72724e9bbf402ae81540300950cc3` | Android #114 success |
| 10 | News & Economic Calendar | `feat/phase10-news-economic-calendar` | `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` | Android #121 success |
| 11 | Trader Tools | `feat/phase11-trader-tools` | `11d91b2cb90a484611a1b1c773187b7c2b2795e4` | Android #126 success |
| 12 | Activity, History & Performance | `feat/phase12-activity-history-performance` | `23f7113d83acdcfda74798380f04da1c7447be9f` | Android #132 success |
| 13 | Offline, Reliability & Background Work | `feat/phase13-offline-reliability-background-work` | `a6b664f035e047afd51515b3481452d57ecd1ee9` | Android #159 success |
| 14 | Security Hardening | `feat/phase14-security-hardening` | `8abdb6909beb2468ec10c911ebd22ad8411a1b5f` | Android #184 + Security #16 success |
| 15 | Quality, Performance & Accessibility | `feat/phase15-quality-performance-accessibility` | code `97d3ebc0165be27e86ad97dceef16494f7a7b428`; final docs `a8d26b7df6332f569f963756a2e041ad31b3cdab` | Android #208/#212 + Security #40/#44 success |
| 16 | Release Engineering | `feat/phase16-release-engineering` | code `0681a763cf504275b60e50495d3c64d13f73ac79`; final docs `5a1a02daf72acc60581665b3aee27dec713b400c` | Android #226/#230 + Security #58/#62 success |
| 17 | Launch Readiness + Phase 1–17 reconciliation | `feat/phase17-launch-readiness` | final checkpoint recorded after exact final docs Head CI | repository implementation/audit in closure validation; external evidence tracked separately |

## Phase 1–17 cumulative audit

The current cumulative source was re-audited after Phase 17 rather than relying only on historical phase badges. Full evidence is in `PHASE1_17_CROSS_PHASE_AUDIT.md`.

Reconciliations made during the final audit:

- roadmap module map now exactly matches `settings.gradle.kts`;
- bottom navigation remains exactly Home / Signals / AI / Tools / Activity;
- auth docs match debug/staging/production property namespaces;
- production read-only smoke uses Phase 3 freshness rules and remains GET-only;
- persisted Signal IDs are positive across Signals, notifications, deep links, AI and execution;
- deep-link shape is restricted to supported app routes;
- execution request/domain boundaries reject invalid Signal identity and request primitives without hidden retry;
- Room market cache independently enforces Phase 3/4 product scope and restores stale;
- Baseline Profile targets current `CoineProThemeKt`;
- Phase 16 versioning docs distinguish local syntax/range validation from Play-enforced cross-release monotonicity;
- staging app unit tests are an actual Android CI gate;
- `scripts/quality/check-cross-phase-consistency.py` permanently checks the key cross-phase invariants.

## Phase 17 repository status

Repository/client work delivered:

- launch and safety education;
- notification permission education and recovery;
- camera education with gallery/file fallback preserved;
- connection/provider-state education;
- trading/AI/provider risk disclosures without fabricated regulatory approval;
- safe system feedback path;
- analytics remains explicitly disabled pending a separate approved telemetry policy;
- incident and rollback runbook;
- protected production read-only smoke workflow and sanitized evidence output;
- Phase 1–17 reconciliation fixes and deterministic consistency gate.

Phase 17 repository closure is valid only when Android CI and Security CI are green on the exact final documentation Head.

## External launch evidence boundary

`PHASE17_EVIDENCE_LEDGER.md` is authoritative for facts that source code cannot prove: final legal approval, protected production configuration, provider/IP whitelist, real production market-data evidence, real provider execution lifecycle in an explicitly approved environment/account, real configured AI Vision production evidence, and production rollout state.

These facts are never inferred from Android CI, mocks, cached rows or client labels.

## Permanent branch rule

For any future maintenance/release branch:

1. start from an explicitly identified green cumulative Head;
2. keep product/provider/AI truth server-owned;
3. preserve positive persisted Signal identity and product scope;
4. run cumulative Android CI, Security CI and the cross-phase consistency gate;
5. record exact SHA/run evidence;
6. do not merge or enable live execution without explicit approval.
