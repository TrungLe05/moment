"use client";

import { useId } from "react";
import { useI18n } from "@/i18n/I18nProvider";
import { format } from "@/i18n/messages";
import {
  limitText,
  MAX_CAPTION_CHARACTERS,
  MAX_CAPTION_LINES,
  MAX_LAYER_TEXT_CHARACTERS,
  STICKER_STYLES,
  type Layer,
} from "@/features/layers/model";
import { layerColors } from "@/features/layers/styles";
import { countGraphemes } from "@/lib/text";

type Props = {
  layer: Layer;
  onChange: (layer: Layer) => void;
  onDone: () => void;
  onRemove: () => void;
};

/**
 * Sửa chữ của caption hoặc sticker (US-23, US-24, BR-11): gõ quá giới hạn thì không nhận thêm, có bộ đếm theo ký tự
 * hiển thị. Chữ đổi ngay trên ảnh trong lúc gõ.
 */
export function LayerTextPanel({ layer, onChange, onDone, onRemove }: Props) {
  const { t } = useI18n();
  const fieldId = useId();
  const isCaption = layer.type === "caption";
  const max = isCaption ? MAX_CAPTION_CHARACTERS : MAX_LAYER_TEXT_CHARACTERS;
  const setText = (text: string) =>
    onChange({ ...layer, text: limitText(text, max, isCaption ? MAX_CAPTION_LINES : 1) });
  const field =
    "w-full rounded-2xl border border-[#2E2E33] bg-night-surface px-4 py-3 text-base text-night-text focus-visible:outline-2 focus-visible:outline-peach";

  return (
    <div className="flex flex-col gap-3 rounded-3xl bg-[#16161A] p-3">
      <div className="flex items-center justify-between">
        <label htmlFor={fieldId} className="text-sm font-semibold">
          {isCaption ? t.compose.captionField : t.compose.stickerField}
        </label>
        <span aria-live="polite" className="text-xs text-night-muted">
          {format(t.compose.counter, { count: String(countGraphemes(layer.text)), max: String(max) })}
        </span>
      </div>
      {isCaption ? (
        <textarea
          id={fieldId}
          autoFocus
          rows={3}
          value={layer.text}
          onChange={(event) => setText(event.target.value)}
          className={`${field} resize-none`}
        />
      ) : (
        <input id={fieldId} autoFocus value={layer.text} onChange={(event) => setText(event.target.value)} className={field} />
      )}
      {layer.type === "sticker" && (
        <fieldset className="flex items-center gap-2">
          <legend className="sr-only">{t.compose.stickerStyle}</legend>
          {STICKER_STYLES.map((style) => {
            const colors = layerColors(style);
            return (
              <label
                key={style}
                className={`flex h-9 cursor-pointer items-center rounded-full px-3 text-xs font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-peach ${
                  layer.style === style ? "ring-2 ring-night-text ring-offset-2 ring-offset-[#16161A]" : ""
                }`}
                style={{ background: colors.background, color: colors.color }}
              >
                <input
                  type="radio"
                  name={`${fieldId}-style`}
                  value={style}
                  checked={layer.style === style}
                  onChange={() => onChange({ ...layer, style })}
                  className="sr-only"
                />
                {t.compose.styles[style]}
              </label>
            );
          })}
        </fieldset>
      )}
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onRemove}
          className="h-11 rounded-full bg-night-surface px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-peach"
        >
          {t.compose.removeLayer}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="h-11 rounded-full bg-night-text px-5 text-sm font-semibold text-night focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
        >
          {t.compose.done}
        </button>
      </div>
    </div>
  );
}
