# Phase 2 — build, release surface, security hardening

Status of each item in the audit prompt, read against the source after the work.

## Done

| Item | Where | Note |
| --- | --- | --- |
| Certificate pinning | `core/network/NetworkFactory.kt` (`okHttpClient(pins=)`, `parsePins`) | Off by default. Turned on by the `COINEPRO_CERTIFICATE_PINS` signing property → `BuildConfig.CERTIFICATE_PINS`, format `host=sha256/…;host2=sha256/…`. Malformed input fails the build, not the user. Both OkHttp clients in `AppModule` read it. `CertificatePinsTest` covers parse, empty, malformed. Producing and rotating pins: `docs/security/PINNING.md`. |
| Crash report leaves the device | `LaunchReadinessScreen.kt` `CrashCard`, `CoineProApp.kt` `onShareCrash` | «ارسال گزارش» opens the system share sheet with the trace as `text/plain`, subject `Pro Chart <version> crash`. No third-party SDK. |
| Release surface is smaller than debug | `app/build.gradle.kts` `ADMIN_PANEL`, `DIRECT_THIRD_PARTY_FEEDS`; `scripts/quality/check-release-surface.py` | The audit asked for product flavours. A `BuildConfig` boolean that R8 folds does the same job with one build variant: the admin route and its strings are absent from the release APK (verified with `aapt2 dump resources`), and the CI gate reads the built APK to prove it on every run. |
| ABI split | `app/build.gradle.kts` release `ndk.abiFilters` | `arm64-v8a` + `armeabi-v7a` in release; benchmark keeps every ABI. |
| AAB alongside the APK | `.github/workflows/android-apk.yml` | Same signing properties, `:app:bundleRelease`, attached to the GitHub release as `pro-chart-<version>.aab`. |
| No secret reaches a log | `check-cross-phase-consistency.py` `check_no_secret_logging` | A log call and a secret-named identifier on one line in `core/security`, `core/auth`, `core/execution`, `core/copytrade`, `feature/connections`, `core/network` fails the gate. |
| R8 full mode | — | AGP 9 default; nothing to switch on. `proguard-rules.pro` already keeps only the Gson wire models. |

## Not done, and why

| Item | Reason |
| --- | --- |
| Play Integrity API | The SDK is not in the offline Gradle cache and needs a Google Cloud project bound to the Play listing. The hook point is `core/security`; wire it when the owner has the project number. |
| Help images as an on-demand asset pack | Play Asset Delivery needs the `asset-delivery` plugin (not cached) and a Play-signed AAB to test. The help catalogue is text-only today (238 entries, `core/help/content.json`), so there is nothing heavy to defer yet. |
| Live pins | Need the production certificate chain from the owner's server. The mechanism is in place and inert until `COINEPRO_CERTIFICATE_PINS` is set. |
