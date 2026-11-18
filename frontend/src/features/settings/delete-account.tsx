"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState, type FormEvent } from "react";
import { StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { GoogleButton } from "@/features/auth/google-button";
import { useSession } from "@/features/auth/session";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api";
import { accountApi } from "@/lib/api/account";

/**
 * US-09: xóa tài khoản cùng toàn bộ dữ liệu. Tài khoản có mật khẩu nhập lại mật khẩu; tài khoản chỉ có Google được
 * server yêu cầu đăng nhập Google lại (REAUTH_REQUIRED, D-026) rồi xóa tiếp. Tài khoản demo không xóa được (D-027).
 */
export function DeleteAccount() {
  const { t, locale } = useI18n();
  const router = useRouter();
  const { user, startSession, signOut } = useSession();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const needsGoogle = error instanceof ApiError && error.code === "REAUTH_REQUIRED";

  const remove = useCallback(
    async (currentPassword: string | null) => {
      setPending(true);
      setError(null);
      try {
        await accountApi.deleteAccount(currentPassword);
      } catch (caught: unknown) {
        setError(caught);
        setPending(false);
        return;
      }
      // Tài khoản đã mất nên lệnh đăng xuất trên server thất bại cũng không sao; phiên ở máy vẫn được xóa.
      await signOut();
      router.replace("/login");
    },
    [router, signOut],
  );

  // Đăng nhập Google lại: phiên mới có auth_time mới, rồi xóa tiếp không cần mật khẩu.
  const onIdToken = useCallback(
    (idToken: string) => {
      void (async () => {
        try {
          startSession(await accountApi.loginWithGoogle(idToken, locale));
        } catch (caught: unknown) {
          setError(caught);
          return;
        }
        await remove(null);
      })();
    },
    [locale, remove, startSession],
  );

  if (user?.isDemo) {
    return <p className="text-sm text-muted-foreground">{t.settings.demoNoDelete}</p>;
  }

  if (!open) {
    return (
      <Button type="button" variant="outline" onClick={() => setOpen(true)}>
        {t.settings.deleteOpen}
      </Button>
    );
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void remove(password.length > 0 ? password : null);
  }

  return (
    <section aria-labelledby="delete-account-title" className="flex flex-col gap-3 rounded-lg border border-destructive/40 p-4">
      <h2 id="delete-account-title" className="font-semibold">
        {t.settings.deleteTitle}
      </h2>
      <p className="text-sm">{t.settings.deleteWarning}</p>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <FormField
          label={t.settings.deletePassword}
          hint={t.settings.deleteGoogleHint}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        {error !== null && !needsGoogle && <StatusMessage tone="error">{errorText(t, error)}</StatusMessage>}
        {needsGoogle && (
          <div className="flex flex-col gap-2">
            <p role="alert" className="text-sm">
              {t.settings.reauthGoogle}
            </p>
            <GoogleButton onIdToken={onIdToken} />
          </div>
        )}
        <div className="flex gap-2">
          <Button type="submit" variant="destructive" disabled={pending}>
            {t.settings.deleteConfirm}
          </Button>
          <Button type="button" variant="outline" disabled={pending} onClick={() => setOpen(false)}>
            {t.settings.deleteCancel}
          </Button>
        </div>
      </form>
    </section>
  );
}
