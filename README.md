# CoinePro Android

Native Android client for CoinePro, built with Kotlin and Jetpack Compose.

CoinePro is an AI-powered market intelligence and signal execution platform for Forex and Crypto. The Android product is intentionally signal-centric and does not implement an embedded trading chart or a generic broker/exchange terminal.

## Foundation

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.0.0
- Kotlin 2.3.0
- compileSdk / targetSdk 36
- minSdk 26
- GitHub Actions CI

## Product documentation

- [Design Direction](docs/DESIGN_DIRECTION.md)
- [Product Roadmap](docs/PRODUCT_ROADMAP.md)
- [Delivery Checklist](docs/ROADMAP_CHECKLIST.md)
- [Canonical Phase Index](docs/PHASE_INDEX.md)

## Current state

Phases 0 through 6 are implemented on the cumulative `bootstrap/android-foundation` integration branch. The exact green milestone branch, end SHA and Android CI run for each completed phase are recorded in `docs/PHASE_INDEX.md`.

Delivered foundations now include authentication/session handling, realtime market-data transport, Signals list/detail, Alerts & Push, MT5/LBank connection surfaces, and signal-scoped execution confirmation/tracking.

The next milestone is **Phase 7 — AI Generated Market Signal**.

Production-only vendor/broker credentials, IP whitelisting, external connectivity and full end-to-end live smoke testing are intentionally deferred to Phase 17 — Launch Readiness.

## Branch discipline

`main` remains the stable base. `bootstrap/android-foundation` is the cumulative integration branch. Completed `feat/...` phase branches are milestone pointers to their exact phase-end commits; new phase work must be created in this repository only.

## Security baseline

This repository is public. Never commit production credentials, API keys, MT5/LBank secrets, signing keys, `.env` files, `google-services.json`, or other private material. Production secrets belong in protected runtime/CI infrastructure as appropriate.

## CI

Android phase milestones validate lint, unit tests, debug assembly and the debug APK artifact. Phase 6 milestone CI Run #65 completed successfully.