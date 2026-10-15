// Everything trading adds to the workspace, mounted once beside the chart: the `pc:order-ticket`
// listener and its ticket, the live positions and orders on the chart, the VIP channel's signals on
// the chart, the watchers that keep the books, the entitlements and the channel fresh, and their
// notices.
import { useEffect } from 'react';
import { usePrefs } from '../state/prefs';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { SymbolLogo } from '../ui/SymbolLogo';
import { usePanels } from '../state/panels';
import { intervalOfTimeframe } from '../data/signals';
import { startTradingWatch } from './store';
import { startEntitlementWatch } from './entitlements';
import { listenForTickets } from './ticket';
import { startVipWatch, useVip } from './vip';
import { OrderTicketHost } from './OrderTicket';
import { ChartTrading } from './ChartTrading';
import { VipOverlay } from './VipOverlay';
import { VIP_PANEL } from './VipPanel';
import { useNotice } from './toast';
import { hostActions } from './host';
import './trade.css';

export function TradeHost({ onSignIn }: { onSignIn(): void }) {
  hostActions.signIn = onSignIn;
  useEffect(() => {
    const offs = [listenForTickets(), startTradingWatch(), startEntitlementWatch(), startVipWatch(() => usePrefs.getState().lang)];
    return () => offs.forEach((f) => f());
  }, []);
  return (
    <>
      <ChartTrading />
      <VipOverlay />
      <OrderTicketHost onSignIn={onSignIn} />
      <Notice />
      <VipToast />
    </>
  );
}

function Notice() {
  const notice = useNotice((s) => s.notice);
  const clear = useNotice((s) => s.clear);
  if (!notice) return null;
  return (
    <div className={`tr-notice ${notice.tone}`} role="status" onClick={clear}>
      <Icon name={notice.tone === 'error' ? 'ph-warning' : notice.tone === 'ok' ? 'ph-check' : 'ph-info'} size="sm" />
      <span className="fa" dir="auto">{notice.text}</span>
    </div>
  );
}

function VipToast() {
  const L = useL();
  const s = useVip((x) => x.toast);
  const dismiss = useVip((x) => x.dismissToast);
  useEffect(() => { if (!s) return; const id = setTimeout(dismiss, 12_000); return () => clearTimeout(id); }, [s, dismiss]);
  if (!s) return null;
  const show = () => {
    const p = usePrefs.getState();
    p.setSymbol(s.symbol);
    const iv = intervalOfTimeframe(s.timeframe);
    if (iv) p.setInterval(iv);
    useVip.getState().setFocus(s);
    if (usePanels.getState().open !== VIP_PANEL) usePanels.getState().toggle(VIP_PANEL);
    dismiss();
  };
  return (
    <div className="toast vip-toast" role="alert">
      <Icon name="ph-crown-simple" size="sm" className="vip-crown" />
      <SymbolLogo symbol={s.symbol} size={18} />
      <span className="fa">{L('سیگنال تازه‌ی VIP', 'New VIP signal')}</span>
      <b className="sym" dir="ltr">{s.symbol}</b>
      <span className={`sig-dir ${s.direction === 'long' ? 'up' : 'down'}`}>{s.direction === 'long' ? 'LONG' : 'SHORT'}</span>
      <span className="num muted">{s.timeframe}</span>
      <button className="btn btn-quiet sm" onClick={show}>{L('روی چارت', 'On the chart')}</button>
      <button className="lt" onClick={dismiss} aria-label="×"><Icon name="ph-x" size="sm" /></button>
    </div>
  );
}
