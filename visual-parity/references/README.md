# Reference packs

Empty on purpose.

A pack here is a capture of the **real TradingView Android app**, taken on the same device profile
as our own capture, checksummed, and filed with the metadata in
[`docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md`](../../docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md).

Nothing else may go here. Not a web screenshot, not a store image, not a JPG, not a resized PNG,
not a re-render of their design in a drawing tool. A baseline invented once is a baseline trusted
forever by people who were not there when it was invented, and there is no way to tell afterwards
which numbers came from a real device and which came from somebody's afternoon.

While this directory is empty, `scripts/visual/compare_tradingview_reference.py` reports
`REFERENCE_MISSING` for every screen and the project's answer on TradingView parity is
`PARITY NOT YET PROVEN`. That is the correct state, not a broken one.

Layout of a pack:

    tradingview-android-<versionName>-<yyyy-mm-dd>/
      reference-manifest.json
      watchlist-dark.png
      …
