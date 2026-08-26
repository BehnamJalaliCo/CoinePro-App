#!/usr/bin/env python3
"""Build every instrument logo the app ships, from the archives, in one reproducible pass.

The order is the owner's: crypto comes from Binance, and TradingView fills whatever Binance does not
carry. Run this and the drawables, the Kotlin lookup and the manifest are all regenerated together;
none of the three is hand-edited.

Getting Binance to lead took work on the converter rather than a change of mind. The set converted
at only 352/563 at first, blocked by two things that turned out to be surmountable:

* 83 files carry a ``clip-path`` that covers the entire canvas and therefore crops nothing. The
  converter now recognises a no-op clip and drops it, while still refusing one that really crops.
* 152 files use gradients. Android's vector format supports gradients, so they are carried across
  rather than flattened — flattening a brushed-metal coin to a flat disc passes a file listing and
  is obviously wrong beside the original.

``tv-logos`` then fills the gaps, which is mostly the 2023-24 listings both older packs predate:
ARB, SUI, PEPE, SEI, TIA, WLD, ONDO. ``crypto-icons`` sits last as a long tail of small caps neither
of the other two draws. Four symbols have no usable vector anywhere and ship as raster; see
``RASTER_ONLY`` — and a symbol only stays there until some vector archive learns to draw it, at
which point the vector displaces the raster automatically rather than colliding with it.

Forex flags and the four metals do not come through here at all — they are the ``tv-logos``
country and metal sets, converted by ``build-fx-logos.py``.

``scripts/design/compare-symbol-logos.py`` renders the candidates side by side on both grounds.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ARCHIVE = REPO / "design" / "asset-logos"
DRAWABLE = REPO / "core" / "designsystem" / "src" / "main" / "res" / "drawable"
MANIFEST = ARCHIVE / "SYMBOL-LOGO-MANIFEST.txt"
KOTLIN = (
    REPO
    / "core"
    / "designsystem"
    / "src"
    / "main"
    / "kotlin"
    / "com"
    / "coinepro"
    / "core"
    / "designsystem"
    / "AssetLogoTable.kt"
)

# Forex and metals. Not part of the crypto merge — these are one archive each, with no competing
# source to prefer between, and they are keyed by country rather than by ticker. They live here
# rather than in a separate command because --clean deletes every asset drawable, and a set this
# script does not build is a set it would silently destroy.
# The names are read from the archive rather than listed, so a flag `download-tv-logos.py` fetched
# is a flag that ships. The hand-written tuple was twenty entries and the archive is now twenty-nine;
# a list that has to be edited in a second place to take effect is a list that will be wrong.
def _names(subset: str) -> tuple[str, ...]:
    directory = ARCHIVE / subset
    if not directory.is_dir():
        return ()
    return tuple(sorted(path.stem for path in directory.glob("*.svg")))


PAIR_SETS = (
    ("tv-logos/country", "asset_flag_", _names("tv-logos/country")),
    ("tv-logos/metal", "asset_metal_", _names("tv-logos/metal")),
    # Equity, index and ETF marks — the instruments the LBank listing carries that no crypto icon
    # pack draws. Keyed by TradingView's own slug; `CoineProAssetLogo` maps a ticker onto it.
    ("tv-logos/equity", "asset_equity_", _names("tv-logos/equity")),
)

# In preference order. See the module docstring for why.
VECTOR_SOURCES = ("binance-icons/crypto", "tv-logos/crypto", "crypto-icons")

# Symbols the vector archives cannot serve, taken from the Binance raster archive instead. Kept to a
# named list rather than an automatic sweep: a raster among vectors is a deliberate exception and
# should have to be argued for, one symbol at a time. Each of these has a reason —
#
#   ton   no vector archive carries it at all
#   arb   drawn with a gradientTransform, which re-frames the gradient and cannot be flattened
#   sei   the same
#   wif   its vector is a 33 KB photo-like illustration, well past MAX_VECTOR_BYTES
#
# 192px covers 42dp — the largest the app draws one of these — at every density through xxxhdpi, so
# nothing here is upscaled. ARB is the exception at 64px and is soft above 24dp; it ships anyway,
# because a soft Arbitrum mark still reads as Arbitrum and a lettered "A" does not.
RASTER_ARCHIVE = "binance"

RASTER_ONLY = ("ton", "arb", "wif")

# Symbols pinned to one archive because the automatic order picks the wrong artwork for them.
#
# This is for errors, not preferences — a preference belongs in VECTOR_SOURCES where it applies to
# everything. TradingView's `arb.svg` is Arweave's "AR" mark rather than Arbitrum's hexagon, and no
# ordering can fix a file that draws the wrong coin.
OVERRIDES: dict[str, str] = {
    "arb": RASTER_ARCHIVE,
}

# Anything larger than this is an illustration rather than an icon. A 72 KB path renders as a grey
# smudge at 24dp, so it costs the APK real space to look worse than the lettered token it would
# otherwise fall back to.
MAX_VECTOR_BYTES = 12_288


def archive_symbols(subset: str) -> set[str]:
    folder = ARCHIVE / subset
    return {p.stem for p in folder.glob("*.svg")} if folder.is_dir() else set()


def resource_name(symbol: str) -> str:
    """Android resource names allow only lowercase, digits and underscore."""
    return re.sub(r"[^a-z0-9_]", "_", symbol.lower())


def convert(subset: str, symbols: list[str], prefix: str = "asset_") -> set[str]:
    """Convert one archive's symbols, returning the ones that landed."""
    if not symbols:
        return set()
    result = subprocess.run(
        [
            sys.executable, str(REPO / "scripts/design/svg-to-vector.py"),
            "--set", subset, "--prefix", prefix, *symbols,
        ],
        capture_output=True,
        text=True,
        cwd=REPO,
    )
    landed = set()
    for line in result.stdout.splitlines():
        if line.startswith("ok "):
            symbol = line.split()[1]
            landed.add(symbol)
            # The other half of the rule in convert_raster: a vector that lands displaces the
            # raster that used to stand in for it.
            (DRAWABLE / f"{prefix}{resource_name(symbol)}.webp").unlink(missing_ok=True)
    return landed


def convert_raster(symbol: str) -> bool:
    """Write one raster logo as a lossless webp drawable. Returns whether it landed."""
    try:
        from PIL import Image
    except ImportError:
        print(f"SKIP     {symbol}: Pillow not installed, raster logos not built", file=sys.stderr)
        return False
    for stem in (symbol.upper(), symbol):
        source = ARCHIVE / RASTER_ARCHIVE / f"{stem}.png"
        if source.exists():
            break
    else:
        print(f"MISSING  {symbol}: no raster in {RASTER_ARCHIVE}", file=sys.stderr)
        return False
    destination = DRAWABLE / f"asset_{resource_name(symbol)}.webp"
    # A raster and a vector of the same name are two resources with one id, and aapt refuses to
    # merge them — a build failure whose message names the file but not the cause. It happened the
    # first time TradingView's set grew to cover TON and APE, which had been raster-only: the vector
    # landed beside the raster and the whole module stopped packaging. Whichever source wins, the
    # other one's file goes.
    destination.with_suffix(".xml").unlink(missing_ok=True)
    # Lossless, because these are flat brand marks: lossy webp puts ringing around a hard edge on a
    # transparent ground, which is exactly what every one of these is.
    Image.open(source).convert("RGBA").save(destination, "WEBP", lossless=True, quality=100)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--clean",
        action="store_true",
        help="delete every generated asset drawable first, so a removed archive entry really goes",
    )
    args = parser.parse_args()

    if args.clean:
        for pattern in ("asset_*.xml", "asset_*.webp"):
            for existing in DRAWABLE.glob(pattern):
                existing.unlink()

    chosen: dict[str, str] = {}
    oversized: dict[str, list[str]] = {}
    for subset in VECTOR_SOURCES:
        remaining = sorted(
            symbol for symbol in archive_symbols(subset) - chosen.keys()
            # A symbol pinned elsewhere is skipped here even if this archive has artwork for it.
            if OVERRIDES.get(symbol, subset) == subset
        )
        for symbol in convert(subset, remaining):
            path = DRAWABLE / f"asset_{resource_name(symbol)}.xml"
            # Oversized artwork does not claim the symbol. Falling through to the next archive is
            # the whole point: Binance draws DOGE as a detailed coin well past the size limit, and
            # dropping it there would have shown a lettered "D" while a clean flat one sat unused in
            # the next archive along.
            if path.exists() and path.stat().st_size > MAX_VECTOR_BYTES:
                path.unlink()
                oversized.setdefault(symbol, []).append(subset)
                continue
            chosen[symbol] = subset


    pairs = 0
    for subset, prefix, names in PAIR_SETS:
        pairs += len(convert(subset, list(names), prefix=prefix))

    # Anything still without artwork tries the raster archive, including every symbol whose vectors
    # were all too detailed to read at icon size. A 24 KB illustration of an ape is worse than the
    # lettered token; a 192px raster of the same mark is better than both.
    for symbol in sorted(set(RASTER_ONLY) | oversized.keys()):
        if symbol in chosen:
            continue
        if convert_raster(symbol):
            chosen[symbol] = f"{RASTER_ARCHIVE} (raster)"

    dropped = [
        (symbol, ", ".join(sources))
        for symbol, sources in sorted(oversized.items())
        if symbol not in chosen
    ]

    lines = [
        "# Generated by scripts/design/build-symbol-logos.py — do not hand-edit.",
        "# symbol  source",
        "",
    ]
    lines += [f"{symbol:<12} {source}" for symbol, source in sorted(chosen.items())]
    if dropped:
        lines += ["", "# Dropped as too large to read at icon size; these fall back to the token:"]
        lines += [f"# {symbol:<10} {source}" for symbol, source in sorted(dropped)]
    MANIFEST.write_text("\n".join(lines) + "\n", encoding="utf-8")

    write_kotlin(sorted(chosen))

    by_source: dict[str, int] = {}
    for source in chosen.values():
        by_source[source] = by_source.get(source, 0) + 1
    for source, count in sorted(by_source.items()):
        print(f"{source:<22} {count}")
    print(f"{'flags and metals':<22} {pairs}")
    print(f"{'total':<22} {len(chosen) + pairs}   ({len(dropped)} dropped as oversized)")
    return 0


def write_kotlin(symbols: list[str]) -> None:
    # A ticker may contain "$" (there really is a $PAC on the archive), and inside a Kotlin string
    # literal that opens a template. Escaped rather than skipped: the symbol is legitimate and there
    # is artwork for it.
    def key(symbol: str) -> str:
        return symbol.upper().replace("$", "\\$")

    entries = "\n".join(
        f'    "{key(symbol)}" to R.drawable.asset_{resource_name(symbol)},' for symbol in symbols
    )

    KOTLIN.write_text(
        f'''package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes

/**
 * Every instrument logo the app ships, by symbol base.
 *
 * Generated by `scripts/design/build-symbol-logos.py` from the archives under `design/asset-logos`.
 * Do not hand-edit: add or remove artwork in the archive and re-run the script, so the drawables,
 * this table and `SYMBOL-LOGO-MANIFEST.txt` can never disagree about what shipped.
 *
 * Keyed on the **base** symbol, so `BTCUSDT`, `BTCUSDC` and a future `BTCUSD` all resolve to one
 * entry rather than needing three. [CoineProAssetLogo] does the stripping.
 *
 * A symbol absent from this table is not a bug — it is the ordinary case for a listing newer than
 * the archives, and the lettered token is its permanent answer rather than a placeholder.
 */
internal object AssetLogoTable {{

    @DrawableRes
    fun forBase(base: String): Int? = LOGOS[base]

    val size: Int get() = LOGOS.size

    private val LOGOS: Map<String, Int> = mapOf(
{entries}
    )
}}
''',
        encoding="utf-8",
    )


if __name__ == "__main__":
    raise SystemExit(main())
