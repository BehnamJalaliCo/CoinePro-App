# Phase 1 — Global readiness & correctness

Read with `PHASE0_INVENTORY.md`, which carries the numbers. Everything below is on `main`, built
and tested; the items that need something the owner holds are named as such rather than pretended.

## Done

| Item | What changed | Proof |
|---|---|---|
| 1.2 Missing translations | Seven app-owned keys with no English (`community_like/reply/best_badge`, `activity_signals_loaded_partial`, `tools_f_orders_none`, `academy_level_progress`, `field_clear`) translated; `terminal_error_disabled` names the terminal; `chart_band_more` is *Analysis* / «تحلیل» in both. Nine help topics had English titles but Persian-only *how*, *example* and *tips* — twenty-seven fields written. Four help ids that differed only in case were two entries each; the indicators point at the richer ones and the export copies alias to them. | `HelpCatalogTest` (both languages on every field, no case collisions, aliases resolve); `ChartCatalogTest` (every indicator's help id exists) |
| 1.3 Brand | *Pro Chart / پرو چارت* everywhere the reader can see; `BrandConfig` in `core/common` for the scheme, recovery host, legal base URL, support URL; every code site that built a brand link reads it (`AlertDeepLink`, `MarketsWidget`, `CoineProFirebaseMessagingService`, `DeepLinkValidation`, `DeleteAccountScreen`, `MembershipGate`, `MembershipJourneyPanel`, `PublicFeedClient`). | consistency gate `check_brand_spelling` |
| 1.4 Legal | `docs/legal/TERMS_EN.md`, synced to the app asset and the website (`/terms/en/`); English locale names it; the "Persian only" note is gone because nothing English shows a Persian document any more. English privacy policy stamps a revision date, not "App version 1.0". Legal URLs come from `BrandConfig.LEGAL_BASE_URL`. TradeYar and CoinePro-FX stay named: they are the owner's own services, and a user who connects a MetaTrader account is entitled to know which host holds it. | `sync-legal-documents.py --check`, `build-site.py --check` |
| 1.5 KYC | `KycIdentity(fullName, country, documentType, documentNumber, birthDate, phone)`; the form opens on Iran + national card so an Iranian reader's visit is unchanged, and any other country picks a passport or licence from a searchable list in the phone's own language. The wire keeps the original contract: an Iranian card goes as `national_id` alone; anything else as `country`, `document_type`, `document_number` with `national_id` absent. | `KycLevel1RequestTest` (four cases), the KYC render case |
| 1.6 Feeds | Fallback-only, behind `BuildConfig.DIRECT_THIRD_PARTY_FEEDS`; contract in `docs/backend/FEEDS.md`. | `PublicMarketIntel.directFeeds` |
| 1.7 App Links | `docs/release/APP_LINKS.md`: what is claimed, what each host must serve, which key's fingerprint, how to verify. | — |

## Not done, and why

* **1.1 Locale inversion.** Declined by the owner on 2026-09-05: Persian stays the default. In its
  place the gate fails on any Persian text in `values-en/` — the failure that matters when the
  default is Persian is an English screen showing Persian, and that is what is now caught.
* **1.5, the server side.** The generic fields are sent; whether CoinePro-FX's `kyc/level1` accepts
  them is the backend's to confirm. Until it does, a non-Iranian submission is refused in the
  server's own words rather than silently shaped into a national id.
* **1.7, gating the `.ir` link.** Kept, deliberately — it is the owner's backend and the recovery
  e-mail already sends it. The document says how to move it when a brand domain exists.

## Decisions for the owner

1. Confirm the KYC contract with the CoinePro-FX backend (`country`, `document_type`,
   `document_number`), or say the app should keep refusing non-Iranian identities client-side.
2. Serve `assetlinks.json` on both recovery hosts with the release fingerprint
   (`scripts/release/print-assetlinks.sh`), or accept that recovery links open the browser.
