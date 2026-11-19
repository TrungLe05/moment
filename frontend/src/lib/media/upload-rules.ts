/**
 * BR-10: file tải từ máy. Kiểm ở máy để báo trước khi tải lên (US-22); server luôn kiểm lại (MediaRules).
 * Dựa vào loại MIME trình duyệt đọc được, không dựa vào tên hay phần mở rộng của file.
 */
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024;
export const MAX_VIDEO_BYTES = 50 * 1024 * 1024;
export const MAX_VIDEO_MS = 60_000;

const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];
const VIDEO_TYPES = ["video/mp4", "video/webm", "video/quicktime"];

/** Giá trị `accept` của ô chọn file. */
export const ACCEPTED_TYPES = [...PHOTO_TYPES, ...VIDEO_TYPES].join(",");

export type UploadRuleError = "UNSUPPORTED_MEDIA_TYPE" | "PAYLOAD_TOO_LARGE" | "VIDEO_TOO_LONG" | "MEDIA_UNREADABLE";

export function classifyFile(file: Blob): { kind: "photo" | "video" } | { error: UploadRuleError } {
  if (PHOTO_TYPES.includes(file.type)) {
    return file.size > MAX_PHOTO_BYTES ? { error: "PAYLOAD_TOO_LARGE" } : { kind: "photo" };
  }
  if (VIDEO_TYPES.includes(file.type)) {
    return file.size > MAX_VIDEO_BYTES ? { error: "PAYLOAD_TOO_LARGE" } : { kind: "video" };
  }
  return { error: "UNSUPPORTED_MEDIA_TYPE" };
}

/** Video quá 60 giây bị từ chối; không đọc được thời lượng (WebM không ghi trong header) thì để server đo (D-033). */
export function checkDuration(durationMs: number | null): UploadRuleError | null {
  return durationMs !== null && durationMs > MAX_VIDEO_MS ? "VIDEO_TOO_LONG" : null;
}
