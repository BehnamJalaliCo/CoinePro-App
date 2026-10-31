import { usePage } from '../state/page';
import { useL } from './kit';
import type { PageId } from './registry';
import '../shell/shell.css';

// «بازارها» as one place (app ideas/MarketsHub.kt): the market list, the heat map and the screener
// are three faces of one destination behind one switch. Each keeps its own address, so a link still
// opens exactly the face it names; switching rewrites the address rather than stacking history.
const FACES: { id: PageId; fa: string; en: string }[] = [
  { id: 'markets', fa: 'بازارها', en: 'Markets' },
  { id: 'heatmap', fa: 'هیت‌مپ', en: 'Heat map' },
  { id: 'screener', fa: 'اسکرینر', en: 'Screener' },
];

export function HubSwitch({ current }: { current: 'markets' | 'heatmap' | 'screener' }) {
  const L = useL();
  const open = usePage((s) => s.open);
  return (
    <nav className="hub-switch" role="tablist" aria-label={L('بازارها', 'Markets')}>
      {FACES.map((f) => (
        <button key={f.id} role="tab" aria-selected={f.id === current} className="fa" onClick={() => { if (f.id !== current) open(f.id); }}>
          {L(f.fa, f.en)}
        </button>
      ))}
    </nav>
  );
}
