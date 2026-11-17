"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState, type KeyboardEvent } from "react";
import { LoadMoreSentinel } from "@/components/load-more-sentinel";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { ARCHIVE_SCOPES, type ArchiveScope, type Moment } from "@/lib/api/moments";
import { groupByMonth, postedDate } from "./months";
import { useArchive } from "./use-archive";

const navLink =
  "flex h-11 items-center rounded-full bg-night-surface px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-peach";

function scopeFrom(value: string | null): ArchiveScope {
  return value === "received" ? "received" : "mine";
}

/**
 * Kho lưu giữ (US-40, US-41): hai tab "Của tôi" và "Được gửi", lưới ảnh thu nhỏ nhóm theo tháng, mới nhất trước.
 * Tab đang mở nằm trên địa chỉ (?tab=received) để quay lại từ trang chi tiết vẫn đúng tab.
 */
export function ArchiveView() {
  const { t } = useI18n();
  const router = useRouter();
  const initial = scopeFrom(useSearchParams().get("tab"));
  const [scope, setScope] = useState<ArchiveScope>(initial);
  const tabs = useRef(new Map<ArchiveScope, HTMLButtonElement>());

  const choose = (next: ArchiveScope) => {
    if (next === scope) return;
    setScope(next);
    router.replace(next === "mine" ? "/archive" : `/archive?tab=${next}`, { scroll: false });
  };

  // Mẫu tablist của WAI-ARIA: mũi tên trái/phải chuyển tab và dời focus theo.
  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const next = ARCHIVE_SCOPES[(ARCHIVE_SCOPES.indexOf(scope) + 1) % ARCHIVE_SCOPES.length]!;
    choose(next);
    tabs.current.get(next)?.focus();
  };

  return (
    <div className="flex min-h-dvh flex-col bg-night text-night-text">
      <header className="mx-auto flex w-full max-w-[390px] shrink-0 items-center justify-between gap-2 px-4 py-3">
        <h1 className="text-lg font-semibold">{t.archive.title}</h1>
        <Link href="/" className={navLink}>
          {t.archive.back}
        </Link>
      </header>
      <main className="mx-auto w-full max-w-[390px] px-4 pb-8">
        <div role="tablist" aria-label={t.archive.tabs} className="mb-4 grid grid-cols-2 gap-1 rounded-full bg-night-surface p-1">
          {ARCHIVE_SCOPES.map((option) => (
            <button
              key={option}
              ref={(element) => {
                if (element) tabs.current.set(option, element);
              }}
              type="button"
              role="tab"
              id={`archive-tab-${option}`}
              aria-selected={option === scope}
              aria-controls="archive-panel"
              tabIndex={option === scope ? 0 : -1}
              onClick={() => choose(option)}
              onKeyDown={onTabKey}
              className={`h-11 rounded-full text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach ${
                option === scope ? "bg-peach text-peach-ink" : "text-night-text"
              }`}
            >
              {t.archive[option]}
            </button>
          ))}
        </div>
        <div role="tabpanel" id="archive-panel" aria-labelledby={`archive-tab-${scope}`}>
          {/* key theo tab: đổi tab thì dựng lại danh sách từ trang đầu. */}
          <ArchiveTab key={scope} scope={scope} />
        </div>
      </main>
    </div>
  );
}

function ArchiveTab({ scope }: { scope: ArchiveScope }) {
  const { t, locale } = useI18n();
  const { items, status, error, loadMore, refreshMoment } = useArchive(scope);
  const groups = groupByMonth(items, locale);

  return (
    <>
      {status === "done" && items.length === 0 ? (
        <section className="flex flex-col items-center gap-3 py-24 text-center">
          <h2 className="text-lg font-semibold">{scope === "mine" ? t.archive.emptyMineTitle : t.archive.emptyReceivedTitle}</h2>
          <p className="text-sm text-night-muted">{scope === "mine" ? t.archive.emptyMineBody : t.archive.emptyReceivedBody}</p>
        </section>
      ) : (
        <div className="flex flex-col gap-6">
          {groups.map((group) => (
            <section key={group.key} aria-labelledby={`month-${scope}-${group.key}`}>
              <h3 id={`month-${scope}-${group.key}`} className="mb-2 text-sm font-semibold text-night-muted">
                {group.label}
              </h3>
              <ul className="grid grid-cols-3 gap-1.5">
                {group.items.map((moment) => (
                  <li key={moment.id}>
                    <Thumbnail moment={moment} scope={scope} onMediaError={() => void refreshMoment(moment.id)} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}

      {status === "idle" && <LoadMoreSentinel onVisible={() => void loadMore()} />}
      {status === "loading" && (
        <p role="status" className="py-8 text-center text-sm text-night-muted">
          {t.common.loading}
        </p>
      )}
      {status === "error" && (
        <div role="alert" className="flex flex-col items-center gap-3 py-8 text-center text-sm">
          <p>
            {t.archive.loadError} {errorText(t, error)}
          </p>
          <button
            type="button"
            onClick={() => void loadMore()}
            className="h-11 rounded-full bg-peach px-5 font-semibold text-peach-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
          >
            {t.archive.retry}
          </button>
        </div>
      )}
    </>
  );
}

// Nhãn nhỏ trên ảnh thu nhỏ: nền tối 60% để chữ trắng đọc được trên ảnh sáng.
const pill = "rounded-full bg-night/60 px-2 py-0.5 text-[11px] font-medium text-white backdrop-blur-md";

function Thumbnail({ moment, scope, onMediaError }: { moment: Moment; scope: ArchiveScope; onMediaError: () => void }) {
  const { t, locale } = useI18n();
  const label = format(t.archive.open, { date: postedDate(moment.createdAt, locale) });

  return (
    <Link
      href={`/archive/moment?id=${encodeURIComponent(moment.id)}&from=${scope}`}
      aria-label={label}
      className="relative block aspect-square overflow-hidden rounded-xl bg-night-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
    >
      {moment.media?.thumbnailUrl && (
        // eslint-disable-next-line @next/next/no-img-element -- xuất tĩnh không dùng được next/image tối ưu ảnh.
        <img
          src={moment.media.thumbnailUrl}
          alt=""
          loading="lazy"
          decoding="async"
          className="h-full w-full object-cover"
          onError={onMediaError}
        />
      )}
      <span className="pointer-events-none absolute inset-x-1 bottom-1 flex flex-wrap justify-end gap-1">
        {moment.mediaType === "VIDEO" && <span className={pill}>{t.archive.video}</span>}
        {/* BR-09: khoảnh khắc tải lên luôn có nhãn "tải lên". */}
        {moment.source === "UPLOAD" && <span className={pill}>{t.feed.uploaded}</span>}
      </span>
    </Link>
  );
}
