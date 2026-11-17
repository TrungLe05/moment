"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { errorText } from "@/i18n/error-text";
import { useI18n } from "@/i18n/I18nProvider";
import type { Moment } from "@/lib/api/moments";
import { useAsyncAction } from "@/lib/use-async-action";

type Props = {
  moment: Moment;
  onKeep: () => Promise<void>;
  onDelete: () => Promise<void>;
};

const actionButton =
  "flex h-11 items-center rounded-full bg-night-surface px-4 text-sm font-semibold disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach";

/**
 * US-34: chuyển sang lưu giữ (chỉ khi đang chia sẻ, BR-16; nhãn đổi ngay sau khi chuyển).
 * US-35: xóa có hộp xác nhận (BR-18).
 */
export function OwnerActions({ moment, onKeep, onDelete }: Props) {
  const { t } = useI18n();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const keep = useAsyncAction(onKeep);
  const remove = useAsyncAction(onDelete);
  const error = keep.error ?? remove.error;

  return (
    <div className="flex flex-col items-end gap-2">
      <div className="flex gap-2">
        {moment.mode === "SHARED" && (
          <button type="button" className={actionButton} disabled={keep.pending} onClick={() => void keep.run()}>
            {t.feed.keep}
          </button>
        )}
        <button type="button" className={actionButton} disabled={remove.pending} onClick={() => setConfirmOpen(true)}>
          {t.feed.delete}
        </button>
      </div>
      {error !== null && (
        <p role="alert" className="text-right text-sm text-peach">
          {errorText(t, error)}
        </p>
      )}
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t.feed.deleteTitle}
        description={t.feed.deleteBody}
        confirmLabel={t.feed.deleteConfirm}
        cancelLabel={t.feed.cancel}
        destructive
        onConfirm={() => void remove.run()}
      />
    </div>
  );
}
