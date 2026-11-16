"use client";

import Link from "next/link";
import { useSession } from "@/features/auth/session";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";

/** Trang chính tạm thời; feed khoảnh khắc làm ở T-142. */
export function HomeView() {
  const { t } = useI18n();
  const { user } = useSession();
  const name = user?.displayName ?? user?.username ?? "";

  return (
    <main className="mx-auto flex w-full max-w-[390px] flex-1 flex-col items-center justify-center gap-3 px-4 text-center">
      <h1 className="text-3xl font-semibold">{t.appName}</h1>
      <p>{format(t.home.greeting, { name })}</p>
      <p className="text-sm text-muted-foreground">{t.home.comingSoon}</p>
      <nav className="flex gap-4 text-sm">
        <Link href="/friends" className="underline underline-offset-4">
          {t.home.friends}
        </Link>
        <Link href="/settings" className="underline underline-offset-4">
          {t.home.settings}
        </Link>
      </nav>
    </main>
  );
}
