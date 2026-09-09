#!/usr/bin/env python3
"""Regenerates chart/core/src/jvmTest/resources/indicator-reference.txt.

The second golden set for the built-in indicators, independent of the first. `indicator-parity.txt`
is the output of the web terminal's JavaScript, and proves the Kotlin port *is* a port. This file
is the output of an outside reference — the `ta` library (bukosabino/ta, the pandas one) where its
definition is the textbook one, and a formula written out in pandas here where `ta` departs from
the definition TradingView and TA-Lib share — and proves the port is *right*.

Both are computed over the same 120-bar random walk (read from the parity file, so the two golden
sets can never drift apart on their input). `IndicatorReferenceTest` compares every series at
1e-6. Which reference each series uses, and why, is written beside it below and copied into the
fixture's header, so a mismatch can be judged rather than tuned away.

Usage:
    PYTHONPATH=<checkout of ta> python3 scripts/quality/gen_indicator_reference.py

`ta` is not vendored: `pip download ta` fetches the sdist and its `ta/` package directory is
importable as is (pure Python over pandas).
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

try:
    import ta
    from ta import momentum, trend, volatility, volume
except ImportError:  # pragma: no cover - the usage note above says how to get it
    sys.exit("the `ta` package is not importable; see the module docstring")

ROOT = Path(__file__).resolve().parents[2]
PARITY = ROOT / "chart/core/src/jvmTest/resources/indicator-parity.txt"
TARGET = ROOT / "chart/core/src/jvmTest/resources/indicator-reference.txt"


def load_bars() -> pd.DataFrame:
    rows = []
    mode = ""
    for raw in PARITY.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line == "BARS":
            mode = "bars"
            continue
        if line.startswith("SERIES"):
            mode = "series"
            continue
        if mode == "bars":
            t, o, h, l, c, v = line.split(",")
            rows.append((int(t), float(o), float(h), float(l), float(c), float(v)))
    return pd.DataFrame(rows, columns=["t", "open", "high", "low", "close", "volume"])


# --------------------------------------------------------------------------- reference formulas

def wilder(series: pd.Series, n: int, start: int = 0) -> pd.Series:
    """Wilder's smoothing seeded with the simple mean of the first n values from [start] — TA-Lib's
    and TradingView's `ta.rma`. `ta` seeds RSI from the first value instead (pandas `ewm`), which
    converges but is not the definition. [start] is 1 for anything built on a change between bars
    (directional movement), where bar 0 has no reading and must not be averaged in as a zero."""
    values = series.to_numpy(dtype=float)
    out = np.full(len(values), np.nan)
    first = start + n - 1
    if len(values) <= first:
        return pd.Series(out)
    out[first] = np.nanmean(values[start : first + 1])
    for i in range(first + 1, len(values)):
        out[i] = (out[i - 1] * (n - 1) + values[i]) / n
    return pd.Series(out)


def true_range(df: pd.DataFrame) -> pd.Series:
    prev = df.close.shift(1)
    tr = pd.concat([df.high - df.low, (df.high - prev).abs(), (df.low - prev).abs()], axis=1).max(axis=1)
    tr.iloc[0] = df.high.iloc[0] - df.low.iloc[0]
    return tr


def rsi_wilder(close: pd.Series, n: int) -> pd.Series:
    """RSI as Wilder wrote it and TA-Lib ships it: the first average is a simple mean of the first
    n gains/losses (the change at bar 0 does not exist, so the window is bars 1..n)."""
    change = close.diff()
    gain = change.clip(lower=0)
    loss = (-change).clip(lower=0)
    values_g = gain.to_numpy(dtype=float)
    values_l = loss.to_numpy(dtype=float)
    out = np.full(len(close), np.nan)
    ag = np.nanmean(values_g[1 : n + 1])
    al = np.nanmean(values_l[1 : n + 1])
    out[n] = 100 - 100 / (1 + ag / (al if al != 0 else 1e-9))
    for i in range(n + 1, len(close)):
        ag = (ag * (n - 1) + values_g[i]) / n
        al = (al * (n - 1) + values_l[i]) / n
        out[i] = 100 - 100 / (1 + ag / (al if al != 0 else 1e-9))
    return pd.Series(out)


def ema_first_seed(series: pd.Series, n: int) -> pd.Series:
    """EMA seeded with the first *available* value, which is what pandas `ewm(adjust=False)` and
    `ta` do. TA-Lib and TradingView's `ta.ema` seed with the SMA of the first n values instead; the
    two agree to 1e-6 after ~5n bars and differ before. The engine and the web terminal both use
    the first-value seed, so that is the reference here; the difference is documented, not hidden."""
    return series.ewm(span=n, min_periods=n, adjust=False, ignore_na=True).mean()


def ema_over_defined(series: pd.Series, n: int) -> pd.Series:
    """EMA of a series with a NaN head (a MACD line, a PPO line), started at its first defined value
    rather than run through zeros. `ta` does this through pandas; the engine used to run the
    recursion through the undefined head as zeros, which is a transient nobody chose."""
    return ema_first_seed(series, n)


def wilder_psar(high: pd.Series, low: pd.Series, step: float, cap: float) -> pd.Series:
    """Wilder's SAR as he wrote it: the opening trend is the direction of bar 1 against bar 0, the
    first SAR is the opposite extreme of bar 0, and on every bar the SAR may not enter the range
    of the *two* prior bars. `ta` clamps against one of the two, which lets the SAR ride a bar too
    close; TA-Lib and TradingView clamp against both, and so does the engine."""
    h = high.to_numpy(dtype=float)
    l = low.to_numpy(dtype=float)
    out = np.full(len(h), np.nan)
    rising = (h[1] - h[0]) >= (l[0] - l[1])
    ep = h[1] if rising else l[1]
    sar = l[0] if rising else h[0]
    af = step
    out[1] = sar
    for i in range(2, len(h)):
        value = sar + af * (ep - sar)
        if rising:
            value = min(value, l[i - 1], l[i - 2])
            if l[i] < value:
                rising = False
                value = max(ep, h[i], h[i - 1])
                ep = l[i]
                af = step
            elif h[i] > ep:
                ep = h[i]
                af = min(af + step, cap)
        else:
            value = max(value, h[i - 1], h[i - 2])
            if h[i] > value:
                rising = True
                value = min(ep, l[i], l[i - 1])
                ep = h[i]
                af = step
            elif l[i] < ep:
                ep = l[i]
                af = min(af + step, cap)
        sar = value
        out[i] = sar
    return pd.Series(out)


def main() -> None:
    df = load_bars()
    high, low, close, vol = df.high, df.low, df.close, df.volume
    series: dict[str, tuple[pd.Series, str]] = {}

    def put(name: str, values: pd.Series, source: str) -> None:
        series[name] = (pd.Series(np.asarray(values, dtype=float)), source)

    # Averages — `ta`, the textbook definitions (first-value seed for EMA, see ema_first_seed).
    put("sma20", trend.SMAIndicator(close, 20).sma_indicator(), "ta.trend.SMAIndicator")
    put("ema20", trend.EMAIndicator(close, 20).ema_indicator(), "ta.trend.EMAIndicator (first-value seed)")
    put("wma20", trend.WMAIndicator(close, 20).wma(), "ta.trend.WMAIndicator")

    # Momentum.
    put("rsi14", rsi_wilder(close, 14), "Wilder RSI, simple-mean seed (TA-Lib RSI); `ta` seeds from the first bar")
    macd_line = ema_first_seed(close, 12) - ema_first_seed(close, 26)
    macd_signal = ema_over_defined(macd_line, 9)
    put("macd", macd_line, "ema12 - ema26, as ta.trend.MACD")
    put("macdSignal", macd_signal, "ema9 of the MACD line from its first defined value, as ta.trend.MACD")
    put("macdHist", macd_line - macd_signal, "line - signal")
    put("cci20", trend.CCIIndicator(high, low, close, 20, 0.015).cci(), "ta.trend.CCIIndicator (mean absolute deviation)")
    put("wr14", momentum.WilliamsRIndicator(high, low, close, 14).williams_r(), "ta.momentum.WilliamsRIndicator")
    stoch = momentum.StochasticOscillator(high, low, close, window=14, smooth_window=3)
    put("stochK", stoch.stoch(), "ta.momentum.StochasticOscillator %K (raw)")
    put("stochD", stoch.stoch_signal(), "ta.momentum.StochasticOscillator %D (sma3 of %K)")
    put("roc10", momentum.ROCIndicator(close, 10).roc(), "ta.momentum.ROCIndicator")
    put("momentum10", close - close.shift(10), "close - close[10]")
    put("uo", momentum.UltimateOscillator(high, low, close, 7, 14, 28, 4.0, 2.0, 1.0).ultimate_oscillator(), "ta.momentum.UltimateOscillator")
    put("ao", momentum.AwesomeOscillatorIndicator(high, low, 5, 34).awesome_oscillator(), "ta.momentum.AwesomeOscillatorIndicator")
    put("tsi", momentum.TSIIndicator(close, 25, 13).tsi(), "ta.momentum.TSIIndicator")
    put("kama", momentum.KAMAIndicator(close, 10, 2, 30).kama(), "ta.momentum.KAMAIndicator")
    # SMI ergodic is the TSI with (20, 5) and a 5-bar signal: TradingView's definition.
    change = close.diff()
    smi_num = ema_first_seed(ema_first_seed(change, 20), 5)
    smi_den = ema_first_seed(ema_first_seed(change.abs(), 20), 5)
    smi = 100 * smi_num / smi_den
    put("smiErgodic", smi, "100·ema5(ema20(Δclose)) / ema5(ema20(|Δclose|)) — the TSI form TradingView uses")
    put("smiErgodicSignal", ema_over_defined(smi, 5), "ema5 of the SMI from its first defined value")
    ppo = momentum.PercentagePriceOscillator(close, 26, 12, 9)
    put("ppo", ppo.ppo(), "ta.momentum.PercentagePriceOscillator")
    put("ppoSignal", ppo.ppo_signal(), "ta.momentum.PercentagePriceOscillator signal")
    pvo = momentum.PercentageVolumeOscillator(vol, 26, 12, 9)
    put("pvo", pvo.pvo(), "ta.momentum.PercentageVolumeOscillator")
    put("pvoSignal", pvo.pvo_signal(), "ta.momentum.PercentageVolumeOscillator signal")
    rsi = rsi_wilder(close, 14)
    rsi_low = rsi.rolling(14).min()
    rsi_high = rsi.rolling(14).max()
    stoch_rsi = (rsi - rsi_low) / (rsi_high - rsi_low) * 100
    stoch_rsi_k = stoch_rsi.rolling(3).mean()
    put("stochRsiK", stoch_rsi_k, "Stochastic RSI on the Wilder RSI, %K = sma3 (ta's version sits on its own first-bar-seeded RSI)")
    put("stochRsiD", stoch_rsi_k.rolling(3).mean(), "%D = sma3 of %K")

    # Volatility.
    put("tr", true_range(df), "true range, bar 0 = high - low")
    put("atr14", wilder(true_range(df), 14), "Wilder ATR, simple-mean seed (TA-Lib ATR; ta.volatility.AverageTrueRange agrees)")
    bb = volatility.BollingerBands(close, 20, 2)
    put("bbBasis", bb.bollinger_mavg(), "ta.volatility.BollingerBands (population stdev)")
    put("bbUpper", bb.bollinger_hband(), "ta.volatility.BollingerBands")
    put("bbLower", bb.bollinger_lband(), "ta.volatility.BollingerBands")
    put("stdDev20", close.rolling(20).std(ddof=0), "population standard deviation, rolling 20")
    kc_basis = ema_first_seed(close, 20)
    kc_atr = wilder(true_range(df), 10)
    put("kcBasis", kc_basis, "ema20")
    put("kcUpper", kc_basis + 2 * kc_atr, "ema20 + 2·atr10 (TradingView Keltner, EMA basis)")
    put("kcLower", kc_basis - 2 * kc_atr, "ema20 - 2·atr10")
    dc = volatility.DonchianChannel(high, low, close, 20)
    put("dcUpper", dc.donchian_channel_hband(), "ta.volatility.DonchianChannel")
    put("dcLower", dc.donchian_channel_lband(), "ta.volatility.DonchianChannel")
    put("dcBasis", dc.donchian_channel_mband(), "ta.volatility.DonchianChannel")

    # Trend.
    up_move = high.diff()
    down_move = -low.diff()
    plus_dm = pd.Series(np.where((up_move > down_move) & (up_move > 0), up_move, 0.0))
    minus_dm = pd.Series(np.where((down_move > up_move) & (down_move > 0), down_move, 0.0))
    tr14 = wilder(true_range(df), 14, start=1)
    pdm14 = wilder(plus_dm, 14, start=1)
    mdm14 = wilder(minus_dm, 14, start=1)
    plus_di = 100 * pdm14 / tr14
    minus_di = 100 * mdm14 / tr14
    dx = 100 * (plus_di - minus_di).abs() / (plus_di + minus_di)
    put("plusDi", plus_di, "Wilder DMI from bar 1 (TA-Lib / TradingView ta.dmi); ta.trend.ADXIndicator departs from it")
    put("minusDi", minus_di, "Wilder DMI from bar 1")
    put("adx14", wilder(dx, 14, start=14), "Wilder smoothing of DX seeded with the mean of the first 14 defined DX values (bars 14..27)")
    vi = trend.VortexIndicator(high, low, close, 14)
    put("vortexPlus", vi.vortex_indicator_pos(), "ta.trend.VortexIndicator")
    put("vortexMinus", vi.vortex_indicator_neg(), "ta.trend.VortexIndicator")
    ichi = trend.IchimokuIndicator(high, low, 9, 26, 52, visual=False)
    put("tenkan", ichi.ichimoku_conversion_line(), "ta.trend.IchimokuIndicator")
    put("kijun", ichi.ichimoku_base_line(), "ta.trend.IchimokuIndicator")
    put("spanA", ichi.ichimoku_a(), "ta.trend.IchimokuIndicator (unshifted)")
    put("spanB", (high.rolling(52).max() + low.rolling(52).min()) / 2, "Ichimoku span B over a full 52-bar window (ta reports it from bar 0 over a growing window)")
    # TRIX: three EMAs chained with no masking between stages (each seeds on the previous stage's
    # first value), reported after 3·(n-1)+1 bars. `ta` masks each stage to min_periods=n, which
    # changes what the next stage seeds on — a different, and rarer, convention.
    # Each stage seeds on the first value the stage before *reported* (bar n-1), which is where
    # TA-Lib's lookback puts it too; only the seed value differs (first value here, SMA there).
    e1 = close.ewm(span=18, adjust=False).mean()
    e2 = e1.where(e1.index >= 17).ewm(span=18, adjust=False).mean()
    e3 = e2.where(e2.index >= 17).ewm(span=18, adjust=False).mean()
    trix_bp = 10_000 * (e3 - e3.shift(1)) / e3.shift(1)
    trix_bp.iloc[: 3 * 17 + 1] = np.nan
    put("trix", trix_bp, "10000·Δ(ema18³(close))/ema18³ from bar 52; each stage seeded at bar 17 on the stage before")
    put("trixSignal", ema_over_defined(trix_bp, 9), "ema9 of TRIX from its first defined value")
    put("dpo20", trend.DPOIndicator(close, 20).dpo(), "ta.trend.DPOIndicator")
    def roc(n: int) -> pd.Series:
        return (close - close.shift(n)) / close.shift(n) * 100

    kst_line = (
        roc(10).rolling(10).mean()
        + 2 * roc(15).rolling(10).mean()
        + 3 * roc(20).rolling(10).mean()
        + 4 * roc(30).rolling(15).mean()
    )
    put("kst", kst_line, "KST (10,15,20,30 / 10,10,10,15) over full windows, first at bar 44; ta fills its windows from bar 0")
    put("kstSignal", kst_line.rolling(9).mean(), "sma9 of KST")
    put("massIndex", trend.MassIndex(high, low, 9, 25).mass_index(), "ta.trend.MassIndex")
    # Aroon over length+1 bars, as TradingView defines it; ta.trend.AroonIndicator uses length bars.
    n = 14
    aroon_up = pd.Series([np.nan] * len(close))
    aroon_down = pd.Series([np.nan] * len(close))
    for i in range(n, len(close)):
        window_h = high.iloc[i - n : i + 1].to_numpy()
        window_l = low.iloc[i - n : i + 1].to_numpy()
        since_high = n - int(np.argmax(window_h))
        since_low = n - int(np.argmin(window_l))
        aroon_up.iloc[i] = 100.0 * (n - since_high) / n
        aroon_down.iloc[i] = 100.0 * (n - since_low) / n
    put("aroonUp", aroon_up, "Aroon over length+1 bars (TradingView); ta uses length bars")
    put("aroonDown", aroon_down, "Aroon over length+1 bars (TradingView)")
    put("psar", wilder_psar(high, low, 0.02, 0.2), "Wilder's parabolic SAR written out (both-prior-bars clamp); ta clamps against one bar and drifts for a bar at 111")

    # Volume.
    obv_steps = pd.Series(np.where(close > close.shift(1), vol, np.where(close < close.shift(1), -vol, 0.0)))
    obv_steps.iloc[0] = 0.0
    put("obv", obv_steps.cumsum(), "OBV starting at 0 (TradingView); ta and TA-Lib start at +volume[0]")
    tp = (high + low + close) / 3
    put("vwap", (tp * vol).cumsum() / vol.cumsum(), "cumulative VWAP over the whole series")
    raw_flow = tp * vol
    tp_up = tp > tp.shift(1)
    tp_down = tp < tp.shift(1)
    pos_flow = pd.Series(np.where(tp_up, raw_flow, 0.0))
    neg_flow = pd.Series(np.where(tp_down, raw_flow, 0.0))
    pos_flow.iloc[0] = np.nan
    neg_flow.iloc[0] = np.nan
    mfr = pos_flow.rolling(14).sum() / neg_flow.rolling(14).sum()
    put("mfi14", 100 - 100 / (1 + mfr), "MFI over 14 flows, first at bar 14 (TA-Lib); ta reports one bar early")
    put("cmf20", volume.ChaikinMoneyFlowIndicator(high, low, close, vol, 20).chaikin_money_flow(), "ta.volume.ChaikinMoneyFlowIndicator")
    put("adl", volume.AccDistIndexIndicator(high, low, close, vol).acc_dist_index(), "ta.volume.AccDistIndexIndicator")
    put("forceIndex13", volume.ForceIndexIndicator(close, vol, 13).force_index(), "ta.volume.ForceIndexIndicator")
    put("eom14", volume.EaseOfMovementIndicator(high, low, vol, 14).sma_ease_of_movement(), "ta.volume.EaseOfMovementIndicator (×1e8 scaling, as the engine and the web terminal); TradingView uses ×1e4")
    put("pvt", ((close.diff() / close.shift(1)) * vol).fillna(0.0).cumsum(), "price-volume trend, cumulative from 0")

    # --- the price-driven series types, from their definitions
    # Renko, traditional: a brick of fixed height from the first close, laid only when the close
    # clears a whole brick; no wicks. Each brick is its (open, close).
    def renko(brick: float) -> tuple[list[float], list[float]]:
        opens: list[float] = []
        closes: list[float] = []
        base = close.iloc[0]
        for c in close:
            while c >= base + brick:
                opens.append(base)
                closes.append(base + brick)
                base += brick
            while c <= base - brick:
                opens.append(base)
                closes.append(base - brick)
                base -= brick
        return opens, closes

    ro, rc = renko(1.0)
    put("renkoOpen", pd.Series(ro), "traditional Renko, brick 1.0 from the first close, close-driven — StockCharts / TradingView")
    put("renkoClose", pd.Series(rc), "traditional Renko, brick 1.0")

    # Three-line break: a new line in the trend on any close past the last line's close; a reversal
    # only on a close beyond the extreme of the last three lines, drawn from that extreme.
    def line_break(n: int) -> tuple[list[float], list[float]]:
        lines: list[tuple[float, float]] = []
        first_open = df.open.iloc[0]
        for c in close:
            if not lines:
                if abs(c - first_open) > 1e-9:
                    lines.append((first_open, c))
                continue
            last_open, last_close = lines[-1]
            recent = lines[-n:]
            ceiling = max(max(o, k) for o, k in recent)
            floor = min(min(o, k) for o, k in recent)
            rising = last_close >= last_open
            if rising and c > last_close:
                lines.append((last_close, c))
            elif not rising and c < last_close:
                lines.append((last_close, c))
            elif rising and c < floor:
                lines.append((floor, c))
            elif not rising and c > ceiling:
                lines.append((ceiling, c))
        return [o for o, _ in lines], [k for _, k in lines]

    lo, lc = line_break(3)
    put("lineBreakOpen", pd.Series(lo), "three-line break (Nison): continue past the last close, reverse past the extreme of the last three lines")
    put("lineBreakClose", pd.Series(lc), "three-line break")

    # Kagi with a fixed reversal amount on closes: the line extends with the trend and turns only
    # when price retraces the amount from the extreme. The vertices are the turning prices, with
    # the running extreme as the last one.
    def kagi(amount: float) -> list[float]:
        points = [close.iloc[0]]
        direction = 0
        extreme = close.iloc[0]
        for c in close:
            if direction >= 0 and c > extreme:
                extreme = c
                points[-1] = c
                direction = 1
            elif direction <= 0 and c < extreme:
                extreme = c
                points[-1] = c
                direction = -1
            elif direction == 1 and c <= extreme - amount:
                points.append(c)
                extreme = c
                direction = -1
            elif direction == -1 and c >= extreme + amount:
                points.append(c)
                extreme = c
                direction = 1
        return points

    put("kagi", pd.Series(kagi(1.0)), "Kagi, fixed reversal 1.0 on closes: vertices of the line")

    # Point and figure, close method: columns of boxes on a fixed grid. A column extends by whole
    # boxes; a reversal needs `reversal` boxes against the extreme and opens the new column one box
    # in from it, extended by as many whole boxes as the close has covered. The sequence recorded
    # is each column's extreme after every change — every value a multiple of the box away from the
    # first close.
    def pnf(box: float, reversal: int) -> list[float]:
        points: list[float] = []
        direction = 0
        extreme = close.iloc[0]
        for c in close:
            if direction >= 0 and c >= extreme + box:
                extreme += np.floor((c - extreme) / box) * box
                points.append(extreme)
                direction = 1
            elif direction <= 0 and c <= extreme - box:
                extreme -= np.floor((extreme - c) / box) * box
                points.append(extreme)
                direction = -1
            elif direction == 1 and c <= extreme - box * reversal:
                extreme -= np.floor((extreme - c) / box) * box
                points.append(extreme)
                direction = -1
            elif direction == -1 and c >= extreme + box * reversal:
                extreme += np.floor((c - extreme) / box) * box
                points.append(extreme)
                direction = 1
        return points

    put("pointAndFigure", pd.Series(pnf(0.5, 3)), "P&F close method, box 0.5, 3-box reversal: column extremes on the box grid (StockCharts)")

    header = [
        "# Generated by scripts/quality/gen_indicator_reference.py — do not hand-edit.",
        "# The parity file's 120-bar random walk, run through an outside reference: the `ta`",
        "# Python library where its definition is the textbook one, a formula written out in",
        "# pandas where it is not. IndicatorReferenceTest compares every series at 1e-6.",
        "#",
        "# series: reference",
    ]
    for name, (_, source) in series.items():
        header.append(f"#   {name}: {source}")
    lines = header + ["BARS"]
    for row in df.itertuples(index=False):
        lines.append(f"{row.t},{row.open},{row.high},{row.low},{row.close},{int(row.volume)}")
    for name, (values, _) in series.items():
        lines.append(f"SERIES {name}")
        lines.append(",".join("" if (v is None or np.isnan(v)) else f"{v:.10g}" for v in values.tolist()))
    TARGET.write_text("\n".join(lines) + "\n")
    print(f"wrote {TARGET.relative_to(ROOT)}: {len(series)} series over {len(df)} bars")


if __name__ == "__main__":
    main()
