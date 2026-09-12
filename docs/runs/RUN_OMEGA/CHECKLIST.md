# RUN Ω — the end state, line by line

`✅` means done and proven by the evidence beside it. `❌` means not yet — including anything that
was narrowed, per R3. Every line is re-read at the end of every phase, not once.

| # | The line | State | Evidence |
|---|---|---|---|
| E1 | First run: one question, then BTCUSDT H1 with the Signal Layer and Rasad's three sentences | ❌ | Ω3 |
| E2 | Every built-in and every script exposes state, markers, confidence % with N, and a sentence; the legend row opens Explain | ✅ | `SignalSpec`, `ConfidenceEngine`, `ChartSignalEngine`; `SignalLayerTest` (16), `ChartSignalEngineTest` (7), `sem_signal_verdict.nama`; `app/build/proof/omega-signal-layer-fa-dark.png`, `omega-explain-sheet-fa-dark.png` |
| E3 | A Setup score, 0–100, grey→gold, with N signals, that opens into what contributed | ✅ | `ConfidenceEngine.setupScore`; the chip in `omega-signal-layer-fa-dark.png`; `ExplainSheetBody(id = null)` |
| E4 | Replay Arena: daily challenge, discipline + P&L score, streak, league, share card | ❌ | Ω4 |
| E5 | Rasad coach: read this chart, suggest an alert, review my last paper trade | ❌ | Ω4 |
| E6 | Chart owns the phone: ≥ 60 % height, one accent, no prose, haptics, flash, springs | ❌ | Ω2 |
| E7 | Simple/Full mode, switchable anywhere in one tap, nothing lost either way | ❌ | Ω3 |
| E8 | Share card 1080×1080 from any chart, signal or Arena result, in ≤ 3 taps | ❌ | Ω4 |
| E9 | Market Mood strip on Home | ❌ | Ω4 |
| E10 | Every one of the above on the tablet, Explain as a side panel | ❌ | Ω5 |

## Decisions taken for the owner, with the reason

1. **The Now strip is under the plot, not under the legend.** The legend is drawn inside the canvas
   by the chart engine; a row «under» it would be positioned against a plate whose height changes
   with the number of studies. Under the plot it is the first thing below the candles, and it costs
   the plot nothing on a chart with no studies on it.
2. **The Setup score is the first chip of that strip rather than a header.** The chart's header was
   deleted in 4.70.0 for the reason E6 restates — the instrument and its price are the legend's own
   first two lines — so re-adding one to carry the score would undo the line it sits beside.
3. **Confidence is a base rate, never a probability.** The sample size is printed beside every
   percentage and under eight samples no percentage is printed at all. `ConfidenceReport.THIN`.
4. **Direction is green or red; confidence is grey to gold.** A win rate in green on a chart where
   green means «up» would be read as a buy. The dot is the market's colour, the figure is the
   brand's, and they never swap.
5. **A study's sentence is a template, not a model.** Sixteen phrasings cover eighty-three
   indicators, they are computed on the phone from the same arithmetic that drew the line, and they
   are identical every time. `NoteShape`.
6. **`signal(condition)` gained a short form rather than a second function.** One argument is a
   verdict, three are a setup: to a reader they are the same sentence with more or less detail.
