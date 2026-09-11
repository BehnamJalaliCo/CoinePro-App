# Device proofs — the commands, exactly

Everything in this file needs a phone or a tablet with USB debugging on; nothing in the build
environment has a display or a GPU. Each block names the artefact it produces and the number the
plan judges it by. Paths and class names are the repository's, not the plan's guesses.

## 1. Chart fling benchmark (item 4)

```bash
# The benchmark module has its own build type, `benchmark`, and the class lives in
# com.coinepro.benchmark (not com.coinepro.app.benchmark).
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.benchmark.ChartFlingBenchmark
# Output: benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/*.json
# Read frameDurationCpuMs P95 and frameOverrunMs for flingAcrossHistory, pinchZoom, panAndHold.
# Targets: P95 ≤ 8 ms on a phone, ≤ 12 ms on a tablet in the four-chart layout, 0 overrun.
python3 scripts/quality/check-benchmark-thresholds.py   # applies the thresholds to the JSON
```

## 2. Jank count for a 30-second session by hand (item 4)

```bash
adb shell dumpsys gfxinfo com.coinepro.app reset
# … pan, pinch and fling the chart for 30 seconds …
adb shell dumpsys gfxinfo com.coinepro.app | grep -E "Total frames|Janky frames|90th|95th|99th"
# Target: Janky frames 0.
```

## 3. 120 fps recording beside TradingView (item 4)

```bash
# Android's screenrecord caps at 60; scrcpy records the display at its own rate.
scrcpy --max-fps 120 --video-bit-rate 24M --record prochart-chart.mp4
# Same symbol, same timeframe, same gestures in TradingView, recorded the same way.
```

## 4. NamaScript on the device (item 5)

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.app.NamaScriptDevicePerfTest
adb logcat -d -s NamaScriptPerf
# or: adb shell run-as com.coinepro.app cat files/../files/namascript-perf.json  (external files dir)
# Targets on a Pixel 6a: compile < 50 ms, evaluate < 40 ms, realtime append and tick < 2 ms.
# JVM, for scale: 1.4 / 168 / 6.7 / 6.2 ms (docs/engineering/REPORT.md, item 5).
```

The test prints the figures and asserts none of the targets, so a phone that misses one still
reports its number.

## 5. The screenshot matrix on four devices (run E)

```bash
# Once per device — Pixel 6a, Pixel Tablet, Galaxy Tab S9 Ultra, Pixel Fold (open, then closed):
adb shell settings put system font_scale 1.0
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.app.DeviceProofTest
adb pull /sdcard/Android/data/com.coinepro.app/files/device-proof docs/qa/screenshots/device/
# 24 frames per device: six scenes × dark/light × fa/en, named
#   <scene>-<model>-<fa|en>-<dark|light>.png, with manifest.json (model, API, px, dpi, sw-dp).
# The scenes are ToolsProofTest's — the arranged pane legend, the selection toolbar over a trend
# line, the favourites strip, the rail with the last-used tool first, MACD's inputs, RSI's pane
# controls — so each device frame has a Robolectric twin in docs/qa/screenshots/4.67/.
```

The theme and the language are set inside the composition (the test provides both), so the
device's own settings do not need changing between runs; the fold is two runs, one per posture.

## 6. The 30-minute Monkey soak on the tablet (run E)

```bash
bash scripts/qa/monkey-soak.sh          # 30 minutes; writes docs/qa/soak/<model>-<stamp>.log
bash scripts/qa/monkey-soak.sh 5        # a five-minute dry run first, to see the device drive
# The script prints the crash and ANR counts and exits non-zero on either; the target is 0 / 0.
# Commit the log under docs/qa/soak/.
```
