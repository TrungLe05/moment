"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { momentsApi, type Moment } from "@/lib/api/moments";

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

  return { items, status, error, loadMore, refreshMoment };
}
