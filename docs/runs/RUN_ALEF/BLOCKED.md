# RUN א — blocked

One thing, and it is two static files on a machine that already exists.

---

## §א19 — the update document has to be served

**What cannot be done here.** `pro-chart.com` does not answer yet. Until it does,
`GET /api/app/latest` fails, the gateway answers null, the card never appears, and the app behaves
exactly as it did before this run — which is the honest fallback and not a fault, but it is also the
whole point of the run left undelivered.

**What is in the build meanwhile.** Everything on the app's side: the comparison, the refusals, the
card, the browser hand-off, the strings in both languages, the tests. The moment the document
answers, every install already carrying 5.0.0 starts checking. Nothing further has to be shipped to
the phone for the channel to open — **and that matters more than usual here**, because a build with
no update channel cannot ship the update channel to itself.

**What the owner (or the server's agent) must do.** `SERVER_BUILD_PROMPT.md` **Phase 1½**, which is
written to be handed over as it stands. In short:

1. Serve `GET /api/app/latest` — static JSON, `application/json`, cached a few minutes at most.
2. Decide whether the APK is served from `pro-chart.com/download/…` or the document points at the
   GitHub release. Either works; the first means the file and the document come from one host the
   reader is already trusting. This is a question for the owner and the brief says to ask it.
3. Write the document when a release goes out. Four fields the app enforces, and **a release that
   breaks any of them is silently not offered to anybody**:
   * `version_code` — the integer from `python3 scripts/release/version.py --code`;
   * `url` — HTTPS, host `pro-chart.com`, `www.pro-chart.com` or `github.com`;
   * `sha256` — 64 hex characters, the actual digest of the file at `url`, because the app shows it
     to the reader before they fetch it;
   * `mandatory` — a word on a card. Never a switch that stops the app working, and no future
     request should turn it into one.

**The acceptance check**, from the brief:

```bash
curl -s  https://pro-chart.com/api/app/latest | python3 -m json.tool
curl -sI https://pro-chart.com/api/app/latest | grep -i content-type
```

and the served `version_code` against `python3 scripts/release/version.py --code` for the published
tag.

---

## Still open from earlier runs

Nothing here changes any of them; they are listed so this file is the one place to look.

* **`RUN_PSI/BLOCKED.md §Ψ10`** — the three product questions that gate the server's later phases:
  which backend owns the account on the web, whether the terminal is open or member-only, and
  whether the candle archive is built on day one. The machine now exists, so these are next.
* **`RUN_XI/BLOCKED.md §Ξ21`** — whether the signals route can distinguish «entitled but the
  account is not linked». One field on a 403 body.
* **`RUN_T2`'s backlog** — B6 offline-as-a-state, B7 the single-symbol widget, B8 Picture-in-Picture,
  C1's surface, C2's two gaps, C3, C4, C6. None needs a backend.
* **The `assetlinks.json` fingerprint itself.** No longer a question of *where to find it* — §א13
  settled that — but the owner still has to hand the value to whoever configures the host. The app's
  «ایمنی و انتشار» screen prints it with a copy button.
