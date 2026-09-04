# TradingView Android reference packs

Empty on purpose.

A pack here is a capture of the **real TradingView Android app**, taken on the canonical device,
checksummed, and filed with the metadata in
[`../../TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md`](../../TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md).

Nothing else may go here. Not a web screenshot, not a store image, not a JPG, not a resized PNG,
not a re-render of their design in a drawing tool. A baseline invented once is a baseline trusted
forever by people who were not there when it was invented, and afterwards there is no way to tell
which numbers came from a real device and which came from somebody's afternoon.

One directory per **their** version:

    tradingview-android/
      <versionName>/
        reference-manifest.json
        watchlist-dark.png
        bottom-bar-watchlist-dark.png
        …

A new version of their app gets a new directory. The old one is never overwritten and never
deleted: it is the evidence behind every number already published.

While this directory is empty, `scripts/visual/verify_reference_manifest.py` and
`scripts/visual/compare_tradingview_reference.py` both exit 3 — `REFERENCE_MISSING` — and the
project's answer on TradingView parity is `PARITY NOT YET PROVEN`. That is the correct state, not a
broken one, and it is **not** a pass.

The seven JPGs in `../tradingview-phone/` are web captures. They were used to read intent and are
inadmissible as pixel goldens; the verifier rejects them by format.
