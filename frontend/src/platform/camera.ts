/** Camera cho trình soạn khoảnh khắc (US-20, US-21). Bản native dùng plugin camera của Capacitor. */
export interface CameraService {
  isSupported(): boolean;
  openStream(facing: "user" | "environment"): Promise<MediaStream>;
  closeStream(stream: MediaStream): void;
}

export class WebCameraService implements CameraService {
  isSupported(): boolean {
    return typeof navigator !== "undefined" && typeof navigator.mediaDevices?.getUserMedia === "function";
  }

  openStream(facing: "user" | "environment"): Promise<MediaStream> {
    return navigator.mediaDevices.getUserMedia({ video: { facingMode: facing }, audio: true });
  }

  closeStream(stream: MediaStream): void {
    stream.getTracks().forEach((track) => track.stop());
  }
}
