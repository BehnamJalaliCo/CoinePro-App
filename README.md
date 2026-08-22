# CoinePro App

Native Android application for CoinePro.

CoinePro is being built as an AI-powered market intelligence and signal execution companion for Forex and crypto. The Android client is native-first and does not use a WebView architecture.

## Foundation

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.0.0
- Kotlin 2.3.0
- Gradle 9.4.0 in CI
- minSdk 26 / targetSdk 36
- Package: `com.coinepro.app`

## Current phase

This repository is at the foundation/bootstrap stage. Product screens, final visual language, API integration, MT5/LBank execution flows, AI vision, signals, news, tools, and notifications will be introduced incrementally after the core project structure is validated.

## Build

CI installs Gradle 9.4.0 explicitly and validates lint, unit tests, and a debug APK. A committed Gradle Wrapper will be added after the initial toolchain validation.

## Security baseline

- No credentials or production secrets are committed to the repository.
- Cleartext network traffic is disabled.
- Android backup is disabled by default.
- Future auth and broker/exchange credentials must use secure server-side flows and Android Keystore-backed storage where client persistence is necessary.
