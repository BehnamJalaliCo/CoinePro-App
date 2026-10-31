// The reader's avatar: their letter, their picture, a coin, a pair's two flags, a metal, or one of
// the app's own marks — inside a ring of one of the app's meaningful colours (OLD core/designsystem
// CoineProAvatar.kt, feature/profile AvatarComposer.kt, AvatarCatalog.kt). Kept in this browser
// only; nothing here is sent anywhere.
import { useState, type ReactElement } from 'react';
import { SymbolLogo, hasLogo } from '../ui/SymbolLogo';
import { useL } from './kit';

export type AvatarRing = 'none' | 'gold' | 'premium' | 'analysis' | 'buy' | 'sell';
export type AvatarMark = 'rocket' | 'bull' | 'bear' | 'candle' | 'diamond' | 'flame' | 'bolt' | 'trend' | 'shield' | 'globe';
export type AvatarBase = { kind: 'initial' } | { kind: 'photo'; src: string } | { kind: 'symbol'; symbol: string } | { kind: 'mark'; mark: AvatarMark } | { kind: 'legacy'; text: string };

export const MARKS: AvatarMark[] = ['rocket', 'bull', 'bear', 'candle', 'diamond', 'flame', 'bolt', 'trend', 'shield', 'globe'];
export const MARK_LABEL: Record<AvatarMark, [string, string]> = {
  rocket: ['موشک', 'Rocket'], bull: ['گاو', 'Bull'], bear: ['خرس', 'Bear'], candle: ['کندل', 'Candle'], diamond: ['الماس', 'Diamond'],
  flame: ['شعله', 'Flame'], bolt: ['صاعقه', 'Bolt'], trend: ['روند', 'Trend'], shield: ['سپر', 'Shield'], globe: ['جهانی', 'Global'],
};
/** The rings a reader may pick: every one but premium, which the app sets and the eye cannot tell from gold. */
export const OFFERED_RINGS: AvatarRing[] = ['none', 'gold', 'analysis', 'buy', 'sell'];
export const CRYPTO_AVATARS = ['BTC', 'ETH', 'SOL', 'BNB', 'XRP', 'DOGE', 'ADA', 'TRX', 'AVAX', 'LINK', 'TON', 'DOT', 'MATIC', 'SHIB', 'LTC', 'UNI', 'ATOM', 'XLM', 'NEAR', 'APT', 'FIL', 'ARB', 'OP', 'INJ', 'SUI', 'PEPE', 'RNDR', 'AAVE', 'SAND', 'GRT'];
export const FOREX_AVATARS = ['EURUSD', 'GBPUSD', 'USDJPY', 'USDCHF', 'AUDUSD', 'USDCAD', 'NZDUSD', 'EURGBP', 'EURJPY', 'GBPJPY', 'AUDJPY', 'CHFJPY', 'EURAUD', 'USDTRY'];
export const METAL_AVATARS = ['XAUUSD', 'XAGUSD', 'XPTUSD', 'XPDUSD'];

/** The stored string: '' the letter, data: a picture, sym:BTC, mark:rocket; anything else an older emoji choice. */
export function decodeAvatar(raw: string): AvatarBase {
  if (!raw) return { kind: 'initial' };
  if (raw.startsWith('data:image/')) return { kind: 'photo', src: raw };
  if (raw.startsWith('sym:') && /^[A-Z0-9]{2,12}$/.test(raw.slice(4))) return { kind: 'symbol', symbol: raw.slice(4) };
  if (raw.startsWith('mark:') && (MARKS as string[]).includes(raw.slice(5))) return { kind: 'mark', mark: raw.slice(5) as AvatarMark };
  if (raw.startsWith('sym:') || raw.startsWith('mark:')) return { kind: 'initial' };
  return { kind: 'legacy', text: raw.slice(0, 4) };
}
export function encodeAvatar(b: AvatarBase): string {
  switch (b.kind) {
    case 'initial': return '';
    case 'photo': return b.src;
    case 'symbol': return `sym:${b.symbol}`;
    case 'mark': return `mark:${b.mark}`;
    case 'legacy': return b.text;
  }
}
export const readRing = (raw: unknown): AvatarRing => (['none', 'gold', 'premium', 'analysis', 'buy', 'sell'].includes(String(raw)) ? (raw as AvatarRing) : 'gold');

/** A coin's ticker as the logo map knows it (BTC → BTCUSDT); a pair or a metal as itself. */
const logoSymbol = (s: string) => (s.length >= 6 && /USD|JPY|CHF|GBP|EUR|TRY|CAD|AUD$/.test(s.slice(3)) ? s : `${s}USDT`);
export const avatarHasArt = (s: string) => hasLogo(logoSymbol(s));

const RING_FRACTION = 0.055;
const RING_GAP = 0.035;
const RING_VAR: Record<AvatarRing, string> = { none: 'transparent', gold: 'var(--gold-strong)', premium: 'var(--premium, #D4AF37)', analysis: 'var(--info)', buy: 'var(--up)', sell: 'var(--down)' };

export function AvatarView({ base, ring, initial, size = 44, label }: { base: AvatarBase; ring: AvatarRing; initial: string; size?: number; label?: string }) {
  const stroke = Math.max(1, size * RING_FRACTION);
  const inset = ring === 'none' ? 0 : stroke + size * RING_GAP;
  const inner = Math.round(size - inset * 2);
  const ink = ring === 'none' ? RING_VAR.gold : RING_VAR[ring];
  let body: ReactElement;
  if (base.kind === 'photo') body = <img src={base.src} alt="" width={inner} height={inner} />;
  else if (base.kind === 'symbol' && avatarHasArt(base.symbol)) body = <SymbolLogo symbol={logoSymbol(base.symbol)} size={inner} />;
  else if (base.kind === 'mark') body = <MarkArt mark={base.mark} ink={ink} size={inner} />;
  else if (base.kind === 'legacy') body = <span className="av-token" style={{ fontSize: inner * 0.5 }}>{base.text}</span>;
  else body = <span className="av-token" style={{ color: ink, fontSize: inner * 0.42, background: `color-mix(in srgb, ${ink} 16%, var(--bg-elevated))` }}>{(initial.trim().charAt(0) || '?').toUpperCase()}</span>;
  return (
    <span className="avatar-v" role={label ? 'img' : undefined} aria-label={label} data-ring={ring}
      style={{ width: size, height: size, padding: inset, border: ring === 'none' ? undefined : `${stroke}px solid ${RING_VAR[ring]}`, boxSizing: 'border-box' }}>
      <span className="av-inner" style={{ width: inner, height: inner }}>{body}</span>
    </span>
  );
}

/** One of the app's own marks, drawn in a 100×100 space; the bull is the buy colour and the bear the sell colour, always. */
export function MarkArt({ mark, ink, size }: { mark: AvatarMark; ink: string; size: number }) {
  const c = mark === 'bull' ? 'var(--up)' : mark === 'bear' ? 'var(--down)' : ink;
  const g = 'var(--bg)';
  const w = 'var(--warn)';
  const art: Record<AvatarMark, ReactElement> = {
    rocket: <><path d="M50 72c-10 6-8 14 0 20c8-6 10-14 0-20z" fill={w} opacity=".8" /><path d="M50 14c14 16 16 38 12 58H38c-4-20-2-42 12-58z" fill={c} /><path d="M38 52L26 74l12-4zM62 52l12 22-12-4z" fill={c} opacity=".72" /><circle cx="50" cy="38" r="8" fill={g} /></>,
    bull: <><path d="M34 44C18 44 8 32 10 18l10 4c0 10 6 14 15 12zM66 44c16 0 26-12 24-26l-10 4c0 10-6 14-15 12z" fill={c} /><path d="M30 34c0-6 40-6 40 0c2 24-6 40-20 52C36 74 28 58 30 34z" fill={c} /><circle cx="41" cy="50" r="3.6" fill={g} /><circle cx="59" cy="50" r="3.6" fill={g} /><circle cx="45" cy="72" r="2.4" fill={g} /><circle cx="55" cy="72" r="2.4" fill={g} /></>,
    bear: <><circle cx="27" cy="33" r="11" fill={c} /><circle cx="73" cy="33" r="11" fill={c} /><ellipse cx="50" cy="58" rx="31" ry="28" fill={c} /><circle cx="40" cy="52" r="3.4" fill={g} /><circle cx="60" cy="52" r="3.4" fill={g} /><ellipse cx="50" cy="71" rx="12" ry="9" fill={g} opacity=".34" /><ellipse cx="50" cy="67.5" rx="5" ry="3.5" fill={g} /></>,
    candle: <><path d="M50 18v20M50 66v14" stroke={c} strokeWidth="4" strokeLinecap="round" /><rect x="36" y="38" width="28" height="28" rx="3" fill={c} /><rect x="16" y="48" width="12" height="22" rx="2.5" fill={c} opacity=".32" /><rect x="72" y="42" width="12" height="20" rx="2.5" fill={c} opacity=".32" /></>,
    diamond: <><path d="M30 32h40l14 16-34 36-34-36z" fill={c} /><path d="M30 32l10 16h20l10-16M16 48h68M40 48l10 36 10-36" stroke={g} strokeWidth="2" fill="none" opacity=".5" /></>,
    flame: <><path d="M50 14c6 14 22 22 22 42a22 22 0 0 1-44 0c0-10 6-16 10-22c2 8 6 10 8 10c-2-12 0-20 4-30z" fill={c} /><path d="M50 52c4 6 10 10 10 18a10 10 0 0 1-20 0c0-6 6-10 10-18z" fill={w} opacity=".8" /></>,
    bolt: <path d="M56 12L26 56h20l-6 32 34-46H54z" fill={c} />,
    trend: <><path d="M16 74l20-18 14 10 30-32" stroke={c} strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" /><path d="M66 32h16v16" stroke={c} strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" /></>,
    shield: <><path d="M50 14l30 10v24c0 18-12 32-30 40C32 80 20 66 20 48V24z" fill={c} /><path d="M38 50l9 9 17-19" stroke={g} strokeWidth="5" fill="none" strokeLinecap="round" strokeLinejoin="round" /></>,
    globe: <><circle cx="50" cy="50" r="32" fill="none" stroke={c} strokeWidth="5" /><ellipse cx="50" cy="50" rx="14" ry="32" fill="none" stroke={c} strokeWidth="4" /><path d="M18 50h64M24 34h52M24 66h52" stroke={c} strokeWidth="3.5" /></>,
  };
  return (
    <svg className="av-mark" width={size} height={size} viewBox="0 0 100 100" aria-hidden style={{ background: `color-mix(in srgb, ${c} 16%, var(--bg-elevated))` }}>
      {art[mark]}
    </svg>
  );
}

type Shelf = 'mine' | 'crypto' | 'forex' | 'metal' | 'mark';
const shelfOf = (b: AvatarBase): Shelf => (b.kind === 'mark' ? 'mark' : b.kind === 'symbol' ? (b.symbol.startsWith('X') && b.symbol.length === 6 ? 'metal' : b.symbol.length === 6 ? 'forex' : 'crypto') : 'mine');

/**
 * Where the reader builds what represents them: the preview is the real avatar from the working
 * choice, and nothing is kept until «ذخیره»; «انصراف» leaves the old one exactly as it was.
 */
export function AvatarComposer({ base, ring, initial, onSave, onCancel, onPickPhoto, photo }: {
  base: AvatarBase; ring: AvatarRing; initial: string; photo: string | null;
  onSave(base: AvatarBase, ring: AvatarRing): void; onCancel(): void; onPickPhoto(): void;
}) {
  const L = useL();
  const [b, setB] = useState<AvatarBase>(base);
  const [r, setR] = useState<AvatarRing>(ring === 'premium' ? 'gold' : ring);
  const [shelf, setShelf] = useState<Shelf>(shelfOf(base));
  const chosen = (x: AvatarBase) => encodeAvatar(x) === encodeAvatar(b);
  const cell = (x: AvatarBase, key: string, label: string, art: ReactElement) => (
    <button key={key} type="button" className={`av-cell${chosen(x) ? ' on' : ''}`} aria-pressed={chosen(x)} aria-label={label} title={label} onClick={() => setB(x)}>{art}</button>
  );
  const symbols = (list: string[]) => list.filter(avatarHasArt).map((s) => cell({ kind: 'symbol', symbol: s }, s, s, <AvatarView base={{ kind: 'symbol', symbol: s }} ring="none" initial="" size={46} />));
  return (
    <div className="av-composer" role="group" aria-label={L('تصویر نیمرخ', 'Profile picture')}>
      <p className="muted small fa">{L('یک تصویر یا یکی از نشان‌های ما را انتخاب کنید', 'Pick a picture, or one of our marks')}</p>
      <div className="av-preview"><AvatarView base={b} ring={r} initial={initial} size={104} /></div>
      <div className="av-rings">
        {OFFERED_RINGS.map((x) => (
          <button key={x} type="button" className={`av-ring${x === r ? ' on' : ''}`} data-ring={x} aria-pressed={x === r} onClick={() => setR(x)} style={x === 'none' ? undefined : { borderColor: x === r ? RING_VAR[x] : undefined }}>
            {x === 'none' ? <span className="fa small">{L('بدون', 'None')}</span> : <span className="av-dot" style={{ background: RING_VAR[x] }} />}
          </button>
        ))}
      </div>
      <nav className="chips page-tabs" role="tablist">
        {([['mine', L('خودم', 'Mine')], ['crypto', L('رمزارز', 'Crypto')], ['forex', L('فارکس', 'Forex')], ['metal', L('فلزات', 'Metals')], ['mark', L('نشان‌ها', 'Marks')]] as const).map(([id, label]) => (
          <button key={id} type="button" role="tab" aria-selected={shelf === id} className={`chip${shelf === id ? ' on' : ''}`} onClick={() => setShelf(id)}><span className="fa">{label}</span></button>
        ))}
      </nav>
      <div className="av-grid">
        {shelf === 'mine' ? (
          <>
            {cell({ kind: 'initial' }, 'initial', L('حرف اول نام', 'Your initial'), <AvatarView base={{ kind: 'initial' }} ring="none" initial={initial} size={46} />)}
            {photo ? cell({ kind: 'photo', src: photo }, 'photo', L('تصویر', 'Picture'), <AvatarView base={{ kind: 'photo', src: photo }} ring="none" initial={initial} size={46} />) : null}
          </>
        ) : shelf === 'crypto' ? symbols(CRYPTO_AVATARS) : shelf === 'forex' ? symbols(FOREX_AVATARS) : shelf === 'metal' ? symbols(METAL_AVATARS)
          : MARKS.map((m) => cell({ kind: 'mark', mark: m }, m, L(...MARK_LABEL[m]), <span className="av-mark-cell"><MarkArt mark={m} ink={RING_VAR[r === 'none' ? 'gold' : r]} size={42} /><small className="fa">{L(...MARK_LABEL[m])}</small></span>))}
      </div>
      {shelf === 'mine' ? (
        <>
          <button type="button" className="btn btn-quiet sm" onClick={onPickPhoto}>{photo ? L('انتخاب تصویر دیگر', 'Choose another picture') : L('انتخاب تصویر از گوشی', 'Choose a picture')}</button>
          <p className="muted small fa">{L('تصویر روی همین گوشی می‌ماند و جایی فرستاده نمی‌شود.', 'The picture stays on this phone and is not sent anywhere.')}</p>
        </>
      ) : null}
      <div className="row">
        <button type="button" className="btn btn-primary sm" onClick={() => onSave(b, r)}>{L('ذخیره', 'Save')}</button>
        <button type="button" className="btn btn-quiet sm" onClick={onCancel}>{L('انصراف', 'Cancel')}</button>
      </div>
    </div>
  );
}
