# RUN Σ — what is genuinely blocked, and what was shipped instead

R1: a blocker with no implemented workaround is not allowed here. Every entry names the workaround
that is in the build, so the feature works today and the entry is about the *better* version.

---

## 1. The pinch cannot be proved by this container

**Blocked on:** a physical Android device, or an emulator.

No `/dev/kvm` and no system image, so nothing can be recorded and no real thumb can be applied. The
gesture this run fixes is the one thing in the app that a screenshot cannot say anything about.

**Implemented instead:** six tests that inject two real pointers through the same pipeline a thumb
uses — `ChartPinchTest`. They push pointers apart horizontally, vertically and inside the price
ladder, and read what the chart publishes; one of them drives the **whole page** through the root, so
anything stacked above the plot that swallowed a second finger would fail it. Plus `PinchZoneTest`,
which walks every point of the canvas rather than three corners.

That proves the handler is reached and does the right arithmetic. It does not prove the *feel* — how
it tracks, whether it stutters at 120 Hz — and S1 therefore reads **⏳ owed to device** in
`CHECKLIST.md` rather than ✅. The owner's thirty seconds is what closes it.

## 2. The benchmark module cannot be compiled here

**Blocked on:** the offline dependency cache.

`androidx.benchmark:benchmark-macro-junit4` and `androidx.test.uiautomator` are not in this
container's Gradle cache, so `:benchmark` has never configured here, before or after this change.

**Implemented instead:** the scenarios are written — `ChartFlingBenchmark.horizontalPinch` and
`verticalPinch`, with an explicit `performMultiPointerGesture` path kept clear of both gutters — and
they are owed to the same device pass as everything else in `docs/qa/DEVICE_PROOFS.md`. The reason
they are not simply a mode of the existing `pinchZoom` is in the source: `UiObject2.pinchOpen`
pinches against the object's own bounds and spends part of its travel on the price ladder, which
correctly does not zoom time at all.

## 3. The marker-style setting does not survive the app being closed

**Blocked on:** nothing external. It is a decision, and it is the same one `hiddenIndicators` made.

**Implemented instead:** `ChartUiState.markerStyles` is session state, like the legend's eye. Both are
«what I want to see right now» rather than «how this study is set up», and a reader who silences a
noisy study for an afternoon should not find it silenced next week. The study's *settings* — its
period, its colour — are the ones that persist, and they still do. If the owner wants it remembered,
it is one map in the workspace store and the seam is `setMarkerStyle`.

## 4. A share link can only open a script this device already has (Σ1 item C)

**Blocked on:** S7, the community surface, which this run's own plan puts in Σ4.

`pro-chart.com/s/<id>` is claimed in the manifest, checked twice — by `ScriptLink.idOf`, the
language's own rule, and again by `parseCoineProDeepLink`, where every untrusted link in this app is
read — and routed. What it opens is the reader's **own** script, matched on the public id stored
beside it: export a script, mail yourself the link, tap it, and it is there.

A link from somebody else names a script that is not on this device, and the app says exactly that.
It does not fetch, because there is nothing to fetch from, and it does not open an empty editor and
let the reader wonder what they did wrong.

**What this deliberately does not do, and will not:** carry source in the link. A link that carried
code would run a stranger's script on the strength of a tap, from a message nobody can vouch for.
When the service exists, the id will fetch the code, show it to the reader, and add it only if they
say so — every step after the tap something they can see and refuse.

## 5. ~~Sharing a script needs somewhere to share it to~~ — **unblocked in Σ4 (4.85.0)**

**Was blocked on:** S7, the community surface. It turned out to be blocked on nothing: the board in
`core:community` is live — posts, categories, replies, pictures, eight thousand characters — and a
`.nama` document is text. A shared script is a post that carries the code, and the install is a local
read of that post. `ScriptShare`, and Σ4 in `CHECKLIST.md`.

The entry stays because its reasoning was half right and the half that was right still binds: **a
link would have been wrong even with a service behind it**, because the board refuses links
server-side. `ScriptLink` remains what it was — an address carrying an id and never source, so a
tapped link can never be a script that ran — and it is what the web terminal will use, where an
address is a page rather than a deep link.

## 6. The archive carries two stores of five (Σ3, item S6)

**Blocked on:** nothing external. Scope, and named rather than implied.

`ReaderArchive` has a field for each of the five things D8 names — the watchlist, layouts, scripts,
the journal and the streak — and the format round-trips all of them, which is what
`ReaderArchiveTest` holds. What the app actually puts in the file today is the **watchlist** and the
reader's own **scripts**, because those two are the ones whose shape the archive already carries
faithfully: a symbol is a symbol, and a script is a `.nama` file that `ScriptFile` writes and reads.

A layout and a journal entry are typed records with a dozen fields each, and carrying them would
mean a codec per store. A field that came back as a *summary* rather than as the thing would be a
backup that looks like one, which is worse than a gap somebody can read about — so the row's own
note on the profile says, in both languages, what is in the file and what is not.

The streak is the third: the arena keeps it, and it is not in scope where the archive is assembled.
The field exists so that the day it is, there is no format migration.

**What the reader has meanwhile:** everything that cannot be refetched *and* is faithfully
carryable. A reader moving to a new phone keeps their list and their scripts, and is told plainly
that their layouts and journal are not in the file.


## 7. The release keystore lives in GitHub, not in this container

**Blocked on:** nothing the owner has to do — a fact about where the key is.

The signing key is four Actions secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and the release workflow's `COINEPRO_RELEASE_*` pair).
Actions secrets are decrypted inside a run and nowhere else, so **only CI can produce the installable
APK** — `android-apk.yml` signs every push to `main` and attaches the result to a Release. That is
the right arrangement and nothing here asks to change it.

What it means in practice: an APK built in this container is *test-signed* with a throwaway key, so
it will not install over a CI build and a reader would have to uninstall first. The build to hand the
owner is the one on the Release page, not the one from here.

**Which made the next entry matter more than it looked.** `android-apk.yml` had been failing on every
push since 4.71.0 — eleven versions with no signed APK produced anywhere — and the cause was four
Robolectric classes asking for an API level the hosted runner cannot supply, while the same suite
passed in this container. Fixed in 4.85.1 by pinning the default in `robolectric.properties`, with
`RobolectricDefaultSdkTest` to notice if it moves. The lesson is the one D10 keeps making: a check
that is green where somebody is looking and red where nobody is has told you nothing.

## 8. How many people installed a shared script

**Blocked on:** an endpoint that counts installs. There is none, and there is no honest local
substitute.

A shared post carries «به چارت من اضافه کنید» and the install works — `putScript`, the same door the
studio's own button uses. What it cannot carry is the number beside it. An install count is a fact
about everybody who read the post, and this device knows only what this device did.

The tempting shortcut is to print the local number under the public word, and it is worse than
nothing: «۱۲ نصب» computed from one phone reads as social proof and is a private figure wearing a
public label — which is the failure D2 is about, arriving through a feature rather than through a
percentage.

**Implemented instead:** `ScriptInstallStore` remembers what this device added, and the card says
exactly that — «شما این را اضافه کرده‌اید» — which is true, useful, and about the reader rather than
about a crowd. The board's own like is server-side and stays the social number on the post.

The day the community route grows a counter, the shape is a field on the post, and the sentence
above the button becomes two: what everybody did, and what you did.
