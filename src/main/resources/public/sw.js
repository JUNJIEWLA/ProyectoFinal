const CACHE_NAME = 'encuestas-cache-v3';
const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/login.html',
    '/encuestas.html',
    '/usuarios.html',
    '/css/styles.css',
    '/js/auth.js',
    '/js/login.js',
    '/js/encuestas.js',
    '/js/users.js',
    '/js/db.js',
    '/js/sync.js',
    '/js/worker.js',
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then((cache) => cache.addAll(STATIC_ASSETS))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) =>
                Promise.all(
                    keys
                        .filter((k) => k.startsWith('encuestas-cache-') && k !== CACHE_NAME)
                        .map((k) => caches.delete(k))
                )
            )
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const accept = request.headers.get('accept') || '';

    if (request.mode === 'navigate' || accept.includes('text/html')) {
        event.respondWith(
            fetch(request).catch(async () => {
                const cachedPage = await caches.match(request);
                return cachedPage || caches.match('/login.html');
            })
        );
        return;
    }

    event.respondWith(
        caches.match(request).then((cached) => {
            if (cached) {
                return cached;
            }
            return fetch(request);
        })
    );
});
