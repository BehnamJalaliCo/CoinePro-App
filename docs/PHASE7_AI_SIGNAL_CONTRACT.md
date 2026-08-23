# Phase 7 — AI Generated Market Signal Contract

This contract belongs to `BehnamJalaliCo/CoinePro-App` and defines the Android-facing behavior for Phase 7.

## Product boundary

AI Signal is a signal-generation workflow, not a free-form chat response and not a manual trading terminal.

Android may request a signal only for the current CoinePro product scope:

- Forex V1: `XAUUSD`, `XAGUSD`
- Crypto: LBank-style `*USDT` symbols; the Phase 7 UI exposes the curated market list already used by the app
- Timeframes: `M15`, `H1`, `H4`, `D1`
- Risk: `low`, `medium`, `high`

Raw model text is not part of the Android contract.

## Endpoints

All endpoints are authenticated through the existing bearer session.

### GET `user/signals/ai/quota`

Response:

```json
{
  "quota": {
    "remaining": 4,
    "limit": 5,
    "reset_at": "2026-08-24T00:00:00Z"
  }
}
```

### POST `user/signals/ai/jobs`

Request:

```json
{
  "symbol": "XAUUSD",
  "timeframe": "H1",
  "risk": "medium"
}
```

Response contains a server job plus optional updated quota.

### GET `user/signals/ai/jobs/{job_id}`

Returns the latest server-truth job state.

## Job states

Only these states are accepted:

- `queued`
- `running`
- `done`
- `failed`
- `expired`

Android never creates a local success state and never displays a fake completion percentage. While a job is `queued` or `running`, Android polls the job endpoint and renders only the last state returned by the server.

## Structured result

A `done` job is actionable only when the response contains a structured result with all of the following:

- `validated: true`
- positive persisted `signal_id`
- product-scope `symbol` matching the original request
- `timeframe` matching the original request
- direction exactly `BUY` or `SELL`
- finite positive `entry`
- finite positive `stop_loss`
- at least one valid TP target
- confidence between 0 and 100
- optional entry zone with valid ordered bounds
- optional positive finite R:R

Example:

```json
{
  "job": {
    "id": "job-123",
    "status": "done",
    "request": {
      "symbol": "XAUUSD",
      "timeframe": "H1",
      "risk": "medium"
    },
    "result": {
      "validated": true,
      "signal_id": 42,
      "symbol": "XAUUSD",
      "direction": "BUY",
      "timeframe": "H1",
      "entry": 2500.0,
      "entry_zone": {"low": 2498.0, "high": 2502.0},
      "stop_loss": 2485.0,
      "targets": [
        {"level": 1, "price": 2520.0},
        {"level": 2, "price": 2540.0}
      ],
      "confidence": 82,
      "risk_reward_tp1": 1.5,
      "rationale": "Structured server-validated rationale",
      "validated_at": "2026-08-23T07:00:00Z"
    }
  },
  "quota": {
    "remaining": 3,
    "limit": 5,
    "reset_at": "2026-08-24T00:00:00Z"
  }
}
```

If `validated` is false, any required field is invalid, the symbol/timeframe differs from the request, or the job reports `done` without a valid structured result, Android blocks the result and exposes no Signal CTA.

## Execution boundary

The AI screen never calls the execution API.

A valid AI result may only navigate to the persisted standard `signal_id`. Any later execution happens through the existing Signal Detail → Execution flow, where the server-owned signal remains the source of truth.

This prevents unvalidated model output from becoming an executable order.

## Error semantics

- `403`: entitlement required
- `410`: job expired
- `422`: request rejected by server validation
- `429`: quota exhausted
- other HTTP/network errors: request/status unavailable; Android does not infer completion

A failed or expired job remains recoverable: the user may retry the same validated request controls or change the request and create a new job.

## Phase 7 client exit criteria

- request controls stay inside product scope
- entitlement and quota are server-derived
- pending/done/failed/expired states come from server truth
- no fake progress
- unvalidated structured output cannot open a Signal
- raw model text cannot be executed
- generated validated result uses the same Signal language as the rest of CoinePro
- critical mapping/state rules are covered by unit tests
- Android CI is green
