# Phase 17 — Launch Readiness Contract

Status: Repository/client implementation complete; external production/legal evidence and exact-final-Head validation remain evidence-gated.

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

Start point: Phase 16 final green Head `5a1a02daf72acc60581665b3aee27dec713b400c`.

Phase 16 closure evidence:
- Android CI Run #230 — **success**
- Security CI Run #62 — **success**
- Phase 16 PR #12 remains Draft, open and unmerged.

## Purpose

Phase 17 is the final launch-readiness phase. It closes repository/client education, support/privacy/incident readiness, reconciles the cumulative Phase 1–17 application, and provides protected evidence tooling for external production checks that earlier phases deliberately did not claim.

The Android client continues to treat server/provider state as authoritative. Phase 17 must not convert missing external evidence into a successful production-runtime claim.

## Repository/client workstreams

### 1. Onboarding and permission education

Implemented client behavior:
- launch/safety education explains the product and provider-truth boundary;
- camera permission is requested only from the explicit Camera action;
- gallery/file AI Vision use remains available without camera permission;
- notification permission is requested only after an educated user action;
- denied notification permission exposes an explicit settings recovery path;
- RTL layout and LTR financial conventions remain inherited from prior phases.

### 2. Connection setup education

Connection surfaces explain:
- which connection/provider is being configured;
- which values are user/account inputs versus server/provider-derived state;
- Android does not validate a live provider connection by guessing locally;
- execution availability requires positive server/provider evidence;
- vendor/broker credentials are not committed to or logged by the Android repository.

### 3. Legal and risk disclosure surface

The launch build presents concise reviewable disclosures covering:
- trading and investment risk;
- AI output limitations;
- signals/analysis are not guaranteed outcomes;
- execution depends on external providers and market conditions;
- historical/displayed results do not guarantee future performance;
- user responsibility for account/provider permissions and order confirmation.

This repository does not claim that the wording has regulatory/jurisdictional approval. Final legal/product approval is external evidence and must be recorded in `PHASE17_EVIDENCE_LEDGER.md` before production-launch authorization is claimed.

### 4. Support and feedback path

A system share-sheet feedback path is available. The prefilled diagnostic context is limited to app version and build environment and does not include:
- bearer/session tokens;
- broker/exchange passwords or secrets;
- production signing material;
- AI Vision image bytes;
- raw sensitive prompts;
- hidden execution credentials.

### 5. Privacy-reviewed analytics decision

No new production analytics SDK/event stream is enabled in Phase 17. Analytics remains explicitly disabled rather than silently introducing a consent/retention surface without approval.

Any future analytics enablement requires:
- explicit event purpose;
- minimal fields;
- no secrets/tokens/credentials;
- no raw AI Vision image data;
- no raw sensitive AI prompt content;
- environment separation where applicable;
- documented privacy/retention ownership before production enablement.

### 6. Incident and rollback readiness

`docs/PHASE17_INCIDENT_RUNBOOK.md` defines operational response for:
- market-data outage/stale feed;
- authentication/session outage;
- notification degradation;
- AI service degradation;
- broker/exchange execution degradation;
- elevated crash/ANR rate;
- incorrect production environment configuration.

The runbook distinguishes Android presentation controls from server/provider kill switches and never calls a client-only flag a complete execution kill switch.

### 7. Production evidence tooling

Production domains, credentials, provider IDs and IP whitelist configuration remain protected external deployment inputs and are not committed.

`.github/workflows/production-readonly-smoke.yml` plus `scripts/release/production-readonly-smoke.py` provide a protected, GET-only production verification path. The smoke:
- requires an HTTPS production API base URL and protected bearer token;
- validates supported symbol scope;
- requires positive prices, provider/source identity and source timestamp;
- applies the same freshness thresholds as Phase 3: LBank 15 s, Finnhub 90 s, unknown source 30 s;
- rejects implausibly future timestamps;
- reads execution connection/history state without creating/closing/retrying an order;
- may inspect an explicitly supplied existing AI Vision job ID read-only;
- emits only sanitized evidence and never writes tokens/credentials/raw AI content/account identifiers to the artifact.

No live-money order is created merely to satisfy CI.

## Final Phase 1–17 reconciliation

The current cumulative source was audited line-by-line across the major phase contracts. `docs/PHASE1_17_CROSS_PHASE_AUDIT.md` records the findings and fixes.

The final audit reconciled:
- roadmap module map ↔ actual Gradle modules;
- bottom navigation invariant;
- auth environment property names;
- market freshness thresholds ↔ production smoke;
- positive persisted Signal ID across Signals/notifications/deep links/AI/execution;
- deep-link scheme/path restrictions;
- execution request/domain validation without hidden retries;
- Room market-cache product-scope validation;
- Baseline Profile current theme class;
- Phase 16 local version validation ↔ Play monotonicity authority;
- staging app unit-test documentation ↔ actual CI.

`scripts/quality/check-cross-phase-consistency.py` is a permanent Android CI gate so these reconciled invariants cannot silently drift again.

## Production market-data evidence rule

A real production market-data smoke is successful only with evidence from the configured protected production path.

Minimum evidence:
- expected production environment selected;
- supported symbol scope verified;
- provider/source identity observed from actual server response;
- timestamp/freshness verified;
- no client-created `LIVE` state.

A local mock, fixture, cached quote or non-production endpoint cannot satisfy this external gate.

## Broker/exchange execution evidence rule

Execution readiness cannot be declared from UI state alone.

A real lifecycle gate must use an explicitly approved environment/account and explicit server/provider evidence for the required lifecycle, including as applicable:
- connection recognized;
- execution confirmation request;
- accepted/submitted provider state;
- open/active state;
- close/terminal state where provider support exists;
- explicit failure/rate-limit handling;
- idempotency / duplicate-write protection.

No live-money order is initiated merely to satisfy automation, and background work never performs trading writes.

## AI Vision external evidence rule

Production authorization requires real configured AI Vision evidence demonstrating:
- image preprocessing boundary remains intact;
- server job lifecycle is observed rather than locally fabricated;
- structured result validation is enforced;
- only validated actionable output with a positive persisted server `signal_id` may continue to Signal Detail;
- AI Vision itself does not execute an order.

A mocked response cannot satisfy that external-runtime evidence row.

## Evidence ledger states

Every external/runtime readiness item is one of:
- `NOT_CONFIGURED`
- `CONFIGURED_NOT_VERIFIED`
- `VERIFIED`
- `BLOCKED`

Only explicit evidence may move an item to `VERIFIED`. Failed or unavailable checks remain visible.

## CI and client gates

Phase 17 continues and strengthens all Phase 16 gates:
- cumulative core/feature unit tests;
- debug/staging/release lint;
- debug and staging app unit tests;
- debug/staging/release/benchmark assembly;
- Compose accessibility tests including launch-readiness UI coverage;
- benchmark wiring smoke;
- protected release-signing smoke;
- tracked-secret scan;
- resolved dependency OSV audit;
- BuildConfig environment isolation;
- deterministic Phase 1–17 repository consistency gate.

## Completion model

Two facts are recorded separately:

1. **Repository/client completion** — implementation, reconciliation, tests, docs and protected verification tooling are complete only when Android CI and Security CI pass on the exact final documentation Head.
2. **Production-launch authorization** — final legal approval plus required protected production/provider/runtime evidence must be `VERIFIED` in `PHASE17_EVIDENCE_LEDGER.md`.

This separation does not weaken the launch gate. It prevents a green client build from being misrepresented as proof that an external provider/account is live.

## Final Phase 17 exit criteria

Phase 17 may be marked globally `Closed / Complete` only when:
- repository/client completion is exact-Head green;
- final legal/product approval evidence is recorded;
- required protected production vendor/domain configuration evidence is recorded;
- provider/IP whitelist evidence is recorded where required;
- real production market-data smoke is `VERIFIED`;
- required broker/exchange execution lifecycle evidence is `VERIFIED` in an explicitly approved environment/account;
- real configured AI Vision production evidence is `VERIFIED`;
- production rollout decision is recorded;
- PR #13 remains Draft/unmerged unless merge is explicitly approved.
