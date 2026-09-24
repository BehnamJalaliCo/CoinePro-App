# RUN WEB — resume

## Where the run is

**The web version is the phone app.** Every screen, from the phone's own Kotlin, compiled for the
browser (`CHECKLIST.md`). The phone is unchanged: suite and goldens green, release builds.

It installs as an app with the phone's icon and opens with no network. The relay the server needs
for signed-in screens and news photos is written and tested in `web/relay/`.

**Live on `pro-chart.com/terminal/` since 2026-09-24** (5.10.1), the relay on the server's private
network, Google sign-in registered for the page. What is left is only B5 (Web Push while the tab is
closed), if it is wanted. See `BLOCKED.md`.

**A release reaches readers by itself.** Every push to main republishes `web-latest`, the server
deploys it as in `web/relay/README.md`, and `sw.js` fetches every file from the network first, so the
next page load carries it.

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
