// What stands between the reader and a live order on this symbol's platform, step by step, each
// with the button that fixes it: sign in to TradeYar or CoinePro-FX, connect LBank futures keys,
// verify identity; or the platform's own reason when trading is switched off or not offered.
import { useEffect, useState } from 'react';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { getKyc, getVenueLink, type Kyc, type VenueLink } from './api';
import { useTrading } from './store';
import { VENUE_LABEL, type Venue } from './venue';

type Step = { done: boolean | null; fa: string; en: string; noteFa?: string | null; noteEn?: string | null; action?: { fa: string; en: string; run(): void } };

export function LiveNeeds({ venue, onSignIn, onPaper, compact = false }: { venue: Venue | null; onSignIn(): void; onPaper?(): void; compact?: boolean }) {
  const L = useL();
  const open = usePage((s) => s.open);
  const signed = useSession((s) => (venue ? Boolean(s.sessions[venue]) : false));
  const book = useTrading((s) => (venue ? s.books[venue] : null));
  const refresh = useTrading((s) => s.refresh);
  const [link, setLink] = useState<VenueLink | null>(null);
  const [kyc, setKyc] = useState<Kyc | null>(null);

  useEffect(() => {
    if (!signed || !venue) return;
    let alive = true;
    if (venue === 'tradeyar') void getVenueLink().then((x) => { if (alive) setLink(x); }).catch(() => undefined);
    void getKyc(venue).then((x) => { if (alive) setKyc(x); }).catch(() => undefined);
    return () => { alive = false; };
  }, [signed, venue, book?.status]);

  if (venue === 'coineprofx') {
    return (
      <div className="tr-needs">
        <p className="fa">{L('فارکس، فلزات و شاخص‌ها در پرو چارت فقط سیگنال‌اند: ابزار معامله‌ی خودکار کوین‌پرو اف‌ایکس خاموش است و سفارش زنده از چارت فرستاده نمی‌شود.', 'Forex, metals and indices are signals only on Pro Chart: CoinePro-FX’s trading expert is off and no live order is sent from the chart.')}</p>
        <div className="ot-actions">
          <button className="btn btn-quiet sm" onClick={() => open('signals')}>{L('سیگنال‌های فارکس', 'Forex signals')}</button>
          {onPaper ? <button className="btn btn-quiet sm" onClick={onPaper}>{L('معامله‌ی آزمایشی', 'Trade on paper')}</button> : null}
        </div>
      </div>
    );
  }
  if (!venue) {
    return (
      <div className="tr-needs">
        <p className="fa">{L('برای این نماد معامله‌ی زنده وجود ندارد: رمزارزهای USDT روی تریدیار و فارکس، فلزات و شاخص‌ها روی کوین‌پرو اف‌ایکس معامله می‌شوند.', 'There is no live venue for this symbol: USDT crypto trades on TradeYar, forex, metals and indices on CoinePro-FX.')}</p>
        {onPaper ? <button className="btn btn-quiet sm" onClick={onPaper}>{L('معامله‌ی آزمایشی', 'Trade on paper')}</button> : null}
      </div>
    );
  }

  const name = VENUE_LABEL[venue];
  const status = book?.status ?? 'signed-out';
  const steps: Step[] = [{
    done: signed, fa: `ورود به ${name.fa}`, en: `Sign in to ${name.en}`,
    action: signed ? undefined : { fa: 'ورود', en: 'Sign in', run: onSignIn },
  }];
  if (venue === 'tradeyar') {
    const keys = link ? link.connected && link.permission !== 'spot' : status === 'live' ? true : status === 'no-keys' ? false : null;
    steps.push({
      done: signed ? keys : null, fa: 'اتصال کلید API فیوچرز LBank', en: 'Connect LBank futures API keys',
      noteFa: status === 'no-keys' ? book?.message : link?.configured && !link.connected ? 'کلیدها ذخیره شده‌اند ولی سرور هنوز تأییدشان نکرده است.' : null,
      noteEn: status === 'no-keys' ? book?.message : link?.configured && !link.connected ? 'The keys are saved but the server has not verified them yet.' : null,
      action: keys ? undefined : { fa: 'اتصال صرافی', en: 'Connections', run: () => open('connections') },
    });
  }
  const kycDone = kyc ? (kyc.status === 'approved' ? true : /pending/.test(kyc.status) ? null : false) : null;
  steps.push({
    done: signed ? kycDone : null, fa: 'احراز هویت (توصیه‌شده)', en: 'Identity verification (recommended)',
    noteFa: kyc && /pending/.test(kyc.status) ? 'در حال بررسی' : null, noteEn: kyc && /pending/.test(kyc.status) ? 'Under review' : null,
    action: signed && kyc && kyc.status !== 'approved' && !/pending/.test(kyc.status) ? { fa: 'احراز هویت', en: 'Verify', run: () => open('verify') } : undefined,
  });

  return (
    <div className={`tr-needs${compact ? ' compact' : ''}`}>
      {status === 'off' ? <p className="tr-warn fa" dir="rtl">{book?.message ?? 'معامله از داخل برنامه موقتاً غیرفعال است.'}</p> : null}
      {status === 'error' ? (
        <p className="tr-warn fa">{book?.message ? <span dir="rtl">{book.message}</span> : L('صرافی پاسخ نداد.', 'The exchange did not answer.')} <button className="link" onClick={() => void refresh(venue)}>{L('دوباره', 'Retry')}</button></p>
      ) : null}
      {status === 'checking' ? <p className="muted fa">{L('در حال بررسی حساب…', 'Checking the account…')}</p> : null}
      <ol className="tr-steps">
        {steps.map((s, i) => (
          <li key={i} className={s.done === true ? 'done' : s.done === false ? 'todo' : 'wait'}>
            <span className="tr-step-mark" aria-hidden>{s.done === true ? <Icon name="ph-check" size="sm" /> : <span className="num">{i + 1}</span>}</span>
            <span className="tr-step-text">
              <span className="fa">{L(s.fa, s.en)}</span>
              {L(s.noteFa ?? '', s.noteEn ?? '') ? <small className="fa" dir="auto">{L(s.noteFa ?? '', s.noteEn ?? '')}</small> : null}
            </span>
            {s.action ? <button className="btn btn-quiet sm" onClick={s.action.run}>{L(s.action.fa, s.action.en)}</button> : null}
          </li>
        ))}
      </ol>
      {onPaper ? <button className="link fa" onClick={onPaper}>{L('فعلاً معامله‌ی آزمایشی', 'Trade on paper for now')}</button> : null}
    </div>
  );
}
