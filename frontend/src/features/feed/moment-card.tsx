"use client";

import { useEffect, useRef, useState } from "react";
import { VideoLayers } from "@/features/layers/layer-view";
import { useI18n } from "@/i18n/I18nProvider";
import { format, type Messages } from "@/i18n/messages";
import type { Moment, ReactionEmoji } from "@/lib/api/moments";
import { OwnerActions } from "./owner-actions";
import { OwnerReactions } from "./owner-reactions";
import { ReactionBar } from "./reaction-bar";
import { postedAgo, timeLeft, type Span } from "./time";

type Props = {
  moment: Moment;
  now: number;
  /** Ảnh hoặc video không tải được, thường vì URL đã hết hạn. */
  onMediaError: (id: string) => void;
  /** Thả hoặc gỡ reaction; ném lỗi khi không lưu được. */
  onReact: (emoji: ReactionEmoji) => Promise<void>;
  /** Chỉ chủ: lưu giữ và xóa; ném lỗi khi không làm được. */
  onKeep: () => Promise<void>;
  onDelete: () => Promise<void>;
};

// Chữ cái đầu cho ảnh đại diện tạm: tách theo ký tự hiển thị (CLAUDE.md), không cắt đôi emoji hay dấu tiếng Việt.
function initial(name: string): string {
  const first = new Intl.Segmenter(undefined, { granularity: "grapheme" }).segment(name)[Symbol.iterator]().next();
  return first.done ? "" : first.value.segment.toUpperCase();
}

function spanText(t: Messages, span: Span, kind: "posted" | "left"): string {
  const template =
    kind === "posted"
      ? span.unit === "hours" ? t.feed.postedHours : t.feed.postedMinutes
      : span.unit === "hours" ? t.feed.leftHours : t.feed.leftMinutes;
  return format(template, { count: String(span.value) });
}

// Nhãn kính mờ trên ảnh: nền tối 60% để chữ trắng vẫn đọc được trên ảnh sáng.
const pill = "flex items-center gap-1.5 rounded-full bg-night/60 px-3 py-1.5 text-xs font-medium text-white backdrop-blur-md";

/** Một khoảnh khắc trong feed (US-30, US-31): ảnh hoặc video, người đăng, thời gian còn lại hoặc nhãn lưu giữ. */
export function MomentCard({ moment, now, onMediaError, onReact, onKeep, onDelete }: Props) {
  const { t } = useI18n();
  const ownerName = moment.isMine
    ? t.feed.you
    : (moment.owner.displayName ?? moment.owner.username ?? "");
  const label = moment.isMine ? t.feed.myMoment : format(t.feed.momentOf, { name: ownerName });
  const left = timeLeft(moment.expiresAt, now);
  const aspectRatio = moment.width && moment.height ? `${moment.width} / ${moment.height}` : "3 / 4";

  return (
    <article aria-label={label} className="flex w-full flex-col gap-3">
      <div
        className="relative mx-auto max-h-[min(72dvh,calc(100dvh_-_16rem))] w-full overflow-hidden rounded-[28px] bg-night-surface"
        style={{ aspectRatio }}
      >
        {moment.mediaType === "VIDEO" ? (
          <>
            <MomentVideo moment={moment} label={label} onError={() => onMediaError(moment.id)} />
            <VideoLayers layers={moment.layers} width={moment.width} height={moment.height} />
          </>
        ) : (
          moment.media?.displayUrl && (
            // eslint-disable-next-line @next/next/no-img-element -- xuất tĩnh không dùng được next/image tối ưu ảnh.
            <img
              src={moment.media.displayUrl}
              alt={moment.caption ?? label}
              loading="lazy"
              decoding="async"
              className="h-full w-full object-contain"
              onError={() => onMediaError(moment.id)}
            />
          )
        )}
        <div className="pointer-events-none absolute inset-x-3.5 top-3.5 flex items-start justify-between gap-2">
          <span className={`${pill} min-w-0`}>
            <span aria-hidden="true" className="flex size-6 shrink-0 items-center justify-center rounded-full bg-peach text-[11px] font-semibold text-peach-ink">
              {initial(ownerName)}
            </span>
            <span className="truncate text-[13px] font-semibold">
              {format(t.feed.ownerLine, { name: ownerName, age: spanText(t, postedAgo(moment.createdAt, now), "posted") })}
            </span>
          </span>
          <span className="flex shrink-0 flex-col items-end gap-1.5">
            {moment.mode === "KEPT" ? (
              <span className={pill}>{t.feed.kept}</span>
            ) : (
              left && (
                <span className={pill}>
                  <ClockIcon />
                  {spanText(t, left, "left")}
                </span>
              )
            )}
            {/* BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên". */}
            {moment.source === "UPLOAD" && <span className={pill}>{t.feed.uploaded}</span>}
          </span>
        </div>
      </div>
      {/* BR-23: chủ không tự thả reaction, thay vào đó xem ai đã thả gì (US-33). */}
      {moment.isMine ? (
        <div className="flex items-start justify-between gap-3">
          <OwnerReactions momentId={moment.id} count={moment.ownerView?.reactionCount ?? 0} />
          <OwnerActions moment={moment} onKeep={onKeep} onDelete={onDelete} />
        </div>
      ) : (
        <ReactionBar selected={moment.myReaction} onChoose={onReact} />
      )}
    </article>
  );
}

/**
 * US-30: video tự phát không tiếng, lặp lại, bấm để bật tiếng; chỉ phát khi đang hiện trên màn hình.
 * Lớp trang trí vẽ đè lúc phát bằng VideoLayers (ADR 0002, T-135).
 */
function MomentVideo({ moment, label, onError }: { moment: Moment; label: string; onError: () => void }) {
  const { t } = useI18n();
  const video = useRef<HTMLVideoElement>(null);
  const [muted, setMuted] = useState(true);

  useEffect(() => {
    const element = video.current;
    if (!element || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          // Trình duyệt có thể chặn tự phát; khi đó người xem vẫn bấm phát được.
          element.play().catch(() => undefined);
        } else {
          element.pause();
        }
      },
      { threshold: 0.6 },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  if (!moment.media?.videoUrl) return null;
  return (
    <>
      <video
        ref={video}
        src={moment.media.videoUrl}
        poster={moment.media.thumbnailUrl ?? undefined}
        aria-label={label}
        muted={muted}
        loop
        playsInline
        preload="metadata"
        className="h-full w-full object-contain"
        onError={onError}
      />
      <button
        type="button"
        onClick={() => setMuted((current) => !current)}
        aria-label={muted ? t.feed.unmute : t.feed.mute}
        aria-pressed={!muted}
        className="absolute right-3.5 bottom-3.5 flex size-11 items-center justify-center rounded-full bg-night/60 text-white backdrop-blur-md focus-visible:outline-2 focus-visible:outline-peach"
      >
        {muted ? <MutedIcon /> : <SoundIcon />}
      </button>
    </>
  );
}

function ClockIcon() {
  return (
    <svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function MutedIcon() {
  return (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M11 5 6 9H3v6h3l5 4z" />
      <path d="m22 9-6 6M16 9l6 6" />
    </svg>
  );
}

function SoundIcon() {
  return (
    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M11 5 6 9H3v6h3l5 4z" />
      <path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" />
    </svg>
  );
}
