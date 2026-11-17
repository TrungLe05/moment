"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { momentsApi, type Moment } from "@/lib/api/moments";
import { postedDate } from "./months";

// URL file hết hạn sau 10 phút (BR-20); lấy lại tối đa mỗi phút để file lỗi không gây vòng lặp.
const REFRESH_GAP_MS = 60_000;

const pill = "rounded-full bg-night-surface px-3 py-1 text-xs font-medium";

type State = { status: "loading" } | { status: "ready"; moment: Moment } | { status: "error"; error: unknown };

/** US-42: mở một khoảnh khắc trong kho, hiện đầy đủ ảnh hoặc video, caption, ngày đăng. */
export function ArchiveMomentView() {
  const { t } = useI18n();
  const params = useSearchParams();
  const id = params.get("id") ?? "";
  const back = params.get("from") === "received" ? "/archive?tab=received" : "/archive";
  const [state, setState] = useState<State>({ status: "loading" });
  const lastRefresh = useRef(0);

  useEffect(() => {
    let cancelled = false;
    momentsApi.detail(id).then(
      (moment) => {
        if (!cancelled) setState({ status: "ready", moment });
      },
      (caught: unknown) => {
        if (!cancelled) setState({ status: "error", error: caught });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [id]);

  // File không tải được, thường vì URL đã hết hạn: lấy lại khoảnh khắc để có URL mới.
  const refresh = useCallback(async () => {
    const now = Date.now();
    if (now - lastRefresh.current < REFRESH_GAP_MS) return;
    lastRefresh.current = now;
    try {
      setState({ status: "ready", moment: await momentsApi.detail(id) });
    } catch (caught: unknown) {
      setState({ status: "error", error: caught });
    }
  }, [id]);

  return (
    <div className="flex min-h-dvh flex-col bg-night text-night-text">
      <header className="mx-auto flex w-full max-w-[390px] shrink-0 items-center px-4 py-3">
        <Link
          href={back}
          className="flex h-11 items-center rounded-full bg-night-surface px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-peach"
        >
          {t.archive.backToArchive}
        </Link>
      </header>
      <main className="mx-auto w-full max-w-[390px] px-4 pb-8">
        {state.status === "loading" && (
          <p role="status" className="py-8 text-center text-sm text-night-muted">
            {t.common.loading}
          </p>
        )}
        {state.status === "error" && (
          <p role="alert" className="py-8 text-center text-sm">
            {t.archive.detailError} {errorText(t, state.error)}
          </p>
        )}
        {state.status === "ready" && <MomentDetail moment={state.moment} onMediaError={() => void refresh()} />}
      </main>
    </div>
  );
}

function MomentDetail({ moment, onMediaError }: { moment: Moment; onMediaError: () => void }) {
  const { t, locale } = useI18n();
  const ownerName = moment.isMine ? t.feed.you : (moment.owner.displayName ?? moment.owner.username ?? "");
  const label = moment.isMine ? t.feed.myMoment : format(t.feed.momentOf, { name: ownerName });
  const aspectRatio = moment.width && moment.height ? `${moment.width} / ${moment.height}` : "3 / 4";

  return (
    <article aria-label={label} className="flex flex-col gap-3">
      <h1 className="sr-only">{label}</h1>
      <div className="mx-auto max-h-[72dvh] w-full overflow-hidden rounded-[28px] bg-night-surface" style={{ aspectRatio }}>
        {moment.mediaType === "VIDEO"
          ? moment.media?.videoUrl && (
              // Trong kho người xem chủ động mở, nên dùng nút điều khiển của trình duyệt thay cho tự phát như feed.
              <video
                src={moment.media.videoUrl}
                poster={moment.media.thumbnailUrl ?? undefined}
                aria-label={label}
                controls
                loop
                playsInline
                preload="metadata"
                className="h-full w-full object-contain"
                onError={onMediaError}
              />
            )
          : moment.media?.displayUrl && (
              // eslint-disable-next-line @next/next/no-img-element -- xuất tĩnh không dùng được next/image tối ưu ảnh.
              <img
                src={moment.media.displayUrl}
                alt={moment.caption ?? label}
                decoding="async"
                className="h-full w-full object-contain"
                onError={onMediaError}
              />
            )}
      </div>
      {/* Ảnh đã ghép sẵn caption; video vẽ lớp trang trí lúc phát (ADR 0002), tạm hiện caption ở đây tới T-135. */}
      {moment.mediaType === "VIDEO" && moment.caption && <p className="text-[17px] font-semibold">{moment.caption}</p>}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold">{ownerName}</span>
        <span className="text-night-muted">{format(t.archive.postedOn, { date: postedDate(moment.createdAt, locale) })}</span>
        <span className="ml-auto flex gap-1.5">
          {moment.mode === "KEPT" && <span className={pill}>{t.feed.kept}</span>}
          {/* BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên". */}
          {moment.source === "UPLOAD" && <span className={pill}>{t.feed.uploaded}</span>}
        </span>
      </div>
    </article>
  );
}
