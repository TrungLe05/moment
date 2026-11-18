"use client";

import { useId } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { MAX_VIDEO_MS } from "./camera-capture";

type Props = {
  durationMs: number;
  startMs: number;
  onChange: (startMs: number) => void;
};

/**
 * US-22, BR-10: video tải lên dài hơn 5 giây thì chọn điểm bắt đầu của đoạn 5 giây; server cắt đúng đoạn đó.
 * Dùng thanh trượt gốc của trình duyệt nên kéo được bằng chuột, chạm và phím mũi tên (bước 0,1 giây).
 */
export function TrimSlider({ durationMs, startMs, onChange }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const maxSeconds = Math.floor((durationMs - MAX_VIDEO_MS) / 100) / 10;
  const seconds = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const start = startMs / 1000;

  return (
    <div className="flex flex-col gap-2 rounded-3xl bg-night-surface p-4">
      <label htmlFor={id} className="text-sm font-semibold">
        {t.compose.trimLabel}
      </label>
      <input
        id={id}
        type="range"
        min={0}
        max={maxSeconds}
        step={0.1}
        value={start}
        onChange={(event) => onChange(Math.round(Number(event.target.value) * 1000))}
        className="w-full accent-peach"
      />
      <p className="text-xs text-night-muted">
        {format(t.compose.trimRange, {
          start: seconds.format(start),
          end: seconds.format(start + MAX_VIDEO_MS / 1000),
        })}
      </p>
    </div>
  );
}
