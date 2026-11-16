"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { StatusMessage } from "@/components/auth-layout";
import { Button } from "@/components/ui/button";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import type { Friend, FriendList as FriendListData } from "@/lib/api/friends";
import { useAsyncAction } from "@/lib/use-async-action";

function nameOf(friend: Friend): string {
  return friend.displayName ?? friend.username ?? "";
}

/** US-12, US-13: danh sách bạn với số chỗ x/15 và hủy kết bạn có hộp xác nhận. */
export function FriendList({ data, onUnfriend }: { data: FriendListData; onUnfriend: (userId: string) => Promise<void> }) {
  const { t, locale } = useI18n();
  const [pendingRemoval, setPendingRemoval] = useState<Friend | null>(null);
  const unfriend = useAsyncAction(onUnfriend);
  const dateFormat = new Intl.DateTimeFormat(locale, { dateStyle: "medium" });

  return (
    <section aria-labelledby="friends-title" className="flex flex-col gap-3">
      <h2 id="friends-title" className="text-lg font-semibold">
        {format(t.friends.listTitle, { count: String(data.count), limit: String(data.limit) })}
      </h2>
      {data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.friends.empty}</p>
      ) : (
        <ul className="flex flex-col divide-y">
          {data.items.map((friend) => (
            <li key={friend.id} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0">
                <p className="truncate font-medium">{nameOf(friend)}</p>
                <p className="truncate text-sm text-muted-foreground">
                  @{friend.username} ·{" "}
                  {format(t.friends.friendsSince, { date: dateFormat.format(new Date(friend.friendsSince)) })}
                </p>
              </div>
              <Button type="button" variant="ghost" size="sm" disabled={unfriend.pending}
                aria-label={`${t.friends.unfriend}: ${nameOf(friend)}`} onClick={() => setPendingRemoval(friend)}>
                {t.friends.unfriend}
              </Button>
            </li>
          ))}
        </ul>
      )}
      {unfriend.error !== null && <StatusMessage tone="error">{errorText(t, unfriend.error)}</StatusMessage>}
      <ConfirmDialog
        open={pendingRemoval !== null}
        onOpenChange={(open) => {
          if (!open) setPendingRemoval(null);
        }}
        title={format(t.friends.unfriendTitle, { name: pendingRemoval ? nameOf(pendingRemoval) : "" })}
        description={t.friends.unfriendBody}
        confirmLabel={t.friends.confirm}
        cancelLabel={t.friends.cancel}
        destructive
        onConfirm={() => {
          if (pendingRemoval) void unfriend.run(pendingRemoval.id);
        }}
      />
    </section>
  );
}
