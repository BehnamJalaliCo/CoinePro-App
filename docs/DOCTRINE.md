# The doctrine — ten principles, and the gate that holds each one

The owner's brief for the second half opens with these, and the reason they are a document rather
than a conversation is that a principle nobody can fail is a slogan. Every one below has a
**measurable rule** and a **gate**: something that runs, and fails, when the rule is broken.

The status column is honest about which gates exist today. The four that were ❌ were wired in Σ2
(4.82.2) and the column says so; the two that remain are the two whose *feature* does not exist yet,
and they are ❌ because until a gate exists the principle is an intention — which is exactly the
distinction run Ω-FIX was about.

| # | The principle | The measurable rule | The gate | Today |
|---|---|---|---|---|
| D1 | **Meaning before instruments** | Every line on the chart has a state, a sentence and a confidence. No indicator is «just a line» | `SignalLayerTest.every catalogue indicator answers, and none of them throws` walks all of `ChartCatalog.INDICATORS`; `ChartSignalEngineTest` does the same for a reader's own script | ✅ |
| D2 | **Numeric honesty** | Every percentage carries its N; under eight samples there is no percentage at all; a score never exceeds the records behind it | `ConfidenceReport.THIN` + `SignalLayerTest`; `ConfidenceEngine.setupScore`'s property tests (run Ω-FIX 3) and the state-band tests (Ω-FIX 4) | ✅ |
| D3 | **Sixty seconds to meaning** | A new reader with no account sees a *judgement* within sixty seconds of first launch | `SixtySecondsToMeaningTest` — the start destination is not a wall, the chart route asks for no session, and the **default** studies produce a sentence and a non-neutral state on a two-hundred-bar first fetch *and* on a forty-bar half-loaded one, in both languages | ✅ |
| D4 | **One layer** | Nothing is more than one layer away from the chart | `NavigationDepthTest` reads every `composable(route = …)` out of `CoineProApp.kt` — the graph itself, not a list beside it — and requires each to be a menu or bar destination, or to be named in an exemption list with a reason a test checks the length of | ✅ |
| D5 | **Zero explanatory prose** | A note exists only to prevent a real mistake, and is at most one line | `NotePolicy` + `tools/i18n/lint_strings.py`, which fails the build on a `*_note`/`*_hint`/`*_body` key a source resolves itself | ✅ |
| D6 | **Physical feedback** | Every confirmation is a haptic; every tick is a flash; every sheet is a spring | `check-motion-policy.sh` for the motion, and `check-haptic-policy.sh` for the rest: nothing outside `CoineProHaptics` may call the platform's haptics, the five primitives a screen relies on must each still take them, and the call-site count has a floor so a refactor cannot quietly silence the app | ✅ |
| D7 | **A variable reward on every return** | «Since your last visit», today's challenge and a new signal or story are above the fold on Home | A Home test in three data states: nothing new, something new, and offline | ❌ — Σ3 builds the feature; a gate before the feature would be a gate over nothing |
| D8 | **The reader's own investment** | Watchlists, layouts, my scripts, the journal and the streak all sync and export | Round-trip tests per store: write, export, wipe, import, compare | ❌ Σ3 |
| D9 | **Persian as the advantage, English as the doorway** | Every string in both languages; both calendars; Latin digits in figures and Persian digits in prose counts | `tools/i18n/lint_strings.py` (parity, glossary, register, orthography) and `TabletEnglishTest`; the Gradle `checkDefaultLocaleIsEnglish` task | ✅ |
| D10 | **Honest with the device** | No ✅ without a frame or a test; every claim about motion reads «owed» until a device recording lands | `check-checklist-honesty.py` walks every `docs/runs/**/CHECKLIST.md` and fails a ✅ whose Evidence or Frame cell points at nothing — `—` included, because a dash is the shape a blank takes once somebody has been told not to leave one. It found a row in RUN Σ's own checklist the hour it was written | ✅ |

## How to use this

A change is measured against the ten before it is measured against anything else. In practice that
means three questions, in this order:

1. **Does it add a line that cannot explain itself?** (D1) If so it is not finished.
2. **Does it print a number a reader cannot check?** (D2) If so it is worse than not printing one.
3. **What does it claim, and what would fail if the claim were false?** (D10) If the answer is
   «nothing», the claim is not evidence and must not be written as ✅.

## Where D10 came from

4.79.0's checklist said the «→» row above the chart was gone. It was in every portrait frame of two
recordings. What had been removed was the bar's *contents*; the band was still drawn. Nothing was
written dishonestly — a reading of the code supported the claim, and no test did.

So: a row names a frame, or says outright that its claim is not the kind of thing a still frame can
carry and names the gate that fails instead. A blank evidence cell is how that row shipped.
