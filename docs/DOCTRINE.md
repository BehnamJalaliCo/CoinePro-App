# The doctrine — ten principles, and the gate that holds each one

The owner's brief for the second half opens with these, and the reason they are a document rather
than a conversation is that a principle nobody can fail is a slogan. Every one below has a
**measurable rule** and a **gate**: something that runs, and fails, when the rule is broken.

The status column is honest about which gates exist today. Wiring the remaining four is Σ2 —
`docs/runs/RUN_SIGMA/CHECKLIST.md`, line S4 — and until a gate exists the principle is an intention,
which is exactly the distinction run Ω-FIX was about.

| # | The principle | The measurable rule | The gate | Today |
|---|---|---|---|---|
| D1 | **Meaning before instruments** | Every line on the chart has a state, a sentence and a confidence. No indicator is «just a line» | `SignalLayerTest.every catalogue indicator answers, and none of them throws` walks all of `ChartCatalog.INDICATORS`; `ChartSignalEngineTest` does the same for a reader's own script | ✅ |
| D2 | **Numeric honesty** | Every percentage carries its N; under eight samples there is no percentage at all; a score never exceeds the records behind it | `ConfidenceReport.THIN` + `SignalLayerTest`; `ConfidenceEngine.setupScore`'s property tests (run Ω-FIX 3) and the state-band tests (Ω-FIX 4) | ✅ |
| D3 | **Sixty seconds to meaning** | A new reader with no account sees a *judgement* within sixty seconds of first launch | A first-run path test with a step count, not a stopwatch: splash → question → chart → a `SignalRead` with a sentence, asserted as a bounded number of interactions | ❌ Σ2 |
| D4 | **One layer** | Nothing is more than one layer away from the chart | A navigation-depth test: from the chart route, every feature is reachable in ≤ 2 steps | ❌ Σ2 |
| D5 | **Zero explanatory prose** | A note exists only to prevent a real mistake, and is at most one line | `NotePolicy` + `tools/i18n/lint_strings.py`, which fails the build on a `*_note`/`*_hint`/`*_body` key a source resolves itself | ✅ |
| D6 | **Physical feedback** | Every confirmation is a haptic; every tick is a flash; every sheet is a spring | `scripts/quality/check-motion-policy.sh` (no tween on a spatial transition, continuous motion guarded) and the `CoineProHaptics` call-site count | ✅ (motion), ❌ haptic count as a gate — Σ2 |
| D7 | **A variable reward on every return** | «Since your last visit», today's challenge and a new signal or story are above the fold on Home | A Home test in three data states: nothing new, something new, and offline | ❌ Σ3 builds it, Σ2 gates it |
| D8 | **The reader's own investment** | Watchlists, layouts, my scripts, the journal and the streak all sync and export | Round-trip tests per store: write, export, wipe, import, compare | ❌ Σ3 |
| D9 | **Persian as the advantage, English as the doorway** | Every string in both languages; both calendars; Latin digits in figures and Persian digits in prose counts | `tools/i18n/lint_strings.py` (parity, glossary, register, orthography) and `TabletEnglishTest`; the Gradle `checkDefaultLocaleIsEnglish` task | ✅ |
| D10 | **Honest with the device** | No ✅ without a frame or a test; every claim about motion reads «owed» until a device recording lands | The CHECKLIST format itself: a **Frame** column that is never blank, and an explicit `⏳ owed to device` state that is not ✅ | ✅ by format, ❌ as a script — Σ2 |

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
