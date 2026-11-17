"use client";

import type { PendingMoment } from "@/features/compose/pending-moments";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { Messages } from "@/i18n/messages";

type Props = {
  pending: PendingMoment;
  onRetry: () => void;
  onCancel: () => void;
};

const FAILURE_REASONS = ["MEDIA_UNREADABLE", "VIDEO_TOO_LONG", "PROCESSING_ERROR"] as const;

// D-033: lý do xử lý thất bại theo mã; mã lạ coi như lỗi xử lý chung.
function failureText(t: Messages, reason: string | null): string {
  const known = FAILURE_REASONS.find((code) => code === reason);
  return t.compose[known ?? "PROCESSING_ERROR"];
}

const button =
  "h-11 rounded-full px-5 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach";

/**
 * US-28: khoảnh khắc vừa gửi hiện bằng ảnh xem trước ở máy, làm mờ, không thanh tiến trình hay số phần trăm.
 * Video hiện khung hình đầu. Lỗi thì báo theo mã, cho thử lại hoặc hủy.
 */
export function PendingMomentCard({ pending, onRetry, onCancel }: Props) {
  const { t } = useI18n();
  const failed = pending.stage === "upload-failed" || pending.stage === "processing-failed";
  const preview = "h-full w-full scale-110 object-cover blur-xl";

  return (
    <article aria-label={t.compose.pendingLabel} className="flex w-full flex-col gap-3">
      <div className="relative mx-auto aspect-[3/4] max-h-[min(72dvh,calc(100dvh_-_16rem))] w-full overflow-hidden rounded-[28px] bg-night-surface">
        {pending.mediaType === "VIDEO" ? (
          // Không phát: chỉ để trình duyệt vẽ khung hình đầu làm ảnh xem trước.
          <video src={pending.previewUrl} aria-label={t.compose.pendingLabel} muted playsInline preload="auto" className={preview} />
        ) : (
          // eslint-disable-next-line @next/next/no-img-element -- ảnh ở máy (object URL), next/image không dùng được.
          <img src={pending.previewUrl} alt={t.compose.pendingLabel} className={preview} />
        )}
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-night/30 px-6 text-center">
          {failed ? (
            <>
              <p role="alert" className="rounded-2xl bg-night/70 px-4 py-2 text-sm font-medium text-white">
                {pending.stage === "upload-failed"
                  ? `${t.compose.uploadFailed} ${errorText(t, pending.error)}`
                  : `${t.compose.processingFailed} ${failureText(t, pending.failureReason)}`}
              </p>
              <div className="flex gap-2">
                <button type="button" onClick={onRetry} className={`${button} bg-peach text-peach-ink`}>
                  {t.compose.retryPost}
                </button>
                <button type="button" onClick={onCancel} className={`${button} bg-night/70 text-white`}>
                  {t.compose.cancelPost}
                </button>
              </div>
            </>
          ) : (
            <p role="status" className="rounded-full bg-night/60 px-4 py-1.5 text-[13px] font-semibold text-white backdrop-blur-md">
              {pending.stage === "uploading" ? t.compose.uploading : t.compose.processing}
            </p>
          )}
        </div>
      </div>
    </article>
  );
}
