# Brand master

`coinepro-lockup-master.png` — the horizontal lockup as the owner supplied it: 1672×941, RGBA,
genuine transparency, mark and wordmark on one canvas.

Every brand raster the app ships is produced from this file by
`scripts/design/build-brand-lockup.py`, and nothing under `src/main/res` is hand-edited. A new
density or a new size is a re-run rather than a re-cut, and `--check` proves the committed files are
what the script produces rather than asking anyone to take it on trust.

| Asset | Where it goes | Crop from the master |
| --- | --- | --- |
| `coinepro_mark` | `core:designsystem` | `(81, 223, 522, 711)` |
| `coinepro_wordmark` | `core:designsystem` | `(540, 401, 1607, 591)` |
| `ic_launcher_foreground` | `app` | the mark, re-fitted |
| `ic_launcher_background` | `app` | generated, not cropped |
| `ic_launcher_monochrome` | `app` | the mark's silhouette, with the seam cut in |

Heights are held at 104dp and 35dp at mdpi and scaled ×1.5/2/3/4; widths follow the artwork's own
aspect, because a brand mark squeezed a percent to hit a round number is a brand mark that is wrong.

## Two outputs are not straight crops

**The wordmark's silver is remapped.** In the master, "Coine" sits at luminance 232 in a flat top
face while "Pro" is genuinely metallic — so under the mark it reads as white text beside gold rather
than as two halves of one name. The script histogram-matches its silver onto the mark's own, which
is "make it the silver of the C" done by measurement instead of by eye. The gold is untouched and so
is every edge in the file.

**The launcher icon is rebuilt.** The mark is fitted by the radius that holds 98% of its ink, bounded
so nothing leaves the 72dp viewport, and centred on its ink centroid rather than its bounding box —
the mass sits at 44% of the height, so a box-centred mark floats high. The background is a fifteen-
level radial rather than the flat slab it used to be, and the monochrome layer is its own asset with
a seam cut where the gold meets the silver, because a flat-tinted layer otherwise loses the crossing
entirely.

This directory is not packaged into the APK — a directory outside `src/main` is not a source set.

**Still worth asking for:** a vector original. This master is high enough resolution for every
density the app ships, but the Play Store listing and any print use would be better from an SVG or
AI file than from a 1672px raster.
