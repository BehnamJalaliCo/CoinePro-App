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

## 5. Tablet screenshot matrix and the Monkey soak (run E)

```bash
# Frames on a real tablet: the Robolectric proofs in app/src/test (TabletProofTest, StudioProofTest)
# are the same scenes at the same sizes; on a device, run them through the instrumentation rig:
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.app.DeviceProofTest
# The 30-minute soak:
adb shell monkey -p com.coinepro.app --throttle 200 --pct-touch 60 --pct-motion 30 -v 9000 > monkey.log
grep -E "CRASH|ANR" monkey.log | wc -l     # target: 0
```
