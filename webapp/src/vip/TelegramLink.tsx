import { useEffect, useRef, useState } from 'react';
import { request } from '../data/http';
import { authed } from '../data/auth';
import { useSession } from '../data/session';
import { useT } from '../state/prefs';
import { Icon } from '../ui/Icon';
import { errorText, useL } from '../pages/kit';
import { fxSession } from './forexAuth';
import { useVip } from './store';

// Links the reader's Telegram to the CoinePro-FX account (POST user/auth/link/telegram, the Login
// Widget's signed payload), so the forex channel invite, the bot's messages and the channel itself
// know who this account is. The widget is Telegram's own script, loaded only when asked for; it
// answers on this site only once the bot's domain is set to it in BotFather.
interface TgUser { id: number; first_name?: string; last_name?: string; username?: string; photo_url?: string; auth_date: number; hash: string }

export function TelegramLink() {
  const L = useL();
  const t = useT();
  const host = useRef<HTMLDivElement>(null);
  const [on, setOn] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  useEffect(() => {
    if (!on || !host.current) return;
    let alive = true;
    const el = host.current;
    (window as unknown as { pcTelegramAuth?: (u: TgUser) => void }).pcTelegramAuth = (u: TgUser) => {
      void authed<Record<string, unknown>>('coineprofx', '/up/coineprofx/api/user/auth/link/telegram', { body: u })
        .then((r) => {
          if (r.access_token || r.token) useSession.getState().put('coineprofx', fxSession(r));
          setDone(true); void useVip.getState().refresh();
        })
        .catch((e) => setMsg(errorText(e, t)));
    };
    void request<{ bot_username?: string }>('/up/coineprofx/api/public/auth/config').then((c) => {
      if (!alive || !c.bot_username) { setMsg(L('نام ربات تلگرام در دسترس نیست.', 'The Telegram bot is not configured.')); return; }
      const s = document.createElement('script');
      s.async = true;
      s.src = 'https://telegram.org/js/telegram-widget.js?22';
      s.setAttribute('data-telegram-login', c.bot_username.replace(/^@/, ''));
      s.setAttribute('data-size', 'large');
      s.setAttribute('data-request-access', 'write');
      s.setAttribute('data-onauth', 'pcTelegramAuth(user)');
      el.replaceChildren(s);
    }).catch((e) => setMsg(errorText(e, t)));
    return () => { alive = false; };
  }, [on, L, t]);
  if (done) return <p className="up fa">{L('تلگرام به حساب فارکس وصل شد.', 'Telegram is linked to the forex account.')}</p>;
  return (
    <div className="vip-tg stack">
      <p className="muted fa">{L('تلگرام را وصل کنید تا لینک کانال و پیام‌های ربات به همین حساب برسد.', 'Link Telegram so the channel invite and the bot’s messages reach this account.')}</p>
      {on ? <div ref={host} className="vip-tg-widget" /> : <div className="row"><button className="btn btn-quiet sm" onClick={() => setOn(true)}><Icon name="ph-telegram-logo" size="sm" /><span className="fa">{L('اتصال تلگرام', 'Link Telegram')}</span></button></div>}
      {msg ? <p className="down fa">{msg}</p> : null}
    </div>
  );
}
