# RUN Σ — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Σ0 — the pinch, and the buy/sell labels | **done**, 4.81.0 |
| Σ1 — BYO Script (paste & fix, prompt kit, save as mine, share/install, library) | **next** |
| Σ2 — the doctrine gates, `docs/DOCTRINE.md` with D1–D10 | not started |
| Σ3 — the loops and the account (since-last-visit, daily brief, arena card, sync) | not started |
| Σ4 — community scripts | not started |
| Σ5 — tablet parity and the web map, updated for BYO Script | not started |

## Start here

**The owner's recording.** Σ0 was shipped on its own precisely so they could take it: chart portrait
→ horizontal pinch on the candles → vertical pinch on the candles → pinch on the price column → zoom
until «خرید» / «فروش» appear → paste a script → run → add to chart → save under their own name. The
last four of those are Σ1 and are not built yet; the first four are what 4.81.0 is for.

Until that lands, S1 reads **⏳ owed to device** in `CHECKLIST.md`. It is not a ✅ and it must not be
written as one — that is the whole lesson of run Ω-FIX.

**Then Σ1, which is the big one.** `docs/runs/RUN_SIGMA/CHECKLIST.md` carries S3's seven sub-items.
The order that will hurt least:

1. **Paste & Fix first**, because it is the only part that can be wrong in a way a reader cannot work
   around. The twenty most common LLM mistakes are a lookup table over the diagnostics
   `:namascript` already produces — start by collecting the actual outputs rather than guessing which
   twenty.
2. **The Prompt Kit next**, because it is what makes the paste worth having and it is nearly free: a
   copyable prompt built from the spec summary the language already publishes, plus two intents.
3. **Save as mine** and the library after that. The `.nama` export and the `pro-chart.com/s/<id>`
   deep link share one codec; write the codec once and the import, the export and the link are the
   same three lines.
4. **The sixty Persian-named strategies last**, because they are content and content written before
   the container that holds it is content that gets rewritten.

## Standing facts about this environment

Everything in `docs/runs/RUN_OMEGA/RESUME.md`'s own «standing facts» still holds. Added by this run:

* **An activity's content can be set once.** `composeRule.setContent` twice in one test throws. A
  test that needs two charts from rest wraps the chart in `key(generation)` and bumps a counter —
  `ChartPinchTest.chart()` — and a test that needs two *frames* is two tests.
* **Multi-touch injection works in Robolectric.** `performTouchInput { down(0, …); down(1, …);
  updatePointerTo(…); move() }` reaches a `pointerInput` handler watching the Final pass. Update both
  pointers and send **one** `move()`: moving them one at a time sends single-pointer events and any
  span measured from them is half stale on every other frame.
* **`:benchmark` does not configure offline here.** Its dependencies are not in the cache and never
  have been. Changes to it are source-only until a device pass.
* **A new test dependency is not free.** Run Ω-FIX tried to add `compose-ui-test` to `:chart-ui` and
  the transitive `jsr305:2.0.2` was not cached. Prefer moving the test to `:app`, which already has
  the whole Compose test surface, over adding a dependency to a module that does not.
