// The web app's service worker: what makes /terminal/ installable, and what lets it open offline.
//
// Network first, always. A bundle file is asked for from the network every time, so a new release
// is on screen at the next load and nobody is stuck on an old one (the fault Pro-Chart's first
// worker had, and the reason its second cached nothing). The last good copy of each file is kept,
// and used only when the network does not answer — the page then opens as the phone does with no
// signal: last prices, the offline banner, everything local still working.
//
// Never cached: anything outside /terminal/, and any request that is not a GET. The relay's /api/*
// and the passthrough's /up/* are data, not the app, and a stale price must never pass as a live one.
const CACHE = 'pro-chart-shell-v1';
const SCOPE = new URL('./', self.location).pathname;

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((name) => name !== CACHE).map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || !url.pathname.startsWith(SCOPE)) return;
  event.respondWith((async () => {
    try {
      const response = await fetch(request);
      if (response.ok) {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(request, copy)).catch(() => {});
      }
      return response;
    } catch (offline) {
      const cached = await caches.match(request, { ignoreSearch: true });
      if (cached) return cached;
      // A deep link opened offline is still the app: answer with the shell.
      if (request.mode === 'navigate') {
        const shell = await caches.match(SCOPE);
        if (shell) return shell;
      }
      return new Response('', { status: 504, statusText: 'offline' });
    }
  })());
});
