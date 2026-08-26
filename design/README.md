# Where every icon in this app comes from

Nothing under `design/` is packaged. These are the sources; the build scripts convert the ones the
app actually needs into `core/designsystem/src/main/res/drawable`, and that conversion is the only
thing that reaches the APK. Adding a market is therefore a command, not a design task, and six
hundred unused vectors cost nothing.

Two scripts do the work:

| Script | What it does |
| --- | --- |
| `scripts/design/build-symbol-logos.py` | Merges the instrument-logo archives, converts them, and writes both the drawables and `AssetLogoTable.kt` |
| `scripts/design/svg-to-vector.py` | The converter itself — deliberately strict, refusing anything Android's vector format cannot represent faithfully rather than approximating it |
| `scripts/design/compare-symbol-logos.py` | Renders every candidate for a symbol side by side on both grounds, which is how the preference order below was decided |

---

## Instrument logos — `asset-logos/`

Four archives, merged in a recorded preference order. None of them wins outright, which is why the
order is measured rather than asserted.

| Archive | Count | Licence | Role |
| --- | --- | --- | --- |
| `crypto-icons/` | 483 | `cryptocurrency-icons`, CC0 | **Leads.** Converts at 478/483, and its flatter marks stay legible at 24dp where more detailed sets turn to mush |
| `binance-icons/` | 563 + 58 | MIT (`VadimMalykhin/binance-icons`) | Fills the symbols the first lacks. Converts at only 352/563 — the set leans on `clip-path`, which Android cannot express |
| `tv-logos/` | 96 + 20 + 4 | TradingView's published symbol logos | Gap-filler for the 2023-24 listings both vector packs predate: ARB, SUI, PEPE, SEI, TIA, WLD, ONDO |
| `binance/` | 244 | Binance raster | Last resort. Four symbols ship from here, each argued for by name in the build script |

`SYMBOL-LOGO-MANIFEST.txt` records which archive every shipped symbol came from. It is generated —
if it disagrees with the drawables, the script did not run.

**The one thing to know before touching the converter:** SVG lets an arc's two flag arguments run
together with the number after them (`a4.4 4.4 0 00.796-1.815` is three arguments, not one) and
Android's path parser does not accept that packing. It does not report an error either — it reads
some other number as the flag and sweeps the arc backwards, which shows up as a white blade across
the artwork. A third of this archive is written that way. `expand_arc_flags` re-emits every arc with
its arguments separated, and refuses a malformed one rather than half-reading it.

## Forex flags and metals — drawn, not archived

`CoineProPairLogo.kt` draws the twenty currencies the MT5 feed quotes, and letters the four precious
metals. Nothing is shipped as artwork.

At the size these appear — 42dp for the base and half that for the quote — a national flag is four
coloured shapes and no more, so drawing them costs the APK nothing, stays sharp at any size, and is
unambiguously original. They are simplified deliberately and are not accurate flags; nothing here
should be reused as one.

## UI icons — `ui-icons/`

| Set | Count | Licence | Status |
| --- | --- | --- | --- |
| `phosphor-regular/`, `phosphor-fill/` | 38 in use | MIT | The app's icon family |
| `tradingview/` | 23 | TradingView | Chart-toolbar glyphs — magnet, ruler, crosshair, replay. **Unused today**, and correctly so: they name tools the app does not yet have. They become the right set the moment the chart lands |

There is no Binance UI icon set here and there is not going to be one: Binance publishes their coin
logos and not their interface icons — `binance.com` answers 202 to a scripted fetch and
`public.bnbstatic.com` answers 403. What makes an app look like theirs is the palette, the density
and the typography rather than the icon files, and those are ours to set.

## Brand — `core/designsystem/brand/`

The wordmark and mark live with the design system rather than here, because they are one supplied
master rather than an archive to choose from. See that directory's own README.
