import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../ui/Icon';
import { CURSOR_TOOLS, EMOJIS, TOOL_GROUPS, toolDef, toolIcon, toolName, groupOf, type ToolDef, type ToolGroup } from '../chart/tools';
import { usePrefs } from '../state/prefs';
import { useDrawing } from '../state/drawing';
import { useStudies } from '../state/studies';
import { usePage } from '../state/page';
import { useChartBus } from '../chart/chartBus';
import { useL } from '../pages/kit';
import { faNum } from '../i18n/time';
import { zoomOut } from '../chart/DrawingZoom';
import { bumpDrawings, indicatorsHidden, removableDrawings, removableStudies, setIndicatorsHidden } from '../chart/DrawingActions';
import '../chart/drawing.css';

// TradingView's drawing palette for the default colour.
export const PALETTE = ['#2962FF', '#F23645', '#089981', '#FF9800', '#9C27B0', '#00BCD4', '#E91E63', '#FFEB3B', '#787B86', '#131722', '#FFFFFF', '#4CAF50'];

type MenuId = string; // a group id, or 'magnet' | 'hide' | 'remove' | 'color'

// The left rail, TradingView's: one button per group showing the group's last-used tool and a
// corner arrow that opens the whole group (on a click, or after a short hover), Measure and Zoom,
// then the magnet, stay-in-drawing, lock, hide and remove switches (with their menus), and our
// favourites, default colour and the button that hides the rail. Menus open in a portal beside
// the rail, so nothing clips them.
export function ToolRail() {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const st = useDrawing();
  const canUndo = useChartBus((s) => s.canUndo);
  const canRedo = useChartBus((s) => s.canRedo);
  const [menu, setMenu] = useState<{ id: MenuId; anchor: HTMLElement } | null>(null);
  const railRef = useRef<HTMLElement>(null);
  const openTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimers = () => {
    if (openTimer.current) { clearTimeout(openTimer.current); openTimer.current = null; }
    if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null; }
  };
  useEffect(() => clearTimers, []);
  // The rail hidden on desktop: the chart takes its column (contract 8).
  useEffect(() => { document.body.classList.toggle('rail-hidden', st.railHidden); }, [st.railHidden]);

  // Closes on an outside press or Escape.
  useEffect(() => {
    if (!menu) return;
    const close = (e: PointerEvent) => {
      const t = e.target as Node;
      if (railRef.current?.contains(t) || (t as HTMLElement).closest?.('.dfly')) return;
      setMenu(null);
    };
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenu(null); };
    document.addEventListener('pointerdown', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', esc); };
  }, [menu]);

  const name = (t: ToolDef) => (lang === 'fa' ? t.fa : t.en);
  const tip = (t: ToolDef) => `${name(t)}${t.key ? ` · ${t.key}` : ''}`;
  const pick = (id: string, group?: string) => { st.setTool(id, group); setMenu(null); clearTimers(); };
  // A menu the hover has just opened stays open when the click that follows lands on its arrow,
  // however slowly the click comes.
  const hoverOpenedAt = useRef(0);
  const toggleMenu = (id: MenuId, anchor: HTMLElement) => {
    clearTimers();
    const justHovered = menu?.id === id && Date.now() - hoverOpenedAt.current < 1500;
    setMenu(menu?.id === id && !justHovered ? null : { id, anchor });
    hoverOpenedAt.current = 0;
  };

  // Hover: a short pause opens a group's menu; moving to another group while one is open switches
  // at once; leaving closes after a grace period, unless the pointer reaches the menu.
  const hoverIn = (id: MenuId, anchor: HTMLElement) => {
    if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null; }
    if (menu && menu.id === id) return;
    if (openTimer.current) clearTimeout(openTimer.current);
    if (menu) { setMenu({ id, anchor }); hoverOpenedAt.current = Date.now(); return; }
    openTimer.current = setTimeout(() => { openTimer.current = null; setMenu({ id, anchor }); hoverOpenedAt.current = Date.now(); }, 320);
  };
  const hoverOut = () => {
    if (openTimer.current) { clearTimeout(openTimer.current); openTimer.current = null; }
    if (closeTimer.current) clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => { closeTimer.current = null; setMenu(null); }, 180);
  };
  const keepOpen = () => { if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null; } };

  const groupTools = (g: ToolGroup): ToolDef[] => (g.id === 'cursors' ? [...CURSOR_TOOLS, g.tools.find((t) => t.id === 'select')!] : g.tools);

  const groupButton = (g: ToolGroup) => {
    const tools = groupTools(g);
    const remembered = g.id === 'cursors' ? (st.lastInGroup.cursors === 'select' ? 'select' : st.cursor) : st.lastInGroup[g.id];
    const current = tools.find((x) => x.id === remembered) ?? tools[0]!;
    const active = tools.some((x) => x.id === st.tool);
    const shown = active ? tools.find((x) => x.id === st.tool)! : current;
    return (
      <div key={g.id} className="rail-group" onMouseEnter={(e) => hoverIn(g.id, e.currentTarget)} onMouseLeave={hoverOut}>
        <button className={`tool${active ? ' on' : ''}`} title={tip(shown)} aria-label={name(shown)} aria-pressed={active}
          data-tool={shown.id} onClick={() => pick(shown.id, g.id)}>
          <Icon name={toolIcon(shown.id)} />
        </button>
        <button className="tool-more" aria-label={L(g.fa, g.en)} title={L(g.fa, g.en)} aria-expanded={menu?.id === g.id} aria-haspopup="menu"
          data-group={g.id} onClick={(e) => toggleMenu(g.id, e.currentTarget.parentElement as HTMLElement)}><span className="more" /></button>
      </div>
    );
  };

  // A switch with its own corner menu (magnet, hide, remove), as TradingView draws them.
  const switchButton = (id: MenuId, btn: ReactNode, label: string) => (
    <div className="rail-group" onMouseEnter={(e) => hoverIn(id, e.currentTarget)} onMouseLeave={hoverOut}>
      {btn}
      <button className="tool-more" aria-label={label} title={label} aria-expanded={menu?.id === id} aria-haspopup="menu" data-group={id}
        onClick={(e) => toggleMenu(id, e.currentTarget.parentElement as HTMLElement)}><span className="more" /></button>
    </div>
  );

  const drawings = useChartBus((s) => s.drawingLayer)?.getDrawings() ?? [];
  useChartBus((s) => s.drawingVersion);
  const studies = useStudies((s) => s.list);
  const nDraw = removableDrawings(drawings, st.removeLocked).length;
  const nStudy = removableStudies(studies).length;
  const indHidden = indicatorsHidden(studies, st.hiddenStudies);
  const num = (n: number) => (lang === 'fa' ? faNum(n) : String(n));

  const flyContent = (id: MenuId): ReactNode => {
    const g = TOOL_GROUPS.find((x) => x.id === id);
    if (g && g.id === 'icons') {
      return (
        <div className="dfly-emoji" role="group" aria-label={L(g.fa, g.en)}>
          {EMOJIS.map((e) => (
            <button key={e} className={`dfly-emo${st.tool === 'emoji' && st.emoji === e ? ' on' : ''}`} onClick={() => { st.setEmoji(e); pick('emoji', 'icons'); }} aria-label={e}>{e}</button>
          ))}
        </div>
      );
    }
    if (g) {
      const tools = groupTools(g);
      return tools.map((x, i) => (
        <FlyRow key={x.id} t={x} lang={lang} on={x.id === st.tool} fav={st.favorites.includes(x.id)}
          sep={g.id === 'cursors' && x.id === 'select' && i > 0}
          onPick={() => pick(x.id, g.id)} onFav={() => st.toggleFavorite(x.id)}
          onHelp={() => { usePage.getState().open('help', x.id); setMenu(null); }} L={L} />
      ));
    }
    const check = (on: boolean) => (on ? <Icon name="ph-check" size="sm" /> : <span className="dfly-nocheck" />);
    if (id === 'magnet') {
      return (
        <>
          <button role="menuitemradio" aria-checked={st.magnet && st.magnetMode === 'weak'} className="dfly-row" onClick={() => { st.setMagnetMode('weak'); setMenu(null); }}>
            <Icon name="ph-magnet-straight" size="md" /><span className="dfly-label">{L('آهنربای ضعیف', 'Weak magnet')}</span>{check(st.magnet && st.magnetMode === 'weak')}
          </button>
          <button role="menuitemradio" aria-checked={st.magnet && st.magnetMode === 'strong'} className="dfly-row" onClick={() => { st.setMagnetMode('strong'); setMenu(null); }}>
            <Icon name="magnet" size="md" /><span className="dfly-label">{L('آهنربای قوی', 'Strong magnet')}</span>{check(st.magnet && st.magnetMode === 'strong')}
          </button>
        </>
      );
    }
    if (id === 'hide') {
      return (
        <>
          <button role="menuitemcheckbox" aria-checked={st.hidden} className="dfly-row" onClick={() => { st.setHidden(!st.hidden); setMenu(null); }}>
            <Icon name="eye_off" size="md" /><span className="dfly-label">{L('پنهان‌کردن ترسیم‌ها', 'Hide drawings')}</span><kbd className="num">Ctrl+Alt+H</kbd>{check(st.hidden)}
          </button>
          <button role="menuitemcheckbox" aria-checked={indHidden} className="dfly-row" onClick={() => { setIndicatorsHidden(!indHidden); setMenu(null); }}>
            <Icon name="ph-chart-line-up" size="md" /><span className="dfly-label">{L('پنهان‌کردن اندیکاتورها', 'Hide indicators')}</span>{check(indHidden)}
          </button>
          <button role="menuitemcheckbox" aria-checked={st.hidden && indHidden} className="dfly-row" onClick={() => { const on = !(st.hidden && indHidden); st.setHidden(on); setIndicatorsHidden(on); setMenu(null); }}>
            <Icon name="ph-eye-slash" size="md" /><span className="dfly-label">{L('پنهان‌کردن همه', 'Hide all')}</span>{check(st.hidden && indHidden)}
          </button>
        </>
      );
    }
    if (id === 'remove') {
      return (
        <>
          <button className="dfly-row" disabled={!nDraw} onClick={() => { st.clearAll(); setMenu(null); }}>
            <Icon name="trash2" size="md" /><span className="dfly-label">{lang === 'fa' ? `حذف ${num(nDraw)} ترسیم` : `Remove ${nDraw} drawing${nDraw === 1 ? '' : 's'}`}</span>
          </button>
          <button className="dfly-row" disabled={!nStudy} onClick={() => { useStudies.getState().clear(); setMenu(null); }}>
            <Icon name="ph-chart-line-up" size="md" /><span className="dfly-label">{lang === 'fa' ? `حذف ${num(nStudy)} اندیکاتور` : `Remove ${nStudy} indicator${nStudy === 1 ? '' : 's'}`}</span>
          </button>
          <button className="dfly-row" disabled={!nDraw && !nStudy} onClick={() => { st.clearAll(); useStudies.getState().clear(); setMenu(null); }}>
            <Icon name="ph-trash" size="md" /><span className="dfly-label">{lang === 'fa' ? `حذف ${num(nDraw)} ترسیم و ${num(nStudy)} اندیکاتور` : `Remove ${nDraw} drawing${nDraw === 1 ? '' : 's'} & ${nStudy} indicator${nStudy === 1 ? '' : 's'}`}</span>
          </button>
          <div className="dfly-sep" />
          <label className="dfly-row dfly-check">
            <input type="checkbox" checked={st.removeLocked} onChange={(e) => st.setRemoveLocked(e.target.checked)} />
            <span className="dfly-label">{L('حذف ترسیم‌های قفل‌شده هم', 'Always remove locked drawings')}</span>
          </label>
        </>
      );
    }
    if (id === 'color') {
      return (
        <div className="dfly-colors">
          <div className="dfly-sec">{L('رنگ ترسیم‌های تازه', 'Colour of new drawings')}</div>
          <div className="dfly-swatches">
            {PALETTE.map((c) => <button key={c} className={`swatch${c.toLowerCase() === st.color.toLowerCase() ? ' on' : ''}`} style={{ background: c }} aria-label={c} onClick={() => { st.setColor(c); setMenu(null); }} />)}
          </div>
          <label className="dfly-row dfly-check"><span className="dfly-label">{L('رنگ دلخواه', 'Custom colour')}</span><input type="color" value={st.color} onChange={(e) => st.setColor(e.target.value)} /></label>
        </div>
      );
    }
    return null;
  };

  const layerUndo = (redo: boolean) => { const l = useChartBus.getState().drawingLayer; if (!l) return; if (redo) l.redo(); else l.undo(); bumpDrawings(); };
  const favTools = st.favorites.map((id) => toolDef(id)).filter((t): t is ToolDef => Boolean(t));

  return (
    <>
      <nav className="ws-rail" ref={railRef} aria-label={L('ابزارهای ترسیم', 'Drawing tools')}>
        <button className="tool phone-only" title={L('واگرد', 'Undo')} aria-label={L('واگرد', 'Undo')} disabled={!canUndo} onClick={() => layerUndo(false)}><Icon name="ph-arrow-counter-clockwise" size="md" /></button>
        <button className="tool phone-only" title={L('ازنو', 'Redo')} aria-label={L('ازنو', 'Redo')} disabled={!canRedo} onClick={() => layerUndo(true)}><Icon name="ph-arrow-clockwise" size="md" /></button>
        <span className="rail-sep phone-only" />
        {favTools.length ? (
          <>
            {favTools.map((t) => (
              <button key={`fav-${t.id}`} className={`tool rail-fav${st.tool === t.id ? ' on' : ''}`} title={`★ ${tip(t)}`} aria-label={name(t)} aria-pressed={st.tool === t.id}
                onClick={() => pick(t.id, groupOf(t.id))}>
                <Icon name={toolIcon(t.id)} />
              </button>
            ))}
            <span className="rail-sep" />
          </>
        ) : null}
        {TOOL_GROUPS.map(groupButton)}
        <span className="rail-sep" />
        <button className={`tool${st.tool === 'ruler' ? ' on' : ''}`} title={L('اندازه‌گیری', 'Measure')} aria-pressed={st.tool === 'ruler'} onClick={() => pick(st.tool === 'ruler' ? st.cursor : 'ruler', 'measure')}><Icon name="tool_ruler" /></button>
        <button className={`tool${st.tool === 'zoom' ? ' on' : ''}`} title={L('بزرگ‌نمایی (کادر بکشید)', 'Zoom in (drag a box)')} aria-pressed={st.tool === 'zoom'} data-tool="zoom" onClick={() => pick(st.tool === 'zoom' ? st.cursor : 'zoom', 'zoom')}><Icon name="ph-magnifying-glass-plus" size="md" /></button>
        {st.zoomDepth > 0 ? <button className="tool" title={L('کوچک‌نمایی', 'Zoom out')} data-tool="zoomout" onClick={() => zoomOut()}><Icon name="ph-magnifying-glass-minus" size="md" /></button> : null}
        <span className="rail-sep" />
        {switchButton('magnet', (
          <button className={`tool${st.magnet ? ' on' : ''}`} aria-pressed={st.magnet} data-tool="magnet"
            title={`${st.magnetMode === 'weak' ? L('آهنربای ضعیف', 'Weak magnet') : L('آهنربای قوی', 'Strong magnet')} · Ctrl+Alt+M`}
            onClick={() => st.setMagnet(!st.magnet)}><Icon name={st.magnetMode === 'weak' ? 'ph-magnet-straight' : 'magnet'} size={st.magnetMode === 'weak' ? 'md' : 'lg'} /></button>
        ), L('حالت آهنربا', 'Magnets'))}
        <button className={`tool${st.stay ? ' on' : ''}`} aria-pressed={st.stay} data-tool="stay" title={`${L('ماندن در حالت ترسیم', 'Keep drawing')} · Ctrl+Alt+D`} onClick={() => st.setStay(!st.stay)}><Icon name="ph-pencil-simple-line" size="md" /></button>
        <button className={`tool${st.locked ? ' on' : ''}`} aria-pressed={st.locked} data-tool="lock" title={L('قفل همه‌ی ترسیم‌ها', 'Lock all drawings')} onClick={() => st.setLocked(!st.locked)}><Icon name={st.locked ? 'lock' : 'unlock'} /></button>
        {switchButton('hide', (
          <button className={`tool${st.hidden ? ' on' : ''}`} aria-pressed={st.hidden} data-tool="hide" title={`${L('پنهان‌کردن ترسیم‌ها', 'Hide all drawings')} · Ctrl+Alt+H`} onClick={() => st.setHidden(!st.hidden)}>
            <Icon name={st.hidden ? 'eye_off' : 'ph-eye'} size={st.hidden ? 'lg' : 'md'} />
          </button>
        ), L('گزینه‌های پنهان‌کردن', 'Hide options'))}
        <span className="rail-sep" />
        {switchButton('remove', (
          <button className="tool" data-tool="remove" title={L('حذف ترسیم‌ها', 'Remove drawings')} onClick={() => st.clearAll()}><Icon name="trash2" /></button>
        ), L('گزینه‌های حذف', 'Remove options'))}
        <span className="rail-sep" />
        {favTools.length ? (
          <button className={`tool${st.favBar ? ' on' : ''}`} aria-pressed={st.favBar} data-tool="favbar" title={L('نوار شناور ابزارهای منتخب', 'Favourite drawing tools toolbar')} onClick={() => st.setFavBar(!st.favBar)}>
            <Icon name={st.favBar ? 'ph-star-fill' : 'ph-star'} size="md" />
          </button>
        ) : null}
        <div className="rail-group" onMouseEnter={(e) => hoverIn('color', e.currentTarget)} onMouseLeave={hoverOut}>
          <button className="tool" data-tool="color" title={L('رنگ ترسیم‌های تازه', 'Colour of new drawings')} aria-haspopup="menu" aria-expanded={menu?.id === 'color'}
            onClick={(e) => toggleMenu('color', e.currentTarget.parentElement as HTMLElement)}>
            <span className="rail-color" style={{ background: st.color }} />
          </button>
        </div>
        <span className="ws-spacer" />
        <button className="tool desk-only rail-collapse" data-tool="hiderail" title={L('پنهان‌کردن نوار ابزار ترسیم', 'Hide drawing toolbar')} onClick={() => st.setRailHidden(true)}>
          <Icon name="ph-caret-double-left" size="sm" />
        </button>
      </nav>
      {menu ? (
        <Flyout anchor={menu.anchor} rail={railRef.current} lang={lang} onEnter={keepOpen} onLeave={hoverOut} id={menu.id}>
          {flyContent(menu.id)}
        </Flyout>
      ) : null}
      {st.favBar && favTools.length ? <FavBar tools={favTools} /> : null}
      {st.railHidden ? createPortal(
        <button className="rail-show desk-only" title={L('نمایش نوار ابزار ترسیم', 'Show drawing toolbar')} aria-label={L('نمایش نوار ابزار ترسیم', 'Show drawing toolbar')} onClick={() => st.setRailHidden(false)}>
          <Icon name="ph-caret-double-right" size="sm" />
        </button>, document.body) : null}
    </>
  );
}

// One tool in a group's menu: its icon, name and key; on hover a "?" (the tool's help) and a star
// (pin it to the rail and the favourites toolbar), the star staying lit for a favourite.
function FlyRow({ t, lang, on, fav, sep, onPick, onFav, onHelp, L }: { t: ToolDef; lang: 'fa' | 'en'; on: boolean; fav: boolean; sep: boolean; onPick(): void; onFav(): void; onHelp(): void; L(fa: string, en: string): string }) {
  return (
    <>
      {sep ? <div className="dfly-sep" /> : null}
      {t.sec ? <div className="dfly-sec">{lang === 'fa' ? t.sec[0] : t.sec[1]}</div> : null}
      <div role="menuitem" tabIndex={0} className={`dfly-row${on ? ' on' : ''}`} data-tool={t.id}
        onClick={onPick} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onPick(); } }}>
        <Icon name={toolIcon(t.id)} size="lg" />
        <span className="dfly-label">{toolName(t.id, lang)}</span>
        {t.key ? <kbd className="num">{t.key}</kbd> : null}
        <button className="dfly-mini dfly-help" title={L('راهنمای این ابزار', 'Help for this tool')} aria-label={L('راهنما', 'Help')} onClick={(e) => { e.stopPropagation(); onHelp(); }}><Icon name="ph-question" size="sm" /></button>
        <button className={`dfly-mini dfly-star${fav ? ' on' : ''}`} title={fav ? L('برداشتن از منتخب‌ها', 'Remove from favourites') : L('افزودن به منتخب‌ها', 'Add to favourites')} aria-pressed={fav} aria-label={L('منتخب', 'Favourite')}
          onClick={(e) => { e.stopPropagation(); onFav(); }}><Icon name={fav ? 'ph-star-fill' : 'ph-star'} size="sm" /></button>
      </div>
    </>
  );
}

// The menu beside the rail: fixed, at the button's height, kept inside the window.
function Flyout({ anchor, rail, lang, id, onEnter, onLeave, children }: { anchor: HTMLElement; rail: HTMLElement | null; lang: 'fa' | 'en'; id: string; onEnter(): void; onLeave(): void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left: number; top: number; maxH: number }>({ left: -9999, top: 0, maxH: 400 });
  useLayoutEffect(() => {
    const a = anchor.getBoundingClientRect();
    const r = rail?.getBoundingClientRect();
    const left = Math.round((r && r.width ? r.right : a.right) + 4);
    const h = ref.current?.scrollHeight ?? 0; // the whole menu, even while a smaller max-height caps it
    const maxH = window.innerHeight - 16;
    const top = Math.max(8, Math.min(a.top, window.innerHeight - 8 - Math.min(h, maxH)));
    setPos({ left, top, maxH });
  }, [anchor, rail, id]);
  return createPortal(
    <div ref={ref} className="dfly" role="menu" dir={lang === 'fa' ? 'rtl' : 'ltr'} data-menu={id}
      style={{ left: pos.left, top: pos.top, maxHeight: pos.maxH }} onMouseEnter={onEnter} onMouseLeave={onLeave}>
      {children}
    </div>,
    document.body,
  );
}

// TradingView's floating favourites toolbar: the starred tools in a bar dragged by its grip.
function FavBar({ tools }: { tools: ToolDef[] }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const tool = useDrawing((s) => s.tool);
  const pos = useDrawing((s) => s.favPos);
  const ref = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<{ dx: number; dy: number } | null>(null);
  const [live, setLive] = useState(pos ?? { x: 72, y: 56 });
  useEffect(() => { if (pos) setLive(pos); }, [pos]);
  const clamp = (p: { x: number; y: number }) => {
    const w = ref.current?.offsetWidth ?? 200, h = ref.current?.offsetHeight ?? 40;
    return { x: Math.max(0, Math.min(window.innerWidth - w, p.x)), y: Math.max(0, Math.min(window.innerHeight - h, p.y)) };
  };
  const at = clamp(live);
  return createPortal(
    <div ref={ref} className="dfav" role="toolbar" aria-label={L('ابزارهای منتخب', 'Favourite drawing tools')} style={{ left: at.x, top: at.y }}>
      <span className="dfav-grip" title={L('جابه‌جایی', 'Move')}
        onPointerDown={(e) => { (e.target as HTMLElement).setPointerCapture?.(e.pointerId); setDrag({ dx: e.clientX - at.x, dy: e.clientY - at.y }); }}
        onPointerMove={(e) => { if (drag) setLive(clamp({ x: e.clientX - drag.dx, y: e.clientY - drag.dy })); }}
        onPointerUp={() => { if (drag) { setDrag(null); useDrawing.getState().setFavPos(clamp(live)); } }}>
        <Icon name="ph-dots-six-vertical" size="sm" />
      </span>
      {tools.map((t) => (
        <button key={t.id} className={`ib${tool === t.id ? ' on' : ''}`} title={`${lang === 'fa' ? t.fa : t.en}${t.key ? ` · ${t.key}` : ''}`} aria-label={lang === 'fa' ? t.fa : t.en}
          onClick={() => useDrawing.getState().setTool(t.id, groupOf(t.id))}>
          <Icon name={toolIcon(t.id)} size="md" />
        </button>
      ))}
      <button className="ib dfav-close" title={L('بستن', 'Close')} aria-label={L('بستن', 'Close')} onClick={() => useDrawing.getState().setFavBar(false)}><Icon name="ph-x" size="sm" /></button>
    </div>,
    document.body,
  );
}

