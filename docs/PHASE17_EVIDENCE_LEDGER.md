# Phase 17 — Launch Readiness Evidence Ledger

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

Allowed states:
- `NOT_CONFIGURED`
- `CONFIGURED_NOT_VERIFIED`
- `VERIFIED`
- `BLOCKED`

Only explicit evidence can move a row to `VERIFIED`. Repository code, mocks, fixtures, cached data and client labels do not prove an external production runtime is healthy.

| Readiness item | State | Evidence / blocker |
| --- | --- | --- |
| Phase 16 final release-engineering baseline | `VERIFIED` | Final Head `5a1a02daf72acc60581665b3aee27dec713b400c`; Android #230 success; Security #62 success |
| Launch & safety user education | `CONFIGURED_NOT_VERIFIED` | `LaunchReadinessScreen` implemented; pending Phase 17 final UI/CI evidence |
| Notification permission education | `CONFIGURED_NOT_VERIFIED` | Startup auto-prompt removed; permission request moved behind explicit educated user action; pending final UI/CI evidence |
| Camera permission education / gallery fallback | `CONFIGURED_NOT_VERIFIED` | AI Vision requests camera only from the Camera action and keeps Gallery/File path available; final Phase 17 client audit pending |
| Connection setup education | `CONFIGURED_NOT_VERIFIED` | Connections screen separates user-supplied setup inputs from server/provider verified state; pending final UI/CI evidence |
| Trading / AI / provider risk disclosure UI | `CONFIGURED_NOT_VERIFIED` | Launch & safety screen contains concise risk and provider-truth disclosure; legal approval is a separate gate |
| Final legal copy approval | `NOT_CONFIGURED` | No product/legal approval evidence is present in this repository. Android must not claim regulatory or jurisdictional approval. |
| Support / feedback path | `CONFIGURED_NOT_VERIFIED` | Safe system share-sheet feedback path implemented with app version/environment metadata only; pending final UI/CI evidence |
| Analytics production enablement | `VERIFIED` | Explicitly disabled for Phase 17; repository search found no FirebaseAnalytics/logEvent implementation and no new analytics SDK/event was added |
| Incident / rollback runbook | `VERIFIED` | `docs/PHASE17_INCIDENT_RUNBOOK.md` documents outage, execution, crash/ANR, environment and rollback boundaries |
| Production vendor domains/configuration | `NOT_CONFIGURED` | Protected production deployment inputs are not visible/verified in repository evidence; no credential is committed |
| Production provider/IP whitelist | `NOT_CONFIGURED` | No external provider whitelist evidence available in repository/CI |
| Real production market-data smoke | `NOT_CONFIGURED` | No actual configured production response/source/timestamp smoke evidence available |
| Real broker/exchange execution lifecycle smoke | `BLOCKED` | Requires explicitly approved external environment/account and real provider evidence; no live-money order will be created merely to satisfy CI |
| Real configured AI Vision production smoke | `NOT_CONFIGURED` | Repository validates client contract but has no real production AI Vision job/result evidence yet |
| Play production rollout | `NOT_CONFIGURED` | Phase 16 introduced internal-track tooling only; production rollout is not enabled/claimed |

## Evidence rules

- Never paste service-account JSON, API keys, upload keystores, bearer/session tokens, broker passwords, exchange secrets or production signing material into this ledger.
- External evidence should record environment, provider/source identity, timestamp/result and an opaque safe reference where available.
- A failed or unavailable check remains `BLOCKED`/`NOT_CONFIGURED`; it is not converted to a pass because Android UI or a mock behaves correctly.
- Client implementation rows may move to `VERIFIED` only after the exact Phase 17 Head passes the relevant deterministic tests and cumulative CI.
- External runtime rows require actual configured production/provider evidence and are not satisfied by Android CI.

## Required external evidence to close Phase 17

Before Phase 17 may be marked Closed / Complete, the following currently unresolved evidence must exist:

1. product/legal approval for the release disclosure copy;
2. protected production vendor/domain configuration evidence;
3. provider/IP whitelist evidence where the provider requires it;
4. real production market-data source/freshness smoke;
5. real broker/exchange execution lifecycle smoke in an explicitly approved environment/account;
6. real configured AI Vision job/result smoke;
7. final Android CI and Security CI success on the exact final Phase 17 documentation Head.

Until those conditions are met, Phase 17 remains **In progress** even if all repository-contained client work is green.
