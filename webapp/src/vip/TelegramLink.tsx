import { useEffect, useRef, useState } from 'react';
import { authed } from '../data/auth';
import { useSession } from '../data/session';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { errorText, useL } from '../pages/kit';
import { fxSession } from './forexAuth';
import { useVip } from './store';

// Links the reader's Telegram to the CoinePro-FX account (POST user/auth/link/telegram, the Login
// Widget's signed payload), so the forex channel invite, the bot's messages and the channel itself
// know who this account is. A bot answers its Login Widget on one domain only, and @CoineProFxBot's
// is coineprofx.com (its own site signs in with it), so the widget runs there: a small window on
// coineprofx.com/tg-link.html hands the signed payload back to this page with postMessage, and only
// that origin and that window are listened to. The server checks Telegram's signature as before.
export const FX_LINK_PAGE = 'https://coineprofx.com/tg-link.html';
const FX_ORIGIN = new URL(FX_LINK_PAGE).origin;

interface TgUser { id: number; first_name?: string; last_name?: string; username?: string; photo_url?: string; auth_date: number; hash: string }

function readTgUser(x: unknown): TgUser | null {
  if (!x || typeof x !== 'object') return null;
  const u = x as Record<string, unknown>;
  if (typeof u.id !== 'number' || typeof u.auth_date !== 'number' || typeof u.hash !== 'string' || !u.hash) return null;
  const out: TgUser = { id: u.id, auth_date: u.auth_date, hash: u.hash };
  for (const k of ['first_name', 'last_name', 'username', 'photo_url'] as const) if (typeof u[k] === 'string') out[k] = u[k] as string;
  return out;
}

export function TelegramLink() {
  const L = useL();
  const t = useT();
  const popup = useRef<Window | null>(null);
  const [phase, setPhase] = useState<'idle' | 'waiting' | 'linking' | 'done'>('idle');
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    if (phase !== 'waiting') return;
    const onMessage = (e: MessageEvent) => {
      if (e.origin !== FX_ORIGIN || !popup.current || e.source !== popup.current) return;
      const d = e.data as { type?: unknown; user?: unknown } | null;
      const user = d?.type === 'pc:telegram-auth' ? readTgUser(d.user) : null;
      if (!user) return;
      setPhase('linking');
      void authed<Record<string, unknown>>('coineprofx', '/up/coineprofx/api/user/auth/link/telegram', { body: user })
        .then((r) => {
          if (r.access_token || r.token) useSession.getState().put('coineprofx', fxSession(r));
          setPhase('done'); void useVip.getState().refresh();
        })
        .catch((err) => { setMsg(errorText(err, t)); setPhase('idle'); });
    };
    window.addEventListener('message', onMessage);
    // Closed without an answer: back to the button.
    const shut = setInterval(() => { if (popup.current?.closed) setPhase((p) => (p === 'waiting' ? 'idle' : p)); }, 800);
    return () => { window.removeEventListener('message', onMessage); clearInterval(shut); };
  }, [phase, t]);

  const start = () => {
    setMsg(null);
    const w = window.open(FX_LINK_PAGE, 'pc-telegram-link', 'popup,width=460,height=640');
    if (!w) { setMsg(L('پنجره‌ی اتصال باز نشد؛ به این سایت اجازه‌ی پنجره‌ی بازشو بدهید.', 'The link window was blocked; allow pop-ups for this site.')); return; }
    popup.current = w;
    setPhase('waiting');
  };

  if (phase === 'done') return <p className="up fa">{L('تلگرام به حساب فارکس وصل شد.', 'Telegram is linked to the forex account.')}</p>;
  return (
    <div className="vip-tg stack">
      <p className="muted fa">{L('تلگرام را وصل کنید تا لینک کانال و پیام‌های ربات به همین حساب برسد.', 'Link Telegram so the channel invite and the bot’s messages reach this account.')}</p>
      <div className="row">
        <button className="btn btn-quiet sm" onClick={start} disabled={phase === 'linking'}>
          <Icon name="ph-telegram-logo" size="sm" />
          <span className="fa">{phase === 'linking' ? L('در حال اتصال…', 'Linking…') : phase === 'waiting' ? L('در پنجره‌ی تلگرام ادامه دهید', 'Continue in the Telegram window') : L('اتصال تلگرام', 'Link Telegram')}</span>
        </button>
      </div>
      {msg ? <p className="down fa" role="alert">{msg}</p> : null}
    </div>
  );
}
