# Changelog

Every version of the CoinePro Android app, what is in it, and which commits made it.

The format follows [Keep a Changelog](https://keepachangelog.com); the numbers follow
[semantic versioning](https://semver.org). `docs/VERSIONING.md` sets out how a version name becomes
the `versionCode` Android actually enforces.

**On the `0.x` entries.** They are named retroactively. The work happened as one continuous run and
only `1.0.0` has ever been built and handed to anybody — so nothing below `1.0.0` is a release that
somebody once installed. They are here because a version is only useful if it names something you
can point at, and each of these names a capability that was not there the commit before. Where a
version fixed something rather than adding it, it is a `PATCH` bump, which is the scheme doing what
it is for.

---

## [1.16.0] — 2026-08-27 — Sharing the chart

### Added
- One button captures the chart itself — not the screen — and hands it to the system share sheet.
  The phone already has a screenshot key; what a reader wants is the chart without the status bar,
  the navigation bar and the toolbar around it.

### Security
- A FileProvider scoped to one cache directory and nothing else. A provider over `files/` would
  offer the reader's cached quotes and a journal export to any app that could be persuaded to ask.
- The previous share is deleted before the next is written, so the app does not accumulate a hidden
  folder of every chart anybody ever sent.
- The URI is granted for one intent rather than the file being made readable to everything.

---

## [1.15.0] — 2026-08-27 — Saved chart layouts

### Added
- A layout keeps the chart type, the timeframe and the indicators that were on. Setting a chart up
  the way somebody likes it is eight or ten taps, and it is the same eight or ten every time they
  open a different symbol.
- Applying replaces the indicator set rather than merging it. A layout that added to whatever was
  already on would drift towards every indicator being on at once — the state a layout exists to
  escape.
- Drawings are deliberately not saved. A trend line is anchored to one instrument's prices and
  dates; a layout carrying drawings would paste last week's lines onto whatever chart it was applied
  to, at prices that mean nothing there.
- An unknown indicator id is skipped rather than failing the apply, so a layout saved by an older
  build loses one line instead of the whole layout.
- A name containing a record separator is refused rather than sanitised: silently renaming
  somebody's layout is worse than not saving it.

---

## [1.14.0] — 2026-08-27 — Keyboard shortcuts on the chart

### Added
- Digits pick a timeframe, space plays and pauses replay, the arrows step a bar, Escape cancels a
  drawing and Z undoes one. For tablets, DeX, Chromebooks and anything with a Bluetooth keyboard —
  on all of which a chart that ignores the arrow keys feels like a phone app being tolerated.
- Nothing is keyboard-only. Every shortcut has a control on screen; a shortcut that is the sole
  route to a function is a function most readers do not have.
- Key-down only. Android delivers both edges, and acting on both fires every shortcut twice — which
  on a timeframe key is invisible and on a step key is two bars.
- Left and right mean back and forward in *time* even in the right-to-left interface, because the
  chart's time axis runs left to right in every terminal and a trader comparing two screens must not
  have to reverse one of them in their head.

---

## [1.13.0] — 2026-08-27 — The order ticket that can honestly exist

### Added
- The setup drawn on the chart can be taken as a paper trade: same side, same entry, sized by the
  risk the reader entered. One tap from the numbers to the position.
- Deliberately not a real order. Neither backend serves a free-form one — TradeYar executes against
  a *published signal*, CoinePro-FX mirrors a copy account — so a button claiming to place a trade
  would be a button that cannot. The note under it says no order is sent anywhere, and
  `docs/REQUEST4_ACCOUNT_DELETION.md` asks both servers for the real route.

---

## [1.12.0] — 2026-08-27 — Switching symbol without leaving the chart

### Added
- A watchlist strip on the chart. It replaces four taps — back, search, type, open — repeated every
  time somebody compares two instruments, which is most of what looking at charts is.
- The reader's own list and nothing else. A strip of "popular" symbols would be a second market list
  on a screen that is not one; if the watchlist is empty the strip is absent rather than filled with
  suggestions.
- The current symbol scrolls itself into view, so opening the chart on the ninth of a list does not
  show a strip apparently starting somewhere else.
- Switching replaces the chart rather than stacking one on another: flipping through six symbols
  must not build a six-deep back stack that takes six presses to leave.

---

## [1.11.0] — 2026-08-27 — A backtest on the chart

### Added
- `core:backtest`: three named rules — moving-average cross, RSI reversion, channel breakout — run
  over the bars already loaded, on the device, with no request. The web terminal backtests an
  arbitrary language on a server; this answers the question a reader actually has, which is whether
  an idea survives the last thousand bars.
- Every choice is the pessimistic one, because a backtest that flatters is worse than none. A signal
  on bar *n* fills at the **open of bar n+1** — filling at the close that produced it is the most
  common way a backtest invents money. Anything still open closes at the final bar. Costs default to
  five basis points, not zero: a rule that flips every few bars is a fortune at zero and ruinous at
  five, and that difference is usually the whole finding.
- Maximum drawdown is measured peak to trough rather than start to end — the number that decides
  position size, and the one a flattering backtest leaves out.
- Long only, and the sheet says so: a short needs a borrow and a funding rate that a bar series does
  not know, and silently shorting answers a question about a position the reader could not hold.

---

## [1.10.0] — 2026-08-27 — Paper trading

### Added
- `core:papertrade` and `feature:papertrade`: open a position at the price on screen, close it at
  the price on screen, keep the record. For the reader who has installed the app and has not funded
  an exchange account — which, given that membership needs fifty tether, is most first-day readers.
- The entry price is the one the screen was showing at the moment of the tap, passed in rather than
  fetched inside. Filling at a number the reader never saw teaches them something untrue about
  market orders.
- A closed trade marks at its exit, never at today's price, so yesterday's win does not grow every
  time the screen is opened.
- The record counts only closed trades: an open position's profit changes while being read, and a
  win rate that moves when nothing happened is not a win rate.
- What is not modelled — fees, spread, swap, funding, slippage — is stated on the screen rather than
  left to be discovered against a real fill. A simulation that guesses a broker's fee schedule
  produces a number that looks like a real result and is not.

---

## [1.9.0] — 2026-08-27 — A trading journal

### Added
- `core:journal` and `feature:journal`. The signals list holds what the service published and the
  portfolio holds what the broker executed; neither holds the record that actually changes how
  somebody trades — what they thought at the time and what they would do differently.
- Everything optional but the symbol. A journal is written in the ninety seconds after a trade
  closes, and a form demanding four numbers first is a journal abandoned in the second week.
- Statistics that exclude ungraded rows rather than averaging them as zeros, and say how many were
  excluded. An entry with no P&L is not a break-even trade.
- Profit factor is null where there is no loss to divide by: three winners is not an infinite
  profit factor, it is one not yet produced.
- Tag filtering, with the statistics recomputed over the filter — a reader tapping "بریک‌اوت" is
  asking what their breakouts do.
- CSV export with a UTF-8 byte-order mark, shared rather than written to a file the app then owns.
  Without the mark Excel opens a Persian journal as mojibake, and nobody exports twice.

### Changed
- The database is at version 2 with a written migration rather than destructive recreation. Every
  other table in it is a cache; the journal is the one thing that cannot be refetched.

---

## [1.8.0] — 2026-08-27 — Somewhere to create a price alert

### Added
- `feature:alerts`: create, mute and delete. The gateway had existed since the notification work
  and had no screen, so the app could *receive* a price alert while offering nowhere to make one —
  every alert in the list had come from somewhere else.
- All five conditions, not just above and below. "Above" fires immediately on an instrument that has
  been above the level all week, which is usually the opposite of what was wanted; the crossing
  conditions fire on the transition. Telling them apart is the difference between an alert that
  arrives when something happens and one that arrives the moment it is set.
- Once by default, recurring behind a switch with the reason written next to it: on a price
  oscillating around the level, recurring is a phone that will not stop while its owner is asleep.

---

## [1.7.0] — 2026-08-27 — The guest market is the real one

### Changed
- The guest shelf is chosen from the feed's actual universe instead of eight tickers compiled into
  the app months ago. The first read asks for everything, takes the twenty busiest, and polls only
  those.
- The order is fixed at that first read and never recomputed. A list that re-sorts itself by volume
  every ten seconds rearranges under the reader's finger — the market decides what is on the shelf,
  not where each thing sits while somebody is looking at it.
- The header says how many markets there really are. Twenty rows with nothing saying otherwise is a
  much smaller product than the one the feed carries.

---

## [1.6.0] — 2026-08-27 — What the signals actually did, before the sign-up

### Added
- A track record on the guest screen: real published signals that have already closed, with the
  outcome the server recorded. Above the membership card, not below it — a card asking for a
  sign-up before showing what the signals did is asking for trust it has not earned.
- The win rate is the server's own count by its own ladder definition, not recomputed here. The
  route says in as many words that a client must not, and two different win rates in front of one
  reader is worse than none.
- `data_available` is read rather than inferred from an empty list. Empty-because-the-query-failed
  and empty-because-nothing-closed are different sentences to put in front of somebody deciding
  whether to trust the product, and the section is hidden rather than showing either.

---

## [1.5.0] — 2026-08-27 — A watchlist

### Added
- A star on every market row, on Home and in search, and the reader's own list above the market on
  Home. The most-used control in a trading app, and the app did not have one.
- Order is insertion order, oldest first — not alphabetical and not by price. The reader put them in
  a sequence and the sequence is information; a personal list that rearranges itself while being
  read is the one thing it must never do.
- Local, in the same preferences file as every other choice. TradeYar does serve a watchlist, but
  behind its own device-link flow — a second identity to establish before the first star. A round
  trip per star turns the most-tapped control into the slowest one; sync belongs on top of this,
  not instead of it.
- One list across both platforms. A reader watching gold and bitcoin has one list, and splitting it
  would mean the star they pressed vanishing when they switched tab.

---

## [1.4.0] — 2026-08-27 — What the setup on the chart is worth

### Added
- The `longshort` tool has always drawn three lines. This is the other half: entry, stop and target
  as prices, the two distances, the stop in pips, and the risk-to-reward ratio — from
  `TradeFromChart`, the web terminal's own trade maths, which had been ported, tested and wired to
  nothing.
- Position size from a risk amount. Asked for as "how much are you prepared to lose", never as a
  lot size: risk decides size, and the reverse question is the same arithmetic with the opposite
  habit.
- The side is read off the geometry — a stop below the entry is a buy — so the numbers cannot
  disagree with the lines on screen. A setup whose stop sits on its entry is refused rather than
  printed, because a ratio there would be read as a real one.

---

## [1.3.0] — 2026-08-27 — Bar replay, reachable

### Added
- Replay is on the chart. The engine had been ported and tested for weeks and wired to nothing —
  dead code with a green test suite, which is the worst kind, because the tests say it works and
  nobody can use it.
- A transport bar that says loudly that replay is on: a reader who forgets is reading a live chart
  that is hours stale. Leaving is one tap and never behind a menu.

### Fixed
- Indicators and structure levels now compute over the *visible* slice. Derived from the whole
  series they would place a moving average using prices the reader is not allowed to have seen —
  the future leaking back in through the one door nobody watches.

---

## [1.2.1] — 2026-08-27

### Fixed
- **The trader toolkit was half in English.** Fifty-six hard-coded English literals — every
  calculator's field labels, units, result rows and assumption notes — on the screen a Persian
  trader uses to size a position. It had only ever been rendered in the English locale, which is
  the reason nobody saw it.
- Two more of the same: an `executions` metric label on Activity, and `n=1` as a sample-size
  marker.
- Latin numerals in three prose counts, where the app's own rule says Persian.

### Added
- Persian renders of the six screens that had only ever been captured in English — tools,
  activity, connections, news, calendar and launch readiness. A screen the app ships in Persian and
  has only been looked at in English has not been looked at, and this is where its bugs live: a
  Latin figure inside a right-to-left paragraph reorders, a label and its value swap ends, a unit
  lands on the wrong side of its field.

---

## [1.2.0] — 2026-08-27 — One market row, at the density the job needs

### Added
- `CoineProMarketRow` — the row Home, search and the guest market now share. There were three,
  and they had already drifted: different logo sizes, different vertical rhythm, the percentage as
  plain text in two of them and absent from the third.
- `CoineProPercentPill`. Coloured text has to be found before it can be read; a filled block is
  found before it is read, which is the actual job — someone scanning a market list is looking for
  *which rows moved*, not for any particular figure. The fill is the system's 8% tint rather than a
  flat alpha, so it is the same colour on a card, on the stage, and in the light theme.
- `CoineProRangeBar` — where the price sits between the day's low and high. Two numbers the feed
  already carries and almost no app draws: 64,180 says nothing, 64,180 at the top of a
  62,800–64,900 day says the thing a reader opened the app for. It sits *beside* the pill rather
  than under it, so the range costs no extra row height.
- A hairline between search results, inset past the logo. Sixty-five markets on a bare stage had
  nothing for the eye to count by.

### Changed
- The guest market went from a card per quote — a stack of blocks to scroll — to one card of dense
  rows to scan. A market list is read by comparison, and comparison needs the rows close enough to
  hold in one glance.
- The guest lockup is smaller. A logo taking a fifth of the first screen is a brand announcing
  itself; the live market underneath is what does the convincing.

### Fixed
- Persian numerals in the membership steps. They were hand-written `1.` `2.` `3.` — Latin digits in
  a prose count, which is the app's one number rule and the way it actually gets broken: nobody
  writing a numbered list thinks of themselves as formatting a number.
- Three copies of the Latin-to-Persian digit helper became one, in `core:common` beside its
  opposite, with the market-figure exception written down where both live.

---

## [1.1.1] — 2026-08-26

### Changed
- The membership card and the landing page no longer explain where the money comes from. They say
  what a reader needs to decide — membership is free, the condition is a funded account at a
  partner exchange — and stop there. Explaining a commercial arrangement to somebody who asked to
  see a signal changes the subject, and reads as a disclosure rather than an offer.
- The terms keep a short, neutral clause about the partnership, because §6-2 makes registering
  through CoinePro's link mandatory and a condition with no stated reason is a worse document. It
  says the two things a reader actually needs: it costs them nothing extra, and CoinePro never
  holds their funds.
- "Sub-account" and "referral link" are gone from every reader-facing string, in both languages.
  The account is *linked to* CoinePro through a *dedicated link* — the same fact, without the
  vocabulary of somebody else's affiliate programme.

---

## [1.1.0] — 2026-08-26 — The app opens without an account

### Added
- **Guest mode.** The app used to open on a sign-in form, which asks for a password before giving
  any reason to have one. It now opens on the market: real prices from TradeYar's public feed, the
  published headlines, and the membership route explained underneath. The form appears when the
  reader asks for it — or straight away when a recovery link says they are already mid-flow.
- `core:guest` over the public routes the server has published all along for its own web site and
  the app never called. No token, so nothing here can log anybody out.
- A membership card that is not a paywall, because there is no wall: the four steps are stated in
  full, including the one that cannot be undone — an account opened without the referral link is
  not a sub-account in the exchange's own system and cannot be verified afterwards.
- **Deleting an account**, in the app and on the web. What goes, what stays anonymised and for how
  long, and that it does not close the exchange account. It asks for a typed word rather than a
  second tap, and where the server has no route it hands over the published page instead of a
  button that would fail.
- `PageAccent.DESTRUCTIVE` — a fourth accent that means "this cannot be undone", deliberately
  unavailable to a cancel or a sign-out.
- `MarketNumberFormatter.priceAuto`, which takes its decimals from the price's own magnitude. At a
  fixed two, XRP read 0.52 and a sub-cent coin read 0.00 — not a rounding but a claim that the
  asset is worthless.
- The privacy policy, terms and deletion page published as real pages, rendered from
  `docs/legal/*.md` so there is no second copy of the text to drift.

### Changed
- Terms §6 says what the service actually charges: nothing. Membership is free, and its condition
  is a funded account at a partner exchange with a 50 USDT floor — `VIP_MIN_DEPOSIT` in the
  server's own configuration — rather than a purchase. Copy trading is LBank only, because Ourbit
  does not offer it.

### Fixed
- `AccountApi`'s four paths carried CoinePro-FX's `user/mobile` prefix for both platforms. TradeYar
  serves them under `api/mobile/v1`, so briefing, portfolio and both KYC calls had been answering
  404 on the crypto platform.

---

## [1.0.0] — 2026-08-26

**The first release that installs, updates, and can be named.**

Everything below this line was work. This is the first build that leaves the machine it was made
on: signed with the permanent key, published by CI, and carrying a version that means something to
whoever is holding the phone.

### Added
- `version.properties` as the one place the app's version is written, with `versionCode` **derived**
  from it — `MAJOR×10,000,000 + MINOR×100,000 + PATCH×1,000 + BUILD` — so the integer Android
  enforces can never drift from the name a person reads. `docs/VERSIONING.md` has the widths and
  why they are those widths.
- `scripts/release/version.py` — the same arithmetic for CI and for the command line, with
  `--bump major|minor|patch`, `--json`, `--check`, and range checks that fail the build rather than
  wrapping. A wrap would produce a *lower* code, which is the one outcome that breaks updates on
  every device at once.
- `BUILD` counted as commits since the last version bump, so every push produces a strictly higher
  code with nobody having to remember anything, and the counter resets the moment a version is named.
- Semver build metadata on the device — `1.0.0+4` — so a bug report names the exact build rather
  than the nearest version. Git tags spell the same thing as `v1.0.0-b4`, in characters a refname
  and a URL both take unescaped.
- This changelog, covering the whole history rather than the last entry.

### Changed
- CI stopped inventing versions. It computed `run_number + 1000` and called the result
  `0.1.<code>` — monotonic, which was all it had to be, but `0.1.1004` was not a patch of
  `0.1.1003`; it was whatever landed that day. `1.0.0` lands at `10,000,000` and clears the whole
  of that old series with room to spare, so no build already in the field is stranded.
- `android-ci.yml` no longer pins `16.0.1-ci`, which read as a version 16 that has never existed —
  the 16 was a phase number.

---

## [0.27.0] — 2026-08-26 — A signed APK on every push

### Added
- `.github/workflows/android-apk.yml`: every push to `main` produces one installable, signed APK on
  a GitHub Release. One track, one signature, so an update **installs over the last one** instead of
  asking the reader to uninstall and lose their session.
- The workflow refuses to run without the four signing secrets rather than generating a throwaway
  key — a generated key installs fine exactly once, and then every later build is rejected by every
  device that took the first.
- `keytool -list` validates the alias and passwords before Gradle spends ten minutes finding out.
- `docs/CI_APK_SETUP.md`, mapping each secret to where its value comes from.

`99a87e3`

## [0.26.0] — 2026-08-26 — Sign-in that tells the truth

### Removed
- The Telegram Login Widget. It could never have authenticated anybody: the widget asks Telegram to
  sign a payload for the embedding page's **origin**, and Telegram checks that origin against the
  domain the bot's owner registered with BotFather. The origin was `telegram.org`, which nobody can
  register, so Telegram refused every time and drew its own error inside the frame — which is the
  "bot admin" error the owner was seeing. A mobile app has no page, so it cannot be fixed in place;
  the supported shape is a bot deep link plus a server route, asked for in
  `docs/REQUEST3_COINEPROFX.md`.

### Changed
- Google sign-in now says what is actually wrong. `NoCredentialException` and the "developer console
  is not set up correctly" family are not about the reader at all — they mean this build's signing
  certificate is not registered in the Google Cloud project that issued the server client id. A
  reader told "no account found" goes and adds a Google account, which cannot help.
- The auth screen renders the state it actually serves, rather than one that no longer exists.

`bfbeee5`, `2ef3406`

## [0.25.0] — 2026-08-26 — The release build stopped lying

### Fixed
- **R8 was renaming every field Gson serialises.** Nine request bodies are not named `*Dto` and none
  carried `@SerializedName`, so full mode renamed them to single letters and the shipped app posted
  `{"a":…,"b":…}` to every auth route. It is why sign-in failed in release and worked in debug.
- The check is now on the **dex**, not the keep rules. `mapping.txt` only lists *renamed* members,
  so a field's absence there is not evidence it survived — `scripts/quality/check-release-wire-fields.py`
  reads the built APK with `dexdump` and asserts ten wire field names are in it. The shipped build
  had one field named `email`; the fixed build has five.
- The `«؟»` help catalogue was entirely dead code, and R8 was right to remove it. It is now wired to
  the sheet that shows it, and what was genuinely unreachable stayed removed.
- `@HiltWorker` modules and assisted factories were being stripped, taking background refresh with
  them.

`88c716b`, `00da1d5`

## [0.24.0] — 2026-08-26 — Everything the store asks for

### Added
- `docs/PLAY_LISTING.md` — the listing pack: title, short and full description, category, content
  rating answers, data-safety declarations, and the release blockers that are the owner's to clear.
- `docs/legal/PRIVACY_POLICY.md` and `docs/legal/TERMS.md`, in Persian.

`dd36a51`

## [0.23.0] — 2026-08-26 — The untested modules, tested

### Added
- `core:security` covered, including `SessionCipher` extracted as a seam so the cipher can be tested
  without a device keystore.
- `core:model` covered.
- A real baseline profile, recorded against the current theme class, replacing the stub.

`baedc59`

## [0.22.0] — 2026-08-26 — One design language, enforced

### Added
- The `foundation-v2` token layer adopted whole: the five-step surface ladder, three border weights,
  the 8% fill / 34% border tint formula as `Color.lerp` rather than alpha, and 100/160/240 ms on
  `cubic-bezier(.2,0,0,1)`.
- `LocalPageAccent` — one primary-button component with a per-domain identity, so Markets, Trade,
  Copy and Subscribe each read one variable instead of each screen inventing a colour.
- Press feedback with the right proportions: a bigger surface compresses less.

### Changed
- Two golds are no longer interchangeable. The brand gold is an *ink*, not a *fill* — using it as a
  fill produced a near-black button on dark brown in the light theme, which the light design-kit
  render caught.
- The four accents became two. Two of the golds were indistinguishable on a phone; premium is now
  marked by treatment rather than by a colour nobody could tell apart.

### Security
- `scripts/quality/check-motion-policy.sh` now enforces the discipline rules — no blur, no coloured
  glows, gradients only in the five allow-listed places.

`f066647`

## [0.21.0] — 2026-08-26 — The professional terminal

### Added
- `feature:terminal`: the full Bazaarnama terminal — 52 drawing tools, `namascript` with its editor,
  bar replay, the strategy tester — behind one button and one build property. An ordinary reader
  never sees a WebView; a power user gets everything.

`13ca899`

## [0.20.0] — 2026-08-26 — The academy

### Added
- `feature:academy`: the curriculum roadmap, a lesson, and its quiz, over the `/academy/*` routes
  CoinePro-FX already serves.
- Badges, the leaderboard and the glossary, which had nowhere to be seen.
- Lesson HTML rendered to `AnnotatedString` with the bullets **written into the text** — `BulletSpan`
  paints at a fixed left offset, which is the wrong side in RTL.

`b4dadad`, `dc7fda9`

## [0.19.0] — 2026-08-26 — The portfolio

### Added
- `feature:portfolio`: closed trades, an equity curve, per-symbol attribution, a monthly breakdown,
  drawdown, win rate, profit factor and expectancy — computed in `core:portfolio` and tested.
- No borrowed statistics. Every figure is derived from the trades the server returned; nothing is
  carried over from the web app's own numbers.

### Fixed
- Drawdown rendered as `9.6%)( -$4,475.13`. Two adjacent LTR isolates reorder as runs inside an RTL
  paragraph; one isolate around the whole phrase fixes it.
- The monthly bars had no labels, because `DrawScope` has no access to resources. They are passed in
  from the screen.

`b77efd8`

## [0.18.0] — 2026-08-26 — The chart, reachable

### Added
- The chart mounted in a screen and reachable from the app.
- The setup drawn — entry, stop, targets — on both screens that show one.
- The nine new indicators documented in the `«؟»` catalogue.

### Fixed
- The chart preview bled to the screen edges in signal detail. It is in a card, without volume.

`4075b37`, `b551b67`, `d1297a2`

## [0.17.0] — 2026-08-26 — Real candles

### Added
- Candles from both backends against the contracts they published — forex from the `candles` table
  the EA pushes (broker-true prices), crypto live from LBank — on the compact `{t,o,h,l,c,v}` shape
  both already speak.

`756ba88`

## [0.16.1] — 2026-08-26

### Fixed
- No symbol without artwork reaches a list. `SymbolArtwork.covers` is the filter, at the catalogue
  and at the live feed — no blank squares, no lettered discs.

### Added
- TradingView's whole logo set, rather than the twenty-three that had been taken by hand.

`0f534bd`, `61b43a9`

## [0.16.0] — 2026-08-26 — Ninety-five indicators

### Added
- Twenty-nine more indicators, each checked against the app it came from.
- The seven structure studies — and one place where the original is wrong, documented rather than
  reproduced.

`9cffea0`, `2b74136`

## [0.15.0] — 2026-08-26 — The chart's own controls

### Added
- Every control drawn with its real icon, and the fifty-two drawing tools brought across.
- A finger can place a tool: points are stored in chart space `{t, p}`, so pan and zoom are free.

### Changed
- The three chart sheets share one chrome instead of repeating it three times.

`454f65a`, `b255c8b`, `5496267`

## [0.14.0] — 2026-08-26 — The «؟»

### Added
- The help catalogue brought across — 177 entries — and the pickers it hangs off, so every indicator
  and every tool can say what it is where it is used.

`f6a411e`

## [0.13.1] — 2026-08-26

### Added
- The Gradle wrapper, so the build does not depend on whatever Gradle happens to be installed.

`3ab4159`

## [0.13.0] — 2026-08-26 — The chart, drawn

### Added
- The Compose Canvas renderer: candles, Heikin-Ashi, line and area, with pan, zoom and a crosshair.

`cc6edc3`

## [0.12.0] — 2026-08-26 — The chart engine

### Added
- `core:chart`, ported from `proChart.js` — itself already a port from Flutter, so explicit pixel
  maths rather than DOM.
- Proved against the original: both run over the same candle fixture and the outputs are asserted
  equal to 1e-9.

`ee9c2b0`, `51148bd`

## [0.11.0] — 2026-08-26 — Knowing what a symbol is

### Added
- `core:symbols`: the classifier (metal / energy / index / forex / crypto), Persian display names,
  the `FOREX_ALIAS` table, liquidity ranking, and client-owned market status — the server's flag was
  unreliable per category, and weekends are the client's to know.
- The ranked fuzzy matcher: exact 1000, prefix 800, substring 600, subsequence 300, +25 for
  popularity, searching **the Persian description as well as the ticker**, tie-broken by liquidity
  rather than alphabetically.
- `feature:search` on top of it — 80 ms debounce, category chips, recent searches, live prices for
  the visible rows, match highlighting. It replaced a substring filter.

`63dabab`

## [0.10.0] — 2026-08-26 — An interface icon set

### Added
- The whole TradingView icon set rather than the twenty-three chart-toolbar glyphs.
- The app's own eleven — the icons that name its sections.
- A drawn navigation set, after the three exchanges' own sets could not be obtained.
- The bottom bar taken from the exchanges' own sets.
- `design/ui-icons/README.md` states plainly what each set is and where it came from.

`53dd8a9`, `d62664c`, `ac59b25`, `4118e73`, `33e9a66`

## [0.9.2] — 2026-08-26

### Fixed
- An oversized symbol falls through to its raster instead of to a letter. A lettered disc is what
  "failed to load" looks like.

`3e6d140`

## [0.9.1] — 2026-08-26

### Fixed
- The SVG converter learned no-op clips and real gradients, which is what Binance's set needs, so it
  could lead rather than be the fallback.

`748665f`

## [0.9.0] — 2026-08-26 — Every instrument has a logo

### Added
- Every symbol either backend can quote, not eight. The hand-written map is gone; the lookup is
  generated.
- The forex pairs drawn as a two-disc composition — base flag in front, quote flag behind with a
  notch ring — positioned physically, so a pair reads identically in RTL and LTR.
- The four metal discs.

### Fixed
- A third of the instrument logos rendered wrong. Gold in particular looked like the one that failed
  to load.

`84e99b2`, `b6a14ab`

---

## [0.8.0] — 2026-08-25

### Changed
- Every brand raster regenerated from the supplied transparent master.

`9c181ae`

## [0.7.0] — 2026-08-25 — Copy trading

### Added
- `core:copytrade` against CoinePro-FX's copy-status and copy-config.
- The copy-trading screen replaces the execution screen on the forex platform, which is what that
  backend actually offers.
- The plan name shown in Persian from `plan_fa`; the AI capability flags consumed rather than assumed.

`02d7c7d`

## [0.6.0] — 2026-08-25 — Diagnostics

### Added
- `core:diagnostics`: a request log and a catalogue of every route, with a prober that says which
  are live.
- An admin control hub with per-platform sections, behind five taps on the version number.

`49d4ef9`, `b7b438b`, `473dd00`, `2627307`

## [0.5.0] — 2026-08-25 — Two backends, two identities

### Added
- Email-first sign-in, registration and password reset, matched to each backend's real auth shape
  read from its own source.
- Google sign-in through Credential Manager, with the audience taken from `auth/methods` rather than
  compiled in — the two deployments have separate Google configuration and a token minted for one
  has an `aud` the other refuses.
- Refresh tokens stored alongside access tokens, renewed on schedule and on a 401 before signing out.
- Per-platform gateways, with every path pinned in tests.
- The HTTPS App Link, pointed at the host that already serves `assetlinks.json`.

### Fixed
- English protocol strings were being shown to Persian readers as if they had been written for them.
- The `coinepro://` intent filter was accepting more shapes than it should.
- A per-platform install id, so readers behind the same CGNAT address do not share a rate-limit
  bucket.

`7b0c802`, `f2bbff4`, `edbcc51`, `871956e`, `1d0c4a9`, `4057ccc`, `9a35136`, `f293892`, `481cb37`

## [0.4.0] — 2026-08-25 — The «آرام» direction

### Changed
- Home rebuilt in the settled visual direction, then the whole app behind it: chrome, type and
  spacing scale, signals, the AI section, connections, execution, chart analysis, the assistant,
  signal detail, market news, the calendar, sign-in, safety, activity and the trader toolkit.
- A light theme.
- Motion that carries information rather than decorating.
- A mixed market became impossible to produce — the platform is a property of the screen, not of a
  row.

`3112de7` … `6dab604`

## [0.3.0] — 2026-08-24 — The app's own identity

### Added
- Persian as the default language, and the RTL foundation under it.
- IRANYekanX typography.
- The CoinePro identity and a launcher icon, from the black-ground masters.
- The asset logo artwork and a real UI icon family.
- Screens rendered to PNG without an emulator, which is how every visual change since has been
  reviewed.

`042874e`, `e499cb4`, `3080919`, `a95cf59`, `bebcbbe`, `1c75da0`, `823e017`

## [0.2.0] — 2026-08-24 — Release engineering and launch safety

### Added
- A protected release-engineering pipeline, a staging build identity, and a reproducible signed App
  Bundle path with signing material supplied from outside the repository.
- Manual Play Console internal-track publishing for the staging package.
- Notification-permission education and a denial-recovery path, shown *before* the platform prompt.
- Connection setup that distinguishes credentials being configured from a provider having confirmed
  them.
- A feedback and share path carrying app version and environment only.
- `docs/PHASE17_INCIDENT_RUNBOOK.md`, and a read-only production smoke that never mutates anything.
- A permanent Phase 1–17 repository consistency gate.

### Changed
- Signal navigation requires a positive server signal id, consistently, everywhere it is used.
- Deep-link parsing restricted to the CoinePro scheme and the supported route shapes.
- The Room market cache rejects out-of-scope rows on write *and* on restore.
- Release documentation distinguishes this repository's syntax and range checks from the
  cross-release monotonicity Play enforces.

### Security
- Release keystores and service-account credentials are external secrets and are never committed.
- Benchmark builds use non-routable configuration instead of inheriting production endpoints.
- External legal, provider and production evidence is never synthesised from CI, mocks or cache.

## [0.1.0] — 2026-08-23

Initial native Android product milestone covering the Phase 0–15 application surface and quality
gates.
