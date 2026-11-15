import { WebCameraService, type CameraService } from "./camera";
import { WebGoogleSignIn, type GoogleSignIn } from "./google-sign-in";
import { UnsupportedNotificationService, type NotificationService } from "./notifications";
import { WebShareService, type ShareService } from "./share";
import { MemoryTokenStore, type TokenStore } from "./token-store";

/**
 * Mọi thứ phụ thuộc nền tảng đi qua đây (ARCHITECTURE.md mục 7). Component chỉ dùng các interface này,
 * không gọi thẳng API trình duyệt; bản Capacitor sau này chỉ thay phần cài đặt.
 */
export type Platform = {
  tokenStore: TokenStore;
  camera: CameraService;
  notifications: NotificationService;
  share: ShareService;
  googleSignIn: GoogleSignIn;
};

export const platform: Platform = {
  tokenStore: new MemoryTokenStore(),
  camera: new WebCameraService(),
  notifications: new UnsupportedNotificationService(),
  share: new WebShareService(),
  googleSignIn: new WebGoogleSignIn(process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? ""),
};

export type { CameraService, GoogleSignIn, NotificationService, ShareService, TokenStore };
