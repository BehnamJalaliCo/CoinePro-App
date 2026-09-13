# RUN Σ — what changed, phase by phase

## Σ0 — the two the owner asked for first (4.81.0)

Shipped on its own, ahead of the rest of the run, because both items are things the owner has to put
a thumb on before anything else is worth building.

### S1 — the pinch, and why only one axis worked

The report was «pinch افقی روی چارت کار نمی‌کند، عمودی کار می‌کند». Both halves of that come out of
one line.

The time axis was driven by `1.0025.pow(spanX − lastX)` — the bar spacing growing by a quarter of a
per cent for every pixel the fingers opened **in that frame** — and the result was then put through a
one-per-cent dead zone. On a 120 Hz phone a finger separating at a brisk five hundred pixels a second
moves about two pixels of half-span per frame, which is a ratio of 1.005. Under the dead zone. Every
frame, for the whole gesture. To clear it you had to separate your fingers at roughly two thousand
pixels a second, which is not a gesture, it is a flinch.

The price axis, three lines below, used a plain `spanY / lastY`. The same two pixels on a
hundred-pixel span is 1.02 and sailed through. One axis worked and the other was arithmetically
unreachable, which is exactly what the device showed.

Underneath that was a second, worse thing: **the routing discriminated by the angle between the
fingers.** The horizontal branch only fired when the fingers were far enough apart *horizontally*;
the vertical branch fired anywhere on the canvas, gutter or not. So a reader pinching at forty-five
degrees drove both axes at once, and a reader pinching vertically on the candles got the price scale
when what they had asked for was more bars.

The angle between two fingers is not a statement about intent. Where they landed is. `pinchZoneOf`
now decides once, at the first touch — plot, price ladder, or date strip — and the plot's zoom is the
Euclidean distance ratio at any angle. A manual price scale follows the same ratio; an automatic one
rescales itself to the bars now visible, which is what the reference does.

Six injected-pointer tests, one of them through the whole page rather than at the chart, because the
thing that would look identical to this bug is something above the plot swallowing the second finger.

### S2 — the word beside the triangle

«برچسب خرید/فروش روی فلش‌ها.» A triangle is a direction, and an app whose whole thesis is that a
chart should say what it means cannot put an unlabelled arrow on a candle and call that saying it.

The word is the easy half. The half that needed designing is what happens at the other end of the
zoom: a label under every triangle on a year of daily bars is forty words overlapping each other and
the candles they are about, which hides more than it says. So the density rule is arithmetic, in
`:chart-core`, in **points per bar** rather than in bars — a phone and a tablet showing the same
hundred bars are showing them at very different sizes, and the question a label has to answer is «is
there room for four characters here».

| Points a bar | What is drawn |
|---|---|
| ≥ 12 | triangle + «خرید» / «فروش» |
| 6 – 12 | the triangle alone |
| < 6 | one triangle per ten-bar window, the strongest of it |

Three more rules came with it. The glyph is 6, 8 or 10 points by the signal's own strength, so a
cross that barely happened is not the same object on the glass as one that went a long way past the
line. The word goes on the far side of the glyph from the candle — under a buy, over a sell — so it
never lands between the mark and the bar it is about. And a label that would cross the plot's edge,
the legend plate, or a label already placed is dropped rather than drawn: the older signal keeps its
word, which is the only tiebreak that does not leave both illegible.

The setting is per **study** and not per chart, because the reason to silence marks is that *that
study* is noisy. Three chips at the foot of the Explain sheet: «با برچسب», «فقط مثلث», «خاموش». The
middle one is the setting people actually reach for, and offering only the other two would be a false
choice.

### What Σ0 cost

| | |
|---|---|
| Files changed | 9 source, 4 test, 1 benchmark, 3 documents |
| New tests | `PinchZoneTest` (6), `ChartPinchTest` (6), `SignalMarkersTest` (13), `SignalMarkerProofTest` (8) |
| New proof frames | 7 |
| Goldens re-recorded | none — the golden charts carry no Signal Layer, so no marker moved |
| New features | S1 is a bug; S2 is the one feature in this version |
| Owed to device | S1's acceptance, and the two new Macrobenchmark scenarios |
