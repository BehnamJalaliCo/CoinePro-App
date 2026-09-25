# Server handoff — 5.17.0 and 5.18.0

What the server has to do so the features of 5.17.0 and 5.18.0 work on the phone, the tablet and
`pro-chart.com/terminal/`. The apps (APK and web bundle) are already built; everything below is
server work. Never print a key, a secret or a password while doing it — names only.

## 1. TradeYar (crypto) — real LBank trading, crypto price alerts

Code: branch `claude/coinepro-app-clone-8u63ui` of TradeYar.

1. Merge `origin/claude/coinepro-app-clone-8u63ui` into the deployed branch.
2. Apply migration **083_mobile_orders** (`alembic upgrade head`). It creates one table,
   `mobile_orders`, and touches nothing else.
3. Rebuild and restart the API, the Celery worker and Celery beat. Beat must list
   `mobile-price-alerts` (every 60 s).
4. `MOBILE_LIVE_TRADING` is on by default. Setting it to `0` turns every order write off at once
   without a deploy; reads stay up.
5. Prove it against LBank, from the server (LBank answers only the whitelisted IP), with the user id
   of an account whose LBank **futures** key is linked in the app:

   ```bash
   PYTHONPATH=. python scripts/lbank_live_trade_smoke.py --user-id <id>
   PYTHONPATH=. python scripts/lbank_live_trade_smoke.py --user-id <id> --round-trip
   ```

   The first run is read-only (server time, the contract's steps, balance, positions, the order
   rules against the live price). `--round-trip` places ONE limit buy at the minimum size 20 %
   under the market — it cannot fill — reads it back and cancels it. Both must end `ALL PASSED`.
6. Run the suite on a scratch database:
   `MOBILE_TEST_DATABASE_URL=<scratch> pytest tests/test_mobile_live_orders.py tests/test_mobile_alert_eval.py`.

Routes added: `/api/mobile/v1/trade/{account, positions, orders, orders/{id}, positions/protection,
positions/close, leverage}`.

## 2. CoinePro-FX (forex) — ticks, advanced alerts, Telegram and email

Code: branch `claude/coinepro-app-clone-8u63ui` of CoinePro-FX.

1. Merge it into the active branch (`claude/wizardly-edison-zyWyW`).
2. `docker compose build api worker beat && docker compose up -d`.
3. `docker compose exec api alembic upgrade head` (migration **080_mobile_alert_specs** on the server — renumbered there because `079_tp_ladder_records` already existed).
4. Restart the Finnhub WebSocket service so ticks start recording, then check that
   `/public/market/ticks?symbol=EURUSD` returns rows.

## 3. The web terminal

1. Download `web-latest/pro-chart-terminal.zip` and check its sha256 against the published one
   (`web/relay/README.md`, «Getting the bundle»).
2. Replace `/terminal/` with it. `BUILD.txt` inside names the commit; it must be the 5.18.0 one.
3. Open `/terminal/screener`; open a crypto chart signed in with a linked LBank futures key — the
   trade ring opens «Trade on LBank».

## What each platform gets once this is done

| Feature | Phone / tablet | Web |
|---|---|---|
| Growth screener, scan watch, CSV | already in the APK | after step 3 |
| Tick and seconds history | after step 2 | after steps 2 and 3 |
| Advanced server alerts, Telegram and email | after step 2 | after steps 2 and 3 |
| Crypto alerts evaluated on the server | after step 1 | after step 1 |
| Real LBank orders from the chart, ticket and depth ladder | after step 1 | after steps 1 and 3 |
