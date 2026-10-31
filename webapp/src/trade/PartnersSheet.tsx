import { useEffect, useState } from 'react';
import { useL } from '../pages/kit';
import { Sheet } from '../script/Sheet';
import { cryptoRules, forexRules } from '../vip/api';
import { isSafeLink, partnerList, type Partner, type PartnerId } from './partners';
import type { PartnerLogo } from './partnerLogos';

// «معامله با کارگزار» (the phone's TradePartnersSheet.kt), the door behind the top bar's Trade. The
// reader's own route comes first — a reader who tapped Trade to place a trade should not find three
// sign-up buttons above the thing they meant — then the three venues, broker first: each its mark, one
// line saying what kind of account it is, one button. Nothing claims a spread, a fee or a bonus.

function Logo({ logo, name, wide }: { logo: PartnerLogo; name: string; wide: boolean }) {
  return (
    <svg className={`partner-logo${wide ? ' wide' : ''}`} viewBox={logo.viewBox} role="img" aria-label={name} preserveAspectRatio="xMinYMid meet">
      {logo.paths.map((p, i) => <path key={i} d={p.d} fill={p.fill} fillRule={p.evenOdd ? 'evenodd' : undefined} />)}
    </svg>
  );
}

export function PartnersSheet({ onClose, onTradeHere }: { onClose(): void; onTradeHere: (() => void) | null }) {
  const L = useL();
  const [links, setLinks] = useState<Partial<Record<PartnerId, string | null>>>({});
  useEffect(() => {
    let alive = true;
    void Promise.allSettled([cryptoRules(), forexRules()]).then(([c, f]) => {
      if (!alive) return;
      const next: Partial<Record<PartnerId, string | null>> = {};
      if (c.status === 'fulfilled') for (const x of c.value.exchanges) if ((x.id === 'lbank' || x.id === 'ourbit') && isSafeLink(x.referralUrl)) next[x.id] = x.referralUrl;
      if (f.status === 'fulfilled' && isSafeLink(f.value.referralUrl)) next.oneroyal = f.value.referralUrl;
      setLinks(next);
    });
    return () => { alive = false; };
  }, []);
  const partners: Partner[] = partnerList(links);
  return (
    <Sheet title={L('معامله با کارگزار', 'Trade with a broker')} subtitle={L('جایی که می‌توانید معامله را واقعاً ثبت کنید', 'Where you can actually place the trade')} onClose={onClose} className="partners-sheet">
      {onTradeHere ? <button className="btn btn-primary trade-here" onClick={onTradeHere}>{L('باز کردن در پرو چارت', 'Open in Pro Chart')}</button> : null}
      <span className="muted small fa">{L('کارگزار و صرافی‌های همکار', 'Partner broker and exchanges')}</span>
      {partners.map((p) => (
        <div key={p.id} className="card partner-card" data-partner={p.id}>
          <div className="partner-mark" dir="ltr"><Logo logo={p.logo} name={p.name} wide={p.carriesName} />{p.carriesName ? null : <b>{p.name}</b>}</div>
          <span className="muted fa">{p.kind === 'broker' ? L('کارگزار فارکس — حساب MT5', 'Forex broker — MT5 account') : L('صرافی ارز دیجیتال — حساب اسپات و فیوچرز', 'Crypto exchange — spot and futures')}</span>
          <a className="btn btn-quiet sm partner-open" href={p.url} target="_blank" rel="noopener noreferrer">{L('افتتاح حساب', 'Open an account')}</a>
        </div>
      ))}
      <p className="muted small fa">{L('این‌ها معرفی هستند، نه توصیه. حساب و پول شما نزد همان مجموعه است و شرایط، کارمزد و ریسکش را باید از خودشان بخوانید. لینک‌ها در مرورگر خودتان باز می‌شوند.', 'These are introductions, not advice. Your account and your money sit with that company, and their terms, fees and risks are theirs to state. Links open in your own browser.')}</p>
    </Sheet>
  );
}
