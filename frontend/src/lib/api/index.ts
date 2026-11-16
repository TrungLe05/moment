import { defaultLocale, type Locale } from "@/i18n/messages";
import { platform } from "@/platform";
import { createApiClient } from "./client";

let currentLocale: Locale = defaultLocale;

/** I18nProvider gọi khi đổi ngôn ngữ để lỗi từ server cùng ngôn ngữ với giao diện. */
export function setApiLocale(locale: Locale): void {
  currentLocale = locale;
}

export const apiClient = createApiClient({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  tokenStore: platform.tokenStore,
  getLocale: () => currentLocale,
});

export { ApiError, NETWORK_ERROR } from "./errors";
