"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { accountApi } from "@/lib/api/account";
import { useAsyncAction } from "@/lib/use-async-action";

/** US-02: mở link trong email; link hết hạn hoặc đã dùng thì cho gửi lại. */
export function VerifyEmailView() {
  const { t } = useI18n();
  const token = useSearchParams().get("token");
  const verify = useAsyncAction(accountApi.verifyEmail);
  const resend = useAsyncAction(accountApi.resendVerification);
  const [email, setEmail] = useState("");
  const started = useRef(false);
  const runVerify = verify.run;

  useEffect(() => {
    // Token chỉ dùng được một lần, nên không gọi lại khi React chạy effect hai lần ở chế độ dev.
    if (token && !started.current) {
      started.current = true;
      void runVerify(token);
    }
  }, [token, runVerify]);

  function onResend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void resend.run(email);
  }

  const failed = token === null || verify.error instanceof ApiError;
  return (
    <AuthLayout title={t.verifyEmail.title}>
      {verify.pending && <StatusMessage tone="success">{t.verifyEmail.verifying}</StatusMessage>}
      {verify.done && <StatusMessage tone="success">{t.verifyEmail.success}</StatusMessage>}
      {verify.error !== null && <StatusMessage tone="error">{errorText(t, verify.error)}</StatusMessage>}
      {failed && !resend.done && (
        <form onSubmit={onResend} className="flex flex-col gap-4" noValidate>
          <FormField label={t.common.email} type="email" autoComplete="email" required value={email}
            onChange={(event) => setEmail(event.target.value)} />
          <Button type="submit" disabled={resend.pending}>
            {t.verifyEmail.resendSubmit}
          </Button>
        </form>
      )}
      {resend.done && <StatusMessage tone="success">{t.verifyEmail.resendDone}</StatusMessage>}
      <Link href="/login" className="text-sm underline underline-offset-4">
        {t.common.backToLogin}
      </Link>
    </AuthLayout>
  );
}
