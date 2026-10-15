import { useState } from 'react';
import { useT, usePrefs } from '../state/prefs';
import { useAlerts, type AlertCondition } from '../state/alerts';
import { useMarket, digitsOf } from '../state/market';
import { instrumentOf } from '../data/symbols';
import { formatPrice } from '../chart/format';
import { SymbolLogo } from '../ui/SymbolLogo';
import { Icon } from '../ui/Icon';
import { faAgo } from '../i18n/time';

export function AlertsPanel({ onNew }: { onNew(): void }) {
  const t = useT();
  const list = useAlerts((s) => s.list);
  const remove = useAlerts((s) => s.remove);
  const toggle = useAlerts((s) => s.toggle);
  const setSymbol = usePrefs((s) => s.setSymbol);
  const word = (c: AlertCondition) => t(c === 'above' ? 'alertAbove' : c === 'below' ? 'alertBelow' : 'alertCrossing');
  return (
    <section className="panel-generic alerts">
      <header className="panel-head">
        <span className="fa">{t('alerts')}</span>
        <span className="ws-spacer" />
        <button className="ib" title={t('alertAdd')} onClick={onNew}><Icon name="ph-plus" size="md" /></button>
      </header>
      <div className="panel-body">
        {!list.length ? <div className="empty fa">{t('alertsEmpty')}</div> : null}
        {list.map((a) => {
          const digits = digitsOf(a.symbol);
          return (
            <div key={a.id} className={`alert-row${a.active ? '' : ' off'}`}>
              <button className="alert-main" onClick={() => setSymbol(a.symbol)}>
                <SymbolLogo symbol={a.symbol} size={18} />
                <span className="sym">{a.symbol}</span>
                <span className="fa alert-cond">{word(a.condition)}</span>
                <span className="num">{formatPrice(a.value, digits)}</span>
              </button>
              <span className="alert-sub fa">{a.triggeredAt ? `${t('alertTriggered')} · ${faAgo(a.triggeredAt)}` : faAgo(a.createdAt)}</span>
              <span className="alert-tools">
                <button className="lt" onClick={() => toggle(a.id)} title={a.active ? 'pause' : 'resume'}><Icon name={a.active ? 'ph-pause' : 'ph-play'} size="sm" /></button>
                <button className="lt" onClick={() => remove(a.id)} title={t('remove')}><Icon name="ph-trash" size="sm" /></button>
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

export function AlertDialog({ onClose, price: initialPrice }: { onClose(): void; price?: number }) {
  const t = useT();
  const symbol = usePrefs((s) => s.symbol);
  const tk = useMarket((s) => s.tickers[symbol]);
  const add = useAlerts((s) => s.add);
  const digits = instrumentOf(symbol, tk?.last).digits;
  const start = initialPrice ?? tk?.last ?? 0;
  const [value, setValue] = useState(start ? start.toFixed(digits) : '');
  const [cond, setCond] = useState<AlertCondition>(initialPrice != null && tk && initialPrice < tk.last ? 'below' : 'cross');

  const create = () => {
    const v = Number(value);
    if (!Number.isFinite(v) || v <= 0) return;
    add(symbol, cond, v);
    try { if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission(); } catch { /* ignore */ }
    onClose();
  };

  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="dialog settings" role="dialog" aria-modal="true" aria-label={t('alertAdd')} onKeyDown={(e) => { if (e.key === 'Escape') onClose(); if (e.key === 'Enter') create(); }}>
        <header className="dialog-head">
          <h2 className="fa">{t('alertAdd')}</h2>
          <button className="ib" onClick={onClose} aria-label="close"><Icon name="ph-x" size="md" /></button>
        </header>
        <div className="settings-body">
          <div className="setting-row"><span className="fa">{t('symbol')}</span><span className="sym">{symbol}</span></div>
          <label className="setting-row">
            <span className="fa">{t('alertPrice')}</span>
            <select value={cond} onChange={(e) => setCond(e.target.value as AlertCondition)}>
              <option value="cross">{t('alertCrossing')}</option>
              <option value="above">{t('alertAbove')}</option>
              <option value="below">{t('alertBelow')}</option>
            </select>
          </label>
          <label className="setting-row">
            <span className="fa">{t('alertPrice')}</span>
            <input type="number" className="num" step={1 / 10 ** digits} value={value} onChange={(e) => setValue(e.target.value)} autoFocus />
          </label>
        </div>
        <footer className="dialog-foot">
          <span className="ws-spacer" />
          <button className="btn btn-quiet" onClick={onClose}>{t('back')}</button>
          <button className="btn btn-primary" onClick={create}><span className="fa">{t('alertCreate')}</span></button>
        </footer>
      </div>
    </div>
  );
}

/** The toast when an alert fires, top centre, until dismissed. */
export function AlertToast() {
  const t = useT();
  const fired = useAlerts((s) => s.fired);
  const dismiss = useAlerts((s) => s.dismiss);
  if (!fired) return null;
  return (
    <div className="toast" role="alert">
      <Icon name="ph-bell-ringing" size="md" />
      <span className="fa">{t('alertTriggered')}</span>
      <span className="sym">{fired.symbol}</span>
      <span className="num">{formatPrice(fired.value, digitsOf(fired.symbol))}</span>
      <button className="lt" onClick={dismiss} aria-label="close"><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
