# RUN WEB — resume

## Where the run is

**W1b is done and its milestone frame exists**: `CoineProChart` in Chromium on the relay's live
candles, Persian and English, desktop and phone width (`frames/`). The phone is unchanged — same
androidx graph, suite and goldens green, release builds.

**The first public page is built**: `./gradlew :web:terminalBundle` → `web/build/terminal/`. It is
a chart with an instrument, a timeframe and a language; it is not yet the workbench.

**Waiting on the server agent** to put the bundle at `site/terminal` (`BLOCKED.md` B1).

## Next, in order

1. W2 item 1: `ChartWorkbench` — tools column and readings panel. `ToolRail.kt` and `ChartIcons.kt`
   are the Android-only files it will pull into `commonMain`; W1½'s drawables come with them.
2. The forex socket (`wss://pro-chart.com/api/stream`) for ticks, with the welcome frame shown for
   crypto.
3. History paging on crypto (`has_more`/`oldest` are in the response).
4. W3, the script studio.

## How to see it locally

```
./gradlew :web:terminalBundle
python3 <any static server that serves web/build/terminal at /terminal/ and relays /api/* to pro-chart.com>
```
