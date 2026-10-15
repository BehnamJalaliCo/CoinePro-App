// Pro Chart's service worker: it exists so the browser offers to install the site, and it does
// nothing else. No cache, no fetch handler: every request goes to the network exactly as without it,
// so a release is never served stale.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));
