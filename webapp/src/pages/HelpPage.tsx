import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { SHORTCUTS } from '../engine/hotkeys.js';
import { ALL_TOOLS } from '../chart/tools';
import { REGISTRY } from '../engine/indicators.js';
import { State, Tabs, useL, useLoad } from './kit';

// The tutorials: the phone's whole «؟» library (core/help, 270 entries in both languages with
// pictures) — what each timeframe, chart type, indicator, drawing tool and feature is, how to use
// it, tips, an example and the usual mistake — searchable, plus the keyboard shortcuts.
interface Bi { fa?: string; en?: string }
interface BiList { fa?: string[]; en?: string[] }
interface Entry { title?: Bi; useCase?: Bi; what?: Bi; how?: BiList; tips?: BiList; example?: Bi; pitfall?: Bi; images?: { file: string; alt?: Bi }[] }
type Group = 'all' | 'timeframes' | 'types' | 'indicators' | 'drawings' | 'features' | 'shortcuts';

const BASE = `${import.meta.env.BASE_URL}generated/help/`;
const TYPES = new Set(['area', 'bars', 'baseline', 'candles', 'columns', 'heikin', 'hlcarea', 'hollow', 'kagi', 'linebreak', 'line', 'pnf', 'renko', 'step', 'volcandles', 'range', 'highlow']);
const TOOLS = new Set(ALL_TOOLS.map((t) => t.id));
const INDICATORS = new Set(Object.keys(REGISTRY).map((k) => k.toLowerCase()));

function groupOf(id: string): Exclude<Group, 'all' | 'shortcuts'> {
  if (/^(M\d+|H\d+|D1|W1|MN)$/.test(id)) return 'timeframes';
  if (TYPES.has(id)) return 'types';
  if (TOOLS.has(id)) return 'drawings';
  if (INDICATORS.has(id.toLowerCase())) return 'indicators';
  return 'features';
}

export default function HelpPage({ param }: { param: string | null }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const [group, setGroup] = useState<Group>('all');
  const [q, setQ] = useState('');
  const [openId, setOpenId] = useState<string | null>(param);
  const lib = useLoad(() => fetch(`${BASE}content.json`).then((r) => { if (!r.ok) throw new Error(String(r.status)); return r.json() as Promise<Record<string, Entry>>; }), []);
  const pick = (b: Bi | undefined) => (b ? (lang === 'fa' ? b.fa || b.en : b.en || b.fa) ?? '' : '');
  const pickList = (b: BiList | undefined) => (b ? (lang === 'fa' ? b.fa?.length ? b.fa : b.en : b.en?.length ? b.en : b.fa) ?? [] : []);

  const rows = useMemo(() => {
    const all = Object.entries(lib.data ?? {});
    const needle = q.trim().toLowerCase();
    return all.filter(([id, e]) => (group === 'all' || groupOf(id) === group)
      && (!needle || id.toLowerCase().includes(needle) || (e.title?.fa ?? '').includes(q.trim()) || (e.title?.en ?? '').toLowerCase().includes(needle) || (e.useCase?.fa ?? '').includes(q.trim())));
  }, [lib.data, group, q]);

  const entry = openId ? lib.data?.[openId] : null;
  const shortcutGroups = useMemo(() => {
    const m = new Map<string, typeof SHORTCUTS>();
    for (const s of SHORTCUTS) m.set(s.group, [...(m.get(s.group) ?? []), s]);
    return [...m.entries()];
  }, []);

  return (
    <div className="page-inner help">
      <Tabs value={group} onChange={(g) => { setGroup(g); setOpenId(null); }} tabs={[
        { id: 'all', label: L('همه', 'All') }, { id: 'timeframes', label: L('تایم‌فریم‌ها', 'Timeframes') }, { id: 'types', label: L('نوع چارت', 'Chart types') },
        { id: 'indicators', label: L('اندیکاتورها', 'Indicators') }, { id: 'drawings', label: L('ابزار ترسیم', 'Drawing tools') }, { id: 'features', label: L('امکانات', 'Features') },
        { id: 'shortcuts', label: L('میانبرهای صفحه‌کلید', 'Keyboard shortcuts') },
      ]} />
      {group === 'shortcuts' ? (
        <div className="cards">
          {shortcutGroups.map(([g, list]) => (
            <section key={g} className="card"><h3 className="fa">{g}</h3>
              <dl className="kv">{list.map((s) => <div key={s.id} className="kv-row"><dt className="fa">{s.label}</dt><dd><kbd className="num">{s.combo}</kbd></dd></div>)}</dl>
            </section>
          ))}
        </div>
      ) : entry && openId ? (
        <article className="help-entry">
          <button className="link-btn" onClick={() => setOpenId(null)}>← {L('بازگشت به فهرست', 'Back to the list')}</button>
          <h2 className="fa">{pick(entry.title)}</h2>
          {entry.useCase ? <p className="lead fa">{pick(entry.useCase)}</p> : null}
          {entry.images?.length ? <div className="help-images">{entry.images.map((im) => <img key={im.file} src={`${BASE}images/${im.file}`} alt={pick(im.alt)} loading="lazy" />)}</div> : null}
          {entry.what ? <><h3 className="fa">{L('چیست', 'What it is')}</h3><p className="fa">{pick(entry.what)}</p></> : null}
          {pickList(entry.how).length ? <><h3 className="fa">{L('چطور استفاده کنیم', 'How to use it')}</h3><ol className="fa">{pickList(entry.how).map((x, i) => <li key={i}>{x}</li>)}</ol></> : null}
          {pickList(entry.tips).length ? <><h3 className="fa">{L('نکته‌ها', 'Tips')}</h3><ul className="fa">{pickList(entry.tips).map((x, i) => <li key={i}>{x}</li>)}</ul></> : null}
          {entry.example ? <><h3 className="fa">{L('مثال', 'Example')}</h3><p className="fa">{pick(entry.example)}</p></> : null}
          {entry.pitfall ? <div className="card warn"><h3 className="fa">{L('اشتباه رایج', 'Common mistake')}</h3><p className="fa">{pick(entry.pitfall)}</p></div> : null}
        </article>
      ) : (
        <>
          <input className="scr-search wide" value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجو در آموزش‌ها', 'Search the tutorials')} dir="auto" />
          <State loading={lib.loading} error={lib.error} onRetry={lib.reload} empty={!rows.length}>
            <div className="cards">
              {rows.map(([id, e]) => (
                <button key={id} className="card help-card" onClick={() => setOpenId(id)}>
                  <b className="fa">{pick(e.title)}</b>
                  <span className="muted fa">{pick(e.useCase)}</span>
                </button>
              ))}
            </div>
          </State>
        </>
      )}
    </div>
  );
}
