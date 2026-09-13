# RUN Σ — the second half, line by line

`✅` means done and proven by the evidence beside it. `❌` means not yet — including anything that
was narrowed, per R3. **`⏳ owed to device` is neither**: it means the code is in and the only thing
that can finish the claim is a person with a phone, and it stays that way until the owner's
recording lands.

The Frame column is not optional and is never blank. Where a claim is not the kind of thing a still
frame can carry — a gesture, a haptic, a spring — the cell says so and names the device-independent
proof instead. That rule came out of run Ω-FIX, where a ✅ with a sentence behind it turned out to be
false on the device in every frame of two recordings.

| # | The line | State | Evidence | Frame |
|---|---|---|---|---|
| S1 | Horizontal pinch on the plot zooms time; vertical does too with auto-scale on; the price gutter scales price only | ✅ code, ⏳ owed to device | `PinchZone` + `pinchZoneOf` route by **start location only**; the plot's zoom is the Euclidean distance ratio at any angle. The bug was a per-frame `1.0025.pow(Δspan)` behind a one-per-cent dead zone — arithmetically unreachable at any human finger speed. `ChartPinchTest` (6) injects two real pointers; `PinchZoneTest` (6) walks every point of the canvas | **A gesture, so not a still.** Six injected-pointer tests, one of them through the whole page to prove nothing above the plot steals the second finger. The owner's recording is what closes it |
| S2 | Buy/Sell markers carry labels per the density rules; goldens at 4/8/16 dp | ✅ | `ChartMarker.label` + `TradeSide.action`; `SignalMarkers.detailFor` — label at ≥ 12 dp a bar, triangle 6–12, one per ten-bar swing below 6; `sizeDpFor` gives 6/8/10 dp by strength; labels skip the legend plate, the plot's edges and each other. Per-study «با برچسب / فقط مثلث / خاموش» in the Explain sheet. `SignalMarkersTest` (13) | `sigma0-markers-{16,8,4}dp-phone-fa.png`, the same three on a tablet, `sigma0-markers-16dp-phone-en.png`, `sigma0-markers-triangles-phone-fa.png` |
| S3 | BYO Script end to end | ❌ | Σ1 | — |
| S4 | `docs/DOCTRINE.md` with D1–D10, each with a CI gate | ❌ | Σ2 | — |
| S5 | Retention loop | ❌ | Σ3 | — |
| S6 | Account value: sync, export/import, guest migration | ❌ | Σ4 | — |
| S7 | Community scripts | ❌ | Σ4 | — |
| S8 | All of the above on tablet, parity matrix 100 % | ❌ | Σ5 | — |
| S9 | This document, `REPORT.md`, `BLOCKED.md` | ✅ for Σ0 | The Σ0 rows above and `REPORT.md`'s Σ0 section | — |

## Σ0, item by item

The hotfix the owner asked to be shipped immediately, so the next recording can settle S1.

| Σ0 item | State | Evidence |
|---|---|---|
| Gesture routing by **start location only**; finger orientation is never a discriminator | ✅ | `pinchZoneOf(frame, timeAxisTop, start)`. `PinchZoneTest.every point on the canvas resolves to exactly one zone` sweeps the whole canvas rather than three corners, because a routing rule with a gap in it is a gesture that does nothing — which is what was reported |
| Plot pinch: `zoom = currentDistance / previousDistance`, Euclidean, any angle | ✅ | `PointerEvent.pinchSpan()` — the mean distance of the pressed pointers from their centroid. The old `axisSpan(vertical)` threw the direction away twice over: once into a per-axis scalar and once into a power function |
| `barSpacing *= zoom`, clamped 0.5..50 px | ✅ | `ChartViewport.zoomedBy` then `clampedToBarSpacing`, which already held `MIN_BAR_SPACING_PX = 0.5f` and `MAX_BAR_SPACING_PX = 50f` |
| Anchored so the bar under the centroid stays under it | ✅ | The `focal` share passed to `zoomedBy`. `ChartPinchTest.the bar under the fingers stays under the fingers` bounds the drift, and `where the fingers are decides what stays on screen` proves the anchor by contrast — the same pinch at the left and at the right cannot produce the same window |
| Simultaneous pan by the centroid delta | ✅ | Unchanged: `detectTransformGestures` still owns the pan and this handler still consumes nothing |
| Vertical pinch on the plot zooms time with auto-scale on; scales both with it off | ✅ | The `PinchZone.PLOT` branch zooms time always and adds `priceZoomedBy(ratio)` only when `priceZoom != 1f` and `priceBarLock` is off — the lock already moves the price inside `zoomedBy` and doing it twice would double the rate. `ChartPinchTest.fingers moving apart vertically on the plot also show fewer bars` |
| Two pointers in the price gutter: price changes, bar count does not | ✅ | `ChartPinchTest.fingers in the price ladder scale the price and leave the bars alone` |
| Nothing above the plot may consume a second pointer | ✅ | `ChartPinchTest.nothing stacked above the plot steals the second finger` drives the **whole page** through the root — no test tag to aim at — and asks the controller what window it ended on |
| A «horizontal pinch» Macrobenchmark scenario | ✅ code, ⏳ owed to device | `ChartFlingBenchmark.horizontalPinch` and `verticalPinch`, with an explicit `performMultiPointerGesture` path kept clear of both gutters. `UiObject2.pinchOpen` could not stand in: it pinches against the object's bounds and spends part of its travel on the price ladder. The module's own dependencies are not in this container's offline cache, so it has never compiled here — see `BLOCKED.md` |
| Marker = triangle + «خرید» / «فروش», 11 sp Medium, 4 dp pill | ✅ | `drawMarkers` / `drawMarker` in `CoineProChart`; the word is the stage colour on a plate in the study's own, because four characters of one hue on a plate of the same hue is a contrast ratio of about two |
| Buy label under the bar low, sell label above the bar high | ✅ | The label goes on the far side of the glyph from the candle, so it never lands between the mark and the bar it is about |
| Label at ≥ 12 dp a bar; triangle only at 6–12; one per swing below 6 | ✅ | `SignalMarkers.detailFor` + `thin`. `SignalMarkersTest.the rule is monotonic` walks the whole range: a threshold entered the wrong way round passes every named case and fails that one |
| Labels never overlap the last-price label, the legend plate or each other | ✅ | The plot's own bounds (the price tag is in the gutter, which is off the plot), a legend guard rectangle, and a list of claimed rectangles that grows as labels are placed. The older signal keeps its word and the newer one loses it, which is the only tiebreak that does not leave both illegible |
| Marker size 6/8/10 dp by signal strength | ✅ | `SignalMarkers.sizeDpFor(strength)`, fed by `SignalEvent.strength` through `ChartMarker.strength` |
| Per-study setting: labels / triangles only / off, default labels | ✅ | `MarkerStyle` + `ChartUiState.markerStyles` + `ChartController.setMarkerStyle`; three chips at the foot of the Explain sheet. Session-scoped, beside `hiddenIndicators`, for the same reason: it is «what I want to see right now», not how the study is set up |
| Goldens at barSpacing 4, 8, 16 dp on phone and tablet | ✅ | `SignalMarkerProofTest` — seven frames |

## What Σ0 does **not** claim

The pinch is proved against an injected event stream, which is the strongest thing this container
can do and is not the same as a thumb. Until the owner's thirty seconds land, S1 reads
**⏳ owed to device** and not ✅.
