/**
 * Đăng ký service worker của bản web (US-50, public/sw.js). Bản Capacitor sau này không cần: app đã cài sẵn.
 * Chỉ bật ở bản build: lúc dev, cache dễ làm code mới không hiện ra.
 */
export function registerServiceWorker(): void {
  if (process.env.NODE_ENV !== "production") return;
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;
  navigator.serviceWorker.register("/sw.js").catch((error: unknown) => {
    // Không đăng ký được (trình duyệt chặn, trang không qua HTTPS) thì app vẫn chạy bình thường, chỉ không cài được.
    console.warn("Không đăng ký được service worker", error);
  });
}
