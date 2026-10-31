// Pro Chart's service worker: what makes the site installable, and what lets it open offline
// (the old terminal's worker, web/src/wasmJsMain/resources/sw.js, ported).
//
// Network first, always. Every file is asked for from the network every time, so a new release is
// on screen at the next load and nobody is stuck on an old one. The last good copy of each file is
// kept, and used only when the network does not answer — the page then opens as the phone does
// with no signal: the last prices and bars it saved, the offline banner, everything local working.
//
// Never cached: anything outside this app's own folder (BASE), any other origin, any request that
// is not a GET, and the relay's /api/* and the passthrough's /up/* wherever they sit. They are
// data, not the app, and a stale price must never pass as a live one.
const SCOPE = new URL('./', self.location).pathname;
// One cache per app folder, so the old terminal beside it on the same origin keeps its own.
const PREFIX = 'pro-chart-shell:';
const CACHE = `${PREFIX}${SCOPE}:v1`;

/** Whether the worker answers this request at all; everything else goes to the network untouched. */
function handles(request) {
  if (request.method !== 'GET') return false;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return false;
  const path = url.pathname;
  if (!path.startsWith(SCOPE)) return false;
  const rest = path.slice(SCOPE.length);
  if (/^(api|up)(\/|$)/.test(rest) || /^\/(api|up)(\/|$)/.test(path)) return false;
  if (rest === 'sw.js') return false;
  return true;
}

self.addEventListener('install', (event) => {
  // The shell itself, so a deep link opened offline later still has a page to answer with.
  event.waitUntil(caches.open(CACHE).then((cache) => cache.add(SCOPE)).catch(() => undefined).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((name) => name.startsWith(`${PREFIX}${SCOPE}:`) && name !== CACHE).map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (!handles(request)) return;
  event.respondWith((async () => {
    try {
      const response = await fetch(request);
      if (response.ok && response.type !== 'opaque') {
        const copy = response.clone();
        // A page load is the shell under any address: keep it under the folder too.
        const shell = request.mode === 'navigate' ? response.clone() : null;
        caches.open(CACHE).then((cache) => Promise.all([cache.put(request, copy), shell ? cache.put(SCOPE, shell) : null])).catch(() => undefined);
      }
      return response;
    } catch (offline) {
      const cached = await caches.match(request, { ignoreSearch: true, cacheName: CACHE });
      if (cached) return cached;
      // A deep link opened offline is still the app: answer with the shell.
      if (request.mode === 'navigate') {
        const shell = await caches.match(SCOPE, { cacheName: CACHE });
        if (shell) return shell;
      }
      return new Response('', { status: 504, statusText: 'offline' });
    }
  })());
});
