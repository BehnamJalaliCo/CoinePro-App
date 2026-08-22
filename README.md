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

## Current state

The repository is in the foundation/design-system stage. Product features such as Signals, AI Vision, MT5/LBank execution, News, Calendar, Tools, Activity and Notifications are intentionally implemented in later dependency-ordered phases.

## Security baseline

This repository is public. Never commit production credentials, API keys, MT5/LBank secrets, signing keys, `.env` files, `google-services.json`, or other private material. Production secrets belong in protected infrastructure / GitHub Actions secrets as appropriate.

## CI

Pull requests and pushes to `main` validate Android lint, unit tests, and a debug APK build. Successful builds upload a debug APK artifact for test installation.
