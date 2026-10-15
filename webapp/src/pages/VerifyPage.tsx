import { useState } from 'react';
import { useT } from '../state/prefs';
import { member, num, ms, str } from '../data/member';
import { Field, State, dateTime, errorText, useL, useLoad } from './kit';

// Identity verification (feature/kyc, core/account): the level and state the platform holds, and
// the level-one form — name, birth date and phone, with an Iranian national id or, outside Iran,
// a document. Nothing here is kept in the browser.
export default function VerifyPage() {
  const L = useL();
  const t = useT();
  const kyc = useLoad(() => member<Record<string, unknown>>('kyc'), []);
  const [iran, setIran] = useState(true);
  const [f, setF] = useState({ full_name: '', birth_date: '', phone: '', national_id: '', country: '', document_type: 'passport', document_number: '' });
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value });
  const k = kyc.data;
  const status = str(k?.status);
  const label = status === 'approved' ? L('تأیید شده', 'Approved') : status === 'rejected' ? L('رد شده', 'Rejected') : status === 'not_started' || !status ? L('شروع نشده', 'Not started') : L('در حال بررسی', 'Under review');

  const submit = async () => {
    const digits = (s: string) => s.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0));
    if (f.full_name.trim().length < 3 || !f.birth_date || digits(f.phone).replace(/\D/g, '').length < 10) { setMsg(L('نام، تاریخ تولد و شماره را کامل وارد کنید.', 'Enter your name, birth date and phone.')); return; }
    const body: Record<string, string> = { full_name: f.full_name.trim(), birth_date: f.birth_date, phone: digits(f.phone).trim() };
    if (iran) { if (!/^\d{10}$/.test(digits(f.national_id))) { setMsg(L('کد ملی ۱۰ رقم است.', 'The national id has 10 digits.')); return; } body.national_id = digits(f.national_id); }
    else Object.assign(body, { country: f.country.trim(), document_type: f.document_type, document_number: f.document_number.trim() });
    setBusy(true); setMsg(null);
    try { await member('kyc/level1', { body }); kyc.reload(); setMsg(L('فرستاده شد؛ نتیجه پس از بررسی اعلام می‌شود.', 'Sent; you will hear once it is reviewed.')); }
    catch (e) { setMsg(errorText(e, t)); } finally { setBusy(false); }
  };

  return (
    <div className="page-inner narrow">
      <section className="card">
        <State loading={kyc.loading} error={kyc.error} onRetry={kyc.reload}>
          <dl className="kv">
            <dt className="fa">{L('سطح', 'Level')}</dt><dd className="num">{num(k?.level) ?? 0}</dd>
            <dt className="fa">{L('وضعیت', 'Status')}</dt><dd className={status === 'approved' ? 'up' : status === 'rejected' ? 'down' : ''}>{label}</dd>
            {k?.submitted_at ? <><dt className="fa">{L('ارسال', 'Submitted')}</dt><dd className="num">{dateTime(ms(k.submitted_at))}</dd></> : null}
            {k?.reviewed_at ? <><dt className="fa">{L('بررسی', 'Reviewed')}</dt><dd className="num">{dateTime(ms(k.reviewed_at))}</dd></> : null}
            {k?.reason ? <><dt className="fa">{L('دلیل', 'Reason')}</dt><dd className="fa">{str(k.reason)}</dd></> : null}
          </dl>
        </State>
      </section>
      {status !== 'approved' ? (
        <form className="card form" onSubmit={(e) => { e.preventDefault(); void submit(); }}>
          <h3 className="fa">{L('احراز هویت سطح یک', 'Level one verification')}</h3>
          <Field label={L('نام و نام خانوادگی', 'Full name')}><input value={f.full_name} onChange={set('full_name')} dir="auto" autoComplete="name" /></Field>
          <Field label={L('تاریخ تولد', 'Birth date')}><input type="date" value={f.birth_date} onChange={set('birth_date')} /></Field>
          <Field label={L('شماره‌ی موبایل', 'Mobile number')}><input className="num" dir="ltr" value={f.phone} onChange={set('phone')} autoComplete="tel" inputMode="tel" /></Field>
          <label className="check"><input type="checkbox" checked={iran} onChange={(e) => setIran(e.target.checked)} /> <span className="fa">{L('ساکن ایران هستم (کد ملی)', 'I live in Iran (national id)')}</span></label>
          {iran ? <Field label={L('کد ملی', 'National id')}><input className="num" dir="ltr" value={f.national_id} onChange={set('national_id')} inputMode="numeric" /></Field> : (
            <>
              <Field label={L('کشور', 'Country')}><input value={f.country} onChange={set('country')} dir="auto" /></Field>
              <Field label={L('نوع مدرک', 'Document type')}><select value={f.document_type} onChange={set('document_type')}><option value="passport">{L('گذرنامه', 'Passport')}</option><option value="national_id">{L('کارت ملی', 'National id card')}</option><option value="driver_licence">{L('گواهینامه', 'Driving licence')}</option></select></Field>
              <Field label={L('شماره‌ی مدرک', 'Document number')}><input className="num" dir="ltr" value={f.document_number} onChange={set('document_number')} /></Field>
            </>
          )}
          {msg ? <p className="fa">{msg}</p> : null}
          <button className="btn btn-primary" disabled={busy}>{L('ارسال', 'Submit')}</button>
          <p className="muted small fa">{L('این اطلاعات فقط به سرور پلتفرم فرستاده می‌شود و در این مرورگر نمی‌ماند.', 'This goes only to the platform server and is not kept in this browser.')}</p>
        </form>
      ) : null}
    </div>
  );
}
