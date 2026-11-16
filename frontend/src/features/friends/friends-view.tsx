"use client";

import Link from "next/link";
import { StatusMessage } from "@/components/auth-layout";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { FriendList } from "./friend-list";
import { InviteCard } from "./invite-card";
import { useFriends } from "./use-friends";

/** Trang bạn bè (E2): ghép thẻ mời và danh sách bạn. */
export function FriendsView() {
  const { t } = useI18n();
  const { invite, friends, error, rotateInvite, unfriend } = useFriends();

  return (
    <main className="mx-auto flex w-full max-w-[390px] flex-1 flex-col gap-8 px-4 py-8">
      <h1 className="text-2xl font-semibold">{t.friends.title}</h1>
      {error !== null && <StatusMessage tone="error">{errorText(t, error)}</StatusMessage>}
      {!invite && !friends && error === null && (
        <p role="status" className="text-sm text-muted-foreground">
          {t.common.loading}
        </p>
      )}
      {invite && <InviteCard invite={invite} onRotate={rotateInvite} />}
      {friends && <FriendList data={friends} onUnfriend={unfriend} />}
      <Link href="/" className="text-sm underline underline-offset-4">
        {t.settings.back}
      </Link>
    </main>
  );
}
