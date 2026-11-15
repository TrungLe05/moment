"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Trạng thái của một thao tác gửi lên server: đang chạy, lỗi gần nhất, đã xong.
 * Hàm `run` giữ nguyên giữa các lần render để dùng được trong dependency của effect.
 */
export function useAsyncAction<TArgs extends unknown[], TResult>(action: (...args: TArgs) => Promise<TResult>) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);
  const actionRef = useRef(action);

  useEffect(() => {
    actionRef.current = action;
  }, [action]);

  const run = useCallback(async (...args: TArgs): Promise<TResult | undefined> => {
    setPending(true);
    setError(null);
    try {
      const result = await actionRef.current(...args);
      setDone(true);
      return result;
    } catch (caught: unknown) {
      setError(caught);
      return undefined;
    } finally {
      setPending(false);
    }
  }, []);

  return { run, pending, error, done };
}
