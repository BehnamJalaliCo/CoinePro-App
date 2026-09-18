# RUN Ξ — blocked

Each item carries the workaround that is **in the build** and the exact thing the owner must supply.

---

## §Ξ1 — the debug overlay, and why it could not be the instrument

**What cannot be done here.** Item 1 asks for a debug-only overlay on an internal flavour, and a
screen recording of one drag through it. This container has no device and no GPU emulator, so no
recording can be taken. More to the point, **the owner installs signed release APKs from CI** —
there is no internal flavour and no debug build in their hands, so an overlay behind `BuildConfig.DEBUG`
is one they could never open, and the recording item 1 asks for could not be produced by anybody.

**What is in the build instead.** `ChartDragTraceTest` — a stepwise drag through the real chart,
which failed on `main` at 4.96.0 with `[0,0,0,0,0,0,0,0,0,0]`, drove the bisection that named both
culprits, and now reads `[0,0,0,0,1,2,3,4,5,6]`. For finding and fixing the defect this is stronger
than an overlay: it is deterministic, it runs on every push, and it cannot go quiet again.

**What the owner must decide, if the overlay is still wanted.** One of:

* **a debuggable APK** — CI grows a second artifact from the `debug` build type, which is unsigned
  and installable side by side; the overlay then lives in `chart/ui/src/debug/`; or
* **a switch in the release build** — the overlay ships behind the existing diagnostics surface
  (`AdminController`), off by default and reachable the way the admin panel is.

The second is what would actually let the owner film it. It is not built yet because it puts a
development instrument into the store binary, and that is the owner's call rather than a build's.

**Closed for this run's purposes.** The owner's recording of 4.97.0 confirmed the drag on device, so
nothing is waiting on the overlay. It stays here as a decision the owner may want to take the next
time the input path is in question rather than as work this run left undone.

---

## §Ξ8 — the re-measurement

**Answered on device.** The owner recorded 4.97.0 and reported «حرکت چارت درست شد» — the chart
follows the finger. That is the gate the brief put in front of phases Ξ3–Ξ5, and it is the reason
they are built in this release.

What remains unmeasured is the **number**: item 8 asks for ≥ 100 non-zero frames over a second of
slow dragging and ≥ 60 frames of decay on a flick, and a frame count needs an instrument on a real
digitiser rather than an eye on a recording. The row is ✅ on the fact and says so on the figure.
Should the owner want the figures, the overlay in §Ξ1 is what would produce them.

---

## §Ξ20 — the venue's own list, and the endpoint to ask about it

Item 20 says: if the live list is short, that is a backend gap — log the count, ship the client that
handles hundreds, seed the fallback, and **name the endpoint here**.

**The endpoints**, one per platform, both asked the same way — the `symbols` query parameter omitted
entirely, which is what means «everything you have»:

| Platform | Path | What a bare call returns |
|---|---|---|
| CoinePro-FX | `ws/snapshot` | the full configured set |
| TradeYar | `api/mobile/v1/ws/snapshot` | everything LBank is quoting |

**The count is now recorded.** `MarketCatalog.served` carries what the venue returned before this
app drops a single name, so `markets.size` against `served` says which of the two failure modes a
short list is. `SymbolUniverseBreadthTest` proves the client side: twelve hundred synthetic pairs
in, twelve hundred listed, none of them carrying artwork in this repository.

**What the owner may want to raise with the two backends.** The bundled forex list
(`MarketDataSymbols.forex`) is **two symbols**. That is a seed for a cold start, not the universe,
and the app discovers the rest from the snapshot — but if the FX snapshot's configured set is also
small, no amount of client work will fill the screen. The question for that team is one line: how
many symbols does a bare `ws/snapshot` return today, and what is the ceiling.

---

## §Ξ21 — three of the four redemption states describe a door this app does not have

Item 21 asks for the failure states of «redeeming a subscription code that unlocks signals». There
is no redemption flow in this app and no code to redeem: membership is free and says so in two
places — `membership_free` and `membership_access_free`, both of which state «no monthly
subscription, no in-app purchase, no activation fee». The one requirement is a funded account at a
partner venue, and that is a journey rather than a code.

So the item is taken as what it is actually about — **the signals surface must never be an empty
list with no explanation** — and three of its four states map onto states this app really has:

| The brief's state | What it is here | Built |
|---|---|---|
| no connection | any `IOException` from the transport | ✅ `MessageKey.NO_CONNECTION`, its own sentence, app-wide |
| code invalid | the server refuses for want of a membership | ✅ `membershipRequired`, with the server's own words |
| accepted, no signals yet | entitled, and the desk has published none | ✅ the empty state, with a per-tab hint saying what fills it |
| accepted, account not linked | — | ❌ **not built** |

The fourth has nowhere to come from: `SignalsState` carries no account-linkage field, and the
signals gateway answers 403-with-a-message or a list — it never says «you are entitled but
unlinked». Inventing that state in the client would mean guessing, and a sentence the app guessed is
worse than the server's own. **What the owner must supply**: either a distinguishable refusal from
the signals route (a code or a field on the 403 body saying the account is not linked), or the word
that the case cannot happen, at which point the row closes.
