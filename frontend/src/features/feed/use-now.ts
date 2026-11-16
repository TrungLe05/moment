"use client";

import { useEffect, useState } from "react";

/** Giờ hiện tại, cập nhật định kỳ để đồng hồ "còn X giờ" tự đổi và khoảnh khắc hết hạn tự rời feed (US-31). */
export function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
