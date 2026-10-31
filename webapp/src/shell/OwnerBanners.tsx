import { useEffect } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { pageFromPath } from '../pages/registry';
import { ownerDismiss, ownerOpen, ownerShown, useOwner, type OwnerMessage } from '../data/owner';
import { Icon } from '../ui/Icon';
import '../pages/home2.css';

// The maintenance banner, the update banner and the first waiting campaign card, above the chart
// (OLD app/owner OwnerInbox.kt `OwnerBanners`). Nothing at all while the board is empty.
const W = {
  close: ['بستن', 'Close'], update: ['به‌روزرسانی', 'Update'], updateDefault: ['نسخه‌ی تازه‌ی پرو چارت آماده است.', 'A newer Pro Chart is ready.'],
  maintenanceDefault: ['سرورها در حال به‌روزرسانی‌اند؛ ممکن است قیمت‌ها مدتی با تأخیر برسند.', 'The servers are being updated; prices may arrive late for a while.'],
  coupon: ['کد', 'Code'], open: ['باز کردن', 'Open'],
} as const;

export function OwnerBanners() {
  const board = useOwner((s) => s.board);
  const lang = usePrefs((s) => s.lang);
  const pick = (x: readonly [string, string]) => (lang === 'fa' ? x[0] : x[1]);
  const card = board.cards[0] ?? null;
  if (board.maintenance == null && board.updateNudge == null && !card) return null;
  return (
    <div className="owner-banners" dir={lang === 'fa' ? 'rtl' : 'ltr'}>
      {board.maintenance != null ? (
        <div className="owner-bar warn" role="status"><Icon name="ph-warning" size="sm" /><span>{board.maintenance || pick(W.maintenanceDefault)}</span></div>
      ) : null}
      {board.updateNudge != null ? (
        <div className="owner-bar gold" role="status"><Icon name="ph-info" size="sm" /><span>{board.updateNudge || pick(W.updateDefault)}</span>
          <button type="button" className="link-btn" onClick={() => window.location.reload()}>{pick(W.update)}</button>
        </div>
      ) : null}
      {card ? <OwnerCard message={card} pick={pick} /> : null}
    </div>
  );
}

function OwnerCard({ message, pick }: { message: OwnerMessage; pick(x: readonly [string, string]): string }) {
  useEffect(() => { ownerShown(message); }, [message]);
  const opens = Boolean(message.link || message.cta);
  const go = () => ownerOpen(message, (path) => { const p = pageFromPath(path); if (p) usePage.getState().open(p.id, p.param); });
  return (
    <div className={`owner-card${message.kind === 'offer' ? ' offer' : ''}`} data-owner={message.id}>
      <div className="owner-head">
        <Icon name="ph-bell" size="sm" />
        <b>{message.title}</b>
        <button type="button" className="ib" onClick={() => ownerDismiss(message)} aria-label={pick(W.close)}><Icon name="ph-x" size="sm" /></button>
      </div>
      {message.body ? <p>{message.body}</p> : null}
      {message.coupon || opens ? (
        <div className="owner-actions">
          {message.coupon ? <button type="button" className="owner-coupon num" onClick={() => void navigator.clipboard?.writeText(message.coupon).catch(() => undefined)}>{`${pick(W.coupon)} ${message.coupon}`}</button> : null}
          {opens ? <button type="button" className="link-btn" onClick={go}>{message.cta || pick(W.open)}</button> : null}
        </div>
      ) : null}
    </div>
  );
}
