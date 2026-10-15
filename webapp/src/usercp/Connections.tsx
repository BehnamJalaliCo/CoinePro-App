import { usePage } from '../state/page';
import { State, useL, useLoad } from '../pages/kit';
import { Card, Note, Pill, Row, Section } from './ui';
import { BACKENDS, signedOn, venue } from './sources';

const NAME = { tradeyar: ['تریدیار · رمزارز', 'TradeYar · crypto'], coineprofx: ['کوین‌پرو اف‌ایکس · فارکس', 'CoinePro-FX · forex'] } as const;

export default function Connections() {
  const L = useL();
  const open = usePage((s) => s.open);
  const venues = useLoad(() => Promise.all(BACKENDS.map(venue)), []);
  return (
    <Section title={L('اتصال صرافی', 'Exchange connections')} sub={L('وضعیت کلیدهای API صرافی روی هر پلتفرم. کلیدها فقط یک‌بار به سرور فرستاده می‌شوند و در این مرورگر نمی‌مانند.', 'The state of your exchange API keys on each platform. Keys go to the server once and are not kept in this browser.')}
      actions={<button className="btn btn-primary sm" onClick={() => open('connections')}>{L('مدیریت اتصال', 'Manage the connection')}</button>}>
      <State loading={venues.loading} error={venues.error} onRetry={venues.reload}>
        {BACKENDS.map((b, i) => {
          const v = venues.data?.[i] ?? null;
          return (
            <Card key={b} title={`LBank · ${L(NAME[b][0], NAME[b][1])}`}>
              {!signedOn(b) ? <Row label={L('وضعیت', 'Status')}><Pill>{L('وارد نشده', 'Not signed in')}</Pill></Row>
                : !v ? <Row label={L('وضعیت', 'Status')}><Pill>{L('این پلتفرم اتصال صرافی ندارد', 'This platform has no exchange link')}</Pill></Row>
                  : <>
                    <Row label={L('وضعیت', 'Status')} hint={v.status || undefined}>
                      {v.connected ? <Pill tone="good">{L('متصل و تأییدشده', 'Connected and verified')}</Pill> : v.configured ? <Pill tone="warn">{L('ذخیره شده، تأیید نشده', 'Saved, not verified')}</Pill> : <Pill>{L('متصل نیست', 'Not connected')}</Pill>}
                    </Row>
                    {v.permission ? <Row label={L('دسترسی', 'Permission')}><span className="num">{v.permission}</span></Row> : null}
                    {v.keyHint ? <Row label={L('کلید', 'Key')}><span className="num">{v.keyHint}</span></Row> : null}
                  </>}
            </Card>
          );
        })}
      </State>
      <Note tone="info">{L('کلید را بدون دسترسی برداشت بسازید. هر اتصال و قطع اتصال در «معاملات ← ثبت‌شده از چارت وب» هم دیده می‌شود.', 'Create the key without withdrawal permission. Every connect and disconnect also shows under Trading → Sent from the web chart.')}</Note>
    </Section>
  );
}
