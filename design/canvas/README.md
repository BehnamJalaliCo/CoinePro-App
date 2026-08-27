# Design canvas — visual directions

Source artboards for the design canvas the owner picks a direction from. Each
`*.dc.html` is one phone frame (390×844), `canvas.json` lays them out and carries
the notes that state each direction's axis and its cost.

| File | Direction | Screen |
| --- | --- | --- |
| `Main.dc.html` | الف · ترمینال | Chart |
| `TerminalMarkets.dc.html` | الف · ترمینال | Markets |
| `PremiumChart.dc.html` | ب · طلایی | Chart |
| `PremiumMarkets.dc.html` | ب · طلایی | Markets |
| `ImmersiveChart.dc.html` | ج · چارت‌محور | Chart |
| `ImmersiveMarkets.dc.html` | ج · چارت‌محور | Markets |

These are **mockups, not the app**. Every colour in them is lifted from
`core/designsystem`'s real palette — `#0B0E11` stage, `#070A0F` terminal,
`#D8A848` gold, `#00B15C` / `#F6465D` — so a direction that looks right here
looks the same when it is built. Two substitutions are deliberate and are the
only places the mockups differ from the product:

- **Vazirmatn** stands in for IRANYekanX. The shipping face is licensed as a
  desktop and app font and has no web build, and a mockup in a face nobody
  recognises would be judging the wrong thing. Vazirmatn is the closest Persian
  grotesque with the same skeleton.
- **Lettered discs** stand in for the vendored asset logos, which live as Android
  vector drawables and do not render in a browser.

The candle series is generated deterministically by the script embedded in the
git history of this directory rather than drawn by hand, so the chart in a
mockup behaves like a chart: real wicks, a real EMA, a real RSI.

The seeded canvas page itself is not tracked — see `.gitignore`. Regenerate it
with the `design` skill's `seed-canvas.mjs`, passing every artboard above plus
`canvas.json`, then republish to the same artifact URL.
