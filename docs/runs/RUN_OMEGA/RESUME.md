# RUN Ω — where this is, and what is next

_Written at the end of every session, per R5. Re-prompt with «continue RUN Ω from RESUME.md»._

## Done

**Ω1 — the Signal Layer, confidence and Explain (4.75.0).** E2 and E3 are ✅ in
`docs/runs/RUN_OMEGA/CHECKLIST.md`.

- `chart/core/.../SignalSpec.kt` — `MarketState`, `SignalEvent`, `SignalNote`/`NoteShape` (sixteen
  bilingual phrasings), `SignalRead`, and `SignalSpec.read/readLine/readAll/defaultStop`. Four rules
  cover the catalogue: a reference line on the price, a band, a bounded oscillator, an unbounded one;
  `MEASURES` report and never fire.
- `chart/core/.../ConfidenceEngine.kt` — `ConfidenceReport` (win rate, average R, samples, last five,
  horizon), `measure` (walks bar by bar so a stop hit on the way to a win is a loss; a signal whose
  horizon has not printed is not counted), `setupScore` → `SetupScore`.
- `namascript` — `signal(condition, buy, strength, text)` short form → `ScriptVerdict`, carried on
  `ScriptResult.verdicts`; the three-argument setup form is unchanged.
- `feature/chart/.../ChartSignals.kt` — `ChartSignalLayer` (+ `TimeframeRead`) and
  `ChartSignalEngine.evaluate/markersFor`.
- `feature/chart/.../ChartSignalStrip.kt` — the Now strip: Setup chip + one pill per study.
- `feature/chart/.../ExplainSheet.kt` — `ExplainSheetBody`, public because the tablet docks it (E10).
- `ChartController` — `state.signals`, `refreshSignals`/`watchSignals` (off `workers`),
  `setConfidenceHorizon`, `setSignalLanguage`, `readAcrossTimeframes` (H1/H4/D1, fetched on a tap).
- Legend row names open Explain (`CoineProChart.onExplainSeries`).

## Next, in order

1. **Ω2** — chart owns the screen. Price pane ≥ 60 % on a phone; «خوانش بازار و ابزارها» disclosure
   and the studio entry off the page; crosshair «+» on the axis → alert/paper order; draggable alert
   lines; one accent; zero prose; haptics ≥ 12 sites; price flash; rolling digits; undo snackbars;
   one confetti for the three firsts.
2. **Ω3** — Simple/Full mode, the first-run question, Chart Preview from the watchlist, sign-up only
   on save, unified empty/error/offline.
3. **Ω4** — Replay Arena, the Rasad coach (deterministic templates over the Signal Layer), the Market
   Mood strip, share cards, the markets tabs.
4. **Ω5** — tablet parity for all of it (Explain as a side panel) and the web-readiness note.

## Open questions

- **E9's Market Mood** needs a backend for fear/greed, unusual volume and «N watching X». Neither
  CoinePro-FX nor TradeYar serves one today; Ω4 will render the client-computable parts and name the
  endpoint in `BLOCKED.md`.
- **E4's daily challenge** likewise: the symbol and window will be picked deterministically from the
  date until an endpoint exists.
