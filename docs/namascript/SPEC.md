# NamaScript — the language specification

NamaScript (نمااسکریپت) is Pro Chart's indicator language: the text a reader types into the
script studio to draw lines, mark bars, name conditions and propose a trade on the chart in front
of them. This document is the contract for that text. It describes **v1.1**, the language as it
runs in 4.50.0, exactly; §10 describes **v2**, the Pine-v5-class language the plan asks for, and
what of it exists today.

Two facts shape everything below:

1. **A NamaScript expression is a whole series.** `close` is every close on the chart, `ta.ema(close, 20)`
   is every value of that average, `close > open` is true or false on every bar. There is no
   bar-by-bar loop in the language; the interpreter vectorises, the way a spreadsheet column does.
   This is what makes a script a few lines long and impossible to make slow by accident, and it is
   also why there is no `for`, no `var`, no `:=` inside a bar — see §10 for the consequences.
2. **`ta.*` is the chart's own arithmetic.** Every `ta.` function delegates to `:chart-core`'s
   indicator library — the same functions the chart draws when a reader adds an indicator from the
   catalogue. A script's RSI is the chart's RSI to the last bit; `IndicatorReferenceTest` holds
   that library to an outside reference at 1e-6, and `ConformanceSuiteTest` holds the language's
   bindings to the library.

---

## 1. Lexical structure

| Element | Form | Notes |
| --- | --- | --- |
| Comment | `// …` to end of line | `//@version=1` is a comment; the header is accepted and ignored |
| Line continuation | a trailing `\` | joins the next line, for a long condition |
| Number | `12`, `1.5`, `.5` | Latin digits, one decimal point, no exponent, no sign (unary minus is an operator) |
| String | `"…"` or `'…'` | either quote, closed with the same one; no escapes |
| Identifier | letter or `_`, then letters, digits, `_` | case-sensitive; `close` and `Close` differ |
| Qualified name | `ident.ident` | a namespace member: `ta.sma`, `color.gold`, `math.abs` |
| Operators | `+ - * / %`, `== != < > <= >=`, `and or not`, `? :`, `[ ]`, `=`, `:=` | words for the logical ones |
| Statement end | newline | one statement per line; blank lines and comments are skipped |

Whitespace is spaces, tabs and carriage returns. The source is at most 20 000 characters (`E403`).

## 2. Grammar

```
program     := (statement NEWLINE)*
statement   := IDENT "=" expression        -- define
             | IDENT ":=" expression       -- redefine (must exist)
             | expression                  -- a call for its effect: plot, marker, …
expression  := conditional
conditional := or ( "?" expression ":" expression )?
or          := and ( "or" and )*
and         := equality ( "and" equality )*
equality    := comparison ( ("==" | "!=") comparison )*
comparison  := term ( ("<" | ">" | "<=" | ">=") term )*
term        := factor ( ("+" | "-") factor )*
factor      := unary ( ("*" | "/" | "%") unary )*
unary       := ("-" | "not") unary | postfix
postfix     := primary ( "[" expression "]" )*
primary     := NUMBER | STRING | "true" | "false" | IDENT | qualified
             | qualified "(" arguments? ")" | "(" expression ")"
qualified   := IDENT ( "." IDENT )?
arguments   := argument ( "," argument )*
argument    := ( IDENT "=" )? expression
```

Precedence, lowest to highest: `? :`, `or`, `and`, `== !=`, `< > <= >=`, `+ -`, `* / %`, unary
`- not`, postfix `[]`. All binary operators are left-associative; the conditional is
right-associative in its branches. Named arguments may follow positional ones in any order.

## 3. Types

| Type | Written | Meaning |
| --- | --- | --- |
| number | `1.5` | a constant, the same on every bar |
| text | `"name"` | a title, a style name |
| condition | `true`, `close > open` | a constant truth, or a truth per bar |
| colour | `color.gold`, `color.new(color.gold, 50)` | ARGB |
| number series | `close`, `ta.sma(close, 20)` | one number per bar, possibly absent |
| condition series | `close > open`, `ta.crossover(a, b)` | one truth per bar, possibly absent |

There are no arrays, matrices, maps, user types or functions in v1.1 (§10). A value's type is
decided when it is computed; a mismatch is a diagnostic (`E2xx`), never a coercion — except the
four the reader expects: a number used where a series is wanted broadcasts to every bar, a
condition used as a number is 1 or 0, a number used as a condition is "not zero", and (since
4.61.0) `+` with text on either side **joins**: `"close " + close` is text, the number in the
price style and a series by its last bar. A colour cannot be joined (E203).

### 3.1 Absence (`na`)

A series value can be **absent**: an average before its window is full, an offset before the
chart begins, a division by zero, anything not finite. Absence propagates: an arithmetic or
comparison with an absent operand is absent on that bar; `and` / `or` with an absent side are
absent; a conditional whose condition is absent is absent. `nz(x, 0)` replaces absence with a
number. `plot` draws nothing on an absent bar; `marker` and `bgcolor` treat an absent condition
as false. Since 4.61.0 the name `na` is a series absent on every bar — `nz(na, 0)` is 0, `close >
na` is never decided — and `na(x)` is the condition series that is true where `x` is absent.

### 3.2 History (`[]`)

`x[n]` is `x` as it was `n` bars ago: absent on the first `n` bars, never clamped to the first
bar (a clamp is how a crossover fires on bar zero of every chart). `n` must be a constant,
non-negative number (`E202`). A constant indexed is itself.

## 4. Built-in series and names

`open high low close volume hl2 hlc3 ohlc4 time bar_index n confirmed na`. `volume` is absent on
every bar of a feed that sends none; `time` is Unix seconds; `bar_index` counts from zero; `n`
is the bar count (a number); `confirmed` is true on every closed bar and false on the last — a
signal written `… and confirmed` never sits on the bar still forming. These names cannot be
redefined (`E302`). Colours: `color.gold silver buy sell green red blue grey white orange purple teal`.
Named numbers: `strategy.long` (1) and `strategy.short` (−1), for `strategy.entry`.

## 5. Functions

Arguments are positional or named (`title = "x"`). A length is a constant integer ≥ 1 and at most
four times the chart's length (`E206`). "Source" is any number series; most `ta.` functions take a
source first and a length second; the few that read the bar's own high/low/close/volume take
lengths only.

### 5.1 `ta.*` — indicators (the chart's own)

Averages: `sma ema wma hma smma zlema kama mcginley linreg dema tema t3 vwma alligator_jaw
alligator_teeth alligator_lips`. Oscillators: `rsi cci williams_r stoch_k stoch_d stochrsi_k
stochrsi_d macd macd_signal macd_hist ppo ppo_signal pvo pvo_signal tsi tsi_signal trix
trix_signal ultimate ao ac cmo coppock crsi smi smi_signal fisher fisher_signal rvi rvi_signal
momentum roc change kst kst_signal dpo bop chop`. Trend: `adx di_plus di_minus supertrend
supertrend_trend aroon_up aroon_down vortex_plus vortex_minus psar vstop mass ichimoku_conversion
ichimoku_base ichimoku_span_a ichimoku_span_b`. Bands: `bb_upper bb_lower bb_basis bb_percent
bb_width keltner_upper keltner_lower keltner_basis donchian_upper donchian_lower env_upper
env_lower env_basis`. Volatility: `atr tr stdev variance hv chaikin_vol`. Volume: `obv ad pvt
force chaikin_osc eom klinger klinger_signal mfi cmf netvolume vwap`. Windows and logic:
`highest lowest sum avg cum rising falling barssince valuewhen pivothigh pivotlow crossover
crossunder correlation`. Signatures and defaults: `docs/namascript/reference/en.md` (generated
from the same table the in-app reference shows; `ReferenceDocsTest` keeps them equal).

### 5.2 `math.*`

`abs floor ceil round sqrt log log10 exp sign sin cos tan max min pow avg clamp`. Each works on a
number or, bar by bar, on a series. `clamp(x, lo, hi)` takes constant bounds.

### 5.3 Inputs

`input(default, title = "…", min = …, max = …, step = …)` declares a number the reader can
change from the panel under the chart; a value set there wins over the default, clamped to the
range, and `step` sets the slider's step. `input.int` rounds; `input.float` is `input`;
`input.bool(default, title = "…")` is a switch.

Since 4.56.0 the set is complete: `input.string(default, title, options = "a,b,c")` is a choice
drawn as chips and returns text; `input.source("close", title)` is a choice among the price series
(`close open high low hl2 hlc3 ohlc4 volume`) and returns that series; `input.color(color.gold,
title)` is a swatch row and returns a colour; `input.timeframe("240", title)` is a choice of
timeframes for `request.security`. Every kind is stored as one number — a switch as 0/1, a choice
as its index, a colour as its ARGB — so the studio keeps a single map of overrides
(`ScriptInput.kind`, `options`, `step`).

### 5.3.1 Other timeframes

`request.security(timeframe, expression)` evaluates the expression over the chart's bars bucketed
to a coarser timeframe — `"240"`, `"H4"`, `"4H"`, `"D"`, `"1W"` — and maps the result back to the
chart's bars **confirmed**: a bar takes the value of the last *completed* higher bar, and only the
bar that closes a higher bar sees that bar's own value, so history is drawn exactly as it would
have been drawn live. The timeframe must be text, recognised, and a whole multiple of the chart's
(E210 otherwise); the chart's own timeframe returns the expression unchanged. The expression is
evaluated in the other context with the same built-ins and functions; a script variable is not
visible inside it. A script that uses `request.security` is re-run whole on every bar (§6).

### 5.4 Output

| Call | Draws |
| --- | --- |
| `plot(series, title=, color=, width=, dashed=, pane="price"\|"own")` | a line, on the price or in the script's own pane (at most 12) |
| `hline(price, title=, color=, pane=)` | a horizontal level |
| `marker(cond, title=, style="circle"\|"up"\|"down", color=)` | a glyph on every bar the condition holds |
| `plotshape(cond, …)`, `plotchar(cond, …)` | `marker`, accepting Pine's style names (`triangleup`, `arrowdown`, …) |
| `bgcolor(cond, color)` | a colour behind every bar the condition holds (`ScriptResult.backgrounds`; the chart draws it from 4.51.0) |
| `signal(buy, entry, stop, target)` | the trade idea from the last bar the condition held, with R:R |
| `alertcondition(cond, "title")` | a named condition the alert centre can watch (`ScriptResult.alerts`) |
| `log("text")` | a line in the studio's log (at most 40) |

`iff(cond, a, b)` and `cond ? a : b` are the same thing; `nz(x, v)` is §3.1.

### 5.5 `str.*` (4.61.0)

Text is constant — a title, a label — so these are functions of constants: `str.tostring(x)` (a
number in the price style; a series by its last bar), `str.length`, `str.upper`, `str.lower`,
`str.contains`, `str.startswith`, `str.endswith`, `str.replace_all(s, from, to)` and
`str.format("{0} / {1}", a, b)`, which fills `{0}`, `{1}`… with the arguments after the pattern.
`str.length` counts the characters a reader sees, not the direction marks a number carries.

### 5.6 Objects on the chart (4.61.0)

| Call | Draws |
| --- | --- |
| `label.new(x, y, "text", color=, textcolor=)` | a text mark at bar `x`, price `y` |
| `line.new(x1, y1, x2, y2, color=, width=)` | a line between two (bar, price) points |
| `box.new(left, top, right, bottom, color=, text=)` | a filled box between two bars and two prices |

`x` is a bar number as `bar_index` counts it; a series given for `x` or `y` is read **at its last
present bar**, so `label.new(bar_index, high, "…")` marks the newest bar, the way it does in Pine.
A number outside the chart is clamped to its edges. The objects reach the chart as the reader's
own drawing types (`text`, `trend`, `rect` — `ScriptOverlay.drawings`), so they draw, select and
style like a hand-placed mark. At most 40 objects per run; the rest are dropped. A script that
places an object is re-run whole on every bar (§6).

### 5.7 `strategy.*` (4.61.0)

| Call | Does |
| --- | --- |
| `strategy.entry("id", strategy.long \| strategy.short, when = cond)` | an entry order on every bar the condition holds |
| `strategy.close("id", when = cond)` | closes the open trade with that id |
| `strategy.close_all(cond)` | closes whatever is open |
| `strategy.entry("long" \| "short", cond)`, `strategy.close(cond)` | Pine's short form (4.64.0): the id names the direction, the condition is second; a bare condition to `close` closes whatever is open |

The orders are replayed over the series after the script has run: one position at a time (no
pyramiding), a market fill at the **open of the bar after** the signal, an entry in the opposite
direction closing the open trade on the same open, and whatever is still open on the last bar
reported open at the last close and left out of the figures. The report (`ScriptResult.strategy`)
carries the trades, the net return (the sum of each closed trade's return on its entry, in percent,
not compounded), the win rate, the profit factor and the deepest drawdown of the cumulative return.
Each trade is two marks on the chart: the entry pointing its way, the exit with its return. There
is no commission, slippage, position sizing or stop order in this model; `docs/qa` says which of
those the strategy tester's presets have.

### 5.8 `var` and `varip` (4.61.0)

`var x = expr` (and `varip`, which is the same here) declares `x` as the value `expr` has on the
**first bar where it is present**, held on every bar. That is what Pine's «initialise once» means
in a model where every value is a whole series: a name that never changes is a constant line.
`x := expr` afterwards replaces the whole series, as it always did; per-bar mutation (`x := x + 1`
meaning «one more than the last bar») is still v2 (§10).

## 6. Execution model

A program runs top to bottom once per evaluation, over the whole series the chart holds. Each
statement produces a whole series; `plot` and its kin record output; the result is a
`ScriptResult` — plots, levels, markers, backgrounds, alerts, inputs, the setup, the log — that the
chart draws as an overlay (`toOverlay`). There is no state between evaluations: when a bar
arrives the program runs again over the longer series, which is the same as evaluating every bar
with full history, without lookahead — a script can never read a bar after the one it is on.
`confirmed` is how a script keeps a signal off the forming bar.

Since 4.56.0 the program is **compiled once and run many times**: `NamaScript.compile` lexes,
parses and type-checks (`TypeChecker`) the source into a `CompiledScript`, refusing before any
run what a run would refuse — with the same code — so the editor's squiggle arrives on the
keystroke; the studio keeps the compiled script for as long as the source is unchanged. The
`IncrementalRunner` then evaluates only the **tail**: handed the previous series with bars
appended (or its last bar rewritten by a tick), it runs the script over the last *window* bars —
twelve times the largest constant length or offset the checker found, at least 120 — and splices
the bars the previous result could not know onto it. Nothing a script computes at bar *i* reads a
bar after *i*, so every earlier value is final. A script that calls a cumulative function
(`ta.cum`, `ta.obv`, `ta.ad`, `ta.pvt`, `ta.vwap`, `ta.barssince`, `ta.valuewhen`, `ta.psar`,
`ta.supertrend`, `ta.vstop`, `ta.mcginley`, `ta.kama`), `request.security`, an object
(`label.new`, `line.new`, `box.new`) or an order (`strategy.*`) is re-run whole, because those are
placed by absolute bar or replayed over every bar and cannot be spliced from a tail. Per-bar
mutation (`:=` reading the previous bar's own value) remains v2 (§10); `var` is §5.8.

## 7. Diagnostics

Every refusal carries a position (line, column), a message in Persian and English, a stable
**code** and, for most codes, a one-line fix in both languages (`ScriptDiagnostics`). The parser
stops at the first error; there is no recovery in v1.1.

| Code | Raised when | Example |
| --- | --- | --- |
| E101 | text follows a complete statement | `plot(close) plot(open)` |
| E102 | `(` or `[` never closed | `plot(close` |
| E103 | `?` without `:` | `a ? b` |
| E104 | an expression stops short | `close +` |
| E105 | a malformed number | `1.2.3` |
| E106 | a string never closed | `"abc` |
| E107 | a character the language has no use for | `close $ 2` |
| E108 | an operator not supported in that position | — |
| E201 | negation or `not` on text or a colour | `-"x"` |
| E202 | `[]` with a series, a negative or a non-number | `close[-1]` |
| E203 | a number wanted, something else given | `plot("x")` |
| E204 | a condition wanted, something else given | `marker("x")` |
| E205 | a constant wanted, a series given | `ta.sma(close, close)` |
| E206 | a length out of range | `ta.sma(close, 0)` |
| E207 | `plot` given a condition | `plot(close > open)` |
| E208 | text wanted | `plot(close, title = 5)` |
| E209 | a colour wanted | `plot(close, color = 5)` |
| E210 | `request.security` given a timeframe that is not text, not recognised, finer than the chart's or not a multiple of it | `request.security("15", close)` on H1 |
| E301 | a name not defined | `plot(closs)` |
| E302 | a built-in name redefined | `close = 5` |
| E303 | `:=` on a name never defined | `x := 5` |
| E304 | a function that does not exist | `ta.magic(close)` |
| E401 | more than 250 000 nodes evaluated | a pathological expression |
| E402 | more than 12 plots | — |
| E403 | more than 20 000 characters | — |
| E404 | the chart has no bars | — |
| E405 | nesting exhausts the stack | `((((…))))` |
| E406 | more than 2 s of wall clock | — |
| E407 | the variables and plots hold more than 8 000 000 bar-cells at once | 450 series over 20 000 bars |

## 8. The sandbox

A script can touch only the series it is given. There is no I/O, no network, no file, no clock
beyond the sandbox's own; the language has no construct that reaches outside the interpreter.
Limits: 20 000 characters, 250 000 evaluated nodes, 2 000 ms wall clock (read every 1024 nodes),
8 000 000 retained bar-cells (a series is one cell per bar; every variable and plot counts, so the
memory a run can hold is bounded at about 72 MB — E407, since 4.61.0), 12 plots / levels /
markers / backgrounds / alerts, 40 objects and orders, 40 log lines, a stack-overflow guard. All
are diagnostics with codes, never crashes.

## 9. Performance

Whole-series evaluation makes the cost of a script the cost of its indicators, which are the
chart's own and are measured with it. `ScriptPerformanceTest` runs a 300-line script with ten
`ta.` calls over 20 000 bars on the JVM — compile (lex, parse, type-check), a whole evaluation,
and the realtime case through the `IncrementalRunner` (one bar appended, one bar ticked) — and
records the figures in `docs/engineering/REPORT.md`. Since 4.61.0 the arithmetic runs on the
lines' raw arrays rather than through a boxed `Double?` per bar, which took the whole evaluation
from about 1.4 s to under 0.2 s on the JVM. The Pixel 6a targets in the plan (compile < 50 ms,
evaluate < 40 ms, realtime < 2 ms) need a device and are open.

## 10. v2 — the Pine-v5-class language, and where v1.1 stands

The plan asks for a language with bar-by-bar execution and history, `var`/`varip`, functions
with defaults, `if`/`switch`/`for`/`while`, arrays, matrices, maps, user types, libraries,
`request.security`, `strategy.*`, and drawing objects (`label.*`, `line.*`, `box.*`, `table.*`).
That is a different execution model — per-bar with state — and a compiler (typed AST, bytecode
VM) rather than a vectorising interpreter. It is not in 4.50.0. What is:

| v2 item | v1.1 |
| --- | --- |
| `ta.*` "all TV indicators" | 130 functions bound to the engine's 84 indicators; parity-tested |
| `math.*`, `color.*`, `input.*` | the sets in §5 |
| plot family | `plot hline marker plotshape plotchar bgcolor`; `fill`, `plotcandle`, `barcolor` open |
| `alertcondition`, `alert()` | `alertcondition` produces `ScriptResult.alerts`; wiring to the alert centre open |
| `strategy.*` | since 4.61.0: `strategy.entry` / `strategy.close` / `strategy.close_all` with `strategy.long` / `strategy.short`, replayed with next-open fills into a report (§5.7); `signal(...)` still gives one setup with R:R. No `strategy.exit`, sizing, commission or pyramiding |
| `[]`, `na` semantics | as §3; the `na` name and `na(x)` since 4.61.0 |
| `? :`, `iff` | yes; `if`/`switch` expressions open |
| `var`, `varip`, `:=` | since 4.61.0 `var`/`varip` hold the first present value (§5.8); `:=` reassigns a series; per-bar mutation stays open |
| `str.*` | since 4.61.0: the nine in §5.5; text joins with `+` |
| drawing objects | since 4.61.0: `label.new`, `line.new`, `box.new` (§5.6) as the reader's own drawing types; `table.*` open |
| `request.security` | since 4.56.0 (§5.3.1) |
| functions, loops, collections, UDTs, libraries | open |
| diagnostics with codes and hints, both languages | yes (§7) |
| sandbox: CPU, time, size, output | yes (§8); memory cap open |
| repainting / lookahead warnings | no lookahead is possible in v1.1 (§6); warnings open |
| Pine paste helper | `PineTranslator`: best-effort translation with the list of what it could not map |

The honest route to v2 is a second front end on the same `ta.*` bindings: a per-bar evaluator
with a variable environment per bar, which the vectorised interpreter can host as a fallback for
scripts that use only v1.1 constructs. `namascript/` is where it goes; nothing in the module
depends on a platform, so it can be built and tested on the JVM before it ever touches a phone.
