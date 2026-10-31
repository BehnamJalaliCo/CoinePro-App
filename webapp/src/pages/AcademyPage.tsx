import { useMemo, useState } from 'react';
import { usePage } from '../state/page';
import { useSession } from '../data/session';
import { academy } from '../data/academy';
import { num, str } from '../data/member';
import { HttpError } from '../data/http';
import { State, Tabs, useL, useLoad, parseNum } from './kit';

// The academy (feature/academy, CoinePro-FX): the levels and their lessons with what is done and
// what is locked, the roadmap of a level by module, the reader's progress, streak and badges, the
// leaderboard and the glossary.
type Tab = 'lessons' | 'roadmap' | 'progress' | 'leaderboard' | 'glossary';
interface Lesson { slug: string; title: string; order: number; locked: boolean; lockReason: string; completed: boolean; video: boolean }

export default function AcademyPage() {
  const L = useL();
  const open = usePage((s) => s.open);
  const fx = useSession((s) => Boolean(s.sessions.coineprofx));
  const [tab, setTab] = useState<Tab>('lessons');
  const [level, setLevel] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const catalog = useLoad(() => academy<{ tier?: string; levels?: { key: string; name: string; lessons?: Record<string, unknown>[] }[] }>('catalog'), [fx]);
  const me = useLoad(() => academy<Record<string, unknown>>('me'), [fx]);
  const levels = (catalog.data?.levels ?? []).map((l) => ({ key: l.key, name: l.name, lessons: (l.lessons ?? []).map((x): Lesson => ({ slug: str(x.slug), title: str(x.title), order: num(x.order) ?? 0, locked: Boolean(x.locked), lockReason: str(x.lock_reason), completed: Boolean(x.completed), video: Boolean(x.has_video) })).sort((a, b) => a.order - b.order) }));
  const current = level ?? levels[0]?.key ?? null;
  const roadmap = useLoad(() => (tab === 'roadmap' && current ? academy<{ modules?: { index: number; title: string; lessons?: Record<string, unknown>[]; done?: number; total?: number }[] }>(`lessons/${encodeURIComponent(current)}/roadmap`) : Promise.resolve(null)), [tab, current]);
  const extra = useLoad(async () => {
    if (tab === 'progress') return { streak: await academy<Record<string, unknown>>('streak').catch(() => null), ach: await academy<{ items?: Record<string, unknown>[] }>('achievements').catch(() => null) };
    if (tab === 'leaderboard') return { board: await academy<{ items?: Record<string, unknown>[]; my_rank?: number; total_students?: number }>('leaderboard') };
    if (tab === 'glossary') return { glossary: await academy<{ terms?: { term: string; def: string; cat?: string }[] }>('glossary') };
    return null;
  }, [tab]);
  const terms = useMemo(() => ((extra.data as { glossary?: { terms?: { term: string; def: string; cat?: string }[] } } | null)?.glossary?.terms ?? []).filter((x) => !q || x.term.includes(q) || x.def.includes(q)), [extra.data, q]);

  if (!fx) return <div className="empty fa">{L('آکادمی با حساب کوین‌پرو اف‌ایکس است؛ با همان ایمیل وارد شوید.', 'The academy is on CoinePro-FX; sign in with that email.')}</div>;
  const m = me.data;
  const lessonRow = (x: Lesson) => (
    <button key={x.slug} className={`lesson-row${x.completed ? ' done' : ''}${x.locked ? ' locked' : ''}`} onClick={() => open('lesson', x.slug)}>
      <span className="num">{x.completed ? '✓' : x.locked ? '🔒' : x.order}</span><span className="fa">{x.title}</span>{x.video ? <span className="muted small">▶</span> : null}
      {x.locked ? <span className="muted small fa">{x.lockReason === 'phone' ? L('نیاز به شماره‌ی تلفن', 'Needs a phone number') : L('نیاز به سطح بالاتر', 'Needs a higher tier')}</span> : null}
    </button>
  );

  return (
    <div className="page-inner">
      {m && m.phone_required === true ? <PhoneNeeded onDone={() => { me.reload(); catalog.reload(); }} /> : null}
      <State loading={me.loading} error={me.error} onRetry={me.reload}>
        {m ? (
          <div className="pulse">
            <div className="card"><span className="muted fa">{L('پیشرفت', 'Progress')}</span><b className="num">{num(m.completed) ?? 0} / {num(m.total_lessons) ?? 0} · {Math.round(num(m.progress_pct) ?? 0)}%</b></div>
            <div className="card"><span className="muted fa">XP</span><b className="num">{num(m.xp) ?? 0}</b></div>
            <div className="card"><span className="muted fa">{L('آزمون‌ها', 'Quizzes')}</span><b className="num">{num(m.quizzes_taken) ?? 0} · {Math.round(num(m.avg_quiz) ?? 0)}%</b></div>
            <div className="card"><span className="muted fa">{L('سطح عضویت', 'Tier')}</span><b>{str(m.tier) || '—'}</b></div>
          </div>
        ) : null}
      </State>
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'lessons', label: L('درس‌ها', 'Lessons') }, { id: 'roadmap', label: L('نقشه‌ی راه', 'Roadmap') }, { id: 'progress', label: L('پیشرفت و نشان‌ها', 'Progress and badges') }, { id: 'leaderboard', label: L('جدول برترین‌ها', 'Leaderboard') }, { id: 'glossary', label: L('واژه‌نامه', 'Glossary') }]} />
      {tab === 'lessons' || tab === 'roadmap' ? <Tabs value={current ?? ''} onChange={setLevel} tabs={levels.map((l) => ({ id: l.key, label: l.name }))} /> : null}
      {tab === 'lessons' ? (
        <State loading={catalog.loading} error={catalog.error} onRetry={catalog.reload}>
          <div className="lesson-list">{(levels.find((l) => l.key === current)?.lessons ?? []).map(lessonRow)}</div>
        </State>
      ) : null}
      {tab === 'roadmap' ? (
        <State loading={roadmap.loading} error={roadmap.error} onRetry={roadmap.reload}>
          {(roadmap.data?.modules ?? []).map((mod) => (
            <section key={mod.index} className="card"><h3 className="fa">{mod.title} <span className="muted num">{mod.done ?? 0}/{mod.total ?? 0}</span></h3>
              <div className="lesson-list">{(mod.lessons ?? []).map((x) => lessonRow({ slug: str(x.slug), title: str(x.title), order: num(x.order) ?? 0, locked: Boolean(x.locked), lockReason: str(x.lock_reason), completed: Boolean(x.completed), video: Boolean(x.has_video) }))}</div>
            </section>
          ))}
        </State>
      ) : null}
      {tab === 'progress' ? (
        <State loading={extra.loading} error={extra.error} onRetry={extra.reload}>
          {(() => {
            const d = extra.data as { streak?: Record<string, unknown> | null; ach?: { items?: Record<string, unknown>[] } | null } | null;
            return (
              <>
                {d?.streak ? <div className="pulse"><div className="card"><span className="muted fa">{L('روزهای پیاپی', 'Current streak')}</span><b className="num">🔥 {num(d.streak.current) ?? 0}</b></div><div className="card"><span className="muted fa">{L('بیشترین', 'Longest')}</span><b className="num">{num(d.streak.longest) ?? 0}</b></div><div className="card"><span className="muted fa">{L('امروز', 'Today')}</span><b>{d.streak.today_done ? '✓' : '—'}</b></div></div> : null}
                <div className="cards">{(d?.ach?.items ?? []).map((a) => <div key={str(a.badge)} className={`card badge${a.earned ? '' : ' muted'}`}><b>{str(a.icon)} <span className="fa">{str(a.title)}</span></b><span className="fa small">{str(a.desc)}</span></div>)}</div>
              </>
            );
          })()}
        </State>
      ) : null}
      {tab === 'leaderboard' ? (
        <State loading={extra.loading} error={extra.error} onRetry={extra.reload}>
          {(() => {
            const b = (extra.data as { board?: { items?: Record<string, unknown>[]; my_rank?: number; total_students?: number } } | null)?.board;
            return (
              <>
                {b?.my_rank ? <p className="fa">{L('رتبه‌ی شما', 'Your rank')}: <b className="num">{b.my_rank}</b> / <span className="num">{b.total_students ?? '—'}</span></p> : null}
                <table className="table"><tbody>{(b?.items ?? []).map((x) => <tr key={str(x.rank) + str(x.username)} className={x.is_me ? 'me' : ''}><td className="num">{str(x.rank)}</td><td dir="auto">{str(x.username)}</td><td className="r num">{str(x.xp)} XP</td><td className="r num">{str(x.completed)}</td></tr>)}</tbody></table>
              </>
            );
          })()}
        </State>
      ) : null}
      {tab === 'glossary' ? (
        <>
          <input className="scr-search wide" value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجوی واژه', 'Search a term')} dir="auto" />
          <State loading={extra.loading} error={extra.error} onRetry={extra.reload} empty={!terms.length}>
            <dl className="glossary">{terms.map((x) => <div key={x.term}><dt className="fa" dir="auto">{x.term}</dt><dd className="fa" dir="auto">{x.def}</dd></div>)}</dl>
          </State>
        </>
      ) : null}
    </div>
  );
}

/**
 * «شماره‌ی موبایل لازم است» (OLD AcademyScreen PhoneNeeded): the professional levels open once the
 * academy profile has a mobile number, which the reader adds here (POST academy/profile {phone});
 * the server normalises it and says so in its own words when it cannot.
 */
function PhoneNeeded({ onDone }: { onDone(): void }) {
  const L = useL();
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const raw = phone.trim().replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0)).replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
    if (raw.replace(/\D/g, '').length < 8 || parseNum(raw.replace(/[+\s-]/g, '')) == null) { setMsg(L('شماره‌ی موبایلِ معتبر وارد کنید.', 'Enter a valid mobile number.')); return; }
    setBusy(true); setMsg(null);
    try {
      const r = await academy<{ ok?: boolean; phone_required?: boolean }>('profile', { body: { phone: raw } });
      if (r?.phone_required === false || r?.ok) onDone();
    } catch (err) {
      setMsg(err instanceof HttpError && err.readerMessage ? err.readerMessage : L('شماره ثبت نشد. دوباره تلاش کنید.', 'The number was not saved. Try again.'));
    } finally { setBusy(false); }
  };
  return (
    <section className="card academy-phone" data-card="phone">
      <h3 className="fa warn-ink">{L('شماره‌ی موبایل لازم است', 'Phone number required')}</h3>
      <p className="muted small fa">{L('سطح‌های حرفه‌ای وقتی باز می‌شوند که شماره‌ی موبایلت در پروفایل ثبت شده باشد.', 'Professional levels open once your phone number is on your profile.')}</p>
      <form className="row" onSubmit={(e) => void submit(e)}>
        <input type="tel" dir="ltr" inputMode="tel" autoComplete="tel" className="num" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="09xx xxx xxxx" aria-label={L('شماره‌ی موبایل', 'Mobile number')} disabled={busy} />
        <button type="submit" className="btn btn-quiet sm" disabled={busy || !phone.trim()}>{L('ثبت شماره‌ی موبایل', 'Add phone number')}</button>
      </form>
      {msg ? <p className="down small fa" role="alert">{msg}</p> : null}
    </section>
  );
}
