"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { Layer } from "@/features/layers/model";
import { useI18n } from "@/i18n/I18nProvider";
import { probeMedia } from "@/lib/media/probe";
import { checkDuration, classifyFile, type UploadRuleError } from "@/lib/media/upload-rules";
import type { CameraFacing, CapturedMedia } from "@/platform";
import { CameraCapture, MAX_VIDEO_MS } from "./camera-capture";
import { CapturedPreview } from "./captured-preview";
import { SendPanel } from "./send-panel";
import { TrimSlider } from "./trim-slider";
import { UploadButton } from "./upload-button";

/** BR-09: nguồn của khoảnh khắc. */
export type MediaSource = "CAMERA" | "UPLOAD";

type Captured = { media: CapturedMedia; url: string; source: MediaSource };

/**
 * Trang khoảnh khắc mới: chụp hoặc quay (T-133) hay tải từ máy (T-134), trang trí (T-135), chọn chế độ và người nhận
 * rồi gửi (T-136, T-137). Khi xem lại thì gỡ khung ngắm để nhả camera.
 */
export function ComposeView() {
  const { t } = useI18n();
  const [facing, setFacing] = useState<CameraFacing>("user");
  const [captured, setCaptured] = useState<Captured | null>(null);
  const [layers, setLayers] = useState<Layer[]>([]);
  // US-22: điểm bắt đầu đoạn 5 giây của video tải lên dài hơn 5 giây; null khi không cần cắt.
  const [trimStartMs, setTrimStartMs] = useState<number | null>(null);
  const [pickError, setPickError] = useState<UploadRuleError | null>(null);
  // File chỉ nằm ở máy qua object URL: tạo khi chụp xong, thu hồi khi chụp lại hay rời trang để giải phóng bộ nhớ.
  // Khi gửi, kho khoảnh khắc đang đăng tự tạo URL xem trước riêng nên URL này luôn do trang soạn thu hồi.
  const capturedRef = useRef<Captured | null>(null);
  useEffect(() => {
    capturedRef.current = captured;
  }, [captured]);
  useEffect(() => () => {
    if (capturedRef.current) URL.revokeObjectURL(capturedRef.current.url);
  }, []);

  const keep = (media: CapturedMedia, source: MediaSource = "CAMERA") => {
    setPickError(null);
    if (source === "CAMERA") setTrimStartMs(null);
    setCaptured({ media, url: URL.createObjectURL(media.blob), source });
  };

  // BR-10: kiểm định dạng, dung lượng, thời lượng ở máy để báo trước khi tải lên (US-22).
  const pick = async (file: File) => {
    setPickError(null);
    const checked = classifyFile(file);
    if ("error" in checked) {
      setPickError(checked.error);
      return;
    }
    let info;
    try {
      info = await probeMedia(file, checked.kind);
    } catch {
      setPickError("MEDIA_UNREADABLE");
      return;
    }
    const tooLong = checked.kind === "video" ? checkDuration(info.durationMs) : null;
    if (tooLong) {
      setPickError(tooLong);
      return;
    }
    setTrimStartMs(checked.kind === "video" && info.durationMs !== null && info.durationMs > MAX_VIDEO_MS ? 0 : null);
    keep({ kind: checked.kind, blob: file, contentType: file.type, ...info }, "UPLOAD");
  };
  // Chụp lại giữ nguyên các lớp đã thêm, để không phải trang trí lại từ đầu.
  const retake = () => {
    if (captured) URL.revokeObjectURL(captured.url);
    setCaptured(null);
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
            <CapturedPreview
              media={captured.media}
              url={captured.url}
              source={captured.source}
              trimStartMs={trimStartMs}
              layers={layers}
              onLayersChange={setLayers}
              onRetake={retake}
            />
            {trimStartMs !== null && captured.media.durationMs !== null && (
              <TrimSlider durationMs={captured.media.durationMs} startMs={trimStartMs} onChange={setTrimStartMs} />
            )}
            <SendPanel media={captured.media} source={captured.source} trimStartMs={trimStartMs} layers={layers} />
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <CameraCapture
              facing={facing}
              onFlip={() => setFacing((current) => (current === "user" ? "environment" : "user"))}
              onCaptured={(media) => keep(media)}
            />
            {pickError && (
              <p role="alert" className="text-center text-sm text-peach">
                {t.compose.uploadErrors[pickError]}
              </p>
            )}
            <UploadButton onPick={(file) => void pick(file)} />
          </div>
        )}
      </main>
    </div>
  );
}
