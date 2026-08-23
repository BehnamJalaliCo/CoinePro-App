# Phase 13 — Offline, Reliability & Background Work Contract

## Scope

Phase 13 adds durable read caching and background synchronization without changing the server-truth boundary for realtime market data, signals, execution, AI jobs, broker state or account performance.

Repository scope is `BehnamJalaliCo/CoinePro-App` only.

## Storage boundary

`core:database` uses Room 2.8.4 for safe read models only.

Persisted models:
- market snapshots for the supported product scope
- CLOSED signal history used by Activity / Performance

Not persisted as authoritative truth:
- execution lifecycle
- broker/exchange outcome
- active order state
- AI job progress/result lifecycle
- account ROI/equity/return
- raw vendor/broker credentials

Closed-signal cache intentionally does not restore live quote or live P&L fields. Target-hit evidence remains nullable so missing evidence cannot become a confirmed miss.

## Market cache truth

A market quote restored from Room is always treated as cached/stale evidence:
- origin is `CACHE`
- restored quotes remain `isStale=true`
- cache alone can never produce `LIVE`
- only accepted HTTP/WebSocket network data can switch origin to `NETWORK`
- source timestamps and product-scope validation remain mandatory

Realtime network snapshots are persisted at a throttled cadence so a quote stream does not continuously write Room.

## Signal-history cache truth

Signal history may restore cached CLOSED records before a network refresh completes.

State exposes cached provenance and cache storage time. A successful server refresh replaces the cache and clears cached provenance. If refresh fails, cached rows may remain visible with an explicit refresh error; they are not described as refreshed server data.

Membership loss clears account-scoped signal-history cache.

## App-resume synchronization

When an authenticated user returns to the foreground, Android requests fresh server-backed state for the relevant read surfaces, including market data, signals/history, execution history, notifications and market intelligence. Resume sync never fabricates success if a source is unavailable.

## WorkManager boundary

WorkManager 2.11.2 is used for durable **read synchronization only** through `BackgroundReadSyncWorker`.

The background engine may call only:
- market snapshot read gateway
- closed signal-history read gateway
- safe Room cache writes resulting from those reads

The worker has no execution gateway dependency and must not:
- execute a signal
- request a close
- submit or retry broker/exchange writes
- create AI signals/jobs
- upload AI Vision images
- mutate alerts or connection credentials

This prevents WorkManager retry semantics from turning a transient background failure into a duplicate trading side effect.

## Scheduling and retry

Authenticated sessions schedule:
- unique periodic read sync every 6 hours
- network-connected constraint
- battery-not-low constraint for periodic work
- unique immediate read sync on resume/session enable
- exponential backoff beginning at 30 seconds for retryable read failures

Unique work names make repeated scheduling idempotent at the Android scheduler boundary. Periodic work uses update semantics; immediate work replaces an older pending immediate read sync.

`NO_SESSION` is a successful no-op, not a retry loop.

## Session and process-death behavior

If WorkManager runs after process death and in-memory session state is empty, the engine may read the existing secure `SessionTokenStorage` value and hydrate `SessionMemory` only for the duration needed to perform authenticated reads.

The worker does not create another token store, place bearer tokens in URLs, log bearer values, or persist a second plaintext credential copy.

If the worker hydrated memory temporarily, it removes that in-memory token after the iteration unless the session changed independently.

HTTP 401:
- clears secure session storage
- clears in-memory token
- emits the existing unauthorized/session-expired signal
- stops the current background sync as `NO_SESSION`

## Failure semantics

- Network/read failures are retryable background failures.
- Missing session is a no-op success.
- Signal membership loss clears only the account-scoped signal-history cache and is not converted into fake history.
- Cached data never proves execution, broker outcome, AI completion or realtime freshness.
- Android does not infer provider/server state from local retry count.

## WorkManager initialization

`CoineProApplication` supplies the Hilt worker factory through `Configuration.Provider`. The manifest removes only `androidx.work.WorkManagerInitializer` from the AndroidX Startup provider so WorkManager uses the application-provided configuration while unrelated AndroidX Startup initializers remain available.

## Tests and CI

Phase 13 coverage includes:
- cached market restore stays stale
- invalid/non-finite/product-out-of-scope cache records are rejected
- closed-signal cache does not restore live evidence
- signal-history network failure keeps cached provenance explicit
- successful history refresh replaces cache
- membership loss clears history cache
- process-death token hydration for background reads
- no-session background no-op
- retryable read failure outcome

Cumulative Android CI includes `:core:database:testDebugUnitTest`, prior core tests, app unit tests, lint and debug assembly.

Code checkpoint:
- SHA `fd8d56be5023b03ae136a5af633addaf3edee3a7`
- Android CI Run #155 — success
- APK upload — success

The final documentation Head must pass the same cumulative Android CI gate before Phase 13 is considered closed.