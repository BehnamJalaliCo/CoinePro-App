# The TradingView icon set

119 vectors, in three families. All of them came out of the Pro-Chart bundle, where they had been
extracted once already and stored as `{viewBox, inner}` pairs injected with
`dangerouslySetInnerHTML` — a form that suits a web app and is useless to this one.
`scripts/design/extract-tv-icons.py` turns them back into files the converter can read.

| Prefix | Count | What |
| --- | --- | --- |
| *(none)* | 22 | Interface: camera, bell, search, lock, ruler, magnet, crosshair, calendar, star, trash … |
| `tool_` | 79 | The drawing tools — every fibonacci, gann, elliott, pattern and projection tool |
| `chart_` | 18 | Chart types: candles, hollow, heikin, bars, line, area, baseline, step, renko, range, line-break, kagi, point-and-figure … |

They convert to `tv_*` drawables. Most are `currentColor` line art, so every call site must pass a
tint — they are emitted opaque black and will be invisible on the dark stage otherwise.

## One icon was dropped

`eye` is a chevron, not an eye: its path is `M1 8l8.5-6.5L18 8`, an upward caret. Whatever went
wrong went wrong upstream in the original extraction. Phosphor's `icon_eye` is used instead. Its
partner `eye_off` is fine and was kept.

## What is not here

The interface icons are chart-shaped: they name tools the app does not have yet. They are converted
and shipped now because the archive is where they belong and because converting them is what proved
the converter could handle them — five needed capabilities it did not have, including rounded
rectangles and a viewBox whose origin is not at zero. Nothing outside the chart should reach for one
of these when Phosphor has an equivalent.
