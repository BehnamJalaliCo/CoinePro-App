# RUN WEB — resume

## Where the run is

**The web version is the phone app.** Every screen, from the phone's own Kotlin, compiled for the
browser (`CHECKLIST.md`). The phone is unchanged: suite and goldens green, release builds.

It installs as an app with the phone's icon and opens with no network. The relay the server needs
for signed-in screens and news photos is written and tested in `web/relay/`.

**Waiting on the server agent** to put the bundle at `site/terminal` (B1) and run `web/relay`
(B2). **Waiting on the owner** for the Google origin (B4) and, if wanted, Web Push keys (B5). See `BLOCKED.md`.

## When a phone screen changes

Nothing to do. The next `:web:terminalBundle` compiles the changed source. If it fails, the fault
is either a new Android API with no browser version in `web/src/shims/kotlin`, or a new Retrofit or
Hilt shape `web/tools/share_sources.py` does not read yet. Add it there, never in the phone's code.

## How to see it locally

```
./gradlew :web:terminalBundle
python3 <a static server for web/build/terminal at /terminal/, relaying /api/* to pro-chart.com,
         /up/tradeyar/* and /up/coineprofx/* to the two backends, and /api/img?url= to the url>
```
