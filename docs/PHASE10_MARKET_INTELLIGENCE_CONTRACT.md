# Phase 10 — Market Intelligence Contract

Repository scope: `BehnamJalaliCo/CoinePro-App` only.

## Normalized authenticated endpoint

Android consumes one CoinePro-owned endpoint:

`GET /user/market-intelligence`

Expected response shape:

```json
{
  "server_time": "2026-08-23T10:00:00Z",
  "news": [
    {
      "id": "news-id",
      "title": "...",
      "summary": "...",
      "source": "...",
      "url": "https://...",
      "published_at": "2026-08-23T09:55:00Z",
      "sentiment": "bullish | bearish | neutral | unknown",
      "impact": "low | medium | high | unknown",
      "relevance": ["gold", "silver", "crypto"],
      "stale": false
    }
  ],
  "calendar": [
    {
      "id": "event-id",
      "title": "...",
      "country": "US",
      "currency": "USD",
      "scheduled_at": "2026-08-23T12:30:00Z",
      "impact": "low | medium | high | unknown",
      "actual": "3.1%",
      "forecast": "3.0%",
      "previous": "2.9%",
      "relevance": ["gold", "silver", "crypto"],
      "stale": false
    }
  ]
}
```

## Truth rules

- Publication and event timestamps must be ISO-8601 instants. Android normalizes them to `java.time.Instant` and renders them in the device timezone.
- Invalid required timestamps cause the affected item/event to be dropped rather than assigned a guessed time.
- Missing `stale` is treated as stale, never fresh.
- Unknown impact remains `UNKNOWN`; Android never upgrades unknown impact to Low, Medium or High.
- Unknown sentiment remains `UNKNOWN`; Android never infers sentiment from title/body text.
- News article URLs are accepted only when they use HTTPS and have a valid host.
- `actual`, `forecast` and `previous` are displayed only when supplied. Missing values render as `—`.
- Gold maps to XAUUSD, Silver to XAGUSD and Crypto to supported `*USDT` instruments.

## Active-signal high-impact warning

A warning is eligible only when all are true:

1. The server impact is exactly `HIGH`.
2. The event is not stale.
3. Structured relevance matches the signal instrument.
4. The event time is from one hour in the past through six hours in the future.
5. The signal itself is active.

The warning is risk context, not a price prediction or execution instruction.

## Motion rules

Phase 10 UI uses native Compose transitions for loaded/error/empty state changes, card size changes and lazy-list movement. Motion is driven by real state changes only and follows the platform animator-duration scale, so Android reduced-motion settings remain authoritative. No fake live pulse, count-up price, countdown urgency or simulated streaming state is allowed.

## Exit criteria

- News loading/empty/error/refresh states are explicit.
- Economic calendar loading/empty/error/refresh states are explicit.
- Low/Medium/High/Unknown impact is visually distinct without converting unknown into certainty.
- Actual/forecast/previous values are source-owned.
- Gold/Silver/Crypto relevance is structured.
- Active Signal Detail can render eligible high-impact risk context.
- Unit tests cover timestamp normalization, stale defaults, HTTPS URL filtering, unknown impact and warning eligibility.
- Android CI must include `:core:marketintel:testDebugUnitTest` plus existing phase gates, lint, app tests and debug assembly.
