# CoinePro Delivery Checklist

Use this beside `PRODUCT_ROADMAP.md`; canonical phase SHA/CI mapping lives in `PHASE_INDEX.md`.

## Repository rule

- All CoinePro phase work is tracked in `BehnamJalaliCo/CoinePro-App` only.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Completed phase checkpoints are recorded in `PHASE_INDEX.md`.
- Production vendor/broker credentials, external connectivity, whitelist checks and live end-to-end smoke tests remain launch-readiness work unless explicitly moved earlier.

## Global gates for every phase

- [x] API contract explicit
- [x] Loading / empty / error states where applicable
- [x] RTL layout and LTR financial values handled
- [x] Security/privacy/logging implications reviewed
- [x] Critical business-rule tests added
- [x] Android CI green
- [x] No fake realtime, execution or AI progress

## Phase progress

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
- [ ] Phase 16 — Release Engineering
- [ ] Phase 17 — Launch Readiness

## Validated milestones

- Phase 2: `feat/android-mobile-auth` → `12cc837ac02e378f3ca4452a95bfed224ad3222b` → Run #11 success
- Phase 3: `feat/phase3-realtime-market-data` → `7158a78ef6ee378ec531576bf7d9364816d25b56` → Run #14 success
- Phase 4: `feat/phase4-signals-core` → `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` → Run #17 success
- Phase 5: `feat/phase5-alerts-push` → `60dfd64259ec92775b38288f2a4dc8e4c50169e9` → Run #41 success
- Phase 6 audited: `feat/phase6-signal-execution` → `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` → Run #86 success
- Phase 7: `feat/phase7-ai-generated-market-signal` → `f718d9ad310ab37d4b109297c4fadcb33e287775` → Run #91 success
- Phase 8 audited: `feat/phase8-ai-vision` → `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` → Run #101 success
- Phase 9 audited: `feat/phase9-ai-assistant` → `3d158c9d0fc72724e9bbf402ae81540300950cc3` → Run #114 success
- Phase 10: `feat/phase10-news-economic-calendar` → `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` → Run #121 success
- Phase 11: `feat/phase11-trader-tools` → `11d91b2cb90a484611a1b1c773187b7c2b2795e4` → Run #126 success
- Phase 12 final closure: `feat/phase12-activity-history-performance` → `23f7113d83acdcfda74798380f04da1c7447be9f` → Run #132 success
- Phase 13 final closure: `feat/phase13-offline-reliability-background-work` → `a6b664f035e047afd51515b3481452d57ecd1ee9` → Run #159 success
- Phase 14 final closure: `feat/phase14-security-hardening` → `8abdb6909beb2468ec10c911ebd22ad8411a1b5f` → Android Run #184 success + Security Run #16 success
- Phase 15 code checkpoint: `feat/phase15-quality-performance-accessibility` → `97d3ebc0165be27e86ad97dceef16494f7a7b428` → Android Run #208 success + Security Run #40 success

## Phase 12 delivered

- premium Activity / Performance dashboard based on server evidence
- paginated closed-signal history for Forex and Crypto
- explicit coverage count and incomplete-history state
- market / instrument / result filters over loaded history
- total loaded signals and explicit Win / Loss / Breakeven / Missing result states
- Win rate with finite explicit P&L denominator
- TP hit rate with explicit nullable target-hit evidence; omitted hit status stays missing
- SL rate with explicit close-reason denominator
- average planned R:R with finite positive server-provided evidence
- zero, missing denominator, no records and no filter matches are distinct states
- losses receive equal metric prominence
- complete server-reported execution ledger shown separately from signal performance
- no ROI/equity, broker P&L or execution outcome inference
- LTR financial rendering inside RTL layouts
- existing alerts, push preferences and notifications preserved
- no performance action can send an order
- `docs/PHASE12_ACTIVITY_HISTORY_PERFORMANCE_CONTRACT.md` documents evidence, denominators, coverage and truth boundaries
- Phase 12 unit tests run through cumulative `:core:signals:testDebugUnitTest`
- final Phase 12 documentation Head passed Android CI Run #132

## Phase 13 delivered

- `core:database` Room boundary for safe read caches
- market snapshot cache with product-scope and finite-number validation
- every market quote restored from disk remains explicit stale/cache evidence and cannot claim LIVE
- closed-signal history cache intentionally excludes live quote/live P&L authority
- nullable target-hit evidence survives cache round-trips without becoming a fake miss
- cache-aware Signal History fallback with explicit cache provenance and refresh errors
- successful server refresh replaces cached history
- membership loss/logout clears account-scoped cached history
- app-resume synchronization refreshes server-backed read state
- WorkManager durable read sync with network constraints, battery-not-low periodic work and exponential backoff
- background worker has no execution/broker write dependency and cannot submit/close/retry a trade
- unique work scheduling makes repeated background read scheduling idempotent
- process-death worker session hydration uses existing secure token storage only for authenticated reads
- HTTP 401 expires the session instead of retrying with stale credentials
- on-demand WorkManager/Hilt initialization is configured without suppressing lint
- `docs/PHASE13_OFFLINE_RELIABILITY_BACKGROUND_CONTRACT.md` documents storage, stale, retry, session and side-effect boundaries
- Phase 13 database/signals/background tests are included in cumulative Android CI
- Run #155 passed tests, lint, app tests, debug build and APK upload at the code checkpoint

## Phase 14 delivered

- release HTTP logging disabled; debug logging is explicit BASIC only with sensitive headers redacted
- cleartext disabled in manifest/network policy and Retrofit remains HTTPS-only
- system-CA certificate strategy is explicit; certificate pinning is deferred until stable production domains plus primary/backup pins and rotation ownership exist
- tracked-secret CI blocks common credential signatures, private keys, keystores and local secret/config files
- actual resolved debug/release runtime dependencies are audited against OSV; no dependency-graph feature assumption is required
- security vulnerability exception ledger is explicit and starts empty
- debug and release service configuration use separate Gradle property namespaces
- generated debug/release BuildConfig isolation is verified by Security CI with distinct markers
- release is explicitly non-debuggable and Android CI lints/assembles both debug and release
- execution HTTP 429 becomes an explicit rate-limit error; one user action makes one trading gateway call and no automatic write retry
- existing auth, AI Signal, AI Vision and AI Assistant rate-limit/quota states remain explicit
- execution and AI Vision upload threat models reviewed
- AI Vision re-encoding strips original EXIF metadata and app-owned camera cache captures are deleted in a finally path
- root/debug/tamper policy rejects fake client-only trust claims; future integrity signals must be server-evaluated risk inputs rather than local execution truth
- local privacy/retention rules for session, Room cache, AI Assistant and AI Vision are documented
- `docs/PHASE14_SECURITY_HARDENING_CONTRACT.md` is the security contract
- final Phase 14 Head `8abdb6909beb2468ec10c911ebd22ad8411a1b5f` passed Android CI Run #184 and Security CI Run #16

## Phase 15 delivered

- cumulative prior domain/controller/business-rule tests remain authoritative regression gates
- four Compose UI tests cover cached stale truth in RTL, explicit network LIVE semantics, offline Retry behavior and 2× font-scale RTL quote reachability
- Home content is scrollable so large text keeps critical financial information reachable
- TalkBack quote semantics include instrument, symbol, stale/live state, price, source and market type
- LTR financial values remain directionally isolated inside RTL at large font scale
- reduced-motion CI gate rejects infinite/continuous Compose animation primitives in app/core/feature source
- emulator accessibility tests run with Android system animations disabled
- deterministic semantic/state assertions are the hard signature-state golden gate; hosted-emulator pixel rendering is not misrepresented as deterministic
- ProfileInstaller and a checked-in Baseline Profile seed are present
- dedicated benchmark module includes Baseline Profile generation and cold-start Macrobenchmark tooling
- benchmark target app is release-like and non-debuggable; signing is only for installability of benchmark artifacts
- benchmark instrumentation APK is explicitly signed for test installation
- hosted CI runs benchmark dry-run smoke without converting emulator timings into performance claims
- reference-device cold-start and jank target budget is documented, while measured pass/fail remains a physical-device release-candidate responsibility
- benchmark/profile dependencies continue through Security CI OSV/secret/build-config checks
- `docs/PHASE15_QUALITY_PERFORMANCE_ACCESSIBILITY_CONTRACT.md` documents the full quality, accessibility, reduced-motion and performance evidence boundaries
- Phase 15 code checkpoint `97d3ebc0165be27e86ad97dceef16494f7a7b428` passed Android CI #208 and Security CI #40

## Current next milestone

Phase 16 — Release Engineering, after the final Phase 15 documentation Head is green.

There are 2 phases remaining: Phase 16 and Phase 17.
