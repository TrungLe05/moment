"use client";

import { useId } from "react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";

/**
 * Tài khoản demo cấu hình lúc build (US-51, D-045); null khi không có. Next.js chép giá trị NEXT_PUBLIC_* vào bản
 * xuất tĩnh, nên đây chỉ được là thông tin công khai của tài khoản demo.
 */
export function demoAccount(): { email: string; password: string } | null {
  const email = process.env.NEXT_PUBLIC_DEMO_EMAIL;
  const password = process.env.NEXT_PUBLIC_DEMO_PASSWORD;
  return email && password ? { email, password } : null;
}

type Props = {
  disabled: boolean;
  onLogin: (email: string, password: string) => void;
};

/** US-51: thông tin đăng nhập demo hiện trên trang đăng nhập, kèm nút đăng nhập nhanh. */
export function DemoAccount({ disabled, onLogin }: Props) {
  const { t } = useI18n();
  const titleId = useId();
  const account = demoAccount();
  if (!account) return null;

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-2 rounded-lg border border-dashed p-4 text-sm">
      <h2 id={titleId} className="font-semibold">
        {t.login.demoTitle}
      </h2>
      <p className="text-muted-foreground">{t.login.demoBody}</p>
      <p>{format(t.login.demoEmail, { email: account.email })}</p>
      <p>{format(t.login.demoPassword, { password: account.password })}</p>
      <Button type="button" variant="outline" disabled={disabled} onClick={() => onLogin(account.email, account.password)}>
        {t.login.demoLogin}
      </Button>
    </section>
  );
}
