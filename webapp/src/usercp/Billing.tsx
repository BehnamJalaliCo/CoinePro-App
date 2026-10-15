import { usePage } from '../state/page';
import { PremiumBody } from '../shell/PremiumDialog';
import { State, useL, useLoad } from '../pages/kit';
import { Card, Pill, Row, Section, stamp } from './ui';
import { BACKENDS, entitlement, signedOn } from './sources';
import { planLabel } from './labels';

const NAME = { tradeyar: ['تریدیار · رمزارز', 'TradeYar · crypto'], coineprofx: ['کوین‌پرو اف‌ایکس · فارکس', 'CoinePro-FX · forex'] } as const;

export default function Billing() {
  const L = useL();
  const open = usePage((s) => s.open);
  const ents = useLoad(() => Promise.all(BACKENDS.map(entitlement)), []);
  return (
    <Section title={L('اشتراک و پرداخت', 'Plans and billing')} sub={L('اشتراک فعلی هر پلتفرم، طرح‌های پریمیوم پرو چارت و پرداخت با تتر.', 'Each platform’s current plan, Pro Chart Premium plans and paying with USDT.')}
      actions={<button className="btn btn-quiet sm" onClick={() => open('pro')}>{L('مقایسه‌ی رایگان و پریمیوم', 'Free and Premium compared')}</button>}>
      <Card title={L('اشتراک فعلی', 'Current plan')}>
        <State loading={ents.loading} error={ents.error} onRetry={ents.reload}>
          {BACKENDS.map((b, i) => {
            const e = ents.data?.[i] ?? null;
            return (
              <Row key={b} label={L(NAME[b][0], NAME[b][1])} hint={e?.endsAt ? L(`تا ${stamp(e.endsAt)}`, `Until ${stamp(e.endsAt)}`) : undefined}>
                {!signedOn(b) ? <Pill>{L('وارد نشده', 'Not signed in')}</Pill>
                  : !e ? <Pill>{L('در دسترس نیست', 'Not available')}</Pill>
                    : e.premium ? <Pill tone="gold">{e.plan ? planLabel(e.plan, L) : L('پریمیوم', 'Premium')}</Pill>
                      : <Pill tone="info">{e.all ? L('رایگان · همه‌ی ابزارها باز است', 'Free · every tool open') : L('رایگان', 'Free')}</Pill>}
              </Row>
            );
          })}
        </State>
      </Card>
      <Card title={L('خرید یا تمدید پریمیوم', 'Buy or renew Premium')} sub={L('پرداخت با تتر روی شبکه‌ی اعلام‌شده؛ پس از ثبت هش تراکنش، اشتراک روی حساب تریدیار شما فعال می‌شود.', 'Paid in USDT on the stated network; once the transaction hash is in, the plan runs on your TradeYar account.')}>
        <div className="ucp-premium"><PremiumBody onSignIn={() => open('panel')} /></div>
      </Card>
    </Section>
  );
}
