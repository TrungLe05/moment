import type { MetadataRoute } from "next";
import { defaultLocale, messages } from "@/i18n/messages";

// Xuất tĩnh (ADR 0001): manifest tạo một lần lúc build thành /manifest.webmanifest.
export const dynamic = "force-static";

const text = messages[defaultLocale];
// Nền tối của không gian chia sẻ (định hướng giao diện), cùng màu --color-night.
const NIGHT = "#0e0e10";

/** US-50: cài Khoảnh khắc như một app trên máy tính (PWA), mở ở cửa sổ riêng. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: text.appName,
    short_name: text.appName,
    description: text.appDescription,
    lang: defaultLocale,
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: NIGHT,
    theme_color: NIGHT,
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
