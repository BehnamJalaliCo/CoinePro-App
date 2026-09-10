#!/usr/bin/env python3
"""String-resource lint: one voice, one glossary, both locales complete.

Runs over every `src/main/res/values-fa/strings.xml` (Persian, the product's language) and its
`values/strings.xml` sibling (English, the unqualified resource set), and fails on:

  * a key present in one locale and not the other (unless `translatable="false"`),
  * a `%1$s`-style placeholder present in one locale and not the other,
  * an engineering word in a user-facing string (backend, endpoint, viewport, …),
  * a spelling the glossary retired («دیدبان», «واگرد», «Bar length», …),
  * informal second person in Persian («تو», «‌ات», singular imperatives) outside the Rasad
    agent's own strings (`home_agent_*`), which speak warmly on purpose,
  * the hamza-on-heh ezafe (U+0654 / U+06C0) — the app writes «معامله‌ی», not «معاملهٔ»,
  * a space where a ZWNJ belongs («می رود», «آن ها», «بزرگ تر») and «بروز» for «به‌روز»,
  * an exclamation mark or an emoji in English copy (the greeting is the one place for it),
  * a merged feature description (`feature_*_body`) longer than the row can show,
  * the note policy: every `*_note` / `*_hint` / `*_body` key must be registered in
    `tools/i18n/notes.tsv` with a class; the `visible` class must match `NotePolicy.visible` in
    `core/designsystem` and stay within its budget; and a key in the `tip` class must never be
    resolved to a string by feature code (`stringResource(R.string.x_note)`) — it is handed to
    `CoineProNote(R.string.x_note)`, which decides whether it is drawn or folded into an ⓘ.

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
    ("fa", r"شیءها|اشیا", "ترسیم‌ها"),
    ("fa", r"واگرد", "برگرداندن"),
    ("fa", r"ازنو", "انجام دوباره"),
    ("fa", r"بازپخش نوار", "ریپلی"),
    ("fa", r"(?<![\u0620-\u064A\u066E-\u06D5])آپ(?![\u0620-\u064A\u066E-\u06D5])", "اپ"),
    ("fa", r"نقشهٔ حرارتی|نقشه‌ی حرارتی", "هیت‌مپ"),
    ("fa", r"(?<![\u0620-\u064A\u066E-\u06D5])کهنه(?![\u0620-\u064A\u066E-\u06D5])", "قدیمی"),
    ("fa", r"بازهٔ زمانی|بازه‌ی زمانی|بازهٔ کندل|بازه‌ی کندل", "تایم‌فریم"),
    ("fa", r"استودیوی نمودار", "استودیوی چارت"),
    ("en", r"\bBar length\b", "Timeframe"),
    ("en", r"\bStudies\b", "Indicators"),
    ("en", r"\bConnected surfaces\b", "Online tools"),
    ("en", r"\bProvider truth\b", "Broker & exchange status"),
    ("en", r"\bSTALE\b", "Stale"),
    ("en", r"\bInterval\b(?! [a-z])", "Timeframe"),
)

# The glossary's retired words that also appear in Kotlin literals (the tool rail's tiles, say).
KOTLIN_RETIRED = re.compile(r"شیءها|اشیا")

# «نمودار» is retired from the interface in favour of «چارت». It survives only where the word
# means a diagram that is not the price chart — an equity curve, a histogram in a lesson.
PERSIAN_DIAGRAM_ALLOWED_KEYS = {
    "portfolio_equity_curve",
}

# Informal second person: the enclitic «‌ات/‌ت», the pronoun «تو», and the singular imperative.
# Rasad speaks like this on purpose; nothing else does.
INFORMAL_PERSIAN = (
    (r"(?<![\u0620-\u064A\u066E-\u06D5])تو(?![\u0620-\u064A\u066E-\u06D5])", "«شما»"),
    (r"(?<![\u0620-\u064A\u066E-\u06D5])توست(?![\u0620-\u064A\u066E-\u06D5])", "«شماست»"),
    (r"‌ات(?![\u0620-\u064A\u066E-\u06D5])", "«‌تان» یا بازنویسی با «شما»"),
    (r"[\u0620-\u064A\u066E-\u06D5]+هایت(?![\u0620-\u064A\u066E-\u06D5])", "«‌هایتان»"),
    (r"(?<![\u0620-\u064A\u066E-\u06D5])(خودت|برات|بهت|ازت|باهات)(?![\u0620-\u064A\u066E-\u06D5])", "«خودتان» / «برایتان» / «به شما»"),
    # Singular imperatives a row subtitle tends to slip into.
    (
        r"(?<![\u0620-\u064A\u066E-\u06D5\u200C])(بنویس|بفرست|بگیر|ببین|بزن|بساز|کن|بده|بخوان|بیا|برو|بگو|بکش|بردار|بگذار|بذار|"
        r"بسنج|بپرس|بخر|بفروش|بچین|بکن|بیاور|بیار|باش|نکن|نده|نرو|نزن|بشین|بمان|بگرد|بیاب)(?![\u0620-\u064A\u066E-\u06D5\u200C])",
        "فعل جمع: «بنویسید»، «بفرستید»، «کنید»",
    ),
)

INFORMAL_ALLOWED_PREFIXES = ("home_agent_",)

HAMZA_ON_HEH = re.compile("[ٔۀ]")

ZWNJ_RULES = (
    (r"(?<![\u0620-\u064A\u066E-\u06D5])ن?می [\u0620-\u064A\u066E-\u06D5]", "«می‌» با نیم‌فاصله"),
    (r"[\u0620-\u064A\u066E-\u06D5] (ها|های|هایی|هایتان|هایش|تر|ترین)(?![\u0620-\u064A\u066E-\u06D5])", "نیم‌فاصله پیش از «ها/تر»"),
    (r"(?<![\u0620-\u064A\u066E-\u06D5])بروز(?![\u0620-\u064A\u066E-\u06D5])", "«به‌روز»"),
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

# The note policy. Every key ending in one of these suffixes is a subtitle candidate and must be
# registered with a class; see `core/designsystem/.../CoineProNote.kt` for what the classes mean.
NOTE_SUFFIX = re.compile(r"_(note|hint|body)$")
NOTE_REGISTRY = Path("tools/i18n/notes.tsv")
NOTE_POLICY_SOURCE = Path("core/designsystem/src/main/kotlin/com/coinepro/core/designsystem/CoineProNote.kt")
NOTE_CLASSES = {
    "visible",  # drawn inline: prevents a real mistake (money, deletion, security, permissions)
    "tip",  # folded into ⓘ by CoineProNote
    "state",  # the body of an empty / error / locked panel — the panel's content, not a subtitle
    "catalogue",  # a destination's one-line description in the menu, tools and search catalogues
    "system",  # an Android notification channel description or a notification body
    "label",  # a misnamed key that is really a field label or placeholder
    "admin",  # the internal panel, out of the store build
}
NOTE_BUDGET = 60
# A tip-class key resolved to a string in feature code bypasses the policy. These are the calls
# that do the resolving; passing the bare id to CoineProNote / noteRes is the sanctioned shape.
NOTE_RESOLVERS = re.compile(r"\b(stringResource|getString|stringRes)\(\s*(?:[A-Za-z]+\.)?R\.string\.([a-z0-9_]+)")


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
            # A key marked untranslatable lives in the default set alone since 4.52.0 — the brand,
            # an asset path — and has no Persian copy to be missing.
            if key not in fa and entry.translatable:
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

    def check_kotlin_literals(self) -> None:
        """The one orthography rule that reaches into Kotlin: a string literal in source is copy too,
        and the hamza-on-heh ezafe slipped into three hundred of them while the XML stayed clean."""
        for path in self.root.rglob("*.kt"):
            posix = path.as_posix()
            if "/build/" in posix:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            for number, line in enumerate(text.splitlines(), start=1):
                if HAMZA_ON_HEH.search(line):
                    self.fail(path, f"line {number}", "hamza-on-heh ezafe in a Kotlin literal — write «ه‌ی»")
                if "/src/main/" in posix and KOTLIN_RETIRED.search(line):
                    self.fail(path, f"line {number}", "retired word in a Kotlin literal — a drawing is «ترسیم», never «شیء/اشیا»")

    # -- note policy --------------------------------------------------------------------------

    def load_note_registry(self) -> dict[str, str]:
        path = self.root / NOTE_REGISTRY
        if not path.exists():
            self.fail(path, None, "note registry missing")
            return {}
        registry: dict[str, str] = {}
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2 or parts[1] not in NOTE_CLASSES:
                self.fail(path, Entry(parts[0], "", True, number), f"note class must be one of {sorted(NOTE_CLASSES)}")
                continue
            registry[parts[0]] = parts[1]
        return registry

    def load_note_policy(self) -> set[str]:
        path = self.root / NOTE_POLICY_SOURCE
        if not path.exists():
            self.fail(path, None, "NotePolicy source missing")
            return set()
        text = path.read_text(encoding="utf-8")
        match = re.search(r"val visible: Set<String> = setOf\((.*?)\n    \)", text, re.DOTALL)
        if not match:
            self.fail(path, None, "NotePolicy.visible not found")
            return set()
        return set(re.findall(r'"([a-z0-9_]+)"', match.group(1)))

    def check_note_registry(self, path: Path, entries: dict[str, Entry], registry: dict[str, str], seen: set[str]) -> None:
        for entry in entries.values():
            if not NOTE_SUFFIX.search(entry.key):
                continue
            seen.add(entry.key)
            if entry.key not in registry:
                self.fail(
                    path,
                    entry,
                    "new note key: register it in tools/i18n/notes.tsv — class `tip` unless not reading it "
                    "costs money, data, security or a permission, in which case `visible` and NotePolicy.visible",
                )

    def check_note_policy(self, registry: dict[str, str], seen: set[str]) -> None:
        registry_path = self.root / NOTE_REGISTRY
        for key in sorted(set(registry) - seen):
            self.fail(registry_path, Entry(key, "", True, 0), "registered note key no longer exists in any strings.xml")
        visible = {key for key, klass in registry.items() if klass == "visible"}
        if len(visible) > NOTE_BUDGET:
            self.fail(registry_path, None, f"{len(visible)} visible notes; the budget is {NOTE_BUDGET}")
        policy = self.load_note_policy()
        policy_path = self.root / NOTE_POLICY_SOURCE
        for key in sorted(visible - policy):
            self.fail(registry_path, Entry(key, "", True, 0), "visible in the registry but absent from NotePolicy.visible")
        for key in sorted(policy - visible):
            self.fail(policy_path, Entry(key, "", True, 0), "in NotePolicy.visible but not `visible` in tools/i18n/notes.tsv")
        # A demoted key resolved to a string by feature code is a subtitle that escaped the policy.
        tips = {key for key, klass in registry.items() if klass == "tip"}
        for path in self.root.rglob("*.kt"):
            posix = path.as_posix()
            if "/build/" in posix or "/src/test/" in posix or "/src/androidTest/" in posix or "/feature/admin/" in posix:
                continue
            if path.name == "CoineProNote.kt":
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            for number, line in enumerate(text.splitlines(), start=1):
                for match in NOTE_RESOLVERS.finditer(line):
                    key = match.group(2)
                    if key in tips:
                        self.fail(
                            path,
                            Entry(key, "", True, number),
                            "tip-class note resolved to a string; pass the id to CoineProNote(...) instead",
                        )

    def run(self) -> int:
        registry = self.load_note_registry()
        seen_notes: set[str] = set()
        fa_files = sorted(
            list(self.root.glob("*/src/main/res/values-fa/strings.xml"))
            + list(self.root.glob("*/*/src/main/res/values-fa/strings.xml"))
        )
        if not fa_files:
            print("lint_strings: no strings.xml found", file=sys.stderr)
            return 1
        for fa_path in fa_files:
            # The admin panel is internal-only and keeps its engineering vocabulary.
            internal = "feature/admin/" in fa_path.as_posix()
            en_path = fa_path.parent.parent / "values" / "strings.xml"
            fa = parse_strings(fa_path)
            en = parse_strings(en_path) if en_path.exists() else {}
            if not en_path.exists() and any(entry.translatable for entry in fa.values()):
                self.fail(fa_path, None, "module has Persian strings and no values/strings.xml (English, the default set)")
            self.check_parity(fa_path, fa, en_path, en)
            self.check_orthography(fa_path, fa)
            self.check_note_registry(fa_path, fa, registry, seen_notes)
            if internal:
                continue
            self.check_vocabulary(fa_path, fa, "fa")
            self.check_register(fa_path, fa)
            self.check_feature_bodies(fa_path, fa, "fa")
            if en:
                self.check_vocabulary(en_path, en, "en")
                self.check_english_tone(en_path, en)
                self.check_feature_bodies(en_path, en, "en")
        self.check_kotlin_literals()
        self.check_note_policy(registry, seen_notes)
        for failure in self.failures:
            print(failure)
        if self.failures:
            print(f"lint_strings: {len(self.failures)} problem(s) in {len(fa_files)} module(s)", file=sys.stderr)
            return 1
        print(f"lint_strings: {len(fa_files)} module(s) clean — parity, glossary, register, orthography, note policy.")
        return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=Path(__file__).resolve().parents[2], type=Path)
    arguments = parser.parse_args()
    sys.exit(Lint(arguments.root.resolve()).run())


if __name__ == "__main__":
    main()
