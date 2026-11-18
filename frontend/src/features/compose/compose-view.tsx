"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import type { CameraFacing, CapturedMedia } from "@/platform";
import { CameraCapture } from "./camera-capture";
import { CapturedPreview } from "./captured-preview";
import { SendPanel } from "./send-panel";

type Captured = { media: CapturedMedia; url: string };

/**
 * Trang khoảnh khắc mới: chụp hoặc quay (T-133), xem lại, chọn chế độ và người nhận rồi gửi (T-136, T-137).
 * Lớp trang trí (T-135) và tải từ máy (T-134) thêm sau. Khi xem lại thì gỡ khung ngắm để nhả camera.
 */
export function ComposeView() {
  const { t } = useI18n();
  const [facing, setFacing] = useState<CameraFacing>("user");
  const [captured, setCaptured] = useState<Captured | null>(null);
  // File chỉ nằm ở máy qua object URL: tạo khi chụp xong, thu hồi khi chụp lại hay rời trang để giải phóng bộ nhớ.
  const capturedRef = useRef<Captured | null>(null);
  useEffect(() => {
    capturedRef.current = captured;
  }, [captured]);
  useEffect(() => () => {
    if (capturedRef.current) URL.revokeObjectURL(capturedRef.current.url);
  }, []);

  const keep = (media: CapturedMedia) => setCaptured({ media, url: URL.createObjectURL(media.blob) });
  const retake = () => {
    if (captured) URL.revokeObjectURL(captured.url);
    setCaptured(null);
  };
  // Đã giao file cho kho khoảnh khắc đang đăng: rời trang không được thu hồi URL nó còn dùng.
  const handOff = () => {
    capturedRef.current = null;
  };

  return (
    <div className="flex min-h-dvh flex-col bg-night text-night-text">
      <header className="mx-auto grid w-full max-w-[390px] shrink-0 grid-cols-[44px_1fr_44px] items-center px-4 py-3">
        <Link
          href="/"
          aria-label={t.compose.close}
          className="flex size-11 items-center justify-center rounded-full bg-night-surface focus-visible:outline-2 focus-visible:outline-peach"
        >
          <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </Link>
        <h1 className="text-center text-[17px] font-semibold">{t.compose.title}</h1>
      </header>
      <main className="mx-auto w-full max-w-[390px] px-4 pb-8">
        {captured ? (
          <div className="flex flex-col gap-5">
            <CapturedPreview media={captured.media} url={captured.url} onRetake={retake} />
            <SendPanel media={captured.media} url={captured.url} onHandedOff={handOff} />
          </div>
        ) : (
          <CameraCapture
            facing={facing}
            onFlip={() => setFacing((current) => (current === "user" ? "environment" : "user"))}
            onCaptured={keep}
          />
        )}
      </main>
    </div>
  );
}
