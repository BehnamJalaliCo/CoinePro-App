# iOS — what already exists, and the steps to a first build

Counted from the tree at 5.16.0, not from memory.

## What is already cross-platform

| Layer | State | Evidence |
|---|---|---|
| Chart engine (`:chart-core`) | Kotlin Multiplatform, `commonMain` only, `jvm()` + `wasmJs` + Android | `chart/core/build.gradle.kts` |
| Script language (`:namascript`) | Same | `namascript/build.gradle.kts` |
| Chart renderer (`:chart-ui`) | Compose Multiplatform, `commonMain` + Android and browser actuals | `chart/ui/build.gradle.kts`, `ChartUiPlatform.kt` (the only `expect`s: text, logos, glyphs) |
| Every other module (74 core + feature) | Android libraries in form, but **already compiled for a second, non-Android target**: the browser build compiles their own `src/main/kotlin` against shims | `web/tools/share_sources.py`, `web/src/shims/kotlin` (105 files: `android.*`, `androidx.*`, `dagger`, `javax`, `okhttp3`, `okio`, `retrofit2`) |
| Android-specific imports | ≤ 4 files per module; 51 of 74 modules have none | `grep "import android\."` per module |
| Design system, typeface, strings | Compose + IRANYekanX + `values/` / `values-fa/` — the browser already renders all three | `web/build.gradle.kts` (`copyTerminalFonts`, `exportTerminalStrings`) |

The browser build is the proof that matters: it shows the whole app — not a demo — compiles and runs
on a target with no Android framework. iOS is a second such target, with the same method.

## What is Android-only, and its iOS counterpart

| Android piece | Where | iOS counterpart |
|---|---|---|
| Hilt | `:app` | the generated graph the browser uses (`WebGraph`), as an `IosGraph` |
| Room | `:core:database` | Room KMP (supports iOS) — or the browser's in-memory store first |
| DataStore | `:core:datastore` | DataStore KMP (supports iOS) |
| OkHttp / Retrofit | 12 / 20 modules | Ktor Darwin engine behind the same shim the browser uses |
| Firebase Messaging | `:app` | APNs through Firebase iOS SDK |
| WorkManager (on-device alerts) | alerts | none worth porting — iOS suspends apps; alerts must be server-side (the dissection's own §12 calls this the largest debt) |
| Biometric | `:core:security` | LocalAuthentication (Face ID / Touch ID) |
| Glance widgets, PiP | `:app` | WidgetKit (Swift), AVKit PiP — later |
| Coil | 4 modules | Coil 3 is multiplatform |

## Steps, in order

1. Add `iosArm64()` and `iosSimulatorArm64()` to `:chart-core`, `:namascript`, `:chart-ui`; write
   `:chart-ui`'s three `ChartUiPlatform` actuals for iOS.
2. A `:ios` module shaped like `:web`: the same `share_sources.py` over the same modules, the browser's
   shims reused where they are platform-neutral, an `IosGraph` for Hilt.
3. Network on Ktor Darwin, DataStore KMP, the in-memory database first and Room KMP after.
4. A thin Xcode project: a SwiftUI `App` hosting `ComposeUIViewController`, IRANYekanX in the bundle.
5. Push through APNs, Face ID, the share sheet.
6. Server-side alerts before release — on iOS an on-device alert engine does not run in the background.

## What is blocked on the owner

* An Apple Developer account (signing certificates, App Store Connect, APNs key).
* A Mac with Xcode, or a macOS CI runner, to link and sign — Kotlin/Native iOS binaries cannot be
  linked on Linux.
