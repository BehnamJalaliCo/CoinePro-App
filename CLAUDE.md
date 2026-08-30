# CoinePro-App — working agreement

## How the owner wants work done

**تا تموم نکردی توقف نکن و هیچ گزارشی نده.**
Do not stop until the work is finished, and do not report progress along the way.

In practice that means:

- **No progress reports.** Do not summarise what was just committed, do not list what is
  left, do not ask "shall I continue". Work through the list and keep going.
- **No stopping between items.** Finishing one item is not a stopping point. Move to the
  next one in the same turn.
- **Stop only when the whole list is done**, or when something is genuinely blocked on the
  owner (a signing key, a Firebase console, a device) — and then say only what is blocked,
  in one or two lines.
- Answers, when they are unavoidable, are **very short**.

## Standing constraints

- **Work only on `main`.** Never create a branch.
- Run all five gates before every commit:
  `python3 scripts/quality/check-cross-phase-consistency.py`,
  `bash scripts/quality/check-motion-policy.sh`,
  `bash scripts/quality/check-kotlin-style.sh`,
  `bash scripts/security/scan-secrets.sh`,
  `python3 scripts/release/sync-legal-documents.py --check`
  then `./gradlew testDebugUnitTest :app:assembleRelease`.
- Secrets in the two backend repositories are **not this app's business** — the owner will
  rotate them. Never raise them again. Only ever print variable *names*, never values.
- The legal and IP position on the vendored icons is the owner's, settled. Do not re-raise it.
- Font is IRANYekanX (Eco). Persian is the default locale.
- **Latin digits for market figures; Persian digits for prose counts.**
- No symbol without artwork ever reaches a list — no blank squares, no lettered discs.
  `SymbolArtwork.covers` is the filter, at the catalogue and at the live feed.
