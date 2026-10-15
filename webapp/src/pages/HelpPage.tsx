import { useMemo, useState } from 'react';
import { usePrefs } from '../state/prefs';
import { usePage } from '../state/page';
import { useStudies } from '../state/studies';
import { useDrawing } from '../state/drawing';
import { SHORTCUTS } from '../engine/hotkeys.js';
import { ALL_TOOLS, TOOL_GROUPS } from '../chart/tools';
import { STUDIES } from '../engine/indicators_ext_c.js';
import { State, Tabs, useL, useLoad } from './kit';
import { HELP_ALIASES, helpImage, helpKey, loadHelp, type Bi, type BiList, type HelpEntry } from './helpLib';
import { HelpDiagram, type Diagram } from './HelpDiagram';
import { fold } from '../chart/indicatorCatalog';

// The tutorials: the phone's whole «؟» library (core/help, both languages, pictures) with the web's
// own entries (scripts/help/extra.mjs) and the old Pro-Chart library's encyclopedic sections and
// drawn diagrams — what each timeframe, chart type, indicator, drawing tool and feature is, how to
// use it, tips, an example and the usual mistake — searchable, plus the keyboard shortcuts. Any id
// the app uses opens its entry (helpLib's alias map), and an indicator or a tool can be put on the
// chart from its entry.
type Group = 'all' | 'timeframes' | 'types' | 'indicators' | 'drawings' | 'features' | 'shortcuts';

const TYPES = new Set(['area', 'bars', 'baseline', 'candles', 'columns', 'heikin', 'hlcarea', 'hollow', 'kagi', 'linebreak', 'line', 'pnf', 'renko', 'step', 'volcandles', 'range', 'highlow', 'lwm', 'stepline', 'linemarkers']);
const TOOL_KEYS = new Set<string>([...ALL_TOOLS.map((t) => t.id), ...ALL_TOOLS.map((t) => HELP_ALIASES[t.id]).filter((x): x is string => Boolean(x))].map((x) => x.toLowerCase()));
const STUDY_KEYS = new Set<string>([...Object.keys(STUDIES), ...Object.keys(STUDIES).map((k) => HELP_ALIASES[k]).filter((x): x is string => Boolean(x)), 'volumeprofile', 'svp', 'avolumeprofile', 'volumeprofile_ind', 'tpo', 'footprint', 'techRating', 'prevLevels', 'sar'].map((x) => x.toLowerCase()));
/** Entries the library describes that the web app does not draw yet: marked as such, not hidden. */
const NOT_IN_WEB = new Set(['tpo', 'footprint']);

export function groupOf(id: string, e?: HelpEntry): Exclude<Group, 'all' | 'shortcuts'> {
  if (/^(M\d+|H\d+|D1|W1|MN)$/.test(id)) return 'timeframes';
  if (TYPES.has(id)) return 'types';
  if (e?.group) return e.group;
  const low = id.toLowerCase();
  if (TOOL_KEYS.has(low)) return 'drawings';
  if (STUDY_KEYS.has(low)) return 'indicators';
  return 'features';
}

/** The study key or drawing tool an entry describes, for its "on the chart" button. */
function targetOf(id: string): { study?: string; tool?: { id: string; group: string } } {
  const study = Object.keys(STUDIES).find((k) => k === id || HELP_ALIASES[k] === id || k.toLowerCase() === id.toLowerCase());
  if (study) return { study };
  for (const g of TOOL_GROUPS) for (const t of g.tools) if (t.id === id || HELP_ALIASES[t.id] === id) return { tool: { id: t.id, group: g.id } };
  return {};
}

export default function HelpPage({ param }: { param: string | null }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const [group, setGroup] = useState<Group>(param === 'shortcuts' ? 'shortcuts' : 'all');
  const [q, setQ] = useState('');
  const [openId, setOpenId] = useState<string | null>(param === 'shortcuts' ? null : param);
  const lib = useLoad(() => loadHelp(), []);
  const pick = (b: Bi | undefined) => (b ? (fa ? b.fa || b.en : b.en || b.fa) ?? '' : '');
  const pickList = (b: BiList | undefined) => (b ? (fa ? b.fa?.length ? b.fa : b.en : b.en?.length ? b.en : b.fa) ?? [] : []);

  const rows = useMemo(() => {
    const all = Object.entries(lib.data ?? {});
    const needle = fold(q);
    return all.filter(([id, e]) => (group === 'all' || groupOf(id, e) === group)
      && (!needle || id.toLowerCase().includes(needle) || fold(e.title?.fa ?? '').includes(needle) || fold(e.title?.en ?? '').includes(needle) || fold(e.useCase?.fa ?? '').includes(needle) || fold(e.useCase?.en ?? '').includes(needle)));
  }, [lib.data, group, q]);

  const resolved = lib.data ? helpKey(lib.data, openId) : null;
  const entry = resolved ? lib.data?.[resolved] : null;
  const shortcutGroups = useMemo(() => {
    const m = new Map<string, typeof SHORTCUTS>();
    for (const s of SHORTCUTS) m.set(s.group, [...(m.get(s.group) ?? []), s]);
    return [...m.entries()];
  }, []);
  const target = resolved ? targetOf(openId && targetOf(openId).study ? openId : resolved) : {};
  const toChart = () => {
    if (target.study) useStudies.getState().add(target.study);
    else if (target.tool) useDrawing.getState().setTool(target.tool.id, target.tool.group);
    usePage.getState().close();
  };

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
      ) : entry && resolved ? (
        <article className="help-entry">
          <div className="help-entry-bar">
            <button className="link-btn" onClick={() => setOpenId(null)}>{fa ? '→' : '←'} {L('بازگشت به فهرست', 'Back to the list')}</button>
            {(target.study || target.tool) && !NOT_IN_WEB.has(resolved) ? <button className="btn btn-primary sm" onClick={toChart}><span className="fa">{target.study ? L('افزودن به چارت', 'Add to chart') : L('استفاده از ابزار', 'Use this tool')}</span></button> : null}
          </div>
          <h2 className="fa">{pick(entry.title)}</h2>
          {NOT_IN_WEB.has(resolved) ? <p className="help-note fa">{L('این امکان هنوز در نسخه‌ی وب نیست؛ در اپ موبایل در دسترس است.', 'This is not in the web app yet; the phone app has it.')}</p> : null}
          {entry.useCase ? <p className="lead fa">{pick(entry.useCase)}</p> : null}
          {entry.diagram ? <HelpDiagram diagram={entry.diagram as Diagram} /> : null}
          {entry.images?.length ? <div className="help-images">{entry.images.map((im) => <img key={im.file} src={helpImage(im.file)} alt={pick(im.alt)} loading="lazy" />)}</div> : null}
          {entry.what ? <><h3 className="fa">{L('چیست', 'What it is')}</h3><p className="fa">{pick(entry.what)}</p></> : null}
          {pickList(entry.how).length ? <><h3 className="fa">{L('چطور استفاده کنیم', 'How to use it')}</h3><ol className="fa">{pickList(entry.how).map((x, i) => <li key={i}>{x}</li>)}</ol></> : null}
          {pickList(entry.tips).length ? <><h3 className="fa">{L('نکته‌ها', 'Tips')}</h3><ul className="fa">{pickList(entry.tips).map((x, i) => <li key={i}>{x}</li>)}</ul></> : null}
          {entry.example ? <><h3 className="fa">{L('مثال', 'Example')}</h3><p className="fa">{pick(entry.example)}</p></> : null}
          {entry.pitfall ? <div className="card warn"><h3 className="fa">{L('اشتباه رایج', 'Common mistake')}</h3><p className="fa">{pick(entry.pitfall)}</p></div> : null}
          {fa && entry.sections?.length ? (
            <section className="help-sections">
              <h3 className="fa">{L('دانشنامه', 'In depth')}</h3>
              {entry.sections.map((s, i) => (
                <div key={i} className="help-section">
                  <h4 className="fa">{s.h}</h4>
                  {Array.isArray(s.body) ? <ul className="fa">{s.body.map((x, j) => <li key={j}>{x}</li>)}</ul> : <p className="fa">{s.body}</p>}
                </div>
              ))}
            </section>
          ) : null}
        </article>
      ) : (
        <>
          <input className="scr-search wide" value={q} onChange={(e) => setQ(e.target.value)} placeholder={L('جستجو در آموزش‌ها', 'Search the tutorials')} dir="auto" />
          {openId && lib.data && !resolved ? <p className="muted fa">{L('برای این مورد هنوز راهنمایی نوشته نشده است.', 'There is no entry for this yet.')}</p> : null}
          <State loading={lib.loading} error={lib.error} onRetry={lib.reload} empty={!rows.length}>
            <div className="cards">
              {rows.map(([id, e]) => (
                <button key={id} className="card help-card" onClick={() => setOpenId(id)}>
                  <b className="fa">{pick(e.title)}</b>
                  <span className="muted fa">{pick(e.useCase)}</span>
                  {e.diagram || e.sections?.length ? <span className="help-tags num">{e.diagram ? '◇' : ''}{fa && e.sections?.length ? ' ¶' : ''}</span> : null}
                </button>
              ))}
            </div>
          </State>
        </>
      )}
    </div>
  );
}
