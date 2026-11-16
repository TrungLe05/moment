"use client";

import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import { detectLocale } from "./locale";
import { defaultLocale, messages, type Locale, type Messages } from "./messages";

type I18nContextValue = {
  locale: Locale;
  t: Messages;
  setLocale: (locale: Locale) => void;
};

const I18nContext = createContext<I18nContextValue | null>(null);

function subscribeToLanguageChange(onChange: () => void): () => void {
  window.addEventListener("languagechange", onChange);
  return () => window.removeEventListener("languagechange", onChange);
}

export function I18nProvider({ children }: { children: ReactNode }) {
  // Trang xuất tĩnh dựng với ngôn ngữ mặc định (snapshot phía server); trên trình duyệt đọc navigator.languages.
  const browserLocale = useSyncExternalStore(
    subscribeToLanguageChange,
    () => detectLocale(navigator.languages),
    () => defaultLocale,
  );
  // Ngôn ngữ người dùng đã chọn hoặc lưu trong tài khoản thắng ngôn ngữ trình duyệt (US-07).
  const [chosenLocale, setChosenLocale] = useState<Locale | null>(null);
  const locale = chosenLocale ?? browserLocale;

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const value = useMemo(() => ({ locale, t: messages[locale], setLocale: setChosenLocale }), [locale]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useI18n phải nằm trong I18nProvider");
  }
  return context;
}
