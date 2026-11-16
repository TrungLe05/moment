"use client";

import { useCallback, useEffect, useState } from "react";
import { useSession } from "@/features/auth/session";
import { accountApi } from "@/lib/api/account";
import { friendsApi, type FriendList, type Invite } from "@/lib/api/friends";

/** Dữ liệu và thao tác của trang bạn bè: link mời, danh sách, tạo mã mới, hủy kết bạn. */
export function useFriends() {
  const { updateUser } = useSession();
  const [invite, setInvite] = useState<Invite | null>(null);
  const [friends, setFriends] = useState<FriendList | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    // Tải song song khi mở trang; setState chỉ chạy sau khi request xong.
    let cancelled = false;
    Promise.all([friendsApi.myInvite(), friendsApi.list()]).then(
      ([loadedInvite, loadedFriends]) => {
        if (cancelled) return;
        setInvite(loadedInvite);
        setFriends(loadedFriends);
      },
      (caught: unknown) => {
        if (!cancelled) setError(caught);
      },
    );
    return () => {
      cancelled = true;
    };
  }, []);

  const rotateInvite = useCallback(async () => {
    setInvite(await friendsApi.rotateInvite());
  }, []);

  const unfriend = useCallback(
    async (userId: string) => {
      await friendsApi.unfriend(userId);
      // Chỉ danh sách và số bạn (x/15) đổi; mã mời giữ nguyên nên không tải lại.
      const [list, me] = await Promise.all([friendsApi.list(), accountApi.me()]);
      setFriends(list);
      updateUser(me);
    },
    [updateUser],
  );

  return { invite, friends, error, rotateInvite, unfriend };
}
