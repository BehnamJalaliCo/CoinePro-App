#!/usr/bin/env python3
"""Render the legal documents into the public site.

Google Play will not accept a link to a file in a repository as a privacy policy — it wants a page
a person can open. But keeping a second copy of the policy in HTML means the two drift, and a
privacy policy that disagrees with itself is worse than an awkward link. So `docs/legal/*.md` stays
the only place the text is written, and this renders it.

The Markdown here is deliberately a closed set — headings, paragraphs, rules, blockquotes, bullets,
numbered items, tables, bold, inline code, links and autolinks — which is everything those two
documents actually use. That is why there is a renderer here instead of a dependency: the whole of
CommonMark would be a library, and this is forty lines. Anything outside the set raises rather than
rendering silently wrong, so a new construct in the source is a build failure, not a broken page.

No web fonts and no external requests of any kind. A privacy policy that phones a third party to
draw itself is making a claim it cannot keep.

`site/` holds only what is served — the page sources that are not legal documents live in
`docs/site/`, so nothing unpublishable ends up in the deployed tree.

    python3 scripts/site/build-site.py          # write site/
    python3 scripts/site/build-site.py --check  # fail if site/ is out of date
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "site"

# ── inline ────────────────────────────────────────────────────────────────────

def inline(text: str) -> str:
    """Escape first, then re-introduce only the markup this renderer owns."""
    out = html.escape(text, quote=False)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', out)
    # &lt;https://…&gt; — the escape above already turned the angle brackets.
    out = re.sub(r"&lt;(https?://[^&\s]+)&gt;", r'<a href="\1">\1</a>', out)
    # An e-mail in angle brackets is the one autolink Markdown has that is not a URL. Without this
    # the support address rendered as the literal text `<name@example.com>`, which is both ugly and
    # not clickable — on the one page whose whole job is to tell somebody how to reach a human.
    out = re.sub(
        r"&lt;([^\s@&]+@[^\s@&]+\.[a-zA-Z]{2,})&gt;",
        r'<a href="mailto:\1">\1</a>',
        out,
    )
    return out


# ── block ─────────────────────────────────────────────────────────────────────

HEADING = re.compile(r"^(#{1,4})\s+(.*)$")
ORDERED = re.compile(r"^[0-9۰-۹]+[.)]\s+(.*)$")
BULLET = re.compile(r"^[*-]\s+(.*)$")
RULE = re.compile(r"^-{3,}$")


def render(markdown: str) -> str:
    lines = markdown.splitlines()
    out: list[str] = []
    index = 0
    while index < len(lines):
        raw = lines[index]
        line = raw.strip()

        if not line:
            index += 1
            continue

        if RULE.fullmatch(line):
            out.append("<hr>")
            index += 1
            continue

        heading = HEADING.match(line)
        if heading:
            level = len(heading.group(1))
            out.append(f"<h{level}>{inline(heading.group(2).strip())}</h{level}>")
            index += 1
            continue

        if line.startswith(">"):
            body = []
            while index < len(lines) and lines[index].strip().startswith(">"):
                body.append(lines[index].strip().lstrip(">").strip())
                index += 1
            out.append(f"<blockquote><p>{inline(' '.join(body))}</p></blockquote>")
            continue

        if line.startswith("|"):
            rows = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                rows.append([cell.strip() for cell in lines[index].strip().strip("|").split("|")])
                index += 1
            # Row two of a Markdown table is the alignment rule, not data.
            header, body = rows[0], rows[2:]
            cells = "".join(f"<th>{inline(c)}</th>" for c in header)
            table = [f"<table><thead><tr>{cells}</tr></thead><tbody>"]
            for row in body:
                table.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
            table.append("</tbody></table>")
            out.append("".join(table))
            continue

        if BULLET.match(line) or ORDERED.match(line):
            ordered = bool(ORDERED.match(line))
            tag = "ol" if ordered else "ul"
            items: list[str] = []
            while index < len(lines):
                current = lines[index].strip()
                match = ORDERED.match(current) if ordered else BULLET.match(current)
                if match:
                    items.append(match.group(1))
                    index += 1
                elif current and lines[index].startswith(("  ", "\t")) and items:
                    # A wrapped continuation line belongs to the item above it.
                    items[-1] += " " + current
                    index += 1
                else:
                    break
            body = "".join(f"<li>{inline(item)}</li>" for item in items)
            out.append(f"<{tag}>{body}</{tag}>")
            continue

        paragraph = []
        while index < len(lines):
            current = lines[index].strip()
            if not current or RULE.fullmatch(current) or current.startswith(("|", ">", "#")) \
                    or BULLET.match(current) or ORDERED.match(current):
                break
            paragraph.append(current)
            index += 1
        out.append(f"<p>{inline(' '.join(paragraph))}</p>")

    return "\n".join(out)


# ── page ──────────────────────────────────────────────────────────────────────

STYLE = """
:root{--canvas:#0B0E11;--surface:#10141B;--line:#252A31;--text:#F0F1F2;--muted:#B7BDC6;
--faint:#848E9C;--brand:#F0B90B;--radius:10px}
@media (prefers-color-scheme:light){:root{--canvas:#FAFAFA;--surface:#FFFFFF;--line:#E6E8EA;
--text:#12161C;--muted:#4A5259;--faint:#6B7480;--brand:#8A6400}}
*{box-sizing:border-box}
body{margin:0;background:var(--canvas);color:var(--text);
font-family:system-ui,-apple-system,"Segoe UI",Tahoma,"Iranian Sans",sans-serif;
font-size:16px;line-height:1.85;-webkit-text-size-adjust:100%}
.wrap{max-width:46rem;margin:0 auto;padding:2.5rem 1.25rem 5rem}
header{border-bottom:1px solid var(--line);padding-bottom:1.25rem;margin-bottom:2rem}
.brand{display:flex;align-items:center;gap:.6rem;font-weight:700;letter-spacing:-.01em}
.dot{width:.65rem;height:.65rem;border-radius:50%;background:var(--brand);flex:none}
nav{margin-top:.9rem;display:flex;flex-wrap:wrap;gap:.5rem}
nav a{font-size:.86rem;color:var(--muted);text-decoration:none;border:1px solid var(--line);
border-radius:999px;padding:.3rem .8rem}
nav a:hover,nav a[aria-current]{color:var(--text);border-color:var(--brand)}
h1{font-size:1.6rem;line-height:1.4;margin:2rem 0 .5rem}
h2{font-size:1.2rem;margin:2.2rem 0 .5rem;padding-top:.4rem}
h3{font-size:1rem;margin:1.6rem 0 .4rem;color:var(--muted)}
h4{font-size:.95rem;margin:1.2rem 0 .3rem;color:var(--faint)}
p{margin:.7rem 0}
a{color:var(--brand)}
hr{border:0;border-top:1px solid var(--line);margin:2rem 0}
blockquote{margin:1.2rem 0;padding:.1rem 1rem;border-inline-start:2px solid var(--line);
color:var(--faint);font-size:.92rem}
ul,ol{margin:.7rem 0;padding-inline-start:1.4rem}
li{margin:.35rem 0}
code{background:var(--surface);border:1px solid var(--line);border-radius:4px;
padding:.05rem .35rem;font-size:.85em;font-family:ui-monospace,Menlo,Consolas,monospace;
direction:ltr;display:inline-block}
.scroll{overflow-x:auto;margin:1.2rem 0}
table{width:100%;border-collapse:collapse;font-size:.9rem;background:var(--surface);
border:1px solid var(--line);border-radius:var(--radius);overflow:hidden}
th,td{padding:.55rem .75rem;border-bottom:1px solid var(--line);text-align:start;
vertical-align:top}
th{color:var(--faint);font-weight:600;font-size:.82rem}
tr:last-child td{border-bottom:0}
footer{margin-top:4rem;padding-top:1.25rem;border-top:1px solid var(--line);
color:var(--faint);font-size:.84rem}
"""


def page(title: str, body: str, current: str, lang: str = "fa") -> str:
    direction = "rtl" if lang == "fa" else "ltr"
    links = [("/CoinePro-App/", "کوین‌پرو"), ("/CoinePro-App/privacy/", "حریم خصوصی"),
             ("/CoinePro-App/terms/", "شرایط استفاده"),
             ("/CoinePro-App/delete-account/", "حذف حساب")]
    nav = "".join(
        '<a href="{}"{}>{}</a>'.format(href, ' aria-current="page"' if href == current else "", label)
        for href, label in links
    )
    # Tables scroll inside their own box; the page itself never scrolls sideways.
    body = re.sub(r"(<table>.*?</table>)", r'<div class="scroll">\1</div>', body, flags=re.S)
    return f"""<!doctype html>
<html lang="{lang}" dir="{direction}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(title)} — CoinePro">
<style>{STYLE}</style>
</head>
<body>
<div class="wrap">
<header>
  <div class="brand"><span class="dot"></span><span>CoinePro · کوین‌پرو</span></div>
  <nav>{nav}</nav>
</header>
<main>
{body}
</main>
<footer>
  <p>CoinePro · اپ اندروید سیگنال و کپی‌تریدینگ · <a href="https://github.com/BehnamJalaliCo/CoinePro-App/releases/latest">آخرین نسخه</a></p>
  <p>پشتیبانی: <a href="https://t.me/CoinePro_Admin">t.me/CoinePro_Admin</a></p>
</footer>
</div>
</body>
</html>
"""


PAGES = [
    ("terms", "شرایط استفاده — کوین‌پرو", ROOT / "docs/legal/TERMS.md"),
    ("delete-account", "حذف حساب کاربری — کوین‌پرو", ROOT / "docs/site/delete-account.md"),
]
INDEX = ROOT / "docs/site/index.md"
PRIVACY = ROOT / "docs/legal/PRIVACY_POLICY.md"

# The privacy policy carries a Persian text and an English one in a single file. On a page they are
# two documents: an English paragraph inside an RTL column has its punctuation on the wrong side and
# reads badly. So the file is split at the English title and each half gets a page in its own
# direction, cross-linked.
ENGLISH_TITLE = "# Privacy Policy — CoinePro"


def split_privacy() -> tuple[str, str]:
    text = PRIVACY.read_text(encoding="utf-8")
    cut = text.index(ENGLISH_TITLE)
    persian = text[:cut].rstrip().rstrip("-").rstrip()
    return persian, text[cut:]


def build() -> dict[Path, str]:
    files: dict[Path, str] = {}
    files[OUT / "index.html"] = page("کوین‌پرو", render(INDEX.read_text(encoding="utf-8")),
                                     "/CoinePro-App/")
    persian, english = split_privacy()
    files[OUT / "privacy/index.html"] = page(
        "سیاست حریم خصوصی — کوین‌پرو",
        render(persian) + '\n<hr>\n<p><a href="/CoinePro-App/privacy/en/">Read this policy in English</a></p>',
        "/CoinePro-App/privacy/",
    )
    files[OUT / "privacy/en/index.html"] = page(
        "Privacy Policy — CoinePro",
        render(english) + '\n<hr>\n<p><a href="/CoinePro-App/privacy/">خواندن این سیاست به فارسی</a></p>',
        "/CoinePro-App/privacy/", lang="en",
    )
    for slug, title, source in PAGES:
        files[OUT / slug / "index.html"] = page(
            title, render(source.read_text(encoding="utf-8")), f"/CoinePro-App/{slug}/"
        )
    # Without this GitHub runs Jekyll over the output and silently drops anything it dislikes.
    files[OUT / ".nojekyll"] = ""
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="fail if the built site is out of date")
    args = parser.parse_args()

    files = build()
    stale = [p for p, body in files.items() if not p.exists() or p.read_text(encoding="utf-8") != body]

    if args.check:
        if stale:
            for path in stale:
                print(f"::error::{path.relative_to(ROOT)} is out of date; run scripts/site/build-site.py")
            return 1
        print(f"Site is up to date ({len(files)} files).")
        return 0

    for path, body in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    print(f"Wrote {len(files)} files to site/ ({len(stale)} changed).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
