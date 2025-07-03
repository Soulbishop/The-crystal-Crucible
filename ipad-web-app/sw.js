/**
 * Service Worker for Screen Mirror PWA
 * Handles caching and offline functionality
 */

const CACHE_NAME = 'screen-mirror-v1.0.0';
const STATIC_CACHE_NAME = 'screen-mirror-static-v1.0.0';
const DYNAMIC_CACHE_NAME = 'screen-mirror-dynamic-v1.0.0';

// Files to cache for offline use
const STATIC_FILES = [
    '/',
    '/index.html',
    '/styles.css',
    '/manifest.json',
    '/js/app.js',
    '/js/webrtc-client.js',
    '/js/touch-handler.js',
    '/js/video-display.js',
    '/js/device-discovery.js',
    '/js/coordinate-mapper.js',
    '/js/webrtc-adapter.js'
];

// Files that should always be fetched from network
const NETWORK_ONLY = [
    '/api/',
    '/signaling',
    '/discovery'
];

// Maximum age for cached resources (in milliseconds)
const CACHE_MAX_AGE = 24 * 60 * 60 * 1000; // 24 hours

self.addEventListener('install', (event) => {
    console.log('Service Worker: Installing...');
    
    event.waitUntil(
        caches.open(STATIC_CACHE_NAME)
            .then((cache) => {
                console.log('Service Worker: Caching static files');
                return cache.addAll(STATIC_FILES);
            })
            .then(() => {
                console.log('Service Worker: Installation complete');
                return self.skipWaiting();
            })
            .catch((error) => {
                console.error('Service Worker: Installation failed', error);
            })
    );
});

self.addEventListener('activate', (event) => {
    console.log('Service Worker: Activating...');
    
    event.waitUntil(
        caches.keys()
            .then((cacheNames) => {
                return Promise.all(
                    cacheNames.map((cacheName) => {
                        // Delete old caches
                        if (cacheName !== STATIC_CACHE_NAME && 
                            cacheName !== DYNAMIC_CACHE_NAME &&
                            cacheName.startsWith('screen-mirror-')) {
                            console.log('Service Worker: Deleting old cache', cacheName);
                            return caches.delete(cacheName);
                        }
                    })
                );
            })
            .then(() => {
                console.log('Service Worker: Activation complete');
                return self.clients.claim();
            })
            .catch((error) => {
                console.error('Service Worker: Activation failed', error);
            })
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);
    
    // Skip non-GET requests
    if (request.method !== 'GET') {
        return;
    }
    
    // Skip chrome-extension and other non-http(s) requests
    if (!url.protocol.startsWith('http')) {
        return;
    }
    
    // Handle different types of requests
    if (isNetworkOnly(request.url)) {
        // Network only resources
        event.respondWith(fetchFromNetwork(request));
    } else if (isStaticFile(request.url)) {
        // Static files - cache first
        event.respondWith(cacheFirst(request));
    } else {
        // Dynamic content - network first
        event.respondWith(networkFirst(request));
    }
});

// Cache first strategy for static files
async function cacheFirst(request) {
    try {
        const cachedResponse = await caches.match(request);
        
        if (cachedResponse) {
            // Check if cache is still fresh
            const cacheDate = new Date(cachedResponse.headers.get('date'));
            const now = new Date();
            
            if (now - cacheDate < CACHE_MAX_AGE) {
                console.log('Service Worker: Serving from cache', request.url);
                return cachedResponse;
            }
        }
        
        // Fetch from network and update cache
        const networkResponse = await fetch(request);
        
        if (networkResponse.ok) {
            const cache = await caches.open(STATIC_CACHE_NAME);
            cache.put(request, networkResponse.clone());
            console.log('Service Worker: Updated cache', request.url);
        }
        
        return networkResponse;
    } catch (error) {
        console.error('Service Worker: Cache first failed', error);
        
        // Fallback to cache if network fails
        const cachedResponse = await caches.match(request);
        if (cachedResponse) {
            console.log('Service Worker: Fallback to stale cache', request.url);
            return cachedResponse;
        }
        
        // Return offline page for navigation requests
        if (request.mode === 'navigate') {
            return caches.match('/index.html');
        }
        
        throw error;
    }
}

// Network first strategy for dynamic content
async function networkFirst(request) {
    try {
        const networkResponse = await fetch(request);
        
        if (networkResponse.ok) {
            // Cache successful responses
            const cache = await caches.open(DYNAMIC_CACHE_NAME);
            cache.put(request, networkResponse.clone());
            console.log('Service Worker: Cached dynamic content', request.url);
        }
        
        return networkResponse;
    } catch (error) {
        console.error('Service Worker: Network first failed', error);
        
        // Fallback to cache
        const cachedResponse = await caches.match(request);
        if (cachedResponse) {
            console.log('Service Worker: Fallback to cached content', request.url);
            return cachedResponse;
        }
        
        // Return offline page for navigation requests
        if (request.mode === 'navigate') {
            return caches.match('/index.html');
        }
        
        throw error;
    }
}

// Network only strategy
async function fetchFromNetwork(request) {
    try {
        return await fetch(request);
    } catch (error) {
        console.error('Service Worker: Network only failed', error);
        throw error;
    }
}

// Helper functions
function isStaticFile(url) {
    return STATIC_FILES.some(file => url.endsWith(file)) ||
           url.includes('.css') ||
           url.includes('.js') ||
           url.includes('.png') ||
           url.includes('.jpg') ||
           url.includes('.svg') ||
           url.includes('.ico');
}

function isNetworkOnly(url) {
    return NETWORK_ONLY.some(path => url.includes(path));
}

// Handle background sync for offline actions
self.addEventListener('sync', (event) => {
    console.log('Service Worker: Background sync', event.tag);
    
    if (event.tag === 'background-sync') {
        event.waitUntil(doBackgroundSync());
    }
});

async function doBackgroundSync() {
    try {
        // Perform any background sync operations
        console.log('Service Worker: Performing background sync');
        
        // Example: sync offline actions when back online
        const clients = await self.clients.matchAll();
        clients.forEach(client => {
            client.postMessage({
                type: 'BACKGROUND_SYNC',
                data: { status: 'completed' }
            });
        });
    } catch (error) {
        console.error('Service Worker: Background sync failed', error);
    }
}

// Handle push notifications (if needed in future)
self.addEventListener('push', (event) => {
    console.log('Service Worker: Push received', event);
    
    const options = {
        body: 'Screen Mirror notification',
        icon: '/icons/icon-192x192.png',
        badge: '/icons/icon-72x72.png',
        vibrate: [100, 50, 100],
        data: {
            dateOfArrival: Date.now(),
            primaryKey: 1
        },
        actions: [
            {
                action: 'explore',
                title: 'Open App',
                icon: '/icons/icon-72x72.png'
            },
            {
                action: 'close',
                title: 'Close',
                icon: '/icons/icon-72x72.png'
            }
        ]
    };
    
    event.waitUntil(
        self.registration.showNotification('Screen Mirror', options)
    );
});

// Handle notification clicks
self.addEventListener('notificationclick', (event) => {
    console.log('Service Worker: Notification click', event);
    
    event.notification.close();
    
    if (event.action === 'explore') {
        event.waitUntil(
            clients.openWindow('/')
        );
    }
});

// Handle messages from main thread
self.addEventListener('message', (event) => {
    console.log('Service Worker: Message received', event.data);
    
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
    
    if (event.data && event.data.type === 'CACHE_UPDATE') {
        event.waitUntil(updateCache());
    }
});

async function updateCache() {
    try {
        const cache = await caches.open(STATIC_CACHE_NAME);
        await cache.addAll(STATIC_FILES);
        console.log('Service Worker: Cache updated');
    } catch (error) {
        console.error('Service Worker: Cache update failed', error);
    }
}

// Cleanup old caches periodically
setInterval(async () => {
    try {
        const cacheNames = await caches.keys();
        const oldCaches = cacheNames.filter(name => 
            name.startsWith('screen-mirror-') && 
            name !== STATIC_CACHE_NAME && 
            name !== DYNAMIC_CACHE_NAME
        );
        
        await Promise.all(oldCaches.map(name => caches.delete(name)));
        
        if (oldCaches.length > 0) {
            console.log('Service Worker: Cleaned up old caches', oldCaches);
        }
    } catch (error) {
        console.error('Service Worker: Cache cleanup failed', error);
    }
}, 60 * 60 * 1000); // Every hour

