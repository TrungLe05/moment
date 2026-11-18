"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { CapturedMedia } from "@/platform";
import { buildDraft, type SendSelection } from "./draft";
import { pendingMoments } from "./pending-moments";
import { SendOptions } from "./send-options";
import { useFriendList } from "./use-friend-list";

type Props = {
  media: CapturedMedia;
  url: string;
  /** Gửi được rồi: object URL giờ thuộc kho khoảnh khắc đang đăng, trang soạn không thu hồi nữa. */
  onHandedOff: () => void;
};

/**
 * Bước gửi: chọn chế độ, người nhận (T-137) rồi tạo khoảnh khắc. Tạo xong thì về feed, nơi khoảnh khắc hiện mờ trong
 * lúc tải lên và xử lý (T-136, US-28). Lỗi lúc tạo báo ngay ở đây theo mã, nội dung đã soạn vẫn giữ nguyên.
 */
export function SendPanel({ media, url, onHandedOff }: Props) {
  const { t } = useI18n();
  const router = useRouter();
  const friendList = useFriendList();
  const [selection, setSelection] = useState<SendSelection>({ mode: "SHARED", recipientIds: [] });
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const friends = friendList.status === "ready" ? friendList.friends : null;

  const send = async () => {
    setSending(true);
    setError(null);
    try {
      await pendingMoments.submit(buildDraft(media, url, selection));
      onHandedOff();
      router.push("/");
    } catch (caught: unknown) {
      setError(caught);
      setSending(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <SendOptions selection={selection} onChange={setSelection} friends={friends} disabled={sending} />
      {friendList.status === "error" && (
        <p role="alert" className="text-sm text-peach">
          {t.compose.friendsLoadError} {errorText(t, friendList.error)}
        </p>
      )}
      {error !== null && (
        <p role="alert" className="text-sm text-peach">
          {errorText(t, error)}
        </p>
      )}
      <button
        type="button"
        disabled={sending || friends === null || friends.length === 0}
        onClick={() => void send()}
        className="h-14 rounded-full bg-peach text-base font-bold text-peach-ink disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
      >
        {sending ? t.compose.sending : t.compose.send}
      </button>
    </div>
  );
}
