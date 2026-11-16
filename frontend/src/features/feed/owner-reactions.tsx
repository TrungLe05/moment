"use client";

import { useId, useRef, useState } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { momentsApi, type ReactionItem } from "@/lib/api/moments";

/** Danh sách ai đã thả gì của một khoảnh khắc; chỉ tải khi chủ mở ra. */
function useMomentReactions(momentId: string) {
  const [items, setItems] = useState<ReactionItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  // Mở, đóng, mở nhanh tạo nhiều request; chỉ kết quả của request mới nhất được hiện.
  const latest = useRef(0);

  async function load() {
    const request = ++latest.current;
    setFailed(false);
    try {
      const loaded = await momentsApi.reactions(momentId);
      if (request === latest.current) setItems(loaded);
    } catch {
      if (request === latest.current) setFailed(true);
    }
  }

  return { items, failed, load };
}

/** US-33, BR-22: chủ thấy số lượt thả và mở ra xem từng người thả emoji gì. */
export function OwnerReactions({ momentId, count }: { momentId: string; count: number }) {
  const { t } = useI18n();
  const listId = useId();
  const [open, setOpen] = useState(false);
  const { items, failed, load } = useMomentReactions(momentId);

  if (count === 0) {
    return <p className="text-sm text-night-muted">{t.feed.noReactions}</p>;
  }

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) void load();
  }

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={listId}
        onClick={toggle}
        className="flex h-11 w-fit items-center rounded-full bg-night-surface px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
      >
        {/* Số lượt lấy theo danh sách vừa tải nếu có, vì số trong feed có thể đã cũ. */}
        {format(t.feed.reactionCount, { count: String(items?.length ?? count) })}
      </button>
      {open && (
        <div id={listId}>
          {failed && (
            <p role="alert" className="text-sm text-peach">
              {t.feed.reactionsLoadError}
            </p>
          )}
          {items && (
            <ul aria-label={t.feed.reactions} className="flex flex-col gap-1 rounded-2xl bg-night-surface p-3 text-sm">
              {items.map((item) => (
                <li key={item.user.id} className="flex items-center justify-between gap-3">
                  <span className="truncate">{item.user.displayName ?? item.user.username}</span>
                  <span className="text-lg">{item.emoji}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
