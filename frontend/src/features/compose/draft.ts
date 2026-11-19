import { toRequestLayers, type Layer } from "@/features/layers/model";
import type { CreateMomentRequest } from "@/lib/api/moments";
import type { CapturedMedia } from "@/platform";
import type { PendingDraft } from "./pending-moments";

/** Lựa chọn ở bước gửi: chế độ (US-26) và người nhận (US-27). `recipientIds` rỗng nghĩa là tất cả bạn bè. */
export type SendSelection = {
  mode: CreateMomentRequest["mode"];
  recipientIds: string[];
};

/**
 * Dựng bản nháp gửi lên (API mục 3.4, ADR 0002). Ảnh gửi ORIGINAL là ảnh chụp và COMPOSED là bản ghép đã vẽ lớp
 * (`composed`, xuất ở máy); video gửi một ORIGINAL, lớp vẽ đè lúc phát. Video chụp bằng camera không quá 5 giây nên
 * không cần điểm cắt; video tải lên dài hơn thì gửi điểm bắt đầu đoạn đã chọn.
 */
export function buildDraft(
  media: CapturedMedia,
  source: CreateMomentRequest["source"],
  trimStartMs: number | null,
  composed: Blob | null,
  layers: Layer[],
  selection: SendSelection,
  previewUrl: string,
): PendingDraft {
  const original = { variant: "ORIGINAL" as const, contentType: media.contentType, sizeBytes: media.blob.size };
  const caption = layers.find((layer) => layer.type === "caption")?.text ?? null;
  return {
    request: {
      mediaType: media.kind === "photo" ? "PHOTO" : "VIDEO",
      // BR-09: khoảnh khắc tải lên luôn hiện nhãn "tải lên".
      source,
      mode: selection.mode,
      // BR-12: "tất cả bạn bè" là mặc định; chọn ít nhất một người thì gửi danh sách đã chọn.
      audience: selection.recipientIds.length === 0 ? "ALL" : "SELECTED",
      recipientIds: selection.recipientIds,
      // DB mục 5: cột caption chép chữ của lớp caption.
      caption,
      layers: toRequestLayers(layers),
      layersVersion: 1,
      // BR-10: điểm bắt đầu đoạn 5 giây, chỉ cho video tải lên dài hơn 5 giây.
      trimStartMs: media.kind === "video" && source === "UPLOAD" ? trimStartMs : null,
      files: composed
        ? [original, { variant: "COMPOSED", contentType: composed.type || "image/jpeg", sizeBytes: composed.size }]
        : [original],
    },
    files: composed ? { ORIGINAL: media.blob, COMPOSED: composed } : { ORIGINAL: media.blob },
    previewUrl,
  };
}
