"use client";

import Link from "next/link";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import type { Friend } from "@/lib/api/friends";
import type { SendSelection } from "./draft";

type Props = {
  selection: SendSelection;
  onChange: (selection: SendSelection) => void;
  /** null khi đang tải danh sách bạn. */
  friends: Friend[] | null;
  disabled: boolean;
};

const chip = "h-9 shrink-0 rounded-full border-[1.5px] px-3.5 text-[13px] font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach";
const chipOn = "border-peach bg-[#2A2420] text-[#FFB894]";
const chipOff = "border-[#2E2E33] text-night-muted";

/** Chọn chế độ chia sẻ hay lưu giữ (US-26) và người nhận (US-27, BR-12), theo bản mẫu trình soạn. */
export function SendOptions({ selection, onChange, friends, disabled }: Props) {
  const { t } = useI18n();
  const everyone = selection.recipientIds.length === 0;

  const toggle = (id: string) => {
    const picked = selection.recipientIds.includes(id)
      ? selection.recipientIds.filter((current) => current !== id)
      : [...selection.recipientIds, id];
    onChange({ ...selection, recipientIds: picked });
  };

  return (
    <div className="flex flex-col gap-3">
      <fieldset disabled={disabled} className="flex flex-col gap-2">
        <legend className="sr-only">{t.compose.modeLabel}</legend>
        <div className="grid grid-cols-2 gap-1.5 rounded-3xl bg-night-surface p-1.5">
          {(["SHARED", "KEPT"] as const).map((mode) => {
            const checked = selection.mode === mode;
            const on = mode === "SHARED" ? "bg-night-text text-night" : "bg-[#FFFDF8] text-[#8A3B1C]";
            return (
              <label
                key={mode}
                className={`flex h-10 cursor-pointer items-center justify-center rounded-full text-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-peach ${checked ? on : "text-night-muted"}`}
              >
                <input
                  type="radio"
                  name="moment-mode"
                  value={mode}
                  checked={checked}
                  onChange={() => onChange({ ...selection, mode })}
                  className="sr-only"
                />
                {mode === "SHARED" ? t.compose.shared : t.compose.kept}
              </label>
            );
          })}
        </div>
        {/* US-26: một dòng giải thích ngắn cho từng chế độ. */}
        <p className="px-1 text-xs leading-relaxed text-night-muted">
          {selection.mode === "SHARED" ? t.compose.sharedHint : t.compose.keptHint}
        </p>
      </fieldset>

      {friends !== null && friends.length === 0 ? (
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <p className="text-night-muted">{t.compose.noFriends}</p>
          <Link
            href="/friends"
            className="flex h-9 items-center rounded-full bg-night-surface px-4 font-semibold focus-visible:outline-2 focus-visible:outline-peach"
          >
            {t.compose.inviteFriends}
          </Link>
        </div>
      ) : (
        friends !== null && (
          <div role="group" aria-label={t.compose.sendTo} className="flex items-center gap-2">
            <span className="shrink-0 text-[13px] text-night-muted">{t.compose.sendTo}</span>
            <div className="flex gap-2 overflow-x-auto py-1">
              <button
                type="button"
                disabled={disabled}
                aria-pressed={everyone}
                onClick={() => onChange({ ...selection, recipientIds: [] })}
                className={`${chip} ${everyone ? chipOn : chipOff}`}
              >
                {format(t.compose.everyone, { count: String(friends.length) })}
              </button>
              {friends.map((friend) => {
                const picked = selection.recipientIds.includes(friend.id);
                return (
                  <button
                    key={friend.id}
                    type="button"
                    disabled={disabled}
                    aria-pressed={picked}
                    onClick={() => toggle(friend.id)}
                    className={`${chip} ${picked ? chipOn : chipOff}`}
                  >
                    {friend.displayName ?? friend.username}
                  </button>
                );
              })}
            </div>
          </div>
        )
      )}
    </div>
  );
}
