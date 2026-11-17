import type { CreateMomentRequest } from "@/lib/api/moments";
import type { CapturedMedia } from "@/platform";
import type { PendingDraft } from "./pending-moments";

/** Lựa chọn ở bước gửi: chế độ (US-26) và người nhận (US-27). `recipientIds` rỗng nghĩa là tất cả bạn bè. */
export type SendSelection = {
  mode: CreateMomentRequest["mode"];
  recipientIds: string[];
};

/**
 * Dựng bản nháp gửi lên (API mục 3.4). Ảnh cần ORIGINAL và COMPOSED: khi chưa có lớp trang trí (T-135), bản ghép
 * chính là ảnh gốc nên gửi cùng một file. Video chụp bằng camera không quá 5 giây nên không cần điểm cắt.
 */
export function buildDraft(media: CapturedMedia, previewUrl: string, selection: SendSelection): PendingDraft {
  const file = { contentType: media.contentType, sizeBytes: media.blob.size };
  const isPhoto = media.kind === "photo";
  return {
    request: {
      mediaType: isPhoto ? "PHOTO" : "VIDEO",
      source: "CAMERA",
      mode: selection.mode,
      // BR-12: "tất cả bạn bè" là mặc định; chọn ít nhất một người thì gửi danh sách đã chọn.
      audience: selection.recipientIds.length === 0 ? "ALL" : "SELECTED",
      recipientIds: selection.recipientIds,
      caption: null,
      layers: [],
      layersVersion: 1,
      trimStartMs: null,
      files: isPhoto
        ? [
            { variant: "ORIGINAL", ...file },
            { variant: "COMPOSED", ...file },
          ]
        : [{ variant: "ORIGINAL", ...file }],
    },
    files: isPhoto ? { ORIGINAL: media.blob, COMPOSED: media.blob } : { ORIGINAL: media.blob },
    previewUrl,
  };
}
