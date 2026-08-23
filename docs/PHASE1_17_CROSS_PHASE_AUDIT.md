# Phase 1–17 Cross-Phase Audit

Repository: `BehnamJalaliCo/CoinePro-App`

Audit branch: `feat/phase17-launch-readiness`

Purpose: verify that the cumulative Android application still obeys every material product/truth/security contract introduced from Phase 1 through Phase 17, and remediate incompatible assumptions instead of merely checking historical CI badges.

## Audit method

The audit cross-checked:

- current Gradle module graph against the roadmap module map;
- primary navigation and sub-routes;
- auth/session/environment configuration contracts;
- market-data source/product/freshness truth;
- Signal identity/product/lifecycle boundaries;
- notification/deep-link Signal routing;
- execution write/idempotency/provider-state boundaries;
- AI Signal, AI Vision and AI Assistant trust boundaries;
- news/calendar and Trader Tools isolation;
- Activity/performance evidence denominators;
- Room cache and background-work trust boundaries;
- security/build-environment isolation;
- accessibility/reduced-motion/Baseline Profile wiring;
- release/version/signing claims;
- Phase 17 education, support, runbook and production-evidence tooling.

Historical phase CI remains evidence that each phase worked at its checkpoint, but this audit uses the **current cumulative source** as authoritative for compatibility.

## Phase-by-phase result

| Phase | Current cumulative check | Reconciliation result | Repository status |
| --- | --- | --- | --- |
| 1 — Design System & Architecture | Gradle module graph, five bottom destinations, RTL/LTR conventions | Removed phantom roadmap modules, added missing `benchmark` and `core:navigation` entries; roadmap now mirrors `settings.gradle.kts` | Matched |
| 2 — Auth / Session / Entitlements | `/user/me` server truth, encrypted token storage, 401 clearing, build environment properties | AUTH contract corrected from obsolete shared property to debug/staging/production namespaces | Matched |
| 3 — Realtime Market Data | WSS/HTTP fallback, source identity, product scope, stale thresholds | Production smoke uses the same 15 s LBank / 90 s Finnhub / 30 s unknown thresholds plus future-skew rejection | Matched |
| 4 — Signals Core | Forex `XAUUSD/XAGUSD`, Crypto `*USDT`, typed BUY/SELL and persisted identity | Core Signal mapper rejects null/zero/negative IDs so persisted Signal identity matches downstream AI/execution rules | Matched |
| 5 — Alerts & Push | alert symbol validation, FCM, Notification Center, deep links | FCM, Notification Center and app deep links accept Signal navigation only for positive persisted IDs; scheme/path shape is constrained | Matched |
| 6 — Execution Bridge | signal-scoped writes, quantity validation, idempotency, provider-owned status | Execution responses/snapshots reject non-positive Signal IDs; request boundary validates positive Signal ID, finite quantity and nonblank idempotency ID without hidden retries | Matched |
| 7 — AI Signal | server job states, quota, strict structured result, no direct execution | Positive persisted Signal result remains mandatory and aligns with Phase 4/5/6 identity rules | Matched |
| 8 — AI Vision | image preprocessing, EXIF stripping, structured server job/result, no direct execution | Actionability remains gated by validated structured output and positive persisted Signal ID; camera fallback remains independent | Matched |
| 9 — AI Assistant | contextual provenance, stable conversation identity, memory-only local transcript | Active-Signal context requires a positive ID; non-signal context cannot smuggle Signal identity; no direct execution route | Matched |
| 10 — Market Intelligence | ISO timestamps, stale/unknown truth, HTTPS links, high-impact risk context | Current source retains explicit source/stale/impact semantics and remains advisory-only | Matched |
| 11 — Trader Tools | deterministic local formulas, finite validation, LTR financial output | Calculators remain isolated from execution and do not infer broker specifications | Matched |
| 12 — Activity / History / Performance | explicit P&L/TP/SL denominators, incomplete coverage, execution ledger separation | Missing evidence remains missing; no ROI/equity/broker outcome inference introduced by later phases | Matched |
| 13 — Offline / Reliability | stale read cache, closed-history cache, WorkManager read-only sync | Market Room mapper independently rejects product-scope mismatches on both write and restore; background work remains read-only | Matched |
| 14 — Security Hardening | HTTPS-only, secret scan, OSV, BuildConfig isolation, release logging policy, no write retry | Later staging/production namespaces remain isolated; no credential-bearing logging/write retry regression found | Matched |
| 15 — Quality / Performance / Accessibility | Compose accessibility, reduced-motion, Baseline Profile, benchmark wiring | Baseline Profile fixed from removed `ThemeKt` to current `CoineProThemeKt`; no hosted-emulator performance overclaim introduced | Matched |
| 16 — Release Engineering | signing, staging identity, version validation, internal track | Local script validates semver + positive/range-safe `versionCode`; Play enforces cross-release monotonicity. Staging is covered by real `lintStaging` + `assembleStaging`; supported unit tests run on the debug test variant | Matched |
| 17 — Launch Readiness | education, permission recovery, support, analytics decision, runbook, production evidence tooling | Client/readiness tooling implemented; production read-only smoke is GET-only and freshness-aware. External legal/provider/runtime evidence remains separately evidence-gated | Repository matched; external evidence ledger authoritative |

## Reconciliations applied by this audit

1. **Module-map drift fixed** — `PRODUCT_ROADMAP.md` now contains exactly the modules included by `settings.gradle.kts`; nonexistent `core:testing` and `feature:profile` entries were removed.
2. **Auth property drift fixed** — documentation now names `COINEPRO_DEBUG_API_BASE_URL`, `COINEPRO_STAGING_API_BASE_URL` and `COINEPRO_PRODUCTION_API_BASE_URL` instead of the obsolete shared property.
3. **Production market smoke hardened** — source timestamp must be fresh under the same thresholds used by Android; snake_case/camelCase transport compatibility is handled without guessing freshness.
4. **Persisted Signal identity unified** — Signals, notifications, deep links, AI context/result and execution all require a positive persisted Signal ID before navigation/actionability.
5. **Deep-link surface narrowed** — only the app scheme and exact supported path shapes become launch actions.
6. **Execution mapper/request boundary hardened** — invalid Signal IDs and invalid request primitives cannot be promoted into execution state or sent as a write.
7. **Market cache trust boundary fixed** — Room market rows are product-scoped independently of network mappers and always restore stale.
8. **Baseline Profile drift fixed** — current design-system theme class is targeted.
9. **Release-version documentation corrected** — repository validation no longer overclaims knowledge of Play release history; Play monotonicity remains platform-enforced.
10. **Staging validation corrected** — the AGP configuration has no `:app:testStagingUnitTest`; CI therefore uses supported `:app:lintStaging` + `:app:assembleStaging` and keeps unit-test coverage on the supported debug variant. No nonexistent task is claimed or required.
11. **Permanent reconciliation gate added** — `scripts/quality/check-cross-phase-consistency.py` rejects future drift in module map, bottom navigation, auth env naming, Signal identity, freshness policy, cache scope, Baseline Profile and read-only production smoke.

## Non-regression truth invariants after reconciliation

- `Home / Signals / AI / Tools / Activity` remains the only bottom navigation set.
- Forex V1 remains `XAUUSD/XAGUSD`; Crypto remains valid `*USDT`.
- Cached data never proves realtime/live state.
- Missing/stale/error states remain explicit.
- Android never invents broker/provider state or execution outcome.
- One explicit execution action does not gain an automatic trading-write retry.
- AI free text cannot become an executable order.
- AI Signal/Vision actionability requires validated structured output and a positive persisted server Signal.
- AI Assistant has no direct execution route.
- Trader Tools are deterministic local math only.
- Performance metrics use explicit evidence/denominators and never invent ROI/equity.
- Background work remains read-only.
- Release transport remains HTTPS-only and release logging remains disabled.
- Production secrets/signing/provider credentials are not committed.
- Financial output remains explicitly LTR where required inside RTL UI.

## Deterministic CI gate

Android CI runs:

`python3 scripts/quality/check-cross-phase-consistency.py`

The gate deliberately validates repository-owned facts only. It must not convert external legal/provider/production absence into a repository pass.

## External Phase 17 boundary

`docs/PHASE17_EVIDENCE_LEDGER.md` remains authoritative for external launch authorization. Legal approval, protected production configuration, provider/IP whitelist, real production market source evidence, real approved broker/exchange lifecycle evidence and real configured AI Vision production evidence cannot be synthesized from Android source, mocks or historical unit tests.

A repository/client closure and a production-launch authorization are therefore recorded as separate facts. This separation is intentional and prevents a green Android build from being misrepresented as proof of a live external provider.
