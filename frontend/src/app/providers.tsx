"use client";

import { useEffect, type ReactNode } from "react";
import { SessionProvider } from "@/features/auth/session";
import { I18nProvider, useI18n } from "@/i18n/I18nProvider";
import { setApiLocale } from "@/lib/api";
import { registerServiceWorker } from "@/platform/service-worker";

// Lỗi trả về từ server (Accept-Language) theo cùng ngôn ngữ với giao diện.
function ApiLocaleSync() {
  const { locale } = useI18n();
  useEffect(() => setApiLocale(locale), [locale]);
  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  // US-50: cài được như app (PWA).
  useEffect(() => registerServiceWorker(), []);
  return (
    <I18nProvider>
      <ApiLocaleSync />
      <SessionProvider>{children}</SessionProvider>
    </I18nProvider>
  );
}
