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
import { useAsyncAction } from "@/lib/use-async-action";

/** US-05: đặt mật khẩu mới bằng token trong link; mọi phiên cũ bị thu hồi. */
export function ResetPasswordForm() {
  const { t } = useI18n();
  const token = useSearchParams().get("token");
  const [password, setPassword] = useState("");
  const reset = useAsyncAction(accountApi.resetPassword);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token) void reset.run(token, password);
  }

  const passwordError = fieldErrorText(t, reset.error, "newPassword");
  return (
    <AuthLayout title={t.resetPassword.title}>
      {token === null && <StatusMessage tone="error">{t.resetPassword.missingToken}</StatusMessage>}
      {reset.done && <StatusMessage tone="success">{t.resetPassword.done}</StatusMessage>}
      {token !== null && !reset.done && (
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label={t.resetPassword.newPassword} type="password" autoComplete="new-password" required
            hint={t.register.passwordHint} error={passwordError} value={password}
            onChange={(event) => setPassword(event.target.value)} />
          {reset.error !== null && !passwordError && (
            <StatusMessage tone="error">{errorText(t, reset.error)}</StatusMessage>
          )}
          <Button type="submit" size="lg" disabled={reset.pending}>
            {t.resetPassword.submit}
          </Button>
        </form>
      )}
      <Link href="/login" className="text-sm underline underline-offset-4">
        {t.common.backToLogin}
      </Link>
    </AuthLayout>
  );
}
