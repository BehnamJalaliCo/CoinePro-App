import { beforeEach, describe, expect, it } from 'vitest';
import { COACH_GROUPS, COACH_TIPS, COOLDOWN_MS, MOUSE_SESSION_CAP, SESSION_CAP, cardTop, pickTip, tipText, tipTitle, type Box, type PickInput } from '../coach/tips';
import { easeInOut, easeOutBack, filmPrice, seg } from '../coach/films';
import { useCoach, coachDone } from '../coach/store';
import { stepOf } from '../firstrun/store';

const box = (left = 10, top = 10, width = 100, height = 100): Box => ({ left, top, width, height });
const input = (over: Partial<PickInput> = {}): PickInput => ({
  requests: new Map(COACH_TIPS.map((t) => [t.id, box()])), dismissed: new Set(), off: false, touch: true, shown: 0, cooling: false, windowHeight: 900, windowWidth: 1440, blocked: false, ...over,
});

describe('coach tips', () => {
  it('ports the Kotlin list in its order, bilingual, with no «نمودار» and no «هٔ»', () => {
    expect(COACH_TIPS.map((t) => t.id)).toEqual(['draw_cursor', 'draw_toolbar', 'chart_pinch', 'chart_axis', 'chart_hold', 'draw_band', 'draw_handle', 'select_bar', 'gutter_plus', 'alert_line', 'axis_minis', 'legend', 'chart_events', 'realtime', 'replay_bar', 'row_reorder']);
    for (const t of COACH_TIPS) {
      for (const b of [t.title, t.text, t.mouseText, t.mouseTitle].filter(Boolean)) {
        expect(b!.fa.length).toBeGreaterThan(2); expect(b!.en.length).toBeGreaterThan(2);
        expect(b!.fa).not.toMatch(/نمودار|هٔ/); expect(b!.en).not.toMatch(/[؀-ۿ]/);
      }
      expect(COACH_GROUPS.some((g) => g.id === t.group)).toBe(true);
    }
  });
  it('words a tip for the hand in use', () => {
    const pinch = COACH_TIPS.find((t) => t.id === 'chart_pinch')!;
    expect(tipTitle(pinch, true).fa).toBe('زوم با دو انگشت');
    expect(tipTitle(pinch, false).fa).toBe('زوم با چرخ موس');
    const axis = COACH_TIPS.find((t) => t.id === 'chart_axis')!;
    expect(tipTitle(axis, false)).toBe(axis.title);
    expect(tipText(axis, false).en).toMatch(/mouse/);
  });
});

describe('which tip comes next', () => {
  it('takes the first in order whose control is on screen', () => {
    expect(pickTip(input())?.id).toBe('draw_cursor');
    expect(pickTip(input({ touch: false }))?.id).toBe('draw_toolbar');
    expect(pickTip(input({ dismissed: new Set(['draw_cursor', 'draw_toolbar']) }))?.id).toBe('chart_pinch');
  });
  it('never shows a touch-only gesture to a mouse', () => {
    const only = new Map([['draw_cursor', box()]]);
    expect(pickTip(input({ requests: only, touch: false }))).toBeNull();
    expect(pickTip(input({ requests: only, touch: true }))?.id).toBe('draw_cursor');
  });
  it('three a launch on a touch screen, one with a mouse, and the urgent one past both', () => {
    const req = new Map([['chart_pinch', box()]]);
    expect(pickTip(input({ requests: req, shown: SESSION_CAP - 1 }))?.id).toBe('chart_pinch');
    expect(pickTip(input({ requests: req, shown: SESSION_CAP }))).toBeNull();
    expect(pickTip(input({ requests: req, touch: false, shown: MOUSE_SESSION_CAP }))).toBeNull();
    expect(pickTip(input({ requests: new Map([['draw_cursor', box()]]), shown: 99, cooling: true }))?.id).toBe('draw_cursor');
  });
  it('waits out the cooldown, a dialog, «بلدم», and a control off screen or too small', () => {
    const req = new Map([['chart_axis', box()]]);
    expect(pickTip(input({ requests: req, cooling: true }))).toBeNull();
    expect(pickTip(input({ requests: req, blocked: true }))).toBeNull();
    expect(pickTip(input({ requests: req, off: true }))).toBeNull();
    expect(pickTip(input({ requests: new Map([['chart_axis', box(0, 0, 6, 100)]]) }))).toBeNull();
    expect(pickTip(input({ requests: new Map([['chart_axis', box(0, 950)]]) }))).toBeNull();
    expect(pickTip(input({ requests: new Map([['chart_axis', box(-52, 10, 52, 400)]]) }))).toBeNull();
  });
  it('puts the card under the light, over it, over the foot of a tall control, or in the middle', () => {
    expect(cardTop(box(0, 100, 300, 50), 200, 900)).toBe(166);
    expect(cardTop(box(0, 700, 300, 150), 200, 900)).toBe(484);
    expect(cardTop(box(0, 40, 300, 820), 340, 900)).toBe(900 - 340 - 96);
    expect(cardTop(null, 300, 900)).toBe(300);
  });
});

describe('coach store', () => {
  beforeEach(() => { localStorage.clear(); useCoach.setState({ dismissed: [], off: false, showing: null, preview: null, shown: 0, coolingUntil: 0 }); });
  it('puts a tip away the moment it shows, and cools down after it closes', () => {
    useCoach.getState().show('chart_pinch');
    expect(useCoach.getState().showing).toBe('chart_pinch');
    expect(JSON.parse(localStorage.getItem('pc.coach.v1')!).dismissed).toEqual(['chart_pinch']);
    const before = Date.now();
    useCoach.getState().close();
    expect(useCoach.getState().coolingUntil).toBeGreaterThanOrEqual(before + COOLDOWN_MS);
    expect(useCoach.getState().shown).toBe(1);
  });
  it('«بلدم» puts it all away; «همه را دوباره نشان بده» brings it all back with a fresh session', () => {
    useCoach.getState().show('legend');
    useCoach.getState().never();
    expect(coachDone(useCoach.getState())).toBe(true);
    useCoach.getState().replayAll();
    const s = useCoach.getState();
    expect([s.off, s.dismissed.length, s.shown, s.coolingUntil]).toEqual([false, 0, 0, 0]);
    expect(JSON.parse(localStorage.getItem('pc.coach.v1')!)).toEqual({ dismissed: [], off: false });
  });
  it('a library play is a preview, not a dismissal', () => {
    useCoach.getState().play('chart_axis');
    expect(useCoach.getState().preview).toBe('chart_axis');
    expect(useCoach.getState().dismissed).toEqual([]);
  });
});

describe('films', () => {
  it('eases like the Kotlin films and writes market figures in Latin digits', () => {
    expect(seg(0.5, 0.4, 0.6)).toBeCloseTo(0.5);
    expect(seg(0, 0.4, 0.6)).toBe(0);
    expect(easeInOut(0.5)).toBeCloseTo(0.5);
    expect(easeOutBack(1)).toBeCloseTo(1);
    expect(Math.max(...Array.from({ length: 50 }, (_, i) => easeOutBack(i / 50)))).toBeGreaterThan(1);
    expect(filmPrice(85240)).toBe('85,240');
    expect(filmPrice(168)).toBe('168');
  });
});

describe('first run', () => {
  const fresh = { welcome: false, starter: false, reader: null, rise: 'green' as const };
  const ok = { allowed: true, returning: false, pageOpen: false };
  it('welcome, then the starter preferences, then the one question, then nothing', () => {
    expect(stepOf(fresh, ok)).toBe('welcome');
    expect(stepOf({ ...fresh, welcome: true }, ok)).toBe('starter');
    expect(stepOf({ ...fresh, welcome: true, starter: true }, ok)).toBe('question');
    expect(stepOf({ ...fresh, welcome: true, starter: true, reader: 'trader' }, ok)).toBeNull();
  });
  it('never for the e2e suite unless asked, a returning reader, or a page opened by its address', () => {
    expect(stepOf(fresh, { ...ok, allowed: false })).toBeNull();
    expect(stepOf(fresh, { ...ok, returning: true })).toBeNull();
    expect(stepOf(fresh, { ...ok, pageOpen: true })).toBeNull();
  });
});
