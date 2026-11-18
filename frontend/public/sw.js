// Service worker của bản web (US-50, T-162): chỉ cache phần giao diện tĩnh, không bao giờ cache dữ liệu riêng tư
// (API, file khoảnh khắc qua presigned URL). Viết tay, không thư viện. Đổi cách cache thì tăng VERSION để dọn cache cũ.
const VERSION = "v1";
const STATIC_CACHE = `moment-static-${VERSION}`;
const PAGE_CACHE = `moment-pages-${VERSION}`;

/**
 * Chọn cách xử lý một request:
 * - static: file build có băm trong tên (/_next/static), không bao giờ đổi, lấy cache trước
 * - page: điều hướng tới một trang, lấy mạng trước, mất mạng thì dùng bản HTML tĩnh đã lưu (không chứa dữ liệu riêng)
 * - asset: icon, manifest, lấy cache ngay rồi cập nhật ngầm
 * - network: mọi thứ khác, kể cả API và request khác origin, đi thẳng ra mạng, không lưu
 */
function classify({ url, method, mode }) {
  const target = new URL(url);
  if (method !== "GET" || target.origin !== self.location.origin) return "network";
  if (target.pathname.startsWith("/api/")) return "network";
  if (target.pathname.startsWith("/_next/static/")) return "static";
  if (mode === "navigate") return "page";
  if (/^\/(icon[\w-]*\.(png|svg)|manifest\.webmanifest|favicon\.ico)$/.test(target.pathname)) return "asset";
  return "network";
}

// Cho test đọc được hàm phân loại (src/platform/service-worker.test.ts).
self.__classify = classify;

async function cacheFirst(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) await cache.put(request, response.clone());
  return response;
}

async function networkFirst(request) {
  const cache = await caches.open(PAGE_CACHE);
  try {
    const response = await fetch(request);
    // Chỉ lưu trang HTML tĩnh tải thành công; chuyển hướng hay lỗi thì không.
    if (response.ok && response.type === "basic") await cache.put(request, response.clone());
    return response;
  } catch (error) {
    const cached = (await cache.match(request, { ignoreSearch: true })) || (await cache.match("/"));
    if (cached) return cached;
    throw error;
  }
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cached = await cache.match(request);
  const refresh = fetch(request).then((response) => {
    if (response.ok) cache.put(request, response.clone());
    return response;
  });
  return cached || refresh;
}

self.addEventListener("install", (event) => {
  // Lưu sẵn trang chủ để mở app lúc mất mạng vẫn có khung giao diện.
  event.waitUntil(caches.open(PAGE_CACHE).then((cache) => cache.add("/")).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  const keep = [STATIC_CACHE, PAGE_CACHE];
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((name) => !keep.includes(name)).map((name) => caches.delete(name))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const strategy = classify({ url: request.url, method: request.method, mode: request.mode });
  if (strategy === "static") event.respondWith(cacheFirst(request));
  else if (strategy === "page") event.respondWith(networkFirst(request));
  else if (strategy === "asset") event.respondWith(staleWhileRevalidate(request));
  // network: không gọi respondWith, trình duyệt tự xử lý như không có service worker.
});
