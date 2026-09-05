# Feeds the app reads from third parties, and the routes that would take them off the device

**Status:** the app still fetches these from the phone, as a *fallback only*, and
`BuildConfig.DIRECT_THIRD_PARTY_FEEDS` (Gradle property `COINEPRO_DIRECT_THIRD_PARTY_FEEDS`,
default `true`) is the switch that turns that off. It is `true` in every variant today because the
routes below do not exist yet, and a `false` without them is an empty news screen for every reader.
Flipping it is a one-line change in `local.properties` or the CI environment; nothing else moves.

## What the app reads directly, and when

| Host | What | When it is asked |
|---|---|---|
| `https://www.investing.com/rss/news_301.rss`, `news_1.rss`, `news_11.rss` | Forex / markets headlines (RSS) | Only when the platform's own newsroom answered **empty** (`api/v1/news/list` on TradeYar, `academy/bn/news` on CoinePro-FX). |
| `https://www.cointelegraph.com/rss` | Crypto headlines (RSS) | Same rule. |
| `https://nfs.faireconomy.media/ff_calendar_thisweek.json` | This week's economic calendar (ForexFactory's public file) | Only after **both** our own hosts answered empty: the TradeYar relay `api/v1/public/calendar/week` and CoinePro-FX's `academy/bn/calendar`. |

Where the code lives: `core/marketintel` — `PublicMarketIntel.news()` / `.calendar()`,
`PublicNewsFeed`, `PublicCalendarFeed`. The order of sources is written beside each call and is
the same as the table: ours first, theirs last.

## Why it should move

Thousands of handsets reading a publisher's RSS directly is a pattern those publishers block by IP
and forbid in their terms, and when they do the feature fails silently on the phone. Read once by a
server, cached, and handed on under our own host, the same data reaches every reader from one
origin that can be rate-limited, cached and monitored.

## The contract

Two routes, on the platform host the app already talks to. Both are public (no session), both
answer JSON, both carry the server's own freshness so the app can label a stale answer.

### `GET /api/v1/public/news?platform=<crypto|forex>&limit=<n>`

```json
{
  "items": [
    {
      "id": "string, stable per story",
      "title": "string",
      "summary": "string | null",
      "source": "Investing.com | Cointelegraph | …",
      "url": "https://…",
      "image_url": "https://… | null",
      "published_at": "2026-09-05T08:30:00Z"
    }
  ],
  "fetched_at": "2026-09-05T08:41:12Z",
  "cache_ttl_ms": 300000,
  "stale": false
}
```

`limit` ≤ 50. Persian where the upstream is Persian; the app already drops English-only headlines
for a Persian reader (`GuestControllerTest`), so the route need not translate.

### `GET /api/v1/public/calendar/week`

Already served by TradeYar as a byte-for-byte relay of the ForexFactory file, re-read hourly. It
is asked first today. The one ask is that it never answers an empty array while the upstream file
has rows — an empty relay is what makes the app fall through to the file's own host.

```json
[
  {"title": "Non-Farm Employment Change", "country": "USD",
   "date": "2026-09-04T08:30:00-04:00", "impact": "High",
   "forecast": "75K", "previous": "73K", "actual": null}
]
```

## Turning the fallback off

1. Serve the two routes above and confirm them from a handset in Iran (the reason the relay
   exists: the file's own host does not answer from there).
2. Set `COINEPRO_DIRECT_THIRD_PARTY_FEEDS=false` for the release build (CI environment or
   `local.properties`).
3. Ship. `PublicMarketIntel` then answers an empty section as empty, and the screens already say
   «چیزی منتشر نشده» / "nothing published" rather than showing an error.
