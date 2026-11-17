export type CameraFacing = "user" | "environment";

/** Lý do không mở hay không dùng được camera; giao diện hướng dẫn theo từng loại (US-20, US-21). */
export type CameraErrorKind = "unsupported" | "denied" | "not-found" | "busy" | "unknown";

export class CameraError extends Error {
  constructor(readonly kind: CameraErrorKind) {
    super(kind);
    this.name = "CameraError";
  }
}

/** Ảnh hoặc video vừa chụp ở máy, chưa tải lên. `contentType` là loại file khai báo khi tạo khoảnh khắc (BR-10). */
export type CapturedMedia = {
  kind: "photo" | "video";
  blob: Blob;
  contentType: string;
  width: number;
  height: number;
  durationMs: number | null;
};

/** Một lần quay đang chạy: tự dừng khi đủ thời lượng, hoặc dừng sớm bằng `stop`. */
export interface VideoRecording {
  stop(): void;
  readonly result: Promise<CapturedMedia>;
}

/** Camera cho trình soạn khoảnh khắc (US-20, US-21). Bản native dùng plugin camera của Capacitor. */
export interface CameraService {
  isSupported(): boolean;
  /** Chỉ hình, không xin micro: chụp ảnh không cần micro, và từ chối micro không làm hỏng camera. */
  openStream(facing: CameraFacing): Promise<MediaStream>;
  closeStream(stream: MediaStream): void;
  /** Chụp khung hình đang hiện trong `source` (khung ngắm), xuất JPEG. */
  takePhoto(source: HTMLVideoElement): Promise<CapturedMedia>;
  /** Quay từ `stream`, xin thêm micro; micro bị từ chối thì quay không tiếng. */
  startRecording(stream: MediaStream, maxDurationMs: number): Promise<VideoRecording>;
}

const JPEG_QUALITY = 0.9;

// MP4 trước (Safari quay được, server xử lý nhanh hơn), rồi WebM (Chrome, Firefox). Cả hai nằm trong BR-10.
const RECORDER_TYPES = [
  "video/mp4;codecs=avc1,mp4a",
  "video/mp4",
  "video/webm;codecs=vp9,opus",
  "video/webm;codecs=vp8,opus",
  "video/webm",
];

/** Loại MediaRecorder trình duyệt quay được, kèm Content-Type khai báo cho server (bỏ phần codecs). */
export function pickRecorderType(
  isTypeSupported: (type: string) => boolean,
): { recorderType: string; contentType: string } | null {
  const recorderType = RECORDER_TYPES.find((type) => isTypeSupported(type));
  return recorderType ? { recorderType, contentType: recorderType.split(";")[0]! } : null;
}

/** Lỗi của getUserMedia (DOMException theo tên) thành loại lỗi riêng của app. */
export function toCameraError(error: unknown): CameraError {
  if (error instanceof CameraError) return error;
  const name = error instanceof DOMException || error instanceof Error ? error.name : "";
  switch (name) {
    case "NotAllowedError":
    case "SecurityError":
      return new CameraError("denied");
    case "NotFoundError":
    case "OverconstrainedError":
      return new CameraError("not-found");
    case "NotReadableError":
    case "AbortError":
      return new CameraError("busy");
    default:
      return new CameraError("unknown");
  }
}

export class WebCameraService implements CameraService {
  isSupported(): boolean {
    return typeof navigator !== "undefined" && typeof navigator.mediaDevices?.getUserMedia === "function";
  }

  async openStream(facing: CameraFacing): Promise<MediaStream> {
    // Trang không chạy qua HTTPS (trừ localhost) thì trình duyệt không có mediaDevices.
    if (!this.isSupported()) throw new CameraError("unsupported");
    try {
      return await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing }, audio: false });
    } catch (error: unknown) {
      throw toCameraError(error);
    }
  }

  closeStream(stream: MediaStream): void {
    stream.getTracks().forEach((track) => track.stop());
  }

  takePhoto(source: HTMLVideoElement): Promise<CapturedMedia> {
    const width = source.videoWidth;
    const height = source.videoHeight;
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context || width === 0 || height === 0) return Promise.reject(new CameraError("unknown"));
    // Lưu đúng hình camera thấy, không lật như khung ngắm camera trước, để ảnh và video giống nhau.
    context.drawImage(source, 0, 0, width, height);
    return new Promise((resolve, reject) => {
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve({ kind: "photo", blob, contentType: "image/jpeg", width, height, durationMs: null });
          } else {
            reject(new CameraError("unknown"));
          }
        },
        "image/jpeg",
        JPEG_QUALITY,
      );
    });
  }

  async startRecording(stream: MediaStream, maxDurationMs: number): Promise<VideoRecording> {
    const type = typeof MediaRecorder === "undefined" ? null : pickRecorderType((t) => MediaRecorder.isTypeSupported(t));
    if (!type) throw new CameraError("unsupported");
    const microphone = await this.openMicrophone();
    const tracks = [...stream.getVideoTracks(), ...(microphone?.getAudioTracks() ?? [])];
    const recorder = new MediaRecorder(new MediaStream(tracks), { mimeType: type.recorderType });
    const settings = stream.getVideoTracks()[0]?.getSettings() ?? {};
    const chunks: Blob[] = [];
    let startedAt = 0;

    const result = new Promise<CapturedMedia>((resolve, reject) => {
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.push(event.data);
      };
      recorder.onstop = () => {
        clearTimeout(limit);
        if (microphone) this.closeStream(microphone);
        const durationMs = Math.min(Math.round(performance.now() - startedAt), maxDurationMs);
        const blob = new Blob(chunks, { type: type.contentType });
        if (blob.size === 0) {
          reject(new CameraError("unknown"));
          return;
        }
        resolve({
          kind: "video",
          blob,
          contentType: type.contentType,
          width: settings.width ?? 0,
          height: settings.height ?? 0,
          durationMs,
        });
      };
      recorder.onerror = () => {
        clearTimeout(limit);
        if (microphone) this.closeStream(microphone);
        reject(new CameraError("unknown"));
      };
    });

    const stop = () => {
      if (recorder.state !== "inactive") recorder.stop();
    };
    // US-21: tự dừng ở 5 giây.
    const limit = setTimeout(stop, maxDurationMs);
    recorder.start();
    startedAt = performance.now();
    return { stop, result };
  }

  // Micro là tùy chọn: bị từ chối hay không có thì quay không tiếng thay vì báo lỗi.
  private async openMicrophone(): Promise<MediaStream | null> {
    try {
      return await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    } catch {
      return null;
    }
  }
}
