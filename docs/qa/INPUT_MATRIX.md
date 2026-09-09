# Input matrix — the chart on a tablet

§4.3 of the plan: touch, stylus, mouse, keyboard and trackpad on the chart page. Each row is an action a reader takes; each cell says how that input reaches it and which test pins it. «—» is an input that has no path to the action, on purpose or not yet, and the last column says which.

The chart's pointer code is in `chart/ui/.../CoineProChart.kt` (the desk handler for hover, wheel and secondary press sits before the touch handlers) and the keyboard map in `feature/chart/.../ChartShortcuts.kt`. Compose delivers a stylus as a `PointerType.Stylus` pointer and a trackpad as a mouse, so the desk handler treats mouse and stylus alike for hover and every touch gesture works unchanged from a trackpad's click-and-drag.

| action | touch | stylus (S Pen) | mouse | keyboard | trackpad | pinned by |
| --- | --- | --- | --- | --- | --- | --- |
| pan in time | one-finger drag | drag | primary drag | ← → step one bar (replay) | click-drag; two-finger scroll is a wheel | `ChartCanvasGesturesTest`, `ChartKeyboardTest` |
| zoom in time | pinch | — (no pinch) | wheel at the cursor | `+` `=` `-`, on the row and the pad | two-finger pinch arrives as ctrl+wheel → price zoom; two-finger scroll → time zoom | `ChartDeskPointerTest`, `ChartKeyboardTest` |
| zoom the price scale | drag on the price gutter | drag on the gutter | ctrl + wheel | — | ctrl + two-finger scroll | `ChartDeskPointerTest` (wheel), gutter drag in `ChartCanvasGesturesTest` |
| read a bar (crosshair) | long press, then drag | **hover** (no press) | **hover** | — | hover | `ChartDeskPointerTest` «a resting pointer reads the chart and leaving clears it» |
| keep a reading | long press and lift | secondary button (barrel) | right-click | — | two-finger click | `ChartDeskPointerTest` «the secondary button reads the chart and keeps the reading» |
| axis menu | long press on the gutter | barrel button on the gutter | right-click on the gutter | — | two-finger click on the gutter | desk handler (secondary press in the gutter → `axisMenu`) |
| place a drawing | tap the anchors | tap the anchors | click the anchors | Alt+H / Alt+V arm the two line tools | click | `ChartCanvasGesturesTest`, `ChartKeyboardTest` |
| close a polyline | tap the first anchor | same | same | Esc cancels | same | `ChartCanvasGesturesTest` |
| move a handle | drag the handle | drag | drag | — | drag | `ChartPointerAndProfileTest` |
| constrain a handle (snap to angle) | long press the handle | long press | long press (primary held) | — | long press | `ChartPointerAndProfileTest` |
| erase | eraser tool tap; long press erases the object | same | same | — | same | `ChartCanvasGesturesTest` |
| undo / redo | rail button | rail button | rail button | Z, Shift+Z, Y | rail button | `ChartKeyboardTest`, `ChartHistoryTest` |
| pick a timeframe | the interval row | same | same | 1–6 (M1 M5 M15 H1 H4 D1, by name) | same | `ChartKeyboardTest` |
| replay play/pause | the replay bar | same | same | Space | same | `ChartKeyboardTest` |
| freehand | pen tool, one finger | pen tool — **pressure and tilt are not read** | pen tool | — | pen tool | `ChartCanvasGesturesTest` (freehand path) |

## What is deliberately different by input

- **Hover only exists for a hovering pointer.** A finger cannot hover, so the crosshair on touch needs the long press; a stylus or mouse gets it free, and leaving the plot clears it. The reading a touch long-press keeps and the reading a right-click keeps are the same object (`Crosshair`).
- **The wheel zooms about the cursor**, not the centre: the bar under the pointer stays under the pointer (`ChartViewport.zoomedBy(factor, focal)`), which is what every desktop terminal does and what the pinch already did.
- **Keys are down-only.** Android delivers down and up; acting on both would step two bars per press.
- **Right-to-left does not mirror time.** ← and → are back and forward *in time*, and the time axis runs left to right in Persian as in English (see `RTL_TABLET.md`).

## Not covered, and why

- **Palm rejection, stylus pressure, `MotionEventPredictor`.** Compose exposes pressure but the freehand tool draws a fixed-width stroke; prediction is an `androidx.input` dependency not in the cache. Both need a Galaxy Tab with an S Pen to judge — listed in `docs/engineering/REPORT.md` §4 as device work.
- **Trackpad two-finger pinch.** ChromeOS delivers it as ctrl+wheel and Android 14 as a scale gesture on the mouse pointer; the first is covered (price zoom), the second reaches the pinch handler untested off-device.
- **TalkBack focus order on the rail and the panes.** The rail's items carry `Role.Tab` and a content description; the order across rail → plot → tools column has not been walked with a screen reader. Device work.
