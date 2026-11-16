import { locales, type Locale } from "./messages";

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (locales as readonly string[]).includes(value);
}

/** US-07: ngôn ngữ mặc định theo trình duyệt; vi hoặc en, ngôn ngữ khác dùng en. */
export function detectLocale(preferred: readonly string[]): Locale {
  const first = preferred[0]?.toLowerCase() ?? "";
  return first === "vi" || first.startsWith("vi-") ? "vi" : "en";
}
