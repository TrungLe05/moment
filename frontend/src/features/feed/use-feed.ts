"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { momentsApi, type Moment, type ReactionEmoji } from "@/lib/api/moments";

export type FeedStatus = "loading" | "idle" | "done" | "error";

// URL file hết hạn sau 10 phút (BR-20); tải lại một khoảnh khắc tối đa mỗi phút để ảnh lỗi không gây vòng lặp.
const REFRESH_GAP_MS = 60_000;

function appendUnique(current: Moment[], incoming: Moment[]): Moment[] {
  const seen = new Set(current.map((moment) => moment.id));
  return [...current, ...incoming.filter((moment) => !seen.has(moment.id))];
}

/** Dữ liệu feed (US-30): trang đầu khi mở, tải thêm theo con trỏ, lấy lại URL file khi hết hạn. */
export function useFeed() {
  const [items, setItems] = useState<Moment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [status, setStatus] = useState<FeedStatus>("loading");
  const [error, setError] = useState<unknown>(null);
  const loading = useRef(true);
  const lastRefresh = useRef(new Map<string, number>());
  // Lần bấm reaction mới nhất của từng khoảnh khắc: phản hồi về muộn của lần bấm cũ không được ghi đè lựa chọn mới.
  const reactionRequest = useRef(new Map<string, number>());

  const applyPage = useCallback((page: { items: Moment[]; nextCursor: string | null }, append: boolean) => {
    setItems((current) => (append ? appendUnique(current, page.items) : page.items));
    setCursor(page.nextCursor);
    setStatus(page.nextCursor ? "idle" : "done");
    setError(null);
  }, []);

  useEffect(() => {
    // Trang đầu; setState chỉ chạy sau khi request xong. Cờ đang tải gắn với lần chạy này: StrictMode chạy effect
    // hai lần, request bị hủy không được xóa cờ khi request sau vẫn đang chờ.
    let cancelled = false;
    loading.current = true;
    momentsApi.feed(null).then(
      (page) => {
        if (!cancelled) applyPage(page, false);
      },
      (caught: unknown) => {
        if (cancelled) return;
        setError(caught);
        setStatus("error");
      },
    ).finally(() => {
      if (!cancelled) loading.current = false;
    });
    return () => {
      cancelled = true;
    };
  }, [applyPage]);

  /** Trang sau, hoặc thử lại trang đang lỗi; bỏ qua khi đang tải hoặc đã hết. */
  const loadMore = useCallback(async () => {
    if (loading.current || status === "done") return;
    loading.current = true;
    setStatus("loading");
    try {
      applyPage(await momentsApi.feed(cursor), cursor !== null);
    } catch (caught: unknown) {
      setError(caught);
      setStatus("error");
    } finally {
      loading.current = false;
    }
  }, [applyPage, cursor, status]);

  /** Ảnh hoặc video không tải được (thường do URL hết hạn): lấy lại khoảnh khắc; mất quyền xem thì bỏ khỏi feed. */
  const refreshMoment = useCallback(async (id: string) => {
    const now = Date.now();
    if (now - (lastRefresh.current.get(id) ?? 0) < REFRESH_GAP_MS) return;
    lastRefresh.current.set(id, now);
    try {
      const fresh = await momentsApi.detail(id);
      setItems((current) => current.map((moment) => (moment.id === id ? fresh : moment)));
    } catch (caught: unknown) {
      if (caught instanceof ApiError && caught.code === "NOT_FOUND") {
        setItems((current) => current.filter((moment) => moment.id !== id));
      }
    }
  }, []);

  const setMyReaction = useCallback((id: string, myReaction: string | null) => {
    setItems((current) => current.map((moment) => (moment.id === id ? { ...moment, myReaction } : moment)));
  }, []);

  /**
   * US-32, BR-21: thả emoji khác thì thay, thả lại đúng emoji đang chọn thì gỡ. Đổi ngay trên giao diện rồi mới gọi API;
   * lỗi thì trả lại emoji cũ và ném lỗi để thanh reaction báo.
   */
  const toggleReaction = useCallback(
    async (moment: Moment, emoji: ReactionEmoji) => {
      const previous = moment.myReaction;
      const next = previous === emoji ? null : emoji;
      const request = (reactionRequest.current.get(moment.id) ?? 0) + 1;
      reactionRequest.current.set(moment.id, request);
      const isLatest = () => reactionRequest.current.get(moment.id) === request;
      setMyReaction(moment.id, next);
      try {
        if (next === null) {
          await momentsApi.unreact(moment.id);
        } else {
          const saved = (await momentsApi.react(moment.id, next)).emoji;
          if (isLatest()) setMyReaction(moment.id, saved);
        }
      } catch (caught: unknown) {
        // Chỉ lần bấm mới nhất được trả lại trạng thái cũ; lần cũ hơn đã bị lựa chọn sau thay thế.
        if (!isLatest()) return;
        setMyReaction(moment.id, previous);
        throw caught;
      }
    },
    [setMyReaction],
  );

  /** US-34: lưu giữ; thay khoảnh khắc bằng bản server trả về để nhãn đổi ngay. Lỗi ném cho nút báo. */
  const keepMoment = useCallback(async (id: string) => {
    const kept = await momentsApi.keep(id);
    setItems((current) => current.map((moment) => (moment.id === id ? kept : moment)));
  }, []);

  /** US-35: xóa xong thì bỏ khỏi feed. */
  const deleteMoment = useCallback(async (id: string) => {
    await momentsApi.remove(id);
    setItems((current) => current.filter((moment) => moment.id !== id));
  }, []);

  return { items, status, error, loadMore, refreshMoment, toggleReaction, keepMoment, deleteMoment };
}
