"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { accountApi } from "@/lib/api/account";
import { safeReturnPath, withReturnPath } from "@/lib/return-path";
import { useAsyncAction } from "@/lib/use-async-action";
import { DemoAccount } from "./demo-account";
import { GoogleButton } from "./google-button";
import { useSession } from "./session";

/** US-03, US-04: đăng nhập email + mật khẩu hoặc Google. */
export function LoginForm() {
  const { t, locale } = useI18n();
  const { startSession } = useSession();
  // Giữ trang cần quay lại (ví dụ trang mời) khi chuyển sang đăng ký.
  const next = safeReturnPath(useSearchParams().get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const login = useAsyncAction(async (emailValue: string, passwordValue: string) =>
    startSession(await accountApi.login(emailValue, passwordValue)),
  );
  const google = useAsyncAction(async (idToken: string) =>
    startSession(await accountApi.loginWithGoogle(idToken, locale)),
  );
  const resend = useAsyncAction(accountApi.resendVerification);
  const runGoogle = google.run;
  const onIdToken = useCallback((idToken: string) => void runGoogle(idToken), [runGoogle]);

  const notVerified = login.error instanceof ApiError && login.error.code === "EMAIL_NOT_VERIFIED";
  const error = login.error ?? google.error;

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void login.run(email, password);
  }

  return (
    <AuthLayout title={t.login.title}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <FormField label={t.common.email} type="email" autoComplete="email" required value={email}
          onChange={(event) => setEmail(event.target.value)} />
        <FormField label={t.common.password} type="password" autoComplete="current-password" required
          value={password} onChange={(event) => setPassword(event.target.value)} />
        {error !== null && <StatusMessage tone="error">{errorText(t, error)}</StatusMessage>}
        {notVerified && !resend.done && (
          <Button type="button" variant="outline" disabled={resend.pending} onClick={() => void resend.run(email)}>
            {t.login.resendVerification}
          </Button>
        )}
        {resend.done && <StatusMessage tone="success">{t.login.resendDone}</StatusMessage>}
        <Button type="submit" size="lg" disabled={login.pending || google.pending}>
          {t.login.submit}
        </Button>
      </form>
      <GoogleButton onIdToken={onIdToken} />
      <DemoAccount disabled={login.pending || google.pending} onLogin={(demoEmail, demoPassword) => void login.run(demoEmail, demoPassword)} />
      <div className="flex flex-col gap-1 text-sm">
        <Link href="/forgot-password" className="underline underline-offset-4">
          {t.login.forgotPassword}
        </Link>
        <p>
          {t.login.noAccount}{" "}
          <Link href={next ? withReturnPath("/register", next) : "/register"} className="underline underline-offset-4">
            {t.login.toRegister}
          </Link>
        </p>
      </div>
    </AuthLayout>
  );
}
