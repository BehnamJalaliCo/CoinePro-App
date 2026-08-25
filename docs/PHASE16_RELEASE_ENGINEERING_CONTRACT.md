# Phase 16 — Release Engineering Contract

Status: Closed / Complete.

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

Base checkpoint: Phase 15 final green Head `a8d26b7df6332f569f963756a2e041ad31b3cdab` (Android Run #212, Security Run #44).

Validated Phase 16 code checkpoint: `0681a763cf504275b60e50495d3c64d13f73ac79` (Android Run #226, Security Run #58).

Final Phase 16 documentation Head: `5a1a02daf72acc60581665b3aee27dec713b400c` (Android Run #230, Security Run #62).

## Release identities

CoinePro Android has distinct build environments:

- `debug` — developer-only configuration namespace.
- `staging` — release-like, non-debuggable build with application id `com.coinepro.app.staging` and staging-only service properties.
- `release` — production application id `com.coinepro.app` and production-only service properties.
- `benchmark` — release-like benchmark build pinned to non-routable `https://benchmark.example.invalid/` and empty Firebase configuration.

`staging` and `release` API base URLs are required to differ. Staging does not fall back to production Gradle properties. The client boundary is defense in depth only: server-side authentication, environment-specific accounts, entitlements and execution validation remain authoritative.

## Configuration namespaces

Debug:
- `COINEPRO_DEBUG_API_BASE_URL`
- `COINEPRO_DEBUG_FIREBASE_*`

Staging:
- `COINEPRO_STAGING_API_BASE_URL`
- `COINEPRO_STAGING_FIREBASE_*`

Production:
- `COINEPRO_PRODUCTION_API_BASE_URL`
- `COINEPRO_PRODUCTION_FIREBASE_*`

Release signing:
- `COINEPRO_RELEASE_STORE_FILE`
- `COINEPRO_RELEASE_STORE_PASSWORD`
- `COINEPRO_RELEASE_KEY_ALIAS`
- `COINEPRO_RELEASE_KEY_PASSWORD`

Versioning:
- `COINEPRO_VERSION_NAME`
- `COINEPRO_VERSION_CODE`

No production or staging credential is committed to the Android repository.

## Signing contract

Production/staging protected signing is opt-in and all four signing properties must be supplied together. Partial signing configuration fails Gradle configuration.

Normal pull-request CI does not use a production key. Instead it creates a one-run ephemeral JKS under `$RUNNER_TEMP`, builds a signed release AAB and verifies that `jarsigner` reports the bundle as signed. CI does not use `-strict` because a deliberately self-signed ephemeral CI certificate has no external trust chain; trust-chain warnings are not evidence that the AAB is unsigned. This proves the signing plumbing without exposing or depending on production key material.

The manual internal-release workflow materializes the encrypted/base64-provided upload keystore only under `$RUNNER_TEMP`, sets restrictive file permissions, signs the staging AAB, verifies that the bundle is signed, publishes it, and removes the temporary keystore in an `always()` cleanup step.

Tracked-secret CI continues to reject keystores/private-key files, and `.gitignore` excludes JKS/keystore/PEM/key files plus generated Google auth credential files.

## Versioning contract

`versionName` uses semantic version form such as `1.2.3` or `1.2.3-rc.1`.

`versionCode` must be a positive integer without leading zeroes and must not exceed the Android/Play upper bound `2100000000`.

The repository validator checks semantic syntax and the local positive/range rules before signing or publishing. Play enforces cross-release monotonicity when an artifact is uploaded against the application's existing release history; the repository does not pretend to know the latest Play `versionCode` without Play evidence.

The manual internal-release workflow requires both values explicitly and validates them before signing or publishing.

## Internal testing pipeline

`.github/workflows/internal-release.yml` is manual (`workflow_dispatch`) and targets GitHub Environment `play-internal`.

Required protected secrets include:
- upload keystore material and passwords/alias
- staging API/Firebase configuration
- Google Play service-account JSON with Android Publisher access

The workflow:
1. validates version inputs and required secrets;
2. materializes the upload keystore outside the repository;
3. authenticates to Android Publisher;
4. builds and verifies `app-staging.aab`;
5. creates a Google Play edit;
6. uploads the AAB;
7. assigns the uploaded version to the `internal` track;
8. commits the edit;
9. uploads the AAB/mapping as short-lived CI evidence;
10. removes runner signing material.

If the Play edit is not committed, the publishing script attempts to delete the incomplete edit.

No production Play deployment is introduced in Phase 16. Production external/runtime activation remains Phase 17 launch-readiness work.

## Changelog and release notes

`CHANGELOG.md` is the repository-level user-visible change ledger.

Rules:
- unreleased changes stay under `[Unreleased]`;
- release names match `COINEPRO_VERSION_NAME`;
- user-visible behavior, security-relevant behavior and migration notes are recorded;
- internal implementation churn that does not affect release behavior is not required in the changelog.

## Crash / ANR monitoring decision

Phase 16 does not add a new third-party crash-reporting SDK.

For Play-distributed internal/production builds, Android Vitals / Play Console is the baseline crash/ANR source. This avoids adding another telemetry/retention surface before Phase 17 privacy/analytics review.

If a future crash SDK is added, it requires an explicit privacy/retention contract, environment separation, secret review and user-data review. Crash telemetry must never contain bearer tokens, broker credentials, image bytes, raw AI prompts containing sensitive data, or execution secrets.

## CI gates

Android CI must pass:
- cumulative core/feature unit tests on supported debug test variants;
- `:app:lintDebug`, `:app:lintStaging` and `:app:lintRelease`;
- `:app:assembleDebug`, `:app:assembleStaging`, `:app:assembleRelease`, `:app:assembleBenchmark` and benchmark assembly;
- protected release-signing AAB smoke with an ephemeral CI key;
- existing Compose accessibility tests;
- existing macrobenchmark dry-run wiring.

The current AGP configuration does not expose `:app:testStagingUnitTest`; Phase 16 therefore validates staging through the real `lintStaging` + `assembleStaging` gates rather than claiming a nonexistent unit-test task.

Security CI must pass:
- tracked-secret scan;
- resolved dependency OSV audit including staging runtime dependencies;
- debug/staging/production/benchmark BuildConfig isolation.

## Validated code evidence

On `0681a763cf504275b60e50495d3c64d13f73ac79`:
- Android CI Run #226: **success**
- Security CI Run #58: **success**
- protected release-signing AAB smoke: **success**
- debug/staging/release/benchmark cumulative build: **success**
- Compose accessibility + benchmark wiring smoke: **success**
- tracked-secret, OSV and BuildConfig-isolation gates: **success**

Final documentation closure:
- Head `5a1a02daf72acc60581665b3aee27dec713b400c`
- Android CI Run #230: **success**
- Security CI Run #62: **success**

## Explicit non-claims

Phase 16 does not claim:
- that a client-side environment flag is a server trust boundary;
- that staging credentials/accounts can access production execution;
- that production vendor/broker connectivity has been validated;
- that Play production rollout has been enabled;
- that a new crash analytics vendor has user consent or approved retention;
- that CI ephemeral signing keys are production keys;
- that the local version script independently proves cross-release Play monotonicity.

## Exit criteria

Phase 16 is complete because:
- protected signing plumbing is green in CI;
- staging and production BuildConfig isolation is green;
- staging release identity cannot inherit production endpoint configuration;
- internal-track workflow and version validation are committed;
- changelog and monitoring decision are documented;
- final Android CI and Security CI are green on exact Head `5a1a02daf72acc60581665b3aee27dec713b400c`;
- PR remains Draft/unmerged unless merge is explicitly approved.

## App Links — the recovery link

The app claims `https://user.tradeyar.trade-future.ir/reset` with `autoVerify="true"`. Android
checks that claim on install by fetching
`https://user.tradeyar.trade-future.ir/.well-known/assetlinks.json`.

**Until that file is served, the recovery email opens a browser rather than the app, and nothing
anywhere reports why.** There is no error, no log line and no failed request the app can see — the
link simply behaves like an ordinary link.

`scripts/release/print-assetlinks.sh` prints the file from a keystore. The fingerprint it needs is
the one **Google** signs releases with, which is not the upload key while Play App Signing is on —
take that from Play Console → Release → Setup → App signing. Running the script against the local
keystore prints the upload key's fingerprint, which is correct only for a directly installed APK.
Listing both is usually right during a rollout.

The file must be served as `application/json`, without a redirect, and without authentication.
Confirm afterwards on a device with `adb shell pm get-app-links com.coinepro.app`.
