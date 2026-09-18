# Distribution — how this app reaches a phone, and what that costs

**The short version.** Google Play does not serve this product and is not expected to. The app is
downloaded as a signed APK and installed by hand. Everything below is a consequence of that
sentence, and each consequence has somewhere in the build that answers it.

The owner is corresponding with Google about it. That is worth doing and it changes nothing here
until it succeeds: a product that assumes a store it does not have is a product with no update
channel, no verified links and no honest place to point a reader.

---

## 1. Why there is no store

Two separate refusals, and either one alone would be enough:

* **A developer account cannot be registered from Iran.** Google's own country table for Play
  Console does not list it. `docs/PLAY_COUNTRIES.md` reads the tables and has the links.
* **Play does not serve installs into Iran.** Under the US sanctions regime Google has removed
  Iranian apps from the store and in-app purchase does not work in the country.

The first can in principle be worked around — an account registered in a country that is on the
list, which is a real decision with real consequences and is the owner's. The second cannot be
worked around at all, and it is the one that decides: even a listed app would not reach the readers
this app is written for.

So `docs/PLAY_LISTING.md` and `docs/PLAY_COUNTRIES.md` remain as **preparation for a store this
product is not on**. They are not wrong and they are not dead — the day the correspondence succeeds
they are the day's work already done — but nothing in the build may assume they describe how a
reader gets the app.

## 2. How a reader actually gets it

1. The tag is pushed; GitHub Actions ("Build Android APK") builds and signs it.
2. The release `vX.Y.Z` carries `pro-chart-X.Y.Z.apk`.
3. The reader downloads that file and installs it, granting "install unknown apps" to their browser
   once.
4. Once `pro-chart.com` serves it, the same file is offered from the brand host as well, with the
   update document beside it (§4).

Bazaar and Myket are the other two channels that work inside Iran and are not set up today. Nothing
in the build prevents them; they are a listing exercise, not an engineering one.

## 3. What the app has to do for itself

| what a store would do | what the app does instead | where |
|---|---|---|
| tell the reader an update exists | asks the brand host when the safety screen is opened | `:core:update`, `LaunchReadinessScreen` |
| guarantee the package is the publisher's | shows the file's SHA-256 before the reader downloads it, and refuses to offer an address off the publishing hosts | `AppUpdate.publishable` |
| prove which key signed the install | prints the install's own SHA-1 and SHA-256 | `AppIntegrity.fingerprints`, the safety screen |
| stop a repackaged copy running | `AppIntegrity.check` against `COINEPRO_EXPECTED_SIGNERS` | `app/src/main/.../security/AppIntegrity.kt` |

The app does **not** download or install the package. That would mean holding
`REQUEST_INSTALL_PACKAGES` — permission to put any package at all on the phone — to save a reader
three taps they have already performed once. `AppUpdate`'s own note is the argument in full.

Nor does it block. `mandatory` on a published release changes the wording on the card and nothing
else: a flag on a host that can stop an installed app from opening is a remote kill switch, and a
kill switch is one compromised host away from being everybody's app at once.

## 4. The update document

`GET https://pro-chart.com/api/app/latest`, served by the Pro Chart server (`docs/web/SERVER.md`
§4.6). It is a static JSON file the release process writes; there is no code behind it.

```json
{
  "version_code": 50000000,
  "version_name": "5.0.0",
  "url": "https://pro-chart.com/download/pro-chart-5.0.0.apk",
  "sha256": "…64 hex characters…",
  "notes_fa": "…",
  "notes_en": "…",
  "mandatory": false
}
```

* `version_code` is the **only** field the comparison reads. It is what the package manager itself
  compares, and `scripts/release/version.py` derives it from the name for that reason.
* `url` must be HTTPS and on `pro-chart.com`, `www.pro-chart.com` or `github.com`. The list is
  `AppUpdate.PUBLISHING_HOSTS`, and it is an allow-list because this is the one response in the app
  that ends as an installable package rather than as text on a screen.
* `sha256` is required. An APK offered with no way to check it is one this app declines to offer.
* Both note fields may be empty; the card then shows the version alone.

## 5. `assetlinks.json` — which fingerprint

**The release keystore's own SHA-256.** Not Play's.

This is the one place the absence of a store makes something *simpler*. With Play App Signing,
Google re-signs an upload with a key only they hold, and the fingerprint that verifies an App Link
is theirs rather than the one in the keystore — which is why `print-assetlinks.sh` used to carry a
warning about it. With no Play in the path there is no re-signing: the key that signs the build is
the key on the phone.

Three ways to obtain it, in order of how hard they are to get wrong:

1. **From a phone that has the app.** «ایمنی و انتشار» prints the certificate of the running
   install, SHA-1 and SHA-256, with a copy button. This is the only source that reads the installed
   package rather than something upstream of it.
2. `bash scripts/release/print-assetlinks.sh <keystore> <alias>` — prints the whole file.
3. `keytool -list -v -keystore … -alias …`.

If the app ever does reach Play, list both fingerprints. `sha256_cert_fingerprints` is an array and
Android accepts an install matching any entry.

## 6. What this does not change

* **The legal pages still have to be served.** `BrandConfig.LEGAL_BASE_URL` points at
  `pro-chart.com/legal/…` and every terms and privacy link in the app opens it. That obligation
  comes from the documents themselves and from the reader, not from a store review.
* **The account-deletion page still has to stand on its own.** It was written because Play requires
  it; it is kept because a reader who has uninstalled the app must still be able to ask.
* **Firebase Cloud Messaging still works.** Push depends on Play *Services*, which is on the phone,
  not on the Play *Store*, which is not. A device with neither — and there are some — simply has no
  push, which the safety screen already says in `safety_push_not_configured`.
