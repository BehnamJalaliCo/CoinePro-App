# Before / after — what the audit changed on screen

Read against the source at each phase's commit. "Before" is 4.32.1, the APK the audit was written
from; "after" is `main` at the end of Phase 7. Where a difference can only be seen on a phone it is
said rather than claimed.

## Copy

| | Before | After |
| --- | --- | --- |
| Voice | «دارایی‌های تو» beside «وارد شوید»; «استراتژی را بنویس»; «ببین پرو چارت …» | «شما» and the plural imperative everywhere; Rasad alone speaks warmly, on purpose |
| Words | نمودار / چارت (36 / 56), آپ / اپ, کهنه, بازهٔ کندل, Interval, Bar length, واگرد, شیءها, «حقیقتِ ارائه‌دهنده», "this build", "served", "geometry", "deterministic" | one word per concept (§G); no engineering word survives the lint |
| Ezafe | «معاملهٔ», «نقشهٔ» (288 sites) beside «معامله‌ی» | «ه‌ی» throughout the interface; legal prose untouched |
| Feature descriptions | three families (menu, toolkit, search), up to 117 characters, sometimes contradicting each other | one `feature_<id>_body` per feature, ≤ 40 characters / ≤ 6 words, the same on all three screens |
| Rasad | "Rasad" alone in English | "Rasad — your market briefing"; «رَصد — گزارش بازار تو» |
| Enforcement | none | `tools/i18n/lint_strings.py` in the consistency gate and CI |

## Chart

| | Before | After |
| --- | --- | --- |
| Fling | private exponential decay, 0.997/ms; a flick coasted ~393 px from 1 200 px/s at any density | the platform's `SplineOverScroller` curve, density-aware; distance and duration fixed at release and tested to the pixel — a flick on the chart coasts like a flick on the list |
| Pane sync | «Syncing the crosshair and the visible range between panes is not ready yet» | crosshair (by moment) and window (bars per view, offset, stretch) shared across panes; the source keeps its own finger |
| Haptics on the canvas | none | a tick when the magnet takes a point; a tick when the crosshair crosses a stop, a target, an indicator line |
| Seconds bars | grew without bound while the screen stayed open | ring of 2 000; the archive keeps the rest |
| Script errors | Persian only, «خط ۳: …» hard-coded on the card | both languages carried, the card picks; "Line 3, column 12" |
| Frame budget | unmeasured | `ChartFlingBenchmark` + `check-benchmark-thresholds.py` (P95 ≤ 8 ms); the number needs a phone |
| Grid, wicks, axis padding, candle body | measured against TradingView already (`#282828` grid at full alpha, 0.8 dp hairline, 10 dp axis padding, 72 % body, pixel-snapped) | unchanged — the audit's "flat" reading came from the spinner-and-note shell around the chart, not the chart |

## Shell

| | Before | After |
| --- | --- | --- |
| Navigation slide | 240 ms tween | default-spatial spring; the back gesture seeks through it (`enableOnBackInvokedCallback`) |
| Loading | gold spinner on markets, search, signals | skeleton rows in the shape of the list, staggered in |
| Widget | followed the starred list, no way to say otherwise | asks which watchlist on placement |
| Icons | own set; two stock-Material uses in the audit's reading were the app's own `Filled.Chart` | a gate refuses `androidx.compose.material.icons` outside the design system |
| Digits | six copies of `String.format(Locale.US, …)` | `NumberStyle`, one place |
| Fonts | Regular + Bold IRANYekanX; digits already tabular | unchanged; Medium / SemiBold await the owner's licence |

## Build and trust

| | Before | After |
| --- | --- | --- |
| Admin panel | in the store APK | absent (R8 folds `BuildConfig.ADMIN_PANEL`; `check-release-surface.py` reads the APK) |
| ABIs | universal | arm64-v8a + armeabi-v7a; AAB beside the APK; download size gated at 9 MiB |
| Pinning | none | `CertificatePinner` behind `COINEPRO_CERTIFICATE_PINS`, documented; inert until the owner supplies pins |
| KYC | Iranian national card only | country + document type + number; `national_id` for Iran, `country / document_type / document_number` otherwise |
| Legal | terms in Persian; URLs scattered | `TERMS_EN.md`; every legal URL from `BrandConfig.LEGAL_BASE_URL` |
| Crash | copy only | «ارسال گزارش» hands the trace to the share sheet |

## What a phone would show that this file cannot

Frame times during the fling (the benchmark), the spring's feel against the tween's, the haptic
ticks, the predictive-back preview, and the widget's configure sheet in a launcher. Each has the
command or the screen named in the phase reports.
