"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/session";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { Locale } from "@/i18n/messages";
import { accountApi } from "@/lib/api/account";
import { useAsyncAction } from "@/lib/use-async-action";

/** US-07: đổi ngôn ngữ và lưu vào tài khoản; US-08: đăng xuất thiết bị hiện tại. */
export function SettingsView() {
  const { t, locale } = useI18n();
  const router = useRouter();
  const { updateUser, signOut } = useSession();
  const changeLocale = useAsyncAction(async (next: Locale) => updateUser(await accountApi.updateMe({ locale: next })));

  async function onSignOut() {
    await signOut();
    router.replace("/login");
  }

  const options: { value: Locale; label: string }[] = [
    { value: "vi", label: t.settings.vietnamese },
    { value: "en", label: t.settings.english },
  ];
  return (
    <AuthLayout title={t.settings.title}>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium">{t.settings.language}</legend>
        <div className="flex gap-2">
          {options.map((option) => (
            <Button key={option.value} type="button" variant={option.value === locale ? "default" : "outline"}
              aria-pressed={option.value === locale} disabled={changeLocale.pending}
              onClick={() => void changeLocale.run(option.value)}>
              {option.label}
            </Button>
          ))}
        </div>
      </fieldset>
      {changeLocale.error !== null && <StatusMessage tone="error">{errorText(t, changeLocale.error)}</StatusMessage>}
      <Button type="button" variant="destructive" onClick={() => void onSignOut()}>
        {t.settings.logout}
      </Button>
      <Link href="/" className="text-sm underline underline-offset-4">
        {t.settings.back}
      </Link>
    </AuthLayout>
  );
}
