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

The order is the owner's: **crypto comes from Binance, and TradingView fills whatever Binance does
not carry.**

| Archive | Shipped | Licence | Role |
| --- | --- | --- | --- |
| `binance-icons/` | 520 | MIT (`VadimMalykhin/binance-icons`) | **Leads** |
| `crypto-icons/` | 280 | `cryptocurrency-icons`, CC0 | The long tail of small caps neither of the others draws |
| `tv-logos/` | 23 + 24 | TradingView's published symbol logos | Gap-filler, mostly the 2023-24 listings both vector packs predate. Also the country and metal sets, whole |
| `binance/` | 3 | Binance raster | TON, WIF and ARB, each argued for by name in the build script |

**Getting Binance to lead took work on the converter rather than a change of mind.** The set
converted at 352 of 563 at first, blocked by two things that turned out to be surmountable:

- 83 files carry a `clip-path` covering the entire canvas, which crops nothing. The converter now
  recognises a no-op clip and drops it, while still refusing one that really crops.
- 152 files use gradients. Android's vector format supports gradients, so they are carried across
  rather than flattened — flattening a brushed-metal coin to a flat disc passes a file listing and
  is obviously wrong beside the original.

`SYMBOL-LOGO-MANIFEST.txt` records the archive every shipped symbol came from, and the twenty whose
artwork was too detailed to read at icon size and fall back to a lettered token. It is generated —
if it disagrees with the drawables, the script did not run.

### Three rules the script follows that are not obvious

**Oversize falls through, it does not drop.** When an archive's artwork for a symbol is past the
size limit, the next archive gets a turn. Binance draws DOGE as a detailed coin well over the
limit; without this the app showed a lettered "D" while a clean flat one sat unused one archive
along.

**One symbol is pinned, and only because the artwork is wrong.** TradingView's `arb.svg` draws
Arweave's "AR" mark, not Arbitrum's hexagon. No ordering fixes a file that contains the wrong coin,
so `OVERRIDES` pins it. That table is for errors, never for preferences — a preference belongs in
the source order where it applies to everything.

**One bad file must not take the batch with it.** A crash — as opposed to a refusal — used to kill
the whole run, and the cost was not the one icon: 280 perfectly good ones never got written, which
showed up as a suspiciously small archive rather than as an error.

### The thing to know before touching the converter

SVG lets an arc's two flag arguments run together with the number after them (`a4.4 4.4 0
00.796-1.815` is three arguments, not one) and Android's path parser does not accept that packing.
It does not report an error either — it reads some other number as the flag and sweeps the arc
backwards, which shows up as a white blade across the artwork. A third of these archives is written
that way. `expand_arc_flags` re-emits every arc with its arguments separated and refuses a malformed
one rather than half-reading it.

## Forex flags and metals

The `tv-logos/country` and `tv-logos/metal` sets, converted like everything else — twenty flags and
four bullion bars. `CoineProPairLogo.kt` composes a pair from them: base in front, quote behind at
half the size, with a notch between.

They are built by the same script rather than by a separate command, because `--clean` deletes every
asset drawable and a set the script does not build is a set it would silently destroy.

## UI icons — `ui-icons/`

| Set | Count | Source | Status |
| --- | --- | --- | --- |
| `nav/` | 5 outlines | Bybit and OKX | **The bottom bar.** Both weights ship; see below |
| `phosphor-regular/`, `phosphor-fill/` | 38 in use | Phosphor, MIT | Everything else |
| `tradingview/` | 23 | TradingView | Chart-toolbar glyphs — magnet, ruler, crosshair, replay. **Unused**, and correctly so: they name tools the app does not have yet. They become the right set the moment the chart lands |

### The bottom bar is not Phosphor, and that is deliberate

It is the first thing anyone sees and the only chrome present on every screen, so the owner asked
for it to come from the exchanges themselves. It does:

| Destination | Glyph | From |
| --- | --- | --- |
| خانه | house | Bybit |
| سیگنال‌ها | candlesticks | OKX (`okds-candlestick`) |
| هوش | sparkle | OKX (`okx-trading-ai`) |
| ابزارها | controls | OKX (`okds-hamburger-setting`) |
| فعالیت | bell | OKX (`okds-bell`) |

Four of the five are one family, and the fifth was chosen to sit with them rather than because it
was the only house available — it was, but it also happens to share their grid, their stroke weight
and their corner radius. That shared geometry is the whole point: five icons read as a bar only when
they look drawn by one hand, and a set assembled one-icon-at-a-time from four vendors would not.

**The filled weight is derived, not found.** Only OKX publishes both weights of the sparkle. The
other four are generated by `scripts/design/build-nav-icons.py`, which removes each glyph's counters
— its interior contours — leaving the solid shape the outline was cut from. That is not an
approximation: these are font conversions, and an outline glyph in a font *is* the solid shape with
its counters wound backwards inside it. The script explains the containment test it uses to tell a
counter from a candle's wick.

The archive stores the vendor outlines untouched. The `_fill.svg` files beside them are generated
and carry a header saying so; delete them and re-run the script and you get the same ten drawables.

**There is no Binance set in `nav/` even though Binance was named first.** Their published two-weight
family covers wallet, spot, futures and account — none of which is one of these five, and Binance's
own home, bell and tools glyphs exist only inside their mobile app, which no scrape reaches. Where
they had the right icon they were used: the coin logos above are theirs.

## Brand — `core/designsystem/brand/`

The wordmark and mark live with the design system rather than here, because they are one supplied
master rather than an archive to choose from. See that directory's own README.
