"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { useSession } from "@/features/auth/session";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { MomentCard } from "./moment-card";
import { timeLeft } from "./time";
import { useFeed } from "./use-feed";
import { useNow } from "./use-now";

/**
 * Feed (US-30, US-31) trong không gian chia sẻ: nền tối, vuốt dọc từng khoảnh khắc (cuộn dính theo từng thẻ),
 * tới cuối thì tự tải trang sau.
 */
export function FeedView() {
  const { t } = useI18n();
  const { user } = useSession();
  const { items, status, error, loadMore, refreshMoment, toggleReaction, keepMoment, deleteMoment } = useFeed();
  const now = useNow();
  // Khoảnh khắc chia sẻ hết 24 giờ thì rời feed ngay, không chờ tải lại (BR-14).
  const visible = items.filter((moment) => timeLeft(moment.expiresAt, now) !== null);

  return (
    <div className="flex h-dvh flex-col bg-night text-night-text">
      <header className="mx-auto flex w-full max-w-[390px] shrink-0 items-center justify-between px-4 py-3">
        <h1 className="text-lg font-semibold">{t.appName}</h1>
        <nav className="flex items-center gap-2 text-sm font-semibold">
          <Link href="/friends" className="flex h-11 items-center rounded-full bg-night-surface px-4 focus-visible:outline-2 focus-visible:outline-peach">
            {format(t.feed.friendCount, { count: String(user?.friendCount ?? 0), limit: String(user?.friendLimit ?? 15) })}
          </Link>
          <Link href="/settings" className="flex h-11 items-center rounded-full bg-night-surface px-4 focus-visible:outline-2 focus-visible:outline-peach">
            {t.feed.settings}
          </Link>
        </nav>
      </header>

      {/* Vùng cuộn riêng để cuộn dính theo từng thẻ: snap chỉ có tác dụng trên phần tử đang cuộn. */}
      <main className="min-h-0 flex-1 snap-y snap-mandatory overflow-y-auto">
        <div className="mx-auto w-full max-w-[390px] px-4 pb-8">
        <h2 className="sr-only">{t.feed.title}</h2>
        {status === "done" && visible.length === 0 ? (
          <EmptyFeed />
        ) : (
          <ol className="flex flex-col gap-6">
            {visible.map((moment) => (
              <li key={moment.id} className="snap-start">
                <MomentCard
                  moment={moment}
                  now={now}
                  onMediaError={(id) => void refreshMoment(id)}
                  onReact={(emoji) => toggleReaction(moment, emoji)}
                  onKeep={() => keepMoment(moment.id)}
                  onDelete={() => deleteMoment(moment.id)}
                />
              </li>
            ))}
          </ol>
        )}

        {status === "idle" && <LoadMoreSentinel onVisible={() => void loadMore()} />}
        {status === "loading" && (
          <p role="status" className="py-8 text-center text-sm text-night-muted">
            {t.common.loading}
          </p>
        )}
        {status === "error" && (
          <div role="alert" className="flex flex-col items-center gap-3 py-8 text-center text-sm">
            <p>{t.feed.loadError} {errorText(t, error)}</p>
            <button
              type="button"
              onClick={() => void loadMore()}
              className="h-11 rounded-full bg-peach px-5 font-semibold text-peach-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
            >
              {t.feed.retry}
            </button>
          </div>
        )}
        {status === "done" && visible.length > 0 && (
          <p className="py-8 text-center text-sm text-night-muted">{t.feed.end}</p>
        )}
        </div>
      </main>
    </div>
  );
}

function EmptyFeed() {
  const { t } = useI18n();
  return (
    <section className="flex flex-col items-center gap-3 py-24 text-center">
      <h3 className="text-lg font-semibold">{t.feed.emptyTitle}</h3>
      <p className="text-sm text-night-muted">{t.feed.emptyBody}</p>
      <Link
        href="/friends"
        className="flex h-11 items-center rounded-full bg-peach px-5 text-sm font-semibold text-peach-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
      >
        {t.feed.invite}
      </Link>
    </section>
  );
}

/** Mốc ở cuối danh sách: hiện ra trên màn hình thì tải trang sau (US-30 "tải thêm khi cuộn"). */
function LoadMoreSentinel({ onVisible }: { onVisible: () => void }) {
  const marker = useRef<HTMLDivElement>(null);
  const callback = useRef(onVisible);

  useEffect(() => {
    callback.current = onVisible;
  }, [onVisible]);

  useEffect(() => {
    const element = marker.current;
    if (!element || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) callback.current();
      },
      { rootMargin: "600px 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return <div ref={marker} aria-hidden="true" className="h-px" />;
}
