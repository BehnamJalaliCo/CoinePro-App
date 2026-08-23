# Phase 17 — Launch Readiness Contract

Status: Active / In progress.

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

Start point: Phase 16 final green Head `5a1a02daf72acc60581665b3aee27dec713b400c`.

Phase 16 closure evidence:
- Android CI Run #230 — **success**
- Security CI Run #62 — **success**
- Phase 16 PR #12 remains Draft, open and unmerged.

## Purpose

Phase 17 is the final launch-readiness phase. It closes user education, legal/support/privacy/incident readiness and the production external-runtime evidence that earlier phases deliberately did not claim.

The Android client continues to treat server/provider state as authoritative. Phase 17 must not convert missing external evidence into a successful production-readiness claim.

## Workstreams

### 1. Onboarding and permission education

Required client behavior:
- explain the product flow before the user reaches high-consequence actions;
- explain camera access before AI Vision capture is requested;
- explain notification permission before the platform prompt where appropriate;
- keep permission denial/retry/recovery paths explicit;
- do not block gallery-only AI Vision use solely because camera permission is denied;
- preserve RTL layout and LTR financial values.

### 2. Connection setup education

Connection surfaces must explain:
- which connection/provider is being configured;
- which values are user/account inputs versus server/provider-derived state;
- that Android does not validate a live provider connection by guessing locally;
- that execution availability requires positive server/provider evidence;
- that no vendor/broker credential is committed to or logged by the Android repository.

### 3. Legal and risk disclosures

Before launch, the app must present concise, reviewable disclosures covering at minimum:
- trading and investment risk;
- AI output limitations;
- signals/analysis are not guaranteed outcomes;
- execution depends on external providers and market conditions;
- past or displayed historical results do not guarantee future performance;
- user responsibility for account/provider permissions and order confirmation.

Legal copy must be product-approved before it is represented as final legal language. Android must not invent regulatory approval or jurisdictional coverage.

### 4. Support and feedback path

A launch build must expose a clear support/feedback path without embedding privileged credentials or sensitive diagnostic payloads.

Support diagnostics must not include:
- bearer/session tokens;
- broker/exchange passwords or secrets;
- production signing material;
- AI Vision image bytes;
- raw sensitive prompts;
- hidden execution credentials.

### 5. Privacy-reviewed analytics

Phase 17 does not assume analytics consent or retention approval exists.

Any analytics event added in this phase must have:
- explicit event purpose;
- minimal fields;
- no secrets/tokens/credentials;
- no raw AI Vision image data;
- no raw sensitive AI prompt content;
- environment separation where applicable;
- documented privacy/retention ownership before production enablement.

If these conditions are not satisfied, analytics remains disabled/not added rather than being silently enabled.

### 6. Incident and rollback readiness

Launch readiness requires a documented operational response for at least:
- market-data outage or stale feed;
- authentication/session outage;
- notification degradation;
- AI service degradation;
- broker/exchange execution degradation;
- elevated crash/ANR rate;
- incorrect production environment configuration.

Rollback/disable controls must be defined for high-consequence capabilities. A client-only flag must not be described as a complete server-side kill switch unless the server actually enforces it.

### 7. Production vendor configuration

Production domains, credentials, provider IDs and IP whitelist configuration are external deployment inputs and must not be committed to the repository.

Readiness evidence must distinguish:
- configuration prepared;
- credentials available in the protected deployment environment;
- provider/IP whitelist configured;
- connectivity actually verified.

These are different states and must not be collapsed into one "ready" flag.

### 8. Production market-data smoke

A real production market-data smoke is successful only with evidence from the configured production path.

Minimum evidence:
- expected production environment selected;
- supported symbol scope verified (`XAUUSD`, `XAGUSD`, and supported `*USDT` crypto pairs as applicable);
- provider/source identity observed from actual server response;
- timestamp/freshness behavior verified;
- disconnect/stale behavior observed or otherwise validated;
- no client-created `LIVE` state.

A local mock, fixture, cached quote or non-production endpoint cannot satisfy this gate.

### 9. Broker/exchange execution lifecycle smoke

Execution readiness cannot be declared from UI state alone.

A real lifecycle gate must verify the configured provider path through explicit server/provider evidence for the required lifecycle, including as applicable:
- connection recognized;
- execution confirmation request;
- accepted/submitted provider state;
- open/active state;
- close/terminal state where provider support exists;
- explicit failure/rate-limit handling;
- idempotency / duplicate-write protection.

No live-money order should be placed merely to satisfy CI. Any external execution test must use an explicitly approved environment/account and must never be initiated by background work or hidden automatic retries.

### 10. AI Vision launch smoke

Launch readiness requires a real configured AI Vision path demonstrating:
- image input preprocessing still strips original metadata as designed;
- server job lifecycle is observed rather than locally fabricated;
- structured result validation is enforced;
- only validated actionable output with a persisted positive server `signal_id` may continue to Signal Detail;
- AI Vision itself does not execute an order.

A mocked AI response cannot satisfy the external-runtime smoke gate.

## Evidence ledger rule

Every external/runtime readiness item is one of:
- `NOT_CONFIGURED`
- `CONFIGURED_NOT_VERIFIED`
- `VERIFIED`
- `BLOCKED`

Only explicit evidence may move an item to `VERIFIED`.

Evidence records must identify the environment and test result without recording secrets. Failed or blocked checks remain visible and must not be rewritten as passes.

## CI and client gates

Phase 17 continues all Phase 16 gates:
- cumulative unit tests;
- debug/staging/release/benchmark lint/build gates;
- Compose accessibility tests;
- benchmark wiring smoke;
- protected release-signing smoke;
- tracked-secret scan;
- resolved dependency OSV audit;
- BuildConfig environment isolation.

Any new onboarding/disclosure/support UI must add appropriate deterministic tests for critical navigation, accessibility and truth-state behavior.

## Explicit non-claims at phase start

At the start of Phase 17, this repository does **not** claim:
- production market-data connectivity is verified;
- production broker/exchange execution is verified;
- production IP whitelisting is complete;
- production vendor credentials are installed;
- Play production rollout is enabled;
- final legal copy has been approved;
- analytics consent/retention has been approved;
- external AI Vision production smoke has passed.

Those claims require Phase 17 evidence.

## Exit criteria

Phase 17 is complete only when:
- onboarding/permission education is implemented and tested;
- connection setup education is implemented and tested;
- approved legal/risk disclosure content is wired into the launch build;
- support/feedback path is available;
- analytics is either privacy-approved with reviewed events or explicitly remains disabled;
- incident/runbook and rollback/disable responsibilities are documented;
- required production vendor configuration and whitelist states are evidenced without committing secrets;
- real production market-data smoke is verified;
- required broker/exchange execution lifecycle smoke is verified in an explicitly approved environment/account;
- real configured AI Vision path smoke is verified;
- final Android CI and Security CI are green on the exact final Phase 17 Head;
- the PR remains Draft/unmerged unless merge is explicitly approved.
