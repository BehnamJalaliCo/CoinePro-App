# Phase 17 — Launch Readiness Evidence Ledger

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

Allowed external/client evidence states:
- `NOT_CONFIGURED`
- `CONFIGURED_NOT_VERIFIED`
- `VERIFIED`
- `BLOCKED`

Only explicit evidence can move a row to `VERIFIED`. Repository code, mocks, fixtures, cached data and client labels do not prove an external production runtime is healthy.

## Repository/client evidence

| Readiness item | State | Evidence / blocker |
| --- | --- | --- |
| Phase 16 final release-engineering baseline | `VERIFIED` | Final Head `5a1a02daf72acc60581665b3aee27dec713b400c`; Android #230 success; Security #62 success |
| Launch & safety user education | `CONFIGURED_NOT_VERIFIED` | `LaunchReadinessScreen` implemented with deterministic accessibility coverage; exact final Phase 17 Head CI pending |
| Notification permission education | `CONFIGURED_NOT_VERIFIED` | Startup auto-prompt removed; permission request is behind explicit educated user action; settings recovery path exists; final Head CI pending |
| Camera permission education / gallery fallback | `CONFIGURED_NOT_VERIFIED` | Camera permission remains Camera-action scoped and Gallery/File fallback remains available; final Head CI pending |
| Connection setup education | `CONFIGURED_NOT_VERIFIED` | Connections UI distinguishes configured credentials from provider-confirmed state; final Head CI pending |
| Trading / AI / provider risk disclosure UI | `CONFIGURED_NOT_VERIFIED` | Launch & safety disclosure surface implemented; regulatory/legal approval is intentionally not inferred |
| Support / feedback path | `CONFIGURED_NOT_VERIFIED` | System share sheet contains safe app version/environment metadata only; final Head CI pending |
| Analytics production enablement decision | `VERIFIED` | Explicitly disabled/not added in Phase 17 rather than introducing an unreviewed telemetry/retention surface |
| Incident / rollback runbook | `VERIFIED` | `docs/PHASE17_INCIDENT_RUNBOOK.md` documents market/auth/notification/AI/execution/crash/environment response and rollback boundaries |
| Phase 1–17 cumulative reconciliation | `CONFIGURED_NOT_VERIFIED` | `docs/PHASE1_17_CROSS_PHASE_AUDIT.md` plus `scripts/quality/check-cross-phase-consistency.py`; exact final Head CI pending |
| Production read-only verification tooling | `CONFIGURED_NOT_VERIFIED` | Protected `production-readonly-smoke.yml` + GET-only sanitized script; final syntax/consistency CI pending |

## External production/legal evidence

| Readiness item | State | Evidence / blocker |
| --- | --- | --- |
| Final legal/product copy approval | `NOT_CONFIGURED` | No explicit legal/product sign-off artifact or approved reference is available through repository evidence. Android must not claim regulatory/jurisdictional approval. |
| Production vendor domains/configuration | `NOT_CONFIGURED` | Protected production values are intentionally not stored in repository source and no successful protected production run is available through the current connector evidence. |
| Production provider/IP whitelist | `NOT_CONFIGURED` | No external provider whitelist approval/reference is available in repository/CI evidence. |
| Real production market-data smoke | `NOT_CONFIGURED` | Protected GET-only workflow exists, but no successful configured production run/artifact is available through current evidence. |
| Real broker/exchange execution lifecycle smoke | `BLOCKED` | Requires an explicitly approved environment/account and provider evidence. No live-money order is created merely to satisfy CI and this repository has no safe write automation for such a test. |
| Real configured AI Vision production smoke | `NOT_CONFIGURED` | Read-only tooling can verify an existing approved job ID, but no real production job/reference is available through current evidence. |
| Play production rollout | `NOT_CONFIGURED` | Phase 16 provides internal-track tooling only; no production rollout decision/evidence is currently available. |

## Evidence rules

- Never paste service-account JSON, API keys, upload keystores, bearer/session tokens, broker passwords, exchange secrets or production signing material into this ledger.
- External evidence records environment/provider/source/timestamp/result plus an opaque safe reference where available.
- Failed or unavailable checks remain `BLOCKED`/`NOT_CONFIGURED`; client code cannot promote them.
- Repository/client rows move to `VERIFIED` only after the exact Phase 17 final documentation Head passes the required deterministic tests and cumulative CI.
- External runtime rows require actual configured production/provider evidence and are not satisfied by Android CI.
- The protected production smoke remains manual/protected so pull-request-controlled code is not given production bearer credentials.

## Required evidence for global Phase 17 closure

Global `Closed / Complete` requires all of the following:

1. exact-final-Head Android CI and Security CI success;
2. explicit final legal/product approval for the release disclosure copy;
3. protected production vendor/domain configuration evidence;
4. provider/IP whitelist evidence where required;
5. successful real production market-data source/freshness smoke;
6. real broker/exchange lifecycle evidence from an explicitly approved environment/account;
7. real configured AI Vision production evidence;
8. a recorded production rollout decision.

Until those external facts exist, the correct state is **repository/client complete once exact-Head CI is green; production launch authorization blocked by explicit external evidence rows**. This is not a code backlog and must not be hidden by changing the exit criteria.
