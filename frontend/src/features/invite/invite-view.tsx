"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthLayout, StatusMessage } from "@/components/auth-layout";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/session";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { ApiError } from "@/lib/api";
import { friendsApi, type Inviter } from "@/lib/api/friends";
import { withReturnPath } from "@/lib/return-path";
import { useAsyncAction } from "@/lib/use-async-action";

/**
 * Trang mời (US-11): hiện người mời; đã đăng nhập thì bấm Kết bạn (BR-05: thành bạn ngay),
 * chưa đăng nhập thì đăng nhập hoặc đăng ký rồi quay lại đúng trang này.
 */
export function InviteView() {
  const { t } = useI18n();
  const router = useRouter();
  const code = useSearchParams().get("code");
  const { status, user } = useSession();
  const [inviter, setInviter] = useState<Inviter | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const accept = useAsyncAction(async (inviteCode: string) => friendsApi.accept(inviteCode));
  const [alreadyFriends, setAlreadyFriends] = useState(false);

  const returnPath = code ? `/invite?code=${encodeURIComponent(code)}` : "/";

  useEffect(() => {
    if (!code) return;
    let cancelled = false;
    friendsApi.inviter(code).then(
      (found) => {
        if (!cancelled) setInviter(found);
      },
      (caught: unknown) => {
        if (!cancelled) setLoadError(caught);
      },
    );
    return () => {
      cancelled = true;
    };
  }, [code]);

  useEffect(() => {
    // Chưa chọn username thì làm xong onboarding rồi quay lại (US-06).
    if (status === "signed-in" && user?.needsOnboarding) {
      router.replace(withReturnPath("/onboarding", returnPath));
    }
  }, [status, user, router, returnPath]);

  async function onAccept() {
    if (!code) return;
    const result = await accept.run(code);
    if (result) setAlreadyFriends(result.alreadyFriends);
  }

  const inviterName = inviter?.displayName ?? inviter?.username ?? "";
  // FRIEND_LIMIT_REACHED: server cho biết bên nào đã đủ 15 trong detail, hiện nguyên câu đó.
  const acceptErrorText =
    accept.error instanceof ApiError && accept.error.code === "FRIEND_LIMIT_REACHED" && accept.error.detail
      ? accept.error.detail
      : errorText(t, accept.error);

  return (
    <AuthLayout title={t.invite.title}>
      {code === null && <StatusMessage tone="error">{t.invite.missingCode}</StatusMessage>}
      {loadError !== null && <StatusMessage tone="error">{errorText(t, loadError)}</StatusMessage>}
      {inviter && (
        <>
          <p>{format(t.invite.invitedBy, { name: inviterName })}</p>
          {inviter.username && <p className="text-sm text-muted-foreground">@{inviter.username}</p>}
        </>
      )}
      {inviter && accept.done && (
        <>
          <StatusMessage tone="success">{alreadyFriends ? t.invite.alreadyFriends : t.invite.accepted}</StatusMessage>
          <Link href="/friends" className="text-sm underline underline-offset-4">
            {t.invite.toFriends}
          </Link>
        </>
      )}
      {inviter && !accept.done && status === "signed-in" && (
        <Button type="button" size="lg" disabled={accept.pending} onClick={() => void onAccept()}>
          {t.invite.accept}
        </Button>
      )}
      {inviter && status === "signed-out" && (
        <div className="flex flex-col gap-2">
          <Button type="button" size="lg" onClick={() => router.push(withReturnPath("/login", returnPath))}>
            {t.invite.signInToAccept}
          </Button>
          <Link href={withReturnPath("/register", returnPath)} className="text-sm underline underline-offset-4">
            {t.invite.registerToAccept}
          </Link>
        </div>
      )}
      {accept.error !== null && <StatusMessage tone="error">{acceptErrorText}</StatusMessage>}
    </AuthLayout>
  );
}
