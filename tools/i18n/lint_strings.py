#!/usr/bin/env python3
"""String-resource lint: one voice, one glossary, both locales complete.

Runs over every `src/main/res/values/strings.xml` (Persian, the default locale) and its
`values-en/strings.xml` sibling, and fails on:

  * a key present in one locale and not the other (unless `translatable="false"`),
  * a `%1$s`-style placeholder present in one locale and not the other,
  * an engineering word in a user-facing string (backend, endpoint, viewport, …),
  * a spelling the glossary retired («دیدبان», «واگرد», «Bar length», …),
  * informal second person in Persian («تو», «‌ات», singular imperatives) outside the Rasad
    agent's own strings (`home_agent_*`), which speak warmly on purpose,
  * the hamza-on-heh ezafe (U+0654 / U+06C0) — the app writes «معامله‌ی», not «معاملهٔ»,
  * a space where a ZWNJ belongs («می رود», «آن ها», «بزرگ تر») and «بروز» for «به‌روز»,
  * an exclamation mark or an emoji in English copy (the greeting is the one place for it),
  * a merged feature description (`feature_*_body`) longer than the row can show.

Usage: `python3 tools/i18n/lint_strings.py [--root DIR]`. Exit status is the failure count,
capped at one, so it can sit in the CI gate list beside the other checks.
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------------------------------------
# Rules
# ---------------------------------------------------------------------------------------------

# Words that describe how the app is built rather than what the reader sees. Matched as whole
# words, case-insensitive, in English copy. Each pair is (pattern, what to say instead).
BANNED_ENGLISH = (
    (r"\bback-?end\b", "say what the reader is waiting on: «the server» or nothing"),
    (r"\bserver-side setting\b", "«managed on the server»"),
    (r"\bendpoints?\b", "«address» or drop it"),
    (r"\bserved?\b(?! as)", "«sent», «shown», «given»"),
    (r"\bthis build\b|\brelease build\b|\bdebug build\b", "«this version»"),
    (r"\bsurfaces?\b", "«screen», «tool», «section»"),
    (r"\brelays?\b|\brelayed\b", "«forwards», «passes on»"),
    (r"\bcanvas\b", "«chart»"),
    (r"\bviewport\b", "«view», «what you see»"),
    (r"\bdeterministic\b", "«exact», «the same every time»"),
    (r"\bgeometry\b", "«shape», «layout»"),
    (r"\bprovider truth\b", "«broker & exchange status»"),
    (r"\bwhitelist(ing|ed)?\b", "«allowed», «trusted»"),
    (r"\bsource-backed\b", "«from the source»"),
    (r"\bruntime permission\b", "«permission»"),
    (r"\bcrosshair position is not reported\b", "drop it"),
)

# The same idea in Persian: words a trader never says about the thing on the screen.
BANNED_PERSIAN = (
    (r"بک‌?اند", "«سرور» یا هیچ"),
    (r"اندپوینت|نقطهٔ پایانی|نقطه‌ی پایانی", "«آدرس»"),
    (r"سطوح متصل|ابزارهای متصل", "«ابزارهای آنلاین»"),
    (r"حقیقتِ? ارائه‌دهنده", "«وضعیت واقعی کارگزار/صرافی»"),
    (r"قطعی‌گرا|دترمینیستیک", "«قطعی»، «همیشه یکسان»"),
    (r"هندسهٔ|هندسه‌ی", "«شکل»، «چیدمان»"),
    (r"مجوز زمان اجرا", "«مجوز»"),
    (r"سرور-محور|سمت سرور", "«روی سرور»"),
)

# Spellings the glossary retired. Each entry: (locale suffix, pattern, canonical form).
FORBIDDEN_VARIANTS = (
    ("fa", r"Pro CHart|Pro-Chart|ProChart|پروچارت", "Pro Chart / پرو چارت"),
    ("en", r"Pro CHart|Pro-Chart|ProChart", "Pro Chart"),
    ("fa", r"دیدبان", "دیده‌بان"),
    ("fa", r"نما اسکریپت|نما‌اسکریپت", "نمااسکریپت"),
    ("fa", r"شیءها|اشیاء", "ترسیم‌ها"),
    ("fa", r"واگرد", "برگرداندن"),
    ("fa", r"ازنو", "انجام دوباره"),
    ("fa", r"بازپخش نوار", "ریپلی"),
    ("fa", r"(?<![؀-ۿ])آپ(?![؀-ۿ])", "اپ"),
    ("fa", r"نقشهٔ حرارتی|نقشه‌ی حرارتی", "هیت‌مپ"),
    ("fa", r"(?<![؀-ۿ])کهنه(?![؀-ۿ])", "قدیمی"),
    ("fa", r"بازهٔ زمانی|بازه‌ی زمانی|بازهٔ کندل|بازه‌ی کندل", "تایم‌فریم"),
    ("fa", r"استودیوی نمودار", "استودیوی چارت"),
    ("en", r"\bBar length\b", "Timeframe"),
    ("en", r"\bStudies\b", "Indicators"),
    ("en", r"\bConnected surfaces\b", "Online tools"),
    ("en", r"\bProvider truth\b", "Broker & exchange status"),
    ("en", r"\bSTALE\b", "Stale"),
    ("en", r"\bInterval\b(?! [a-z])", "Timeframe"),
)

# «نمودار» is retired from the interface in favour of «چارت». It survives only where the word
# means a diagram that is not the price chart — an equity curve, a histogram in a lesson.
PERSIAN_DIAGRAM_ALLOWED_KEYS = {
    "portfolio_equity_curve",
}

# Informal second person: the enclitic «‌ات/‌ت», the pronoun «تو», and the singular imperative.
# Rasad speaks like this on purpose; nothing else does.
INFORMAL_PERSIAN = (
    (r"(?<![؀-ۿ])تو(?![؀-ۿ])", "«شما»"),
    (r"(?<![؀-ۿ])توست(?![؀-ۿ])", "«شماست»"),
    (r"‌ات(?![؀-ۿ])", "«‌تان» یا بازنویسی با «شما»"),
    (r"[؀-ۿ]+هایت(?![؀-ۿ])", "«‌هایتان»"),
    (r"(?<![؀-ۿ])(خودت|برات|بهت|ازت|باهات)(?![؀-ۿ])", "«خودتان» / «برایتان» / «به شما»"),
    # Singular imperatives a row subtitle tends to slip into.
    (
        r"(?<![؀-ۿ‌])(بنویس|بفرست|بگیر|ببین|بزن|بساز|کن|بده|بخوان|بیا|برو|بگو|بکش|بردار|بگذار|بذار|"
        r"بسنج|بپرس|بخر|بفروش|بچین|بکن|بیاور|بیار|باش|نکن|نده|نرو|نزن|بشین|بمان|بگرد|بیاب)(?![؀-ۿ‌])",
        "فعل جمع: «بنویسید»، «بفرستید»، «کنید»",
    ),
)

INFORMAL_ALLOWED_PREFIXES = ("home_agent_",)

HAMZA_ON_HEH = re.compile("[ٔۀ]")

ZWNJ_RULES = (
    (r"(?<![؀-ۿ])ن?می [؀-ۿ]", "«می‌» با نیم‌فاصله"),
    (r"[؀-ۿ] (ها|های|هایی|هایتان|هایش|تر|ترین)(?![؀-ۿ])", "نیم‌فاصله پیش از «ها/تر»"),
    (r"(?<![؀-ۿ])بروز(?![؀-ۿ])", "«به‌روز»"),
)

# Persian words that end in «ها» or «تر» as part of the stem, and so are not a plural or a
# comparative missing a ZWNJ — the space before them is a real word boundary.
ZWNJ_STEM_WORDS = ("تنها", "رها", "بها", "بهتر", "دفتر", "دکتر", "بستر", "خاکستر", "شوهر", "کمتر", "بیشتر", "زودتر")

EMOJI = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F000-\U0001F2FF\U0001F900-\U0001F9FF⭐⬆⬇⤴⤵〰〽㊗㊙]"
)
EXCLAMATION_ALLOWED_KEYS = {"home_greeting"}
EMOJI_ALLOWED_KEYS = {"home_greeting"}

PLACEHOLDER = re.compile(r"%(\d+\$)?[sdf]")

FEATURE_BODY_MAX_FA_CHARS = 40
FEATURE_BODY_MAX_EN_WORDS = 6


# ---------------------------------------------------------------------------------------------
# Parsing
# ---------------------------------------------------------------------------------------------


@dataclass(frozen=True)
class Entry:
    key: str
    text: str
    translatable: bool
    line: int


def parse_strings(path: Path) -> dict[str, Entry]:
    """Every `<string>`, `<plurals>` item and `<string-array>` item, keyed for parity checks."""
    entries: dict[str, Entry] = {}
    # ElementTree drops line numbers; recover them by key on the raw text afterwards.
    raw_lines = path.read_text(encoding="utf-8").splitlines()
    line_of: dict[str, int] = {}
    for number, line in enumerate(raw_lines, start=1):
        match = re.search(r'name="([^"]+)"', line)
        if match and match.group(1) not in line_of:
            line_of[match.group(1)] = number
    root = ElementTree.parse(path).getroot()
    for element in root:
        name = element.get("name")
        if name is None:
            continue
        translatable = element.get("translatable", "true") != "false"
        if element.tag == "string":
            entries[name] = Entry(name, "".join(element.itertext()), translatable, line_of.get(name, 0))
        elif element.tag == "plurals":
            for item in element.findall("item"):
                quantity = item.get("quantity", "?")
                key = f"{name}[{quantity}]"
                entries[key] = Entry(key, "".join(item.itertext()), translatable, line_of.get(name, 0))
        elif element.tag == "string-array":
            for index, item in enumerate(element.findall("item")):
                key = f"{name}[{index}]"
                entries[key] = Entry(key, "".join(item.itertext()), translatable, line_of.get(name, 0))
    return entries


def unescape(text: str) -> str:
    return text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@")


# ---------------------------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------------------------


class Lint:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.failures: list[str] = []

    def fail(self, path: Path, entry: Entry | None, message: str) -> None:
        where = f"{path.relative_to(self.root)}:{entry.line if entry else 0}"
        key = f" [{entry.key}]" if entry else ""
        self.failures.append(f"{where}{key} {message}")

    # -- parity -------------------------------------------------------------------------------

    def check_parity(self, fa_path: Path, fa: dict[str, Entry], en_path: Path, en: dict[str, Entry]) -> None:
        for key, entry in fa.items():
            if entry.translatable and key not in en:
                self.fail(fa_path, entry, "has no English translation")
        for key, entry in en.items():
            if key not in fa:
                self.fail(en_path, entry, "has no Persian source")
        for key in fa.keys() & en.keys():
            fa_slots = Counter(PLACEHOLDER.findall(fa[key].text))
            en_slots = Counter(PLACEHOLDER.findall(en[key].text))
            if fa_slots != en_slots:
                self.fail(en_path, en[key], f"placeholders differ from Persian: fa={sorted(fa_slots)} en={sorted(en_slots)}")

    # -- vocabulary ---------------------------------------------------------------------------

    def check_vocabulary(self, path: Path, entries: dict[str, Entry], locale: str) -> None:
        banned = BANNED_ENGLISH if locale == "en" else BANNED_PERSIAN
        for entry in entries.values():
            text = unescape(entry.text)
            for pattern, instead in banned:
                if re.search(pattern, text, re.IGNORECASE):
                    self.fail(path, entry, f"engineering word {pattern!r}; say {instead}")
            for variant_locale, pattern, canonical in FORBIDDEN_VARIANTS:
                if variant_locale == locale and re.search(pattern, text):
                    self.fail(path, entry, f"retired spelling {pattern!r}; write «{canonical}»")
            if locale == "fa" and "نمودار" in text and entry.key not in PERSIAN_DIAGRAM_ALLOWED_KEYS:
                self.fail(path, entry, "«نمودار» in interface copy; write «چارت»")

    # -- register -----------------------------------------------------------------------------

    def check_register(self, path: Path, entries: dict[str, Entry]) -> None:
        for entry in entries.values():
            if entry.key.startswith(INFORMAL_ALLOWED_PREFIXES):
                continue
            text = unescape(entry.text)
            for pattern, instead in INFORMAL_PERSIAN:
                match = re.search(pattern, text)
                if match:
                    self.fail(path, entry, f"informal second person «{match.group(0)}»; use {instead}")

    # -- orthography --------------------------------------------------------------------------

    def check_orthography(self, path: Path, entries: dict[str, Entry]) -> None:
        for entry in entries.values():
            text = unescape(entry.text)
            if HAMZA_ON_HEH.search(text):
                self.fail(path, entry, "hamza-on-heh ezafe; write «ه‌ی» (heh, ZWNJ, yeh)")
            for pattern, instead in ZWNJ_RULES:
                for match in re.finditer(pattern, text):
                    fragment = match.group(0)
                    if any(fragment.endswith(stem) for stem in ZWNJ_STEM_WORDS):
                        continue
                    self.fail(path, entry, f"space where a ZWNJ belongs «{fragment}»; {instead}")

    def check_english_tone(self, path: Path, entries: dict[str, Entry]) -> None:
        for entry in entries.values():
            text = unescape(entry.text)
            if "!" in text and entry.key not in EXCLAMATION_ALLOWED_KEYS:
                self.fail(path, entry, "exclamation mark in English copy")
            if EMOJI.search(text) and entry.key not in EMOJI_ALLOWED_KEYS:
                self.fail(path, entry, "emoji outside the greeting")

    # -- lengths ------------------------------------------------------------------------------

    def check_feature_bodies(self, path: Path, entries: dict[str, Entry], locale: str) -> None:
        for entry in entries.values():
            if not (entry.key.startswith("feature_") and entry.key.endswith("_body")):
                continue
            text = unescape(entry.text)
            if locale == "fa" and len(text) > FEATURE_BODY_MAX_FA_CHARS:
                self.fail(path, entry, f"feature description is {len(text)} chars; the row shows {FEATURE_BODY_MAX_FA_CHARS}")
            if locale == "en" and len(text.split()) > FEATURE_BODY_MAX_EN_WORDS:
                self.fail(path, entry, f"feature description is {len(text.split())} words; the row shows {FEATURE_BODY_MAX_EN_WORDS}")

    # -- driver -------------------------------------------------------------------------------

    def run(self) -> int:
        fa_files = sorted(
            list(self.root.glob("*/src/main/res/values/strings.xml"))
            + list(self.root.glob("*/*/src/main/res/values/strings.xml"))
        )
        if not fa_files:
            print("lint_strings: no strings.xml found", file=sys.stderr)
            return 1
        for fa_path in fa_files:
            # The admin panel is internal-only and keeps its engineering vocabulary.
            internal = "feature/admin/" in fa_path.as_posix()
            en_path = fa_path.parent.parent / "values-en" / "strings.xml"
            fa = parse_strings(fa_path)
            en = parse_strings(en_path) if en_path.exists() else {}
            if not en_path.exists() and any(entry.translatable for entry in fa.values()):
                self.fail(fa_path, None, "module has Persian strings and no values-en/strings.xml")
            self.check_parity(fa_path, fa, en_path, en)
            self.check_orthography(fa_path, fa)
            if internal:
                continue
            self.check_vocabulary(fa_path, fa, "fa")
            self.check_register(fa_path, fa)
            self.check_feature_bodies(fa_path, fa, "fa")
            if en:
                self.check_vocabulary(en_path, en, "en")
                self.check_english_tone(en_path, en)
                self.check_feature_bodies(en_path, en, "en")
        for failure in self.failures:
            print(failure)
        if self.failures:
            print(f"lint_strings: {len(self.failures)} problem(s) in {len(fa_files)} module(s)", file=sys.stderr)
            return 1
        print(f"lint_strings: {len(fa_files)} module(s) clean — parity, glossary, register, orthography.")
        return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=Path(__file__).resolve().parents[2], type=Path)
    arguments = parser.parse_args()
    sys.exit(Lint(arguments.root.resolve()).run())


if __name__ == "__main__":
    main()
