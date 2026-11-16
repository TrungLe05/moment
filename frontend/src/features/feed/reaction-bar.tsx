"use client";

import { useState } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import { REACTION_EMOJIS, type ReactionEmoji } from "@/lib/api/moments";

type Props = {
  selected: string | null;
  /** Ném lỗi khi không lưu được; thanh tự hiện thông báo, trạng thái cũ do bên gọi khôi phục. */
  onChoose: (emoji: ReactionEmoji) => Promise<void>;
};

/** US-32: bộ 6 emoji cố định (BR-21); bấm lại đúng emoji đang chọn thì gỡ, do bên gọi quyết định. */
export function ReactionBar({ selected, onChoose }: Props) {
  const { t } = useI18n();
  const [failed, setFailed] = useState(false);

  async function choose(emoji: ReactionEmoji) {
    setFailed(false);
    try {
      await onChoose(emoji);
    } catch {
      setFailed(true);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div role="group" aria-label={t.feed.reactions} className="flex justify-between gap-1">
        {REACTION_EMOJIS.map((emoji) => {
          const active = selected === emoji;
          return (
            <button
              key={emoji}
              type="button"
              aria-label={format(t.feed.reactWith, { name: t.feed.emojiNames[emoji] })}
              aria-pressed={active}
              onClick={() => void choose(emoji)}
              className={`flex size-11 items-center justify-center rounded-full text-xl transition-transform focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach active:scale-90 ${
                active ? "bg-peach/25 ring-2 ring-peach" : "bg-night-surface"
              }`}
            >
              <span aria-hidden="true">{emoji}</span>
            </button>
          );
        })}
      </div>
      {failed && (
        <p role="alert" className="text-sm text-peach">
          {t.feed.reactionError}
        </p>
      )}
    </div>
  );
}
