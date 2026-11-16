"use client";

import { useEffect, useState, type FormEvent } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/session";
import { errorText, fieldErrorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { accountApi } from "@/lib/api/account";
import { useAsyncAction } from "@/lib/use-async-action";
import { countGraphemes } from "@/lib/text";

// BR-02: chỉ kiểm tra ở client để báo sớm; server luôn kiểm tra lại.
const USERNAME_FORMAT = /^[a-z0-9._]{3,20}$/;
const DISPLAY_NAME_MAX = 40;

/** US-06: chọn username và tên hiển thị trước khi vào feed. Ảnh đại diện thêm ở T-116. */
export function OnboardingForm() {
  const { t } = useI18n();
  const { user, updateUser } = useSession();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [lastCheck, setLastCheck] = useState<{ username: string; available: boolean } | null>(null);
  const save = useAsyncAction(async (name: string, display: string) =>
    updateUser(await accountApi.updateMe({ username: name, displayName: display })),
  );

  const normalized = username.trim().toLowerCase();
  const formatValid = USERNAME_FORMAT.test(normalized);
  // Kết quả kiểm tra chỉ có nghĩa với đúng username đang gõ.
  const available = lastCheck?.username === normalized ? lastCheck.available : null;

  useEffect(() => {
    if (!formatValid) return;
    // Chờ người dùng ngừng gõ rồi mới hỏi server, vì API này có giới hạn tần suất.
    const timer = setTimeout(() => {
      accountApi.usernameAvailable(normalized).then(
        (result) => setLastCheck({ username: normalized, available: result }),
        () => setLastCheck(null),
      );
    }, 400);
    return () => clearTimeout(timer);
  }, [normalized, formatValid]);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void save.run(normalized, displayName.trim());
  }

  const usernameError =
    fieldErrorText(t, save.error, "username") ?? (available === false ? t.onboarding.usernameTaken : undefined);
  return (
    <AuthLayout title={t.onboarding.title} description={t.onboarding.description}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <FormField label={t.onboarding.username} autoComplete="username" required value={username}
          hint={available === true ? t.onboarding.usernameAvailable : t.onboarding.usernameHint}
          error={usernameError} onChange={(event) => setUsername(event.target.value)} />
        <FormField label={t.onboarding.displayName} autoComplete="name" value={displayName}
          error={fieldErrorText(t, save.error, "displayName")}
          onChange={(event) => {
            // Không nhận thêm ký tự khi đã đủ 40 ký tự hiển thị.
            if (countGraphemes(event.target.value) <= DISPLAY_NAME_MAX) setDisplayName(event.target.value);
          }} />
        {save.error !== null && !usernameError && (
          <StatusMessage tone="error">{errorText(t, save.error)}</StatusMessage>
        )}
        <Button type="submit" size="lg" disabled={save.pending || !formatValid || available === false}>
          {t.onboarding.submit}
        </Button>
      </form>
    </AuthLayout>
  );
}
