#!/usr/bin/env python3
"""Build every instrument logo the app ships, from the archives, in one reproducible pass.

Three archives overlap and none of them wins outright, which is why this exists as a script with a
recorded preference rather than as a hand-maintained list. Run it and the drawables and the Kotlin
lookup are both regenerated; nothing here is hand-edited.

The preference order is not a guess. It was measured:

* ``crypto-icons`` converts at 478/483 and ``binance-icons`` at 352/563 — the Binance set leans on
  ``clip-path``, which Android's vector format cannot express and which this repo's converter
  refuses rather than approximates.
* At the sizes the app actually draws these (24–42dp), the flatter ``crypto-icons`` marks read
  better. Side by side, its SOL, DOGE and DOT are legible where the Binance set's detailed coin
  illustrations turn to mush.

So ``crypto-icons`` leads and ``binance-icons`` fills the symbols it does not carry. Both vector
packs predate the 2023-24 listings, so ``tv-logos`` goes last purely as a gap-filler — it is the one
archive here that carries ARB, SUI, PEPE, SEI, WIF, TIA, WLD and ONDO. Only TON is in none of the
three and falls back to the raster archive; see ``RASTER_ONLY``.

``scripts/design/compare-symbol-logos.py`` renders the candidates side by side on both grounds; use
it before changing the order below.
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

# In preference order. See the module docstring for why.
VECTOR_SOURCES = ("crypto-icons", "binance-icons/crypto", "tv-logos/crypto")

# Symbols the vector archives cannot serve, taken from the Binance raster archive instead. Kept to a
# named list rather than an automatic sweep: a raster among vectors is a deliberate exception and
# should have to be argued for, one symbol at a time. Each of these has a reason —
#
#   ton   no vector archive carries it at all
#   arb   its only vector uses a gradient, which Android's vector format cannot express
#   sei   the same
#   wif   its vector is a 33 KB photo-like illustration, well past MAX_VECTOR_BYTES
#
# 192px covers 42dp — the largest the app draws one of these — at every density through xxxhdpi, so
# nothing here is upscaled. ARB is the exception at 64px and is soft above 24dp; it ships anyway,
# because a soft Arbitrum mark still reads as Arbitrum and a lettered "A" does not.
RASTER_ONLY = ("ton", "arb", "sei", "wif")
RASTER_ARCHIVE = "binance"

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


def convert(subset: str, symbols: list[str]) -> set[str]:
    """Convert one archive's symbols, returning the ones that landed."""
    if not symbols:
        return set()
    result = subprocess.run(
        [sys.executable, str(REPO / "scripts/design/svg-to-vector.py"), "--set", subset, *symbols],
        capture_output=True,
        text=True,
        cwd=REPO,
    )
    landed = set()
    for line in result.stdout.splitlines():
        if line.startswith("ok "):
            landed.add(line.split()[1])
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
    for subset in VECTOR_SOURCES:
        remaining = sorted(archive_symbols(subset) - chosen.keys())
        for symbol in convert(subset, remaining):
            chosen[symbol] = subset

    # Oversized artwork is dropped rather than shipped: see MAX_VECTOR_BYTES.
    dropped = []
    for symbol in sorted(chosen):
        path = DRAWABLE / f"asset_{resource_name(symbol)}.xml"
        if path.exists() and path.stat().st_size > MAX_VECTOR_BYTES:
            path.unlink()
            dropped.append((symbol, chosen.pop(symbol)))

    for symbol in RASTER_ONLY:
        if symbol in chosen:
            continue
        if convert_raster(symbol):
            chosen[symbol] = f"{RASTER_ARCHIVE} (raster)"

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
    print(f"{'total':<22} {len(chosen)}   ({len(dropped)} dropped as oversized)")
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
