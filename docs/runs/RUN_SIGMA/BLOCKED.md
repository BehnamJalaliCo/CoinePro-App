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

## 4. «Save as mine» is a model with no home yet (Σ1 item C)

**Blocked on:** nothing external. It is scope, and it is named here rather than being folded into a
claim.

`ScriptDocument`, `ScriptFile` and `ScriptLink` are written and tested: a document with a name, a
description, a colour, tags, a pane, default inputs and five revisions; a `.nama` file whose header
is comment lines, so an exported file is itself a runnable script; and a link reader that refuses
every hostile form the test throws at it. `ScriptDocumentTest` is twenty cases.

What does not exist is the wiring. `saved_scripts` stores a name, a source, a preset id and a blob
of input overrides — that is all it has ever stored — so a document needs four more columns and a
migration written out by hand, which is what that table's own documentation requires because there
is no server copy to refetch. On top of that sits a «My scripts» screen that can show a colour, a
set of tags and a revision list.

**What the reader has meanwhile:** the studio's existing save, unchanged since 4.73.0 — a name and a
source. The new types are not half-connected to it; they are simply not connected, which is the
state that cannot mislead anybody.

## 5. Sharing a script needs somewhere to share it to (Σ1 item D)

**Blocked on:** S7, the community surface, which this run's own plan puts in Σ4.

A «share script» that produced a link nobody can open is not a feature, and a community post needs a
feed to post into. What Σ1 leaves for it is the address format — `ScriptLink`, `pro-chart.com/s/<id>`
— and the reason it carries an id and never source: a link that carried code would be running a
stranger's script on the strength of a tap, from a message nobody can vouch for. With an id, the
code arrives from the service, is shown to the reader, and is added only if they say so.
