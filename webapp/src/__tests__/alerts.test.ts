import { beforeEach, describe, expect, it } from 'vitest';
import { startAlertWatch, useAlerts } from '../state/alerts';
import { useMarket } from '../state/market';

const tick = (symbol: string, last: number) => useMarket.getState().put([{ symbol, last, change: 0, changePct: 0, ts: Date.now() }]);

describe('price alerts', () => {
  beforeEach(() => { useAlerts.setState({ list: [], fired: null }); localStorage.clear(); });
  it('fires "above" once and goes inactive', () => {
    startAlertWatch();
    useAlerts.getState().add('BTCUSDT', 'above', 100);
    tick('BTCUSDT', 99);
    expect(useAlerts.getState().fired).toBeNull();
    tick('BTCUSDT', 101);
    expect(useAlerts.getState().fired?.symbol).toBe('BTCUSDT');
    expect(useAlerts.getState().list[0]!.active).toBe(false);
  });
  it('"cross" needs a previous price on the other side', () => {
    startAlertWatch();
    useAlerts.getState().add('ETHUSDT', 'cross', 50);
    tick('ETHUSDT', 55);
    expect(useAlerts.getState().fired).toBeNull();
    tick('ETHUSDT', 49);
    expect(useAlerts.getState().fired?.symbol).toBe('ETHUSDT');
  });
  it('"below" fires at the level', () => {
    startAlertWatch();
    useAlerts.getState().add('XAUUSD', 'below', 4000);
    tick('XAUUSD', 4000);
    expect(useAlerts.getState().fired?.condition).toBe('below');
  });
  it('persists the list', () => {
    useAlerts.getState().add('SOLUSDT', 'above', 1);
    expect(JSON.parse(localStorage.getItem('pc.alerts.v1')!)[0].symbol).toBe('SOLUSDT');
  });
});
