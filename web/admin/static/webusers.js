/* «کاربران وب»: the readers of the web app (pro-chart.com/next) and their panel, for the owner.
 * Loaded after app.js and built from its pieces (api, kpi, timeChart, modal…). The page's CSP allows
 * only this origin's scripts and no inline styles, so sizes go through hydrate() as in app.js. */
'use strict';

const WEB_TABS = [['live', 'زنده و آمار', 'pulse'], ['accounts', 'حساب‌ها', 'users'], ['trades', 'معاملات', 'bolt'],
  ['tickets', 'تیکت‌ها', 'ticket'], ['errors', 'خطاها', 'alert'], ['messages', 'پیام به کاربران', 'megaphone']];
const WEB_EVENTS = {
  signin: ['good', 'ورود'], signout: ['plain', 'خروج'], signin_failed: ['bad', 'ورود ناموفق'], refresh_failed: ['warn', 'پایان نشست'],
  session_revoked: ['warn', 'بستن نشست'], trade: ['accent', 'معامله'], error: ['bad', 'خطای صفحه'], feature: ['info', 'قابلیت'],
  page: ['plain', 'صفحه'], erase: ['bad', 'پاک کردن داده‌ها'],
};
const WEB_ACTIONS = {
  order: 'سفارش تازه', amend: 'ویرایش سفارش', cancel: 'لغو سفارش', close: 'بستن پوزیشن', protection: 'حد سود و ضرر',
  leverage: 'تغییر اهرم', execution: 'اجرای سیگنال VIP', execution_close: 'بستن اجرای سیگنال', venue_connect: 'اتصال صرافی',
  venue_disconnect: 'قطع اتصال صرافی',
};
const WEB_KINDS = { layouts: 'چیدمان‌ها', settings: 'تنظیمات چارت', drawings: 'ترسیم‌ها', templates: 'اندیکاتورها و اسکریپت‌ها', watchlist: 'دیده‌بان‌ها' };
const WEB_TICKET = { open: ['warn', 'باز'], answered: ['good', 'پاسخ داده شد'], closed: ['plain', 'بسته'] };
const WEB_CATEGORY = { account: 'حساب', billing: 'پرداخت', trading: 'معامله', bug: 'خطا', idea: 'پیشنهاد', other: 'سایر' };
const WEB_DEVICE = { phone: 'گوشی', tablet: 'تبلت', desktop: 'رایانه' };
const WEB_BACKEND = { tradeyar: 'TradeYar', coineprofx: 'CoinePro-FX' };
const WEB_INBOX_KIND = { info: 'اطلاعیه', offer: 'پیشنهاد', warning: 'هشدار', update: 'به‌روزرسانی' };

Object.assign(ERRORS, {
  panel_off: 'بخش کاربران وب خاموش است: PANEL_INTERNAL_TOKEN (دست‌کم ۳۲ نویسه) را روی کنسول و رله تنظیم کنید.',
  relay_unreachable: 'رله‌ی وب در دسترس نیست.',
  web_account: 'این حساب وب پیدا نشد.',
  version: 'این نسخه دیگر نگه داشته نمی‌شود.',
  body: 'متن پاسخ را بنویسید.',
  status: 'وضعیت نامعتبر است.',
});
Object.assign(ACTIONS, {
  'web.revoke': ['warn', 'بستن نشست وب کاربر'], 'web.note': ['plain', 'یادداشت کاربر وب'], 'web.refresh': ['info', 'استعلام وضعیت از سرورها'],
  'web.message': ['accent', 'پیام در پنل کاربر'], 'web.ticket_reply': ['accent', 'پاسخ تیکت'], 'web.ticket_status': ['info', 'تغییر وضعیت تیکت'],
  'web.workspace_view': ['info', 'دیدن نسخه‌ی فضای کار'], 'web.workspace_restore': ['warn', 'بازگردانی فضای کار'],
  'web.export_trades': ['info', 'خروجی معاملات وب'], 'web.export_accounts': ['info', 'خروجی حساب‌های وب'], 'web.erase': ['bad', 'پاک کردن داده‌ها توسط کاربر'],
});

const webTab = () => sessionStorage.getItem('web-tab') || 'live';
const devLabel = (s) => [s.browser, s.os].filter(Boolean).join(' · ') + (s.device ? ` · ${WEB_DEVICE[s.device] || s.device}` : '');
const aliasPill = (alias) => `<span class="pill plain mono">${esc(alias)}</span>`;
const webEvent = (kind) => { const [cls, label] = WEB_EVENTS[kind] || ['plain', kind]; return `<span class="pill ${cls}">${esc(label)}</span>`; };
const tradeState = (t) => (t.ok ? `<span class="pill good plain num">${t.status}</span>` : `<span class="pill bad plain num">${t.status || '—'}</span>`);
const sideCell = (side) => (side ? `<span class="side ${side === 'buy' || side === 'long' ? 'buy' : 'sell'}">${esc(side.toUpperCase())}</span>` : '—');
const accountWho = (a) => `<div class="who"><span class="avatar sm">${esc((a.name || a.email || a.account || '?').trim().slice(0, 1).toUpperCase())}</span>
  <div><strong>${esc(a.name || a.email || a.account)}</strong><span class="mono faint">${esc(a.email || a.account)}</span></div></div>`;

function membershipPills(a) {
  const out = [];
  if (a.online) out.push('<span class="pill good live">آنلاین</span>');
  if (a.vip) out.push('<span class="pill accent">VIP</span>');
  if (a.paid || (a.premium_until && Date.parse(a.premium_until) > Date.now())) out.push('<span class="pill info">پریمیوم</span>');
  if (a.plan && !a.vip && !a.paid) out.push(`<span class="pill plain">${esc(a.plan)}</span>`);
  return out.join(' ') || '<span class="faint">—</span>';
}

function webTabs(current) {
  return `<div class="toolbar web-tabs"><div class="segmented" id="web-tabs">${WEB_TABS.map(([k, l, i]) => `<button data-t="${k}" class="${k === current ? 'on' : ''}">${icon(i)}<span>${l}</span></button>`).join('')}</div>
    <span class="grow"></span><span class="faint">حساب‌های وب از TradeYar و CoinePro-FX، از طریق رله‌ی pro-chart.com</span></div>`;
}

async function renderWeb(host) {
  const tab = WEB_TABS.some(([k]) => k === webTab()) ? webTab() : 'live';
  const body = document.createElement('div');
  body.className = 'grid';
  host.innerHTML = webTabs(tab);
  host.append(body);
  $('#web-tabs', host).addEventListener('click', (e) => {
    const b = e.target.closest('[data-t]');
    if (!b) return;
    sessionStorage.setItem('web-tab', b.dataset.t);
    route();
  });
  await ({ live: webLive, accounts: webAccounts, trades: webTrades, tickets: webTickets, errors: webErrors, messages: webMessages }[tab])(body);
}

// ── Live and figures ───────────────────────────────────────────────────────────────────────────

async function webLive(host) {
  const d = await api('web/overview');
  const k = d.kpis;
  host.innerHTML = `
    ${d.relay.ok ? '' : `<div class="notice bad">${icon('server')}<span>رله‌ی وب جواب نداد (${esc(errorText({ code: d.relay.error || String(d.relay.status) }))}). «آنلاین» و نشست‌ها از رله می‌آیند؛ بقیه‌ی آمار از همین کنسول است.</span></div>`}
    <section class="hero">
      <div><h2><span class="status-dot ${d.online_now ? 'ok' : ''}"></span>&nbsp; ${fmt.fa(d.online_now)} کاربر آنلاین</h2>
        <p>نشست‌هایی که در ۵ دقیقه‌ی اخیر دیده شده‌اند · ${fmt.fa(d.live_sessions)} نشست باز · ${fmt.fa(d.guests)} مهمان بدون حساب</p></div>
      <div class="hero-stats">
        <div><strong>${fmt.n(k.accounts)}</strong><span>حساب وب</span></div>
        <div><strong>${fmt.n(k.active_1d)}</strong><span>فعال امروز</span></div>
        <div><strong>${fmt.n(k.vip)}</strong><span>VIP</span></div>
        <div><strong>${fmt.n(k.synced)}</strong><span>فضای کار ابری</span></div>
      </div>
    </section>
    <div class="grid g-4">
      ${kpi({ label: 'ورود امروز', value: fmt.n(k.signins_today), iconName: 'user', color: '#2ad4c4', foot: `۷ روز: <b class="num">${fmt.n(k.signins_7d)}</b> · ناموفق ۲۴ ساعت: <b class="num">${fmt.n(k.failed_24h)}</b>`, spark: d.series.signins.slice(-14) })}
      ${kpi({ label: 'معامله‌ی ۲۴ ساعت', value: fmt.n(k.trades_24h), iconName: 'bolt', color: '#8b7bff', foot: `ردشده: <b class="num">${fmt.n(k.trades_failed_24h)}</b>`, spark: d.series.trades.slice(-14) })}
      ${kpi({ label: 'خطای صفحه (۲۴ ساعت)', value: fmt.n(k.errors_24h), iconName: 'alert', color: '#f0506e', foot: 'از مرورگر کاربران وارد شده', spark: d.series.errors.slice(-14) })}
      ${kpi({ label: 'تیکت باز', value: fmt.n(k.open_tickets), iconName: 'ticket', color: '#f5a524', foot: `منتظر پاسخ: <b class="num">${fmt.n(k.waiting_tickets)}</b>` })}
    </div>
    <div class="grid g-main">
      <div class="card"><div class="card-head"><div><h3>۳۰ روز اخیر</h3><p>ورود، معامله‌ی ثبت‌شده از وب و خطای صفحه</p></div></div><div id="web-chart"></div></div>
      <div class="card flush"><div class="card-head"><div><h3>آنلاین همین حالا</h3><p>از حافظه‌ی رله</p></div><span class="pill ${d.relay.ok ? 'good' : 'bad'}">${d.relay.ok ? 'رله وصل است' : 'رله در دسترس نیست'}</span></div>
        <div class="web-online">${d.online.length ? d.online.map((s) => `<button class="web-online-row" data-account="${esc((s.accounts || [])[0] || '')}">
          <span class="status-dot ok"></span><span><strong class="mono">${esc((s.accounts || []).join(' · '))}</strong><small class="faint">${esc(devLabel(s))}</small></span>
          <span class="faint">${country(s.country)}</span><span class="faint">${ago(s.last_seen)}</span></button>`).join('') : empty('الان کسی آنلاین نیست', '', 'users')}</div></div>
    </div>
    <div class="grid g-3">
      <div class="card"><div class="card-head"><h3>دستگاه</h3></div>${donut(d.devices, { label: 'نشست', labelOf: (l) => WEB_DEVICE[l] || l })}</div>
      <div class="card"><div class="card-head"><h3>مرورگر</h3></div>${donut(d.browsers, { label: 'نشست' })}</div>
      <div class="card"><div class="card-head"><h3>سیستم‌عامل</h3></div>${donut(d.systems, { label: 'نشست' })}</div>
    </div>
    <div class="grid g-2">
      <div class="card"><div class="card-head"><h3>کشور</h3></div>${barList(d.countries, { labelOf: country })}</div>
      <div class="card"><div class="card-head"><div><h3>قابلیت‌ها و صفحه‌های پرکاربرد</h3><p>۷ روز اخیر، از گزارش خود صفحه</p></div></div>${barList(d.features, { labelOf: (l) => `<span class="mono">${esc(l)}</span>` })}</div>
    </div>`;
  hydrate(host);
  timeChart($('#web-chart', host), { labels: d.days, series: [
    { name: 'ورود', color: '#2ad4c4', values: d.series.signins },
    { name: 'معامله', color: '#8b7bff', values: d.series.trades, area: false },
    { name: 'خطا', color: '#f0506e', values: d.series.errors, area: false, dash: true },
  ] });
  $$('[data-account]', host).forEach((b) => b.dataset.account && b.addEventListener('click', () => openWebAccount(b.dataset.account)));
}

// ── Accounts ───────────────────────────────────────────────────────────────────────────────────

async function webAccounts(host) {
  const filters = JSON.parse(sessionStorage.getItem('web-filters') || '{}');
  const page = +(filters.page || 1);
  const params = new URLSearchParams({ page, size: 50 });
  ['q', 'backend', 'filter', 'sort'].forEach((k) => filters[k] && params.set(k, filters[k]));
  const d = await api(`web/accounts?${params}`);
  const select = (name, options) => `<select class="input" name="${name}">${options.map(([v, l]) => `<option value="${v}" ${(filters[name] || '') === v ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
  const csv = new URLSearchParams(params); csv.delete('page'); csv.delete('size');
  host.innerHTML = `
    <div class="card flush">
      <div class="card-head"><div><h3>${fmt.fa(d.total)} حساب وب</h3><p>هر حسابی که دست‌کم یک‌بار در وب وارد شده؛ روی هر ردیف بزنید</p></div>
        <a class="btn sm" href="/admin/api/web/accounts.csv?${csv}" download>${icon('csv')}خروجی CSV</a></div>
      <form class="toolbar" id="wa-filter" autocomplete="off">
        <label class="search grow">${icon('search')}<input class="input" name="q" placeholder="ایمیل، نام یا شناسه (tradeyar:7)" value="${esc(filters.q || '')}"></label>
        ${select('backend', [['', 'هر دو سرور'], ['tradeyar', 'TradeYar'], ['coineprofx', 'CoinePro-FX']])}
        ${select('filter', [['', 'همه'], ['online', 'آنلاین'], ['active7', 'فعال ۷ روز'], ['vip', 'VIP'], ['premium', 'پریمیوم'], ['traders', 'معامله در ۳۰ روز'], ['tickets', 'تیکت باز'], ['errors', 'خطا در ۷ روز']])}
        ${select('sort', [['', 'آخرین فعالیت'], ['new', 'جدیدترین'], ['signins', 'بیشترین ورود'], ['trades', 'بیشترین معامله'], ['email', 'ایمیل']])}
        <button class="btn sm primary">فیلتر</button><button class="btn sm ghost" type="button" id="wa-reset">پاک کردن</button>
      </form>
      ${d.relay_ok ? '' : `<div class="callout warn">${icon('server')}<span>رله در دسترس نیست؛ ستون «آنلاین» خالی است.</span></div>`}
      <div class="table-wrap"><table class="users">
        <thead><tr><th>کاربر</th><th>حساب‌ها</th><th>وضعیت</th><th>آخرین فعالیت</th><th>دستگاه</th><th>ورود</th><th>معامله ۳۰ روز</th><th>تیکت</th><th>خطا ۷ روز</th></tr></thead>
        <tbody>${d.accounts.map((a) => `<tr class="clickable" data-account="${esc(a.account)}">
          <td>${accountWho(a)}</td><td><div class="chips">${a.aliases.map(aliasPill).join('')}</div></td>
          <td>${membershipPills(a)}</td><td>${ago(a.last_seen)}</td>
          <td><span class="faint">${esc(devLabel({ browser: a.last_browser, os: a.last_os, device: a.last_device }) || '—')}</span> ${a.last_country ? country(a.last_country) : ''}</td>
          <td class="num">${fmt.n(a.sign_ins)}</td><td class="num">${fmt.n(a.trades_30d)}</td>
          <td class="num">${a.open_tickets ? `<span class="pill warn plain">${a.open_tickets}</span>` : '0'}</td>
          <td class="num">${a.errors_7d ? `<span class="pill bad plain">${a.errors_7d}</span>` : '0'}</td></tr>`).join('')}</tbody></table>
        ${d.accounts.length ? '' : empty('حسابی پیدا نشد', 'پس از راه‌اندازی رله با PANEL_INTERNAL_TOKEN، هر ورود به وب اینجا ثبت می‌شود', 'users')}</div>
      <div class="pager"><span>${fmt.fa(Math.min(d.total, (page - 1) * 50 + 1))}–${fmt.fa(Math.min(d.total, (page - 1) * 50 + d.accounts.length))} از ${fmt.fa(d.total)}</span>
        <span><button class="btn sm" id="wa-prev" ${page > 1 ? '' : 'disabled'}>قبلی</button> <button class="btn sm" id="wa-next" ${page * 50 < d.total ? '' : 'disabled'}>بعدی</button></span></div>
    </div>`;
  const save = (next) => { sessionStorage.setItem('web-filters', JSON.stringify(next)); route(); };
  $('#wa-filter', host).addEventListener('submit', (e) => { e.preventDefault(); save({ ...Object.fromEntries(new FormData(e.target)), page: 1 }); });
  $('#wa-reset', host).addEventListener('click', () => save({}));
  $('#wa-prev', host).addEventListener('click', () => save({ ...filters, page: page - 1 }));
  $('#wa-next', host).addEventListener('click', () => save({ ...filters, page: page + 1 }));
  $$('tr[data-account]', host).forEach((row) => row.addEventListener('click', () => openWebAccount(row.dataset.account)));
}

function bridgeCards(d) {
  // The servers' own word, through the admin bridges, when they answer; otherwise what each
  // server's `me` said when the reader last signed in on the web.
  const fresh = d.bridges.filter((b) => b.bridge_id);
  const cached = (d.membership || []).filter((m) => m.membership);
  const rows = fresh.length ? fresh : cached.length ? cached.map((m) => ({ ...m, cached: true }))
    : d.aliases.map((a) => ({ alias: a.alias, backend: a.backend, membership: { vip: !!a.vip, paid: !!a.paid, plan: a.plan }, hint: true,
      error: (d.bridges.find((b) => b.alias === a.alias) || {}).error, configured: (d.bridges.find((b) => b.alias === a.alias) || {}).configured }));
  if (!rows.length) return empty('هنوز از سرورها استعلام نشده', 'دکمه‌ی «استعلام از سرورها» را بزنید', 'server');
  return rows.map((b) => {
    const m = b.membership || {};
    const kv = [
      ['سرور', esc(WEB_BACKEND[b.backend || (b.alias || '').split(':')[0]] || b.alias)],
      ['VIP', m.vip ? '<span class="pill accent">VIP</span>' : 'ندارد'],
      ['پرداختی', m.paid ? '<span class="pill info">دارد</span>' : 'ندارد'],
      ['پلن', esc(m.plan || b.plan || '—')],
      ['پریمیوم تا', b.premium_until ? when(isoEpoch(b.premium_until)) : '—'],
      ...(b.banned ? [['وضعیت', '<span class="pill bad">مسدود</span>']] : []),
      ...(b.bridge_id ? [['شناسه در سرور', `<span class="num">${b.bridge_id}</span>`]] : []),
      ...(b.error ? [['استعلام', `<span class="faint">${esc(b.error === 'not_found' ? 'با این ایمیل پیدا نشد' : errorText({ code: b.error }))}</span>`]] : []),
      ...(b.configured === false ? [['استعلام', '<span class="faint">پل این سرور تنظیم نشده</span>']] : []),
    ];
    const note = b.cached ? 'از آخرین استعلام' : b.hint ? 'از پاسخ سرور هنگام ورود' : 'از پل مدیریت سرور';
    return `<div class="card inset"><div class="card-head"><h3 class="mono">${esc(b.alias)}</h3><span class="faint">${note}</span></div>
      <dl class="kv mixed">${kv.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}</dl></div>`;
  }).join('');
}

async function openWebAccount(account, tab = 'timeline') {
  let d;
  try { d = await api(`web/accounts/${encodeURIComponent(account)}`); } catch (error) { toast(errorText(error), 'bad'); return; }
  const a = d.account;
  const m = modal(`
    <div class="modal-head"><div class="who lg"><span class="avatar">${esc((a.name || a.email || '?').slice(0, 1).toUpperCase())}</span>
      <div><h3>${esc(a.name || a.email || account)}</h3><span class="mono faint">${esc(a.email || '')} · ${esc(account)}</span></div></div>
      <button class="icon-btn ghost" data-close>${icon('x')}</button></div>
    <div class="modal-body">
      <div class="chips">${membershipPills({ ...a, online: d.sessions.some((s) => Date.now() / 1000 - s.last_seen <= 300) })} ${d.aliases.map((x) => aliasPill(x.alias)).join(' ')}</div>
      <div class="grid g-4">
        ${kpi({ label: 'ورود', value: fmt.n(a.sign_ins), iconName: 'user', color: '#2ad4c4', foot: `از ${when(a.first_seen)}` })}
        ${kpi({ label: 'معامله ۳۰ روز', value: fmt.n(d.counts.trades_30d), iconName: 'bolt', color: '#8b7bff', foot: `ردشده: <b class="num">${fmt.n(d.counts.trades_failed_30d)}</b>` })}
        ${kpi({ label: 'خطای ۷ روز', value: fmt.n(d.counts.errors_7d), iconName: 'alert', color: '#f0506e', foot: `ورود ناموفق ۳۰ روز: <b class="num">${fmt.n(d.counts.failed_signins_30d)}</b>` })}
        ${kpi({ label: 'نشست باز وب', value: fmt.n(d.sessions.length), iconName: 'globe', color: '#5b8cff', foot: d.relay_ok ? `آخرین فعالیت ${ago(a.last_seen)}` : 'رله در دسترس نیست' })}
      </div>
      <div class="grid g-2">
        <div class="card inset"><div class="card-head"><h3>وضعیت VIP و پریمیوم</h3><button class="btn sm" data-act="refresh">${icon('refresh')}استعلام از سرورها</button></div>
          <div class="grid">${bridgeCards(d)}</div></div>
        <div class="card inset grid"><div class="card-head"><h3>اقدام‌ها</h3></div>
          <div class="toolbar">
            <button class="btn sm danger" data-act="revoke-all" ${d.sessions.length ? '' : 'disabled'}>${icon('logout')}خروج از همه‌ی نشست‌های وب</button>
            <button class="btn sm" data-act="message">${icon('bell')}پیام در پنل</button>
          </div>
          <label class="field"><span>یادداشت مالک (فقط در همین کنسول)</span><textarea id="wa-note" maxlength="2000">${esc(a.note || '')}</textarea></label>
          <div><button class="btn sm primary" data-act="note">${icon('check')}ذخیره‌ی یادداشت</button></div>
          ${d.features.length ? `<div class="section-title">پرکاربردترین‌ها (۳۰ روز)</div>${barList(d.features, { labelOf: (l) => `<span class="mono">${esc(l)}</span>` })}` : ''}
        </div>
      </div>
      <div class="tabs" id="wa-tabs">${[['timeline', 'خط زمان'], ['sessions', 'نشست‌های زنده'], ['trades', 'معاملات'], ['workspace', 'فضای کار ابری'], ['tickets', 'تیکت‌ها']]
        .map(([k, l]) => `<button data-wt="${k}" class="${k === tab ? 'on' : ''}">${l}</button>`).join('')}</div>
      <div id="wa-pane"></div>
    </div>`, { size: 'lg' });
  const pane = $('#wa-pane', m.el);
  const reopen = (next = tab) => { m.close(); openWebAccount(account, next); };
  const show = async (which) => {
    tab = which;
    $$('#wa-tabs button', m.el).forEach((b) => b.classList.toggle('on', b.dataset.wt === which));
    pane.innerHTML = '<div class="card inset skeleton web-pane-wait"></div>';
    try {
      if (which === 'timeline') await paneTimeline(pane, account);
      if (which === 'sessions') paneSessions(pane, d, account, () => reopen('sessions'));
      if (which === 'trades') await paneTrades(pane, account);
      if (which === 'workspace') paneWorkspace(pane, d, account, () => reopen('workspace'));
      if (which === 'tickets') paneTickets(pane, d);
    } catch (error) { pane.innerHTML = `<div class="card inset">${empty('بارگذاری نشد', errorText(error), 'alert')}</div>`; }
    hydrate(pane);
  };
  $('#wa-tabs', m.el).addEventListener('click', (e) => { const b = e.target.closest('[data-wt]'); if (b) show(b.dataset.wt); });
  $$('[data-act]', m.el).forEach((b) => b.addEventListener('click', async () => {
    const act = b.dataset.act;
    try {
      if (act === 'refresh') { await busy(b, () => api(`web/accounts/${encodeURIComponent(account)}/refresh`, { method: 'POST', body: {} })); toast('وضعیت از سرورها خوانده شد'); reopen(); }
      if (act === 'note') { await busy(b, () => api(`web/accounts/${encodeURIComponent(account)}/note`, { method: 'PUT', body: { note: $('#wa-note', m.el).value } })); toast('یادداشت ذخیره شد'); }
      if (act === 'message') openWebMessage(account);
      if (act === 'revoke-all') {
        if (!(await confirmBox('خروج از همه‌ی نشست‌های وب', 'همه‌ی مرورگرهایی که این کاربر در آن‌ها وارد وب شده، همین حالا خارج می‌شوند. نشست‌های اپ اندروید دست نمی‌خورند.', { danger: true, ok: 'خارج کن' }))) return;
        const r = await api(`web/accounts/${encodeURIComponent(account)}/revoke`, { method: 'POST', body: {} });
        toast(`${fmt.fa(r.revoked)} نشست بسته شد`);
        reopen('sessions');
      }
    } catch (error) { toast(errorText(error), 'bad'); }
  }));
  show(tab);
}

async function paneTimeline(pane, account, kind = '') {
  const d = await api(`web/accounts/${encodeURIComponent(account)}/timeline?limit=200${kind ? `&kind=${kind}` : ''}`);
  pane.innerHTML = `<div class="card inset flush"><div class="card-head"><div class="segmented" id="wt-kind">${[['', 'همه'], ['signin', 'ورود'], ['trade', 'معامله'], ['error', 'خطا'], ['feature', 'قابلیت'], ['page', 'صفحه'], ['session_revoked', 'بستن نشست']]
    .map(([k, l]) => `<button data-k="${k}" class="${k === kind ? 'on' : ''}">${l}</button>`).join('')}</div></div>
    <div class="table-wrap"><table><thead><tr><th>زمان</th><th>رویداد</th><th>جزئیات</th><th>دستگاه</th><th>نشست</th></tr></thead><tbody>
    ${d.items.map((e) => `<tr><td>${when(e.at)}<div class="faint">${ago(e.at)}</div></td><td>${webEvent(e.kind)} <span class="mono faint">${esc(e.name)}</span></td>
      <td class="mono faint web-detail">${esc(e.detail ? JSON.stringify(e.detail).slice(0, 220) : '')}</td>
      <td class="faint">${esc(devLabel(e) || '—')} ${e.country ? country(e.country) : ''}</td><td class="mono faint">${esc((e.sid || '').slice(0, 8))}</td></tr>`).join('')}
    </tbody></table>${d.items.length ? '' : empty('رویدادی ثبت نشده', '', 'list')}</div></div>`;
  $('#wt-kind', pane).addEventListener('click', (e) => { const b = e.target.closest('[data-k]'); if (b) paneTimeline(pane, account, b.dataset.k).catch((error) => toast(errorText(error), 'bad')); });
}

function paneSessions(pane, d, account, after) {
  pane.innerHTML = `<div class="card inset flush"><div class="card-head"><div><h3>نشست‌های وب که الان باز است</h3><p>از حافظه‌ی رله؛ بستن یک نشست، آن مرورگر را همین حالا خارج می‌کند</p></div></div>
    <div class="table-wrap"><table><thead><tr><th>مرورگر و دستگاه</th><th>کشور</th><th>شروع</th><th>آخرین فعالیت</th><th>حساب‌ها</th><th></th></tr></thead><tbody>
    ${d.sessions.map((s) => `<tr><td>${esc(devLabel(s))}<div class="mono faint">${esc(s.sid)}</div></td><td>${country(s.country)}</td><td>${when(s.created)}</td>
      <td>${Date.now() / 1000 - s.last_seen <= 300 ? '<span class="pill good live">آنلاین</span>' : ago(s.last_seen)}</td>
      <td>${(s.accounts || []).map(aliasPill).join(' ')}</td><td><button class="btn sm danger ghost" data-sid="${esc(s.sid)}">${icon('logout')}بستن</button></td></tr>`).join('')}
    </tbody></table>${d.sessions.length ? '' : empty(d.relay_ok ? 'نشست بازی نیست' : 'رله در دسترس نیست', '', 'globe')}</div></div>`;
  $$('[data-sid]', pane).forEach((b) => b.addEventListener('click', async () => {
    try {
      await busy(b, () => api(`web/accounts/${encodeURIComponent(account)}/revoke`, { method: 'POST', body: { sid: b.dataset.sid } }));
      toast('نشست بسته شد');
      after();
    } catch (error) { toast(errorText(error), 'bad'); }
  }));
}

function tradeTable(items, withAccount = false) {
  return `<div class="table-wrap"><table><thead><tr><th>زمان</th>${withAccount ? '<th>حساب</th>' : ''}<th>اقدام</th><th>نماد</th><th>جهت</th><th>مقدار</th><th>نوع</th><th>قیمت</th><th>صرافی</th><th>پاسخ</th><th>شناسه‌ی سفارش</th><th>دستگاه</th></tr></thead><tbody>
    ${items.map((t) => `<tr><td>${when(t.at)}</td>${withAccount ? `<td>${t.account ? `<button class="link-btn mono" data-account="${esc(t.account)}">${esc(t.account)}</button>` : '<span class="faint">بی‌نام</span>'}</td>` : ''}
      <td>${esc(WEB_ACTIONS[t.action] || t.action)}<div class="mono faint">${esc(t.method)} ${esc(t.path)}</div></td>
      <td class="num"><strong>${esc(t.symbol || '—')}</strong></td><td>${sideCell(t.side)}</td><td class="num">${t.quantity ?? '—'}</td><td>${esc(t.type || '—')}</td>
      <td class="num">${t.price ?? '—'}</td><td>${esc(t.venue || t.backend || '—')}</td>
      <td>${tradeState(t)}${t.detail ? `<div class="faint web-detail">${esc(t.detail)}</div>` : ''}</td><td class="mono">${esc(t.order_id || '—')}</td><td class="faint">${esc(WEB_DEVICE[t.device] || t.device || '—')}</td></tr>`).join('')}
    </tbody></table>${items.length ? '' : empty('معامله‌ای ثبت نشده', 'سفارش‌هایی که از چارت وب فرستاده شوند اینجا می‌آیند', 'bolt')}</div>`;
}

async function paneTrades(pane, account) {
  const d = await api(`web/trades?account=${encodeURIComponent(account)}&size=200`);
  pane.innerHTML = `<div class="card inset flush"><div class="card-head"><div><h3>${fmt.fa(d.total)} معامله</h3><p>${fmt.fa(d.ok)} پذیرفته‌شده</p></div>
    <a class="btn sm" href="/admin/api/web/trades.csv?account=${encodeURIComponent(account)}" download>${icon('csv')}CSV</a></div>${tradeTable(d.items)}</div>`;
}

function paneWorkspace(pane, d, account, after) {
  const versions = (kind) => d.workspace_versions.filter((v) => v.kind === kind);
  pane.innerHTML = `<div class="grid g-2">${d.workspace.map((w) => `<div class="card inset"><div class="card-head"><div><h3>${esc(WEB_KINDS[w.kind] || w.kind)}</h3>
      <p>${w.version ? `نسخه‌ی <b class="num">${w.version}</b> · ${fmt.bytes(w.size)} از ${fmt.bytes(w.limit)} · ${ago(w.updated_at)}` : 'هنوز همگام نشده'}</p></div></div>
      ${w.version ? `<div class="bar-track"><div class="bar-fill" data-w="${(w.size / w.limit) * 100}"></div></div>` : ''}
      ${versions(w.kind).length ? `<div class="table-wrap"><table><tbody>${versions(w.kind).map((v) => `<tr><td class="num">v${v.version}</td><td>${when(v.saved_at)}</td><td class="num faint">${fmt.bytes(v.size)}</td>
        <td class="faint">${esc(v.device || '')}${v.restored_from ? ` · از v${v.restored_from}` : ''}</td>
        <td><div class="msg-actions"><a class="btn sm ghost" href="/admin/api/web/accounts/${encodeURIComponent(account)}/workspace/${w.kind}/${v.version}" download>${icon('download')}</a>
        ${v.version !== w.version ? `<button class="btn sm" data-restore="${w.kind}:${v.version}">بازگردانی</button>` : '<span class="pill good plain">جاری</span>'}</div></td></tr>`).join('')}</tbody></table></div>` : ''}
    </div>`).join('')}</div>`;
  $$('[data-restore]', pane).forEach((b) => b.addEventListener('click', async () => {
    const [kind, version] = b.dataset.restore.split(':');
    if (!(await confirmBox('بازگردانی فضای کار', `نسخه‌ی ${version} از «${WEB_KINDS[kind]}» نسخه‌ی تازه‌ی جاری می‌شود. نسخه‌ی فعلی در تاریخچه می‌ماند و کاربر در همگام‌سازی بعدی آن را می‌گیرد.`, { ok: 'بازگردانی' }))) return;
    try { await api(`web/accounts/${encodeURIComponent(account)}/workspace/${kind}/restore`, { method: 'POST', body: { version: +version } }); toast('بازگردانده شد'); after(); }
    catch (error) { toast(errorText(error), 'bad'); }
  }));
}

function paneTickets(pane, d) {
  pane.innerHTML = `<div class="card inset flush">${ticketTable(d.tickets, false)}</div>`;
  bindTickets(pane);
}

// ── Trades across every account ────────────────────────────────────────────────────────────────

async function webTrades(host) {
  const f = JSON.parse(sessionStorage.getItem('web-trades') || '{}');
  const page = +(f.page || 1);
  const params = new URLSearchParams({ page, size: 100 });
  ['account', 'symbol', 'side', 'venue', 'status', 'backend', 'action'].forEach((k) => f[k] && params.set(k, f[k]));
  if (f.from) params.set('from', inputToEpoch(`${f.from}T00:00`));
  if (f.to) params.set('to', inputToEpoch(`${f.to}T23:59`) + 59);
  const d = await api(`web/trades?${params}`);
  const select = (name, options) => `<select class="input" name="${name}">${options.map(([v, l]) => `<option value="${v}" ${(f[name] || '') === v ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
  const csv = new URLSearchParams(params); csv.delete('page'); csv.delete('size');
  host.innerHTML = `
    <div class="grid g-4">
      ${kpi({ label: 'معامله در فیلتر', value: fmt.n(d.total), iconName: 'bolt', color: '#8b7bff' })}
      ${kpi({ label: 'پذیرفته‌شده', value: d.total ? fmt.pct(d.ok / d.total, 0) : '—', iconName: 'check', color: '#22c58b', foot: `<b class="num">${fmt.n(d.ok)}</b> از <b class="num">${fmt.n(d.total)}</b>` })}
      ${kpi({ label: 'حساب‌ها', value: fmt.n(d.accounts), iconName: 'users', color: '#2ad4c4' })}
      <div class="card"><div class="card-head"><h3>نمادها</h3></div>${barList(d.symbols, { labelOf: (l) => `<span class="num">${esc(l)}</span>` })}</div>
    </div>
    <div class="card flush">
      <div class="card-head"><div><h3>همه‌ی معاملات ثبت‌شده از وب</h3><p>هر سفارش، بستن، حد سود و ضرر، اهرم، اجرای سیگنال و اتصال صرافی که رله دیده؛ هرگز کلید یا رمز</p></div>
        <a class="btn sm" href="/admin/api/web/trades.csv?${csv}" download>${icon('csv')}خروجی CSV</a></div>
      <form class="toolbar" id="wt-filter" autocomplete="off">
        <input class="input" name="account" dir="ltr" placeholder="tradeyar:7" value="${esc(f.account || '')}">
        <input class="input" name="symbol" dir="ltr" placeholder="BTCUSDT" value="${esc(f.symbol || '')}">
        ${select('side', [['', 'هر جهت'], ['buy', 'خرید'], ['sell', 'فروش']])}
        ${select('status', [['', 'هر پاسخ'], ['ok', 'پذیرفته'], ['failed', 'رد شده']])}
        ${select('action', [['', 'هر اقدام'], ...Object.entries(WEB_ACTIONS)])}
        ${select('backend', [['', 'هر دو سرور'], ['tradeyar', 'TradeYar'], ['coineprofx', 'CoinePro-FX']])}
        <input class="input" name="venue" dir="ltr" placeholder="lbank" value="${esc(f.venue || '')}">
        <input class="input" type="date" name="from" dir="ltr" value="${esc(f.from || '')}" title="از تاریخ">
        <input class="input" type="date" name="to" dir="ltr" value="${esc(f.to || '')}" title="تا تاریخ">
        <button class="btn sm primary">فیلتر</button><button class="btn sm ghost" type="button" id="wt-reset">پاک کردن</button>
      </form>
      ${tradeTable(d.items, true)}
      <div class="pager"><span>صفحه‌ی ${fmt.fa(page)}</span><span><button class="btn sm" id="wt-prev" ${page > 1 ? '' : 'disabled'}>قبلی</button> <button class="btn sm" id="wt-next" ${page * 100 < d.total ? '' : 'disabled'}>بعدی</button></span></div>
    </div>`;
  hydrate(host);
  const save = (next) => { sessionStorage.setItem('web-trades', JSON.stringify(next)); route(); };
  $('#wt-filter', host).addEventListener('submit', (e) => { e.preventDefault(); save({ ...Object.fromEntries(new FormData(e.target)), page: 1 }); });
  $('#wt-reset', host).addEventListener('click', () => save({}));
  $('#wt-prev', host).addEventListener('click', () => save({ ...f, page: page - 1 }));
  $('#wt-next', host).addEventListener('click', () => save({ ...f, page: page + 1 }));
  $$('[data-account]', host).forEach((b) => b.addEventListener('click', () => openWebAccount(b.dataset.account)));
}

// ── Tickets ────────────────────────────────────────────────────────────────────────────────────

function ticketTable(items, withAccount = true) {
  return `<div class="table-wrap"><table><thead><tr><th>#</th><th>موضوع</th>${withAccount ? '<th>کاربر</th>' : ''}<th>دسته</th><th>وضعیت</th><th>آخرین تغییر</th></tr></thead><tbody>
    ${items.map((t) => `<tr class="clickable" data-ticket="${t.id}"><td class="num">${t.id}</td><td><strong>${esc(t.subject)}</strong> ${t.unread_owner && t.status !== 'closed' ? '<span class="pill warn">منتظر پاسخ</span>' : ''}</td>
      ${withAccount ? `<td>${esc(t.name || t.email || t.account)}<div class="mono faint">${esc(t.account)}</div></td>` : ''}
      <td>${esc(WEB_CATEGORY[t.category] || t.category)}</td><td><span class="pill ${WEB_TICKET[t.status][0]}">${WEB_TICKET[t.status][1]}</span></td><td>${ago(t.updated_at)}</td></tr>`).join('')}
    </tbody></table>${items.length ? '' : empty('تیکتی نیست', '', 'ticket')}</div>`;
}

function bindTickets(root) {
  $$('tr[data-ticket]', root).forEach((row) => row.addEventListener('click', () => openWebTicket(+row.dataset.ticket)));
}

async function webTickets(host) {
  const status = sessionStorage.getItem('web-ticket-status') || 'waiting';
  const q = sessionStorage.getItem('web-ticket-q') || '';
  const d = await api(`web/tickets?status=${status}&q=${encodeURIComponent(q)}`);
  host.innerHTML = `<div class="card flush"><div class="card-head"><div class="segmented" id="wk-status">${[['waiting', 'منتظر پاسخ'], ['open', `باز (${d.counts.open})`], ['answered', `پاسخ‌داده (${d.counts.answered})`], ['closed', `بسته (${d.counts.closed})`], ['', 'همه']]
    .map(([k, l]) => `<button data-s="${k}" class="${k === status ? 'on' : ''}">${l}</button>`).join('')}</div>
    <form id="wk-search"><label class="search">${icon('search')}<input class="input" name="q" placeholder="موضوع، ایمیل یا حساب" value="${esc(q)}"></label></form></div>
    ${ticketTable(d.items)}</div>`;
  $('#wk-status', host).addEventListener('click', (e) => { const b = e.target.closest('[data-s]'); if (!b) return; sessionStorage.setItem('web-ticket-status', b.dataset.s); route(); });
  $('#wk-search', host).addEventListener('submit', (e) => { e.preventDefault(); sessionStorage.setItem('web-ticket-q', e.target.q.value); route(); });
  bindTickets(host);
}

async function openWebTicket(id) {
  let d;
  try { d = await api(`web/tickets/${id}`); } catch (error) { toast(errorText(error), 'bad'); return; }
  const t = d.ticket;
  const m = modal(`
    <div class="modal-head"><div><h3>${esc(t.subject)}</h3><span class="faint">#${t.id} · ${esc(WEB_CATEGORY[t.category] || t.category)} · <button class="link-btn mono" data-account="${esc(t.account)}">${esc(d.account.email || t.account)}</button></span></div>
      <span class="pill ${WEB_TICKET[t.status][0]}">${WEB_TICKET[t.status][1]}</span><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
    <div class="modal-body">
      <div class="thread">${d.messages.map((msg) => `<div class="bubble ${msg.author === 'owner' ? 'owner' : 'user'}">
        <div class="bubble-head"><strong>${msg.author === 'owner' ? `پشتیبانی · ${esc(msg.operator || '')}` : 'کاربر'}</strong><span class="faint">${when(msg.at)}</span></div>
        <p>${esc(msg.body)}</p>
        ${msg.attachments.length ? `<div class="shots">${msg.attachments.map((a) => `<a href="/admin/api/web/attachments/${a.id}" target="_blank" rel="noopener"><img src="/admin/api/web/attachments/${a.id}" alt="${esc(a.name)}"></a>`).join('')}</div>` : ''}
      </div>`).join('')}</div>
      <label class="field"><span>پاسخ (در پنل کاربر و صندوق پیام او دیده می‌شود)</span><textarea id="wk-reply" maxlength="4000"></textarea></label>
      <p class="form-error" id="wk-error"></p>
    </div>
    <div class="modal-foot">
      ${t.status === 'closed' ? '<button class="btn" data-status="open">باز کردن دوباره</button>' : '<button class="btn" data-status="closed">بستن بدون پاسخ</button>'}
      <button class="btn" data-reply="close">${icon('check')}پاسخ و بستن</button>
      <button class="btn primary" data-reply="send">${icon('arrow')}ارسال پاسخ</button>
    </div>`, { size: 'lg' });
  $('[data-account]', m.el).addEventListener('click', () => { m.close(); openWebAccount(t.account, 'tickets'); });
  $$('[data-reply]', m.el).forEach((b) => b.addEventListener('click', async () => {
    const body = $('#wk-reply', m.el).value.trim();
    if (!body) { $('#wk-error', m.el).textContent = ERRORS.body; return; }
    try {
      await busy(b, () => api(`web/tickets/${id}/reply`, { method: 'POST', body: { body, close: b.dataset.reply === 'close' } }));
      toast('پاسخ فرستاده شد');
      m.close();
      if (state.view === 'web') route();
    } catch (error) { $('#wk-error', m.el).textContent = errorText(error); }
  }));
  $$('[data-status]', m.el).forEach((b) => b.addEventListener('click', async () => {
    try { await api(`web/tickets/${id}/status`, { method: 'POST', body: { status: b.dataset.status } }); m.close(); if (state.view === 'web') route(); }
    catch (error) { toast(errorText(error), 'bad'); }
  }));
}

// ── Errors ─────────────────────────────────────────────────────────────────────────────────────

async function webErrors(host) {
  const days = +(sessionStorage.getItem('web-err-days') || 7);
  const q = sessionStorage.getItem('web-err-q') || '';
  const d = await api(`web/errors?days=${days}&q=${encodeURIComponent(q)}`);
  host.innerHTML = `<div class="card flush"><div class="card-head"><div class="segmented" id="we-days">${[1, 7, 30].map((n) => `<button data-d="${n}" class="${n === days ? 'on' : ''}">${fmt.fa(n)} روز</button>`).join('')}</div>
    <form id="we-search"><label class="search">${icon('search')}<input class="input" name="q" placeholder="متن خطا" value="${esc(q)}"></label></form></div>
    <div class="table-wrap"><table><thead><tr><th>خطا</th><th>تعداد</th><th>کاربران</th><th>مرورگر</th><th>نسخه</th><th>آخرین بار</th></tr></thead><tbody>
    ${d.groups.map((g, i) => `<tr class="clickable" data-group="${i}"><td class="mono web-detail"><strong>${esc(g.name)}</strong></td><td class="num">${fmt.n(g.count)}</td><td class="num">${fmt.n(g.accounts.length)}</td>
      <td class="faint">${esc(Object.keys(g.browsers).slice(0, 2).join('، '))}</td><td class="num faint">${esc(Object.keys(g.versions).slice(0, 3).join(' '))}</td><td>${ago(g.last_at)}</td></tr>`).join('')}
    </tbody></table>${d.groups.length ? '' : empty('خطایی گزارش نشده', 'صفحه‌ی وب خطاهای خودش را فقط برای کاربران واردشده و بدون داده‌ی شخصی می‌فرستد', 'check')}</div></div>`;
  $('#we-days', host).addEventListener('click', (e) => { const b = e.target.closest('[data-d]'); if (!b) return; sessionStorage.setItem('web-err-days', b.dataset.d); route(); });
  $('#we-search', host).addEventListener('submit', (e) => { e.preventDefault(); sessionStorage.setItem('web-err-q', e.target.q.value); route(); });
  $$('tr[data-group]', host).forEach((row) => row.addEventListener('click', () => {
    const g = d.groups[+row.dataset.group];
    const recent = d.recent.filter((r) => r.name === g.name).slice(0, 20);
    modal(`<div class="modal-head"><h3 class="mono web-detail">${esc(g.name)}</h3><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
      <div class="modal-body"><div class="notes mono" dir="ltr">${esc(JSON.stringify(g.sample, null, 1))}</div>
      <div class="chips">${g.accounts.map((a) => `<button class="chip mono" data-account="${esc(a)}">${esc(a)}</button>`).join('')}</div>
      <div class="table-wrap"><table><tbody>${recent.map((r) => `<tr><td>${when(r.at)}</td><td class="mono">${esc(r.account || '—')}</td><td class="faint">${esc(devLabel(r))}</td></tr>`).join('')}</tbody></table></div></div>`, { size: 'lg' });
    $$('#modal-root [data-account]').forEach((b) => b.addEventListener('click', () => openWebAccount(b.dataset.account)));
  }));
}

// ── Messages to web readers ────────────────────────────────────────────────────────────────────

function openWebMessage(account = null) {
  const m = modal(`
    <div class="modal-head"><h3>${account ? 'پیام در پنل این کاربر' : 'پیام به همه‌ی کاربران وب'}</h3><button class="icon-btn ghost" data-close>${icon('x')}</button></div>
    <form class="modal-body" id="wm-form">
      ${account ? `<p class="muted mono">${esc(account)}</p>` : ''}
      <label class="field"><span>نوع</span><select name="kind">${Object.entries(WEB_INBOX_KIND).map(([k, l]) => `<option value="${k}">${l}</option>`).join('')}</select></label>
      <label class="field"><span>عنوان</span><input name="title" maxlength="120" required></label>
      <label class="field"><span>متن</span><textarea name="body" maxlength="2000" required></textarea></label>
      <label class="field"><span>پیوند (اختیاری)</span><input name="link" dir="ltr" placeholder="/next/panel/billing یا https://…"></label>
      <p class="form-error" id="wm-error"></p>
    </form>
    <div class="modal-foot"><button class="btn" data-close>انصراف</button><button class="btn primary" id="wm-send">${icon('bell')}ارسال</button></div>`, { size: 'sm' });
  $('#wm-send', m.el).addEventListener('click', async (e) => {
    const body = { ...Object.fromEntries(new FormData($('#wm-form', m.el))), account };
    if (!account && !(await confirmBox('ارسال به همه', 'این پیام در صندوق پنل همه‌ی کاربران وب می‌نشیند.', { ok: 'ارسال' }))) return;
    try {
      await busy(e.currentTarget, () => api('web/messages', { method: 'POST', body }));
      toast('پیام فرستاده شد');
      m.close();
      if (state.view === 'web' && webTab() === 'messages') route();
    } catch (error) { $('#wm-error', m.el).textContent = errorText(error); }
  });
}

async function webMessages(host) {
  const d = await api('web/messages');
  host.innerHTML = `<div class="card flush"><div class="card-head"><div><h3>پیام‌های پنل</h3><p>در صندوق «اعلان‌ها»ی پنل کاربر وب دیده می‌شوند، نه به‌صورت اعلان گوشی</p></div>
    <button class="btn primary" id="wm-new">${icon('plus')}پیام به همه</button></div>
    <div class="table-wrap"><table><thead><tr><th>زمان</th><th>به</th><th>نوع</th><th>عنوان</th><th>خوانده‌شده</th><th>فرستنده</th></tr></thead><tbody>
    ${d.items.map((x) => `<tr><td>${when(x.created_at)}</td><td>${x.account ? `<button class="link-btn mono" data-account="${esc(x.account)}">${esc(x.account)}</button>` : `<span class="pill accent">همه (${fmt.fa(d.accounts)})</span>`}</td>
      <td>${esc(WEB_INBOX_KIND[x.kind] || x.kind)}</td><td><strong>${esc(x.title)}</strong><div class="faint web-detail">${esc(x.body.slice(0, 140))}</div></td><td class="num">${fmt.n(x.reads)}</td><td>${esc(x.created_by || '')}</td></tr>`).join('')}
    </tbody></table>${d.items.length ? '' : empty('هنوز پیامی نفرستاده‌اید', '', 'megaphone')}</div></div>`;
  $('#wm-new', host).addEventListener('click', () => openWebMessage(null));
  $$('[data-account]', host).forEach((b) => b.addEventListener('click', () => openWebAccount(b.dataset.account)));
}

VIEWS.web = {
  title: 'کاربران وب', sub: 'آنلاین‌ها، حساب‌ها، معاملات، تیکت‌ها و خطاهای نسخه‌ی وب', render: renderWeb,
  // Only the live figures repaint themselves; a list being filtered must not be redrawn under the cursor.
  get refresh() { return webTab() === 'live' ? 30 : 0; },
};
