// Building blocks the VIP pages share: the bot's own sentences rendered safely, the step rail, the
// balance gauge against the server's thresholds, a live countdown, partner links and copy fields.
import { useEffect, useState, type ReactNode } from 'react';
import { usePrefs } from '../state/prefs';
import { faNum } from '../i18n/time';
import { Icon } from '../ui/Icon';
import { useL } from '../pages/kit';
import { gaugeAt, segments, timeLeft, usdt, zoneOf } from './logic';
import './vip.css';

/** A sentence from the bot (Telegram HTML), as text with its bold runs. */
export function BotText({ html, className = '' }: { html: string; className?: string }) {
  if (!html) return null;
  return (
    <p className={`vip-bot fa ${className}`} dir="rtl">
      {segments(html).map((s, i) => (s.bold ? <b key={i}>{s.text}</b> : <span key={i}>{s.text}</span>))}
    </p>
  );
}

export interface RailStep { id: string; label: string; state: 'done' | 'now' | 'todo'; onClick?: () => void }

export function StepRail({ steps, label }: { steps: RailStep[]; label: string }) {
  const lang = usePrefs((s) => s.lang);
  return (
    <ol className="vip-rail" aria-label={label}>
      {steps.map((s, i) => (
        <li key={s.id} className={`vip-rail-step ${s.state}`} aria-current={s.state === 'now' ? 'step' : undefined}>
          <button className="vip-rail-btn" onClick={s.onClick} disabled={!s.onClick}>
            <span className="vip-rail-dot" aria-hidden="true">{s.state === 'done' ? <Icon name="ph-check" size="sm" /> : <span className="num">{lang === 'fa' ? faNum(i + 1) : i + 1}</span>}</span>
            <span className="vip-rail-label fa">{s.label}</span>
          </button>
        </li>
      ))}
    </ol>
  );
}

/** The balance on a bar with the server's three marks: critical, warning, entry. */
export function BalanceGauge({ balance, warn, critical, entry }: { balance: number | null; warn: number | null; critical: number | null; entry: number | null }) {
  const L = useL();
  const zone = zoneOf(balance, warn, critical);
  const top = entry ?? warn;
  const marks = [
    { v: critical, label: L('حذف', 'Removal'), cls: 'crit' },
    { v: warn, label: L('هشدار', 'Warning'), cls: 'warn' },
    { v: entry, label: L('ورود', 'Entry'), cls: 'entry' },
  ].filter((m) => m.v != null);
  return (
    <div className={`vip-gauge ${zone}`} role="img" aria-label={`${L('موجودی', 'Balance')} ${usdt(balance)} USDT`}>
      <div className="vip-gauge-top">
        <span className="muted fa">{L('موجودی حساب صرافی', 'Exchange balance')}</span>
        <b className="num vip-gauge-value">{usdt(balance)} <small>USDT</small></b>
      </div>
      <div className="vip-gauge-bar" dir="ltr">
        <i className="vip-gauge-fill" style={{ width: `${(gaugeAt(balance, top) * 100).toFixed(1)}%` }} />
        {marks.map((m) => (
          <span key={m.cls} className={`vip-gauge-mark ${m.cls}`} style={{ left: `${(gaugeAt(m.v, top) * 100).toFixed(1)}%` }}>
            <span className="vip-gauge-tick" />
            <span className="vip-gauge-label"><span className="fa">{m.label}</span> <span className="num">{usdt(m.v)}</span></span>
          </span>
        ))}
      </div>
    </div>
  );
}

/** «۴۷ ساعت و ۱۲ دقیقه» (Persian prose count) or «47 h 12 min», refreshed every minute. */
export function Countdown({ at }: { at: number | null }) {
  const lang = usePrefs((s) => s.lang);
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 30_000); return () => clearInterval(id); }, []);
  const left = timeLeft(at, now);
  if (!left) return null;
  if (left.over) return <span className="fa">{lang === 'fa' ? 'همین حالا' : 'now'}</span>;
  return lang === 'fa'
    ? <span className="fa">{left.h ? `${faNum(left.h)} ساعت و ` : ''}{faNum(left.m)} دقیقه</span>
    : <span>{left.h ? `${left.h} h ` : ''}{left.m} min</span>;
}

/** An outbound partner link: sponsored, never sends our page as the referrer. */
export function PartnerLink({ href, children, primary = false, onOpen }: { href: string; children: ReactNode; primary?: boolean; onOpen?(): void }) {
  return (
    <a className={`btn sm ${primary ? 'btn-primary' : 'btn-quiet'} vip-partner`} href={href} target="_blank" rel="sponsored nofollow noopener noreferrer" onClick={onOpen}>
      <span className="fa">{children}</span><Icon name="ph-arrow-square-out" size="sm" />
    </a>
  );
}

export function CopyField({ text, label }: { text: string; label: string }) {
  const [done, setDone] = useState(false);
  return (
    <div className="vip-copy" dir="ltr">
      <code className="num">{text}</code>
      <button className="ib" aria-label={label} title={label} onClick={() => { void navigator.clipboard?.writeText(text).then(() => { setDone(true); setTimeout(() => setDone(false), 1500); }); }}>
        <Icon name={done ? 'ph-check' : 'ph-copy'} size="sm" />
      </button>
    </div>
  );
}

/** A status pill: tone follows the state. */
export function Pill({ tone, children }: { tone: 'ok' | 'warn' | 'crit' | 'info' | 'mute'; children: ReactNode }) {
  return <span className={`vip-pill ${tone}`}><span className="fa">{children}</span></span>;
}
