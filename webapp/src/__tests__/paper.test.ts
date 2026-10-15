import { beforeEach, describe, expect, it } from 'vitest';
import { paperStats, sizeForRisk, usePaper, DEFAULT_RULES, type PaperTrade } from '../state/paper';

const trade = (pnl: number, closedAt: number): PaperTrade => ({ id: String(closedAt), symbol: 'BTCUSDT', side: 'long', qty: 1, entry: 100, exit: 100 + pnl, openedAt: closedAt - 1, closedAt, pnl });

describe('paper account', () => {
  beforeEach(() => { usePaper.setState({ balance: 10_000, positions: [], history: [], orders: [], rules: { ...DEFAULT_RULES, spreadPercent: 0, slippagePercent: 0, takerFeePercent: 0 } }); });

  it('sizes a trade from the risk and the stop', () => {
    expect(sizeForRisk(10_000, 1, 100, 98)).toBe(50);
    expect(sizeForRisk(10_000, 1, 100, 100)).toBe(0);
  });

  it('keeps the record: runs, drawdown and the curve, oldest first', () => {
    const s = paperStats([trade(-5, 6), trade(-5, 5), trade(10, 4), trade(10, 3), trade(10, 2), trade(-20, 1)], 1000);
    expect([s.n, s.wins, s.winStreak, s.lossStreak, s.net]).toEqual([6, 3, 3, 2, 0]);
    expect(s.curve.map((p) => p.v)).toEqual([1000, 980, 990, 1000, 1010, 1005, 1000]);
    expect(s.dd).toBe(20);
  });

  it('closes part of a position into the history and keeps the rest', () => {
    const id = usePaper.getState().open('BTCUSDT', 'long', 2, 100)!;
    usePaper.getState().closePart(id, 0.25, 110);
    const st = usePaper.getState();
    expect(st.positions[0]!.qty).toBe(1.5);
    expect(st.history[0]!.qty).toBe(0.5);
    expect(st.history[0]!.pnl).toBeCloseTo(5);
    expect(st.balance).toBeCloseTo(10_005);
  });

  it('moves a working order', () => {
    usePaper.getState().place({ symbol: 'BTCUSDT', side: 'long', type: 'limit', qty: 1, limitPrice: 90 }, 100);
    const o = usePaper.getState().orders[0]!;
    usePaper.getState().modifyOrder(o.id, { limitPrice: 92, sl: 88 });
    expect(usePaper.getState().orders[0]).toMatchObject({ limitPrice: 92, sl: 88 });
  });
});
