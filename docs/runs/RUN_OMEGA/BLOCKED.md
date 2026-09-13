# RUN Ω — what is genuinely blocked, and what was shipped instead

R1: a blocker with no implemented workaround is not allowed here. Every entry below names the
workaround that is in the build, so the feature works today and the entry is about the *better*
version that needs something this session cannot reach.

---

## 1. Every recording, every benchmark, every device frame

**Blocked on:** a physical Android device, or an emulator.

This container has no `/dev/kvm` and no system image, so no emulator can start and nothing can be
recorded. That rules out, precisely:

* every MP4 the owner has asked for across runs B, G, H, K and Ω;
* the fling benchmark with three script indicators on the chart;
* a 120 fps recording of a pan;
* NamaScript evaluation timings on a Pixel 6a;
* Pixel Fold frames;
* the tablet soak.

**Implemented instead:** Robolectric with `GraphicsMode.NATIVE`, which renders the real Compose tree
through the real Skia and writes PNGs to `app/build/proof/`. It proves *composition and paint* — that
the Now strip draws, that the Explain sheet's body lays out at 411 dp, that the light theme's candles
are saturated — and it cannot prove *motion*. Every visual claim in this run's reports is a frame
from that rig, and every claim about frame timing is marked as owed.

`docs/qa/DEVICE_PROOFS.md` carries the exact `adb` commands, so the owner can take the recordings in
one pass rather than discovering the incantation.

**The one that 4.80.0 is waiting on.** The Ω-FIX list ends with a thirty-second sequence the owner
asked to record once the eight fixes landed: chart portrait with no top row → scrub → «+» on the
axis → alert → the Setup sheet → the Rasad sheet → an Arena result → a share card. Every step of it
has a still frame in `app/build/proof/` or a test behind it, and none of that is the same thing as
watching it happen. It is the acceptance test for this version and it is the one piece of evidence
this container cannot produce.

## 2. The Arena's daily challenge has no server to ask

**Blocked on:** an endpoint. Neither backend serves «today's symbol and window».

**Implemented instead** (Ω4, shipped): `Arena.challengeFor` picks deterministically from the date, so
every reader on a given day gets the same symbol and the same window and a shared score is
comparable. `ArenaTest` holds that property — the same day always gives the same challenge, and the
window always has both a past and a future. The seam is that one function; when the endpoint exists
it replaces the pick and nothing else moves.

## 3. The friends league has no server either

**Blocked on:** the same absence.

**Implemented instead** (Ω4, shipped): `ArenaStore` keeps the reader's own history on the phone — one
row per day, the streak counted back over the rows rather than stored beside them — and the result
sheet draws «you, over time»: today's score, the streak, and the reader's own best. A leaderboard of
one is worse than no leaderboard, so it is not drawn as one. The store stays when the endpoint
arrives: a reader's own record is the thing they look at most and it has to survive being offline.

## 4. The news-image proxy does not exist

**Blocked on:** a backend route. `docs/backend/FEEDS.md` asks for it.

**Implemented instead** (run K, shipped): `NewsImagePolicy.proxyBase` is the single seam and is
currently null, so images load direct where the source allows it and fall back to a monogram plate
where it does not. Tested, inert, and one constant from working.
