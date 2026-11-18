"use client";

import { useI18n } from "@/i18n/I18nProvider";
import { ACCEPTED_TYPES } from "@/lib/media/upload-rules";

/** US-22: chọn ảnh hoặc video có sẵn trên máy; có cả khi camera không dùng được (US-20). */
export function UploadButton({ onPick }: { onPick: (file: File) => void }) {
  const { t } = useI18n();

  return (
    <label className="mx-auto flex h-11 cursor-pointer items-center gap-2 rounded-full bg-night-surface px-5 text-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-peach">
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="3" />
        <circle cx="9" cy="9" r="2" />
        <path d="M21 15l-5-5L5 21" />
      </svg>
      {t.compose.upload}
      <input
        type="file"
        accept={ACCEPTED_TYPES}
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.[0];
          // Xóa lựa chọn để chọn lại đúng file đó vẫn báo onChange.
          event.target.value = "";
          if (file) onPick(file);
        }}
      />
    </label>
  );
}
