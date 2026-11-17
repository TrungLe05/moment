"use client";

import { useEffect, useRef } from "react";

/** Mốc ở cuối danh sách: hiện ra trên màn hình thì tải trang sau (US-30, US-40 "tải thêm khi cuộn"). */
export function LoadMoreSentinel({ onVisible }: { onVisible: () => void }) {
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
