# What is in the APK, and what protects it

Written after the owner asked that the app be built so that "if it falls into anybody's hands they
cannot extract code or anything security-sensitive from it". This is the honest answer: what is
already true, what was added, and — the part most documents like this leave out — what cannot be
done at all.

---

## The short version

There is **no secret in this APK**. Not because it was scrubbed, but because the app was built so
that there is nothing to scrub: every credential belongs to a session, is minted by a server after
somebody signs in, and lives in the phone's hardware keystore. An attacker who unpacks the APK gets
the same thing a Play reviewer gets — the code, the artwork and two public addresses.

Verified by unpacking `app-release.apk` and reading every string in `classes.dex` and
`resources.arsc`. What is in there:

| Found | Is it a secret? |
| --- | --- |
| `https://coineprofx.com/api/`, `https://tradeyar.trade-future.ir/` | No. Every request from every phone goes to them; they are as public as a web address gets. |
| The Firebase Android API key (`AIza…`) | **No** — Google documents this explicitly. It identifies the project, it does not authorise anything, and it is restricted by package name and signing certificate. See "Restrict the key" below for the one thing to do about it. |
| The Google OAuth **web** client id | No. It is an audience, published in `auth/methods` by the server to any caller. |
| `github.io` links to the privacy, terms and deletion pages | Public pages. |
| Obfuscated class names — `com/coinepro/app/a`, `b`, `c` … | The code, unnamed. |

What is **not** in there: no private key, no API secret, no token, no password, no `mapping.txt`.

---

## What was already in place

* **R8 with obfuscation and shrinking.** `isMinifyEnabled` and `isShrinkResources` on release. Class
  and method names come out as `a`, `b`, `c`; unreachable code is gone. `proguard-rules.pro` keeps
  only what reflection needs, and every keep in it has the failure it prevents written beside it.
* **The de-obfuscation map is never shipped.** `mapping.txt` is written to `app/build/outputs/`,
  which the `.gitignore` excludes and which is not part of the APK. It is worth keeping the file for
  each release you publish, off the repository, because without it a crash report from a user is
  unreadable.
* **Tokens are in the hardware keystore.** `core:security` wraps the access and refresh tokens with
  an AES key generated inside the Android Keystore; the key material never enters the app's memory
  and cannot be exported off the device. A rooted phone's attacker can ask the keystore to decrypt
  *on that device*; they cannot take the token elsewhere.
* **`android:allowBackup="false"`.** The app's files are excluded from ADB backup and from Google's
  cloud backup, so a token cannot be lifted out of a backup archive.
* **Cleartext traffic refused**, by manifest flag and by a network security config that trusts only
  the system CA store. No user-installed certificate is trusted, which is what stops the ordinary
  intercepting-proxy attack on an unrooted phone.
* **Almost nothing is exported.** One activity, for the launcher and the App Links.
* **No JavaScript bridge.** The Telegram WebView that used to have one is gone.

## What this release adds

* **A repackaging check.** The release build bakes in the SHA-256 of the certificate that signs it,
  read from the keystore at configure time, and refuses to start under any other certificate. See
  `AppIntegrity` for what that does and does not stop — the summary is that Android will not install
  a modified APK under the original signature, so a changed CoinePro must be re-signed, and a
  re-signed one now shows a refusal screen instead of the app. It is bypassable by somebody who
  patches the check out; it stops the copy that spreads.
* **The build residue is no longer packaged.** `DebugProbesKt.bin` (a map of the coroutine
  internals, useless in release), the Google libraries' `.properties` version stamps and their
  `.proto` analytics schemas. The version stamps in particular are the first thing anybody looking
  for a known vulnerability reads.
* **The signing fingerprint is visible in the app**, under «ایمنی و نسخه», with a copy button. Not a
  secret — it is derived from the APK — and it settles the one question that cost days: *which key
  is this install actually signed with?*

---

## Play App Signing — read this before uploading

If the app is uploaded to Google Play with App Signing enabled, **Play re-signs it with a key Google
holds**. The installed APK then carries a certificate this repository has never seen, the check
above finds a fingerprint it does not expect, and every install refuses to run.

The fix takes one minute and must happen *before* the first release:

1. Play Console → your app → Setup → App integrity → App signing key certificate.
2. Copy the **SHA-256**.
3. Build with `-PCOINEPRO_EXPECTED_SIGNERS=<that fingerprint>`, or put the line in
   `gradle.properties`.

Both keys are then accepted: the upload key for anything you sideload, Play's key for anything from
the store.

## Restrict the Firebase key

The `AIza…` key is not a secret, but it should be scoped so it cannot be reused by another app:

Google Cloud Console → APIs & Services → Credentials → the Android key → **Application
restrictions → Android apps** → add `com.coinepro.app` with the SHA-1 fingerprint.

---

## What cannot be done, and why saying so matters

An app runs on hardware the attacker owns. Everything below is true of every Android app that has
ever shipped, including the banking ones:

* **The code can be read.** R8 removes names, not logic. A decompiler produces working Java from any
  APK; obfuscation makes it tedious, not impossible.
* **Strings cannot be hidden.** R8 has no string encryption. Anything the app must send — a URL, a
  route, a header name — is in the binary in plain text, because the app has to have it to run.
  Encrypting it only moves it: the key would have to be in the same binary.
* **The repackaging check can be removed** by the same person who repackages, if they are patient
  enough to find it.
* **A rooted phone with a debugger attached beats all of it.** Frida can read any string in memory
  at the moment it is used.

The conclusion that follows is the one that actually protects an account, and it is not in the app:
**a token must be worth little on its own, and the server must be what decides what it can do.**
Short access-token lifetimes, refresh tokens that rotate, rate limits, and an execution route that
re-checks entitlement per order. All four are already how the two backends behave.

Google Play Integrity is the one remaining option — it asks Google, from outside the app, whether
this install came from Play and whether the device is genuine. It is worth adding when the app is on
Play, because the answer arrives at the server rather than at the app, which is the only place an
answer cannot be patched out. It requires the app to be published first.
