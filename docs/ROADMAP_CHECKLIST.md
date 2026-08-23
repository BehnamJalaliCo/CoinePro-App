# CoinePro Delivery Checklist

Use this beside `PRODUCT_ROADMAP.md`; canonical phase SHA/CI mapping lives in `PHASE_INDEX.md` and external launch evidence lives in `PHASE17_EVIDENCE_LEDGER.md`.

## Repository rule

- All CoinePro Android phase work is tracked in `BehnamJalaliCo/CoinePro-App` only.
- Phase PRs remain Draft and unmerged unless merge is explicitly approved.
- Repository/client completion and external production-launch authorization are separate evidence domains.

## Global repository gates

- [x] API contracts explicit
- [x] Loading / empty / error states where applicable
- [x] RTL layout and LTR financial values handled
- [x] Security/privacy/logging implications reviewed
- [x] Critical business-rule tests present
- [x] No fake realtime, execution, provider state or AI progress
- [x] Release transport/configuration isolation enforced
- [x] Phase 1–17 cross-phase consistency gate added to Android CI

## Phase repository delivery

- [x] Phase 0 — Foundation bootstrap
- [x] Phase 1 — Design System & Architecture Skeleton
- [x] Phase 2 — Authentication / Session / Entitlements
- [x] Phase 3 — Realtime Market Data Foundation
- [x] Phase 4 — Signals Core
- [x] Phase 5 — Alerts & Push
- [x] Phase 6 — Connections & Signal Execution Bridge
- [x] Phase 7 — AI Generated Market Signal
- [x] Phase 8 — AI Vision Flagship
- [x] Phase 9 — AI Assistant
- [x] Phase 10 — News & Economic Calendar
- [x] Phase 11 — Trader Tools
- [x] Phase 12 — Activity / History / Performance
- [x] Phase 13 — Offline / Reliability / Background Work
- [x] Phase 14 — Security Hardening
- [x] Phase 15 — Quality / Performance / Accessibility
- [x] Phase 16 — Release Engineering
- [x] Phase 17 — Launch Readiness repository/client implementation and Phase 1–17 reconciliation

## Validated historical milestones

- Phase 2: `12cc837ac02e378f3ca4452a95bfed224ad3222b` — Android #11 success
- Phase 3: `7158a78ef6ee378ec531576bf7d9364816d25b56` — Android #14 success
- Phase 4: `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` — Android #17 success
- Phase 5: `60dfd64259ec92775b38288f2a4dc8e4c50169e9` — Android #41 success
- Phase 6: `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` — Android #86 success
- Phase 7: `f718d9ad310ab37d4b109297c4fadcb33e287775` — Android #91 success
- Phase 8: `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` — Android #101 success
- Phase 9: `3d158c9d0fc72724e9bbf402ae81540300950cc3` — Android #114 success
- Phase 10: `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` — Android #121 success
- Phase 11: `11d91b2cb90a484611a1b1c773187b7c2b2795e4` — Android #126 success
- Phase 12 final: `23f7113d83acdcfda74798380f04da1c7447be9f` — Android #132 success
- Phase 13 final: `a6b664f035e047afd51515b3481452d57ecd1ee9` — Android #159 success
- Phase 14 final: `8abdb6909beb2468ec10c911ebd22ad8411a1b5f` — Android #184 + Security #16 success
- Phase 15 final: `a8d26b7df6332f569f963756a2e041ad31b3cdab` — Android #212 + Security #44 success
- Phase 16 final: `5a1a02daf72acc60581665b3aee27dec713b400c` — Android #230 + Security #62 success
- Phase 17 final exact-Head runs are recorded only after the final docs Head is green.

## Final Phase 1–17 reconciliation completed

- [x] Gradle module graph matches documented module map exactly
- [x] Bottom navigation is exactly Home / Signals / AI / Tools / Activity
- [x] Auth configuration docs match debug/staging/production namespaces
- [x] Market freshness policy is shared by Android and production read-only smoke
- [x] Forex scope remains `XAUUSD/XAGUSD`; Crypto remains valid `*USDT`
- [x] Persisted Signal identity is positive across Signals, notifications, deep links, AI and execution
- [x] Deep links reject unsupported scheme/path/IDs
- [x] Execution request/domain validation preserves one-action/one-write and provider-owned truth
- [x] Room market cache independently enforces product scope and restores stale
- [x] Background work remains read-only
- [x] Performance metrics remain evidence/denominator based with no inferred ROI/equity
- [x] Release HTTP remains HTTPS-only and release logging remains disabled
- [x] Baseline Profile targets current `CoineProThemeKt`
- [x] Phase 16 versioning wording matches actual local validation and Play authority
- [x] Staging coverage uses real `lintStaging + assembleStaging`; unit tests run on the supported debug variant and no nonexistent staging test task is claimed
- [x] Deterministic `check-cross-phase-consistency.py` gate protects future drift
- [x] Full audit recorded in `PHASE1_17_CROSS_PHASE_AUDIT.md`

## Phase 17 repository deliverables

- [x] Launch/safety education
- [x] Notification permission education and denial recovery
- [x] Camera education with gallery/file fallback preserved
- [x] Connection setup/provider-state education
- [x] Trading/AI/provider risk disclosure UI without regulatory overclaim
- [x] Safe support/feedback path
- [x] Analytics explicitly disabled instead of adding unreviewed telemetry
- [x] Incident/rollback runbook
- [x] GET-only sanitized production smoke workflow
- [x] Production smoke freshness checks aligned with Phase 3
- [x] External readiness evidence ledger

## External production-launch authorization

These are not repository checkboxes and cannot be truthfully completed from source code. Their current state is maintained only in `PHASE17_EVIDENCE_LEDGER.md`:

- final legal/product approval evidence;
- protected production vendor/domain configuration evidence;
- provider/IP whitelist evidence where required;
- real production market-data source/freshness evidence;
- real broker/exchange lifecycle evidence from an explicitly approved environment/account;
- real configured AI Vision production evidence;
- Play production rollout decision/evidence.

No source-code change, mock, cached row or Android CI run may silently convert those external facts into `VERIFIED`.
