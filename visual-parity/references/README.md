# Reference packs moved

Packs now live at
[`docs/design/reference/tradingview-android/<versionName>/`](../../docs/design/reference/tradingview-android/),
versioned by TradingView's own app version rather than by capture date — a pack is a statement
about one release of somebody else's software, and their version is the thing that makes two packs
different.

This directory is still read by `verify_reference_manifest.py` so that a pack filed here before the
move is not orphaned. Nothing new should be put in it.
