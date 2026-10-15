import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { ScriptHost } from './ScriptHost';
import { useStudyTables } from '../chart/studies';
import { useStudies } from '../state/studies';
import { useChartBus } from '../chart/chartBus';
import { usePrefs } from '../state/prefs';
import { StudySettings } from './StudySettings';
import { TradeLinesHost } from './TradeLines';
import { PaperLines } from './PaperLines';
import './studies.css';

// The studies area's pieces that live beside the chart rather than in a panel: the NamaScript runner
// and its tables, the trading lines (replay practice, paper trading), and the settings dialog any part of the app can open with
// `window.dispatchEvent(new CustomEvent('pc:study-settings', { detail: { uid } }))`.
// Mounted once in App.
export function StudiesHost() {
  const [settings, setSettings] = useState<string | null>(null);
  useEffect(() => {
    const open = (e: Event) => { const uid = (e as CustomEvent<{ uid?: string }>).detail?.uid; if (uid) setSettings(uid); };
    window.addEventListener('pc:study-settings', open);
    return () => window.removeEventListener('pc:study-settings', open);
  }, []);
  return (
    <>
      <ScriptHost />
      <StudyTables />
      <TradeLinesHost />
      <PaperLines />
      {settings ? <StudySettings uid={settings} onClose={() => setSettings(null)} /> : null}
    </>
  );
}

/** Tables the studies put over the chart (Performance, Seasonality), top right, as TradingView places them. */
function StudyTables() {
  const tables = useStudyTables((s) => s.tables);
  const list = useStudies((s) => s.list);
  const api = useChartBus((s) => s.api);
  const fa = usePrefs((s) => s.lang) === 'fa';
  const host = api?.chartElement().closest('.ws-chart') as HTMLElement | null | undefined;
  const shown = tables.filter((t) => list.some((s) => s.uid === t.uid && !s.hidden));
  if (!host || !shown.length) return null;
  const TITLE: Record<string, string> = { Performance: 'بازدهی', 'Seasonality (avg · this year)': 'فصلی بودن (میانگین · امسال)' };
  return createPortal(
    <div className="st-tables">
      {shown.map((t) => (
        <table key={t.uid} className="ns-table">
          <thead><tr><th colSpan={2} className={fa ? 'fa' : ''}>{fa ? TITLE[t.title] ?? t.title : t.title}</th></tr></thead>
          <tbody>{t.rows.map(([k, v, c]) => <tr key={k}><td className="num">{k}</td><td className="num" style={c ? { color: c } : undefined}>{v}</td></tr>)}</tbody>
        </table>
      ))}
    </div>,
    host,
  );
}
