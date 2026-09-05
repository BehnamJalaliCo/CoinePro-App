# Phase 3 — copy, naming, tone

One voice, one glossary, both locales complete. Persian stays the default locale (owner's
decision, Phase 0): `values/` is Persian, `values-en/` is English.

## Done

| Item | Where | Note |
| --- | --- | --- |
| String lint in CI | `tools/i18n/lint_strings.py`; run by `check-cross-phase-consistency.py` and by the workflow's *Quality gates* step | Checks, per module: key parity both ways (honouring `translatable="false"`), `%1$s` placeholder parity, engineering words in either locale, retired spellings from §G, informal second person outside `home_agent_*` (pronoun, enclitic «‌ات», singular imperatives), the hamza-on-heh ezafe (U+0654 / U+06C0), a space where a ZWNJ belongs («می رود», «آن ها», «بزرگ تر», «بروز»), exclamation marks and emoji in English outside the greeting, and the length of every `feature_*_body` (≤ 40 chars FA / ≤ 6 words EN). 42 modules, clean. |
| «ه‌ی» convention | every `strings.xml` | 288 hamza ezafes rewritten to heh + ZWNJ + yeh. Legal documents are untouched: they are prose in a formal register and the lint does not read them. |
| Register | 44 strings | «تو / خودت / ‌ات» and singular imperatives («بنویس», «بفرست», «کن») rewritten to «شما» and the plural imperative. Rasad's own strings keep «تو» on purpose. |
| Glossary | 89 strings | «نمودار» → «چارت» (32), «آپ» → «اپ» (20), «کهنه» → «قدیمی», «بازه‌ی کندل / Interval» → «تایم‌فریم / Timeframe», «استودیوی نمودار» → «استودیوی چارت», «نما اسکریپت» → «نمااسکریپت», `ProChart` → `Pro Chart`. |
| Engineering words | 28 strings | "this build" → "this version", "served" → "available", "relay" → "pass on", "geometry" → "layout / order", "deterministic" → "exact", "whitelisting" → "trusted", "backend" → "server", «سمت سرور» → «سرور», «هندسه» → «چیدمان / ترتیب». `connections_lbank_body` (FA) also said the opposite of its English — "saving a key means the exchange verified it" — and now agrees with it. |
| One description per feature | `core/designsystem` `feature_<id>_body` ×28; `MenuCatalogue.kt`, `AppSurfaces.kt`, `ToolsScreen.kt` | The three families (`menu_*_body` ×7, `surface_*_body` ×24, `tools_*_body` ×10) collapse into one string each, cut to one line. The old keys are deleted; parity would fail if one came back. Menu rows never carried a separate "note"; the description *is* the one line. |
| Digits through one place | `core/common/NumberStyle.kt` | `fixed`, `grouped`, `integer`, `percent`, all `Locale.US`. `MarketNumberFormatter`, the chart axis (`ChartViewport.formatPrice`, `CoineProChart.formatPrice`), the DOM ladder labels and the calculators' `TraderToolsFormat` now call it; the six private copies of `String.format(Locale.US, …)` are gone. Tabular figures come from the font itself (IRANYekanX Latin digits share one advance — gated since Phase 0), so no `tnum` feature is set. |
| Rasad subtitle | `HomeScreen.kt` `AssistantCard`; `home_agent_subtitle` | EN "your market briefing", FA «گزارش بازار تو», under the name on the home card. |

## Not done, and why

| Item | Reason |
| --- | --- |
| §G "Brand = CoinePro" | Owner chose *Pro Chart / پرو چارت* in Phase 0; the lint enforces that spelling, not the glossary's. |
| English as default locale | Owner's standing rule: Persian is the default. The lint enforces parity both ways, which is what the rule was for. |
| Title-case detection in EN | Not linted: "Sharpe", "LBank", "MetaTrader" and every symbol are proper nouns, and a rule that cannot tell them from a title-cased label would fail on every list. |
