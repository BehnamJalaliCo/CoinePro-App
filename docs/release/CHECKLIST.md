# Release checklist — Pro Chart

What has to be true before a version goes to readers, and where each thing is checked. CI does
everything marked **CI**; the rest is a person with the owner's material.

## Before tagging

- [ ] `CHANGELOG.md` has the version's entry, in Persian, in the app's own vocabulary
      (`tools/i18n/lint_strings.py` is the vocabulary; the glossary is `docs/audit/…`).
- [ ] `scripts/release/version.py --check` agrees with `app/build.gradle.kts` — **CI**.
- [ ] Legal documents in sync: `scripts/release/sync-legal-documents.py --check` — **CI**.
      Terms and privacy exist in both languages under `docs/legal/`.
- [ ] Store listing strings come from the same strings the app ships (`app_name`, `feature_*_body`,
      `widget_description`); nothing in the listing uses a word the lint would refuse.

## Gates (every push, before any artefact)

- [ ] `scripts/quality/check-cross-phase-consistency.py` — brand spelling, UI vocabulary, English
      locale is English, no secret in a log line, assets clean, tabular digits, string lint — **CI**.
- [ ] `scripts/quality/check-motion-policy.sh`, `check-kotlin-style.sh`, `scripts/security/scan-secrets.sh` — **CI**.
- [ ] `tools/i18n/lint_strings.py` — parity both ways, placeholders, register, orthography — **CI**.
- [ ] Unit tests: indicators (`IndicatorParityTest`, 1e-6 against the web terminal), NamaScript,
      calculators (`TraderToolsRulesTest` walks every `tools_rule_*`), help schema, goldens — **CI**.

## Signing and packaging

- [ ] Release keystore restored from the secret; `apksigner verify` passes on the APK — **CI**.
- [ ] `check-release-wire-fields.py`: Gson field names survived R8 — **CI**.
- [ ] `check-release-surface.py`: no admin strings, no emulator ABIs, no stray files in the APK — **CI**.
- [ ] AAB built from the same commit and key (`:app:bundleRelease`) and attached to the release — **CI**.
- [ ] Download size: `scripts/release/check-bundle-size.sh <aab> 16` — the largest per-device split
      set under 16 MiB (4.33.0 measures 13.7–13.9 MiB; 9 MiB is the target once an asset pack
      exists) — **CI**.
- [ ] Baseline profile present in the APK (`check-cross-phase-consistency.py` `check_baseline_profile`) — **CI**.

## Security

- [ ] Certificate pins: `COINEPRO_CERTIFICATE_PINS` set as a signing property when the production
      chain is known (`docs/security/PINNING.md` — format, rotation, the backup pin rule). Until
      then the client is unpinned and the build says nothing, which is the honest state.
- [ ] Play Integrity: blocked on a Google Cloud project bound to the listing (`PHASE2_REPORT.md`).
- [ ] `usesCleartextTraffic="false"`, `allowBackup="false"` — unchanged in the manifest.

## App Links and deep links

- [ ] `docs/release/APP_LINKS.md`: `assetlinks.json` on `coineprofx.com` carries the release
      certificate's SHA-256; `adb shell pm get-app-links com.coinepro.app` shows `verified`.
- [ ] `coinepro://market/<ticker>`, `coinepro://signal/<id>`, `coinepro://activity` open the right
      screen from a cold start (`DeepLinkValidation` refuses anything else).

## Performance

- [ ] `ChartFlingBenchmark` and `StartupBenchmark` run on a phone
      (`./gradlew :benchmark:connectedBenchmarkAndroidTest`) and
      `scripts/quality/check-benchmark-thresholds.py` passes on the JSON: P95 frame ≤ 8 ms on the
      chart, cold start ≤ 800 ms with the profile. CI runs the script with `--allow-missing`
      because the runner has no device; the number is a person's to produce per release.

## Play Asset Delivery

- [ ] Not used: the help catalogue is text (`core/help/content.json`, 238 entries) and no asset
      pack exists. Revisit when imagery lands; the plugin is not in the offline cache
      (`PHASE2_REPORT.md`).

## After publishing

- [ ] The GitHub release carries `CoinePro-<version>.apk` and `.aab`, the tag matches
      `version.py`, and the release note is the changelog entry.
- [ ] Install over the previous version on a phone (same key, so it updates in place); open the
      chart, the watchlist, a signal, the widget.
