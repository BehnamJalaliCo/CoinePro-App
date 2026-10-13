/* Pro Chart owner console. No framework, no third-party code: the page's CSP allows only itself. */
'use strict';

// ── Basics ─────────────────────────────────────────────────────────────────────────────────────

const TZ = 'Asia/Tehran';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
const state = { csrf: null, me: null, view: null, timers: [], observers: [], cache: {} };

const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, cls = '') => `<svg viewBox="0 0 24 24" class="${cls}"><use href="#i-${name}"/></svg>`;
const sum = (values) => values.reduce((a, b) => a + (b || 0), 0);
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
let uidCounter = 0;
const uid = (p = 'u') => `${p}${++uidCounter}`;

const fmt = {
  n: (v) => (v === null || v === undefined || Number.isNaN(v) ? '—' : Number(v).toLocaleString('en-US')),
  compact: (v) => {
    if (v === null || v === undefined) return '—';
    const a = Math.abs(v);
    if (a >= 1e6) return (v / 1e6).toFixed(a >= 1e7 ? 0 : 1) + 'M';
    if (a >= 1e4) return (v / 1e3).toFixed(a >= 1e5 ? 0 : 1) + 'K';
    return Number(v).toLocaleString('en-US');
  },
  pct: (v, digits = 1) => (v === null || v === undefined ? '—' : (v * 100).toFixed(digits) + '%'),
  bytes: (v) => {
    if (!v) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.min(units.length - 1, Math.floor(Math.log(v) / Math.log(1024)));
    return (v / 1024 ** i).toFixed(i ? 1 : 0) + ' ' + units[i];
  },
  // Persian digits for counts in prose; Latin for every figure in a table or a chart.
  fa: (v) => Number(v).toLocaleString('fa-IR'),
};
const jalali = new Intl.DateTimeFormat('fa-IR-u-ca-persian-nu-latn', { timeZone: TZ, year: 'numeric', month: 'long', day: 'numeric' });
const jalaliShort = new Intl.DateTimeFormat('fa-IR-u-ca-persian-nu-latn', { timeZone: TZ, month: 'short', day: 'numeric' });
const jalaliFull = new Intl.DateTimeFormat('fa-IR-u-ca-persian-nu-latn', { timeZone: TZ, weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
const timeFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const secondsFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' });
const regionNames = (() => { try { return new Intl.DisplayNames(['fa'], { type: 'region' }); } catch { return null; } })();

const dayDate = (day) => new Date(day + 'T12:00:00Z');
const dayLabel = (day) => jalaliShort.format(dayDate(day));
const when = (epoch) => (epoch ? `${jalali.format(new Date(epoch * 1000))} · ${timeFmt.format(new Date(epoch * 1000))}` : '—');
function ago(epoch) {
  if (!epoch) return '—';
  const s = Math.max(0, Date.now() / 1000 - epoch);
  if (s < 60) return 'همین حالا';
  if (s < 3600) return `${fmt.fa(Math.floor(s / 60))} دقیقه پیش`;
  if (s < 86400) return `${fmt.fa(Math.floor(s / 3600))} ساعت پیش`;
  if (s < 86400 * 30) return `${fmt.fa(Math.floor(s / 86400))} روز پیش`;
  return jalali.format(new Date(epoch * 1000));
}
function country(code) {
  if (!code || code === '??' || code === '?') return 'نامشخص';
  const flag = /^[A-Z]{2}$/.test(code) ? String.fromCodePoint(...[...code].map((c) => 0x1f1a5 + c.charCodeAt(0))) : '';
  let name = code;
  try { name = regionNames?.of(code) || code; } catch { /* an unknown code stays a code */ }
  return esc(`${flag} ${name}`.trim());
}
const ANDROID = { 21: '5.0', 22: '5.1', 23: '6', 24: '7', 25: '7.1', 26: '8', 27: '8.1', 28: '9', 29: '10', 30: '11', 31: '12', 32: '12L', 33: '13', 34: '14', 35: '15', 36: '16' };
const androidOf = (sdk) => (sdk && ANDROID[sdk] ? `Android ${ANDROID[sdk]}` : sdk ? `SDK ${sdk}` : '—');
const PLATFORM = { android: 'اندروید', web: 'وب', direct: 'مستقیم', bazaar: 'بازار', fa: 'فارسی', en: 'English' };
const platformLabel = (label) => String(label).split(' · ').map((p) => PLATFORM[p] || p).join(' · ');

// Wall time in Tehran ⇄ epoch seconds, whatever zone this browser is in.
function tehranParts(epoch) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(epoch * 1000)).map((p) => [p.type, p.value]));
  return parts;
}
function epochToInput(epoch) {
  const p = tehranParts(epoch);
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}
function inputToEpoch(value) {
  if (!value) return null;
  const [d, t] = value.split('T');
  const [y, m, day] = d.split('-').map(Number);
  const [hh, mm] = (t || '00:00').split(':').map(Number);
  const wall = Date.UTC(y, m - 1, day, hh, mm) / 1000;
  let guess = wall - 3.5 * 3600;
  for (let i = 0; i < 2; i += 1) {
    const p = tehranParts(guess);
    const seen = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute) / 1000;
    guess += wall - seen;
  }
  return Math.round(guess);
}

// ── Network ────────────────────────────────────────────────────────────────────────────────────

const ERRORS = {
  bad_credentials: 'نام کاربری یا رمز عبور درست نیست.',
  locked: 'به خاطر تلاش‌های ناموفق، حساب چند دقیقه قفل است.',
  too_many_attempts: 'تلاش‌ها زیاد بود؛ چند دقیقه صبر کنید.',
  bad_code: 'کد تأیید درست نیست یا قبلاً استفاده شده.',
  csrf: 'نشست تازه نیست؛ صفحه را دوباره بارگذاری کنید.',
  origin: 'درخواست از مبدأ ناشناس رد شد.',
  signed_out: 'نشست به پایان رسیده؛ دوباره وارد شوید.',
  kind: 'نوع پیام را انتخاب کنید.',
  text: 'عنوان و متن، دست‌کم به یک زبان، لازم است.',
  link: 'پیوند باید با coinepro:// یا https:// شروع شود.',
  coupon: 'این کد تخفیف ساخته نشده است.',
  schedule: 'زمان پایان باید بعد از زمان شروع باشد.',
  already_sent: 'پیامی که ارسال شده دیگر ویرایش نمی‌شود؛ آن را لغو یا تکثیر کنید.',
  exists: 'این کد قبلاً ساخته شده است.',
  percent: 'درصد تخفیف باید بین ۱ تا ۹۰ باشد.',
  code: 'کد فقط حروف بزرگ لاتین، عدد و خط تیره (۳ تا ۴۰ نویسه).',
  numbers: 'عددها را درست وارد کنید.',
  bad_current: 'رمز فعلی درست نیست.',
  too_short: 'رمز جدید دست‌کم ۱۰ نویسه باشد.',
  too_simple: 'رمز جدید خیلی ساده است.',
  no_secret: 'اول کلید را بسازید.',
  min_version_code: 'کد نسخه عدد صحیح است.',
  hello_every_hours: 'فاصلهٔ بررسی بین ۱ تا ۲۴ ساعت است.',
  network: 'ارتباط با سرور برقرار نشد.',
};
const errorText = (error) => ERRORS[error?.code] || ERRORS[error?.message] || `خطا (${esc(error?.code || error?.message || '?')})`;

class ApiError extends Error {
  constructor(code, status, data) { super(code); this.code = code; this.status = status; this.data = data; }
}

async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') headers['X-CSRF-Token'] = state.csrf || '';
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch('/admin/api/' + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'same-origin', cache: 'no-store',
    });
  } catch {
    throw new ApiError('network', 0);
  }
  let data = null;
  try { data = await response.json(); } catch { /* an empty body */ }
  if (response.status === 401 && path !== 'login' && path !== 'session') {
    showLogin();
    throw new ApiError('signed_out', 401, data);
  }
  if (!response.ok) throw new ApiError(data?.error || String(response.status), response.status, data);
  return data;
}

// ── Little UI pieces ───────────────────────────────────────────────────────────────────────────

function toast(text, kind = 'good') {
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.innerHTML = `${icon(kind === 'good' ? 'check' : 'alert')}<span>${text}</span>`;
  $('#toasts').append(el);
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 300); }, 3600);
}

function modal(html, { size = '' } = {}) {
  const scrim = document.createElement('div');
  scrim.className = 'modal-scrim';
  scrim.innerHTML = `<div class="modal ${size}" role="dialog" aria-modal="true">${html}</div>`;
  $('#modal-root').append(scrim);
  const close = () => { scrim.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  scrim.addEventListener('mousedown', (e) => { if (e.target === scrim) close(); });
  $$('[data-close]', scrim).forEach((b) => b.addEventListener('click', close));
  hydrate(scrim);
  return { el: scrim, close };
}

function confirmBox(title, text, { danger = false, ok = 'تأیید' } = {}) {
  return new Promise((resolve) => {
    const m = modal(`
      <div class="modal-head"><h3>${title}</h3><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
      <div class="modal-body"><p class="muted">${text}</p></div>
      <div class="modal-foot"><button class="btn" data-close>انصراف</button><button class="btn ${danger ? 'danger' : 'primary'}" data-ok>${ok}</button></div>`, { size: 'sm' });
    $('[data-ok]', m.el).addEventListener('click', () => { m.close(); resolve(true); });
    $$('[data-close]', m.el).forEach((b) => b.addEventListener('click', () => resolve(false)));
  });
}

async function busy(button, job) {
  button?.classList.add('loading');
  try { return await job(); } finally { button?.classList.remove('loading'); }
}

async function copy(text) {
  try { await navigator.clipboard.writeText(text); toast('کپی شد'); } catch { toast('کپی نشد', 'bad'); }
}

function downloadCsv(name, header, rows) {
  const line = (cells) => cells.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',');
  const blob = new Blob(['﻿' + [line(header), ...rows.map(line)].join('\n')], { type: 'text/csv' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

/** The page's CSP refuses inline style attributes, so sizes and colours are set through the CSSOM. */
function hydrate(root) {
  $$('[data-w]', root).forEach((el) => { el.style.width = `${clamp(+el.dataset.w, 0, 100)}%`; });
  $$('[data-bg]', root).forEach((el) => { el.style.background = el.dataset.bg; });
  $$('[data-c]', root).forEach((el) => { el.style.setProperty('--kpi-c', el.dataset.c); el.style.setProperty('--kpi', el.dataset.c); });
  $$('[data-op]', root).forEach((el) => { el.style.opacity = el.dataset.op; });
}

const PALETTE = ['#8b7bff', '#2ad4c4', '#f5a524', '#f0506e', '#5b8cff', '#c06cf0', '#7ad36b', '#ff8a5b', '#6f7a94', '#e4c66b'];

function delta(now, before) {
  if (before === null || before === undefined) return '';
  if (!before && !now) return '<span class="delta flat">0%</span>';
  if (!before) return '<span class="delta up">▲ new</span>';
  const change = (now - before) / before;
  const cls = change > 0.001 ? 'up' : change < -0.001 ? 'down' : 'flat';
  return `<span class="delta ${cls}">${change > 0 ? '▲' : change < 0 ? '▼' : ''} ${Math.abs(change * 100).toFixed(0)}%</span>`;
}

function kpi({ label, value, iconName, color, foot = '', spark = null }) {
  return `
    <div class="card kpi" data-c="${color}">
      <div class="kpi-label"><i>${icon(iconName)}</i>${label}</div>
      <div class="kpi-value">${value}</div>
      <div class="kpi-foot"><span>${foot}</span>${spark ? sparkline(spark, color) : ''}</div>
    </div>`;
}

function empty(text, sub = '', iconName = 'pulse') {
  return `<div class="empty">${icon(iconName)}<strong>${text}</strong>${sub ? `<span>${sub}</span>` : ''}</div>`;
}

function skeleton(rows = 3) {
  return `<div class="grid g-4">${'<div class="card skeleton" data-h></div>'.repeat(4)}</div>` +
    `<div class="grid g-main"><div class="card skeleton"></div><div class="card skeleton"></div></div>`.repeat(Math.max(1, rows - 2));
}

// ── Charts (SVG, drawn here) ───────────────────────────────────────────────────────────────────

function niceMax(v) {
  if (v <= 0) return 1;
  const exp = 10 ** Math.floor(Math.log10(v));
  const f = v / exp;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * exp;
}

/** Monotone cubic through the points: smooth, and never dips below a zero day. */
function smoothPath(points) {
  if (points.length < 2) return points.length ? `M${points[0][0]},${points[0][1]}` : '';
  const n = points.length;
  const dx = [], dy = [], m = [], t = [];
  for (let i = 0; i < n - 1; i += 1) {
    dx[i] = points[i + 1][0] - points[i][0];
    dy[i] = points[i + 1][1] - points[i][1];
    m[i] = dy[i] / (dx[i] || 1);
  }
  t[0] = m[0];
  t[n - 1] = m[n - 2];
  for (let i = 1; i < n - 1; i += 1) t[i] = m[i - 1] * m[i] <= 0 ? 0 : (m[i - 1] + m[i]) / 2;
  for (let i = 0; i < n - 1; i += 1) {
    if (m[i] === 0) { t[i] = 0; t[i + 1] = 0; continue; }
    const a = t[i] / m[i], b = t[i + 1] / m[i], h = a * a + b * b;
    if (h > 9) { const s = 3 / Math.sqrt(h); t[i] = s * a * m[i]; t[i + 1] = s * b * m[i]; }
  }
  let d = `M${points[0][0].toFixed(1)},${points[0][1].toFixed(1)}`;
  for (let i = 0; i < n - 1; i += 1) {
    const [x0, y0] = points[i], [x1, y1] = points[i + 1], h = dx[i] / 3;
    d += ` C${(x0 + h).toFixed(1)},${(y0 + t[i] * h).toFixed(1)} ${(x1 - h).toFixed(1)},${(y1 - t[i + 1] * h).toFixed(1)} ${x1.toFixed(1)},${y1.toFixed(1)}`;
  }
  return d;
}

function sparkline(values, color, w = 96, h = 30) {
  if (!values?.length) return '';
  const max = Math.max(1, ...values);
  const pts = values.map((v, i) => [values.length === 1 ? w : (i * w) / (values.length - 1), h - 3 - ((v || 0) / max) * (h - 6)]);
  const id = uid('sg');
  const line = smoothPath(pts);
  return `<svg class="spark" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">
    <defs><linearGradient id="${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${color}" stop-opacity=".35"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></linearGradient></defs>
    <path d="${line} L${w},${h} L0,${h} Z" fill="url(#${id})" stroke="none"/>
    <path d="${line}" stroke="${color}" stroke-width="1.8" fill="none"/></svg>`;
}

/**
 * A time chart: lines with a soft area, or bars, sharing one axis; hover shows every series for the day.
 * Redraws itself on resize and when a legend entry is switched off.
 */
function timeChart(host, { labels, series, height = 280, format = fmt.n, labelOf = dayLabel }) {
  host.classList.add('chart');
  host.dir = 'ltr';
  const legend = document.createElement('div');
  legend.className = 'legend';
  const canvas = document.createElement('div');
  canvas.className = 'chart-canvas';
  host.replaceChildren(canvas, legend);
  legend.innerHTML = series.map((s, i) => `<span data-i="${i}" class="${s.off ? 'off' : ''}"><i data-bg="${s.color}"></i>${s.name}</span>`).join('');
  hydrate(legend);
  legend.addEventListener('click', (e) => {
    const item = e.target.closest('[data-i]');
    if (!item) return;
    const s = series[+item.dataset.i];
    if (series.filter((x) => !x.off).length === 1 && !s.off) return;
    s.off = !s.off;
    item.classList.toggle('off', s.off);
    draw();
  });

  function draw() {
    const W = Math.max(280, canvas.clientWidth || host.clientWidth || 600), H = height;
    const pad = { l: 46, r: 14, t: 14, b: 30 };
    const visible = series.filter((s) => !s.off);
    const max = niceMax(Math.max(1, ...visible.flatMap((s) => s.values)));
    const n = labels.length;
    const iw = W - pad.l - pad.r, ih = H - pad.t - pad.b;
    const bars = visible.filter((s) => s.type === 'bar');
    const slot = iw / Math.max(1, n);
    const x = (i) => (bars.length ? pad.l + slot * (i + 0.5) : pad.l + (n <= 1 ? iw : (i * iw) / (n - 1)));
    const y = (v) => pad.t + ih * (1 - (v || 0) / max);
    let svg = `<svg viewBox="0 0 ${W} ${H}" width="${W}" height="${H}"><defs>`;
    visible.forEach((s) => {
      s.gid = s.gid || uid('g');
      svg += `<linearGradient id="${s.gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${s.color}" stop-opacity="${s.type === 'bar' ? 0.95 : 0.3}"/><stop offset="1" stop-color="${s.color}" stop-opacity="${s.type === 'bar' ? 0.45 : 0}"/></linearGradient>`;
    });
    svg += '</defs>';
    for (let k = 0; k <= 4; k += 1) {
      const v = (max * k) / 4, yy = y(v);
      svg += `<line class="grid-line" x1="${pad.l}" x2="${W - pad.r}" y1="${yy}" y2="${yy}"/>`;
      svg += `<text class="axis" x="${pad.l - 8}" y="${yy + 3.5}" text-anchor="end">${fmt.compact(v)}</text>`;
    }
    const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(iw / 78))));
    labels.forEach((l, i) => {
      if ((n - 1 - i) % every === 0) svg += `<text class="axis" x="${x(i)}" y="${H - 8}" text-anchor="middle">${esc(labelOf(l))}</text>`;
    });
    bars.forEach((s, b) => {
      const bw = Math.max(2, Math.min(22, (slot * 0.7) / bars.length));
      s.values.forEach((v, i) => {
        const x0 = x(i) - (bw * bars.length) / 2 + b * bw, y0 = y(v), hgt = Math.max(0, pad.t + ih - y0);
        svg += `<rect x="${x0.toFixed(1)}" y="${y0.toFixed(1)}" width="${(bw - 1).toFixed(1)}" height="${hgt.toFixed(1)}" rx="${Math.min(4, bw / 3).toFixed(1)}" fill="url(#${s.gid})"/>`;
      });
    });
    visible.filter((s) => s.type !== 'bar').forEach((s) => {
      const pts = s.values.map((v, i) => [x(i), y(v)]);
      const line = smoothPath(pts);
      if (s.area !== false) svg += `<path d="${line} L${x(n - 1)},${pad.t + ih} L${x(0)},${pad.t + ih} Z" fill="url(#${s.gid})" stroke="none"/>`;
      svg += `<path d="${line}" fill="none" stroke="${s.color}" stroke-width="2.2" ${s.dash ? 'stroke-dasharray="5 5"' : ''}/>`;
    });
    svg += `<line class="hover-line" x1="0" x2="0" y1="${pad.t}" y2="${pad.t + ih}" visibility="hidden"/>`;
    svg += visible.filter((s) => s.type !== 'bar').map((s) => `<circle class="hover-dot" r="4.5" fill="${s.color}" stroke="var(--panel)" stroke-width="2" visibility="hidden"/>`).join('');
    svg += `<rect class="hover-zone" x="${pad.l}" y="${pad.t}" width="${iw}" height="${ih}" fill="transparent" stroke="none"/></svg>`;
    canvas.innerHTML = svg;
    const el = canvas.firstElementChild, zone = $('.hover-zone', el), lineEl = $('.hover-line', el), dots = $$('.hover-dot', el);
    const tip = $('#tooltip');
    const lines = visible.filter((s) => s.type !== 'bar');
    zone.addEventListener('mousemove', (e) => {
      const rect = el.getBoundingClientRect();
      const px = ((e.clientX - rect.left) / rect.width) * W;
      const i = clamp(bars.length ? Math.floor((px - pad.l) / slot) : Math.round(((px - pad.l) / iw) * (n - 1)), 0, n - 1);
      lineEl.setAttribute('x1', x(i)); lineEl.setAttribute('x2', x(i)); lineEl.setAttribute('visibility', 'visible');
      dots.forEach((d, k) => { d.setAttribute('cx', x(i)); d.setAttribute('cy', y(lines[k].values[i])); d.setAttribute('visibility', 'visible'); });
      tip.innerHTML = `<b>${esc(labelOf(labels[i]))}</b>` + visible.map((s) => `<div class="row"><span><i data-bg="${s.color}"></i>${s.name}</span><strong>${format(s.values[i])}</strong></div>`).join('');
      hydrate(tip);
      tip.hidden = false;
      const tx = e.clientX + 16 + tip.offsetWidth > innerWidth ? e.clientX - tip.offsetWidth - 16 : e.clientX + 16;
      tip.style.left = `${tx}px`;
      tip.style.top = `${Math.max(8, e.clientY - tip.offsetHeight / 2)}px`;
    });
    zone.addEventListener('mouseleave', () => {
      tip.hidden = true;
      lineEl.setAttribute('visibility', 'hidden');
      dots.forEach((d) => d.setAttribute('visibility', 'hidden'));
    });
  }
  draw();
  const observer = new ResizeObserver(() => draw());
  observer.observe(host);
  state.observers.push(observer);
}

function donut(items, { label = 'کل', format = fmt.n, labelOf = (l) => l } = {}) {
  const total = sum(items.map((i) => i.value));
  if (!total) return empty('هنوز داده‌ای نیست');
  const r = 58, c = 2 * Math.PI * r;
  let offset = 0;
  const arcs = items.map((item, i) => {
    const len = (item.value / total) * c;
    const arc = `<circle cx="75" cy="75" r="${r}" fill="none" stroke="${PALETTE[i % PALETTE.length]}" stroke-width="16" stroke-dasharray="${Math.max(0, len - 2)} ${c}" stroke-dashoffset="${-offset}"/>`;
    offset += len;
    return arc;
  }).join('');
  const rows = items.map((item, i) => `
    <div class="split-row"><i data-bg="${PALETTE[i % PALETTE.length]}"></i><span title="${esc(labelOf(item.label))}">${esc(labelOf(item.label))}</span><strong>${format(item.value)}</strong><em>${fmt.pct(item.value / total, 0)}</em></div>`).join('');
  return `<div class="donut-wrap"><div class="donut"><svg viewBox="0 0 150 150"><circle cx="75" cy="75" r="${r}" fill="none" stroke="var(--panel-3)" stroke-width="16"/>${arcs}</svg>
    <div class="center"><strong>${fmt.compact(total)}</strong><span>${label}</span></div></div><div class="split-list">${rows}</div></div>`;
}

function barList(items, { labelOf = (l) => esc(l), format = fmt.n, max = null, colorOf = null } = {}) {
  if (!items?.length) return empty('هنوز داده‌ای نیست');
  const top = max ?? Math.max(1, ...items.map((i) => i.value));
  const total = sum(items.map((i) => i.value));
  return `<div class="bars">${items.map((item, i) => `
    <div class="bar-row"><div class="bar-top"><span>${labelOf(item.label, item)}</span><span><strong>${format(item.value)}</strong> <span class="faint num">${total ? fmt.pct(item.value / total, 0) : ''}</span></span></div>
    <div class="bar-track"><div class="bar-fill" data-w="${(item.value / top) * 100}" ${colorOf ? `data-bg="${colorOf(item, i)}"` : ''}></div></div></div>`).join('')}</div>`;
}

function heatmap(matrix) {
  // Python's weekday: Monday is 0. Shown from Saturday, as the week runs in Iran.
  const names = ['دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه', 'یکشنبه'];
  const order = [5, 6, 0, 1, 2, 3, 4];
  const max = Math.max(1, ...matrix.flat());
  let html = '<div class="heat"><span></span>';
  for (let h = 0; h < 24; h += 1) html += `<span class="hr">${h % 3 === 0 ? h : ''}</span>`;
  order.forEach((d) => {
    html += `<span class="lbl">${names[d]}</span>`;
    for (let h = 0; h < 24; h += 1) {
      const v = matrix[d]?.[h] || 0;
      const a = v ? 0.15 + 0.85 * (v / max) : 0;
      html += `<span class="cell" title="${names[d]} ${h}:00 — ${v}" ${v ? `data-bg="rgba(139,123,255,${a.toFixed(2)})"` : ''}></span>`;
    }
  });
  html += '</div><div class="heat-scale">کم' + [0.15, 0.4, 0.65, 1].map((a) => `<i data-bg="rgba(139,123,255,${a})"></i>`).join('') + 'زیاد</div>';
  return html;
}

function gauge(value, label, color = '#8b7bff') {
  const r = 52, c = Math.PI * r;
  const v = value === null || value === undefined ? 0 : clamp(value, 0, 1);
  return `<div class="gauge"><svg viewBox="0 0 130 76"><path d="M13,68 A52,52 0 0 1 117,68" stroke="var(--panel-3)" fill="none" stroke-linecap="round"/>
    <path d="M13,68 A52,52 0 0 1 117,68" stroke="${color}" fill="none" stroke-linecap="round" stroke-dasharray="${(v * c).toFixed(1)} ${c}"/></svg>
    <strong>${value === null || value === undefined ? '—' : fmt.pct(value, 0)}</strong><span>${label}</span></div>`;
}

// ── Shell ──────────────────────────────────────────────────────────────────────────────────────

const VIEWS = {
  overview: { title: 'داشبورد', sub: 'نمای کلی پرو چارت در یک نگاه', render: renderOverview, refresh: 60 },
  downloads: { title: 'دانلودها و بازدید', sub: 'از گزارش دسترسی Caddy؛ هر نشانی در هر روز یک‌بار', render: renderDownloads },
  audience: { title: 'مخاطبان و ماندگاری', sub: 'کاربران فعال، بازگشت و ساعت‌های حضور', render: renderAudience },
  installs: { title: 'نصب‌ها', sub: 'هر نصب با شناسهٔ ناشناس؛ هیچ اطلاعات شخصی نگه داشته نمی‌شود', render: renderInstalls },
  messages: { title: 'پیام‌ها و اعلان‌ها', sub: 'اعلان همگانی، پیشنهاد تخفیف، اطلاعیه و هشدار', render: renderMessages },
  coupons: { title: 'کدهای تخفیف', sub: 'ساخت، محدودیت و پیگیری استفاده', render: renderCoupons },
  config: { title: 'تنظیمات از راه دور', sub: 'بدون انتشار نسخهٔ جدید، رفتار برنامه را تنظیم کنید', render: renderConfig },
  release: { title: 'انتشار نسخه', sub: 'نسخهٔ عرضه‌شده و میزان به‌روزرسانی کاربران', render: renderRelease },
  health: { title: 'سلامت سرویس‌ها', sub: 'پایش خودکار هر ۵ دقیقه', render: renderHealth, refresh: 60 },
  audit: { title: 'گزارش فعالیت', sub: 'هر ورود و هر تغییر در این کنسول', render: renderAudit },
  account: { title: 'حساب و امنیت', sub: 'رمز عبور، تأیید دومرحله‌ای و نشست‌ها', render: renderAccount },
};

function showLogin() {
  $('#shell').hidden = true;
  $('#login').hidden = false;
  $('#step-code').hidden = true;
  $('#step-credentials').hidden = false;
  $('#login-error').textContent = '';
  state.csrf = null;
  clearView();
  setTimeout(() => $('#login-form [name=username]').focus(), 50);
}

async function enter() {
  const me = await api('session');
  state.csrf = me.csrf;
  state.me = me;
  $('#login').hidden = true;
  $('#shell').hidden = false;
  $('#me-name').textContent = me.username;
  $('#me-avatar').textContent = (me.username[0] || '?').toUpperCase();
  $('#me-meta').textContent = me.totp ? 'مالک · تأیید دومرحله‌ای فعال' : 'مالک پروژه';
  route();
}

function clearView() {
  state.timers.forEach(clearInterval);
  state.timers = [];
  state.observers.forEach((o) => o.disconnect());
  state.observers = [];
  $('#tooltip').hidden = true;
}

async function route() {
  if (!state.csrf) return;
  const name = (location.hash.replace(/^#\/?/, '') || 'overview').split('?')[0];
  const view = VIEWS[name] ? name : 'overview';
  clearView();
  state.view = view;
  $$('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.view === view));
  $('#view-title').textContent = VIEWS[view].title;
  $('#view-sub').textContent = VIEWS[view].sub;
  $('#shell').classList.remove('nav-open');
  await paint(view, false);
}

/**
 * Every paint gets a fresh element, so the listeners a view hangs on it go with it. A quiet repaint
 * (the auto-refresh) builds the new one behind the old and swaps them, so nothing blinks.
 */
async function paint(view, quiet) {
  const old = $('#view');
  const host = document.createElement('div');
  host.className = quiet ? 'view quiet' : 'view';
  host.setAttribute('aria-live', 'polite');
  if (quiet) {
    host.hidden = true;
    old.removeAttribute('id');
    old.before(host);
  } else {
    old.replaceWith(host);
    host.innerHTML = skeleton(4);
    hydrateSkeleton(host);
  }
  host.id = 'view';
  try {
    await VIEWS[view].render(host);
  } catch (error) {
    if (quiet) { host.remove(); old.id = 'view'; return; }
    if (error.code !== 'signed_out') host.innerHTML = `<div class="card">${empty('بارگذاری نشد', errorText(error), 'alert')}</div>`;
  }
  if (quiet) { old.remove(); host.hidden = false; }
  if (VIEWS[view].refresh) {
    state.timers.push(setInterval(() => { if (!document.hidden && state.view === view && !$('#modal-root').children.length) silentRefresh(); }, VIEWS[view].refresh * 1000));
  }
}

function hydrateSkeleton(host) {
  $$('.skeleton', host).forEach((el) => { el.style.minHeight = el.hasAttribute('data-h') ? '130px' : '300px'; });
}

async function silentRefresh() {
  const scroll = scrollY;
  clearView();
  await paint(state.view, true);
  scrollTo(0, scroll);
}

function tickClock() {
  const d = new Date();
  $('#clock').textContent = `${secondsFmt.format(d)} · تهران`;
}

function greeting() {
  const hour = +tehranParts(Date.now() / 1000).hour;
  if (hour < 5) return 'شب بخیر';
  if (hour < 12) return 'صبح بخیر';
  if (hour < 17) return 'روز بخیر';
  if (hour < 21) return 'عصر بخیر';
  return 'شب بخیر';
}

// ── Overview ───────────────────────────────────────────────────────────────────────────────────

async function renderOverview(host) {
  const d = await api('overview');
  const k = d.kpis;
  const health = d.health || [];
  const down = health.filter((h) => !h.ok);
  updateBadges(k.live_messages, down.length);
  host.innerHTML = `
    <section class="hero">
      <div>
        <h2>${greeting()}، ${esc(state.me.username)}</h2>
        <p>${jalaliFull.format(new Date())} · ${down.length ? `<span class="pill bad">${fmt.fa(down.length)} سرویس مشکل دارد</span>` : '<span class="pill good">همهٔ سرویس‌ها سالم‌اند</span>'}</p>
      </div>
      <div class="hero-stats">
        <div><strong>${fmt.n(k.live_now)}</strong><span>آنلاین در ۳۰ دقیقهٔ اخیر</span></div>
        <div><strong>${fmt.n(k.installs_total)}</strong><span>کل نصب‌های ثبت‌شده</span></div>
        <div><strong>${fmt.n(k.downloads_total)}</strong><span>کل دانلودها</span></div>
        <div><strong>${fmt.n(k.live_messages)}</strong><span>پیام فعال</span></div>
      </div>
    </section>
    <div class="grid g-4">
      ${kpi({ label: 'دانلود امروز', value: fmt.n(k.downloads_today), iconName: 'download', color: '#8b7bff', foot: `${delta(k.downloads_today, k.downloads_yesterday)} نسبت به دیروز`, spark: d.series.downloads.slice(-14) })}
      ${kpi({ label: 'کاربران فعال امروز', value: fmt.n(k.dau), iconName: 'users', color: '#2ad4c4', foot: `${delta(k.dau, k.dau_yesterday)} نسبت به دیروز`, spark: d.series.dau.slice(-14) })}
      ${kpi({ label: 'نصب جدید (۷ روز)', value: fmt.n(k.new_7d), iconName: 'phone', color: '#f5a524', foot: `امروز ${fmt.n(k.new_today)}`, spark: d.series.new_installs.slice(-14) })}
      ${kpi({ label: 'بازدید وب امروز', value: fmt.n(k.visits_today), iconName: 'globe', color: '#5b8cff', foot: `۷ روز: <b class="num">${fmt.n(k.visits_7d)}</b>`, spark: d.series.visits.slice(-14) })}
    </div>
    <div class="grid g-4">
      ${kpi({ label: 'دانلود ۷ روز', value: fmt.n(k.downloads_7d), iconName: 'bolt', color: '#c06cf0', foot: `${delta(k.downloads_7d, k.downloads_prev_7d)} نسبت به هفتهٔ قبل` })}
      ${kpi({ label: 'فعال هفتگی (WAU)', value: fmt.n(k.wau), iconName: 'users', color: '#7ad36b', foot: `ماهانه (MAU): <b class="num">${fmt.n(k.mau)}</b>` })}
      ${kpi({ label: 'چسبندگی DAU/MAU', value: k.mau ? fmt.pct(k.dau / k.mau, 0) : '—', iconName: 'pulse', color: '#ff8a5b', foot: 'بالای ۲۰٪ یعنی عادت روزانه' })}
      ${kpi({ label: 'روی آخرین نسخه', value: k.adoption === null ? '—' : fmt.pct(k.adoption, 0), iconName: 'rocket', color: '#2ad4c4', foot: `بررسی به‌روزرسانی امروز: <b class="num">${fmt.n(k.update_checks_today)}</b>` })}
    </div>
    <div class="grid g-main">
      <div class="card"><div class="card-head"><div><h3>روند ۳۰ روز</h3><p>دانلود، کاربر فعال، بازدید وب و نصب جدید</p></div></div><div id="ov-chart"></div></div>
      <div class="card"><div class="card-head"><div><h3>سلامت سرویس‌ها</h3><p>آخرین بررسی خودکار</p></div><a class="btn sm ghost" href="#/health">جزئیات</a></div>
        ${health.length ? `<div class="bars">${health.map((h) => `<div class="svc-top"><span><span class="status-dot ${h.ok ? 'ok' : 'bad'}"></span>&nbsp; ${esc(h.name)}</span><span class="num faint">${h.ok ? `${h.ms} ms` : esc(h.status || h.error || 'down')}</span></div>`).join('')}</div>` : empty('هنوز بررسی نشده', 'اولین بررسی چند دقیقه پس از راه‌اندازی انجام می‌شود')}
      </div>
    </div>
    <div class="grid g-3">
      <div class="card"><div class="card-head"><h3>پلتفرم و فروشگاه</h3></div>${donut(d.platforms, { label: 'فعال ۳۰ روز', labelOf: platformLabel })}</div>
      <div class="card"><div class="card-head"><h3>نسخه‌ها</h3></div>${donut(d.versions, { label: 'نصب فعال' })}</div>
      <div class="card"><div class="card-head"><h3>کشورها</h3></div>${barList(d.countries, { labelOf: country })}</div>
    </div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><div><h3>نسخهٔ عرضه‌شده</h3><p>کانال به‌روزرسانی /api/app/latest</p></div><a class="btn sm ghost" href="#/release">جزئیات</a></div>
        ${d.latest ? `<div class="release-card"><div class="release-badge"><strong>${esc(d.latest.version_name)}</strong><span>${fmt.n(d.latest.version_code)}</span></div>
          <div><p class="muted">${esc((d.latest.notes_fa || '').slice(0, 220))}${(d.latest.notes_fa || '').length > 220 ? '…' : ''}</p></div></div>` : empty('فایل نسخه خوانده نشد', 'ADMIN_LATEST_JSON را بررسی کنید', 'file')}
      </div>
      <div class="card"><div class="card-head"><h3>آخرین فعالیت‌ها</h3><a class="btn sm ghost" href="#/audit">همه</a></div>
        ${d.audit.length ? `<div class="bars">${d.audit.map((a) => `<div class="svc-top"><span>${actionLabel(a.action)}</span><span class="faint">${ago(a.at)}</span></div>`).join('')}</div>` : empty('فعالیتی نیست')}
      </div>
    </div>`;
  hydrate(host);
  timeChart($('#ov-chart'), {
    labels: d.days,
    series: [
      { name: 'دانلود', color: '#8b7bff', values: d.series.downloads },
      { name: 'کاربر فعال', color: '#2ad4c4', values: d.series.dau },
      { name: 'بازدید وب', color: '#5b8cff', values: d.series.visits, area: false, dash: true },
      { name: 'نصب جدید', color: '#f5a524', values: d.series.new_installs, area: false },
    ],
  });
}

function updateBadges(live, down) {
  if (live !== null) {
    const badge = $('#badge-live');
    badge.hidden = !live;
    badge.textContent = live || '';
  }
  if (down !== null) $('#badge-health').hidden = !down;
}

// ── Downloads ──────────────────────────────────────────────────────────────────────────────────

async function renderDownloads(host) {
  const days = +(sessionStorage.getItem('dl-days') || 30);
  const d = await api(`downloads?days=${days}`);
  const unique = sum(d.unique), hits = sum(d.hits);
  host.innerHTML = `
    <div class="toolbar"><div class="segmented" id="dl-range">${[7, 30, 90, 365].map((n) => `<button data-days="${n}" class="${n === days ? 'on' : ''}">${n === 365 ? '۱ سال' : `${fmt.fa(n)} روز`}</button>`).join('')}</div>
      <span class="grow"></span><button class="btn sm" id="dl-csv">${icon('csv')}خروجی CSV</button></div>
    <div class="grid g-4">
      ${kpi({ label: 'دانلود یکتا در بازه', value: fmt.n(unique), iconName: 'download', color: '#8b7bff', foot: `میانگین روزانه <b class="num">${(unique / d.days.length).toFixed(1)}</b>`, spark: d.unique })}
      ${kpi({ label: 'کل دانلودها (همیشه)', value: fmt.n(d.total), iconName: 'bolt', color: '#2ad4c4', foot: `درخواست خام در بازه: <b class="num">${fmt.n(hits)}</b>` })}
      ${kpi({ label: 'حجم ارسال‌شده', value: fmt.bytes(d.bytes), iconName: 'server', color: '#f5a524', foot: 'از روی اندازهٔ پاسخ‌ها' })}
      ${kpi({ label: 'بازدید یکتای وب', value: fmt.n(sum(d.visits)), iconName: 'globe', color: '#5b8cff', foot: `بررسی به‌روزرسانی: <b class="num">${fmt.n(sum(d.update_checks))}</b>`, spark: d.visits })}
    </div>
    <div class="card"><div class="card-head"><div><h3>دانلود روزانه</h3><p>یکتا (هر نشانی در روز یک‌بار؛ ادامهٔ دانلود قطع‌شده دوباره شمرده نمی‌شود) و درخواست خام</p></div></div><div id="dl-chart"></div></div>
    <div class="card"><div class="card-head"><div><h3>وب و به‌روزرسانی</h3><p>بازدید یکتای pro-chart.com و بررسی نسخهٔ جدید از داخل برنامه</p></div></div><div id="dl-chart-2"></div></div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><h3>بر اساس فایل / نسخه</h3></div>${barList(d.files, { labelOf: (l, item) => `<span class="pill accent plain num">${esc(item.version)}</span> <span class="faint num">${esc(l)}</span>` })}</div>
      <div class="card"><div class="card-head"><h3>کشور دانلودکنندگان</h3></div>${barList(d.countries, { labelOf: country })}</div>
    </div>`;
  hydrate(host);
  timeChart($('#dl-chart'), { labels: d.days, series: [
    { name: 'دانلود یکتا', color: '#8b7bff', values: d.unique, type: 'bar' },
    { name: 'درخواست خام', color: '#2ad4c4', values: d.hits, area: false },
  ] });
  timeChart($('#dl-chart-2'), { labels: d.days, height: 220, series: [
    { name: 'بازدید وب', color: '#5b8cff', values: d.visits },
    { name: 'بررسی به‌روزرسانی', color: '#f5a524', values: d.update_checks, area: false },
  ] });
  $('#dl-range').addEventListener('click', (e) => {
    const b = e.target.closest('[data-days]');
    if (!b) return;
    sessionStorage.setItem('dl-days', b.dataset.days);
    route();
  });
  $('#dl-csv').addEventListener('click', () => downloadCsv(`pro-chart-downloads-${days}d.csv`, ['day', 'unique_downloads', 'requests', 'web_visits', 'update_checks'],
    d.days.map((day, i) => [day, d.unique[i], d.hits[i], d.visits[i], d.update_checks[i]])));
}

// ── Audience ───────────────────────────────────────────────────────────────────────────────────

async function renderAudience(host) {
  const days = +(sessionStorage.getItem('au-days') || 30);
  const d = await api(`audience?days=${days}`);
  const dau = d.dau.at(-1) || 0, wau = d.wau.at(-1) || 0;
  const avg = d.dau.length ? sum(d.dau) / d.dau.length : 0;
  const peak = Math.max(0, ...d.dau);
  host.innerHTML = `
    <div class="toolbar"><div class="segmented" id="au-range">${[30, 90, 180].map((n) => `<button data-days="${n}" class="${n === days ? 'on' : ''}">${fmt.fa(n)} روز</button>`).join('')}</div></div>
    <div class="grid g-4">
      ${kpi({ label: 'فعال امروز (DAU)', value: fmt.n(dau), iconName: 'users', color: '#2ad4c4', spark: d.dau.slice(-14) })}
      ${kpi({ label: 'فعال ۷ روز (WAU)', value: fmt.n(wau), iconName: 'users', color: '#8b7bff', spark: d.wau.slice(-14) })}
      ${kpi({ label: 'میانگین روزانه', value: avg.toFixed(1), iconName: 'pulse', color: '#f5a524', foot: `اوج: <b class="num">${fmt.n(peak)}</b>` })}
      ${kpi({ label: 'چسبندگی DAU/WAU', value: wau ? fmt.pct(dau / wau, 0) : '—', iconName: 'bolt', color: '#c06cf0', foot: 'سهم کاربرانی که هر روز می‌آیند' })}
    </div>
    <div class="grid g-main">
      <div class="card"><div class="card-head"><div><h3>کاربران فعال</h3><p>روزانه و پنجرهٔ غلتان ۷ روزه</p></div></div><div id="au-chart"></div></div>
      <div class="card"><div class="card-head"><div><h3>ماندگاری</h3><p>از نصب‌های تازه، چند درصد برگشتند</p></div></div>
        <div class="grid g-3">${gauge(d.retention['1'], 'روز ۱', '#2ad4c4')}${gauge(d.retention['7'], 'روز ۷', '#8b7bff')}${gauge(d.retention['30'], 'روز ۳۰', '#f5a524')}</div>
        <div class="callout">${icon('bolt')}<span>معیار مقایسه برای برنامه‌های مالی: روز ۱ حدود ۲۵٪، روز ۷ حدود ۱۲٪ و روز ۳۰ حدود ۶٪.</span></div>
      </div>
    </div>
    <div class="card"><div class="card-head"><div><h3>ساعت‌های حضور</h3><p>روز هفته × ساعت (به وقت تهران) — بهترین زمان ارسال اعلان را اینجا ببینید</p></div><span class="pill info" id="best-hour"></span></div>${heatmap(d.heat)}</div>
    <div class="grid g-3">
      <div class="card"><div class="card-head"><h3>پلتفرم</h3></div>${donut(d.platforms, { labelOf: platformLabel })}</div>
      <div class="card"><div class="card-head"><h3>فروشگاه</h3></div>${donut(d.flavours, { labelOf: platformLabel })}</div>
      <div class="card"><div class="card-head"><h3>زبان</h3></div>${donut(d.langs, { labelOf: platformLabel })}</div>
    </div>
    <div class="grid g-3">
      <div class="card"><div class="card-head"><h3>نسخهٔ برنامه</h3></div>${barList(d.versions, { labelOf: (l) => `<span class="num">${esc(l)}</span>` })}</div>
      <div class="card"><div class="card-head"><h3>نسخهٔ اندروید</h3></div>${barList(d.sdks.filter((s) => s.label !== '0'), { labelOf: (l) => `<span class="num">${androidOf(+l)}</span>` })}</div>
      <div class="card"><div class="card-head"><h3>کشور</h3></div>${barList(d.countries, { labelOf: country })}</div>
    </div>`;
  hydrate(host);
  const best = bestHour(d.heat);
  $('#best-hour').textContent = best ? `پرترافیک‌ترین ساعت: ${best}` : 'هنوز داده‌ای نیست';
  timeChart($('#au-chart'), { labels: d.days, series: [
    { name: 'DAU', color: '#2ad4c4', values: d.dau },
    { name: 'WAU', color: '#8b7bff', values: d.wau, area: false },
  ] });
  $('#au-range').addEventListener('click', (e) => {
    const b = e.target.closest('[data-days]');
    if (!b) return;
    sessionStorage.setItem('au-days', b.dataset.days);
    route();
  });
}

function bestHour(heat) {
  const byHour = Array.from({ length: 24 }, (_, h) => sum(heat.map((row) => row[h])));
  const max = Math.max(...byHour);
  if (!max) return null;
  const h = byHour.indexOf(max);
  return `${String(h).padStart(2, '0')}:00–${String((h + 1) % 24).padStart(2, '0')}:00`;
}

// ── Installs ───────────────────────────────────────────────────────────────────────────────────

async function renderInstalls(host) {
  const filters = JSON.parse(sessionStorage.getItem('in-filters') || '{}');
  const offset = +(filters.offset || 0);
  const params = new URLSearchParams({ limit: 50, offset });
  ['q', 'platform', 'flavour', 'lang', 'country', 'version'].forEach((k) => filters[k] && params.set(k, filters[k]));
  const d = await api(`installs?${params}`);
  const select = (name, options) => `<select class="input" name="${name}"><option value="">${options[0]}</option>${options.slice(1).map(([v, l]) => `<option value="${v}" ${filters[name] === v ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
  host.innerHTML = `
    <div class="card flush">
      <div class="card-head"><div><h3>${fmt.fa(d.total)} نصب</h3><p>مرتب بر اساس آخرین فعالیت</p></div>
        <a class="btn sm" href="/admin/api/installs.csv" download>${icon('csv')}خروجی CSV همه</a></div>
      <form class="toolbar" id="in-filter" autocomplete="off">
        <label class="search grow">${icon('search')}<input class="input" name="q" placeholder="جستجوی شناسه، نسخه یا کشور" value="${esc(filters.q || '')}"></label>
        ${select('platform', ['همهٔ پلتفرم‌ها', ['android', 'اندروید'], ['web', 'وب']])}
        ${select('flavour', ['همهٔ فروشگاه‌ها', ['direct', 'مستقیم'], ['bazaar', 'بازار']])}
        ${select('lang', ['همهٔ زبان‌ها', ['fa', 'فارسی'], ['en', 'English']])}
        <input class="input" name="country" placeholder="کشور (IR)" maxlength="2" dir="ltr" value="${esc(filters.country || '')}">
        <button class="btn sm primary">فیلتر</button>
        <button class="btn sm ghost" type="button" id="in-reset">پاک کردن</button>
      </form>
      <div class="table-wrap"><table>
        <thead><tr><th>شناسه</th><th>پلتفرم</th><th>نسخه</th><th>اندروید</th><th>زبان</th><th>کشور</th><th>منطقهٔ زمانی</th><th>اولین بار</th><th>آخرین فعالیت</th><th>check-in</th></tr></thead>
        <tbody>${d.rows.map((r) => `<tr>
          <td><span class="mono faint">${esc(r.id_hash)}</span></td>
          <td>${r.platform === 'android' ? `<span class="pill good plain">${platformLabel(r.platform)} · ${platformLabel(r.flavour)}</span>` : `<span class="pill info plain">${platformLabel(r.platform)}</span>`}</td>
          <td class="num">${esc(r.version || '—')}</td>
          <td class="num">${androidOf(r.sdk)}</td>
          <td>${platformLabel(r.lang)}</td>
          <td>${country(r.country)}</td>
          <td class="num faint">${esc(r.tz || '—')}</td>
          <td>${when(r.first_seen)}</td>
          <td>${ago(r.last_seen)}</td>
          <td class="num">${fmt.n(r.hellos)}</td></tr>`).join('')}</tbody></table>
        ${d.rows.length ? '' : empty('نصبی پیدا نشد', 'نسخهٔ 5.30.0 به بعد هر چند ساعت یک‌بار خودش را معرفی می‌کند', 'phone')}
      </div>
      <div class="pager"><span>${fmt.fa(Math.min(d.total, offset + 1))}–${fmt.fa(Math.min(d.total, offset + d.rows.length))} از ${fmt.fa(d.total)}</span>
        <span><button class="btn sm" id="in-prev" ${offset ? '' : 'disabled'}>قبلی</button> <button class="btn sm" id="in-next" ${offset + 50 < d.total ? '' : 'disabled'}>بعدی</button></span></div>
    </div>`;
  const save = (next) => { sessionStorage.setItem('in-filters', JSON.stringify(next)); route(); };
  $('#in-filter').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    f.country = (f.country || '').toUpperCase();
    save({ ...f, offset: 0 });
  });
  $('#in-reset').addEventListener('click', () => save({}));
  $('#in-prev').addEventListener('click', () => save({ ...filters, offset: Math.max(0, offset - 50) }));
  $('#in-next').addEventListener('click', () => save({ ...filters, offset: offset + 50 }));
}

// ── Messages ───────────────────────────────────────────────────────────────────────────────────

const KINDS = {
  announcement: { label: 'اطلاعیه', icon: 'megaphone', hint: 'خبر، قابلیت تازه یا رویداد' },
  offer: { label: 'پیشنهاد تخفیف', icon: 'gift', hint: 'همراه با کد تخفیف' },
  update: { label: 'به‌روزرسانی', icon: 'rocket', hint: 'دعوت به نصب نسخهٔ تازه' },
  maintenance: { label: 'تعمیرات', icon: 'wrench', hint: 'قطعی یا کندی برنامه‌ریزی‌شده' },
  alert: { label: 'هشدار مهم', icon: 'alert', hint: 'رویداد فوری بازار یا امنیتی' },
};
const TEMPLATES = {
  announcement: { title_fa: 'قابلیت تازه در پرو چارت', body_fa: 'از امروز می‌توانید …', title_en: 'New in Pro Chart', body_en: 'Starting today you can …', cta_fa: 'ببینید', cta_en: 'Take a look' },
  offer: { title_fa: 'تخفیف ویژهٔ پرمیوم', body_fa: 'فقط تا پایان این هفته، اشتراک پرمیوم پرو چارت با تخفیف. کد را در صفحهٔ پرمیوم وارد کنید.', title_en: 'Premium, on sale', body_en: 'Until the end of this week, Pro Chart Premium is discounted. Enter the code on the Premium screen.', cta_fa: 'دریافت تخفیف', cta_en: 'Get the deal' },
  update: { title_fa: 'نسخهٔ تازهٔ پرو چارت آماده است', body_fa: 'برای سرعت بیشتر و قابلیت‌های تازه، برنامه را به‌روز کنید.', title_en: 'A new Pro Chart is ready', body_en: 'Update for more speed and the newest features.', cta_fa: 'به‌روزرسانی', cta_en: 'Update', link: 'https://pro-chart.com/download/' },
  maintenance: { title_fa: 'به‌روزرسانی سرورها', body_fa: 'امشب از ساعت ۲ تا ۳ بامداد ممکن است قیمت‌ها با تأخیر برسند.', title_en: 'Server maintenance', body_en: 'Tonight between 02:00 and 03:00 Tehran time prices may arrive late.', cta_fa: '', cta_en: '' },
  alert: { title_fa: 'هشدار مهم', body_fa: '', title_en: 'Important', body_en: '', cta_fa: 'بیشتر', cta_en: 'More' },
};
const LINKS = [
  ['', 'فقط باز کردن برنامه'],
  ['coinepro://market/BTCUSDT', 'نمودار بیت‌کوین'],
  ['coinepro://market/XAUUSD', 'نمودار طلا'],
  ['coinepro://news', 'اخبار'],
  ['coinepro://calendar', 'تقویم اقتصادی'],
  ['coinepro://screener', 'غربالگر'],
  ['coinepro://activity', 'فعالیت‌ها'],
  ['https://pro-chart.com/download/', 'صفحهٔ دانلود'],
  ['https://pro-chart.com/terminal/', 'نسخهٔ وب'],
];

function messageState(m) {
  return { live: ['good live', 'در حال ارسال'], scheduled: ['info', 'زمان‌بندی‌شده'], draft: ['plain', 'پیش‌نویس'], ended: ['plain', 'پایان‌یافته'], cancelled: ['bad', 'لغو شده'] }[m.state] || ['plain', m.state];
}

function audienceText(a) {
  const parts = [];
  if (a.platforms?.length) parts.push(a.platforms.map(platformLabel).join('، '));
  if (a.flavours?.length) parts.push(a.flavours.map(platformLabel).join('، '));
  if (a.langs?.length) parts.push(a.langs.map(platformLabel).join('، '));
  if (a.countries?.length) parts.push(a.countries.join(', '));
  if (a.min_version_code) parts.push(`از کد ${a.min_version_code}`);
  if (a.max_version_code) parts.push(`تا کد ${a.max_version_code}`);
  return parts.length ? parts.join(' · ') : 'همهٔ کاربران';
}

async function renderMessages(host) {
  const [d, coupons] = await Promise.all([api('messages'), api('coupons')]);
  state.cache.coupons = coupons.coupons;
  const filter = sessionStorage.getItem('msg-filter') || 'all';
  const list = d.messages.filter((m) => filter === 'all' || m.state === filter);
  const delivered = sum(d.messages.map((m) => m.stats.delivered));
  const opened = sum(d.messages.map((m) => m.stats.opened));
  const live = d.messages.filter((m) => m.state === 'live').length;
  updateBadges(live, null);
  host.innerHTML = `
    <div class="grid g-4">
      ${kpi({ label: 'پیام در حال ارسال', value: fmt.n(live), iconName: 'bell', color: '#8b7bff', foot: `زمان‌بندی‌شده: <b class="num">${fmt.n(d.messages.filter((m) => m.state === 'scheduled').length)}</b>` })}
      ${kpi({ label: 'تحویل‌شده (کل)', value: fmt.n(delivered), iconName: 'check', color: '#2ad4c4', foot: 'اعلان روی گوشی نمایش داده شد' })}
      ${kpi({ label: 'باز شده (کل)', value: fmt.n(opened), iconName: 'eye', color: '#f5a524', foot: 'کاربر روی اعلان زد' })}
      ${kpi({ label: 'نرخ باز شدن', value: delivered ? fmt.pct(opened / delivered) : '—', iconName: 'bolt', color: '#c06cf0', foot: 'میانگین همهٔ پیام‌ها' })}
    </div>
    <div class="toolbar">
      <div class="segmented" id="msg-filter">${[['all', 'همه'], ['live', 'در حال ارسال'], ['scheduled', 'زمان‌بندی'], ['draft', 'پیش‌نویس'], ['ended', 'پایان‌یافته'], ['cancelled', 'لغو شده']]
        .map(([k, l]) => `<button data-f="${k}" class="${k === filter ? 'on' : ''}">${l}</button>`).join('')}</div>
      <span class="grow"></span>
      <button class="btn primary" id="msg-new">${icon('plus')}پیام تازه</button>
    </div>
    <div class="msg-list">${list.length ? list.map(messageCard).join('') : `<div class="card">${empty('پیامی نیست', 'اولین اعلان همگانی را بسازید', 'bell')}</div>`}</div>`;
  hydrate(host);
  $('#msg-filter').addEventListener('click', (e) => {
    const b = e.target.closest('[data-f]');
    if (!b) return;
    sessionStorage.setItem('msg-filter', b.dataset.f);
    route();
  });
  $('#msg-new').addEventListener('click', () => openComposer());
  host.addEventListener('click', async (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) return;
    const id = +b.dataset.id;
    const m = d.messages.find((x) => x.id === id);
    if (b.dataset.act === 'edit') openComposer(m);
    if (b.dataset.act === 'dup') {
      const r = await api(`messages/${id}/duplicate`, { method: 'POST', body: {} });
      toast('نسخهٔ پیش‌نویس ساخته شد');
      const fresh = (await api('messages')).messages.find((x) => x.id === r.id);
      openComposer(fresh);
      route();
    }
    if (b.dataset.act === 'cancel' && await confirmBox('لغو پیام', 'این پیام دیگر به هیچ نصبی تحویل نمی‌شود. اعلان‌هایی که قبلاً رسیده‌اند می‌مانند.', { danger: true, ok: 'لغو پیام' })) {
      await api(`messages/${id}/cancel`, { method: 'POST', body: {} });
      toast('پیام لغو شد');
      route();
    }
  });
}

function messageCard(m) {
  const [cls, label] = messageState(m);
  const kind = KINDS[m.kind] || KINDS.announcement;
  const rate = m.stats.open_rate;
  const editable = m.state === 'draft' || m.state === 'scheduled';
  return `<article class="msg">
    <div class="msg-icon k-${m.kind}">${icon(kind.icon)}</div>
    <div>
      <h4>${esc(m.title_fa || m.title_en)} <span class="pill ${cls}">${label}</span>${m.priority === 'high' ? '<span class="pill warn plain">فوری</span>' : ''}${m.notify ? '' : '<span class="pill plain">بی‌صدا (فقط درون برنامه)</span>'}</h4>
      <p>${esc(m.body_fa || m.body_en)}</p>
      <div class="msg-meta">
        <span>${icon('users')}${esc(audienceText(m.audience))}</span>
        <span>${icon('clock')}${when(m.starts_at)}${m.ends_at ? ` ← ${when(m.ends_at)}` : ''}</span>
        ${m.coupon ? `<span>${icon('ticket')}<span class="code-tag">${esc(m.coupon)}</span></span>` : ''}
        ${m.link ? `<span>${icon('arrow')}<span class="mono">${esc(m.link)}</span></span>` : ''}
        <span>${icon('edit')}${esc(m.created_by || '')} · ${ago(m.created_at)}</span>
      </div>
    </div>
    <div class="msg-stats">
      <div class="svc-figs"><span>تحویل <b>${fmt.n(m.stats.delivered)}</b></span><span>باز <b>${fmt.n(m.stats.opened)}</b></span><span>نرخ <b>${rate === null ? '—' : fmt.pct(rate, 0)}</b></span></div>
      <div class="bar-track"><div class="bar-fill" data-w="${(rate || 0) * 100}"></div></div>
      <div class="msg-actions">
        ${editable ? `<button class="btn sm" data-act="edit" data-id="${m.id}">${icon('edit')}ویرایش</button>` : ''}
        <button class="btn sm ghost" data-act="dup" data-id="${m.id}" title="تکثیر">${icon('copy')}تکثیر</button>
        ${m.state !== 'cancelled' && m.state !== 'ended' ? `<button class="btn sm danger" data-act="cancel" data-id="${m.id}">${icon('stop')}لغو</button>` : ''}
      </div>
    </div>
  </article>`;
}

function openComposer(existing = null, preset = {}) {
  const coupons = (state.cache.coupons || []).filter((c) => c.active);
  const m = {
    kind: 'announcement', priority: 'normal', title_fa: '', body_fa: '', title_en: '', body_en: '', cta_fa: '', cta_en: '', link: '', coupon: '',
    audience: { platforms: [], flavours: [], langs: [], countries: [] }, starts_at: null, ends_at: null, notify: true, ...preset, ...(existing || {}),
  };
  m.audience = { platforms: [], flavours: [], langs: [], countries: [], ...(m.audience || {}) };
  if (!existing && !preset.title_fa) Object.assign(m, TEMPLATES[m.kind]);
  const scheduled = existing && existing.starts_at > Date.now() / 1000 + 60;
  const chip = (group, value, label) => `<button type="button" class="chip ${m.audience[group].includes(value) ? 'on' : ''}" data-group="${group}" data-value="${value}">${label}</button>`;
  const dlg = modal(`
    <div class="modal-head"><h3>${existing ? 'ویرایش پیام' : 'پیام و اعلان تازه'}</h3><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
    <form class="modal-body" id="composer" autocomplete="off">
      <div class="composer">
        <div class="grid">
          <div class="section-title">نوع پیام</div>
          <div class="kind-grid">${Object.entries(KINDS).map(([k, v]) => `<button type="button" class="kind ${k === m.kind ? 'on' : ''}" data-kind="${k}" title="${v.hint}"><span class="ic k-${k}">${icon(v.icon)}</span>${v.label}</button>`).join('')}</div>
          <div class="callout warn" id="offer-note" ${m.kind === 'offer' ? '' : 'hidden'}>${icon('gift')}<span>اعلانِ پیشنهاد فقط به کسانی می‌رسد که «پیشنهادها» را در تنظیمات اعلان روشن کرده‌اند؛ بقیه همین پیام را به‌صورت کارت بالای صفحهٔ اصلی برنامه می‌بینند.</span></div>
          <div class="tabs" id="lang-tabs"><button type="button" data-lang="fa" class="on">فارسی</button><button type="button" data-lang="en">English</button></div>
          <div class="form-grid" data-pane="fa">
            <label class="field wide"><span>عنوان</span><input name="title_fa" maxlength="80" value="${esc(m.title_fa)}"><span class="counter" data-count="title_fa">0/80</span></label>
            <label class="field wide"><span>متن</span><textarea name="body_fa" maxlength="400">${esc(m.body_fa)}</textarea><span class="counter" data-count="body_fa">0/400</span></label>
            <label class="field"><span>متن دکمه</span><input name="cta_fa" maxlength="30" value="${esc(m.cta_fa || '')}"></label>
          </div>
          <div class="form-grid" data-pane="en" hidden>
            <label class="field wide"><span>Title</span><input name="title_en" dir="ltr" maxlength="80" value="${esc(m.title_en)}"><span class="counter" data-count="title_en">0/80</span></label>
            <label class="field wide"><span>Body</span><textarea name="body_en" dir="ltr" maxlength="400">${esc(m.body_en)}</textarea><span class="counter" data-count="body_en">0/400</span></label>
            <label class="field"><span>Button</span><input name="cta_en" dir="ltr" maxlength="30" value="${esc(m.cta_en || '')}"></label>
          </div>
          <div class="callout">${icon('globe')}<span>کاربر انگلیسی‌زبان متن انگلیسی را می‌بیند؛ اگر خالی باشد، متن فارسی برایش می‌رود.</span></div>

          <div class="section-title">مقصد و پیشنهاد</div>
          <div class="form-grid">
            <label class="field"><span>با زدن روی اعلان</span><select name="link_preset">${LINKS.map(([v, l]) => `<option value="${v}" ${v === (m.link || '') ? 'selected' : ''}>${l}</option>`).join('')}<option value="__custom" ${m.link && !LINKS.some(([v]) => v === m.link) ? 'selected' : ''}>پیوند دلخواه…</option></select></label>
            <label class="field"><span>پیوند</span><input name="link" dir="ltr" placeholder="coinepro://… یا https://…" value="${esc(m.link || '')}"></label>
            <label class="field"><span>کد تخفیف</span><select name="coupon"><option value="">بدون کد</option>${coupons.map((c) => `<option value="${esc(c.code)}" ${c.code === m.coupon ? 'selected' : ''}>${esc(c.code)} — ${c.percent}%</option>`).join('')}</select>
              <small>${coupons.length ? 'کد در اعلان نشان داده می‌شود.' : 'هنوز کد فعالی نساخته‌اید.'}</small></label>
            <label class="field"><span>اولویت</span><select name="priority"><option value="normal">عادی</option><option value="high" ${m.priority === 'high' ? 'selected' : ''}>فوری (بالای فهرست، اعلان با صدا)</option></select></label>
          </div>

          <div class="section-title">مخاطبان</div>
          <div class="grid">
            <div class="chips">${chip('platforms', 'android', 'اندروید')}${chip('platforms', 'web', 'وب')}<span class="faint">·</span>${chip('flavours', 'direct', 'نسخهٔ مستقیم')}${chip('flavours', 'bazaar', 'بازار')}<span class="faint">·</span>${chip('langs', 'fa', 'فارسی')}${chip('langs', 'en', 'English')}</div>
            <div class="form-grid">
              <label class="field"><span>کشورها (کد دوحرفی، با کاما)</span><input name="countries" dir="ltr" placeholder="IR, DE, TR" value="${esc((m.audience.countries || []).join(', '))}"></label>
              <label class="field"><span>محدودهٔ کد نسخه</span><div class="toolbar"><input class="input" name="min_version_code" dir="ltr" inputmode="numeric" placeholder="از" value="${m.audience.min_version_code || ''}"><input class="input" name="max_version_code" dir="ltr" inputmode="numeric" placeholder="تا" value="${m.audience.max_version_code || ''}"></div><small>مثلاً برای کاربرانی که هنوز زیر 52900000 هستند.</small></label>
            </div>
            <div class="reach"><div>${icon('users')}</div><div><strong id="reach">…</strong> <span id="reach-of"></span><br><span>نصب فعال در ۳۰ روز اخیر که این پیام را دریافت می‌کنند</span></div></div>
          </div>

          <div class="section-title">زمان</div>
          <div class="form-grid">
            <label class="field"><span>شروع</span><select name="when"><option value="now">همین حالا</option><option value="later" ${scheduled ? 'selected' : ''}>زمان مشخص…</option></select></label>
            <label class="field" data-later ${scheduled ? '' : 'hidden'}><span>زمان شروع (به وقت تهران)</span><input type="datetime-local" name="starts_at" dir="ltr" value="${m.starts_at ? epochToInput(m.starts_at) : epochToInput(Date.now() / 1000 + 3600)}"><small data-jalali="starts_at"></small></label>
            <label class="field"><span>پایان</span><select name="ends"><option value="">بدون پایان</option><option value="1">۱ روز بعد</option><option value="3">۳ روز بعد</option><option value="7">۷ روز بعد</option><option value="custom" ${m.ends_at ? 'selected' : ''}>زمان مشخص…</option></select></label>
            <label class="field" data-ends ${m.ends_at ? '' : 'hidden'}><span>زمان پایان (به وقت تهران)</span><input type="datetime-local" name="ends_at" dir="ltr" value="${m.ends_at ? epochToInput(m.ends_at) : ''}"><small data-jalali="ends_at"></small></label>
            <label class="switch wide"><input type="checkbox" name="notify" ${m.notify ? 'checked' : ''}><span>نمایش به‌صورت اعلان روی گوشی (خاموش: فقط کارت درون برنامه)</span></label>
          </div>
          <p class="form-error" id="composer-error"></p>
        </div>
        <div>
          <div class="phone"><div class="phone-screen"><div class="phone-notch"></div>
            <div class="phone-time" id="pv-time"></div><div class="phone-date" id="pv-date"></div>
            <div class="notif" id="pv"></div>
          </div></div>
        </div>
      </div>
    </form>
    <div class="modal-foot">
      <button class="btn" data-close>انصراف</button>
      <button class="btn" id="save-draft">${icon('file')}ذخیرهٔ پیش‌نویس</button>
      <button class="btn primary" id="publish">${icon('bell')}<span id="publish-label">ارسال</span></button>
    </div>`);
  const form = $('#composer', dlg.el);
  let lang = 'fa';
  const read = () => {
    const f = Object.fromEntries(new FormData(form));
    const countries = (f.countries || '').split(/[\s,،]+/).map((c) => c.trim().toUpperCase()).filter((c) => /^[A-Z]{2}$/.test(c));
    let starts = f.when === 'later' ? inputToEpoch(f.starts_at) : null;
    let ends = null;
    if (f.ends === 'custom') ends = inputToEpoch(f.ends_at);
    else if (f.ends) ends = (starts || Math.floor(Date.now() / 1000)) + (+f.ends) * 86400;
    return {
      id: existing?.id, kind: m.kind, priority: f.priority, title_fa: f.title_fa, body_fa: f.body_fa, title_en: f.title_en, body_en: f.body_en,
      cta_fa: f.cta_fa, cta_en: f.cta_en, link: (f.link || '').trim(), coupon: f.coupon, notify: !!f.notify,
      audience: { ...m.audience, countries, min_version_code: +f.min_version_code || 0, max_version_code: +f.max_version_code || 0 },
      starts_at: starts, ends_at: ends,
    };
  };
  const preview = () => {
    const v = read();
    const en = lang === 'en';
    const title = (en && v.title_en) || v.title_fa || v.title_en || 'عنوان پیام';
    const body = (en && v.body_en) || v.body_fa || v.body_en || 'متن پیام اینجا دیده می‌شود.';
    const cta = (en && v.cta_en) || v.cta_fa;
    const pv = $('#pv', dlg.el);
    pv.dir = en && v.title_en ? 'ltr' : 'rtl';
    pv.innerHTML = `<div class="notif-head"><img src="/admin/static/mark.svg" alt=""><span>Pro Chart</span><em>${en ? 'now' : 'اکنون'}</em></div>
      <strong>${esc(title)}</strong><p>${esc(body)}</p>
      ${v.coupon ? `<span class="notif-coupon">${esc(v.coupon)}</span>` : ''}
      ${cta ? `<div class="notif-cta"><span>${esc(cta)}</span></div>` : ''}`;
    const start = v.starts_at || Date.now() / 1000;
    $('#pv-time', dlg.el).textContent = timeFmt.format(new Date(start * 1000));
    $('#pv-date', dlg.el).textContent = jalaliFull.format(new Date(start * 1000));
    $$('[data-count]', dlg.el).forEach((c) => {
      const field = form.elements[c.dataset.count];
      const max = +field.maxLength;
      c.textContent = `${field.value.length}/${max}`;
      c.classList.toggle('over', field.value.length >= max);
    });
    $$('[data-jalali]', dlg.el).forEach((s) => {
      const epoch = inputToEpoch(form.elements[s.dataset.jalali].value);
      s.textContent = epoch ? jalaliFull.format(new Date(epoch * 1000)) + ' · ' + timeFmt.format(new Date(epoch * 1000)) : '';
    });
    $('#publish-label', dlg.el).textContent = v.starts_at && v.starts_at > Date.now() / 1000 + 60 ? 'زمان‌بندی' : 'ارسال همین حالا';
  };
  let reachTimer = null;
  const estimate = () => {
    clearTimeout(reachTimer);
    reachTimer = setTimeout(async () => {
      try {
        const r = await api('messages/estimate', { method: 'POST', body: { audience: read().audience } });
        $('#reach', dlg.el).textContent = fmt.n(r.reach);
        $('#reach-of', dlg.el).textContent = `از ${fmt.n(r.active)}`;
      } catch { /* the estimate is a hint */ }
    }, 350);
  };
  $$('.kind', dlg.el).forEach((b) => b.addEventListener('click', () => {
    const before = TEMPLATES[m.kind];
    m.kind = b.dataset.kind;
    $$('.kind', dlg.el).forEach((x) => x.classList.toggle('on', x === b));
    $('#offer-note', dlg.el).hidden = m.kind !== 'offer';
    // A template replaces only what is still the previous template's text.
    const next = TEMPLATES[m.kind];
    ['title_fa', 'body_fa', 'title_en', 'body_en', 'cta_fa', 'cta_en'].forEach((k) => {
      if (!form.elements[k].value || form.elements[k].value === before[k]) form.elements[k].value = next[k] || '';
    });
    if (next.link && !form.elements.link.value) { form.elements.link.value = next.link; form.elements.link_preset.value = next.link; }
    preview();
  }));
  $('#lang-tabs', dlg.el).addEventListener('click', (e) => {
    const b = e.target.closest('[data-lang]');
    if (!b) return;
    lang = b.dataset.lang;
    $$('#lang-tabs button', dlg.el).forEach((x) => x.classList.toggle('on', x === b));
    $$('[data-pane]', dlg.el).forEach((p) => { p.hidden = p.dataset.pane !== lang; });
    preview();
  });
  $$('.chip[data-group]', dlg.el).forEach((c) => c.addEventListener('click', () => {
    const list = m.audience[c.dataset.group];
    const at = list.indexOf(c.dataset.value);
    if (at >= 0) list.splice(at, 1); else list.push(c.dataset.value);
    c.classList.toggle('on', at < 0);
    estimate();
  }));
  form.elements.link_preset.addEventListener('change', (e) => {
    if (e.target.value !== '__custom') form.elements.link.value = e.target.value;
    else form.elements.link.focus();
    preview();
  });
  form.elements.when.addEventListener('change', (e) => { $('[data-later]', dlg.el).hidden = e.target.value !== 'later'; preview(); });
  form.elements.ends.addEventListener('change', (e) => { $('[data-ends]', dlg.el).hidden = e.target.value !== 'custom'; preview(); });
  form.addEventListener('input', (e) => {
    preview();
    if (['countries', 'min_version_code', 'max_version_code'].includes(e.target.name)) estimate();
  });
  form.addEventListener('submit', (e) => e.preventDefault());
  const submit = async (draft, button) => {
    const body = { ...read(), draft };
    $('#composer-error', dlg.el).textContent = '';
    try {
      await busy(button, () => api('messages', { method: 'POST', body }));
      dlg.close();
      toast(draft ? 'پیش‌نویس ذخیره شد' : body.starts_at ? 'پیام زمان‌بندی شد' : 'پیام فعال شد؛ هر نصب در check-in بعدی آن را می‌گیرد');
      if (state.view === 'messages') route(); else location.hash = '#/messages';
    } catch (error) {
      $('#composer-error', dlg.el).textContent = errorText(error);
    }
  };
  $('#save-draft', dlg.el).addEventListener('click', (e) => submit(true, e.currentTarget));
  $('#publish', dlg.el).addEventListener('click', async (e) => {
    const button = e.currentTarget;
    const v = read();
    const ok = await confirmBox('ارسال پیام', `این پیام برای <b>${$('#reach', dlg.el).textContent}</b> نصب فعال ${v.starts_at ? 'در زمان تعیین‌شده' : 'از همین حالا'} ارسال می‌شود. ادامه می‌دهید؟`, { ok: 'ارسال' });
    if (ok) submit(false, button);
  });
  preview();
  estimate();
}

// ── Coupons ────────────────────────────────────────────────────────────────────────────────────

const PLANS = { monthly: 'ماهانه', quarterly: 'سه‌ماهه', yearly: 'سالانه' };

async function renderCoupons(host) {
  const d = await api('coupons');
  state.cache.coupons = d.coupons;
  const now = Date.now() / 1000;
  const active = d.coupons.filter((c) => c.active && (!c.expires_at || c.expires_at > now));
  host.innerHTML = `
    <div class="grid g-4">
      ${kpi({ label: 'کد فعال', value: fmt.n(active.length), iconName: 'ticket', color: '#f5a524', foot: `کل کدها: <b class="num">${fmt.n(d.coupons.length)}</b>` })}
      ${kpi({ label: 'استعلام کد', value: fmt.n(sum(d.coupons.map((c) => c.checks))), iconName: 'search', color: '#8b7bff', foot: 'دفعاتی که کاربر کد را وارد کرد' })}
      ${kpi({ label: 'استفادهٔ ثبت‌شده', value: fmt.n(sum(d.coupons.map((c) => c.uses))), iconName: 'check', color: '#2ad4c4', foot: 'خریدهای انجام‌شده با کد' })}
      ${kpi({ label: 'بیشترین تخفیف فعال', value: active.length ? Math.max(...active.map((c) => c.percent)) + '%' : '—', iconName: 'gift', color: '#f0506e' })}
    </div>
    <div class="card flush">
      <div class="card-head"><div><h3>کدهای تخفیف</h3><p>کد را بسازید و با یک کلیک در قالب پیام تخفیف برای کاربران بفرستید</p></div>
        <button class="btn primary" id="cp-new">${icon('plus')}کد تازه</button></div>
      <div class="table-wrap"><table>
        <thead><tr><th>کد</th><th>تخفیف</th><th>طرح</th><th>استفاده</th><th>استعلام</th><th>انقضا</th><th>وضعیت</th><th>یادداشت</th><th></th></tr></thead>
        <tbody>${d.coupons.map((c) => {
          const expired = c.expires_at && c.expires_at <= now;
          const full = c.max_uses && c.uses >= c.max_uses;
          const status = !c.active ? ['plain', 'غیرفعال'] : expired ? ['bad', 'منقضی'] : full ? ['warn', 'تمام شد'] : ['good', 'فعال'];
          return `<tr>
            <td><span class="code-tag">${esc(c.code)}</span> <button class="icon-btn sm ghost" data-copy="${esc(c.code)}" title="کپی">${icon('copy')}</button></td>
            <td><strong class="num">${c.percent}%</strong></td>
            <td>${esc(PLANS[c.plan] || c.plan || 'همهٔ طرح‌ها')}</td>
            <td class="num">${fmt.n(c.uses)}${c.max_uses ? ` / ${fmt.n(c.max_uses)}` : ''}${c.max_uses ? `<div class="bar-track"><div class="bar-fill" data-w="${(c.uses / c.max_uses) * 100}"></div></div>` : ''}</td>
            <td class="num">${fmt.n(c.checks)}</td>
            <td>${c.expires_at ? when(c.expires_at) : '<span class="faint">بدون انقضا</span>'}</td>
            <td><span class="pill ${status[0]}">${status[1]}</span></td>
            <td class="faint">${esc(c.note || '')}</td>
            <td><div class="msg-actions">
              <button class="btn sm" data-offer="${esc(c.code)}" title="ساخت پیام تخفیف">${icon('gift')}پیام</button>
              <button class="btn sm ghost" data-toggle="${esc(c.code)}">${c.active ? 'غیرفعال' : 'فعال'}</button>
              <button class="icon-btn sm ghost" data-del="${esc(c.code)}" title="حذف">${icon('trash')}</button></div></td></tr>`;
        }).join('')}</tbody></table>
        ${d.coupons.length ? '' : empty('هنوز کدی نساخته‌اید', 'مثلاً یک کد ۲۰٪ برای جشنواره بسازید', 'ticket')}
      </div>
    </div>`;
  hydrate(host);
  $('#cp-new').addEventListener('click', openCouponForm);
  host.addEventListener('click', async (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.dataset.offer) {
      const c = d.coupons.find((x) => x.code === t.dataset.offer);
      const tpl = TEMPLATES.offer;
      openComposer(null, {
        kind: 'offer', coupon: c.code, ...tpl,
        body_fa: `${c.percent}٪ تخفیف اشتراک پرمیوم پرو چارت با کد ${c.code}. کد را در صفحهٔ پرمیوم وارد کنید.`,
        body_en: `${c.percent}% off Pro Chart Premium with code ${c.code}. Enter it on the Premium screen.`,
        ends_at: c.expires_at || null,
      });
    }
    if (t.dataset.toggle) {
      await api(`coupons/${encodeURIComponent(t.dataset.toggle)}/toggle`, { method: 'POST', body: {} });
      route();
    }
    if (t.dataset.del && await confirmBox('حذف کد', `کد <span class="code-tag">${esc(t.dataset.del)}</span> برای همیشه حذف می‌شود.`, { danger: true, ok: 'حذف' })) {
      await api(`coupons/${encodeURIComponent(t.dataset.del)}`, { method: 'DELETE' });
      toast('کد حذف شد');
      route();
    }
  });
}

function openCouponForm() {
  const dlg = modal(`
    <div class="modal-head"><h3>کد تخفیف تازه</h3><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
    <form class="modal-body" id="cp-form" autocomplete="off">
      <div class="form-grid">
        <label class="field"><span>کد</span><input name="code" dir="ltr" maxlength="40" placeholder="خالی = ساخت خودکار (PC-XXXXXX)"></label>
        <label class="field"><span>درصد تخفیف: <b class="num" id="cp-pct">20%</b></span><input type="range" name="percent" min="1" max="90" value="20"></label>
        <label class="field"><span>طرح</span><select name="plan"><option value="">همهٔ طرح‌ها</option><option value="monthly">ماهانه</option><option value="quarterly">سه‌ماهه</option><option value="yearly">سالانه</option></select></label>
        <label class="field"><span>سقف استفاده</span><input name="max_uses" dir="ltr" inputmode="numeric" placeholder="بدون سقف"></label>
        <label class="field"><span>انقضا (وقت تهران)</span><input type="datetime-local" name="expires_at" dir="ltr"></label>
        <label class="field"><span>یادداشت (فقط برای شما)</span><input name="note" maxlength="200" placeholder="مثلاً جشنوارهٔ یلدا"></label>
      </div>
      <p class="form-error" id="cp-error"></p>
    </form>
    <div class="modal-foot"><button class="btn" data-close>انصراف</button><button class="btn primary" id="cp-save">${icon('check')}ساخت کد</button></div>`, { size: 'sm' });
  const form = $('#cp-form', dlg.el);
  form.elements.percent.addEventListener('input', (e) => { $('#cp-pct', dlg.el).textContent = `${e.target.value}%`; });
  form.elements.code.addEventListener('input', (e) => { e.target.value = e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''); });
  $('#cp-save', dlg.el).addEventListener('click', async (e) => {
    const f = Object.fromEntries(new FormData(form));
    try {
      const r = await busy(e.currentTarget, () => api('coupons', { method: 'POST', body: { ...f, percent: +f.percent, max_uses: +f.max_uses || null, expires_at: inputToEpoch(f.expires_at) } }));
      dlg.close();
      toast(`کد ${r.code} ساخته شد`);
      route();
    } catch (error) {
      $('#cp-error', dlg.el).textContent = errorText(error);
    }
  });
}

// ── Remote configuration ───────────────────────────────────────────────────────────────────────

async function renderConfig(host) {
  const [d, release] = await Promise.all([api('config'), api('release')]);
  const c = d.config;
  const meta = (key) => (d.meta[key] ? `آخرین تغییر: ${esc(d.meta[key].updated_by)} · ${ago(d.meta[key].updated_at)}` : 'هنوز تغییر نکرده');
  const banner = (key, title, sub, cls, iconName) => `
    <div class="card"><div class="card-head"><div><h3>${title}</h3><p>${sub}</p></div><label class="switch"><input type="checkbox" data-key="${key}.on" ${c[key].on ? 'checked' : ''}><span></span></label></div>
      <div class="grid">
        <label class="field"><span>متن فارسی</span><input data-key="${key}.message_fa" maxlength="200" value="${esc(c[key].message_fa)}"></label>
        <label class="field"><span>English</span><input data-key="${key}.message_en" dir="ltr" maxlength="200" value="${esc(c[key].message_en)}"></label>
        <div class="banner-preview ${cls}" data-preview="${key}">${icon(iconName)}<span></span></div>
        <small class="faint">${meta(key)}</small>
      </div></div>`;
  host.innerHTML = `
    <div class="callout">${icon('shield')}<span>این تنظیمات در check-in بعدی هر نصب (حداکثر چند ساعت) اعمال می‌شود. هیچ‌کدام برنامه را از کار نمی‌اندازد: بنرها فقط اطلاع می‌دهند.</span></div>
    <div class="grid g-2">
      ${banner('maintenance', 'بنر تعمیرات', 'بالای صفحهٔ اصلی برنامه، تا وقتی روشن است', 'maint', 'wrench')}
      ${banner('update_nudge', 'بنر دعوت به به‌روزرسانی', 'برای نصب‌هایی که زیر «حداقل نسخهٔ توصیه‌شده» هستند', 'update', 'rocket')}
    </div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><div><h3>حداقل نسخهٔ توصیه‌شده</h3><p>نصب‌های پایین‌تر از این کد، بنر به‌روزرسانی را می‌بینند</p></div></div>
        <div class="grid">
          <label class="field"><span>کد نسخه</span><input data-key="min_version_code" dir="ltr" inputmode="numeric" value="${c.min_version_code || ''}" placeholder="0 = خاموش"></label>
          <div class="toolbar">${release.latest ? `<button class="btn sm" id="use-latest">استفاده از آخرین نسخه (${esc(release.latest.version_name)} · ${release.latest.version_code})</button>` : ''}</div>
          <div class="callout" id="below-latest"></div>
          <small class="faint">${meta('min_version_code')}</small>
        </div></div>
      <div class="card"><div class="card-head"><div><h3>فاصلهٔ check-in</h3><p>هر نصب هر چند ساعت یک‌بار پیام‌ها و تنظیمات را می‌گیرد</p></div></div>
        <div class="grid">
          <label class="field"><span>هر <b class="num" id="hello-v">${c.hello_every_hours}</b> ساعت</span><input type="range" min="1" max="24" data-key="hello_every_hours" value="${c.hello_every_hours}"></label>
          <div class="callout">${icon('bolt')}<span>کمتر = پیام‌ها زودتر می‌رسند و آمار دقیق‌تر است؛ بیشتر = مصرف باتری و ترافیک کمتر. ۶ ساعت پیشنهاد می‌شود.</span></div>
          <small class="faint">${meta('hello_every_hours')}</small>
        </div></div>
    </div>
    <div class="toolbar"><span class="grow"></span><button class="btn primary" id="cfg-save">${icon('check')}ذخیرهٔ تنظیمات</button></div>`;
  const read = () => {
    const out = { maintenance: { ...c.maintenance }, update_nudge: { ...c.update_nudge } };
    $$('[data-key]', host).forEach((el) => {
      const [a, b] = el.dataset.key.split('.');
      const v = el.type === 'checkbox' ? el.checked : el.value;
      if (b) out[a][b] = v; else out[a] = el.type === 'range' || a === 'min_version_code' ? +v || 0 : v;
    });
    return out;
  };
  const refresh = () => {
    const v = read();
    [['maintenance', 'برنامه در حال به‌روزرسانی سرورهاست.'], ['update_nudge', 'نسخهٔ تازهٔ پرو چارت آماده است.']].forEach(([k, fallback]) => {
      const el = $(`[data-preview="${k}"]`, host);
      el.hidden = !v[k].on;
      $('span', el).textContent = v[k].message_fa || fallback;
    });
    $('#hello-v').textContent = v.hello_every_hours;
    const below = release.versions.filter((x) => v.min_version_code && x.code < v.min_version_code);
    const total = sum(release.versions.map((x) => x.value));
    $('#below-latest').innerHTML = v.min_version_code
      ? `${icon('users')}<span><b class="num">${fmt.n(sum(below.map((x) => x.value)))}</b> نصب فعال اندروید (${total ? fmt.pct(sum(below.map((x) => x.value)) / total, 0) : '—'}) زیر این نسخه‌اند.</span>`
      : `${icon('users')}<span>خاموش است؛ هیچ نصبی بنر به‌روزرسانی نمی‌بیند.</span>`;
  };
  host.addEventListener('input', refresh);
  $('#use-latest')?.addEventListener('click', () => { $('[data-key="min_version_code"]', host).value = release.latest.version_code; refresh(); });
  $('#cfg-save').addEventListener('click', async (e) => {
    try {
      await busy(e.currentTarget, () => api('config', { method: 'PUT', body: read() }));
      toast('تنظیمات ذخیره شد');
      route();
    } catch (error) { toast(errorText(error), 'bad'); }
  });
  refresh();
}

// ── Release ────────────────────────────────────────────────────────────────────────────────────

async function renderRelease(host) {
  const d = await api('release');
  const l = d.latest;
  const total = sum(d.versions.map((v) => v.value));
  const onLatest = l ? sum(d.versions.filter((v) => v.code >= l.version_code).map((v) => v.value)) : 0;
  host.innerHTML = `
    <div class="grid g-main">
      <div class="card"><div class="card-head"><div><h3>نسخهٔ عرضه‌شده</h3><p>همان چیزی که برنامه در «به‌روزرسانی» به کاربر پیشنهاد می‌کند</p></div>${l?.mandatory ? '<span class="pill warn">ضروری</span>' : '<span class="pill good">اختیاری</span>'}</div>
        ${l ? `<div class="release-card"><div class="release-badge"><strong>${esc(l.version_name)}</strong><span>${fmt.n(l.version_code)}</span></div>
          <dl class="kv"><dt>فایل</dt><dd><a href="${esc(l.url)}" class="mono">${esc(l.url.split('/').pop())}</a></dd>
          <dt>SHA-256</dt><dd><span class="mono faint">${esc(l.sha256.slice(0, 16))}…${esc(l.sha256.slice(-8))}</span> <button class="icon-btn sm ghost" data-copy="${esc(l.sha256)}">${icon('copy')}</button></dd>
          <dt>روی این نسخه</dt><dd>${total ? fmt.pct(onLatest / total, 0) : '—'} <span class="faint">(${fmt.n(onLatest)} / ${fmt.n(total)})</span></dd></dl></div>
          <div class="tabs" id="notes-tabs"><button data-n="fa" class="on">یادداشت فارسی</button><button data-n="en">English notes</button></div>
          <div class="notes" id="notes">${esc(l.notes_fa)}</div>` : empty('فایل /api/app/latest خوانده نشد', 'ADMIN_LATEST_JSON را بررسی کنید', 'file')}
      </div>
      <div class="card"><div class="card-head"><div><h3>پذیرش نسخه</h3><p>نصب‌های فعال اندروید در ۳۰ روز</p></div></div>
        ${l ? gauge(total ? onLatest / total : null, 'روی آخرین نسخه', '#2ad4c4') : ''}
        ${barList(d.versions, { labelOf: (lb, item) => `<span class="num">${esc(lb || '?')}</span> ${l && item.code >= l.version_code ? '<span class="pill good plain">آخرین</span>' : ''}`, colorOf: (item) => (l && item.code >= l.version_code ? 'var(--good)' : 'var(--grad)') })}
      </div>
    </div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><div><h3>دانلود هر فایل</h3><p>از ابتدا، یکتا در روز</p></div></div>${barList(d.files, { labelOf: (lb) => `<span class="mono">${esc(lb)}</span>` })}</div>
      <div class="card"><div class="card-head"><h3>چک‌لیست انتشار</h3></div>
        <div class="bars">
          ${['CI نسخه را می‌سازد و در GitHub منتشر می‌کند', 'فایل APK در /download/ قرار می‌گیرد', '/api/app/latest با UPDATE_NOTES به‌روز می‌شود', 'بستهٔ وب از web-latest (نه ساخت روی سرور)', 'پیام «به‌روزرسانی» برای نسخه‌های قدیمی بفرستید']
            .map((t, i) => `<div class="svc-top"><span><span class="pill accent plain num">${i + 1}</span>&nbsp; ${t}</span></div>`).join('')}
          <button class="btn" id="nudge">${icon('rocket')}ساخت پیام به‌روزرسانی برای نسخه‌های قدیمی</button>
        </div></div>
    </div>`;
  hydrate(host);
  host.addEventListener('click', (e) => {
    const n = e.target.closest('[data-n]');
    if (n && l) {
      $$('#notes-tabs button').forEach((x) => x.classList.toggle('on', x === n));
      $('#notes').textContent = n.dataset.n === 'fa' ? l.notes_fa : l.notes_en;
      $('#notes').dir = n.dataset.n === 'fa' ? 'rtl' : 'ltr';
    }
  });
  $('#nudge').addEventListener('click', async () => {
    state.cache.coupons = (await api('coupons')).coupons;
    const t = TEMPLATES.update;
    openComposer(null, {
      kind: 'update', ...t,
      title_fa: l ? `پرو چارت ${l.version_name} آماده است` : t.title_fa,
      title_en: l ? `Pro Chart ${l.version_name} is ready` : t.title_en,
      link: l?.url || t.link,
      audience: { platforms: ['android'], flavours: ['direct'], langs: [], countries: [], max_version_code: l ? l.version_code - 1 : 0 },
    });
  });
}

// ── Health ─────────────────────────────────────────────────────────────────────────────────────

async function renderHealth(host) {
  const d = await api('health');
  const results = d.last?.results || [];
  const down = results.filter((r) => !r.ok);
  updateBadges(null, down.length);
  const s = d.server || {};
  const memUsed = s.memory ? 1 - s.memory.available / s.memory.total : null;
  const diskUsed = s.disk ? 1 - s.disk.free / s.disk.total : null;
  const names = [...new Set([...results.map((r) => r.name), ...Object.keys(d.history)])];
  host.innerHTML = `
    <section class="hero">
      <div><h2>${down.length ? `${fmt.fa(down.length)} سرویس پاسخ نمی‌دهد` : 'همه‌چیز سالم است'}</h2>
        <p>آخرین بررسی: ${d.last ? ago(d.last.at) : 'هنوز انجام نشده'}</p></div>
      <button class="btn primary" id="probe-now">${icon('refresh')}بررسی همین حالا</button>
    </section>
    <div class="grid g-2">${names.map((name) => {
      const last = results.find((r) => r.name === name);
      const hist = d.history[name] || [];
      const up = d.uptime_7d[name];
      const slots = 48;
      const now = Date.now() / 1000;
      const strip = Array.from({ length: slots }, (_, i) => {
        const from = now - 86400 + (i * 86400) / slots, to = from + 86400 / slots;
        const inSlot = hist.filter((h) => h[0] >= from && h[0] < to);
        if (!inSlot.length) return '<i></i>';
        return `<i class="${inSlot.every((h) => h[1]) ? 'ok' : 'bad'}" title="${timeFmt.format(new Date(from * 1000))}"></i>`;
      }).join('');
      const ms = hist.filter((h) => h[1]).map((h) => h[2]);
      const avg = ms.length ? Math.round(sum(ms) / ms.length) : null;
      const ok = last ? last.ok : hist.length ? !!hist.at(-1)[1] : null;
      return `<div class="card svc">
        <div class="svc-top"><strong><span class="status-dot ${ok === null ? '' : ok ? 'ok' : 'bad'}"></span>&nbsp; ${esc(name)}</strong>${last ? `<span class="pill ${last.ok ? 'good' : 'bad'} plain num">${last.ok ? `${last.ms} ms` : esc(last.error || `HTTP ${last.status}`)}</span>` : ''}</div>
        <div class="svc-figs"><span>دسترس‌پذیری ۷ روز <b>${up === undefined ? '—' : fmt.pct(up, 2)}</b></span><span>میانگین تأخیر <b>${avg === null ? '—' : avg + ' ms'}</b></span></div>
        <div class="uptime-strip" title="۲۴ ساعت اخیر">${strip}</div>
        ${ms.length > 1 ? sparkline(ms.slice(-60), ok === false ? '#f0506e' : '#2ad4c4', 300, 34).replace('class="spark"', 'class="spark wide-spark"') : ''}
      </div>`;
    }).join('') || `<div class="card">${empty('هنوز بررسی‌ای ثبت نشده', 'دکمهٔ «بررسی همین حالا» را بزنید')}</div>`}</div>
    <div class="card"><div class="card-head"><div><h3>سرور</h3><p>منابع ماشینی که کنسول روی آن اجرا می‌شود</p></div></div>
      <div class="stat-tiles">
        <div class="stat-tile"><span>بار پردازنده (۱/۵/۱۵ دقیقه)</span><strong>${s.load ? s.load.map((x) => x.toFixed(2)).join(' / ') : '—'}</strong><small class="faint">${s.cpus ? `${fmt.fa(s.cpus)} هسته` : ''}</small></div>
        <div class="stat-tile"><span>حافظه</span><strong>${memUsed === null ? '—' : fmt.pct(memUsed, 0)}</strong><div class="bar-track"><div class="bar-fill" data-w="${(memUsed || 0) * 100}" ${memUsed > 0.85 ? 'data-bg="var(--bad)"' : ''}></div></div><small class="faint num">${s.memory ? `${fmt.bytes(s.memory.total - s.memory.available)} / ${fmt.bytes(s.memory.total)}` : ''}</small></div>
        <div class="stat-tile"><span>دیسک</span><strong>${diskUsed === null ? '—' : fmt.pct(diskUsed, 0)}</strong><div class="bar-track"><div class="bar-fill" data-w="${(diskUsed || 0) * 100}" ${diskUsed > 0.85 ? 'data-bg="var(--bad)"' : ''}></div></div><small class="faint num">${s.disk ? `${fmt.bytes(s.disk.free)} آزاد` : ''}</small></div>
        <div class="stat-tile"><span>روشن بودن سرور</span><strong>${s.uptime ? `${(s.uptime / 86400).toFixed(1)} d` : '—'}</strong></div>
        <div class="stat-tile"><span>پایگاه دادهٔ کنسول</span><strong>${s.db_bytes ? fmt.bytes(s.db_bytes) : '—'}</strong></div>
      </div>
      <div class="table-wrap"><table><thead><tr><th>گزارش دسترسی</th><th>خوانده‌شده تا</th></tr></thead><tbody>
        ${(d.ingest || []).map((r) => `<tr><td class="mono">${esc(r.path)}</td><td class="num">${fmt.bytes(r.offset)}</td></tr>`).join('') || '<tr><td colspan="2" class="faint">هنوز گزارشی خوانده نشده؛ مسیر ADMIN_ACCESS_LOG را بررسی کنید.</td></tr>'}
      </tbody></table></div>
    </div>`;
  hydrate(host);
  $$('.wide-spark', host).forEach((el) => { el.style.width = '100%'; el.style.height = '34px'; });
  $('#probe-now').addEventListener('click', async (e) => {
    const b = e.currentTarget;
    b.classList.add('spin');
    try { await busy(b, () => api('health/run', { method: 'POST', body: {} })); toast('بررسی انجام شد'); route(); } catch (error) { toast(errorText(error), 'bad'); } finally { b.classList.remove('spin'); }
  });
}

// ── Audit ──────────────────────────────────────────────────────────────────────────────────────

const ACTIONS = {
  'login.ok': ['good', 'ورود موفق'], 'login.failed': ['bad', 'ورود ناموفق'], 'login.locked': ['bad', 'تلاش روی حساب قفل'], 'login.bad_code': ['bad', 'کد تأیید نادرست'],
  logout: ['plain', 'خروج'], 'message.create': ['accent', 'ساخت پیام'], 'message.update': ['accent', 'ویرایش پیام'], 'message.cancel': ['warn', 'لغو پیام'],
  'message.duplicate': ['accent', 'تکثیر پیام'], 'coupon.create': ['info', 'ساخت کد تخفیف'], 'coupon.toggle': ['info', 'تغییر وضعیت کد'], 'coupon.delete': ['warn', 'حذف کد'],
  'config.update': ['info', 'تغییر تنظیمات'], 'password.change': ['warn', 'تغییر رمز'], 'password.bad_current': ['bad', 'رمز فعلی نادرست'], 'password.set_from_cli': ['warn', 'تنظیم رمز از سرور'],
  'totp.enable': ['good', 'فعال‌سازی تأیید دومرحله‌ای'], 'totp.disable': ['warn', 'غیرفعال‌سازی تأیید دومرحله‌ای'], 'sessions.revoke_others': ['warn', 'خروج دیگر دستگاه‌ها'],
};
const actionLabel = (a) => { const [cls, label] = ACTIONS[a] || ['plain', a]; return `<span class="pill ${cls}">${esc(label)}</span>`; };

async function renderAudit(host) {
  const filter = sessionStorage.getItem('audit-filter') || '';
  const d = await api(`audit?action=${encodeURIComponent(filter)}`);
  host.innerHTML = `
    <div class="card flush">
      <div class="card-head"><div class="segmented" id="audit-filter">${[['', 'همه'], ['login', 'ورودها'], ['message', 'پیام‌ها'], ['coupon', 'کدها'], ['config', 'تنظیمات'], ['password', 'رمز'], ['totp', 'دومرحله‌ای']]
        .map(([k, l]) => `<button data-f="${k}" class="${k === filter ? 'on' : ''}">${l}</button>`).join('')}</div>
        <a class="btn sm" href="/admin/api/audit.csv" download>${icon('csv')}خروجی CSV</a></div>
      <div class="table-wrap"><table><thead><tr><th>زمان</th><th>کاربر</th><th>نشانی IP</th><th>رویداد</th><th>جزئیات</th></tr></thead>
        <tbody>${d.rows.map((r) => `<tr><td>${when(r.at)}<div class="faint">${ago(r.at)}</div></td><td>${esc(r.username || '—')}</td><td class="mono faint">${esc(r.ip || '—')}</td><td>${actionLabel(r.action)}</td><td class="mono faint">${esc(r.detail || '')}</td></tr>`).join('')}</tbody></table>
        ${d.rows.length ? '' : empty('رویدادی نیست', '', 'list')}</div>
    </div>`;
  $('#audit-filter').addEventListener('click', (e) => {
    const b = e.target.closest('[data-f]');
    if (!b) return;
    sessionStorage.setItem('audit-filter', b.dataset.f);
    route();
  });
}

// ── Account ────────────────────────────────────────────────────────────────────────────────────

function strength(password) {
  let score = 0;
  if (password.length >= 10) score += 1;
  if (password.length >= 14) score += 1;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^A-Za-z0-9]/.test(password)) score += 1;
  return score;
}

async function renderAccount(host) {
  const [me, sessions] = await Promise.all([api('session'), api('account/sessions')]);
  state.csrf = me.csrf;
  host.innerHTML = `
    <div class="grid g-2">
      <div class="card"><div class="card-head"><div><h3>حساب مالک</h3><p>تنها حساب این کنسول</p></div><span class="pill ${me.totp ? 'good' : 'warn'}">${me.totp ? 'تأیید دومرحله‌ای فعال' : 'تأیید دومرحله‌ای خاموش'}</span></div>
        <dl class="kv"><dt>نام کاربری</dt><dd>${esc(me.username)}</dd><dt>آخرین ورود</dt><dd>${when(me.last_login_at)}</dd><dt>منطقهٔ زمانی گزارش‌ها</dt><dd>${esc(me.timezone)}</dd><dt>نشست‌های باز</dt><dd>${fmt.n(sessions.sessions.length)}</dd></dl>
      </div>
      <div class="card"><div class="card-head"><div><h3>تأیید دومرحله‌ای (TOTP)</h3><p>Google Authenticator، Microsoft Authenticator، 1Password و …</p></div></div><div id="totp-box"></div></div>
    </div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><div><h3>تغییر رمز عبور</h3><p>پس از تغییر، همهٔ دستگاه‌های دیگر خارج می‌شوند</p></div></div>
        <form class="grid" id="pw-form" autocomplete="off">
          <label class="field"><span>رمز فعلی</span><input type="password" name="current" dir="ltr" autocomplete="current-password"></label>
          <label class="field"><span>رمز جدید</span><input type="password" name="new" dir="ltr" autocomplete="new-password"><div class="bar-track"><div class="bar-fill" id="pw-meter" data-w="0"></div></div><small id="pw-hint" class="faint">دست‌کم ۱۰ نویسه؛ ترکیب حروف بزرگ و کوچک، عدد و نماد</small></label>
          <label class="field"><span>تکرار رمز جدید</span><input type="password" name="again" dir="ltr" autocomplete="new-password"></label>
          <p class="form-error" id="pw-error"></p>
          <button class="btn primary">${icon('key')}تغییر رمز</button>
        </form></div>
      <div class="card flush"><div class="card-head"><div><h3>نشست‌ها</h3><p>مرورگرهایی که الان وارد حساب‌اند</p></div><button class="btn sm danger" id="revoke">${icon('logout')}خروج همهٔ دستگاه‌های دیگر</button></div>
        <div class="table-wrap"><table><thead><tr><th>مرورگر</th><th>IP</th><th>آخرین فعالیت</th></tr></thead><tbody>
          ${sessions.sessions.map((s) => `<tr><td>${esc(agentName(s.agent))} ${s.current ? '<span class="pill good plain">همین دستگاه</span>' : ''}<div class="faint">از ${when(s.created_at)}</div></td><td class="mono faint">${esc(s.ip || '—')}</td><td>${ago(s.last_seen)}</td></tr>`).join('')}
        </tbody></table></div></div>
    </div>`;
  hydrate(host);
  drawTotp(me);
  const pw = $('#pw-form');
  pw.elements.new.addEventListener('input', (e) => {
    const s = strength(e.target.value);
    const meter = $('#pw-meter');
    meter.style.width = `${(s / 5) * 100}%`;
    meter.style.background = s <= 2 ? 'var(--bad)' : s === 3 ? 'var(--warn)' : 'var(--good)';
    $('#pw-hint').textContent = ['خیلی ضعیف', 'ضعیف', 'ضعیف', 'متوسط', 'خوب', 'عالی'][s];
  });
  pw.addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(pw));
    if (f.new !== f.again) { $('#pw-error').textContent = 'دو رمز جدید یکی نیستند.'; return; }
    try {
      await busy($('button', pw), () => api('account/password', { method: 'POST', body: { current: f.current, new: f.new } }));
      toast('رمز عوض شد');
      route();
    } catch (error) { $('#pw-error').textContent = errorText(error); }
  });
  $('#revoke').addEventListener('click', async () => {
    if (!await confirmBox('خروج دیگر دستگاه‌ها', 'همهٔ نشست‌ها به جز همین مرورگر بسته می‌شوند.', { danger: true, ok: 'خروج' })) return;
    const r = await api('account/sessions/revoke-others', { method: 'POST', body: {} });
    toast(`${fmt.fa(r.removed)} نشست بسته شد`);
    route();
  });
}

function agentName(agent) {
  const a = agent || '';
  const browser = /Edg\//.test(a) ? 'Edge' : /OPR\//.test(a) ? 'Opera' : /Firefox\//.test(a) ? 'Firefox' : /Chrome\//.test(a) ? 'Chrome' : /Safari\//.test(a) ? 'Safari' : 'مرورگر';
  const os = /Windows/.test(a) ? 'Windows' : /Android/.test(a) ? 'Android' : /iPhone|iPad/.test(a) ? 'iOS' : /Mac OS/.test(a) ? 'macOS' : /Linux/.test(a) ? 'Linux' : '';
  return os ? `${browser} · ${os}` : browser;
}

function drawTotp(me) {
  const box = $('#totp-box');
  if (me.totp) {
    box.innerHTML = `<div class="grid"><div class="callout">${icon('shield')}<span>ورود به این کنسول علاوه بر رمز، کد شش‌رقمی برنامهٔ احراز هویت را می‌خواهد.</span></div>
      <label class="field"><span>برای خاموش کردن، کد فعلی را وارد کنید</span><input name="code" class="code-input" dir="ltr" inputmode="numeric" maxlength="7"></label>
      <button class="btn danger" id="totp-off">خاموش کردن</button></div>`;
    $('#totp-off').addEventListener('click', async (e) => {
      try {
        await busy(e.currentTarget, () => api('account/totp/disable', { method: 'POST', body: { code: $('#totp-box [name=code]').value } }));
        toast('تأیید دومرحله‌ای خاموش شد');
        route();
      } catch (error) { toast(errorText(error), 'bad'); }
    });
    return;
  }
  box.innerHTML = `<div class="grid"><div class="callout warn">${icon('alert')}<span>حساب فقط با رمز محافظت می‌شود. با روشن کردن تأیید دومرحله‌ای، رمز لو رفته هم کافی نیست.</span></div>
    <button class="btn primary" id="totp-setup">${icon('shield')}روشن کردن</button></div>`;
  $('#totp-setup').addEventListener('click', async (e) => {
    const r = await busy(e.currentTarget, () => api('account/totp/setup', { method: 'POST', body: {} }));
    box.innerHTML = `<div class="grid">
      <p class="muted">۱. در برنامهٔ احراز هویت «افزودن با کلید» را بزنید و این کلید را وارد کنید (نوع: بر پایهٔ زمان):</p>
      <div class="secret-box"><span>${esc(r.secret.match(/.{1,4}/g).join(' '))}</span><button class="icon-btn sm ghost" data-copy="${esc(r.secret)}">${icon('copy')}</button></div>
      <p class="muted">یا پیوند زیر را در گوشی باز کنید:</p>
      <div class="secret-box"><span class="faint">otpauth://…</span><button class="icon-btn sm ghost" data-copy="${esc(r.uri)}">${icon('copy')}</button></div>
      <label class="field"><span>۲. کد شش‌رقمی‌ای که برنامه نشان می‌دهد</span><input name="code" class="code-input" dir="ltr" inputmode="numeric" maxlength="7"></label>
      <button class="btn primary" id="totp-on">${icon('check')}تأیید و فعال‌سازی</button></div>`;
    $('#totp-on').addEventListener('click', async (ev) => {
      try {
        await busy(ev.currentTarget, () => api('account/totp/enable', { method: 'POST', body: { code: $('#totp-box [name=code]').value } }));
        toast('تأیید دومرحله‌ای فعال شد');
        route();
      } catch (error) { toast(errorText(error), 'bad'); }
    });
  });
}

// ── Start ──────────────────────────────────────────────────────────────────────────────────────

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  $('#theme use').setAttribute('href', theme === 'dark' ? '#i-sun' : '#i-moon');
  try { localStorage.setItem('pca-theme', theme); } catch { /* private window */ }
}

function wire() {
  let saved = 'dark';
  try { saved = localStorage.getItem('pca-theme') || 'dark'; } catch { /* private window */ }
  applyTheme(saved);
  $('#theme').addEventListener('click', () => applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'));
  $('#refresh').addEventListener('click', () => route());
  $('#quick-compose').addEventListener('click', async () => {
    try { state.cache.coupons = (await api('coupons')).coupons; } catch { /* the composer works without */ }
    openComposer();
  });
  $('#menu').addEventListener('click', () => $('#shell').classList.add('nav-open'));
  $('#scrim').addEventListener('click', () => $('#shell').classList.remove('nav-open'));
  $('#logout').addEventListener('click', async () => {
    try { await api('logout', { method: 'POST', body: {} }); } catch { /* signed out anyway */ }
    showLogin();
  });
  window.addEventListener('hashchange', route);
  document.addEventListener('keydown', (e) => {
    if (e.target.closest('input, textarea, select') || e.metaKey || e.ctrlKey || e.altKey || $('#modal-root').children.length || !state.csrf) return;
    if (e.key === 'r') route();
    if (e.key === 'n') $('#quick-compose').click();
  });
  document.addEventListener('click', (e) => {
    const c = e.target.closest('[data-copy]');
    if (c) copy(c.dataset.copy);
  });

  const form = $('#login-form');
  $('[data-toggle-password]').addEventListener('click', () => {
    const input = form.elements.password;
    input.type = input.type === 'password' ? 'text' : 'password';
  });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const error = $('#login-error');
    error.textContent = '';
    const body = { username: form.elements.username.value.trim(), password: form.elements.password.value };
    if (!$('#step-code').hidden) body.code = form.elements.code.value;
    try {
      await busy($('#login-submit'), () => api('login', { method: 'POST', body }));
      form.elements.password.value = '';
      form.elements.code.value = '';
      await enter();
    } catch (err) {
      if (err.data?.need_code) {
        $('#step-code').hidden = false;
        form.elements.code.focus();
        return;
      }
      error.textContent = err.code === 'locked' && err.data?.retry_after
        ? `حساب قفل است؛ ${fmt.fa(Math.ceil(err.data.retry_after / 60))} دقیقهٔ دیگر دوباره امتحان کنید.`
        : errorText(err);
    }
  });

  tickClock();
  setInterval(tickClock, 1000);
}

document.addEventListener('DOMContentLoaded', async () => {
  wire();
  try { await enter(); } catch { showLogin(); }
});
