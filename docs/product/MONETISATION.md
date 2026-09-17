# How this app is meant to make money

Written in run ΤΦΥ (F12). **No paywall work was done in that run and none is planned in the next
one.** This file exists so that the decision behind «everything free for everybody» is written down
rather than remembered, and so that the person who eventually reverses it knows exactly which switch
to flip and what it turns back on.

## The position

Until roughly a hundred thousand installs, every surface in this app is open to everybody. That is
not generosity and it is not a trial: it is the cheapest way to find out whether the product is
good, in a market where the alternative is a Persian-speaking reader installing TradingView and
never coming back. A wall in front of a reader who has not yet decided whether the chart is any good
costs a user and earns nothing.

`FeatureFlags.allUnlocked` is that decision, `Entitlements` is what reads it, and every `locked`
check in the app is still exactly where it was.

## The primary path: referral and copy trading

The two revenue lines that do not charge the reader anything:

* **Referral.** The reader opens an account at a venue through this app's link and the venue pays a
  share of what they trade. `TradePartners` holds the links and the codes. The whole mechanism is
  three constants — `ONEROYAL_REFERRAL`, `LBANK_REFERRAL`, `OURBIT_REFERRAL` — and it is the one
  place a code is entered.
* **Copy trading.** A reader links a broker account and the service trades it. This is the forex
  side's model and it is behind `FeatureFlags.forexTrading`, which is **off** in this build.

Both scale with a reader who is doing well and earn nothing from a reader who is not, which is the
alignment a subscription does not have.

## The fallback: subscriptions

If the referral share turns out not to cover the servers, the app has a subscription surface already
— the plan row on the profile, the membership journey, the academy's tiers — and all of it is
compiled and tested with the walls on. Turning it back on is:

1. `FeatureFlags.allUnlocked = false` in `core/common/.../FeatureFlags.kt`.
2. Remove the `VIP` entries from `FORBIDDEN_VARIANTS` in `tools/i18n/lint_strings.py`, if the plan
   is going to be called that again. (The string keys never changed; only the copy did.)
3. Nothing else. `EntitlementGateTest` drives both states, so the walls that come back are walls
   that have been exercised on every build in between.

What must **not** happen on that day: the founding-member badge disappearing. It marks the readers
who were here while it was free, and that is the one thing about this period worth keeping.

## What is deliberately not here

* **Advertising.** «بدون آگهی» is in the copy the app shows readers. It is a promise, not a status.
* **Selling what the reader does.** Neither backend receives a watchlist, a drawing or a journal
  entry, and this app is not going to start sending them somewhere in order to be paid for it.
* **A price.** There is no number in this file on purpose. A price written down a year early is a
  price somebody quotes back.
