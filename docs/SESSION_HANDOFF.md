# Session handoff — CoinePro Android

Written so a fresh session can pick the work up without re-deriving anything. Read this first.

---

## 1. Working agreement

- **All work goes directly on `main`. No branches, no pull requests.** The repository owner asked
  for this explicitly. Note the consequence: `android-ci.yml` and `security-ci.yml` run on push to
  `main`, so a broken push turns the stable branch red. Build and run the gates locally before
  every push (see §6).
- The owner writes in Persian; reply in Persian.
- The product is **CoinePro** — an AI-assisted signal and trade-execution platform for Forex and
  Crypto. The Android client is signal-centric: no embedded chart, no generic broker terminal.

## 2. Where things stand

Five commits landed this session, all on `main`, all green:

| Commit | What |
| --- | --- |
| `042874e` | IRANYekanX typography; R8 keep rules for the wire models |
| `0617b09` | Font licence code recorded |
| `3080919` | Robolectric screenshot harness (renders real screens without an emulator) |
| `e499cb4` | Persian as default language; RTL and bidi foundation |
| `a95cf59` | Brand identity: launcher icon, gold accent, sign-in lockup |

### Decisions already taken — do not reopen these

- **Numbers stay Latin everywhere** (`2,412.85`), so prices stay comparable with MetaTrader,
  Binance and TradingView.
- **Persian is the default language** (unqualified `values/`), English is the alternative
  (`values-en/`). Both must be kept in sync.
- **Accent colour is the brand gold**, not blue. The logo contains gold and neutral silver and no
  blue at all. Filled gold takes *dark* labels — light-on-gold measures 2.0:1 and fails contrast.
- **Admin panel lives inside the app**, unlocked by five taps on the version number.
- **Auth target**: email sign-up with light (level 1) identity verification, Google sign-in, and
  Telegram kept as a secondary option beneath them.
- **Design target**: new eToro + Binance's speed + Revolut's feel. The owner rated the current UI
  2/100 — a real redesign is expected, not polish.

### The one open design question

`HomeScreen.kt` formats dates with no explicit locale, so under Persian it prints Persian month
names against a **Gregorian** calendar. Whether the product should show Jalali dates is a product
decision nobody has answered yet. Ask before implementing either way.

## 3. What is blocking the real work

The owner is making these two repositories public:

- `BehnamJalaliCo/CoineProFx`
- `BehnamJalaliCo/TradeYar`

They are the Forex and Crypto platforms behind the product and contain **signals *and* copy
trading**, which the Android app does not implement at all. Until they are readable, these cannot
be answered or started:

1. **Does the Android app need its own server, or can it run on one of theirs?** This is the
   owner's central question and it changes the whole architecture.
2. Auth rebuild — the server side of email sign-up, verification, Google, and KYC level 1.
3. Copy trading — no screens exist for it.
4. The admin panel's backing API.
5. The full redesign — designing 13 screens before knowing how many new ones copy trading and
   sign-up require would mean rebuilding the navigation afterwards.

**Once the repos are public, clone them read-only and study: route/endpoint definitions, database
models and migrations, the current auth implementation, anything copy-trading, and any admin
panel.** If `add_repo` still fails with `MCP tool call requires approval`, plain `git clone` works
for public repositories.

### The API surface the Android client already expects

32 endpoints plus one WebSocket. Compare against the two repos to answer question 1:

- **Auth** — `GET user/auth/config`, `POST user/auth/telegram`, `GET user/me`
- **Market** — `GET ws/snapshot?symbols=`, `WSS ws/prices`
- **Signals** — `GET user/signals`, `GET user/signals/{id}`
- **Execution** — eight under `user/signals/execution/*` (MT5 and LBank connect, execute, close, list)
- **Push and alerts** — nine under `user/signals/mobile/*`
- **AI** — `user/signals/ai/*` (quota, jobs), `user/ai/vision/jobs`, `user/ai/assistant/messages`
- **News** — `GET user/market-intelligence`

Product scope is narrow and enforced in code: Forex is `XAUUSD` and `XAGUSD` only; Crypto is
`*USDT` pairs.

## 4. Architecture you need to know before editing

- 33 Gradle modules, ~13k lines of Kotlin. Compose only, Material 3, Hilt, Retrofit + Gson, Room,
  WorkManager. AGP 9.0 / Kotlin 2.3 / compileSdk 36 / minSdk 26.
- **There are no ViewModels.** Every controller is a `@Singleton` injected into `MainActivity` and
  passed down the composable tree by parameter. `CoineProApp` takes 18 parameters.
- **`rememberSaveable` is used zero times**, so every form field is lost on rotation and process
  death. This is a known defect, not a style choice.
- The codebase has a strong, consistently applied principle: **the server owns truth and Android
  never invents it.** No fabricated broker state, no automatic retry on a trading write, stale and
  missing states always visible, AI free text can never become an order. Preserve this.

### Foundations added this session — build on them, do not reinvent

| Thing | Where |
| --- | --- |
| `BidiText.isolateLtr` — keeps Latin runs correct inside Persian | `core/common/.../BidiText.kt` |
| `UiMessage` / `MessageKey` — how non-UI layers produce translatable text | `core/common/.../UiMessage.kt` |
| `UiMessage.resolve()` — the only place a key becomes words | `core/designsystem/.../UiMessageText.kt` |
| `LtrDirection` / `LtrLayout` — shared replacement for five ad-hoc LTR hacks | `core/designsystem/.../LtrText.kt` |
| `CoineProLockup` / `CoineProWordmark` / `CoineProMark` | `core/designsystem/.../CoineProBrand.kt` |
| Brand palette sampled from the logo | `core/designsystem/.../CoineProColors.kt` |
| App language storage and `attachBaseContext` override | `app/.../AppLanguageStore.kt` |

**`feature:signals` is the worked example.** It is the only screen fully converted: strings
extracted to `values/` and `values-en/`, controller emitting `UiMessage`, prices isolated LTR.
Copy that pattern. The other 12 screens still hold ~199 hardcoded English literals, and 7 of the 8
controllers still build English strings — deliberately left until each screen is redesigned, since
converting a controller alone changes nothing visible.

## 5. Environment — this is not preinstalled

A fresh container has **no Android SDK and no usable Gradle** (system Gradle is 8.14.3; AGP 9
needs ≥ 9.1). There is also **no `gradlew` in the repo** — a real gap worth fixing. To build:

```bash
SP=<scratchpad>
curl -sSLo "$SP/g.zip" https://services.gradle.org/distributions/gradle-9.4.0-bin.zip
unzip -q "$SP/g.zip" -d "$SP"
curl -sSLo "$SP/cmdtools.zip" \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p "$SP/android-sdk/cmdline-tools"
unzip -q "$SP/cmdtools.zip" -d "$SP/android-sdk/cmdline-tools"
mv "$SP/android-sdk/cmdline-tools/cmdline-tools" "$SP/android-sdk/cmdline-tools/latest"
yes | "$SP/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SP/android-sdk" --licenses
"$SP/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SP/android-sdk" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$SP/android-sdk" > local.properties   # already gitignored
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

**There is no KVM, so no emulator.** Use the screenshot harness instead — it renders the real
production composables off-device:

```bash
"$SP/gradle-9.4.0/bin/gradle" --no-daemon :app:testDebugUnitTest
# PNGs land in app/build/screenshots/ — read them, then send them to the owner
```

Send screenshots on every visual change. The owner judges the work visually and cannot run the app
themselves during the session.

## 6. Run these before every push

```bash
"$SP/gradle-9.4.0/bin/gradle" --no-daemon \
  :app:testDebugUnitTest :app:lintDebug :app:lintStaging :app:lintRelease \
  :app:assembleDebug :app:assembleStaging :app:assembleRelease
python3 scripts/quality/check-cross-phase-consistency.py
bash scripts/quality/check-motion-policy.sh
bash scripts/security/scan-secrets.sh
```

Two traps in the gates:

- `check-cross-phase-consistency.py` asserts **exact source substrings**. A harmless refactor of
  `SignalGateway`, `ExecutionGateway`, `RoomReadCaches` or `DeepLinkValidation` will fail CI. Read
  it before touching those files.
- `check-motion-policy.sh` **blocks all continuous animation** (`rememberInfiniteTransition`,
  `infiniteRepeatable`). Any modern motion design will need this policy rewritten first — raise it
  with the owner rather than quietly deleting the gate.

## 7. Known defects still open

Found in a full review of `main`; none are fixed yet. Roughly by severity.

**CI is reporting success on evidence it did not produce**

- `android-ci.yml` line ~144 pulls the emulator screenshot from `run-as … files/`, but the test
  writes to `/sdcard/`. The step fails, the failure is swallowed because the
  `android-emulator-runner` script runs without `set -e` and only the last command's exit code
  counts, and `if-no-files-found: error` does not catch a zero-byte file. **The uploaded
  `coinepro-actual-menu-screenshot` artifact on `main` is 206 bytes** — an empty file — while CI
  shows green.
- `rendered-app-screenshot.yml` has failed its last four runs including on `main`'s current HEAD.
  It never runs on `main` (its push trigger names the deleted `feat/phase17-launch-readiness`
  branch), so the red never surfaces.
- `ActualAppMenuScreenshotTest` reconstructs a fake menu instead of launching the app, despite
  being named "actual".
- `connected-test-apk.yml` is gated on that same deleted branch and can never run.
- `internal-release.yml` has **never run once** — the entire signing and Play-publishing path is
  unexercised. It also pins `actions/checkout@v7` while every other workflow uses `v5`.

**Correctness**

- `MarketDataController.kt:180` assigns `socket` *after* `newWebSocket()` returns, while
  `onOpen` (:138) and `onMessage` (:162) compare against it from OkHttp's dispatcher thread. A
  healthy socket can be closed as "superseded". `socket` is also neither `@Volatile` nor atomic.
- `SessionController.kt:102` — a failed Telegram login silently returns to signed-out with no
  message. The user taps and nothing happens.
- `ExecutionController.kt:63` and `:86` set a success message that `refreshConnections()` (:34)
  immediately overwrites with a fresh state object, so it never appears.
- `MainActivity.onResume` fires six network refreshes plus a WorkManager job on every resume, with
  no debounce — including re-paginating up to 2000 signal history records.

**Security hardening**

- `AuthScreen.kt:123-125` — the Telegram WebView uses the default `WebViewClient` with no
  navigation allowlist, exposes `addJavascriptInterface`, and uses `loadDataWithBaseURL` with a
  spoofed `telegram.org` origin while loading a remote script. Impact is bounded because the
  server verifies Telegram's HMAC, but this is a standard review finding. The WebView is also never
  `destroy()`ed, and `setSupportMultipleWindows` is unset so the widget's popup may not open at
  all. `feature:auth` has zero tests.

**Dead and misleading**

- `README.md` says "Phases 0 through 6 are implemented" and "next milestone is Phase 7". `main`
  contains phases 1–17. It is the repository's front door and it is wrong.
- `core:datastore` is entirely unused — `UserPreferencesStore` is referenced nowhere.
- `EntitlementSnapshot`, `hasPaidPanelAccess`, `kycStatus` and `disclaimerAccepted` are computed
  and stored in session state but **read by no UI**. All gating is server-side 403s.
- `app/src/main/baseline-prof.txt` is an 8-line hand-written stub with no method entries, so it
  delivers effectively no AOT benefit. `BaselineProfileGenerator` exists but its output is never
  wired back.

**Missing engineering hygiene**

- No `gradlew` wrapper — builds are not reproducible for a new contributor.
- 13 feature modules have zero tests; `core:security` (token encryption) is untested.
- No ktlint/detekt/spotless, no dependabot, no dependency lock, no CodeQL.
- `appScope` runs on `Dispatchers.Main.immediate`; signal-history sorting and `Instant.parse` for
  up to 2000 records run on the main thread.
- Library modules target Java 11 while `:app` targets Java 17.

## 8. Assets and third-party material

- **Font**: IRANYekanX (Eco), Regular and Bold only. Proprietary; licence code is recorded in
  `core/designsystem/FONT_LICENSE.txt`. The Latin-numeral variant is installed on purpose.
- **Logo**: the owner supplied a proper transparent master (1672×941 RGBA, mark and wordmark on
  one canvas). It is kept at `core/designsystem/brand/` and **every brand raster is generated from
  it** — the five densities of `coinepro_mark` and `coinepro_wordmark`, and the adaptive launcher
  foreground. Nothing is hand-cut any more; the earlier black-ground JPEG cut is gone. A vector
  original would still be better for the Play listing and any print use, but nothing in the app
  needs one.
- **Icons**: the owner wants TradingView/Binance-style icons and has explicitly accepted the legal
  responsibility, so do not relitigate it. Practical constraint only: those companies' asset files
  cannot be fetched and their artwork cannot be reproduced from memory. Either the owner supplies
  the files, or use openly licensed sets that match the look — Lucide (ISC), Phosphor (MIT),
  Tabler (MIT), and `cryptocurrency-icons` (CC0) for coin logos.

## 9. Suggested order once the repos are readable

1. Read both repos; answer the shared-vs-separate server question with evidence.
2. Agree the full screen inventory, including copy trading and sign-up, before drawing anything.
3. Establish the visual language on one or two screens, get sign-off by screenshot, then roll out.
4. Auth rebuild (client and server contract together).
5. Admin panel behind the five-tap version gate.
6. Versioning, then Play release — and fix `internal-release.yml`, which has never executed.

Fold the §7 defects into whichever phase touches the same code rather than doing them as a
separate sweep, except the CI evidence problems, which are worth fixing early: right now a green
build is not proof of anything it claims.
