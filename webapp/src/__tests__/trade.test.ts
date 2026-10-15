import { beforeEach, describe, expect, it } from 'vitest';
import { isSignalOnly, liveVenueOf, tradePath, venueOf } from '../trade/venue';
import { checkOrder, distanceUnit, entryPrice, levelFrom, pnlAt, qtyDecimals, qtyForPercent, rewardRisk, summarize, valueFrom } from '../trade/math';
import { cleanDetail, onOrderTicket, routeTicket, useTicket } from '../trade/ticket';
import { protectionOf, protectKey, useTrading } from '../trade/store';
import { backendText, readAccount, readFills, readOrder, readPosition, tradeErrorText } from '../trade/wire';
import { combine, readEntitlement } from '../trade/entitlements';
import { newIds, outcomeOf, readRules, statusLabel, winRate } from '../trade/vip';
import { readSignal } from '../data/signals';
import { HttpError } from '../data/http';

const L = (fa: string, en: string) => `${fa}|${en}`;

describe('broker routing', () => {
  it('sends every USDT symbol to TradeYar and the forex list to CoinePro-FX', () => {
    expect(venueOf('BTCUSDT')).toBe('tradeyar');
    expect(venueOf('1000PEPEUSDT')).toBe('tradeyar');
    expect(venueOf('xauusd')).toBe('coineprofx');
    expect(venueOf('EURUSD')).toBe('coineprofx');
    expect(venueOf('US30')).toBe('coineprofx');
    expect(venueOf('AAPL')).toBeNull();
    expect(venueOf('USDT')).toBeNull();
  });
  it('trades live on TradeYar only: forex is signals only', () => {
    expect(liveVenueOf('ETHUSDT')).toBe('tradeyar');
    expect(liveVenueOf('XAUUSD')).toBeNull();
    expect(isSignalOnly('GBPJPY')).toBe(true);
    expect(isSignalOnly('SOLUSDT')).toBe(false);
  });
  it('builds the platforms\' trade paths', () => {
    expect(tradePath('tradeyar', 'orders')).toBe('/up/tradeyar/api/mobile/v1/trade/orders');
    expect(tradePath('coineprofx', '/account')).toBe('/up/coineprofx/api/user/mobile/trade/account');
  });
});

describe('ticket math', () => {
  it('enters a market buy at the ask and a sell at the bid', () => {
    const q = { bid: 99, ask: 101, last: 100 };
    expect(entryPrice('buy', 'market', null, q)).toBe(101);
    expect(entryPrice('sell', 'market', null, q)).toBe(99);
    expect(entryPrice('buy', 'limit', 95, q)).toBe(95);
    expect(entryPrice('buy', 'limit', null, q)).toBeNull();
    expect(entryPrice('sell', 'market', null, { last: 100 })).toBe(100);
  });
  it('turns a distance or a percent into a level on the right side', () => {
    expect(levelFrom('distance', 10, 'buy', 'sl', 100)).toBe(90);
    expect(levelFrom('distance', 10, 'buy', 'tp', 100)).toBe(110);
    expect(levelFrom('distance', 10, 'sell', 'sl', 100)).toBe(110);
    expect(levelFrom('percent', 2, 'sell', 'tp', 100)).toBe(98);
    expect(levelFrom('price', 105, 'buy', 'tp', 100)).toBe(105);
    expect(levelFrom('distance', 50, 'buy', 'sl', 1.1, 0.0001)).toBeCloseTo(1.095, 10);
    expect(levelFrom('distance', 200, 'buy', 'sl', 100)).toBeNull();
    // and back
    expect(valueFrom('distance', 90, 100)).toBe(10);
    expect(valueFrom('percent', 98, 100)).toBeCloseTo(2, 10);
    expect(valueFrom('distance', 1.095, 1.1, 0.0001)).toBeCloseTo(50, 6);
  });
  it('pips for forex, ticks elsewhere', () => {
    expect(distanceUnit('EURUSD', 5)).toBe(0.0001);
    expect(distanceUnit('USDJPY', 3)).toBe(0.01);
    expect(distanceUnit('BTCUSDT', 1)).toBeCloseTo(0.1, 10);
    expect(distanceUnit('XAUUSD', 2)).toBeCloseTo(0.01, 10);
  });
  it('profit, reward to risk and the summary', () => {
    expect(pnlAt('buy', 100, 110, 2)).toBe(20);
    expect(pnlAt('sell', 100, 110, 2)).toBe(-20);
    expect(rewardRisk('buy', 100, 95, 110)).toBe(2);
    expect(rewardRisk('buy', 100, 105, 110)).toBeNull();
    const s = summarize({ side: 'buy', type: 'market', qty: 0.5, entry: 1000, leverage: 10, sl: 990, tp: 1020, venue: 'tradeyar' });
    expect(s.value).toBe(500);
    expect(s.margin).toBe(50);
    expect(s.fee).toBeCloseTo(0.3, 10);
    expect(s.risk).toBe(-5);
    expect(s.reward).toBe(10);
    expect(s.rr).toBe(2);
    expect(s.liquidation).toBe(900);
    expect(summarize({ side: 'buy', type: 'limit', qty: 1, entry: 100, leverage: 1, sl: null, tp: null, venue: 'tradeyar' }).fee).toBeCloseTo(0.02, 10);
    expect(summarize({ side: 'buy', type: 'market', qty: 1, entry: 100, leverage: 1, sl: null, tp: null, venue: null }).fee).toBeNull();
  });
  it('sizes from a share of what is available, fee included, rounded down', () => {
    // 1000 USDT, 10x, 100 % at 100: qty·100·(0.1 + 0.0006) ≤ 1000 → 99.40…
    expect(qtyForPercent(100, 1000, 10, 100, 0.0006, 2)).toBe(99.4);
    expect(qtyForPercent(25, 1000, 1, 81000, 0.0006, 4)).toBe(0.0030);
    expect(qtyForPercent(50, null, 10, 100, 0, 2)).toBeNull();
    expect(qtyDecimals(81000, 'tradeyar')).toBe(4);
    expect(qtyDecimals(2400, 'tradeyar')).toBe(3);
    expect(qtyDecimals(0.08, 'tradeyar')).toBe(0);
    expect(qtyDecimals(1.1, 'coineprofx')).toBe(2);
  });
  it('catches what TradeYar would refuse before anything is sent', () => {
    const base = { venue: 'tradeyar' as const, live: true, side: 'buy' as const, type: 'market' as const, qty: 1, price: null, entry: 100, ref: 100, leverage: 10, sl: null, tp: null, reduceOnly: false, available: 1000, margin: 10 };
    expect(checkOrder(base)).toEqual([]);
    expect(checkOrder({ ...base, type: 'stop', price: 105 }).map((i) => i.field)).toContain('type');
    expect(checkOrder({ ...base, type: 'stop', price: 105, live: false }).map((i) => i.field)).not.toContain('type');
    expect(checkOrder({ ...base, qty: 0 }).map((i) => i.field)).toEqual(['qty']);
    expect(checkOrder({ ...base, sl: 101 }).map((i) => i.field)).toEqual(['sl']);
    expect(checkOrder({ ...base, side: 'sell', tp: 101 }).map((i) => i.field)).toEqual(['tp']);
    expect(checkOrder({ ...base, type: 'limit', price: 130, entry: 130 }).map((i) => i.field)).toEqual(['price']);
    expect(checkOrder({ ...base, leverage: 75 }).map((i) => i.field)).toEqual(['leverage']);
    expect(checkOrder({ ...base, reduceOnly: true, tp: 110 }).map((i) => i.field)).toEqual(['sl']);
    const margin = checkOrder({ ...base, margin: 2000 });
    expect(margin.map((i) => [i.field, i.block])).toEqual([['margin', false]]);
  });
});

describe('the order-ticket contract', () => {
  beforeEach(() => {
    useTicket.setState({ req: null });
    useTrading.setState((s) => ({ mode: null, books: { ...s.books, tradeyar: { ...s.books.tradeyar, status: 'signed-out' } } }));
  });
  const fire = (detail: unknown) => { const e = new CustomEvent('pc:order-ticket', { cancelable: true, detail }); onOrderTicket(e); return e.defaultPrevented; };

  it('keeps only the contract\'s fields', () => {
    expect(cleanDetail({ symbol: ' btcusdt', side: 'buy', price: 81000, type: 'limit', quantity: -1, stopLoss: 'x', source: 'legend', extra: 1 }))
      .toEqual({ symbol: 'BTCUSDT', side: 'buy', type: 'limit', price: 81000, quantity: undefined, stopLoss: undefined, takeProfit: undefined, source: 'legend' });
    expect(cleanDetail({ symbol: 'BTCUSDT', side: 'long' })).toBeNull();
    expect(cleanDetail(null)).toBeNull();
  });
  it('a guest\'s chart order opens the ticket on paper', () => {
    expect(fire({ symbol: 'BTCUSDT', side: 'buy', price: 81000, source: 'legend' })).toBe(true);
    expect(useTicket.getState().req?.mode).toBe('paper');
  });
  it('a guest\'s panel order is left to the panel\'s paper book', () => {
    expect(fire({ symbol: 'BTCUSDT', side: 'buy', quantity: 0.01, source: 'panel' })).toBe(false);
    expect(useTicket.getState().req).toBeNull();
  });
  it('goes live when TradeYar can trade, also for the panel', () => {
    useTrading.setState((s) => ({ books: { ...s.books, tradeyar: { ...s.books.tradeyar, status: 'live' } } }));
    expect(routeTicket({ symbol: 'ETHUSDT', side: 'sell', source: 'legend' })).toEqual({ take: true, mode: 'live' });
    expect(fire({ symbol: 'ETHUSDT', side: 'sell', quantity: 1, source: 'panel' })).toBe(true);
    expect(useTicket.getState().req?.mode).toBe('live');
    useTrading.setState({ mode: 'paper' });
    expect(routeTicket({ symbol: 'ETHUSDT', side: 'sell', source: 'panel' }).take).toBe(false);
    expect(routeTicket({ symbol: 'ETHUSDT', side: 'sell', source: 'legend' })).toEqual({ take: true, mode: 'paper' });
  });
  it('never offers forex live', () => {
    useTrading.setState((s) => ({ books: { ...s.books, tradeyar: { ...s.books.tradeyar, status: 'live' }, coineprofx: { ...s.books.coineprofx, status: 'live' } } }));
    expect(routeTicket({ symbol: 'XAUUSD', side: 'buy', source: 'axis-plus' })).toEqual({ take: true, mode: 'paper' });
    expect(routeTicket({ symbol: 'EURUSD', side: 'buy', source: 'panel' }).take).toBe(false);
  });
  it('leaves an event someone else already took', () => {
    const e = new CustomEvent('pc:order-ticket', { cancelable: true, detail: { symbol: 'BTCUSDT', side: 'buy', source: 'legend' } });
    e.preventDefault();
    onOrderTicket(e);
    expect(useTicket.getState().req).toBeNull();
  });
});

describe('the trade routes\' answers', () => {
  it('reads the account, positions and orders without inventing figures', () => {
    expect(readAccount({ available: 1000, equity: 1050, margin_used: null, trading_enabled: true })).toEqual({ currency: 'USDT', available: 1000, equity: 1050, marginUsed: null, unrealizedPnl: null, tradingEnabled: true });
    expect(readAccount({ trading_enabled: false }).tradingEnabled).toBe(false);
    expect(readPosition({ symbol: 'BTCUSDT', side: 'short', quantity: -0.01, entry_price: 80000 })).toMatchObject({ side: 'short', quantity: 0.01, entry: 80000, mark: null, stopLoss: null });
    expect(readPosition({ symbol: 'BTCUSDT', side: 'flat', quantity: 1 })).toBeNull();
    const o = readOrder({ id: 4, symbol: 'ETHUSDT', side: 'buy', type: 'limit', quantity: 0.5, price: 2300, status: 'open', can_cancel: true, can_amend: true, protection: 'none' })!;
    expect(o).toMatchObject({ id: '4', type: 'limit', price: 2300, canCancel: true, canAmend: true, reduceOnly: false });
    expect(readOrder({ id: 1, symbol: 'X', side: 'hold' })).toBeNull();
  });
  it('reads both history shapes', () => {
    const ty = readFills({ trades: [{ id: 1, symbol: 'BTCUSDT', side: 'sell', opened_at: 1700000000, closed_at: 1700003600, entry: 1, exit: 2, quantity: 3, pnl: -4, fee: 0.1 }] });
    expect(ty[0]).toMatchObject({ side: 'sell', pnl: -4, openedAt: 1700000000000, currency: 'USDT' });
    const fx = readFills({ items: [{ id: 2, symbol: 'XAUUSD', direction: 'BUY', volume: 0.1, entry_price: 4100, exit_price: 4140, net_profit: 40, commission: 0.7, swap: 0.3 }] });
    expect(fx[0]).toMatchObject({ side: 'buy', quantity: 0.1, pnl: 40, fee: 1, currency: 'USD' });
  });
  it('shows the platform\'s own refusal word for word', () => {
    const e = new HttpError(422, { detail: 'قیمت سفارش بیش از ۲۵٪ با قیمت فعلی فاصله دارد.', code: 'TYR-1' }, null);
    expect(backendText(e)).toBe('قیمت سفارش بیش از ۲۵٪ با قیمت فعلی فاصله دارد.');
    expect(tradeErrorText(e, L)).toBe('قیمت سفارش بیش از ۲۵٪ با قیمت فعلی فاصله دارد.');
    expect(backendText(new HttpError(422, { detail: { message: 'Insufficient margin' } }, null))).toBe('Insufficient margin');
    expect(tradeErrorText(new HttpError(-1, 'x', null), L)).toContain('Could not reach');
    expect(tradeErrorText(new Error('x'), L)).toContain('The request failed');
  });
  it('stop and target: the platform\'s, then this browser\'s, then the opening bracket', () => {
    const p = readPosition({ symbol: 'BTCUSDT', side: 'long', quantity: 0.01, entry_price: 80000 })!;
    const opener = readOrder({ id: 9, symbol: 'BTCUSDT', side: 'buy', type: 'market', status: 'filled', stop_loss: 79000, take_profit: 83000, protection: 'armed', created_at: 1000 })!;
    expect(protectionOf('tradeyar', p, [], {})).toEqual({ sl: null, tp: null });
    expect(protectionOf('tradeyar', p, [opener], {})).toEqual({ sl: 79000, tp: 83000 });
    expect(protectionOf('tradeyar', p, [opener], { [protectKey('tradeyar', 'BTCUSDT', 'long')]: { sl: 79500, tp: null, at: Date.now() } })).toEqual({ sl: 79500, tp: null });
    expect(protectionOf('tradeyar', { ...p, stopLoss: 78000 }, [opener], {})).toEqual({ sl: 78000, tp: null });
  });
});

describe('VIP ⇒ everything free', () => {
  it('premium when either platform says so, and VIP names the plan', () => {
    expect(combine([readEntitlement({ all: true, premium: false, plan: null }), undefined])).toEqual({ premium: false, vip: false, forexVip: false, plan: null, endsAt: null });
    expect(combine([readEntitlement({ all: true, premium: true, plan: 'vip', ends_at: null })])).toEqual({ premium: true, vip: true, forexVip: false, plan: 'vip', endsAt: null });
    // CoinePro-FX's «vip» is its own forex VIP, not the TradeYar channel (server report, 2026-10-15).
    expect(combine([readEntitlement({ premium: true, plan: 'monthly', ends_at: '2026-11-01T00:00:00Z' }), readEntitlement({ premium: true, plan: 'vip' })])).toEqual({ premium: true, vip: false, forexVip: true, plan: 'vip', endsAt: null });
    expect(combine([undefined, readEntitlement({ premium: true, plan: 'vip', ends_at: '2027-02-01T00:00:00Z' })]).vip).toBe(false);
    expect(combine([readEntitlement({ premium: true, plan: 'vip' }), readEntitlement({ premium: true, plan: 'vip' })])).toMatchObject({ vip: true, forexVip: true });
    expect(combine([undefined, readEntitlement({ premium: true, plan: 'yearly', ends_at: '2027-01-01T00:00:00Z' })])).toEqual({ premium: true, vip: false, forexVip: false, plan: 'yearly', endsAt: '2027-01-01T00:00:00Z' });
    // A plan named vip is premium even if a field is missing.
    expect(readEntitlement({ plan: 'VIP' }).premium).toBe(true);
  });
});

describe('the VIP channel', () => {
  const sig = (x: Record<string, unknown>) => readSignal({ id: 1, symbol: 'BTCUSDT', direction: 'buy', status: 'ACTIVE', entry: 100, stop_loss: 95, targets: [{ level: 1, price: 105, hit: false }], created_at: '2026-10-15T10:00:00Z', ...x }, 'tradeyar');
  it('tells how a signal ended', () => {
    expect(outcomeOf(sig({}))).toBe('active');
    expect(outcomeOf(sig({ status: 'CLOSED_TP2', closed_at: '2026-10-15T12:00:00Z' }))).toBe('win');
    expect(outcomeOf(sig({ status: 'STOPPED', closed_at: '2026-10-15T12:00:00Z' }))).toBe('loss');
    expect(outcomeOf(sig({ status: 'MANUAL_CLOSE', closed_at: '2026-10-15T12:00:00Z', result: { pnl_usd: -3 } }))).toBe('loss');
    expect(statusLabel(sig({ targets: [{ level: 1, price: 105, hit: true }] }))[1]).toBe('Active · TP1 hit');
    expect(statusLabel(sig({ status: 'STOPPED', closed_at: '2026-10-15T12:00:00Z' }))[1]).toBe('Stopped out');
    const r = winRate([sig({ status: 'CLOSED_TP1', closed_at: 'x' }), sig({ status: 'STOPPED', closed_at: 'x' }), sig({ status: 'CLOSED_TP3', closed_at: 'x' }), sig({})]);
    expect(r).toEqual({ wins: 2, losses: 1, rate: (2 / 3) * 100 });
  });
  it('announces only what is new, and nothing on the first look', () => {
    expect(newIds(['1', '2', '3'], [], true)).toEqual([]);
    expect(newIds(['1', '2', '3'], ['1', '2'], false)).toEqual(['3']);
  });
  it('reads the channel rules from TradeYar, whatever it sends', () => {
    expect(readRules({ min_deposit_usdt: 50, warn_balance_usdt: 10, removal_balance_usdt: 0, rules_fa: ['یک', 'دو'] })).toEqual({ minDeposit: 50, warnBelow: 10, removeAt: 0, linesFa: ['یک', 'دو'] });
    expect(readRules({ notice_fa: 'متن' })).toEqual({ minDeposit: null, warnBelow: null, removeAt: null, linesFa: ['متن'] });
  });
});
