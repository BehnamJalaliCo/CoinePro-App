# Brand master

`coinepro-lockup-master.png` — the horizontal lockup as the owner supplied it: 1672×941, RGBA,
genuine transparency, mark and wordmark on one canvas.

Every brand raster in `src/main/res` is generated from this file and nothing is hand-edited, so a
new density or a new size is a re-run rather than a re-cut. The regions are:

| Asset | Crop from the master |
| --- | --- |
| `coinepro_mark` | `(81, 223, 522, 711)` |
| `coinepro_wordmark` | `(540, 401, 1607, 591)` |

Heights are held at 104dp and 35dp at mdpi and scaled ×1.5/2/3/4; widths follow the artwork's own
aspect. `ic_launcher_foreground` is the mark fitted inside the adaptive icon's 72-of-108 safe zone,
because a launcher may mask the canvas to a circle and only the middle two thirds survives every
shape.

This is not packaged into the APK — a directory outside `src/main` is not a source set.

**Still worth asking for:** a vector original. This master is high enough resolution for every
density the app ships, but the Play Store listing and any print use would be better from an SVG or
AI file than from a 1672px raster.
