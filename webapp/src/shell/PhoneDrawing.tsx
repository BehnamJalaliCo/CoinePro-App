// The phone's drawing mode (OLD feature/chart FloatingDrawingToolbar.kt and ChartChrome.kt
// ChartDrawingBand, TradingView's phone app in the owner's recording).
//
// The pencil puts the page into drawing mode instead of opening a sheet of tools over the chart
// being drawn on. Two things come up:
//   * the floating drawing toolbar over the left of the plot, one glyph per group of tools, the
//     armed one in TradingView's blue: the grip at its head drags it anywhere over the plot, a tap
//     on the grip folds it to the grip and the armed tool with a spring (and back), a tap on a
//     glyph arms the group's last-used tool and a long press opens the group's tools beside it;
//   * the drawing band in place of the time bar, with what a hand reaches for between strokes:
//     leave drawing mode, the current tool (the full palette), the magnet, the lock, the eye, the
//     object tree and full screen.
// The placement cursor itself (a dot moved like a trackpad) is the chart's: chart/PlacementCursor.ts.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { create } from 'zustand';
import { Icon } from '../ui/Icon';
import { TOOL_GROUPS, toolIcon, toolName, type ToolGroup } from '../chart/tools';
import { useDrawing } from '../state/drawing';
import { usePanels } from '../state/panels';
import { usePrefs } from '../state/prefs';
import { useChartBus } from '../chart/chartBus';
import { PHONE_QUERY } from '../chart/PlacementCursor';
import { useL } from '../pages/kit';
import '../chart/drawalerts.css';

interface PhoneDrawState { on: boolean; set(on: boolean): void; toggle(): void }
/** Whether the phone is in drawing mode (the pencil in the top bar). */
export const usePhoneDraw = create<PhoneDrawState>((set, get) => ({
  on: false,
  set: (on) => set({ on }),
  toggle: () => set({ on: !get().on }),
}));

/** Leaves drawing mode: the tool goes back to the cursor, the palette closes. */
export function leaveDrawMode(): void {
  usePhoneDraw.getState().set(false);
  useDrawing.getState().resetTool();
  document.body.classList.remove('rail-open');
}

function usePhone(): boolean {
  const [phone, setPhone] = useState(() => { try { return window.matchMedia(PHONE_QUERY).matches; } catch { return false; } });
  useEffect(() => {
    let mq: MediaQueryList;
    try { mq = window.matchMedia(PHONE_QUERY); } catch { return; }
    const on = () => setPhone(mq.matches);
    mq.addEventListener?.('change', on);
    return () => mq.removeEventListener?.('change', on);
  }, []);
  return phone;
}

const fullscreen = () => {
  if (document.fullscreenElement) void document.exitFullscreen();
  else void document.documentElement.requestFullscreen?.();
};

/** The drawing band and the floating toolbar, on a phone in drawing mode. */
export function PhoneDrawing() {
  const phone = usePhone();
  const on = usePhoneDraw((s) => s.on);
  const active = phone && on;
  useEffect(() => { document.body.classList.toggle('draw-mode', active); return () => document.body.classList.remove('draw-mode'); }, [active]);
  // A phone that becomes a desktop leaves drawing mode.
  useEffect(() => { if (!phone && on) usePhoneDraw.getState().set(false); }, [phone, on]);
  if (!active) return null;
  return (
    <>
      <DrawingBand />
      <FloatingToolbar />
    </>
  );
}

function DrawingBand() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const st = useDrawing();
  const n = useChartBus((s) => s.drawingCount);
  const armed = TOOL_GROUPS.some((g) => g.id !== 'cursors' && g.tools.some((t) => t.id === st.tool));
  const shownTool = armed ? st.tool : Object.entries(st.lastInGroup).find(([g]) => g !== 'cursors')?.[1] ?? 'trend';
  const objects = usePanels((s) => s.open === 'objects');
  return (
    <div className="draw-band" role="toolbar" aria-label={L('ابزارهای حالت ترسیم', 'Drawing mode')} data-band>
      <button className="db-btn on" data-act="exit" title={L('خروج از حالت ترسیم', 'Leave drawing mode')} aria-label={L('خروج از حالت ترسیم', 'Leave drawing mode')} onClick={leaveDrawMode}><Icon name="pencil" /></button>
      <span className="db-sep" />
      <button className={`db-btn${armed ? ' on' : ''}`} data-act="tools" title={toolName(shownTool, lang)} aria-label={toolName(shownTool, lang)} onClick={() => document.body.classList.toggle('rail-open')}><Icon name={toolIcon(shownTool)} /></button>
      <button className={`db-btn${st.magnet ? ' on' : ''}`} data-act="magnet" aria-pressed={st.magnet} title={L('آهنربا', 'Magnet')} aria-label={L('آهنربا', 'Magnet')} onClick={() => st.setMagnet(!st.magnet)}>
        <Icon name={st.magnet && st.magnetMode === 'weak' ? 'ph-magnet-straight' : 'magnet'} />
      </button>
      <button className={`db-btn${st.locked ? ' on' : ''}`} data-act="lock" aria-pressed={st.locked} title={L('قفل همه‌ی ترسیم‌ها', 'Lock all drawings')} aria-label={L('قفل همه‌ی ترسیم‌ها', 'Lock all drawings')} onClick={() => st.setLocked(!st.locked)}><Icon name={st.locked ? 'lock' : 'unlock'} /></button>
      <button className={`db-btn${st.hidden ? ' on' : ''}`} data-act="eye" aria-pressed={st.hidden} title={L('نمایش یا پنهان‌کردن همه‌ی ترسیم‌ها', 'Show or hide every drawing')} aria-label={L('نمایش یا پنهان‌کردن همه‌ی ترسیم‌ها', 'Show or hide every drawing')} onClick={() => st.setHidden(!st.hidden)}><Icon name={st.hidden ? 'eye_off' : 'ph-eye'} /></button>
      <button className={`db-btn${n > 0 || objects ? ' on' : ''}`} data-act="objects" title={L('درخت ترسیم‌ها', 'Drawing tree')} aria-label={L('درخت ترسیم‌ها', 'Drawing tree')} onClick={() => usePanels.getState().toggle('objects')}>
        <Icon name="ph-stack" />{n > 0 ? <span className="db-badge num">{Math.min(n, 99)}</span> : null}
      </button>
      <span className="db-sep" />
      <button className="db-btn" data-act="fullscreen" title={L('تمام‌صفحه', 'Full screen')} aria-label={L('تمام‌صفحه', 'Full screen')} onClick={fullscreen}><Icon name="maximize2" /></button>
    </div>
  );
}

const EDGE = 8;
const TOP = 72;
const HOLD_MS = 450;
const SLOP = 6;

/** The groups the floating toolbar shows: every drawing group (the cursors are the band's business). */
export const FLOAT_GROUPS: ToolGroup[] = TOOL_GROUPS.filter((g) => g.id !== 'cursors');

function FloatingToolbar() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const st = useDrawing();
  const ref = useRef<HTMLDivElement>(null);
  const [bounds, setBounds] = useState<DOMRect | null>(null);
  const [pos, setPos] = useState({ x: 0, y: TOP });
  const [collapsed, setCollapsed] = useState(false);
  const [fly, setFly] = useState<{ group: string; top: number } | null>(null);
  const grip = useRef<{ x: number; y: number; dragging: boolean; id: number } | null>(null);
  const hold = useRef<{ timer: ReturnType<typeof setTimeout>; fired: boolean } | null>(null);

  // The plot it floats over: the chart grid's box, kept current.
  useLayoutEffect(() => {
    const grid = document.querySelector('.chart-grid') as HTMLElement | null;
    const read = () => setBounds(grid?.getBoundingClientRect() ?? null);
    read();
    const ro = grid ? new ResizeObserver(read) : null;
    if (grid) ro!.observe(grid);
    window.addEventListener('resize', read);
    return () => { ro?.disconnect(); window.removeEventListener('resize', read); };
  }, []);
  // A press outside an open group closes it.
  useEffect(() => {
    if (!fly) return;
    const close = (e: PointerEvent) => { if (!(e.target as HTMLElement).closest?.('.dft-fly, .dft')) setFly(null); };
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [fly]);

  if (!bounds) return null;
  const w = ref.current?.offsetWidth ?? 48, h = ref.current?.offsetHeight ?? 60;
  const clamp = (p: { x: number; y: number }) => ({
    x: Math.max(0, Math.min(bounds.width - w - EDGE * 2, p.x)),
    y: Math.max(0, Math.min(bounds.height - Math.min(h, 24) - EDGE * 2, p.y)),
  });
  const at = clamp(pos);
  const groupTool = (g: ToolGroup) => {
    const remembered = st.lastInGroup[g.id];
    return g.tools.find((t) => t.id === remembered) ?? g.tools[0]!;
  };
  const armedGroup = FLOAT_GROUPS.find((g) => g.tools.some((t) => t.id === st.tool));
  const arm = (id: string, group: string) => { setFly(null); if (st.tool === id) st.resetTool(); else st.setTool(id, group); };

  const press = (g: ToolGroup, el: HTMLElement) => {
    if (hold.current) clearTimeout(hold.current.timer);
    const h0 = { fired: false, timer: setTimeout(() => { h0.fired = true; const r = el.getBoundingClientRect(); setFly({ group: g.id, top: r.top }); }, HOLD_MS) };
    hold.current = h0;
  };
  const release = (g: ToolGroup) => {
    const h0 = hold.current; hold.current = null;
    if (!h0) return;
    clearTimeout(h0.timer);
    if (h0.fired) return;
    const active = armedGroup?.id === g.id;
    if (active) st.resetTool(); else arm(groupTool(g).id, g.id);
  };
  const cancelHold = () => { if (hold.current) { clearTimeout(hold.current.timer); hold.current = null; } };

  const flyGroup = fly ? FLOAT_GROUPS.find((g) => g.id === fly.group) : null;
  const left = bounds.left + EDGE + at.x;
  return createPortal(
    <>
      <div ref={ref} className={`dft${collapsed ? ' folded' : ''}`} dir="ltr" role="toolbar" aria-label={L('ابزارهای ترسیم', 'Drawing tools')}
        style={{ left, top: bounds.top + EDGE + at.y, maxHeight: bounds.height - EDGE * 2 }} data-floating-tools>
        <div className="dft-grip" role="button" tabIndex={0} aria-label={collapsed ? L('باز کردن نوار ابزار', 'Unfold the toolbar') : L('جمع کردن نوار ابزار', 'Fold the toolbar')} aria-expanded={!collapsed}
          onPointerDown={(e) => { try { (e.target as HTMLElement).setPointerCapture?.(e.pointerId); } catch { /* a pointer the browser does not track */ } grip.current = { x: e.clientX, y: e.clientY, dragging: false, id: e.pointerId }; }}
          onPointerMove={(e) => {
            const g = grip.current; if (!g) return;
            const dx = e.clientX - g.x, dy = e.clientY - g.y;
            if (!g.dragging && Math.hypot(dx, dy) < SLOP) return;
            g.dragging = true; g.x = e.clientX; g.y = e.clientY;
            setPos((p) => clamp({ x: clamp(p).x + dx, y: clamp(p).y + dy }));
          }}
          onPointerUp={() => { const g = grip.current; grip.current = null; if (g && !g.dragging) { setCollapsed((c) => !c); setFly(null); } }}
          onPointerCancel={() => { grip.current = null; }}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setCollapsed((c) => !c); } }}>
          <span className="dft-dots" aria-hidden="true">{Array.from({ length: 6 }, (_, i) => <i key={i} />)}</span>
        </div>
        <div className="dft-body">
          <div className="dft-inner" key={collapsed ? 'folded' : 'open'}>
            {collapsed ? (
              armedGroup ? (
                <button className="dft-tool on" data-tool={st.tool} title={toolName(st.tool, lang)} aria-label={toolName(st.tool, lang)} aria-pressed onClick={() => st.resetTool()}>
                  <span className="dft-plate"><Icon name={toolIcon(st.tool)} /></span>
                </button>
              ) : null
            ) : (
              <>
                {FLOAT_GROUPS.map((g) => {
                  const active = armedGroup?.id === g.id;
                  const shown = active ? st.tool : groupTool(g).id;
                  return (
                    <button key={g.id} className={`dft-tool${active ? ' on' : ''}${g.tools.length > 1 ? ' many' : ''}`} data-group={g.id} data-tool={shown}
                      title={L(g.fa, g.en)} aria-label={L(g.fa, g.en)} aria-pressed={active} aria-haspopup="menu" aria-expanded={fly?.group === g.id}
                      onPointerDown={(e) => press(g, e.currentTarget)} onPointerUp={() => release(g)} onPointerLeave={cancelHold} onPointerCancel={cancelHold}
                      onContextMenu={(e) => e.preventDefault()}
                      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (active) st.resetTool(); else arm(groupTool(g).id, g.id); } }}>
                      <span className="dft-plate"><Icon name={toolIcon(shown)} /></span>
                    </button>
                  );
                })}
                <button className="dft-tool" data-act="all" title={L('همه‌ی ابزارها', 'Every tool')} aria-label={L('همه‌ی ابزارها', 'Every tool')} onClick={() => document.body.classList.toggle('rail-open')}>
                  <span className="dft-plate"><Icon name="layout_grid" /></span>
                </button>
              </>
            )}
          </div>
        </div>
      </div>
      {flyGroup && fly ? (
        <div className="dft-fly" role="menu" dir={lang === 'fa' ? 'rtl' : 'ltr'} data-fly={flyGroup.id}
          style={{ left: left + w + 6, top: Math.max(8, Math.min(fly.top, window.innerHeight - 8 - Math.min(440, 40 + flyGroup.tools.length * 44))) }}>
          <div className="dft-fly-title">{L(flyGroup.fa, flyGroup.en)}</div>
          {flyGroup.tools.map((t) => (
            <button key={t.id} role="menuitem" className={`dft-fly-row${st.tool === t.id ? ' on' : ''}`} data-tool={t.id} onClick={() => arm(t.id, flyGroup.id)}>
              <Icon name={toolIcon(t.id)} /><span className={lang === 'fa' ? 'fa' : ''}>{toolName(t.id, lang)}</span>
            </button>
          ))}
        </div>
      ) : null}
    </>,
    document.body,
  );
}
