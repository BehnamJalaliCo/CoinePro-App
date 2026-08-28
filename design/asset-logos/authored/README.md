# Authored marks

Everything else under `design/asset-logos` is vendored: somebody else drew it and the archive
records who. These four are drawn here, and they are here rather than in `tv-logos/country`
precisely so that the vendored set stays exactly what the vendor shipped.

## The flags

`de.svg` and `fr.svg`. TradingView's country set — which is where every other flag in this app comes
from — does not include either, and the app quotes DAX and CAC. Both flags are three plain bands, so
there is nothing to reproduce: a national flag's design is not anybody's copyrighted artwork, and
these are the geometry, not a copy of somebody's drawing of it.

They are drawn in the vendor's palette rather than in the official flag colours — `#EF5350` and not
`#DD0000`, `#FDD835` and not `#FFCE00`, `#2A2E39` and not black. That is deliberate and it is the
whole reason they are hand-made rather than downloaded: a row of market rows shows these beside
twenty-seven vendored flags, and one flag in true colours among twenty-seven softened ones reads as
the odd one out. Matching the set matters more than matching the flag.

Same 18×18 viewBox, same `shape-rendering="crispEdges"`, same band order and proportions as the
vendored tricolours (`ru.svg`, `it.svg`), so they convert through the same pipeline and land at the
same weight.

## Adding another

Run the converter as for any other archive:

    python3 scripts/design/svg-to-vector.py --set authored --prefix asset_flag_ de fr

Then add the code to `ARTWORK` in `CoineProPairLogo.kt` and, for an index, to `INDEX_COUNTRY` in
`SymbolArtwork.kt`. A code in either table with no drawable is a crash; a drawable with no entry is
a lettered token where a flag was available.
