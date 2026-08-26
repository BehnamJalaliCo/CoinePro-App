#!/usr/bin/env python3
"""Fetch TradingView's symbol logos into ``design/asset-logos/tv-logos``.

The owner collected 96 crypto marks by hand into Pro-Chart and asked that the app use them. This
takes the same source and covers the whole universe rather than the part that happened to be saved:
every crypto base the app already knows, every currency whose flag a forex pair needs, the four
metals, and the equity/index/ETF marks that the LBank listing carries.

One namespace per kind, which is TradingView's own arrangement:

===========  ==============================  ====================================
kind         path                            example
===========  ==============================  ====================================
crypto       ``/crypto/XTVC<BASE>.svg``      ``XTVCBTC.svg``
country      ``/country/<ISO2>.svg``         ``US.svg`` — the flag behind a currency
metal        ``/metal/<name>.svg``           ``gold.svg``
equity/ETF   ``/<slug>.svg``                 ``apple.svg``, ``nasdaq.svg``
===========  ==============================  ====================================

Only files that are actually SVG and actually new are written; a 404 is the ordinary answer for a
base TradingView does not draw and leaves that symbol to whatever source already covered it.

The legal position on redistributing these marks is the owner's, stated explicitly. This script
records where each file came from so that position is at least documented rather than implied.

    python3 scripts/design/download-tv-logos.py            # every base in the shipped table
    python3 scripts/design/download-tv-logos.py --only BTC ETH
"""

from __future__ import annotations

import argparse
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
ARCHIVE = REPO / "design" / "asset-logos" / "tv-logos"
TABLE = (
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

CDN = "https://s3-symbol-logo.tradingview.com"

# Currency → ISO-3166 alpha-2, for the flag behind a forex leg. Pro-Chart's table, extended with the
# currencies an MT5 broker actually quotes beyond the majors.
CURRENCY_COUNTRY = {
    "USD": "US", "EUR": "EU", "GBP": "GB", "JPY": "JP", "CHF": "CH", "CAD": "CA",
    "AUD": "AU", "NZD": "NZ", "TRY": "TR", "SEK": "SE", "NOK": "NO", "DKK": "DK",
    "ZAR": "ZA", "MXN": "MX", "SGD": "SG", "HKD": "HK", "CNH": "CN", "CNY": "CN",
    "PLN": "PL", "CZK": "CZ", "HUF": "HU", "RUB": "RU", "INR": "IN", "BRL": "BR",
    "KRW": "KR", "THB": "TH", "ILS": "IL", "SAR": "SA", "AED": "AE", "TWD": "TW",
}

METALS = {"XAU": "gold", "XAG": "silver", "XPT": "platinum", "XPD": "palladium"}

# The equity, index and ETF marks the LBank listing carries — TradeYar's team named these while
# scoping the crypto universe, and they are exactly the ones a crypto-only icon pack cannot draw.
# Keyed by the ticker the feeds use; the value is TradingView's own slug at the CDN root.
EQUITY_SLUG = {
    "AAPL": "apple", "TSLA": "tesla", "MSFT": "microsoft", "GOOGL": "alphabet",
    "AMZN": "amazon", "META": "meta-platforms", "NVDA": "nvidia", "NFLX": "netflix",
    "AMD": "advanced-micro-devices", "INTC": "intel", "COIN": "coinbase",
    "MSTR": "microstrategy", "SAMSUNG": "samsung", "SKHYNIX": "sk-hynix",
    "QQQ": "nasdaq", "SPY": "spdr", "GLD": "spdr", "US30": "dow-jones",
    "US500": "s-and-p-global", "NAS100": "nasdaq", "US100": "nasdaq",
}

TIMEOUT = 20
RETRIES = 3
WORKERS = 8


# Every archive on disk, so the fetch is seeded by what the *sources* know rather than by what has
# already converted. Seeding from the shipped table was circular: a symbol no archive could convert
# was never in the table, so TradingView was never asked for it — and TradingView is exactly the
# source most likely to have the one the others lack.
ARCHIVE_ROOTS = (
    "binance-icons/crypto",
    "crypto-icons",
    "binance",
    "tv-logos/crypto",
)


def bases_to_fetch() -> list[str]:
    """Every symbol name any archive knows, plus everything the shipped lookup already carries."""
    names: set[str] = set()
    root = REPO / "design" / "asset-logos"
    for subset in ARCHIVE_ROOTS:
        directory = root / subset
        if not directory.is_dir():
            continue
        for path in directory.iterdir():
            if path.suffix.lower() in (".svg", ".png", ".webp"):
                names.add(path.stem.upper())
    if TABLE.exists():
        names.update(re.findall(r'^\s*"([^"]+)" to R\.drawable\.', TABLE.read_text(), re.MULTILINE))
    return sorted(names)


def fetch(url: str) -> bytes | None:
    """One file, retried. Returns None for a 404 — the ordinary answer for a mark TV lacks."""
    for attempt in range(RETRIES):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "coinepro-logo-sync"})
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                body = response.read()
            # A CDN that answers 200 with an error page is worse than one that 404s, so the
            # content is checked rather than the status. Anything that is not an SVG is dropped.
            if b"<svg" not in body[:512].lower():
                return None
            if is_placeholder(body):
                return None
            return body
        except urllib.error.HTTPError as error:
            if error.code in (403, 404):
                return None
        except Exception:
            pass
        time.sleep(0.4 * (attempt + 1))
    return None


# TradingView answers with a plain grey square for a flag it does not publish — Hong Kong and
# Taiwan among them. It is a 200 and it is valid SVG, so nothing about the response says "missing",
# and saved it becomes a grey disc on the symbol row that reads as a broken image rather than as an
# absence. The lettered token is the better answer, so a placeholder is treated as a 404.
PLACEHOLDER_FILL = b"#DBDBDB"


def is_placeholder(body: bytes) -> bool:
    shapes = body.count(b"<path") + body.count(b"<rect") + body.count(b"<circle")
    return shapes == 1 and PLACEHOLDER_FILL in body.upper()


def download(kind: str, name: str, url: str, force: bool) -> tuple[str, str, bool]:
    target = ARCHIVE / kind / f"{name}.svg"
    if target.exists() and not force:
        return kind, name, False
    body = fetch(url)
    if body is None:
        return kind, name, False
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(body)
    return kind, name, True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", nargs="*", help="Fetch just these crypto bases.")
    parser.add_argument("--force", action="store_true", help="Re-fetch files that already exist.")
    arguments = parser.parse_args()

    jobs: list[tuple[str, str, str]] = []

    crypto = [b.upper() for b in (arguments.only or bases_to_fetch())]
    # A base with a character TradingView's path cannot carry is not a base it draws either.
    crypto = sorted({b for b in crypto if re.fullmatch(r"[A-Z0-9]{2,12}", b)})
    for base in crypto:
        jobs.append(("crypto", base.lower(), f"{CDN}/crypto/XTVC{base}.svg"))

    if not arguments.only:
        for code in sorted(set(CURRENCY_COUNTRY.values())):
            jobs.append(("country", code.lower(), f"{CDN}/country/{code}.svg"))
        for name in sorted(set(METALS.values())):
            jobs.append(("metal", name, f"{CDN}/metal/{name}.svg"))
        for slug in sorted(set(EQUITY_SLUG.values())):
            jobs.append(("equity", slug, f"{CDN}/{slug}.svg"))

    print(f"fetching {len(jobs)} logos from {CDN}", file=sys.stderr)
    written = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for kind, name, ok in pool.map(lambda job: download(*job, arguments.force), jobs):
            if ok:
                written += 1

    for kind in ("crypto", "country", "metal", "equity"):
        directory = ARCHIVE / kind
        count = len(list(directory.glob("*.svg"))) if directory.exists() else 0
        print(f"  {kind:<8} {count}", file=sys.stderr)
    print(f"{written} new file(s)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
