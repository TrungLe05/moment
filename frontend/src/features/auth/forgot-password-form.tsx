"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { accountApi } from "@/lib/api/account";
import { useAsyncAction } from "@/lib/use-async-action";

/** US-05: thông báo giống nhau dù email có tồn tại hay không. */
export function ForgotPasswordForm() {
  const { t } = useI18n();
  const [email, setEmail] = useState("");
  const forgot = useAsyncAction(accountApi.forgotPassword);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void forgot.run(email);
  }

  return (
    <AuthLayout title={t.forgotPassword.title} description={t.forgotPassword.description}>
      {forgot.done ? (
        <StatusMessage tone="success">{t.forgotPassword.done}</StatusMessage>
      ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
          <FormField label={t.common.email} type="email" autoComplete="email" required value={email}
            onChange={(event) => setEmail(event.target.value)} />
          {forgot.error !== null && <StatusMessage tone="error">{errorText(t, forgot.error)}</StatusMessage>}
          <Button type="submit" size="lg" disabled={forgot.pending}>
            {t.forgotPassword.submit}
          </Button>
        </form>
      )}
      <Link href="/login" className="text-sm underline underline-offset-4">
        {t.common.backToLogin}
      </Link>
    </AuthLayout>
  );
}
