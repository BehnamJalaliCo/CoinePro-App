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
2. The release `vX.Y.Z` carries `pro-chart-X.Y.Z.apk`. A build on top of a named version is tagged
   `vX.Y.Z-bN` and **is** a pre-release; the named version is not. That distinction was a hard-coded
   `prerelease: true` until 5.0.0, which left GitHub with no latest release to point at —
   `releases/latest` answered `302` to the list of releases rather than to a file.
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
* Both note fields may be empty; the card then shows the version alone. Where they are not empty,
  they are copied from **`docs/release/UPDATE_NOTES.md`**, which exists so that the one place this
  product speaks to a reader about itself is not written from whatever was to hand.

## 4½. What the digest proves, and what actually protects a reader

Worth writing down now that one machine serves both the document and the file it names, because the
answer is not the obvious one.

**The `sha256` proves transport, not publication.** A reader who downloads the APK and checks it
against the document has established that the bytes arrived intact and that the host meant to serve
*that* file. They have **not** established that the publisher of this app made it — because the same
host wrote both numbers. Anybody with root on `pro-chart.com` could serve a different APK and a
matching digest, and every check in this paragraph would pass.

**The signature is the anchor, and it is on the phone rather than the server.** Android refuses to
install an APK over an existing one unless the signing certificate is identical. So the worst a
compromised `pro-chart.com` can do to somebody who already has this app is offer a package their
phone declines with «app not installed». It cannot replace the app in place, because the key that
signs releases is not on that machine — it is a GitHub Actions secret, used in CI and nowhere else.
`AppIntegrity.check` is the second line: a copy signed with another key refuses to run even if a
reader uninstalls first and installs it fresh.

Three things follow, and they are the reason the design is shaped the way it is:

* **Keep the GitHub release.** It is the independent copy. The workflow that builds it holds the
  key; the host that serves it does not. `AppUpdate.PUBLISHING_HOSTS` allows `github.com` for
  exactly this reason, and `--host github` stays as a supported way to publish the document.
* **Never put the signing key on the server.** Nothing on that machine needs it, and the moment
  something does, the two independent things become one.
* **A new reader — first install, no existing app — is the exposed case**, since there is no
  previous signature to compare against. That reader's only real protection is that the fingerprint
  is published in more than one place: this document, `assetlinks.json`, and their own phone's
  «ایمنی و انتشار» screen afterwards. It is also why §5's value is written down here rather than
  left to be looked up.

None of this makes the digest useless — it catches a truncated download, a mirror, a proxy that
rewrote something — and all of it is why the card shows the digest *and* the app never installs
anything itself.

## 5. `assetlinks.json` — which fingerprint

**The release keystore's own SHA-256.** Not Play's.

This is the one place the absence of a store makes something *simpler*. With Play App Signing,
Google re-signs an upload with a key only they hold, and the fingerprint that verifies an App Link
is theirs rather than the one in the keystore — which is why `print-assetlinks.sh` used to carry a
warning about it. With no Play in the path there is no re-signing: the key that signs the build is
the key on the phone.

**The value, as of 5.0.0** — read off the published APK with `apksigner verify --print-certs`, and
independently confirmed by the Pro Chart server's own reading of three separate releases (4.99.0,
5.0.0, 5.0.0+4; all three identical):

```
subject: CN=CoinePro, OU=Mobile, O=CoinePro, L=Tehran, C=IR
SHA-256: 96:12:AB:6C:BF:BB:4F:4F:FB:F1:51:D8:60:2C:12:9D:CC:E8:A9:77:1E:85:46:69:E6:63:87:0F:04:04:FB:D0
SHA-1:   5D:E8:7F:4B:B3:E8:35:6B:4E:98:1E:D4:DA:63:0B:A7:77:5F:9A:A8
```

Not a secret — it is derived from a file anybody can download, and it is served publicly in
`assetlinks.json`. It is written down here so a mismatch is something a reader can *notice*: if a
phone's «ایمنی و انتشار» screen ever shows a different SHA-256, that install was signed with another
key and did not come from this repository.

One thing that surprised the server and is worth recording: **the APK carries no v1 signature at
all**, so `keytool -printcert -jarfile` finds nothing and says so unhelpfully. The certificate lives
in the v2 signing block. `apksigner` reads it; `keytool` does not.

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
