// TradingView's navigation buttons at the bottom of the price pane (zoom out, zoom in, scroll
// left, scroll right, reset), the «Scroll to the most recent bar» button that appears once the
// reader has scrolled back, and the pane buttons at the top of each pane (move up, move down,
// collapse, maximise, remove) — on mouse-over, always or never, as the settings say.
import { memo, useEffect, useState } from 'react';
import type { IChartApi } from 'lightweight-charts';
import { useChartSettings } from '../state/chartSettings';
import { useStudies } from '../state/studies';
import { usePlacement } from './studyPlacement';
import { useChartBus } from './chartBus';
import type { StudyLegendRow } from './studies';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { resetChartView } from './actions';
import './chart-x.css';

export const NavButtons = memo(function NavButtons({ api, paneHeight }: { api: IChartApi | null; paneHeight: number }) {
  const L = useL();
  const mode = useChartSettings((s) => s.navButtons);
  const rightOffset = useChartSettings((s) => s.rightOffset);
  const [behind, setBehind] = useState(false);
  useEffect(() => {
    if (!api) return;
    const ts = api.timeScale();
    const check = () => {
      const r = ts.getVisibleLogicalRange();
      const n = useChartBus.getState().shown.length;
      setBehind(Boolean(r && n && r.to < n - 2));
    };
    check();
    ts.subscribeVisibleLogicalRangeChange(check);
    return () => ts.unsubscribeVisibleLogicalRangeChange(check);
  }, [api]);
  if (!api || !paneHeight) return null;
  const ts = api.timeScale();
  const zoom = (k: number) => {
    const r = ts.getVisibleLogicalRange();
    if (!r) return;
    const span = (r.to - r.from) * k;
    ts.setVisibleLogicalRange({ from: r.to - span, to: r.to });
  };
  const scroll = (bars: number) => ts.scrollToPosition(ts.scrollPosition() + bars, true);
  const left = api.priceScale('left').width();
  const width = ts.width();
  return (
    <>
      {mode !== 'hidden' ? (
        <div className={`nav-buttons ${mode}`} style={{ left: left + width / 2, top: paneHeight - 46 }} role="toolbar" aria-label={L('ناوبری', 'Navigation')}>
          <button type="button" data-nav="zoom-out" title={L('کوچک‌نمایی', 'Zoom out')} onClick={() => zoom(1.25)}><Icon name="ph-minus" size="sm" /></button>
          <button type="button" data-nav="zoom-in" title={L('بزرگ‌نمایی', 'Zoom in')} onClick={() => zoom(0.8)}><Icon name="ph-plus" size="sm" /></button>
          <button type="button" data-nav="left" title={L('رفتن به چپ', 'Scroll to the left')} onClick={() => scroll(-10)}><Icon name="ph-caret-left" size="sm" /></button>
          <button type="button" data-nav="right" title={L('رفتن به راست', 'Scroll to the right')} onClick={() => scroll(10)}><Icon name="ph-caret-right" size="sm" /></button>
          <button type="button" data-nav="reset" title={L('بازنشانی نمای چارت', 'Reset chart view')} onClick={() => resetChartView(rightOffset)}><Icon name="ph-arrow-counter-clockwise" size="sm" /></button>
        </div>
      ) : null}
      {behind ? (
        <button type="button" className="go-live" style={{ left: left + width - 40, top: paneHeight - 40 }} title={L('رفتن به آخرین میله', 'Scroll to the most recent bar')} aria-label={L('رفتن به آخرین میله', 'Scroll to the most recent bar')}
          onClick={() => ts.scrollToRealTime()}>
          <Icon name="ph-caret-double-right" size="sm" />
        </button>
      ) : null}
    </>
  );
});

/** The buttons at the top right of each pane, when there is more than one. */
export const PaneControls = memo(function PaneControls({ panes, mode, rows, onMaximise, onCollapse }: {
  panes: { top: number; height: number }[]; mode: { max: number | null; collapsed: number[] }; rows: StudyLegendRow[];
  onMaximise(i: number): void; onCollapse(i: number): void;
}) {
  const L = useL();
  const show = useChartSettings((s) => s.paneButtons);
  const api = useChartBus((b) => b.api);
  if (!api || panes.length < 2 || show === 'hidden') return null;
  const right = api.priceScale('left').width() + api.timeScale().width();
  const st = useStudies.getState();
  return (
    <>
      {panes.map((p, i) => {
        if (mode.max != null && mode.max !== i) return null;
        const inPane = rows.filter((r) => r.pane === i);
        const own = i > 0 && inPane.length > 0;
        return (
          <div key={i} className={`pane-btns ${show}`} style={{ top: p.top + 4, left: right - 8 }} data-pane={i} role="toolbar" aria-label={L('پنل', 'Pane')}>
            {own ? (
              <>
                <button type="button" data-pane-act="up" title={L('یک پنل بالاتر', 'Move pane up')} disabled={i <= 1} onClick={() => inPane.forEach((r) => st.move(r.uid, -1))}><Icon name="ph-arrow-up" size="sm" /></button>
                <button type="button" data-pane-act="down" title={L('یک پنل پایین‌تر', 'Move pane down')} disabled={i >= panes.length - 1} onClick={() => inPane.forEach((r) => st.move(r.uid, 1))}><Icon name="ph-arrow-down" size="sm" /></button>
              </>
            ) : null}
            <button type="button" data-pane-act="collapse" title={mode.collapsed.includes(i) ? L('باز کردن پنل', 'Restore pane') : L('جمع کردن پنل', 'Collapse pane')} onClick={() => onCollapse(i)}>
              <Icon name={mode.collapsed.includes(i) ? 'ph-arrows-out-line-vertical' : 'ph-arrows-in-line-vertical'} size="sm" />
            </button>
            <button type="button" data-pane-act="max" title={mode.max === i ? L('بازگرداندن پنل', 'Restore pane') : L('بزرگ کردن پنل', 'Maximize pane')} onClick={() => onMaximise(i)}>
              <Icon name={mode.max === i ? 'ph-corners-in' : 'ph-corners-out'} size="sm" />
            </button>
            {own ? (
              <button type="button" data-pane-act="remove" title={L('حذف پنل', 'Delete pane')} onClick={() => inPane.forEach((r) => { st.remove(r.uid); usePlacement.getState().forget(r.uid); })}><Icon name="ph-trash" size="sm" /></button>
            ) : null}
          </div>
        );
      })}
    </>
  );
});
