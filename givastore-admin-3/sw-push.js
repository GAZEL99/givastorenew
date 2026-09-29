// sw-push.js — Service Worker admin GivaStore: Web Push + app-shell cache ringan.
var CACHE_NAME = 'giva-admin-shell-v1';
var SHELL_ASSETS = [
  './index.html',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', function (e) {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return cache.addAll(SHELL_ASSETS).catch(function () {
        // Kalau salah satu gagal di-cache (mis. offline pas install), jangan gagalkan install
      });
    })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(
        keys.filter(function (k) { return k !== CACHE_NAME; }).map(function (k) { return caches.delete(k); })
      );
    })
  );
  self.clients.claim();
});

// Cache-first untuk app-shell (HTML/manifest/icon), tapi SELALU network untuk request ke Supabase
// (data pesanan/produk harus selalu fresh, jangan pernah kena cache).
self.addEventListener('fetch', function (event) {
  var url = event.request.url;
  if (event.request.method !== 'GET') return;
  if (url.indexOf('supabase.co') !== -1) return; // biarkan lewat network langsung

  event.respondWith(
    caches.match(event.request).then(function (cached) {
      if (cached) return cached;
      return fetch(event.request).then(function (res) {
        if (res && res.status === 200 && res.type === 'basic') {
          var resClone = res.clone();
          caches.open(CACHE_NAME).then(function (cache) { cache.put(event.request, resClone); });
        }
        return res;
      }).catch(function () { return cached; });
    })
  );
});

self.addEventListener('push', function (event) {
  var data = {};
  try { data = event.data ? event.data.json() : {}; } catch (e) { data = { title: 'GivaStore', body: event.data ? event.data.text() : 'Ada notifikasi baru' }; }

  var title = data.title || 'GivaStore Admin';
  var options = {
    body: data.body || '',
    icon: './icons/icon-192.png',
    badge: './icons/icon-192.png',
    tag: data.tag || 'giva-order',
    renotify: true,
    vibrate: [200, 100, 200],
    data: { url: data.url || './index.html' },
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  var targetUrl = (event.notification.data && event.notification.data.url) || './index.html';
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (clientList) {
      for (var i = 0; i < clientList.length; i++) {
        var c = clientList[i];
        if (c.url.indexOf('index.html') !== -1 && 'focus' in c) return c.focus();
      }
      if (clients.openWindow) return clients.openWindow(targetUrl);
    })
  );
});
