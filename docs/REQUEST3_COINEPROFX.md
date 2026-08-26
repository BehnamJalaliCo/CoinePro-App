# CoinePro-FX — one ask: a Telegram sign-in an app can actually use

**From:** CoinePro-App (Android) · **Date:** 2026-08-26

One item. It is short because the problem is narrow and the fix is a route you have most of
already.

---

## What is broken, and it was ours

`/auth/methods` reports `telegram: true` and names the bot, so the app offered Telegram sign-in. It
never worked, and the reason is entirely on the client side — worth stating plainly so you are not
looking for a bug in your code.

The app embedded Telegram's Login Widget in a WebView, loading it with a faked base URL of
`https://telegram.org/`. The widget asks `oauth.telegram.org` to sign a payload **for the origin of
the page it is embedded in**, and Telegram checks that origin against the domain registered for the
bot with BotFather. Our origin was `telegram.org`, which nobody can register as their own bot's
domain, so Telegram refused every attempt and rendered its own error inside the frame. A reader saw
a sign-in method that looked available, opened, and then complained about the bot.

The widget has been removed from the app. It cannot be fixed in place: a widget needs a real page
on a registered domain and a mobile app has no page.

## What we would need instead

The shape every mobile app uses for Telegram, and it needs one thing from you that does not exist
yet — a route that turns a bot conversation into a session.

```
1.  App    →  POST /user/auth/telegram/start
              (no auth)                          →  { "nonce": "...", "deep_link": "https://t.me/<bot>?start=<nonce>", "expires_in": 300 }

2.  App    →  opens the deep link; Telegram opens; the reader presses Start.

3.  Bot    →  receives /start <nonce>, and because it is talking to that person it already knows
              their Telegram id — the same identity your widget payload carries. It binds the
              nonce to that user.

4.  App    →  POST /user/auth/telegram/poll { "nonce": "..." }
              →  202 while nothing has happened yet
              →  200 with the same session payload /user/auth/telegram returns today
              →  410 once the nonce has expired or been used
```

Everything after step 3 is your existing sign-in. `_verify_telegram_login` is not needed at all in
this flow — there is no widget payload to verify, because the bot *is* the proof: Telegram delivered
the message, so the sender is who Telegram says.

### Three things worth deciding on your side

* **Nonce lifetime and single use.** Five minutes and one use is what we assumed. A nonce that
  survives its redemption is a session anybody who saw the deep link can claim.
* **Rate limit the poll.** The app will ask about once a second while the screen is open. If you
  would rather it did not, say so and give us an interval — we would rather match your limiter than
  discover it.
* **What happens when that Telegram id already has an e-mail account.** Your `/auth/link/telegram`
  already answers this with `identity.link_telegram` and a `merged` flag. If the same rule applies
  here, we will read the flag and drop the old token; if not, tell us what to expect.

## Until then

The app says, in one line, that Telegram sign-in works in the web app only, and offers e-mail and
Google. It does not offer a button. Nothing else changed, and `/user/auth/telegram` is untouched —
`SessionController.completeTelegramLogin` is still there waiting for a payload the moment there is
a flow that can produce one.

## Not an ask, but you should know

Google sign-in fails on our signed builds too, and that one is ours to fix outside the code: the
release key's SHA-1 is not registered as an Android OAuth client in the Firebase project, so
Credential Manager will not mint a token however the reader is signed in. `google-services.json`
carries only the web client. Nothing needed from you.
