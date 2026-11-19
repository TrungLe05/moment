/** Kích thước (và thời lượng với video) của file chọn từ máy, đọc bằng trình duyệt trước khi tải lên. */
export type MediaInfo = { width: number; height: number; durationMs: number | null };

const PROBE_TIMEOUT_MS = 15_000;

/**
 * Đọc thông tin ảnh hoặc video. File hỏng hay trình duyệt không mở được thì báo lỗi để giao diện từ chối file
 * trước khi tải lên. Video không ghi thời lượng (WebM từ MediaRecorder) trả `durationMs` null.
 */
export function probeMedia(file: Blob, kind: "photo" | "video"): Promise<MediaInfo> {
  const url = URL.createObjectURL(file);
  const done = <T>(value: T) => {
    URL.revokeObjectURL(url);
    return value;
  };
  const probe = new Promise<MediaInfo>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), PROBE_TIMEOUT_MS);
    const fail = () => {
      clearTimeout(timer);
      reject(new Error("unreadable"));
    };
    if (kind === "photo") {
      const image = new Image();
      image.onload = () => {
        clearTimeout(timer);
        resolve({ width: image.naturalWidth, height: image.naturalHeight, durationMs: null });
      };
      image.onerror = fail;
      image.src = url;
    } else {
      const video = document.createElement("video");
      video.preload = "metadata";
      video.muted = true;
      video.onloadedmetadata = () => {
        clearTimeout(timer);
        const seconds = video.duration;
        resolve({
          width: video.videoWidth,
          height: video.videoHeight,
          durationMs: Number.isFinite(seconds) ? Math.round(seconds * 1000) : null,
        });
      };
      video.onerror = fail;
      video.src = url;
    }
  });
  return probe.then(done, (error: unknown) => {
    done(null);
    throw error;
  });
}
