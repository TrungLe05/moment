"use client";

import { useEffect, useRef } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { platform } from "@/platform";

/** Nút "Đăng nhập bằng Google" do Google Identity Services vẽ; ẩn khi chưa cấu hình client id. */
export function GoogleButton({ onIdToken }: { onIdToken: (idToken: string) => void }) {
  const { locale, t } = useI18n();
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = container.current;
    if (!element || !platform.googleSignIn.isConfigured()) {
      return;
    }
    element.replaceChildren();
    platform.googleSignIn.renderButton(element, locale, onIdToken).catch(() => {
      // Không tải được script của Google thì vẫn đăng nhập bằng email được.
    });
  }, [locale, onIdToken]);

  if (!platform.googleSignIn.isConfigured()) {
    return null;
  }
  return <div ref={container} aria-label={t.login.google} className="flex justify-center" />;
}
