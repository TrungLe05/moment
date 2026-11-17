"use client";

import { useI18n } from "@/i18n/I18nProvider";
import type { CapturedMedia } from "@/platform";

type Props = {
  media: CapturedMedia;
  /** Object URL của file ở máy; trang soạn tạo và thu hồi. */
  url: string;
  onRetake: () => void;
};

/** Xem lại ảnh hoặc video vừa chụp (US-20, US-21) trước khi soạn (T-135) và gửi (T-136, T-137). */
export function CapturedPreview({ media, url, onRetake }: Props) {
  const { t } = useI18n();

  return (
    <div className="flex flex-col gap-4">
      <div className="relative aspect-[358/410] w-full overflow-hidden rounded-[28px] bg-night-surface">
        {media.kind === "photo" ? (
          // eslint-disable-next-line @next/next/no-img-element -- ảnh ở máy (object URL), next/image không dùng được.
          <img src={url} alt={t.compose.capturedPhoto} className="h-full w-full object-cover" />
        ) : (
          <video
            src={url}
            aria-label={t.compose.capturedVideo}
            autoPlay
            loop
            muted
            playsInline
            className="h-full w-full object-cover"
          />
        )}
        {/* BR-09: nguồn của khoảnh khắc. */}
        <span className="absolute top-3.5 right-3.5 rounded-full bg-night/60 px-3 py-1.5 text-xs font-medium text-white backdrop-blur-md">
          {t.compose.camera}
        </span>
      </div>
      <button
        type="button"
        onClick={onRetake}
        className="mx-auto h-11 rounded-full bg-night-surface px-5 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
      >
        {t.compose.retake}
      </button>
    </div>
  );
}
