"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { errorText, fieldErrorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { accountApi } from "@/lib/api/account";
import { safeReturnPath, withReturnPath } from "@/lib/return-path";
import { useAsyncAction } from "@/lib/use-async-action";

/** US-01: đăng ký xong thì báo kiểm tra hộp thư. */
export function RegisterForm() {
  const { t, locale } = useI18n();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const register = useAsyncAction(accountApi.register);
  const next = safeReturnPath(useSearchParams().get("next"));
  const loginHref = next ? withReturnPath("/login", next) : "/login";

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void register.run(email, password, locale);
  }

  if (register.done) {
    return (
      <AuthLayout title={t.register.checkInboxTitle} description={t.register.checkInboxBody}>
        <Link href={loginHref} className="text-sm underline underline-offset-4">
          {t.common.backToLogin}
        </Link>
      </AuthLayout>
    );
  }

  const emailError = fieldErrorText(t, register.error, "email");
  const passwordError = fieldErrorText(t, register.error, "password");
  return (
    <AuthLayout title={t.register.title}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <FormField label={t.common.email} type="email" autoComplete="email" required value={email}
          error={emailError} onChange={(event) => setEmail(event.target.value)} />
        <FormField label={t.common.password} type="password" autoComplete="new-password" required value={password}
          hint={t.register.passwordHint} error={passwordError} onChange={(event) => setPassword(event.target.value)} />
        {register.error !== null && !emailError && !passwordError && (
          <StatusMessage tone="error">{errorText(t, register.error)}</StatusMessage>
        )}
        <Button type="submit" size="lg" disabled={register.pending}>
          {t.register.submit}
        </Button>
      </form>
      <p className="text-sm">
        {t.register.haveAccount}{" "}
        <Link href={loginHref} className="underline underline-offset-4">
          {t.register.toLogin}
        </Link>
      </p>
    </AuthLayout>
  );
}
