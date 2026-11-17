"use client";

import { useCallback, useEffect, useState } from "react";
import { CameraError, platform, type CameraErrorKind, type CameraFacing } from "@/platform";

export type CameraState =
  | { status: "opening" }
  | { status: "ready"; stream: MediaStream }
  | { status: "error"; kind: CameraErrorKind };

// Kết quả mở camera gắn với lần mở sinh ra nó; kết quả của lần cũ (camera kia, lần thử trước) coi như đang mở.
type Opened = { key: string; state: CameraState };

/**
 * Luồng camera cho khung ngắm (US-20, US-21): mở khi component dùng hook này hiện ra, mở lại khi đổi camera hay
 * bấm thử lại, luôn đóng khi đổi hoặc rời đi để trình duyệt tắt đèn camera.
 */
export function useCamera(facing: CameraFacing) {
  const supported = platform.camera.isSupported();
  const [attempt, setAttempt] = useState(0);
  const [opened, setOpened] = useState<Opened | null>(null);
  const key = `${facing}:${attempt}`;

  useEffect(() => {
    if (!supported) return;
    // Mỗi lần mở có cờ hủy riêng: luồng mở xong sau khi đã đổi camera hay rời trang thì đóng ngay.
    let cancelled = false;
    let stream: MediaStream | null = null;
    platform.camera.openStream(facing).then(
      (opening) => {
        if (cancelled) {
          platform.camera.closeStream(opening);
          return;
        }
        stream = opening;
        setOpened({ key, state: { status: "ready", stream: opening } });
      },
      (error: unknown) => {
        if (cancelled) return;
        setOpened({ key, state: { status: "error", kind: error instanceof CameraError ? error.kind : "unknown" } });
      },
    );
    return () => {
      cancelled = true;
      if (stream) platform.camera.closeStream(stream);
    };
  }, [supported, facing, key]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);

  const state: CameraState = !supported
    ? { status: "error", kind: "unsupported" }
    : opened?.key === key
      ? opened.state
      : { status: "opening" };
  return { state, retry };
}
