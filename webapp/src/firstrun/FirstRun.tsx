// The first screens anybody sees (OLD web WebApp.kt:335-363): the welcome slides (WelcomeSlides.kt),
// then four things asked once together (StarterPreferences.kt), then one question about how much to
// put on the chart (FirstRunQuestion.kt). Nothing is required: every row has a value the moment it
// opens, «بعداً» is drawn as plainly as the three answers, and a reader who touches nothing gets the
// defaults they would have had anyway. Never shown to the end-to-end suite unless a test asks
// (?e2e=1&firstrun=1), never over a page opened by its address (a reset link, a lesson), and never
// to a browser that used the app before this screen existed.
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { usePrefs, type Theme } from '../state/prefs';
import { useChartSettings } from '../state/chartSettings';
import { usePage } from '../state/page';
import { useL } from '../pages/kit';
import { Icon } from '../ui/Icon';
import { reducedMotion } from '../coach/anchors';
import { firstRunPermitted, isReturning, stepOf, useFirstRun, type ReaderMode, type Rise } from './store';
import { drawWelcomeArt, type WelcomeSlideId } from './welcomeArt';
import './firstrun.css';

const SLIDES: { id: WelcomeSlideId; headline: [string, string]; body: [string, string] }[] = [
  { id: 'chart', headline: ['چارتی که حرف می‌زند', 'A chart that explains itself'], body: ['هر خوانش چارت با دلیلش می‌آید، به زبان ساده.', 'Every chart reading comes with the reason behind it, in plain words.'] },
  { id: 'tools', headline: ['بیش از ۲۴۰ ابزار، و ابزار خودتان', '240+ tools, and your own'], body: ['اندیکاتور، ترسیم، و نمااسکریپت برای هرچه در فهرست نیست.', 'Indicators, drawings, and NamaScript for anything the list does not have.'] },
  { id: 'alerts', headline: ['هشدار و عمق بازار', 'Alerts and the order book'], body: ['وقتی قیمت رسید خبردار شوید، و ببینید پشتش چه منتظر است.', 'Be told when a price arrives, and see what is waiting behind it.'] },
  { id: 'practice', headline: ['تمرین بدون ریسک', 'Practise without risk'], body: ['یک روز را کندل‌به‌کندل بازپخش کنید و روی کاغذ معامله کنید.', 'Replay a day bar by bar, and trade it on paper.'] },
  { id: 'free', headline: ['رایگان، فارسی، بدون واریز', 'Free, in Persian, no deposit'], body: ['اینجا چیزی پول نمی‌خواهد و چیزی پشت دیوار نیست.', 'Nothing here asks for money and nothing is behind a wall.'] },
];

/** How long the brand is held before the first slide. */
const BRAND_HOLD_MS = 600;
/** Long enough to read six words and a line under them. */
const DWELL_MS = 3200;
/** The stillness a touch buys before the clock runs again. */
const RESUME_MS = 3000;
/** One run of a scene's reveal: it happens once and then the picture is finished. */
const REVEAL_MS = 900;
const SWIPE_ARM = 48;
/** The welcome is near-black in both themes: it runs before the reader has chosen one. */
const ART = { gold: '#D4A64A', ink: '#DBDBDB', muted: '#3D3D3D' };

function WelcomeArt({ slide, moves }: { slide: WelcomeSlideId; moves: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current; const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    let raf = 0; const t0 = performance.now();
    const frame = () => {
      const w = canvas.clientWidth; const h = canvas.clientHeight; const dpr = window.devicePixelRatio || 1;
      if (w > 0 && h > 0) {
        if (canvas.width !== Math.round(w * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr); }
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        const x = moves ? Math.min(1, (performance.now() - t0) / REVEAL_MS) : 1;
        drawWelcomeArt(ctx, w, h, slide, 1 - (1 - x) ** 3, ART);
        if (x < 1) { raf = requestAnimationFrame(frame); return; }
        canvas.dataset.done = '1';
      } else raf = requestAnimationFrame(frame);
    };
    frame();
    return () => cancelAnimationFrame(raf);
  }, [slide, moves]);
  return <canvas ref={ref} className="wl-art" data-slide={slide} aria-hidden />;
}

function Welcome({ onStart, onSignIn }: { onStart(): void; onSignIn(): void }) {
  const L = useL();
  const lang = usePrefs((s) => s.lang);
  const fa = lang === 'fa';
  const [moves] = useState(() => !reducedMotion());
  const [slide, setSlide] = useState(0);
  const [opening, setOpening] = useState(true);
  const [held, setHeld] = useState(false);
  const [touched, setTouched] = useState(false);
  const down = useRef<{ x: number; id: number } | null>(null);
  const count = SLIDES.length;

  useEffect(() => { const t = setTimeout(() => setOpening(false), BRAND_HOLD_MS); return () => clearTimeout(t); }, []);
  // The clock: stopped while a finger or the button is down, three seconds of stillness after, and
  // not at all without motion (the page must not change under a reader who asked for stillness).
  useEffect(() => {
    if (opening || held || !moves) return;
    if (touched) { const t = setTimeout(() => setTouched(false), RESUME_MS); return () => clearTimeout(t); }
    const t = setTimeout(() => setSlide((s) => (s + 1) % count), DWELL_MS);
    return () => clearTimeout(t);
  }, [slide, held, touched, opening, moves, count]);

  const go = (towardsStart: boolean) => { setOpening(false); setSlide((s) => (s + (towardsStart ? 1 : count - 1)) % count); };
  const s = SLIDES[slide]!;
  return (
    <div className="first-run welcome" role="dialog" aria-modal="true" aria-label={L('خوش آمدید', 'Welcome')} dir={fa ? 'rtl' : 'ltr'}
      onPointerDown={(e) => { if ((e.target as HTMLElement).closest('button')) return; down.current = { x: e.clientX, id: e.pointerId }; setHeld(true); setTouched(true); }}
      onPointerUp={(e) => {
        const d = down.current; down.current = null; setHeld(false);
        if (!d) return;
        const dx = e.clientX - d.x;
        if (Math.abs(dx) < SWIPE_ARM) return;
        // A drag towards the reading edge goes on: leftwards in English, rightwards in Persian.
        go(fa ? dx > 0 : dx < 0);
      }}
      onPointerCancel={() => { down.current = null; setHeld(false); }}>
      <div className="wl-col">
        <div className="wl-stage">
          {opening ? (
            <div className="wl-brand" aria-hidden={false}>
              <svg className="wl-mark" viewBox="0 0 48 48" aria-hidden>
                <rect x="9" y="16" width="8" height="18" rx="2" fill="#D4A64A" /><line x1="13" y1="10" x2="13" y2="40" stroke="#D4A64A" strokeWidth="2" strokeLinecap="round" />
                <rect x="27" y="10" width="8" height="22" rx="2" fill="#DBDBDB" /><line x1="31" y1="6" x2="31" y2="38" stroke="#DBDBDB" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <b className={fa ? 'fa' : ''}>{L('پرو چارت', 'Pro Chart')}</b>
            </div>
          ) : (
            <div className="wl-slide" key={s.id} data-slide={s.id}>
              <WelcomeArt slide={s.id} moves={moves} />
              <h2 className={fa ? 'fa' : ''}>{fa ? s.headline[0] : s.headline[1]}</h2>
              <p className={fa ? 'fa' : ''}>{fa ? s.body[0] : s.body[1]}</p>
            </div>
          )}
        </div>
        <div className="wl-dots" style={{ visibility: opening ? 'hidden' : 'visible' }}>
          {SLIDES.map((x, i) => (
            <button key={x.id} type="button" className={i === slide ? 'on' : ''} aria-label={`${i + 1}`} aria-current={i === slide}
              onClick={() => { setOpening(false); setTouched(true); setSlide(i); }} />
          ))}
        </div>
        <div className="wl-buttons">
          <button type="button" className="wl-primary" onClick={onStart}>{L('شروع', 'Get started')}</button>
          <button type="button" className="wl-secondary" onClick={onSignIn}>{L('ورود به حساب', 'Sign in')}</button>
          <p className={`wl-terms${fa ? ' fa' : ''}`}>{L('با ادامه، قوانین و یادداشت حریم خصوصی را می‌پذیرید.', 'By continuing you accept the terms and the privacy note.')}</p>
        </div>
      </div>
    </div>
  );
}

function Choice<T extends string>({ title, options, value, onPick, swatch }: {
  title: string; options: { id: T; label: string; lang?: string }[]; value: T; onPick(v: T): void; swatch?: (v: T) => string;
}) {
  return (
    <div className="fr-choice" role="radiogroup" aria-label={title}>
      <span className="fr-choice-title">{title}</span>
      <div className="fr-pills">
        {options.map((o) => (
          <button key={o.id} type="button" role="radio" aria-checked={o.id === value} className={`fr-pill${o.id === value ? ' on' : ''}`} lang={o.lang} onClick={() => onPick(o.id)}>
            {swatch ? <i className="fr-swatch" style={{ background: swatch(o.id) }} aria-hidden /> : null}
            <span>{o.label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

const GREEN = '#089981';
const RED = '#F23645';

/** Swaps the chart's bar colours when the reader says a rise is red (or back to green). */
function applyRiseToChart(rise: Rise): void {
  const cs = useChartSettings.getState();
  const [up, down] = rise === 'red' ? [RED, GREEN] : [GREEN, RED];
  cs.set({ up, down, borderUp: up, borderDown: down, wickUp: up, wickDown: down });
}

function Starter({ onDone }: { onDone(): void }) {
  const L = useL();
  const theme = usePrefs((s) => s.theme); const setTheme = usePrefs((s) => s.setTheme);
  const lang = usePrefs((s) => s.lang); const setLang = usePrefs((s) => s.setLang);
  const rise = useFirstRun((s) => s.rise); const setRise = useFirstRun((s) => s.setRise);
  const fa = lang === 'fa';
  // Quote currency is not asked: every list here is already in its market's own quote (USDT pairs,
  // FX and metals in USD), so the web app has no use for the answer.
  return (
    <div className="first-run starter" role="dialog" aria-modal="true" aria-label={L('چند مورد، فقط یک بار', 'A few things, once')} dir={fa ? 'rtl' : 'ltr'}>
      <div className="fr-col">
        <h2 className={fa ? 'fa' : ''}>{L('چند مورد، فقط یک بار', 'A few things, once')}</h2>
        <Choice<Theme> title={L('پوسته', 'Theme')} value={theme} onPick={setTheme}
          options={[{ id: 'system', label: L('سیستم', 'System') }, { id: 'light', label: L('روشن', 'Light') }, { id: 'dark', label: L('تیره', 'Dark') }]} />
        {/* Each language written in itself: the one row that has to work for a reader who cannot read the rest. */}
        <Choice<'fa' | 'en'> title={L('زبان', 'Language')} value={lang} onPick={setLang}
          options={[{ id: 'fa', label: 'فارسی', lang: 'fa' }, { id: 'en', label: 'English', lang: 'en' }]} />
        <Choice<Rise> title={L('رشد یعنی', 'A rise is')} value={rise} onPick={(r) => { setRise(r); applyRiseToChart(r); }}
          options={[{ id: 'green', label: L('سبز', 'Green') }, { id: 'red', label: L('قرمز', 'Red') }]}
          swatch={(r) => (r === 'green' ? GREEN : RED)} />
        <button type="button" className="btn btn-primary fr-done" onClick={onDone}>{L('بزن بریم', 'Let’s go')}</button>
      </div>
    </div>
  );
}

function Question({ onChoose }: { onChoose(mode: ReaderMode): void }) {
  const L = useL();
  const fa = usePrefs((s) => s.lang) === 'fa';
  // Simple first: the reader this question exists for is the beginner.
  const cards: { mode: ReaderMode; icon: string; title: string; body: string }[] = [
    { mode: 'simple', icon: 'chart_candles', title: L('تازه‌کارم', 'I am new to this'), body: L('چارت، خوانشش، و هشدارها.', 'The chart, what it is saying, and alerts.') },
    { mode: 'trader', icon: 'tool_longshort', title: L('معامله می‌کنم', 'I trade'), body: L('اندیکاتورها، ابزار ترسیم، بک‌تست و معامله‌ی آزمایشی.', 'Indicators, drawing tools, backtests and paper trades.') },
    { mode: 'pro', icon: 'code2', title: L('خودم می‌نویسم', 'I write my own'), body: L('همه‌اش، به‌اضافه‌ی نمااسکریپت و چند چارت هم‌زمان.', 'All of it, plus NamaScript and several charts at once.') },
  ];
  return (
    <div className="first-run question" role="dialog" aria-modal="true" aria-label={L('چه چیزی روی چارت شما باشد؟', 'What should we put on your chart?')} dir={fa ? 'rtl' : 'ltr'}>
      <div className="fr-col">
        <h2 className={fa ? 'fa' : ''}>{L('چه چیزی روی چارت شما باشد؟', 'What should we put on your chart?')}</h2>
        <p className={`fr-reassure${fa ? ' fa' : ''}`}>{L('هر وقت خواستید از منوی اصلی عوضش کنید.', 'You can change this at any time, from the main menu.')}</p>
        {cards.map((c) => (
          <button key={c.mode} type="button" className="fr-mode" data-mode={c.mode} onClick={() => onChoose(c.mode)}>
            <span className="fr-plate"><Icon name={c.icon} size="md" /></span>
            <span className="fr-mode-text"><b className={fa ? 'fa' : ''}>{c.title}</b><span className={fa ? 'fa' : ''}>{c.body}</span></span>
            <Icon name={fa ? 'ph-caret-left' : 'ph-caret-right'} size="sm" className="fr-chev" />
          </button>
        ))}
        <button type="button" className="fr-skip" onClick={() => onChoose('trader')}>{L('بعداً', 'Later')}</button>
      </div>
    </div>
  );
}

/** The first run, whichever of its screens is due. Mounted once, by the app. */
export function FirstRun({ onSignIn }: { onSignIn(): void }) {
  const st = useFirstRun();
  // Decided on the first frame: a page opened by its address keeps the reader there.
  const [pageAtStart] = useState(() => usePage.getState().page !== null);
  const step = stepOf(st, { allowed: firstRunPermitted, returning: isReturning, pageOpen: pageAtStart });
  const setActive = st.setActive;
  useEffect(() => { setActive(step !== null); }, [step, setActive]);
  let body: ReactNode = null;
  if (step === 'welcome') body = <Welcome onStart={st.seeWelcome} onSignIn={() => { st.seeWelcome(); onSignIn(); }} />;
  else if (step === 'starter') body = <Starter onDone={st.finishStarter} />;
  else if (step === 'question') body = <Question onChoose={st.setReader} />;
  return body;
}
