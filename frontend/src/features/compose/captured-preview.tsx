"use client";

import { useI18n } from "@/i18n/I18nProvider";
import type { Layer } from "@/features/layers/model";
import type { CapturedMedia } from "@/platform";
import { LayerEditor } from "./layer-editor";

type Props = {
  media: CapturedMedia;
  /** Object URL của file ở máy; trang soạn tạo và thu hồi. */
  url: string;
  layers: Layer[];
  onLayersChange: (layers: Layer[]) => void;
  onRetake: () => void;
};

/** Xem lại ảnh hoặc video vừa chụp (US-20, US-21) và trang trí (US-23, US-24, T-135) trước khi gửi. */
export function CapturedPreview({ media, url, layers, onLayersChange, onRetake }: Props) {
  const { t } = useI18n();

  return (
    <div className="flex flex-col gap-4">
      <LayerEditor
        media={{ kind: media.kind, url, width: media.width, height: media.height }}
        layers={layers}
        onChange={onLayersChange}
      />
      <button
        type="button"
        onClick={onRetake}
        className="mx-auto h-11 rounded-full bg-night-surface px-5 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-peach"
      >
        {t.compose.retake}
      </button>
    </div>
  );
}
