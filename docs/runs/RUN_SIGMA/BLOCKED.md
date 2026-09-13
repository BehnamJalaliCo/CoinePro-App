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

## 5. Sharing a script needs somewhere to share it to (Σ1 item D)

**Blocked on:** S7, the community surface, which this run's own plan puts in Σ4.

A «share script» that produced a link nobody can open is not a feature, and a community post needs a
feed to post into. What Σ1 leaves for it is the address format — `ScriptLink`, `pro-chart.com/s/<id>`
— and the reason it carries an id and never source: a link that carried code would be running a
stranger's script on the strength of a tap, from a message nobody can vouch for. With an id, the
code arrives from the service, is shown to the reader, and is added only if they say so.
