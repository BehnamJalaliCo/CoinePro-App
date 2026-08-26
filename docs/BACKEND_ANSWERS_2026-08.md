# What the two backends answered, and what the app owes them

Both teams replied to `REQUEST_COINEPROFX.md` and `REQUEST_TRADEYAR.md` and shipped to production.
This is the app-side record: what changed on the wire, what is already wired here, and what is not
yet. It exists because three of the items below are **things the app must stop doing or start
doing**, and an answer that lives only in the other repository's commit message is an answer this
app will forget.

---

## CoinePro-FX

| Ask | Answer | App side |
| --- | --- | --- |
| Discovery mode on the snapshot | `GET /ws/snapshot` with no parameter now returns all 19 symbols. The cap of 20 applies only to an explicit list, and one unknown name no longer fails the whole request — it comes back in a new `unsupported` array. | **Done.** `NetworkMarketCatalogGateway` asks both platforms the same way now; `MarketSnapshotDto.unsupported` is parsed. |
| A symbol catalogue | `GET /user/markets` added. | **Not wired.** The snapshot already carries the catalogue, so this is a second route to the same fact; worth wiring only if it carries metadata the snapshot does not. |
| Reach the chart routes with a mobile token | `POST /user/academy-token` mints a 12-hour `scope=academy` token, switchable off with `MOBILE_ACADEMY_TOKEN_ENABLED`. | **Done.** `NetworkAcademyTokenStore` mints, caches in memory and renews five minutes early; `CoineProFxCandleGateway` and `NetworkAcademyGateway` both send it explicitly. |
| One token for the whole academy | Confirmed: the same token opens all ~40 `/academy/*` routes. Paid lessons stay locked for `tier: "free"` (`reason: "tier"`), and a VIP with no phone number on file gets `reason: "phone"` — both existing behaviour. | **Done.** `core:academy` reads the learning routes; both locks are told apart and each offers the action that clears it. |
| Trade history | All four routes were already open to the mobile token. Real JSON documented. | **Done.** `CoineProFxPortfolioGateway` reads `/user/trade-history`. The other three compute statistics this app computes itself — see below. |

### Do not display `max_drawdown_rel_pct`

Their team found the denominator is wrong and it reports values like 312 %. It is not a rounding
problem and it is not fixed. Nothing in this app reads it today, and this note exists so that the
portfolio screen does not start: a drawdown of 312 % is not obviously absurd to a reader who has
just had a bad month, which is exactly why a wrong number here is worse than a missing one.

Show `max_drawdown_abs` and compute the ratio here from the equity curve, or show nothing.

**Done, and generalised.** No statistics endpoint on either platform is read. `PortfolioMath` computes
every figure from the trades themselves, for a reason beyond this one bug: the two servers do not
agree with each other. Reading each one's own `/stats` would mean "win rate" meaning two different
things depending on which account is signed in.

The drawdown percentage follows the rule above exactly. It is offered only when the equity curve is
a real account balance — which is CoinePro-FX, where every trade carries `balance_after` — and is
null on TradeYar, whose curve is cumulative profit from zero and whose denominator would therefore
be the same wrong one.

### Neither backend has an equity curve, for the same reason

Both teams checked and both said so plainly: CoinePro-FX's `equity_curve` is cumulative profit, not
balance; TradeYar's `user_balance_snapshot` is an upsert with one row per user and no history at
all. So the app draws the curve it can honestly draw, and labels it — «موجودی حساب» where the
figures are real balances, «سود انباشته» with a note where they are not.

---

## TradeYar

| Ask | Answer | App side |
| --- | --- | --- |
| More than eight crypto symbols | **441.** Scoped as `LBank live ∩ Binance USDT-M perps − forex/metal`. | **Done** — the app already asks for the whole universe, so this arrives without a change here. |
| Candles | `GET /market/candles`, eight timeframes, `t` in seconds, ascending, `before` paging back to March 2024, `limit_max: 1000`. The forming candle is assembled from finer bars. | **Done.** `TradeYarCandleGateway`. |
| Portfolio history | `GET /portfolio/history`, tested live: 106 orders → 52 trades over 7 days. | **Done.** `TradeYarPortfolioGateway`, with the 31-day cap and the `truncated` flag both read back and shown. |
| Delete `spread`? | **No.** The premise was wrong: LBank publishes 25 levels of book, unauthenticated. The nulls are the relay's ticker topic, not the exchange. | **Keep `spread`.** It is not a dead field, it is an unwired one. |

### The app must send `?symbols=`

Their scope went from 8 markets to 441, and a bare `ws/prices` subscribes to all of them. They
declined to cap it server-side, and they were right to: a silent truncation would leave this app
believing it had the whole feed. So the cap belongs here.

**Done.** `webSocketUrl` takes the symbol list, and `MarketDataController.subscribe()` narrows the
live feed to what a screen is showing. What is *not* done is any screen actually calling it — the
markets list should, as soon as it knows its visible rows.

### `/auth/methods` is now ~34 KB

Pre-login, uncached, 2.8 KB gzipped. Worth watching on a slow connection, since it sits on the
critical path of the first screen anybody sees.

---

## Two bugs their teams found on the way

Recorded because both were the same shape — a failure that produced plausible, empty output — and
because that shape is the one this app's own tests are written against.

* **CoinePro-FX**: `verify_password` returned 500 on a corrupt hash, and leaked whether a username
  existed while doing it.
* **TradeYar**: `mobile/ai.py` read candle open times from a DataFrame index that `candle_cache`
  resets to a `RangeIndex`, so every row raised inside a bare `except: continue`. **The AI evidence
  has been shipping an empty candle list for every request ever made.** Fixed.

The second one is worth this app's attention beyond the fix: it means any AI-analysis output the app
has displayed to date was produced without candle evidence. Nothing to change here, but the feature
has never been seen working as designed.
