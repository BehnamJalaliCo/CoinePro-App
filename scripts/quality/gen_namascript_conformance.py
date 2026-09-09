#!/usr/bin/env python3
"""Writes the generated half of the NamaScript conformance suite.

One script per built-in — every `ta.`, `math.`, input, plot and colour form the language binds —
into namascript/src/jvmTest/resources/conformance/gen_*.nama. Each file's expectations are then
recorded from the engine by ConformanceSuiteTest with `-Dnamascript.conformance.record=true` and
committed; a change in a binding shows up as a diff in those headers. The hand-written `sem_*`
scripts beside them are never touched here.

Usage: python3 scripts/quality/gen_namascript_conformance.py
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILTINS = ROOT / "namascript/src/commonMain/kotlin/com/coinepro/core/script/Builtins.kt"
TARGET = ROOT / "namascript/src/jvmTest/resources/conformance"

# How each namespace's functions are called: a template per name, or a default by shape.
SOURCE_LENGTH = "{name}(close, 14)"
LENGTH_ONLY = "{name}(14)"
NO_ARGS = "{name}()"
SPECIAL = {
    "ta.hma": "{name}(close, 21)",
    "ta.kama": "{name}(close, 10, 2, 30)",
    "ta.macd": "{name}(close, 12, 26, 9)", "ta.macd_signal": "{name}(close, 12, 26, 9)", "ta.macd_hist": "{name}(close, 12, 26, 9)",
    "ta.ppo": "{name}(close, 12, 26, 9)", "ta.ppo_signal": "{name}(close, 12, 26, 9)",
    "ta.pvo": "{name}(12, 26, 9)", "ta.pvo_signal": "{name}(12, 26, 9)",
    "ta.tsi": "{name}(close, 25, 13, 13)", "ta.tsi_signal": "{name}(close, 25, 13, 13)",
    "ta.bb_upper": "{name}(close, 20, 2)", "ta.bb_lower": "{name}(close, 20, 2)", "ta.bb_basis": "{name}(close, 20, 2)",
    "ta.bb_percent": "{name}(close, 20, 2)", "ta.bb_width": "{name}(close, 20, 2)",
    "ta.env_upper": "{name}(close, 20, 1)", "ta.env_lower": "{name}(close, 20, 1)", "ta.env_basis": "{name}(close, 20, 1)",
    "ta.keltner_upper": "{name}(20, 2)", "ta.keltner_lower": "{name}(20, 2)", "ta.keltner_basis": "{name}(20, 2)",
    "ta.donchian_upper": "{name}(20)", "ta.donchian_lower": "{name}(20)",
    "ta.supertrend": "{name}(10, 3)", "ta.supertrend_trend": "{name}(10, 3)",
    "ta.stoch_k": "{name}(14, 3)", "ta.stoch_d": "{name}(14, 3)",
    "ta.stochrsi_k": "{name}(close, 14, 14, 3, 3)", "ta.stochrsi_d": "{name}(close, 14, 14, 3, 3)",
    "ta.ichimoku_conversion": "{name}(9, 26)", "ta.ichimoku_base": "{name}(9, 26)",
    "ta.ichimoku_span_a": "{name}(9, 26)", "ta.ichimoku_span_b": "{name}(9, 26, 52)",
    "ta.trix": "{name}(close, 18, 9)", "ta.trix_signal": "{name}(close, 18, 9)",
    "ta.smi": "{name}(close, 20, 5, 5)", "ta.smi_signal": "{name}(close, 20, 5, 5)",
    "ta.ultimate": "{name}(7, 14, 28)", "ta.crsi": "{name}(close, 3, 2, 100)",
    "ta.chaikin_vol": "{name}(10, 10)", "ta.chaikin_osc": "{name}(3, 10)",
    "ta.klinger": "{name}(34, 55, 13)", "ta.klinger_signal": "{name}(34, 55, 13)",
    "ta.mass": "{name}(25, 9)", "ta.psar": "{name}(0.02, 0.2)", "ta.vstop": "{name}(20, 2)",
    "ta.t3": "{name}(close, 10, 0.7)", "ta.coppock": "{name}(close, 14, 11, 10)",
    "ta.correlation": "{name}(close, open, 20)", "ta.kst": "{name}(close)", "ta.kst_signal": "{name}(close)",
    "ta.change": "{name}(close, 1)", "ta.roc": "{name}(close, 12)", "ta.cum": "{name}(volume)",
    "ta.rising": "marker({name}(close, 3), title=\"r\")", "ta.falling": "marker({name}(close, 3), title=\"f\")",
    "ta.barssince": "{name}(close > open)", "ta.valuewhen": "{name}(close > open, close, 0)",
    "ta.pivothigh": "{name}(5, 5)", "ta.pivotlow": "{name}(5, 5)",
    "ta.crossover": "marker({name}(close, ta.sma(close, 20)), title=\"x\")",
    "ta.crossunder": "marker({name}(close, ta.sma(close, 20)), title=\"x\")",
    "ta.highest": "{name}(high, 20)", "ta.lowest": "{name}(low, 20)", "ta.sum": "{name}(volume, 20)",
    "ta.avg": "{name}(close, 20)", "ta.variance": "{name}(close, 20)", "ta.stdev": "{name}(close, 20)",
    "ta.bop": "{name}(1)", "ta.ao": "{name}()", "ta.ac": "{name}()", "ta.tr": "{name}()", "ta.vwap": "{name}()",
    "ta.obv": "{name}()", "ta.ad": "{name}()", "ta.pvt": "{name}()", "ta.netvolume": "{name}()",
    "ta.alligator_jaw": "{name}()", "ta.alligator_teeth": "{name}()", "ta.alligator_lips": "{name}()",
    "ta.force": "{name}(13)", "ta.eom": "{name}(14)", "ta.mfi": "{name}(14)", "ta.cmf": "{name}(20)",
    "ta.vwma": "{name}(20)", "ta.williams_r": "{name}(14)", "ta.cci": "{name}(20)", "ta.atr": "{name}(14)",
    "ta.adx": "{name}(14)", "ta.di_plus": "{name}(14)", "ta.di_minus": "{name}(14)",
    "ta.aroon_up": "{name}(14)", "ta.aroon_down": "{name}(14)", "ta.vortex_plus": "{name}(14)",
    "ta.vortex_minus": "{name}(14)", "ta.fisher": "{name}(9)", "ta.fisher_signal": "{name}(9)",
    "ta.chop": "{name}(14)", "ta.rvi": "{name}(10)", "ta.rvi_signal": "{name}(10)",
    "ta.hv": "{name}(close, 10)", "ta.linreg": "{name}(close, 20)", "ta.mcginley": "{name}(close, 14)",
    "ta.dpo": "{name}(close, 20)", "ta.cmo": "{name}(close, 9)", "ta.momentum": "{name}(close, 10)",
    "math.pow": "{name}(close, 2)", "math.max": "{name}(close, open)", "math.min": "{name}(close, open)",
    "math.avg": "{name}(close, open)", "math.clamp": "{name}(close, 90, 110)",
    "math.log": "{name}(close)", "math.log10": "{name}(close)", "math.sqrt": "{name}(close)",
}
MATH_DEFAULT = "{name}(close)"


def builtin_names() -> list[str]:
    text = BUILTINS.read_text()
    names = re.findall(r'^\s+"([a-z_.]+)"(?:, "([a-z_.]+)")? ->', text, re.M)
    flat = []
    for first, second in names:
        flat.append(first)
        if second:
            flat.append(second)
    return sorted(set(flat))


def call_for(name: str) -> str | None:
    if name in SPECIAL:
        return SPECIAL[name].format(name=name)
    if name.startswith("ta."):
        return SOURCE_LENGTH.format(name=name)
    if name.startswith("math."):
        return MATH_DEFAULT.format(name=name)
    return None


def main() -> None:
    TARGET.mkdir(parents=True, exist_ok=True)
    for old in TARGET.glob("gen_*.nama"):
        old.unlink()
    written = 0

    def write(stem: str, body: str) -> None:
        nonlocal written
        (TARGET / f"gen_{stem}.nama").write_text(body.rstrip() + "\n")
        written += 1

    for name in builtin_names():
        call = call_for(name)
        if call is None:
            continue
        stem = name.replace(".", "_")
        if call.startswith("marker("):
            write(stem, f"// {name}: a condition series, counted through a marker\n{call}\n")
        else:
            write(stem, f"// {name}\nplot({call}, title=\"{name}\")\n")
            # A second form: the same call on the script's own pane, and offset by one bar, so
            # both the pane routing and the history operator are exercised per built-in.
            write(stem + "_pane_offset", f"// {name}, own pane, one bar back\nx = {call}\nplot(x[1], title=\"{name}[1]\", pane=\"own\")\n")

    # The non-ta surface: inputs, plots, colours, levels, markers, backgrounds, alerts, logs.
    extras = {
        "input_default": 'len = input(20, title="Length", min=5, max=50)\nplot(ta.sma(close, len), title="sma")',
        "input_int_rounds": 'len = input.int(19.6, title="Length")\nplot(len)',
        "input_float": 'k = input.float(2.5, title="Mult")\nplot(close * k)',
        "input_bool": 'on = input.bool(true, title="Show")\nplot(on ? close : open)',
        "plot_styles": 'plot(close, title="c", color=color.gold, width=2, dashed=true)\nplot(open, title="o", color=color.new(color.blue, 40), pane="own")',
        "hline_levels": 'plot(ta.rsi(close, 14), pane="own")\nhline(70, title="over", color=color.sell, pane="own")\nhline(30, title="under", color=color.buy, pane="own")',
        "marker_styles": 'marker(close > open, title="up", style="up")\nmarker(close < open, title="down", style="down")\nmarker(close == open, title="flat")',
        "plotshape_pine_names": 'plotshape(close > open, title="tri", style="triangleup", color=color.green)\nplotchar(close < open, title="chr", style="arrowdown")',
        "bgcolor": 'bgcolor(close > ta.sma(close, 20), color.new(color.gold, 80))',
        "alertcondition": 'alertcondition(ta.crossover(close, ta.sma(close, 20)), "golden")',
        "signal_long": 'atr = ta.atr(14)\nsignal(close > ta.sma(close, 20), close, close - 2 * atr, close + 3 * atr)',
        "log_lines": 'log("hello")\nplot(close)',
        "colour_names": 'plot(close, color=color.silver)\nplot(open, color=color.purple)\nplot(hl2, color=color.teal)\nplot(hlc3, color=color.orange)',
        "series_builtins": 'plot(hl2)\nplot(hlc3)\nplot(ohlc4)\nplot(bar_index, pane="own")\nplot(time, pane="own")',
        "confirmed": 'marker(close > open and confirmed, title="closed")',
        "iff_nz": 'plot(iff(close > open, 1, 0), pane="own")\nplot(nz(ta.sma(close, 200), 0), pane="own")',
        "arithmetic_broadcast": 'plot(close * 2 + 1)\nplot(close / open)\nplot(close % 10, pane="own")\nplot(-close, pane="own")',
        "comparison_chain": 'marker(close > open and high > high[1] or low < low[1], title="mix")',
        "reassign": 'x = close\nx := x * 2\nplot(x)',
        "line_continuation": 'plot(close + \\\n  open)',
        "comment_header": '//@version=1\n// a comment\nplot(close) // trailing',
    }
    for stem, body in extras.items():
        write(stem, body)
    print(f"wrote {written} generated scripts into {TARGET.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
