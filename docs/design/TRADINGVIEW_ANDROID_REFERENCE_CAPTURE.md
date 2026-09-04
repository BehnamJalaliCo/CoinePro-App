# Capturing the TradingView Android reference pack

The checklist for producing the only kind of image
[TRADINGVIEW_VISUAL_PARITY.md](TRADINGVIEW_VISUAL_PARITY.md) will accept as a pixel golden.

> **Status: not captured.** No pack exists in this repository. Everything below is runnable and
> nothing below has been run — capturing it needs a device with the TradingView Android app
> installed, which this build host does not have and cannot have. Until somebody with that device
> works through this file, the TradingView layer of the gate reports `REFERENCE_MISSING` and the
> project's answer on parity stays `PARITY NOT YET PROVEN`.

---

## Before anything else

You need:

* a **Pixel 6**, or an AVD with the Pixel 6 profile at API 35, `1080 × 2400`, `420 dpi`;
* the **TradingView Android app** installed on it, signed in far enough to see a watchlist;
* `adb` on the host;
* nothing else running that can draw over the screen.

Capture the CoinePro side on the **same device, in the same session**, with
`VisualParityCaptureTest`. Two devices means two densities and two font renderers, and the comparison
is then measuring the difference between phones.

## 1. Pin the device

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system font_scale 1.0
adb shell settings put secure ui_night_mode 2          # 2 = dark, 1 = light
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0          # portrait
```

Then read every one of them back — the recorded values are what the device reports, never what was
asked for:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
adb shell settings get system font_scale
adb shell cmd uimode night
adb shell settings get secure default_input_method     # for the locale note
```

## 2. Read the app's own version off the device

Not from a changelog, not from a store page:

```bash
adb shell dumpsys package com.tradingview.tradingviewapp | grep -E "versionName|versionCode"
```

Both numbers go into the manifest. A reference pack without them cannot be told apart from the next
one, and `REFERENCE_VERSION_DIFFERENCE` becomes unarguable.

## 3. Take the screenshots

Direct PNG, off the framebuffer, never a share sheet and never a photo:

```bash
adb exec-out screencap -p > watchlist-dark.png
```

Verify each one before moving on:

```bash
python3 - <<'PY'
from PIL import Image
im = Image.open("watchlist-dark.png")
assert im.format == "PNG", im.format
assert im.size == (1080, 2400), im.size
print(im.size, im.mode)
PY
sha256sum watchlist-dark.png
```

Any file that is not a PNG at exactly the device's resolution is discarded. Do not fix it; take it
again.

## 4. The matrix

Dark theme:

| File | Screen | Notes |
|---|---|---|
| `watchlist-dark.png` | Watchlist | Scrolled to top. **At least ten rows visible** — five are measured for cadence and the rest prove the cadence does not drift. Load symbols of comparable asset classes to ours: crypto, an index, an FX pair, a metal. Prices need not match; row geometry does. |
| `chart-dark.png` | Chart | Default interval, no drawing tool selected, no crosshair. |
| `explore-dark.png` | Explore / markets home | Scrolled to top. |
| `ideas-dark.png` | Ideas | First face, scrolled to top. |
| `menu-dark.png` | Menu / more | Scrolled to top. |

Light theme (`ui_night_mode 1`), re-verifying the device after the switch:

| File | Screen |
|---|---|
| `watchlist-light.png` | Watchlist |
| `explore-light.png` | Explore |
| `menu-light.png` | Menu |

**Every bottom-bar state, and one capture is not enough.** The bar is five columns and a selection
treatment, and the selection is the only part that changes — so a single screenshot measures the
geometry once and the *state* never. Five captures, one per destination:

| File | Selected |
|---|---|
| `bottom-bar-watchlist-dark.png` | Watchlist |
| `bottom-bar-chart-dark.png` | Chart |
| `bottom-bar-explore-dark.png` | Explore |
| `bottom-bar-ideas-dark.png` | Ideas |
| `bottom-bar-menu-dark.png` | Menu |

Each is read for icon position and size, the selected treatment's bounds, the label baseline, the
column width, the divider and the vertical geometry — twelve named anchors, listed in
`visual-parity/specs/bottom-bar.json`.

Optional, and worth having:

| File | Screen |
|---|---|
| `search-dark.png` | Symbol search |
| `list-switcher-dark.png` | Watchlist switcher |

## 5. Normalise the state, not the content

The comparison is of **geometry**, not of what the market did that morning. So:

* the same number of rows on both sides;
* the same tab selected;
* no toast, no tooltip, no sheet, no keyboard, no notification shade;
* no crosshair, no long-press state, no drawing in progress;
* the list at the top, not mid-scroll, on both sides.

Prices, percentages, timestamps and avatars are expected to differ and are masked. Do **not** try to
make them match — a masked class is honest and a doctored screenshot is not.

## 6. File the pack

```
docs/design/reference/tradingview-android/<versionName>/
  reference-manifest.json
  watchlist-dark.png
  chart-dark.png
  …
```

Versioned by **their** app version, because a pack is a statement about one release of somebody
else's software. A new version of their app gets a new directory; the old one is never overwritten
and never deleted — see *Reference update rule* below.

## 7. The manifest

`reference-manifest.json` carries the environment once and each image's own metadata. Every field is
required; `verify_reference_manifest.py` refuses the pack otherwise.

```json
{
  "package": "com.tradingview.tradingviewapp",
  "versionName": "<read from dumpsys>",
  "versionCode": "<read from dumpsys>",
  "firstInstallTime": "<read from dumpsys>",
  "lastUpdateTime": "<read from dumpsys>",
  "device": "Pixel 6",
  "resolution": "1080x2400",
  "densityDpi": 420,
  "fontScale": 1.0,
  "orientation": "portrait",
  "theme": "dark",
  "locale": "en-US",
  "captureMethod": "adb",
  "captureDate": "<ISO 8601, with offset>",
  "capturedBy": "<who>",
  "crop": { "left": 0, "top": 96, "right": 1080, "bottom": 2280 },
  "sha256": {
    "watchlist-dark.png": "<sha256sum of the file>",
    "menu-dark.png": "<sha256sum of the file>"
  }
}
```

`crop` is how the system bars are taken out of the comparison, and its four numbers are **measured
off the device, not guessed** — the status bar's height and the navigation bar's are read with
`adb shell dumpsys window displays` or from the captured PNG itself. Cropping is not resizing: the
pixels that remain are exactly the pixels the device drew, and the same crop is applied to both
sides. Omit `crop` entirely if the system bars are made deterministic in both apps instead.

A capture must be either the full device resolution or exactly the declared crop. Anything else has
been resized, and the verifier says so.

### Language

Capture **English, left-to-right**. Both apps then draw the same direction, text is directly
comparable, baselines are baselines rather than mirror images, and far less has to be masked.
CoinePro's Persian right-to-left integrity is a separate question, asked by Layer A's own goldens —
see the policy document. Do not merge the two.

## 8. Check it in and verify it

```bash
python3 scripts/visual/verify_reference_manifest.py
```

Every checksum is recomputed. A pack whose files do not match its manifest is rejected, which is the
whole point of having one.

## Reference update rule

When TradingView ships a new version:

1. capture a new pack into a **new** directory named for the new version;
2. leave the old pack exactly where it is — it is the evidence behind every number already
   published;
3. run the comparison and produce a report;
4. have a person read it, and decide which differences are `REFERENCE_VERSION_DIFFERENCE` and which
   are ours;
5. only then move CoinePro's parity target.

CI never does any of this. It never overwrites a reference, never records a golden, and never
accepts a changed capture because a comparison failed. A gate that repairs its own inputs is not a
gate.

## What must never be done here

* Never substitute a web screenshot, a store image, a JPG, or a resized PNG. Not once, not
  temporarily, not "to get the pipeline running".
* Never scale a capture to make two sizes agree. A size mismatch is a finding.
* Never edit a capture — not to remove a badge, not to change a price, not to crop out something
  awkward.
* Never record a capture whose device settings were not read back off the device.
* Never patch, re-theme, re-scale or otherwise modify the TradingView app. The reference has to
  be what their official build renders, or it is a reference to something that does not exist.
