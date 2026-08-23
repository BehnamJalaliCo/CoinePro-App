# Phase 17 — Incident & Rollback Runbook

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

This runbook defines the Android-side response boundary for launch incidents. It does not pretend that a client-only flag is a complete production kill switch. Server/provider controls remain authoritative for authentication, entitlements, market-data truth and execution availability.

## Incident principles

1. Protect user funds and provider credentials before preserving feature availability.
2. Never fabricate `LIVE`, connected, executed, closed, profitable or AI-complete state during an outage.
3. Prefer explicit unavailable/stale/error states over fallback that changes financial truth.
4. Disable high-consequence actions at the authoritative server/provider boundary when integrity is uncertain.
5. Preserve read-only recovery paths where they cannot create execution side effects.
6. Do not place, close or retry orders from background work.
7. Incident evidence must exclude bearer tokens, broker credentials, upload/signing keys, AI Vision image bytes and sensitive prompts.

## Severity

- **SEV-1** — possible incorrect execution, credential exposure, cross-environment routing, or widespread inability to determine provider truth.
- **SEV-2** — major authenticated feature outage, widespread stale market data, AI outage, push degradation with material user impact, or elevated crash/ANR rate.
- **SEV-3** — contained degradation with a safe workaround and no evidence of financial-state corruption.

## Market-data outage / stale feed

Trigger examples:
- production source unavailable;
- timestamps stop advancing;
- server marks feed stale;
- provider/source identity differs from expected production configuration.

Android response:
- keep stale/cache provenance visible;
- never upgrade cached or failed data to `LIVE`;
- keep source/as-of evidence visible where available;
- execution surfaces must not infer a current market price from stale/cache data.

Authoritative mitigation:
- disable/route production market-data source server-side if integrity is uncertain;
- restore only after source identity, timestamps and freshness are verified.

Rollback evidence:
- affected environment;
- first/last observed good timestamp;
- source identity;
- restore verification result.

## Authentication / session outage

Android response:
- preserve explicit signed-out/error state;
- HTTP 401 expires local session instead of retrying indefinitely;
- do not retry authenticated trading writes with stale credentials.

Authoritative mitigation:
- disable affected authentication path server-side if token/session integrity is uncertain;
- rotate compromised credentials outside the repository if required.

## Notification degradation

Android response:
- notification permission remains optional;
- the app must remain usable without notification permission;
- notification delivery must never be treated as proof that a signal/execution state exists.

Mitigation:
- diagnose FCM/server delivery separately from in-app source-of-truth state;
- do not create local fake notifications to mask a backend outage.

## AI service degradation

Android response:
- keep exact server `QUEUED / RUNNING / DONE / FAILED / EXPIRED` state;
- no local progress percentage or fabricated completion;
- raw model text remains non-executable;
- AI Vision can open a Signal only from validated structured result plus persisted positive server `signal_id`.

Authoritative mitigation:
- disable AI job creation server-side if structured-result integrity is uncertain;
- preserve already persisted verified Signals independently.

## Broker / exchange execution degradation

Treat any ambiguity about write acceptance or provider state as high consequence.

Android response:
- one explicit user action maps to one gateway write;
- no hidden automatic execution retry;
- rate limit remains explicit;
- duplicate-close/idempotency protections remain active;
- missing provider confirmation must not become accepted/open/closed locally.

Authoritative mitigation:
- disable new execution server-side for the affected venue/account class;
- preserve read-only execution/history retrieval when safe;
- reconcile provider state before re-enabling writes.

Re-enable evidence must include:
- configured provider/environment;
- connection recognition;
- accepted/submitted state evidence;
- terminal/close behavior where supported;
- duplicate-write/idempotency verification.

## Elevated crash / ANR rate

Baseline source: Android Vitals / Play Console for Play-distributed builds.

Response:
- identify affected version/build environment;
- stop rollout or promote rollback through Play controls as appropriate;
- do not add emergency telemetry that captures secrets or financial credentials;
- preserve minified mapping evidence for protected releases.

Release rollback options:
- halt further rollout / keep current production version;
- publish a corrective higher-version build after validation;
- server-side disable high-consequence capability when client rollback latency is unsafe.

Android cannot remotely downgrade an already installed app by itself.

## Incorrect production environment configuration

Examples:
- staging endpoint in production build;
- production endpoint in staging build;
- wrong provider project/account configuration.

Response:
- treat as SEV-1 if execution or credentials may cross environments;
- stop rollout;
- disable affected server-side writes;
- rotate credentials if exposure is possible;
- use BuildConfig isolation evidence and protected release configuration to identify the source.

No client-side environment label is sufficient evidence that server routing is safe.

## Rollback / disable matrix

| Capability | Android behavior | Authoritative disable/rollback owner |
| --- | --- | --- |
| Market data | Show stale/error; no fake LIVE | Server/provider routing |
| New execution | UI remains truth-bound; no hidden retries | Server execution gate / provider access |
| Close execution | Preserve provider-truth lifecycle | Server/provider close gate |
| AI Signal | Explicit failed/unavailable state | Server AI job creation gate |
| AI Vision | Explicit failed/unavailable state | Server AI Vision job gate |
| Notifications | App remains usable without delivery | Push/backend configuration |
| App release | Build remains versioned/signed | Play rollout / corrective release |

## Recovery checklist

Before declaring an incident recovered:
- root cause or bounded failure mode is understood;
- secrets were not written to logs/issues/artifacts;
- affected external source/provider is verified, not inferred;
- Android CI and Security CI remain green for any corrective code change;
- high-consequence server/provider gates are intentionally re-enabled;
- stale/error states recover only after fresh authoritative evidence;
- release/version and rollback actions are recorded without confidential material.
