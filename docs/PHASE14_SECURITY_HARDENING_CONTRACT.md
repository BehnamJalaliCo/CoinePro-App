# Phase 14 — Security Hardening Contract

## Scope

This contract defines the Android security boundaries delivered in Phase 14 for `BehnamJalaliCo/CoinePro-App`.

It does not claim that a client application can make a compromised device trustworthy, and it does not move production broker/vendor credentials or live-provider validation into the Android repository. Those remain external launch-readiness concerns.

## Security principles

1. Server/provider state remains authoritative for authentication, entitlements, signals and execution.
2. Android must not turn network ambiguity, rate limits, rooted-device checks or cache state into fabricated success.
3. Production vendor/broker secrets are never source-controlled, persisted by Android, written to logs or embedded as BuildConfig fields.
4. Read retries and trading writes have different safety rules: a read may be retried where explicitly safe; an execution/close write is never automatically repeated by Phase 14 code.
5. Security controls must fail closed where the app has evidence, and must remain explicit when Android lacks evidence.

## CI security gates

### Tracked-secret scan

`Security CI` runs `scripts/security/scan-secrets.sh` over tracked repository content.

Blocked material includes:

- private-key signatures
- common cloud/GitHub/Slack/OpenAI/API token signatures
- bearer-token-looking literals
- tracked keystores/private key files
- tracked `.env*`, `local.properties` and `google-services.json`

A match fails CI. Exposed real credentials must be removed and rotated; suppressing the scanner is not an accepted remediation.

### Resolved dependency audit

The repository's GitHub Dependency Graph is not assumed to be available. Instead, Security CI resolves the actual Android `debugRuntimeClasspath` and `releaseRuntimeClasspath` dependency graph, exports Maven coordinates, and queries OSV.

- direct and transitive resolved dependencies are audited
- the exception ledger is explicit and starts empty
- a vulnerability is not silently ignored merely because the GitHub Dependency Graph is unavailable

### Build configuration isolation

Debug and release consume separate Gradle property namespaces.

Debug reads only `COINEPRO_DEBUG_*` service configuration. Release reads the production/release `COINEPRO_*` service configuration. Security CI generates both BuildConfig variants with unique markers and verifies that release markers do not appear in debug and debug markers do not appear in release.

Broker credentials and exchange API secrets are not BuildConfig inputs in either variant.

## Network transport and logging

### HTTPS / cleartext

- Retrofit rejects non-HTTPS API base URLs.
- the application manifest disables cleartext traffic
- Android Network Security Config also disables cleartext and uses system certificate authorities
- WebSocket market transport derives WSS only from an HTTPS base URL

### HTTP logging

Release network clients do not install `HttpLoggingInterceptor`.

Debug may explicitly enable `BASIC` logging only. It never enables body logging in the Phase 14 network factory. `Authorization`, `Cookie` and `Set-Cookie` headers are redacted.

This is intentionally stricter than relying on ProGuard/R8 to remove logs after the fact.

## Certificate strategy

Phase 14 deliberately does **not** invent certificate pins.

Current policy:

- require HTTPS/WSS
- trust Android system certificate authorities
- disallow cleartext
- do not accept user-added debug CAs through a production override in the checked-in release policy

Certificate pinning is deferred until the production API/provider domains and certificate-rotation/backup-pin process are final and operationally owned. A guessed pin would create an avoidable availability risk and could encourage disabling validation during rotation incidents.

Before enabling pinning later, the project must have:

- stable production hostnames
- primary and backup SPKI pins
- a documented rotation and emergency rollback process
- staging validation that does not weaken the production trust policy

No absence of pinning is presented as evidence that a connection is provider-authentic beyond normal TLS/PKI validation.

## Execution threat model

### Sensitive inputs

MT5 login/password and LBank API key/secret are transient request inputs. Android does not persist them in Room/DataStore and the HTTP body is not logged by the release client.

The server is responsible for provider credential storage/protection and provider-side permissions. Android must not claim that a submitted credential was securely stored by a broker unless server/provider state confirms the connection contract.

### Trading writes

`executeSignal` requires a non-blank client request ID and the existing quantity validation. The client request ID is the Android idempotency boundary supplied to the server.

Phase 14 maps HTTP 429 into an explicit execution rate-limit error. The controller makes one gateway call per user execution action and does not automatically retry the write. The same rule applies conceptually to close requests: ambiguous/rate-limited writes are not converted into a second hidden trading request.

Android never interprets a timeout, 429, generic HTTP success without a valid execution payload, or cached state as broker execution success.

## AI Vision upload threat model

Before upload, selected/captured images are decoded, orientation-normalized, optionally resized to a maximum 2048 px edge, and re-encoded as JPEG under the existing 6 MB limit.

Re-encoding strips original EXIF metadata from the outbound file. Camera cache captures owned by the app are deleted in a `finally` path after preparation, including preparation failures. Image bytes, local file paths and EXIF data are not intentionally logged.

Uploads use the authenticated HTTPS Retrofit path. The release HTTP client has no body logger.

Android does not claim a server-side deletion/retention duration unless the server contract provides it. Local preprocessing privacy is not presented as server retention truth.

## Root, debugger and tamper policy

Phase 14 does not implement a client-only `rooted = blocked` security claim.

Reasoning:

- local root/debug/tamper checks are bypassable on a compromised client
- blanket root blocking produces false positives and can become a false security boundary
- execution authority already belongs to authenticated server/provider contracts

Release builds are explicitly non-debuggable and minified/shrunk. Debug and release service configuration are isolated.

A future Play Integrity or equivalent device-integrity signal may be added as a server-evaluated risk input during release/launch work. Such a signal must not become the sole proof that an execution succeeded, and Android must not locally forge an entitlement or execution result based on it.

## API abuse and rate limits

- authentication maps HTTP 429 to explicit rate-limit state
- AI Signal, AI Vision and AI Assistant already expose explicit 429/quota/rate-limit states
- execution now exposes a dedicated rate-limit exception and performs no automatic trading-write retry
- background WorkManager from Phase 13 remains read-only; it cannot be repurposed to bypass a write rate limit
- the client does not spin, parallelize duplicate writes or fabricate quota recovery

Server-provided future retry metadata may be displayed or used for safe read scheduling, but Android must not invent retry windows.

## Local privacy and retention

### Session

The authenticated session token uses the existing Android Keystore-backed storage boundary. It is loaded into memory only when needed and is cleared on logout/unauthorized session expiry.

### Room cache

Phase 13 Room storage contains safe read models only: market snapshots and closed signal history. Cached quotes restore as stale and account-scoped signal history is cleared on logout/membership loss.

No broker credential, AI Vision image, assistant transcript, live execution command or bearer token is added to Room by Phase 14.

### AI Assistant

The Android assistant transcript remains memory-only and is cleared on logout/session loss or New Chat. Server retention remains whatever the server explicitly reports; Android does not infer it.

### AI Vision

Prepared upload bytes are held for the request flow, not persisted as a Phase 14 database model. App-owned camera temp captures are deleted after preparation as described above.

## Build and artifact policy

- release is explicitly `debuggable=false`
- release HTTP logging is disabled
- R8/resource shrinking remain enabled for release
- debug and release service configuration use separate property namespaces
- Android CI now lints and assembles both debug and release variants
- the debug APK artifact uploaded by CI contains only the debug variant generated from CI's debug configuration
- signing keys are never committed; protected release signing remains Phase 16

## Verification

Phase 14 code checkpoint:

- branch: `feat/phase14-security-hardening`
- code checkpoint SHA: `ed568e8672ef1c112f874f85411a11e0c6e4b7fb`
- Android CI Run #178: success
- Security CI Run #10: success

Run #178 validates cumulative unit tests, network/execution security tests, debug lint, release lint, debug assembly, release assembly and debug APK upload. Security Run #10 validates tracked-secret scanning and the resolved dependency OSV audit.

The final Phase 14 documentation/security-verifier Head must pass both Android CI and Security CI before Phase 15 is created.

## Explicit non-claims

Phase 14 does not claim:

- that a rooted/untrusted client can be made trustworthy by local checks
- that certificate pinning is active before production pin/rotation inputs exist
- that server-side AI image retention is known when it is not supplied
- that local cache proves current provider state
- that a rate-limited/ambiguous execution request succeeded
- that production broker/vendor secrets belong in the Android repository
