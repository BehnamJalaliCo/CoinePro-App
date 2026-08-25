# Firebase configuration

CoinePro does **not** use `google-services.json` or the Google Services Gradle plugin. Firebase is
initialised at runtime from four `BuildConfig` fields, which come from Gradle properties per
environment — the same mechanism as every other service setting, documented in
`PHASE16_RELEASE_ENGINEERING_CONTRACT.md`.

`google-services.json` is listed in `.gitignore` deliberately. Nothing in it belongs in the
repository, and the file's presence would silently change how Firebase initialises.

## Mapping the file to properties

Google hands out a `google-services.json`. Four values in it are the ones CoinePro needs:

| Value in the file | Gradle property suffix |
| --- | --- |
| `project_info.project_id` | `FIREBASE_PROJECT_ID` |
| `client[0].client_info.mobilesdk_app_id` | `FIREBASE_APPLICATION_ID` |
| `client[0].api_key[0].current_key` | `FIREBASE_API_KEY` |
| `project_info.project_number` | `FIREBASE_SENDER_ID` |

Each is prefixed per environment — `COINEPRO_DEBUG_`, `COINEPRO_STAGING_`,
`COINEPRO_PRODUCTION_` — so a production project is never reachable from a debug build by accident.

Put them in `~/.gradle/gradle.properties` (never in the repository's own `gradle.properties`), or
pass them as `-P` flags, or as `ORG_GRADLE_PROJECT_*` environment variables in CI:

```properties
COINEPRO_PRODUCTION_FIREBASE_PROJECT_ID=…
COINEPRO_PRODUCTION_FIREBASE_APPLICATION_ID=…
COINEPRO_PRODUCTION_FIREBASE_API_KEY=…
COINEPRO_PRODUCTION_FIREBASE_SENDER_ID=…
```

Leaving them unset is a supported state, not a broken one: `CoineProApplication` skips Firebase
initialisation when any value is blank, and the safety screen then reports that push is not
configured for this build rather than asking for a notification permission that would deliver
nothing.

## What a client Firebase config does and does not protect

The API key in this file ships inside the APK and is extractable from it. It is an identifier, not
a credential, and Firebase is designed on that basis — so it is not treated as a secret here. It is
kept out of the repository for a different reason: a project id and app id committed to source
control outlive the project they were issued for, and a key that cannot be rotated without a code
change is one that never gets rotated.

Restrict it in the Google Cloud console anyway (Android app restriction: package name plus signing
certificate SHA-1). Unrestricted, it can be spent against the project's quota for other Google APIs
by anyone who opens the APK.

## Three things a client config does not give you

**It does not enable Google Sign-In.** That needs an OAuth 2.0 client, which appears in the file as
a non-empty `oauth_client` array with a `client_type: 3` web entry. Its id is also what the
backends want for `GOOGLE_CLIENT_IDS`. Without it, `/user/auth/methods` keeps reporting
`google: false` and the app correctly draws no Google button.

**It does not cover the staging application id.** Staging builds are `com.coinepro.app.staging`,
and Firebase registers a config per package name. Push on a staging build needs a second Android
app added to the same Firebase project under that id, and its own
`COINEPRO_STAGING_FIREBASE_APPLICATION_ID`. The other three values are shared across apps in one
project. Debug builds carry no suffix, so they use the production registration as-is.

**It gives the server nothing.** Sending a push needs a service-account credential held by the
backend, which is a genuine secret and never touches this repository. Until CoinePro-FX and
TradeYar have theirs, `/user/auth/methods` reports `push: false` and the app does not ask for the
notification permission — which is the honest behaviour, not a limitation to work around.
