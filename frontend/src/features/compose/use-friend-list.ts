"use client";

import { useEffect, useState } from "react";
import { friendsApi, type Friend } from "@/lib/api/friends";

type FriendListState = { status: "loading" } | { status: "ready"; friends: Friend[] } | { status: "error"; error: unknown };

/** Danh sách bạn hiện tại để chọn người nhận (US-27, BR-12); tải một lần khi tới bước gửi. */
export function useFriendList(): FriendListState {
  const [state, setState] = useState<FriendListState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    friendsApi.list().then(
      (list) => {
        if (!cancelled) setState({ status: "ready", friends: list.items });
      },
      (error: unknown) => {
        if (!cancelled) setState({ status: "error", error });
      },
    );
    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}
