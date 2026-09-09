# Right-to-left on the tablet — the decision

Persian is the default locale, so every tablet layout is designed mirrored first and checked unmirrored second. Three things are pinned, one of them against the mirror.

| element | Persian (RTL) | English (LTR) | why |
| --- | --- | --- | --- |
| navigation rail | **right edge** (the start edge) | left edge | The rail is the first thing on the reading side. `CoineProApp` puts it first in a `Row` and names no side; the layout direction places it. A rail pinned to a physical edge would be on the wrong side in one of the two languages. |
| list ⇄ detail | list on the **right**, detail on the left | list left, detail right | `CoineProListDetail` is a `Row` in reading order: the list is what the reader scans, the detail is where the eye goes next. |
| chart price scale | **right**, unmirrored | right | The TradingView convention, kept on purpose. Every chart a trader has ever compared this one against has its prices on the right and its time running left→right; mirroring the plot would make a screenshot of ours unreadable beside a screenshot of theirs. The plot is a canvas drawn in screen coordinates, not a mirrored layout: time runs left→right and the scale sits on the right whatever the locale (`chart-fa-1280.png` shows it in Persian), and the ← → keys mean back and forward in time, not on screen. |
| tools column and readings panel | tools on the right of the plot, readings on the left | tools on the left, readings on the right | `ChartWorkbench` is a `Row` with the tools first, so they take the start edge: the palette sits between the rail and the plot on the reading side, the readings on the far side, so the hand crosses the plot as little as possible. |
| sheets → dialogs (Expanded) | centred, ≤ 560 dp | centred, ≤ 560 dp | Centred. On a book-posture foldable the centre *is* the hinge; parking the dialog in one half is open work, listed in `docs/engineering/REPORT.md` §4. |

Evidence: `GoldenScreenshotTest` renders the watchlist and the menu in both directions on the phone (`watchlist-en-411`, `menu-en-411-dark`) and every tablet golden in Persian (`*-fa-840`, `*-fa-1280`); `ScreenshotRenderTest.tabletShell` shows the rail on the right; `ChartKeyboardTest` pins the arrows' meaning.

What is not rendered: an English tablet golden. The layout is the same composable under the other direction and the phone's English golden already proves the mirror; a tablet English set is a follow-up when a reader asks for it, not a gap in the decision.
