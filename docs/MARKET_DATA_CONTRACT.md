# Phase 3 — Realtime Market Data Contract

## Source ownership

- Gold `XAUUSD`: Finnhub WebSocket via CoinePro backend.
- Silver `XAGUSD`: Finnhub WebSocket via CoinePro backend.
- Crypto: LBank APIs only. Realtime source is the LBank Futures public WebSocket supplied in the private integration documentation.
- Android never connects to Finnhub or LBank directly.

This keeps vendor credentials, IP whitelisting, reconnect policy, schema changes and rate limits on the server.

## LBank integration migration

Use the legacy/whitelisted domains until LBank explicitly approves the new whitelist:

- Futures: `fmapi.lbankverify.com` (new-domain equivalent: `fapi.lbank.info`)
- Affiliate: `affiliate.lbankverify.com` (new-domain equivalent: `affiliatehub.lbank.info`)
- Spot/Broker: `mmapi.lbankverify.com` (new-domain equivalent: `sapi.lbank.info`)

API-key permissions were renamed by LBank:

- `Spot` = spot trading permission
- `Futures` = futures trading permission

The old generic `Trade` permission must not be used in future execution flows.

## LBank realtime contract used by backend

From `FuturesApi_EN_250422.md` supplied by LBank:

- WebSocket: `wss://fmapi.lbankverify.com/ws/v3`
- Product group: `SwapU`
- Latest Transaction Push topic: `x = 4`
- Subscribe: `z = 1`
- Snapshot/push success: `z = 3` / `z = 4`
- Heartbeat: literal `ping` / `pong`
- The server can disconnect a client after 5 seconds without a client request; the backend heartbeat is 3 seconds.

## CoinePro app-facing contract

Primary realtime transport: `WSS <API_BASE>/ws/prices`.

Subscription:

```json
{"action":"subscribe","symbols":["XAUUSD","XAGUSD","BTCUSDT","ETHUSDT"]}
```

HTTP fallback/snapshot:

```text
GET <API_BASE>/ws/snapshot?symbols=XAUUSD,XAGUSD,BTCUSDT,ETHUSDT
```

Normalized quote fields are `symbol`, `price`, optional `bid`/`ask`, `ts`, `source`, optional `venue`, and optional `market`.

## Freshness rules

The UI never treats the presence of a cached value as proof that the market is live.

- LBank quote: stale after 15 seconds without a new source timestamp.
- Finnhub quote: stale after 90 seconds without a new source timestamp.
- Unknown source: stale after 30 seconds.
- Invalid/missing timestamps are stale immediately.

When WebSocket is disconnected, Android keeps the latest visible values but marks the stream degraded/stale as appropriate and uses HTTP snapshot fallback while reconnecting with exponential backoff.

## External gate

LBank live connectivity is not considered production-verified until the requested IP whitelist is approved by LBank. Code-level parser, reconnect, heartbeat, normalization, HTTP fallback and Android behavior are independently testable before that approval.
