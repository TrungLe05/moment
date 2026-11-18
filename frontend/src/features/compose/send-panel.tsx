"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { exportComposed } from "@/features/layers/draw";
import type { Layer } from "@/features/layers/model";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { CapturedMedia } from "@/platform";
import { buildDraft, type SendSelection } from "./draft";
import { pendingMoments } from "./pending-moments";
import { SendOptions } from "./send-options";
import { useFriendList } from "./use-friend-list";

type Props = {
  media: CapturedMedia;
  source: "CAMERA" | "UPLOAD";
  trimStartMs: number | null;
  layers: Layer[];
};

// API mục 3.4: bản ghép chỉ nhận JPEG hoặc WebP.
const COMPOSED_TYPES = ["image/jpeg", "image/webp"];

/**
 * Bước gửi: chọn chế độ, người nhận (T-137), xuất bản ghép với ảnh (T-136, ADR 0002) rồi tạo khoảnh khắc. Tạo xong thì
 * về feed, nơi khoảnh khắc hiện mờ trong lúc tải lên và xử lý (US-28). Lỗi lúc tạo báo ngay ở đây theo mã, nội dung
 * đã soạn vẫn giữ nguyên.
 */
export function SendPanel({ media, source, trimStartMs, layers }: Props) {
  const { t } = useI18n();
  const router = useRouter();
  const friendList = useFriendList();
  const [selection, setSelection] = useState<SendSelection>({ mode: "SHARED", recipientIds: [] });
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const friends = friendList.status === "ready" ? friendList.friends : null;

  const send = async () => {
    setSending(true);
    setError(null);
    let composed: Blob | null = null;
    if (media.kind === "photo") {
      try {
        // Chưa có lớp và ảnh đã là JPEG/WebP thì bản ghép chính là ảnh gốc, khỏi vẽ lại; PNG luôn vẽ lại thành JPEG.
        const redraw = layers.length > 0 || !COMPOSED_TYPES.includes(media.contentType);
        composed = redraw ? await exportComposed(media.blob, layers) : media.blob;
      } catch {
        setError(t.compose.composeFailed);
        setSending(false);
        return;
      }
    }
    // US-28: ảnh xem trước mờ ở feed là bản ghép với ảnh, video gốc với video. Kho khoảnh khắc đang đăng giữ và
    // thu hồi URL này; tạo không được thì thu hồi ngay.
    const previewUrl = URL.createObjectURL(composed ?? media.blob);
    try {
      await pendingMoments.submit(buildDraft(media, source, trimStartMs, composed, layers, selection, previewUrl));
      router.push("/");
    } catch (caught: unknown) {
      URL.revokeObjectURL(previewUrl);
      setError(errorText(t, caught));
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
          {error}
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
